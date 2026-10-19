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

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_UPDATE_RESOURCE;
import static com.peregrine.commons.util.PerConstants.CONTENT;
import static com.peregrine.commons.util.PerConstants.CREATED;
import static com.peregrine.commons.util.PerConstants.NAME;
import static com.peregrine.commons.util.PerConstants.PAGE;
import static com.peregrine.commons.util.PerConstants.PATH;
import static com.peregrine.commons.util.PerConstants.STATUS;
import static com.peregrine.commons.util.PerConstants.TYPE;
import static com.peregrine.commons.util.PerUtil.EQUALS;
import static com.peregrine.commons.util.PerUtil.PER_PREFIX;
import static com.peregrine.commons.util.PerUtil.PER_VENDOR;
import static com.peregrine.commons.util.PerUtil.POST;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

import com.peregrine.admin.resource.AdminResourceHandler;
import com.peregrine.admin.resource.AdminResourceHandler.ManagementException;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import com.peregrine.reference.ReferenceLister;
import com.peregrine.replication.AssetAltTextRepublishCoordinator;
import com.peregrine.replication.AssetAltTextOverrideDetector;
import com.peregrine.replication.PerReplicable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.Servlet;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionHistory;
import org.apache.sling.api.resource.Resource;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.peregrine.commons.util.PerConstants.JCR_CONTENT;
import static com.peregrine.commons.util.PerConstants.JCR_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.PAGE_CONTENT_TYPE;
import static com.peregrine.commons.util.PerConstants.PAGE_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.PUBLISHED_LABEL;

/**
 * Servlet to provide Update Resources support
 *
 * These are the rules:
 * 1) The properties / node structure is provided as JSon on the parameter: 'content'
 * 2) The node structure is a JSon Map where each property is a property of the node
 *    and each Map is a sub node
 * 3) Sub nodes can be deleted by:
 *    a) providing a property on the node called 'delete'
 *       and either be null or 'true'
 *    b) providing a property on the parent called 'delete' and providing the name of the child
 *       node. If found it will be deleted
 *
 * The API Definition can be found in the Swagger Editor configuration:
 *    ui.apps/src/main/content/jcr_root/perapi/definitions/admin.yaml
 */
@Component(
    service = Servlet.class,
    property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "Update Resource servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + POST,
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_UPDATE_RESOURCE
    }
)
@SuppressWarnings("serial")
public class UpdateResourceServlet extends AbstractBaseServlet {

    public static final String FAILED_TO_UPDATE_PAGE = "Failed to Update Page";
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateResourceServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Reference
    AdminResourceHandler resourceManagement;

    @Reference
    ReferenceLister referenceLister;

    @Reference
    AssetAltTextRepublishCoordinator altTextRepublishCoordinator;

    @Override
    protected Response handleRequest(Request request) throws IOException {
        String path = request.getParameter(PATH);
        String content = request.getParameter(CONTENT);
        if (request.getResponse() != null) {
            request.getResponse().setHeader("X-Peregrine-UpdateResource-Handled", "true");
        }
        LOGGER.info("Received update-resource request for {}", path);
        String trackedAssetPath = null;
        String trackedReportToken = null;
        boolean editorSaveExpected = false;
        boolean assetSaveCommitted = false;
        try {
            String assetPath = null;
            Resource asset = null;
            String altTextReportToken = null;
            boolean imageChanged = false;
            String republishWarning = null;
            String expectedAltText = "";
            try {
                assetPath = getAssetPath(path);
                asset = assetPath == null ? null : request.getResourceResolver().getResource(assetPath);
                if (asset != null && !"per:Asset".equals(asset.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) asset = null;
                imageChanged = asset != null && hasSubmittedAssetChanges(asset.getChild(JCR_CONTENT), content);
                expectedAltText = asset == null ? "" : getCurrentAltText(asset.getChild(JCR_CONTENT));
                final String submittedAltText = getSubmittedAltText(content);
                if (submittedAltText != null) expectedAltText = submittedAltText;
                // Track every asset save so metadata-only changes are reported too.
                if (asset != null) {
                    imageChanged = true;
                    altTextReportToken = UUID.randomUUID().toString();
                }
            } catch (RuntimeException trackingFailure) {
                LOGGER.warn("Could not prepare image republish tracking for {}", assetPath, trackingFailure);
                if (assetPath != null) republishWarning = "Image changes were saved, but their republish queue entry could not be created. Verify the published image and pages manually.";
                imageChanged = false;
            }
            if (imageChanged) {
                try {
                    altTextRepublishCoordinator.expectDeferredEditorSave(assetPath, altTextReportToken, expectedAltText);
                    trackedAssetPath = assetPath;
                    trackedReportToken = altTextReportToken;
                    editorSaveExpected = true;
                } catch (RuntimeException trackingFailure) {
                    LOGGER.warn("Could not start image republish tracking for {}; saving the asset anyway", assetPath, trackingFailure);
                    try {
                        altTextRepublishCoordinator.abortEditorSave(assetPath, altTextReportToken);
                    } catch (RuntimeException cleanupFailure) {
                        LOGGER.warn("Could not clean up failed alt text republish tracking for {}", assetPath, cleanupFailure);
                    }
                    republishWarning = "Image changes were saved, but their republish queue entry could not be created. Verify the published image and pages manually.";
                }
            }
            Resource updatePage = resourceManagement.updateResource(request.getResourceResolver(), path, content);
            request.getResourceResolver().commit();
            assetSaveCommitted = true;
            try {
                final Set<String> unpublishedPages = new LinkedHashSet<>();
                if (imageChanged && asset != null) {
                    try {
                        unpublishedPages.addAll(findUnpublishedReferencingPages(asset));
                    } catch (RuntimeException trackingFailure) {
                        LOGGER.warn("Asset {} was saved, but unpublished page tracking failed", assetPath, trackingFailure);
                    }
                }
                final JsonResponse response = new JsonResponse()
                    .writeAttribute(TYPE, PAGE)
                    .writeAttribute(STATUS, CREATED)
                    .writeAttribute(NAME, updatePage.getName())
                    .writeAttribute(PATH, updatePage.getPath());
                response.writeArray("unpublishedPages");
                for (final String pagePath : unpublishedPages) response.writeString(pagePath);
                response.writeClose();
                response.writeAttribute("imageChanged", imageChanged);
                boolean assetRepublishable = false;
                try {
                    final PerReplicable assetReplicable = asset == null ? null : asset.adaptTo(PerReplicable.class);
                    assetRepublishable = assetReplicable != null && assetReplicable.isReplicated()
                            && hasPublishedVersion(assetReplicable.getMainResource());
                } catch (RuntimeException trackingFailure) {
                    LOGGER.warn("Asset {} was saved, but its publish state could not be checked", assetPath, trackingFailure);
                }
                response.writeAttribute("assetRepublishable", assetRepublishable);
                if (editorSaveExpected) {
                    response.writeAttribute("reportToken", altTextReportToken);
                    response.writeAttribute("assetPath", asset.getPath());
                }
                if (republishWarning != null) response.writeAttribute("republishWarning", republishWarning);
                return response.writeClose();
            } catch (RuntimeException responseFailure) {
                // Reporting errors must not turn a committed save into a client-side failure.
                LOGGER.error("Asset/resource {} was saved, but its update response could not be fully assembled", path, responseFailure);
                return new JsonResponse()
                    .writeAttribute(TYPE, PAGE)
                    .writeAttribute(STATUS, CREATED)
                    .writeAttribute(NAME, updatePage.getName())
                    .writeAttribute(PATH, updatePage.getPath())
                    .writeAttribute("imageChanged", imageChanged)
                    .writeAttribute("republishWarning", "Image changes were saved, but republish status could not be prepared. Verify the image manually.")
                    .writeClose();
            }
        } catch (ManagementException e) {
            LOGGER.warn("Rejected update-resource request for {}: {}", path, e.getMessage(), e);
            return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage(FAILED_TO_UPDATE_PAGE).setRequestPath(path).setException(e);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to update resource {}", path, e);
            throw e;
        } finally {
            if (editorSaveExpected && !assetSaveCommitted) {
                try {
                    altTextRepublishCoordinator.abortEditorSave(trackedAssetPath, trackedReportToken);
                } catch (RuntimeException cleanupFailure) {
                    LOGGER.warn("Could not clean up image republish tracking for {}", trackedAssetPath, cleanupFailure);
                }
            }
        }
    }

    private String getAssetPath(final String contentPath) {
        if (contentPath == null) return null;
        final int contentIndex = contentPath.indexOf("/jcr:content");
        if (contentIndex < 0 || !contentPath.substring(contentIndex).startsWith("/jcr:content")) return null;
        final String assetPath = contentPath.substring(0, contentIndex);
        if (!assetPath.startsWith("/content/") || !assetPath.contains("/assets/")) return null;
        return assetPath;
    }

    private String getSubmittedAltText(final String content) {
        if (content == null) return null;
        try {
            final JsonNode submitted = OBJECT_MAPPER.readTree(content);
            final JsonNode value = submitted.get("alt");
            if (value != null) return value.isNull() ? "" : value.asText();
            final JsonNode deletedProperties = submitted.get("_opDeleteProps");
            if (deletedProperties != null && deletedProperties.isArray()) {
                for (final JsonNode deleted : deletedProperties) {
                    if (deleted.isTextual() && "alt".equals(deleted.asText())) return "";
                }
            }
            return null;
        } catch (IOException e) {
            LOGGER.debug("Unable to read submitted asset alt text", e);
            return null;
        }
    }

    private String getCurrentAltText(final Resource content) {
        if (content == null) return "";
        final String alt = content.getValueMap().get("alt", String.class);
        return alt == null ? "" : alt.trim();
    }

    private boolean hasSubmittedAssetChanges(final Resource current, final String submittedContent) {
        if (current == null || submittedContent == null) return false;
        try {
            final JsonNode submitted = OBJECT_MAPPER.readTree(submittedContent);
            return hasSubmittedAssetChanges(current, submitted);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot compare submitted image properties", e);
        }
    }

    private boolean hasSubmittedAssetChanges(final Resource current, final JsonNode submitted) {
        if (submitted == null || !submitted.isObject()) return false;
        final Iterator<Map.Entry<String, JsonNode>> fields = submitted.fields();
        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> field = fields.next();
            final String name = field.getKey();
            final JsonNode value = field.getValue();
            if ("_opDeleteProps".equals(name) && value.isArray()) {
                for (final JsonNode deleted : value) {
                    if (deleted.isTextual() && current.getValueMap().containsKey(deleted.asText())) return true;
                }
                continue;
            }
            if ("_opDeleteNodes".equals(name) && value.isArray()) {
                for (final JsonNode deleted : value) {
                    if (deleted.isTextual() && current.getChild(deleted.asText()) != null) return true;
                }
                continue;
            }
            if (isNonEditableAssetField(name)) continue;
            final Resource child = current.getChild(name);
            if (value.isObject() && child != null) {
                if (hasSubmittedAssetChanges(child, value)) return true;
                continue;
            }
            if (value.isObject()) {
                if (!value.isEmpty()) return true;
                continue;
            }
            final Object existing = current.getValueMap().get(name);
            if (existing instanceof Calendar || existing instanceof Date) continue;
            final JsonNode existingValue = OBJECT_MAPPER.valueToTree(existing);
            if (existingValue == null ? !value.isNull() : !existingValue.equals(value)) return true;
        }
        return false;
    }

    private boolean isNonEditableAssetField(final String name) {
        return "path".equals(name) || "name".equals(name) || "id".equals(name)
                || "_opDeleteProps".equals(name) || "_opDeleteNodes".equals(name)
                || "jcr:primaryType".equals(name) || "jcr:mixinTypes".equals(name)
                || "jcr:created".equals(name) || "jcr:createdBy".equals(name)
                || "jcr:lastModified".equals(name) || "jcr:lastModifiedBy".equals(name)
                || "jcr:uuid".equals(name) || "jcr:data".equals(name);
    }

    private Set<String> findUnpublishedReferencingPages(final Resource asset) {
        final Set<String> pages = new LinkedHashSet<>();
        for (final com.peregrine.reference.Reference reference : referenceLister.getReferencedByList(asset)) {
            final Resource page = findPage(reference.getResource());
            if (page == null || AssetAltTextOverrideDetector.hasOverride(reference, page.getChild(JCR_CONTENT))) continue;
            final Resource pageContent = page.getChild(JCR_CONTENT);
            if (pageContent == null || !PAGE_CONTENT_TYPE.equals(pageContent.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) {
                continue;
            }
            final PerReplicable replicable = pageContent.adaptTo(PerReplicable.class);
            final boolean replicated = replicable != null && replicable.isReplicated();
            final boolean stale = replicable != null && replicable.isStale();
            final boolean publishedVersion = hasPublishedVersion(pageContent);
            final boolean unpublished = (replicated && stale) || (publishedVersion && (!replicated || stale));
            if (unpublished) {
                pages.add(page.getPath());
            }
        }
        return pages;
    }

    private boolean hasPublishedVersion(final Resource content) {
        final Session session = content.getResourceResolver().adaptTo(Session.class);
        if (session == null) return false;
        try {
            final VersionHistory history = session.getWorkspace().getVersionManager().getVersionHistory(content.getPath());
            for (final String label : history.getVersionLabels()) {
                if (PUBLISHED_LABEL.equals(label)) return true;
            }
        } catch (RepositoryException | RuntimeException e) {
            LOGGER.warn("Could not check published version of {}", content.getPath(), e);
        }
        return false;
    }

    private Resource findPage(final Resource resource) {
        Resource current = resource;
        while (current != null) {
            if (PAGE_PRIMARY_TYPE.equals(current.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) return current;
            current = current.getParent();
        }
        return null;
    }

}
