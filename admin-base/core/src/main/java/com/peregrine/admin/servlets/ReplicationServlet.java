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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_DO_REPLICATION;
import static com.peregrine.commons.util.PerConstants.*;
import static com.peregrine.commons.util.PerUtil.*;
import static java.lang.Boolean.parseBoolean;
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

    private Map<String, String> prePublishWebhookMap;
    private Map<String, String> postPublishWebhookMap;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @Reference
    private ReplicationsContainerWithDefault replications;

    @Reference
    private AdminResourceHandler resourceManagement;

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
        final boolean draft = parseBoolean(request.getParameter("draft"));
        final boolean callback = parseBoolean(request.getParameter("callback"));

        final String tenant = getTenantNameFromResource(resource);
        final String resourceType = resource.getResourceType();

        if (parseBoolean(request.getParameter(DEACTIVATE))) {
            return performDeactivation(replication, resource, callback, tenant);
        }

        final PerUtil.ResourceChecker tenantChecker = new ReplicationUtil.TenantOwnedResourceChecker(resource);
        List<Resource> toBeReplicated = listMissingResources(resource, tenantChecker, deep, new LinkedList<>());
        for (final Resource r : Optional.of(RESOURCES)
                .map(request::getParameterValues)
                .map(Arrays::stream)
                .orElseGet(Stream::empty)
                .map(resourceResolver::getResource)
                .filter(Objects::nonNull)
                .collect(Collectors.toList())) {
            listMissingResources(r, tenantChecker, deep, toBeReplicated);
        }

        // Remove source from the list if it's an assets folder
        if (resourceManagement.isAssetsFolder(resource)) {
            toBeReplicated.remove(resource);
        }

        toBeReplicated = replication.prepare(toBeReplicated);
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
        if (draft && (resourceType.equals(PAGE_PRIMARY_TYPE) || resourceType.equals(OBJECT_PRIMARY_TYPE))) {
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

            // Add all replicated paths
            allReplicatedPaths.addAll(publishedReferences.stream().map(Resource::getPath).collect(Collectors.toList()));
            allReplicatedPaths.addAll(draftReferences.stream().map(Resource::getPath).collect(Collectors.toList()));
        }

        List<Resource> replicateResponse = replication.replicate(toBeReplicated);

        // Trigger the Post-publish Webhook
        if (callback) {
            String postPublishWebhook = postPublishWebhookMap.get(tenant);
            callWebhook(postPublishWebhook, allReplicatedPaths.toArray(new String[0]), "post-publish", false);
        }

        return prepareResponse(resource, replicateResponse);
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
            callWebhook(postPublishWebhook, new String[]{resource.getPath()}, "post-publish", false);
        }

        return prepareResponse(resource, replicatedStuff);
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
        String[] prePublishWebhooks = configuration.pre_publish_webhook_map();
        for (String webhook : prePublishWebhooks) {
            String[] tokens = webhook.split("=", 2);
            if (tokens.length == 2 && isNotEmpty(tokens[0]) && isNotEmpty(tokens[1])) {
                prePublishWebhookMap.put(tokens[0], tokens[1]);
            }
        }

        postPublishWebhookMap = new HashMap<>();
        String[] postPublishWebhooks = configuration.post_publish_webhook_map();
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
