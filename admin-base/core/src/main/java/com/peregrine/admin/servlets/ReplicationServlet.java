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
                name = "Webhook URL",
                description = "Webhook URL to call after replication",
                required = false
        )
        String webhook() default "";
    }

    public static final String DEACTIVATE = "deactivate";
    public static final String RESOURCES = "resources";

    private String webhook;

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
        if (parseBoolean(request.getParameter(DEACTIVATE))) {
            return performDeactivation(replication, resource);
        }

        final boolean deep = parseBoolean(request.getParameter("deep"));
        final boolean draft = parseBoolean(request.getParameter("draft"));
        final boolean callback = parseBoolean(request.getParameter("callback"));

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

        String resourceType = resource.getResourceType();

        // per:Page OR per:Object -> republish all pages (except published above) with label "Draft" or "Published"
        if (draft && (resourceType.equals(PAGE_PRIMARY_TYPE) || resourceType.equals(OBJECT_PRIMARY_TYPE))) {
            Workspace workspace = resourceResolver.adaptTo(Session.class).getWorkspace();
            QueryManager queryManager = workspace.getQueryManager();
            VersionManager versionManager = workspace.getVersionManager();

            String tenant = getTenantNameFromResource(resource);

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

        if (callback) {
            callWebhook(allReplicatedPaths.toArray(new String[0]));
        }

        return prepareResponse(resource, replicateResponse);
    }

    @NotNull
    private Response performDeactivation(
            final Replication replication,
            final Resource resource
    ) throws ReplicationException, IOException {
        final var replicatedStuff = replication.deactivate(resource);
        for (final Resource r : streamReplicableResources(replicatedStuff)
                .collect(Collectors.toList())) {
            resourceManagement.deleteVersionLabel(r, PerConstants.PUBLISHED_LABEL);
        }

        callWebhook(new String[]{resource.getPath()});

        return prepareResponse(resource, replicatedStuff);
    }

    private void callWebhook(String[] paths) {
        try {
            if (!isEmpty(webhook) && paths.length > 0) {
                logger.trace("Calling FS Replication Webhook: {}", webhook);

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

                HttpClient httpClient = HttpClient.newHttpClient();
                HttpResponse<String> httpResponse = httpClient.send(webhookRequest, HttpResponse.BodyHandlers.ofString());;

                logger.trace("FS Replication Webhook Response: " + httpResponse.statusCode());
            }
        }
        catch (Exception e) {
            logger.error("FS Replication Webhook failed with: " + e.getMessage());
        }
    }

    @Activate
    @SuppressWarnings("unused")
    void activate(ReplicationServlet.Configuration configuration) { setup(configuration); }

    @Modified
    @SuppressWarnings("unused")
    void modified(ReplicationServlet.Configuration configuration) { setup(configuration); }

    private void setup(ReplicationServlet.Configuration configuration) {
        webhook = configuration.webhook();
    }
}
