package com.peregrine.admin.servlets;

/*-
 * #%L
 * admin base - Core
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peregrine.admin.resource.AdminResourceHandler;
import com.peregrine.commons.util.PerConstants;
import com.peregrine.commons.util.PerUtil;
import com.peregrine.replication.PerReplicable;
import com.peregrine.replication.Replication;
import com.peregrine.replication.Replication.ReplicationException;
import com.peregrine.replication.ReplicationUtil;
import com.peregrine.replication.ReplicationsContainerWithDefault;
import com.peregrine.admin.resource.GeminiGeneration;
import com.peregrine.admin.resource.SmartPublishCoordinator;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.jetbrains.annotations.NotNull;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;
import javax.servlet.Servlet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_DO_REPLICATION;
import static com.peregrine.commons.util.PerConstants.*;
import static com.peregrine.commons.util.PerUtil.*;
import static java.lang.Boolean.parseBoolean;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * This servlet replicates the given resource with its JCR Content
 * and any references
 *
 * The API Definition can be found in the Swagger Editor configuration:
 *    ui.apps/src/main/content/jcr_root/perapi/definitions/admin.yaml
 *
 * It is invoked like this:
 *      curl -X POST "http://localhost:8080/perapi/admin/repl.json/content/themeclean" -H  "accept: application/json" -H  "content-type: application/x-www-form-urlencoded" -d "name=defaultRepl&quot;deep=false"
 *
 * Smart Publish (draft, default true) is queued with the {@link SmartPublishCoordinator} and returns a jobId right
 * away (see publishStatus.json). Other replication waits until no Smart Publish batch is running. draft and callback
 * default to true; only "false" turns them off.
 */
@Component(
    service = Servlet.class,
    property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "Replication Servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + POST,
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_DO_REPLICATION
    }
)
@Designate(ocd = ReplicationServlet.Configuration.class)
@SuppressWarnings("serial")
public final class ReplicationServlet extends ReplicationServletBase {
    @ObjectClassDefinition(
            name = "Peregrine: Replication Servlet",
            description = "Replicates resources via Sling renderers"
    )
    @interface Configuration {
        @AttributeDefinition(
                name = "Pre-publish Webhook Map",
                description = "Pre-publish Webhook Configuration. Format: tenant = Webhook URL to call before replication",
                required = false
        )
        String[] pre_publish_webhook_map();

        @AttributeDefinition(
                name = "Post-publish Webhook Map",
                description = "Post-publish Webhook Configuration. Format: tenant = Webhook URL to call after replication",
                required = false
        )
        String[] post_publish_webhook_map();
    }

    public static final String DEACTIVATE = "deactivate";
    public static final String RESOURCES = "resources";

    private static final long EXCLUSIVE_LOCK_TIMEOUT_SECONDS = 120;
    private static final Duration WEBHOOK_TIMEOUT = Duration.ofSeconds(60);

    private Map<String, String> prePublishWebhookMap = new HashMap<>();
    private Map<String, String> postPublishWebhookMap = new HashMap<>();

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Reference
    private ReplicationsContainerWithDefault replications;

    @Reference
    private AdminResourceHandler resourceManagement;

    @Reference
    private GeminiGeneration geminiGeneration;

    @Reference
    private SmartPublishCoordinator coordinator;

    protected ReplicationsContainerWithDefault getReplications() {
        return replications;
    }

    @Override
    protected Response performReplication(
            final Replication replication,
            final Request request,
            final Resource resource,
            final ResourceResolver resourceResolver
    ) throws IOException, ReplicationException, RepositoryException {
        final boolean deep = parseBoolean(request.getParameter("deep"));
        // Smart Publish and the post-publish webhook are on unless explicitly turned off with "false"
        final boolean draft = !"false".equalsIgnoreCase(request.getParameter("draft"));
        final boolean callback = !"false".equalsIgnoreCase(request.getParameter("callback"));

        final boolean deactivate = parseBoolean(request.getParameter(DEACTIVATE));

        final String tenant = getTenantNameFromResource(resource);
        final String resourceType = resource.getResourceType();
        final PublishRequest publishRequest = new PublishRequest(resource.getPath(), deep, callback,
                draft && (resourceType.equals(PAGE_PRIMARY_TYPE) || resourceType.equals(OBJECT_PRIMARY_TYPE)),
                Optional.ofNullable(request.getParameterValues(RESOURCES)).orElse(new String[0]));

        if (draft && !deactivate) {
            return queueSmartPublish(replication, resource, resourceResolver, tenant, publishRequest);
        }

        if (!lockExclusive()) {
            return new ErrorResponse()
                    .setHttpErrorCode(SC_SERVICE_UNAVAILABLE)
                    .setErrorMessage("A site publish is still running. Try again in a few minutes.");
        }
        try {
            if (deactivate) {
                return performDeactivation(replication, resource, callback, tenant);
            }
            return prepareResponse(resource, publish(replication, resourceResolver, tenant, List.of(publishRequest)));
        } finally {
            coordinator.exclusiveLock().unlock();
        }
    }

    /** Publishes one or more requests (several when Smart Publish requests were combined into one batch). */
    private List<Resource> publish(
            final Replication replication,
            final ResourceResolver resourceResolver,
            final String tenant,
            final List<PublishRequest> requests
    ) throws IOException, ReplicationException, RepositoryException {
        List<Resource> toBeReplicated = new LinkedList<>();
        for (final PublishRequest publishRequest : requests) {
            final Resource resource = resourceResolver.getResource(publishRequest.path);
            if (resource == null) {
                logger.warn("Skipping {}: it no longer exists", publishRequest.path);
                continue;
            }
            final PerUtil.ResourceChecker tenantChecker = new ReplicationUtil.TenantOwnedResourceChecker(resource);
            listMissingResources(resource, tenantChecker, publishRequest.deep, toBeReplicated);
            for (final Resource r : Arrays.stream(publishRequest.resources)
                    .map(resourceResolver::getResource)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())) {
                listMissingResources(r, tenantChecker, publishRequest.deep, toBeReplicated);
            }

            // Remove source from the list if it's an assets folder
            if (resourceManagement.isAssetsFolder(resource)) {
                toBeReplicated.remove(resource);
            }
        }

        toBeReplicated = replication.prepare(toBeReplicated);

        // Sort the list to prioritize per:Asset resources
        toBeReplicated.sort((r1, r2) -> {
            boolean isAsset1 = isAsset(r1);
            boolean isAsset2 = isAsset(r2);
            if (isAsset1 && !isAsset2) return -1;
            if (!isAsset1 && isAsset2) return 1;
            return 0;
        });

        logger.info("Replication triggered. Preparing {} resources for processing.", toBeReplicated.size());

        // Iterate and generate AI content for assets before versioning
        for (Resource r : toBeReplicated) {
            boolean isAssetNode = isAsset(r);
            logger.info("Evaluating resource in replication payload: path={}, isAsset={}", r.getPath(), isAssetNode);

            if (isAssetNode) {
                Node assetNode = r.adaptTo(Node.class);
                try {
                    logger.info("Triggering AI metadata generation for asset: {}", r.getPath());
                    geminiGeneration.generateAndApplyAltTextAndTranslations(assetNode, resourceResolver);
                    logger.info("Finished AI metadata generation step for asset: {}", r.getPath());
                } catch (Exception e) {
                    logger.error("Failed to generate AI alt text/translations for asset: {}", r.getPath(), e);
                }
            }
        }

        // Save the session to persist new AI-generated properties
        resourceResolver.adaptTo(Session.class).save();

        List<String> toBeReplicatedPaths = new ArrayList<>();
        HashSet<String> allReplicatedPaths = new HashSet<>();
        streamReplicableResources(toBeReplicated)
                .map(Resource::getPath)
                .forEach(p -> {
                    try {
                        resourceManagement.createVersion(resourceResolver, p, PerConstants.PUBLISHED_LABEL);
                        toBeReplicatedPaths.add(p);
                        allReplicatedPaths.add(p);
                    } catch (final AdminResourceHandler.ManagementException e) {
                        logger.trace("Unable to create a version for path: {} ", p, e);
                    }
                });

        // Trigger the pre-publish Webhook before querying for drafts and checking out for the first path (Halts on failure)
        String prePublishWebhook = prePublishWebhookMap.get(tenant);
        String[] prePublishPaths = toBeReplicatedPaths.isEmpty() ? new String[0] : new String[]{ toBeReplicatedPaths.get(0) };
        callWebhook(prePublishWebhook, prePublishPaths, "pre-publish", true);

        // per:Page OR per:Object -> republish all pages (except published above) with label "Draft" or "Published"
        if (requests.stream().anyMatch(r -> r.sweep)) {
            Workspace workspace = resourceResolver.adaptTo(Session.class).getWorkspace();
            QueryManager queryManager = workspace.getQueryManager();
            VersionManager versionManager = workspace.getVersionManager();

            // Find all pages
            String pages = "/"+ CONTENT +"/"+ tenant +"/" + PAGES;

            String statement =  "select * from [per:PageContent] as r " +
                    "where ISDESCENDANTNODE(r, '"+ pages +"')";
            Query query = queryManager.createQuery(statement, Query.JCR_SQL2);
            QueryResult queryResult = query.execute();

            List<Resource> publishedReferences = new ArrayList<>();
            List<Resource> draftReferences = new ArrayList<>();
            NodeIterator nodeIterator = queryResult.getNodes();
            String draftLabel = "Draft";

            try {
                while (nodeIterator.hasNext()) {
                    Node node = nodeIterator.nextNode();
                    String nodePath = node.getPath();

                    if (toBeReplicatedPaths.contains(nodePath)) {
                        continue;
                    }

                    // Ignore specific folders
                    if (!nodePath.startsWith(pages + "/css") &&
                            !nodePath.startsWith(pages + "/js") &&
                            !nodePath.startsWith(pages + "/skeleton-pages")) {

                        Resource pageContent = resourceResolver.getResource(nodePath);
                        PerReplicable replicable = pageContent.adaptTo(PerReplicable.class);

                        if (replicable.isReplicated()) {
                            // Create a Draft version to restore the latest changes after publishing
                            if (replicable.isStale()) {
                                VersionHistory versionHistory = versionManager.getVersionHistory(nodePath);

                                if (versionManager.isCheckedOut(nodePath)){
                                    try {
                                        Version publishedVersion = versionHistory.getVersionByLabel(PUBLISHED_LABEL);
                                        Version draftVersion = versionManager.checkin(nodePath);
                                        versionManager.checkout(nodePath);
                                        versionHistory.addVersionLabel(draftVersion.getName(), draftLabel, true);

                                        logger.warn("Draft version created for {} at {}", nodePath, draftVersion.getFrozenNode().getPath());

                                        // Checkout Published version for publishing
                                        versionManager.restore(publishedVersion, true);
                                        versionManager.checkout(nodePath);

                                        draftReferences.add(resourceResolver.getResource(pageContent.getParent().getPath()));
                                    }
                                    catch (Exception e) {
                                        logger.error("Unable to create a draft version for path: {} ", nodePath, e);
                                    }
                                }
                            }
                            else {
                                publishedReferences.add(pageContent.getParent());
                            }
                        }
                    }
                }

                // Publish page references
                replication.replicate(draftReferences);
                replication.replicate(publishedReferences);
            } finally {
                // Restore Draft versions
                for (Resource pageResource : draftReferences) {
                    String contentPath = pageResource.getChild(JCR_CONTENT).getPath();
                    VersionHistory versionHistory = versionManager.getVersionHistory(contentPath);
                    try {
                        Version draftVersion = versionHistory.getVersionByLabel(draftLabel);
                        versionManager.restore(draftVersion, true);
                        versionManager.checkout(contentPath);
                    }
                    catch (Exception e) {
                        logger.error("Unable to restore a draft version for path: {} ", contentPath, e);
                    }
                }
            }

            // Add all replicated paths
            allReplicatedPaths.addAll(publishedReferences.stream().map(Resource::getPath).collect(Collectors.toList()));
            allReplicatedPaths.addAll(draftReferences.stream().map(Resource::getPath).collect(Collectors.toList()));
        }

        List<Resource> replicateResponse = replication.replicate(toBeReplicated);

        // Trigger the Post-publish Webhook
        if (requests.stream().anyMatch(r -> r.callback)) {
            String postPublishWebhook = postPublishWebhookMap.get(tenant);
            callWebhook(postPublishWebhook, allReplicatedPaths.toArray(new String[0]), "post-publish", true);
        }

        return replicateResponse;
    }

    private Response queueSmartPublish(
            final Replication replication,
            final Resource resource,
            final ResourceResolver resourceResolver,
            final String tenant,
            final PublishRequest publishRequest
    ) throws IOException, ReplicationException {
        try {
            // The batch outlives this request, so it runs with its own copy of the user's session
            publishRequest.forBatch(tenant, replication, resourceResolver.clone(null));
        } catch (final LoginException e) {
            throw new ReplicationException("Unable to open a session for the background publish: " + e.getMessage(), e);
        }
        final SmartPublishCoordinator.Ticket ticket;
        try {
            ticket = coordinator.submit(Objects.toString(tenant, ""), resourceResolver.getUserID(), resource.getPath(),
                    publishRequest, this::runSmartPublish);
        } catch (final RejectedExecutionException e) {
            publishRequest.close();
            return new ErrorResponse()
                    .setHttpErrorCode(SC_SERVICE_UNAVAILABLE)
                    .setErrorMessage("The publishing service is not running");
        }
        return new JsonResponse()
                .writeAttribute("sourcePath", resource.getPath())
                .writeAttribute("jobId", ticket.getId())
                .writeAttribute("state", ticket.getState().name().toLowerCase(Locale.ROOT))
                .writeAttribute("batchSize", ticket.getBatchSize());
    }

    /** Runs combined Smart Publish requests with the session of the latest one. */
    private void runSmartPublish(final List<Object> payloads) throws Exception {
        final List<PublishRequest> requests = payloads.stream().map(PublishRequest.class::cast).collect(Collectors.toList());
        final PublishRequest latest = requests.get(requests.size() - 1);
        final long start = System.currentTimeMillis();
        try {
            final List<Resource> replicated = publish(latest.replication, latest.resolver, latest.tenant, requests);
            logger.info("Smart Publish for tenant {} ({} request(s)) replicated {} resources in {} ms",
                    latest.tenant, requests.size(), replicated.size(), System.currentTimeMillis() - start);
        } finally {
            requests.forEach(PublishRequest::close);
        }
    }

    private boolean lockExclusive() {
        try {
            return coordinator.exclusiveLock().tryLock(EXCLUSIVE_LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** One publish request. A queued Smart Publish request also carries what its background batch needs. */
    private static final class PublishRequest implements AutoCloseable {
        final String path;
        final boolean deep;
        final boolean callback;
        final boolean sweep;
        final String[] resources;
        String tenant;
        Replication replication;
        ResourceResolver resolver;

        PublishRequest(String path, boolean deep, boolean callback, boolean sweep, String[] resources) {
            this.path = path;
            this.deep = deep;
            this.callback = callback;
            this.sweep = sweep;
            this.resources = resources;
        }

        void forBatch(String tenant, Replication replication, ResourceResolver resolver) {
            this.tenant = tenant;
            this.replication = replication;
            this.resolver = resolver;
        }

        @Override
        public void close() {
            if (resolver != null && resolver.isLive()) {
                resolver.close();
            }
        }
    }

    @NotNull
    private Response performDeactivation(
            final Replication replication,
            final Resource resource,
            final boolean callback,
            final String tenant
    ) throws ReplicationException, IOException {
        final var replicatedStuff = replication.deactivate(resource);
        for (final Resource r : streamReplicableResources(replicatedStuff)
                .collect(Collectors.toList())) {
            resourceManagement.deleteVersionLabel(r, PerConstants.PUBLISHED_LABEL);
        }

        if (callback) {
            String postPublishWebhook = postPublishWebhookMap.get(tenant);
            callWebhook(postPublishWebhook, new String[]{resource.getPath()}, "post-publish", true);
        }

        return prepareResponse(resource, replicatedStuff);
    }

    private boolean isAsset(Resource resource) {
        if (resource != null) {
            Node node = resource.adaptTo(Node.class);
            try {
                return node != null && node.isNodeType("per:Asset");
            } catch (RepositoryException e) {
                logger.warn("Failed to check node type", e);
            }
        }
        return false;
    }

    /**
     * Unified method for dispatching webhooks, capable of handling varying severity of errors.
     * @param webhook The target URL
     * @param paths The list of paths to send in the payload
     * @param webhookType String descriptor ("pre-publish" or "post-publish")
     * @param failOnError If true, exceptions and non-200 responses will throw ReplicationExceptions and halt the process
     */
    private void callWebhook(String webhook, String[] paths, String webhookType, boolean failOnError) throws ReplicationException {
        if (!isEmpty(webhook) && paths.length > 0) {
            logger.trace("Calling FS Replication {} Webhook: {}", webhookType, webhook);

            try {
                ObjectMapper objectMapper = new ObjectMapper();
                ObjectNode payload = objectMapper.createObjectNode();

                ArrayNode arrayNode = payload.putArray("paths");
                for (String path : paths) {
                    arrayNode.add(path);
                }

                String requestBody = objectMapper.writeValueAsString(payload);

                // Webhook request
                HttpRequest webhookRequest = HttpRequest.newBuilder()
                        .uri(URI.create(webhook))
                        .header("Content-Type", "application/json")
                        .timeout(WEBHOOK_TIMEOUT)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpClient httpClient = getHttpClient();
                HttpResponse<String> httpResponse = httpClient.send(webhookRequest, HttpResponse.BodyHandlers.ofString());

                // If the response is not HTTP 200 OK, throw an exception to be caught and logged below
                if (httpResponse.statusCode() != 200) {
                    throw new ReplicationException("HTTP " + httpResponse.statusCode() + " - " + httpResponse.body());
                }

                logger.trace("FS Replication {} Webhook Response: {}", webhookType, httpResponse.statusCode());
            } catch (ReplicationException e) {
                // Always log the error
                logger.error("FS Replication {} Webhook failed with: {}", webhookType, e.getMessage());

                // Halt the process only if we care about failures
                if (failOnError) {
                    throw e;
                }
            } catch (Exception e) {
                // Always log general exceptions
                logger.error("FS Replication {} Webhook failed with: {}", webhookType, e.getMessage(), e);

                // Wrap and throw general exceptions if we care about failures
                if (failOnError) {
                    throw new ReplicationException("FS Replication " + webhookType + " Webhook failed: " + e.getMessage(), e);
                }
            }
        }
    }

    @Activate
    @SuppressWarnings("unused")
    void activate(ReplicationServlet.Configuration configuration) { setup(configuration); }

    @Modified
    @SuppressWarnings("unused")
    void modified(ReplicationServlet.Configuration configuration) { setup(configuration); }

    private void setup(ReplicationServlet.Configuration configuration) {
        prePublishWebhookMap = new HashMap<>();
        String[] prePublishWebhooks = Optional.ofNullable(configuration.pre_publish_webhook_map()).orElse(new String[0]);
        for (String webhook : prePublishWebhooks) {
            String[] tokens = webhook.split("=", 2);
            if (tokens.length == 2 && isNotEmpty(tokens[0]) && isNotEmpty(tokens[1])) {
                prePublishWebhookMap.put(tokens[0], tokens[1]);
            }
        }

        postPublishWebhookMap = new HashMap<>();
        String[] postPublishWebhooks = Optional.ofNullable(configuration.post_publish_webhook_map()).orElse(new String[0]);
        for (String webhook : postPublishWebhooks) {
            String[] tokens = webhook.split("=", 2);
            if (tokens.length == 2 && isNotEmpty(tokens[0]) && isNotEmpty(tokens[1])) {
                postPublishWebhookMap.put(tokens[0], tokens[1]);
            }
        }
    }

    // For test mocking only
    protected HttpClient getHttpClient() {
        return HttpClient.newHttpClient();
    }
}
