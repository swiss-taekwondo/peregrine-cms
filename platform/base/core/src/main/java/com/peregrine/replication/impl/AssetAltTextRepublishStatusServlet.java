package com.peregrine.replication.impl;

/*-
 * #%L
 * platform base - Core
 * %%
 * Copyright (C) 2026 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import com.peregrine.commons.servlets.AbstractBaseServlet;
import com.peregrine.reference.ReferenceLister;
import com.peregrine.replication.AssetAltTextOverrideDetector;
import com.peregrine.replication.AssetAltTextRepublishCoordinator;
import com.peregrine.replication.PerReplicable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionHistory;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

import static com.peregrine.commons.util.PerConstants.PATH;
import static com.peregrine.commons.util.PerUtil.EQUALS;
import static com.peregrine.commons.util.PerUtil.GET;
import static com.peregrine.commons.util.PerUtil.POST;
import static com.peregrine.commons.util.PerUtil.PER_PREFIX;
import static com.peregrine.commons.util.PerUtil.PER_VENDOR;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_PATHS;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

@Component(service = Servlet.class, property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "Image Republish Status Servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + GET,
        SLING_SERVLET_METHODS + EQUALS + POST,
        SLING_SERVLET_PATHS + EQUALS + "/perapi/admin/assetAltTextRepublishStatus"
})
@SuppressWarnings("serial")
public final class AssetAltTextRepublishStatusServlet extends AbstractBaseServlet {

    @Reference
    private ResourceResolverFactory resourceResolverFactory;

    @Reference
    private ReferenceLister referenceLister;

    @Reference
    private AssetAltTextRepublishCoordinator coordinator;

    @Override
    protected Response handleRequest(final Request request) throws IOException {
        if ("true".equals(request.getParameter("queue"))) return queueResponse(request);
        final String assetPath = request.getParameter(PATH);
        final String reportToken = request.getParameter("reportToken");
        if (assetPath == null || !assetPath.startsWith("/content/") || !assetPath.contains("/assets/")
                || reportToken == null || !reportToken.matches("[0-9a-fA-F-]{36}")) {
            return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Invalid asset or report token");
        }
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, "asset-alttext-republish");
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final int separator = assetPath.lastIndexOf('/');
            final boolean assetExists = resolver.getResource(assetPath) != null;
            if (separator < 0 || (assetExists && request.getResourceResolver().getResource(assetPath) == null)
                    || (!assetExists && request.getResourceResolver().getResource(assetPath.substring(0, separator)) == null)) {
                return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Asset not accessible");
            }
            final Resource report = resolver.getResource("/var/asset-alttext-republish" + assetPath + "/" + reportToken);
            final boolean completed = report != null && reportToken.equals(report.getValueMap().get("reportToken", String.class))
                    && report.getValueMap().get("completed", false);
            final JsonResponse response = new JsonResponse().writeAttribute("completed", completed);
            final boolean retrying = report != null && reportToken.equals(report.getValueMap().get("reportToken", String.class))
                    && report.getValueMap().get("retrying", false);
            response.writeAttribute("retrying", retrying);
            if (retrying) response.writeAttribute("error", report.getValueMap().get("error", ""));
            if (completed) {
                response.writeAttribute("error", report.getValueMap().get("error", ""));
                response.writeArray("republishedPages");
                for (final String pagePath : report.getValueMap().get("republishedPages", new String[0])) {
                    if (request.getResourceResolver().getResource(pagePath) != null) response.writeString(pagePath);
                }
                response.writeClose();
                response.writeArray("republishedAssets");
                for (final String imagePath : report.getValueMap().get("republishedAssets", new String[0])) {
                    if (request.getResourceResolver().getResource(imagePath) != null) response.writeString(imagePath);
                }
                response.writeClose();
                response.writeArray("unpublishedPages");
                for (final String pagePath : report.getValueMap().get("unpublishedPages", new String[0])) {
                    if (request.getResourceResolver().getResource(pagePath) != null) response.writeString(pagePath);
                }
                response.writeClose();
            }
            return response.writeClose();
        } catch (final LoginException e) {
            return new ErrorResponse().setHttpErrorCode(SC_INTERNAL_SERVER_ERROR).setErrorMessage("Cannot read republish status").setException(e);
        }
    }

    private Response queueResponse(final Request request) throws IOException {
        final String requestedAssetPath = request.getParameter(PATH);
        if (requestedAssetPath != null && (!requestedAssetPath.startsWith("/content/")
                || !requestedAssetPath.contains("/assets/")
                || request.getResourceResolver().getResource(requestedAssetPath) == null)) {
            return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Invalid asset path");
        }
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, "asset-alttext-republish");
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource pendingRoot = resolver.getResource("/var/asset-alttext-republish/pending");
            final String method = request.getRequest().getMethod();
            if ("POST".equals(method)) {
                final String assetPath = request.getParameter(PATH);
                final String token = request.getParameter("reportToken");
                final String action = request.getParameter("action");
                if (assetPath == null || !assetPath.startsWith("/content/") || !assetPath.contains("/assets/")
                        || token == null || !token.matches("[0-9a-fA-F-]{36}")
                        || request.getResourceResolver().getResource(assetPath) == null
                        || !("republish".equals(action) || "discard".equals(action))) {
                    return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Invalid queue action");
                }
                final Session editorSession = request.getResourceResolver().adaptTo(Session.class);
                try {
                    if (editorSession == null || !editorSession.hasPermission(assetPath + "/jcr:content",
                            Session.ACTION_SET_PROPERTY)) {
                        return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Asset is not editable");
                    }
                } catch (final RepositoryException e) {
                    return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Cannot check asset permission");
                }
                boolean accepted = "republish".equals(action)
                        ? coordinator.republishDeferredEditorSave(assetPath, token)
                        : coordinator.discardDeferredEditorSave(assetPath, token);
                if (!accepted && "discard".equals(action)) {
                    final Resource manual = resolver.getResource("/var/asset-alttext-republish/manual/" + token);
                    if (manual != null && assetPath.equals(manual.getValueMap().get("assetPath", String.class))) {
                        final Resource manualRoot = resolver.getResource("/var/asset-alttext-republish/manual");
                        for (final Resource entry : manualRoot.getChildren()) {
                            if (assetPath.equals(entry.getValueMap().get("assetPath", String.class))) resolver.delete(entry);
                        }
                        resolver.commit();
                        accepted = true;
                    }
                }
                if (!accepted) return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage("Queue entry is no longer available; reload the queue");
                return new JsonResponse().writeAttribute("accepted", true).writeClose();
            }
            final JsonResponse response = new JsonResponse().writeArray("entries");
            if (pendingRoot != null) {
                final Map<String, Resource> latestByAsset = new HashMap<>();
                for (final Resource pending : pendingRoot.getChildren()) {
                    final String assetPath = pending.getValueMap().get("assetPath", String.class);
                    if (!Boolean.TRUE.equals(pending.getValueMap().get("deferred", Boolean.class))
                            || Boolean.TRUE.equals(pending.getValueMap().get("discarded", Boolean.class))
                            || (requestedAssetPath != null && !requestedAssetPath.equals(assetPath))
                            || assetPath == null || request.getResourceResolver().getResource(assetPath) == null) continue;
                    final Resource asset = resolver.getResource(assetPath);
                    final Resource content = asset == null ? null : asset.getChild("jcr:content");
                    if (content == null || !pending.getValueMap().get("expectedAlt", "")
                            .equals(content.getValueMap().get("alt", "").trim())) continue;
                    final Resource previous = latestByAsset.get(assetPath);
                    if (previous == null || pending.getValueMap().get("createdAt", 0L)
                            > previous.getValueMap().get("createdAt", 0L)) latestByAsset.put(assetPath, pending);
                }
                for (final Map.Entry<String, Resource> queued : latestByAsset.entrySet()) {
                    final String assetPath = queued.getKey();
                    final Resource pending = queued.getValue();
                    final Resource asset = resolver.getResource(assetPath);
                    final PerReplicable assetReplicable = asset == null ? null : asset.adaptTo(PerReplicable.class);
                    final boolean assetRepublishable = assetReplicable != null && assetReplicable.isReplicated()
                            && hasPublishedVersion(assetReplicable.getMainResource());
                    final Set<String> eligible = new LinkedHashSet<>();
                    final Set<String> blocked = new LinkedHashSet<>();
                    for (final com.peregrine.reference.Reference reference : referenceLister.getReferencedByList(asset)) {
                        final Resource page = findPage(reference.getResource());
                        if (page == null || request.getResourceResolver().getResource(page.getPath()) == null
                                || AssetAltTextOverrideDetector.hasOverride(reference, page.getChild("jcr:content"))) continue;
                        final Resource pageContent = page.getChild("jcr:content");
                        if (pageContent == null || !"per:PageContent".equals(pageContent.getValueMap().get("jcr:primaryType", ""))) continue;
                        final PerReplicable replicable = pageContent.adaptTo(PerReplicable.class);
                        final boolean publishedVersion = hasPublishedVersion(pageContent);
                        if (replicable != null && replicable.isReplicated() && !replicable.isStale() && publishedVersion) {
                            eligible.add(page.getPath());
                        } else if (publishedVersion || (replicable != null && replicable.isReplicated())) {
                            blocked.add(page.getPath());
                        }
                    }
                    response.writeObject();
                    response.writeAttribute("assetPath", assetPath);
                    response.writeAttribute("reportToken", pending.getName());
                    response.writeAttribute("altText", pending.getValueMap().get("expectedAlt", ""));
                    response.writeAttribute("assetRepublishable", assetRepublishable);
                    response.writeArray("eligiblePages");
                    for (final String page : eligible) response.writeString(page);
                    response.writeClose();
                    response.writeArray("blockedPages");
                    for (final String page : blocked) response.writeString(page);
                    response.writeClose();
                    response.writeClose();
                }
            }
            final Resource manualRoot = resolver.getResource("/var/asset-alttext-republish/manual");
            if (manualRoot != null) {
                final Map<String, Resource> latestManual = new HashMap<>();
                final Map<String, Set<String>> blockedByAsset = new HashMap<>();
                for (final Resource manual : manualRoot.getChildren()) {
                    final String assetPath = manual.getValueMap().get("assetPath", String.class);
                    if (assetPath == null || (requestedAssetPath != null && !requestedAssetPath.equals(assetPath))
                            || request.getResourceResolver().getResource(assetPath) == null) continue;
                    final Set<String> blocked = blockedByAsset.computeIfAbsent(assetPath, key -> new LinkedHashSet<>());
                    for (final String pagePath : manual.getValueMap().get("blockedPages", new String[0])) {
                        final Resource page = resolver.getResource(pagePath);
                        if (page != null && request.getResourceResolver().getResource(pagePath) != null) blocked.add(pagePath);
                    }
                    final Resource previous = latestManual.get(assetPath);
                    if (previous == null || manual.getValueMap().get("createdAt", 0L)
                            > previous.getValueMap().get("createdAt", 0L)) latestManual.put(assetPath, manual);
                }
                for (final Map.Entry<String, Resource> entry : latestManual.entrySet()) {
                    final String assetPath = entry.getKey();
                    final Resource manual = entry.getValue();
                    final Set<String> blocked = blockedByAsset.get(assetPath);
                    if (blocked.isEmpty()) continue;
                    response.writeObject();
                    response.writeAttribute("assetPath", assetPath);
                    response.writeAttribute("reportToken", manual.getName());
                    final Resource asset = resolver.getResource(assetPath);
                    final Resource assetContent = asset == null ? null : asset.getChild("jcr:content");
                    response.writeAttribute("altText", assetContent == null ? "" : assetContent.getValueMap().get("alt", ""));
                    response.writeAttribute("manualReview", true);
                    response.writeArray("eligiblePages").writeClose();
                    response.writeArray("blockedPages");
                    for (final String page : blocked) response.writeString(page);
                    response.writeClose().writeClose();
                }
            }
            return response.writeClose().writeClose();
        } catch (final LoginException | PersistenceException e) {
            return new ErrorResponse().setHttpErrorCode(SC_INTERNAL_SERVER_ERROR)
                    .setErrorMessage("Cannot read alt text queue").setException(e);
        }
    }

    private Resource findPage(final Resource resource) {
        Resource current = resource;
        while (current != null) {
            if ("per:Page".equals(current.getValueMap().get("jcr:primaryType", ""))) return current;
            current = current.getParent();
        }
        return null;
    }

    private boolean hasPublishedVersion(final Resource content) {
        final Session session = content.getResourceResolver().adaptTo(Session.class);
        if (session == null) return false;
        try {
            final VersionHistory history = session.getWorkspace().getVersionManager().getVersionHistory(content.getPath());
            for (final String label : history.getVersionLabels()) if ("Published".equals(label)) return true;
        } catch (final RepositoryException e) {
            return false;
        }
        return false;
    }
}
