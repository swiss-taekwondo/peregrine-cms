package com.peregrine.replication.impl;

/*-
 * #%L
 * platform base - Core
 * %%
 * Copyright (C) 2026 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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

import com.peregrine.reference.Reference;
import com.peregrine.reference.ReferenceLister;
import com.peregrine.replication.PerReplicable;
import com.peregrine.replication.AssetAltTextRepublishJobConstants;
import com.peregrine.replication.AssetAltTextRepublishCoordinator;
import com.peregrine.replication.AssetAltTextOverrideDetector;
import com.peregrine.replication.Replication;
import com.peregrine.replication.ReplicationWebhookService;
import com.peregrine.replication.ReplicationsContainerWithDefault;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.Node;
import javax.jcr.Session;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.peregrine.commons.util.PerConstants.JCR_CONTENT;
import static com.peregrine.commons.util.PerConstants.PAGE_CONTENT_TYPE;
import static com.peregrine.commons.util.PerConstants.PAGE_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.PUBLISHED_LABEL;
import static com.peregrine.commons.util.PerUtil.EQUALS;
import static com.peregrine.commons.util.PerConstants.JCR_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerUtil.getTenantNameFromResource;

/** Republishes published pages when an image asset’s alt text changes. **/
@Component(service = {ResourceChangeListener.class, JobConsumer.class, Runnable.class,
        AssetAltTextRepublishCoordinator.class}, immediate = true, property = {
        JobConsumer.PROPERTY_TOPICS + EQUALS + AssetAltTextRepublishJobConstants.TOPIC,
        ResourceChangeListener.PATHS + "=/content",
        "scheduler.expression=0/10 * * * * ?",
        "scheduler.concurrent=false",
        "scheduler.runOn=LEADER"
})
public final class AssetAltTextRepublishListener implements ResourceChangeListener, JobConsumer, Runnable,
        AssetAltTextRepublishCoordinator {

    static final String TOPIC = AssetAltTextRepublishJobConstants.TOPIC;
    private static final String PER_ASSET = "per:Asset";
    private static final String PN_ASSET_PATHS = AssetAltTextRepublishJobConstants.ASSET_PATHS;
    private static final String REPUBLISH_SERVICE = "asset-alttext-republish";
    private static final String REPORT_ROOT = "/var/asset-alttext-republish";
    private static final String PENDING_ROOT = REPORT_ROOT + "/pending";
    private static final String MANUAL_ROOT = REPORT_ROOT + "/manual";
    private static final String PN_COMPLETED = "completed";
    private static final String PN_REPUBLISHED_PAGES = "republishedPages";
    private static final String PN_REPUBLISHED_ASSETS = "republishedAssets";
    private static final String PN_UNPUBLISHED_PAGES = "unpublishedPages";
    private static final String SUPERSEDED_MESSAGE = "This image change was superseded by a newer save.";
    private static final String UNCONFIRMED_MESSAGE = "This image change could not be confirmed; publish the image and its pages manually if needed.";
    private static final String MISSING_ASSET_MESSAGE = "The image was removed before its alt text could be republished.";
    private static final String MISSING_CONTENT_MESSAGE = "The image content is unavailable; its changes were not republished.";
    private static final String NO_REPLICATION_SERVICE_MESSAGE = "Default replication service is unavailable.";
    private static final String REPUBLISH_FAILURE_MESSAGE = "Republish failed. Check the server log for details.";
    private static final long REPORT_RETENTION_MILLIS = 24L * 60 * 60 * 1000;
    private static final long EDITOR_SAVE_WINDOW_MILLIS = 24L * 60 * 60 * 1000;
    private static final long REPORT_CLEANUP_INTERVAL_MILLIS = 24L * 60 * 60 * 1000;

    private final Map<String, Map<String, EditorSave>> editorSaves = new ConcurrentHashMap<>();
    private final Set<String> queuedAutomaticAssets = ConcurrentHashMap.newKeySet();
    private volatile long lastReportCleanupAt;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    @org.osgi.service.component.annotations.Reference
    private JobManager jobManager;

    @org.osgi.service.component.annotations.Reference
    private ResourceResolverFactory resourceResolverFactory;

    @org.osgi.service.component.annotations.Reference
    private ReferenceLister referenceLister;

    @org.osgi.service.component.annotations.Reference
    private ReplicationsContainerWithDefault replications;

    @org.osgi.service.component.annotations.Reference
    private ReplicationWebhookService replicationWebhookService;

    @Override
    public void expectEditorSave(final String assetPath, final String reportToken, final String altText) {
        expectEditorSave(assetPath, reportToken, altText, false);
    }

    @Override
    public void expectDeferredEditorSave(final String assetPath, final String reportToken, final String altText) {
        expectEditorSave(assetPath, reportToken, altText, true);
    }

    private void expectEditorSave(final String assetPath, final String reportToken, final String altText,
            final boolean deferred) {
        final long createdAt = System.currentTimeMillis();
        persistPendingRequest(assetPath, reportToken, altText, createdAt, deferred);
        editorSaves.compute(assetPath, (key, pending) -> {
            final Map<String, EditorSave> saves = pending == null ? new ConcurrentHashMap<>() : pending;
            saves.put(reportToken, new EditorSave(altText, createdAt, deferred));
            return saves;
        });
    }

    @Override
    public boolean queueEditorSave(final String assetPath, final String reportToken) {
        final boolean[] queued = {false};
        editorSaves.computeIfPresent(assetPath, (key, pending) -> {
            final EditorSave save = pending.get(reportToken);
            if (save != null) {
                save.ready = true;
                if (!save.queued) {
                    try {
                        save.queued = enqueueClaimedRequest(assetPath, reportToken);
                    } catch (final RuntimeException e) {
                        logger.warn("Could not queue alt text republish job for {}", assetPath, e);
                    }
                }
                queued[0] = save.queued;
            }
            return pending;
        });
        return queued[0];
    }

    @Override
    public boolean republishDeferredEditorSave(final String assetPath, final String reportToken) {
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource pending = resolver.getResource(PENDING_ROOT + "/" + reportToken);
            if (pending == null || !assetPath.equals(pending.getValueMap().get("assetPath", String.class))
                    || !Boolean.TRUE.equals(pending.getValueMap().get("deferred", Boolean.class))
                    || Boolean.TRUE.equals(pending.getValueMap().get("discarded", Boolean.class))) return false;
            final Resource asset = resolver.getResource(assetPath);
            final Resource content = asset == null ? null : asset.getChild(JCR_CONTENT);
            if (content == null || !pending.getValueMap().get("expectedAlt", "")
                    .equals(content.getValueMap().get("alt", "").trim())) return false;
            final Map<String, EditorSave> saves = editorSaves.get(assetPath);
            final EditorSave save = saves == null ? null : saves.get(reportToken);
            if (save != null) {
                final boolean queued = queueEditorSave(assetPath, reportToken);
                if (queued) save.deferred = false;
                return queued;
            }
            return enqueueClaimedRequest(assetPath, reportToken);
        } catch (final LoginException e) {
            logger.error("Cannot republish deferred alt text for {}", assetPath, e);
            return false;
        }
    }

    @Override
    public boolean discardDeferredEditorSave(final String assetPath, final String reportToken) {
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource pending = resolver.getResource(PENDING_ROOT + "/" + reportToken);
            if (pending == null || !assetPath.equals(pending.getValueMap().get("assetPath", String.class))
                    || !Boolean.TRUE.equals(pending.getValueMap().get("deferred", Boolean.class))) return false;
            final Resource pendingRoot = resolver.getResource(PENDING_ROOT);
            for (final Resource entry : pendingRoot.getChildren()) {
                if (assetPath.equals(entry.getValueMap().get("assetPath", String.class))
                        && Boolean.TRUE.equals(entry.getValueMap().get("deferred", Boolean.class))) {
                    entry.adaptTo(org.apache.sling.api.resource.ModifiableValueMap.class).put("discarded", true);
                }
            }
            resolver.commit();
            return true;
        } catch (final LoginException | PersistenceException e) {
            logger.error("Cannot discard deferred alt text for {}", assetPath, e);
            return false;
        }
    }

    @Override
    public boolean cancelEditorSave(final String assetPath, final String reportToken) {
        final boolean[] observed = {false};
        editorSaves.computeIfPresent(assetPath, (key, pending) -> {
            final EditorSave save = pending.remove(reportToken);
            observed[0] = save != null && save.observed;
            return pending.isEmpty() ? null : pending;
        });
        deletePendingRequest(reportToken);
        return observed[0];
    }

    @Override
    public void abortEditorSave(final String assetPath, final String reportToken) {
        cancelEditorSave(assetPath, reportToken);
        if (assetPath == null || reportToken == null) return;
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource report = resolver.getResource(REPORT_ROOT + assetPath + "/" + reportToken);
            if (report != null && !report.getValueMap().get(PN_COMPLETED, false)) {
                resolver.delete(report);
                resolver.commit();
            }
        } catch (final LoginException | PersistenceException e) {
            logger.warn("Could not remove aborted alt text report {}", reportToken, e);
        }
    }

    private void persistPendingRequest(final String assetPath, final String reportToken, final String altText,
            final long createdAt, final boolean deferred) {
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            Resource root = resolver.getResource(REPORT_ROOT);
            if (root == null) root = resolver.create(resolver.getResource("/var"), "asset-alttext-republish", Collections.emptyMap());
            Resource pendingRoot = resolver.getResource(PENDING_ROOT);
            if (pendingRoot == null) pendingRoot = resolver.create(root, "pending", Collections.emptyMap());
            final Map<String, Object> properties = new HashMap<>();
            properties.put("assetPath", assetPath);
            properties.put("createdAt", createdAt);
            properties.put("expectedAlt", altText == null ? "" : altText.trim());
            properties.put("deferred", deferred);
            final Resource pending = resolver.create(pendingRoot, reportToken, properties);
            final Node node = pending.adaptTo(Node.class);
            if (node == null) throw new IllegalStateException("Cannot lock alt text republish request " + reportToken);
            node.addMixin("mix:lockable");
            final org.apache.sling.api.resource.ModifiableValueMap report = reportValues(resolver, assetPath, reportToken);
            if (report == null) throw new IllegalStateException("Cannot create alt text report " + reportToken);
            report.put("reportToken", reportToken);
            report.put("requestCreatedAt", createdAt);
            report.put(PN_COMPLETED, false);
            resolver.commit();
        } catch (final LoginException | PersistenceException | RepositoryException e) {
            throw new IllegalStateException("Cannot persist alt text republish request for " + assetPath, e);
        }
    }

    private void deletePendingRequest(final String reportToken) {
        if (reportToken == null) return;
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource pending = resolver.getResource(PENDING_ROOT + "/" + reportToken);
            if (pending != null) {
                resolver.delete(pending);
                resolver.commit();
            }
        } catch (final LoginException | PersistenceException e) {
            logger.warn("Could not remove pending alt text republish request {}", reportToken, e);
        }
    }

    private boolean enqueueClaimedRequest(final String assetPath, final String reportToken) {
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource pending = resolver.getResource(PENDING_ROOT + "/" + reportToken);
            return pending == null ? hasAcceptedJob(reportToken)
                    : enqueueClaimedRequest(resolver, pending, assetPath, reportToken, true);
        } catch (final LoginException | PersistenceException | RepositoryException e) {
            logger.warn("Could not claim alt text republish request {}", reportToken, e);
            return false;
        }
    }

    private boolean enqueueClaimedRequest(final ResourceResolver resolver, final Resource pending,
            final String assetPath, final String reportToken, final boolean editorCommitted)
            throws RepositoryException, PersistenceException {
        final Session session = resolver.adaptTo(Session.class);
        if (session == null) return false;
        try {
            session.getWorkspace().getLockManager().lock(pending.getPath(), false, true, 30, null);
        } catch (final javax.jcr.lock.LockException e) {
            return false;
        }
        if (editorCommitted) {
            final Node node = pending.adaptTo(Node.class);
            if (node == null) return false;
            node.setProperty("committed", true);
            resolver.commit();
        }
        if (hasAcceptedJob(reportToken)) {
            resolver.delete(pending);
            resolver.commit();
            return true;
        }
        final Map<String, Object> properties = new HashMap<>();
        properties.put(PN_ASSET_PATHS, new String[]{assetPath});
        properties.put(AssetAltTextRepublishJobConstants.REPORT_TOKEN, reportToken);
        if (jobManager.addJob(TOPIC, properties) == null) return false;
        resolver.delete(pending);
        resolver.commit();
        return true;
    }

    private static final class EditorSave {
        private final String altText;
        private final long createdAt;
        private final long expiresAt;
        private boolean observed;
        private boolean queued;
        private boolean ready;
        private boolean deferred;

        private EditorSave(final String altText, final long createdAt, final boolean deferred) {
            this.altText = altText == null ? "" : altText.trim();
            this.expiresAt = createdAt + EDITOR_SAVE_WINDOW_MILLIS;
            this.createdAt = createdAt;
            this.deferred = deferred;
        }
    }

    @Override
    public void onChange(final List<ResourceChange> changes) {
        final Set<String> assetPaths = new LinkedHashSet<>();
        for (final ResourceChange change : changes) {
            final String changedPath = change.getPath();
            if (changedPath == null || !changedPath.startsWith("/content/") || !changedPath.contains("/assets/")
                    || changedPath.contains("/renditions/")) {
                continue;
            }

            final int contentIndex = changedPath.indexOf("/jcr:content");
            if (contentIndex < 0 || !(changedPath.substring(contentIndex).equals("/jcr:content")
                    || changedPath.substring(contentIndex).equals("/jcr:content/metadata")
                    || changedPath.substring(contentIndex).startsWith("/jcr:content/metadata/"))) {
                continue;
            }
            final String assetPath = changedPath.substring(0, contentIndex);
            assetPaths.add(assetPath);
        }

        if (assetPaths.isEmpty()) return;
        final Set<String> changedAltTextAssets = new LinkedHashSet<>();
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            for (final String assetPath : assetPaths) {
                final Resource asset = resolver.getResource(assetPath);
                if (asset == null || !PER_ASSET.equals(asset.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) continue;
                try {
                    if (isAltTextChangeCandidate(asset)) {
                        final Resource content = asset.getChild(JCR_CONTENT);
                        final String currentAlt = content == null ? "" : content.getValueMap().get("alt", "");
                        if (hasDeferredRequest(resolver, assetPath, currentAlt)) continue;
                        final List<String> pendingTokens = pendingEditorSaveTokens(assetPath, currentAlt);
                        if (pendingTokens.isEmpty()) changedAltTextAssets.add(assetPath);
                        else for (final String token : pendingTokens) {
                            final Map<String, EditorSave> saves = editorSaves.get(assetPath);
                            final EditorSave save = saves == null ? null : saves.get(token);
                            if (save != null && !save.deferred) queueEditorSave(assetPath, token);
                        }
                    }
                } catch (final RepositoryException e) {
                    logger.warn("Cannot compare published alt text for {}", assetPath, e);
                }
            }
        } catch (final LoginException e) {
            logger.error("Cannot access image alt text republish service resolver", e);
            return;
        }
        if (!changedAltTextAssets.isEmpty()) {
            final Set<String> newlyQueuedAssets = new LinkedHashSet<>();
            for (final String assetPath : changedAltTextAssets) {
                if (queuedAutomaticAssets.add(assetPath)) newlyQueuedAssets.add(assetPath);
            }
            if (newlyQueuedAssets.isEmpty()) return;
            final Map<String, Object> properties = new HashMap<>();
            properties.put(PN_ASSET_PATHS, newlyQueuedAssets.toArray(new String[0]));
            if (jobManager.addJob(TOPIC, properties) == null) queuedAutomaticAssets.removeAll(newlyQueuedAssets);
        }
    }

    private List<String> pendingEditorSaveTokens(final String assetPath, final String currentAlt) {
        final List<String> tokens = new ArrayList<>();
        editorSaves.computeIfPresent(assetPath, (key, pending) -> {
            pending.entrySet().removeIf(entry -> !entry.getValue().queued
                    && System.currentTimeMillis() > entry.getValue().expiresAt);
            pending.forEach((token, save) -> {
                if (currentAlt != null && save.altText.equals(currentAlt.trim())) {
                    save.observed = true;
                    save.ready = true;
                }
                if (!save.deferred && (currentAlt == null ? save.ready : save.observed && save.altText.equals(currentAlt.trim()))) {
                    tokens.add(token);
                }
            });
            return pending.isEmpty() ? null : pending;
        });
        return tokens;
    }

    private boolean hasDeferredRequest(final ResourceResolver resolver, final String assetPath, final String currentAlt) {
        final Resource pendingRoot = resolver.getResource(PENDING_ROOT);
        if (pendingRoot == null) return false;
        for (final Resource request : pendingRoot.getChildren()) {
            if (Boolean.TRUE.equals(request.getValueMap().get("deferred", Boolean.class))
                    && assetPath.equals(request.getValueMap().get("assetPath", String.class))) {
                final Long createdAt = request.getValueMap().get("createdAt", Long.class);
                if (currentAlt.equals(request.getValueMap().get("expectedAlt", String.class))
                        || (createdAt != null && System.currentTimeMillis() - createdAt < 10_000)) return true;
            }
        }
        return false;
    }

    @Override
    public synchronized JobResult process(final org.apache.sling.event.jobs.Job job) {
        final String[] assetPaths = job.getProperty(PN_ASSET_PATHS, String[].class);
        final String reportToken = job.getProperty(AssetAltTextRepublishJobConstants.REPORT_TOKEN, String.class);
        if (assetPaths == null || assetPaths.length == 0) {
            return JobResult.OK;
        }

        final Set<String> publishedAssets = new LinkedHashSet<>();
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            if (reportToken != null && hasCompletedRepublishReport(resolver, assetPaths, reportToken)) {
                return JobResult.OK;
            }
            final Replication replication = replications.getDefault();
            if (replication == null) {
                logger.error("Cannot republish changed image and referencing pages: default replication service is unavailable");
                saveRetryableReport(resolver, assetPaths, reportToken, NO_REPLICATION_SERVICE_MESSAGE);
                return JobResult.FAILED;
            }

            final Set<String> pagesToPublish = new LinkedHashSet<>();
            final Set<String> assetsToPublish = new LinkedHashSet<>();
            final Set<String> unpublishedPages = new LinkedHashSet<>();
            final Map<String, Set<String>> assetsByPage = new HashMap<>();
            final Set<String> unpublishedAssets = new LinkedHashSet<>();
            for (final String assetPath : assetPaths) {
                final Resource asset = resolver.getResource(assetPath);
                if (asset == null || !PER_ASSET.equals(asset.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) {
                    unpublishedAssets.add(assetPath);
                    continue;
                }
                // Skip duplicate jobs if the current asset was already published.
                if (!isAltTextChangeCandidate(asset)) continue;
                final PerReplicable assetReplicable = asset.adaptTo(PerReplicable.class);
                final boolean assetPublished = assetReplicable != null && assetReplicable.isReplicated()
                        && hasPublishedVersion(assetReplicable.getMainResource());
                if (assetPublished) assetsToPublish.add(assetPath);
                else unpublishedAssets.add(assetPath);

                final List<Reference> references = referenceLister.getReferencedByList(asset);
                for (final Reference reference : references) {
                    final Resource page = findPage(reference.getResource());
                    if (page == null) {
                        continue;
                    }
                    if (AssetAltTextOverrideDetector.hasOverride(reference, page.getChild(JCR_CONTENT))) {
                        continue;
                    }
                    if (isCurrentlyPublishedAndFresh(page)) {
                        if (assetPublished) {
                            pagesToPublish.add(page.getPath());
                            assetsByPage.computeIfAbsent(page.getPath(), key -> new LinkedHashSet<>()).add(asset.getPath());
                        }
                    }
                    else if (isPublished(page)) {
                        unpublishedPages.add(page.getPath());
                    }
                }
            }
            for (final Set<String> referencedAssets : assetsByPage.values()) assetsToPublish.addAll(referencedAssets);

            if (assetsToPublish.isEmpty()) {
                saveReport(resolver, assetPaths, reportToken, new String[0],
                        unpublishedPages.toArray(new String[0]), unpublishedAssets.isEmpty() ? null
                                : "Image is unavailable or not published; referencing pages were not republished: " + String.join(", ", unpublishedAssets));
                return JobResult.OK;
            }

            final Map<String, String> previousPublishedVersions = new HashMap<>();
            boolean publishCompleted = false;
            try {
                final List<Resource> resourcesToPublish = new ArrayList<>();
                final Set<String> replicatedPaths = new LinkedHashSet<>();
                final Set<String> republishedPages = new LinkedHashSet<>();
                for (final String assetPath : assetsToPublish) {
                    final Resource asset = resolver.getResource(assetPath);
                    if (asset != null) {
                        createPublishedVersion(asset.adaptTo(PerReplicable.class).getMainResource(), previousPublishedVersions);
                        resourcesToPublish.add(asset);
                        replicatedPaths.add(asset.getPath());
                    }
                }
                for (final String pagePath : pagesToPublish) {
                    final Resource page = resolver.getResource(pagePath);
                    if (page != null && isCurrentlyPublishedAndFresh(page)) {
                        final Resource content = page.getChild(JCR_CONTENT);
                        createPublishedVersion(content, previousPublishedVersions);
                        resourcesToPublish.add(page);
                        replicatedPaths.add(page.getPath());
                        republishedPages.add(page.getPath());
                    }
                }
                if (resourcesToPublish.isEmpty()) {
                    saveReport(resolver, assetPaths, reportToken, new String[0], unpublishedPages.toArray(new String[0]));
                    return JobResult.OK;
                }

                final String tenant = getTenantNameFromResource(resourcesToPublish.get(0));
                final String[] paths = replicatedPaths.toArray(new String[0]);
                replicationWebhookService.beforePublish(tenant, paths);
                final List<Resource> replicated = replication.replicate(resourcesToPublish);
                publishCompleted = true;
                final Set<String> replicatedPagePaths = new LinkedHashSet<>();
                for (final String pagePath : republishedPages) {
                    if (wasReplicated(replication, replicated, resolver.getResource(pagePath))) replicatedPagePaths.add(pagePath);
                    else {
                        unpublishedPages.add(pagePath);
                        restorePublishedLabel(resolver, previousPublishedVersions, pagePath + "/" + JCR_CONTENT);
                    }
                }
                boolean assetReplicated = true;
                for (final String assetPath : assetsToPublish) {
                    if (wasReplicated(replication, replicated, resolver.getResource(assetPath))) {
                        publishedAssets.add(assetPath);
                    } else {
                        assetReplicated = false;
                        restorePublishedLabel(resolver, previousPublishedVersions, assetPath + "/" + JCR_CONTENT);
                    }
                }
                if (!assetReplicated) unpublishedPages.addAll(republishedPages);
                final String replicationWarning = !assetReplicated
                        ? "The image was not included in the replication result; verify the published pages manually."
                        : replicatedPagePaths.size() == republishedPages.size() ? null
                                : "Some pages were not included in the replication result; publish them manually.";
                String webhookWarning = replicationWarning;
                try {
                    replicationWebhookService.afterPublish(tenant, paths);
                } catch (final Replication.ReplicationException | RuntimeException e) {
                    logger.error("Pages were republished, but the post-publish webhook failed", e);
                    final String message = "Post-publish webhook failed. Check the server log.";
                    webhookWarning = webhookWarning == null ? message : webhookWarning + " " + message;
                }
                saveReport(resolver, assetPaths, reportToken,
                        assetReplicated ? replicatedPagePaths.toArray(new String[0]) : new String[0],
                        unpublishedPages.toArray(new String[0]), publishedAssets.toArray(new String[0]), webhookWarning);
                logger.info("Republished {} already-published page(s) and {} image asset(s) after image changes",
                        pagesToPublish.size(), assetsToPublish.size());
                return JobResult.OK;
            } finally {
                if (!publishCompleted) {
                    restorePublishedLabels(resolver, previousPublishedVersions);
                }
            }
        } catch (final LoginException e) {
            logger.error("Cannot access image alt text republish service resolver", e);
            return JobResult.FAILED;
        } catch (final Replication.ReplicationException | RepositoryException | PersistenceException | RuntimeException e) {
            logger.error("Failed to republish changed image and referencing pages", e);
            saveFailureReport(assetPaths, reportToken, REPUBLISH_FAILURE_MESSAGE);
            return JobResult.FAILED;
        } finally {
            if (reportToken == null) {
                for (final String assetPath : assetPaths) queuedAutomaticAssets.remove(assetPath);
            }
            if (reportToken != null) {
                for (final String assetPath : assetPaths) {
                    final boolean observed = cancelEditorSave(assetPath, reportToken);
                    if (observed && publishedAssets.contains(assetPath)) enqueueIfAltTextStillChanged(assetPath);
                }
            }
        }
    }

    private boolean hasCompletedRepublishReport(final ResourceResolver resolver, final String[] assetPaths,
            final String reportToken) {
        for (final String assetPath : assetPaths) {
            final Resource report = resolver.getResource(REPORT_ROOT + assetPath + "/" + reportToken);
            if (report == null || !report.getValueMap().get(PN_COMPLETED, false)) return false;
        }
        return true;
    }

    private void enqueueIfAltTextStillChanged(final String assetPath) {
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource asset = resolver.getResource(assetPath);
            if (asset != null && isAltTextChangeCandidate(asset)) {
                final Map<String, Object> properties = new HashMap<>();
                properties.put(PN_ASSET_PATHS, new String[]{assetPath});
                jobManager.addJob(TOPIC, properties);
            }
        } catch (final LoginException | RepositoryException | RuntimeException e) {
            logger.error("Cannot check for a newer alt text change after republishing {}", assetPath, e);
        }
    }

    private boolean wasReplicated(final Replication replication, final List<Resource> replicated, final Resource source) {
        if (replicated == null || source == null) return false;
        final String expectedPath = replication.getReplicatedPath(source);
        if (expectedPath == null) return false;
        for (final Resource resource : replicated) {
            if (resource != null && expectedPath.equals(resource.getPath())) return true;
        }
        return false;
    }

    private void restorePublishedLabel(final ResourceResolver resolver, final Map<String, String> previousVersions,
            final String contentPath) {
        final String previousVersion = previousVersions.get(contentPath);
        if (previousVersion == null) return;
        final Session session = resolver.adaptTo(Session.class);
        if (session == null) return;
        try {
            session.getWorkspace().getVersionManager().getVersionHistory(contentPath)
                    .addVersionLabel(previousVersion, PUBLISHED_LABEL, true);
        } catch (final RepositoryException e) {
            logger.error("Cannot restore Published label for resource omitted from replication: {}", contentPath, e);
        }
    }

    private void saveFailureReport(final String[] assetPaths, final String reportToken, final String error) {
        try {
            final Map<String, Object> authInfo = new HashMap<>();
            authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
            try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
                saveRetryableReport(resolver, assetPaths, reportToken, error);
            }
        } catch (final LoginException | PersistenceException reportFailure) {
            logger.error("Could not save image alt text republish failure report", reportFailure);
        }
    }

    @Override
    public void run() {
        for (final String assetPath : editorSaves.keySet()) {
            for (final String token : pendingEditorSaveTokens(assetPath, null)) queueEditorSave(assetPath, token);
        }
        retryPersistedRequests();
        final long now = System.currentTimeMillis();
        if (now - lastReportCleanupAt < REPORT_CLEANUP_INTERVAL_MILLIS) return;
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource root = resolver.getResource(REPORT_ROOT);
            if (root == null) {
                lastReportCleanupAt = now;
                return;
            }
            final List<Resource> expired = new ArrayList<>();
            collectExpiredReports(resolver, root, now - REPORT_RETENTION_MILLIS, expired);
            for (final Resource report : expired) resolver.delete(report);
            if (!expired.isEmpty()) resolver.commit();
            lastReportCleanupAt = now;
        } catch (final LoginException | PersistenceException e) {
            logger.error("Could not clean up expired image alt text republish reports", e);
        }
    }

    private void retryPersistedRequests() {
        final Map<String, Object> authInfo = new HashMap<>();
        authInfo.put(ResourceResolverFactory.SUBSERVICE, REPUBLISH_SERVICE);
        try (ResourceResolver resolver = resourceResolverFactory.getServiceResourceResolver(authInfo)) {
            final Resource pendingRoot = resolver.getResource(PENDING_ROOT);
            if (pendingRoot == null) return;
            final List<Resource> requests = new ArrayList<>();
            for (final Resource pending : pendingRoot.getChildren()) requests.add(pending);
            for (final Resource pending : requests) {
                try {
                    final String assetPath = pending.getValueMap().get("assetPath", String.class);
                    final String token = pending.getName();
                    final Long createdAt = pending.getValueMap().get("createdAt", Long.class);
                    if (assetPath == null || !assetPath.startsWith("/content/") || !assetPath.contains("/assets/")) {
                        logger.warn("Removing pending alt text republish request with invalid asset path: {}", pending.getPath());
                        resolver.delete(pending);
                        resolver.commit();
                        continue;
                    }
                    final Resource report = resolver.getResource(REPORT_ROOT + assetPath + "/" + token);
                    if (report != null && report.getValueMap().get(PN_COMPLETED, false)) {
                        resolver.delete(pending);
                        resolver.commit();
                        continue;
                    }
                    if (createdAt == null) {
                        logger.warn("Pending alt text republish request has no creation time: {}", pending.getPath());
                        saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                UNCONFIRMED_MESSAGE);
                        resolver.delete(pending);
                        resolver.commit();
                        continue;
                    }
                    if (Boolean.TRUE.equals(pending.getValueMap().get("deferred", Boolean.class))) {
                        final boolean discarded = Boolean.TRUE.equals(pending.getValueMap().get("discarded", Boolean.class));
                        if (System.currentTimeMillis() - createdAt >= REPORT_RETENTION_MILLIS) {
                            final Resource deferredAsset = resolver.getResource(assetPath);
                            final Resource deferredContent = deferredAsset == null ? null : deferredAsset.getChild(JCR_CONTENT);
                            final String expectedAlt = pending.getValueMap().get("expectedAlt", String.class);
                            final boolean superseded = deferredContent == null || expectedAlt == null
                                    || !expectedAlt.equals(deferredContent.getValueMap().get("alt", "").trim());
                            if (discarded || superseded) {
                                saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                        discarded ? "Republishing was skipped by the editor." : SUPERSEDED_MESSAGE);
                                resolver.delete(pending);
                                resolver.commit();
                            }
                        }
                        continue;
                    }
                    if (System.currentTimeMillis() - createdAt < 10_000) continue;
                    if (editorSaves.containsKey(assetPath)
                            && editorSaves.get(assetPath).containsKey(token)) continue;
                    final Resource asset = resolver.getResource(assetPath);
                    if (asset == null) {
                        saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                MISSING_ASSET_MESSAGE);
                        resolver.delete(pending);
                        resolver.commit();
                        continue;
                    }
                    final String expectedAlt = pending.getValueMap().get("expectedAlt", String.class);
                    final Resource content = asset.getChild(JCR_CONTENT);
                    if (content == null) {
                        saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                MISSING_CONTENT_MESSAGE);
                        resolver.delete(pending);
                        resolver.commit();
                        continue;
                    }
                    final boolean timedOut = System.currentTimeMillis() - createdAt >= EDITOR_SAVE_WINDOW_MILLIS;
                    if (expectedAlt == null) {
                        if (timedOut) {
                            saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                    UNCONFIRMED_MESSAGE);
                            resolver.delete(pending);
                            resolver.commit();
                        }
                        continue;
                    }
                    if (!expectedAlt.equals(content.getValueMap().get("alt", "").trim())) {
                        final boolean committed = pending.getValueMap().get("committed", false);
                        if (committed && hasNewerCompletedReport(resolver, assetPath, token, createdAt)) {
                            saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                    SUPERSEDED_MESSAGE);
                            resolver.delete(pending);
                            resolver.commit();
                        } else if (timedOut) {
                            saveReport(resolver, new String[]{assetPath}, token, new String[0], new String[0],
                                    UNCONFIRMED_MESSAGE);
                            resolver.delete(pending);
                            resolver.commit();
                        }
                        continue;
                    }
                    enqueueClaimedRequest(resolver, pending, assetPath, token, false);
                } catch (final PersistenceException | RepositoryException | RuntimeException e) {
                    logger.error("Could not retry pending alt text republish request {}", pending.getPath(), e);
                    resolver.revert();
                }
            }
        } catch (final LoginException | RuntimeException e) {
            logger.error("Could not retry persisted alt text republish requests", e);
        }
    }

    private boolean hasNewerCompletedReport(final ResourceResolver resolver, final String assetPath,
            final String reportToken, final long createdAt) {
        final Resource reports = resolver.getResource(REPORT_ROOT + assetPath);
        if (reports == null) return false;
        for (final Resource report : reports.getChildren()) {
            if (reportToken.equals(report.getName()) || !report.getValueMap().get(PN_COMPLETED, false)) continue;
            final Long requestCreatedAt = report.getValueMap().get("requestCreatedAt", Long.class);
            if (requestCreatedAt != null && requestCreatedAt > createdAt) return true;
        }
        return false;
    }

    private boolean hasAcceptedJob(final String reportToken) {
        final Map<String, Object> filter = new HashMap<>();
        filter.put(AssetAltTextRepublishJobConstants.REPORT_TOKEN, reportToken);
        final java.util.Collection<Job> queued = jobManager.findJobs(JobManager.QueryType.QUEUED, TOPIC, 1, filter);
        if (queued != null && !queued.isEmpty()) return true;
        final java.util.Collection<Job> active = jobManager.findJobs(JobManager.QueryType.ACTIVE, TOPIC, 1, filter);
        return active != null && !active.isEmpty();
    }

    private void collectExpiredReports(final ResourceResolver resolver, final Resource resource, final long cutoff,
            final List<Resource> expired) {
        final String reportToken = resource.getValueMap().get("reportToken", String.class);
        if (reportToken != null) {
            final boolean completed = resource.getValueMap().get(PN_COMPLETED, false);
            final Long createdAt = resource.getValueMap().get(
                    completed ? "createdAt" : "requestCreatedAt", Long.class);
            if (createdAt != null && createdAt < cutoff && (completed
                    || (resolver.getResource(PENDING_ROOT + "/" + reportToken) == null
                    && !hasAcceptedJob(reportToken)))) expired.add(resource);
            return;
        }
        for (final Resource child : resource.getChildren()) collectExpiredReports(resolver, child, cutoff, expired);
    }

    private boolean isAltTextChangeCandidate(final Resource asset) throws RepositoryException {
        final Resource content = asset.getChild(JCR_CONTENT);
        if (content == null) {
            return false;
        }
        final Node current = content.adaptTo(Node.class);
        if (current == null) {
            return false;
        }
        final javax.jcr.version.Version publishedVersion = current.getSession().getWorkspace().getVersionManager()
                .getVersionHistory(current.getPath()).getVersionByLabel(PUBLISHED_LABEL);
        if (publishedVersion == null) return false;
        final Node published = publishedVersion.getFrozenNode();
        return !assetText(current).equals(assetText(published));
    }

    private String assetText(final Node node) throws RepositoryException {
        final List<String> values = new ArrayList<>();
        try {
            collectAssetProperties(node, "", values);
        } catch (final RuntimeException iteratorFailure) {
            // Fallback to comparing alt directly if properties cannot be enumerated.
            if (node.hasProperty("alt")) return "alt=" + node.getProperty("alt").getString();
            throw iteratorFailure;
        }
        Collections.sort(values);
        return String.join("\n", values);
    }

    private void collectAssetProperties(final Node node, final String prefix, final List<String> values)
            throws RepositoryException {
        final javax.jcr.PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            final javax.jcr.Property property = properties.nextProperty();
            final String name = property.getName();
            if (isSystemAssetProperty(name) || property.getType() == javax.jcr.PropertyType.BINARY) continue;
            final StringBuilder value = new StringBuilder(prefix).append(name).append('=');
            if (property.isMultiple()) {
                for (final javax.jcr.Value item : property.getValues()) value.append(item.getString()).append('\u0000');
            } else {
                value.append(property.getString());
            }
            values.add(value.toString());
        }
        final javax.jcr.NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            final Node child = children.nextNode();
            if ("renditions".equals(child.getName())) continue;
            collectAssetProperties(child, prefix + child.getName() + "/", values);
        }
    }

    private boolean isSystemAssetProperty(final String name) {
        return "jcr:primaryType".equals(name) || "jcr:mixinTypes".equals(name)
                || "jcr:created".equals(name) || "jcr:createdBy".equals(name)
                || "jcr:lastModified".equals(name) || "jcr:lastModifiedBy".equals(name)
                || "jcr:uuid".equals(name) || name.startsWith("cq:lastReplicated")
                || name.startsWith("cq:lastReplication") || name.startsWith("per:");
    }

    private Resource findPage(final Resource resource) {
        Resource current = resource;
        while (current != null) {
            final String primaryType = current.getValueMap().get(JCR_PRIMARY_TYPE, String.class);
            if (PAGE_PRIMARY_TYPE.equals(primaryType)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private boolean isCurrentlyPublishedAndFresh(final Resource page) throws RepositoryException {
        final Resource content = page.getChild(JCR_CONTENT);
        if (content == null || !PAGE_CONTENT_TYPE.equals(content.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) {
            return false;
        }
        final PerReplicable replicable = content.adaptTo(PerReplicable.class);
        if (replicable == null || !replicable.isReplicated() || replicable.isStale()) {
            return false;
        }

        final javax.jcr.Session session = page.getResourceResolver().adaptTo(javax.jcr.Session.class);
        if (session == null) {
            return false;
        }
        return hasPublishedVersion(content);
    }

    private boolean isPublished(final Resource page) throws RepositoryException {
        final Resource content = page.getChild(JCR_CONTENT);
        if (content == null || !PAGE_CONTENT_TYPE.equals(content.getValueMap().get(JCR_PRIMARY_TYPE, String.class))) return false;
        final PerReplicable replicable = content.adaptTo(PerReplicable.class);
        return (replicable != null && replicable.isReplicated()) || hasPublishedVersion(content);
    }

    private void saveReport(final ResourceResolver resolver, final String[] assetPaths, final String reportToken,
            final String[] republishedPages,
            final String[] unpublishedPages) throws PersistenceException {
        saveReport(resolver, assetPaths, reportToken, republishedPages, unpublishedPages, new String[0], null);
    }

    private void saveReport(final ResourceResolver resolver, final String[] assetPaths, final String reportToken,
            final String[] republishedPages, final String[] unpublishedPages, final String error) throws PersistenceException {
        saveReport(resolver, assetPaths, reportToken, republishedPages, unpublishedPages, new String[0], error);
    }

    private void saveReport(final ResourceResolver resolver, final String[] assetPaths, final String reportToken,
            final String[] republishedPages, final String[] unpublishedPages, final String[] republishedAssets,
            final String error) throws PersistenceException {
        if (reportToken == null) return;
        for (final String assetPath : assetPaths) {
            final org.apache.sling.api.resource.ModifiableValueMap values = reportValues(resolver, assetPath, reportToken);
            if (values == null) continue;
            values.put("reportToken", reportToken);
            values.put(PN_COMPLETED, true);
            values.put("retrying", false);
            values.put(PN_REPUBLISHED_PAGES, republishedPages);
            values.put(PN_REPUBLISHED_ASSETS, republishedAssets);
            values.put(PN_UNPUBLISHED_PAGES, unpublishedPages);
            values.put("error", error == null ? "" : error);
            values.put("createdAt", System.currentTimeMillis());
        }
        if (unpublishedPages.length > 0 && assetPaths.length > 0) {
            Resource manualRoot = resolver.getResource(MANUAL_ROOT);
            if (manualRoot == null) {
                final Resource reportRoot = resolver.getResource(REPORT_ROOT);
                if (reportRoot != null) manualRoot = resolver.create(reportRoot, "manual", Collections.emptyMap());
            }
            if (manualRoot != null) {
                final Map<String, Object> manualValues = new HashMap<>();
                manualValues.put("assetPath", assetPaths[0]);
                manualValues.put("blockedPages", unpublishedPages);
                manualValues.put("createdAt", System.currentTimeMillis());
                final Resource existing = resolver.getResource(MANUAL_ROOT + "/" + reportToken);
                if (existing == null) resolver.create(manualRoot, reportToken, manualValues);
                else existing.adaptTo(org.apache.sling.api.resource.ModifiableValueMap.class).putAll(manualValues);
            }
        }
        resolver.commit();
    }

    private void saveRetryableReport(final ResourceResolver resolver, final String[] assetPaths,
            final String reportToken, final String error) throws PersistenceException {
        if (reportToken == null) return;
        for (final String assetPath : assetPaths) {
            final org.apache.sling.api.resource.ModifiableValueMap values = reportValues(resolver, assetPath, reportToken);
            if (values == null) continue;
            values.put("reportToken", reportToken);
            values.put(PN_COMPLETED, false);
            values.put("retrying", true);
            values.put(PN_REPUBLISHED_PAGES, new String[0]);
            values.put(PN_UNPUBLISHED_PAGES, new String[0]);
            values.put("error", error);
        }
        resolver.commit();
    }

    private org.apache.sling.api.resource.ModifiableValueMap reportValues(final ResourceResolver resolver,
            final String assetPath, final String reportToken) throws PersistenceException {
        final String reportPath = REPORT_ROOT + assetPath + "/" + reportToken;
        Resource report = resolver.getResource(reportPath);
        if (report == null) {
            Resource parent = resolver.getResource(REPORT_ROOT);
            if (parent == null) parent = resolver.create(resolver.getResource("/var"), "asset-alttext-republish", new HashMap<>());
            for (final String segment : assetPath.split("/")) {
                if (segment.isEmpty()) continue;
                Resource child = parent.getChild(segment);
                if (child == null) child = resolver.create(parent, segment, new HashMap<>());
                parent = child;
            }
            report = resolver.create(parent, reportToken, new HashMap<>());
        }
        return report.adaptTo(org.apache.sling.api.resource.ModifiableValueMap.class);
    }

    private boolean hasPublishedVersion(final Resource resource) throws RepositoryException {
        if (resource == null) {
            return false;
        }
        final Session session = resource.getResourceResolver().adaptTo(Session.class);
        if (session == null) {
            return false;
        }
        try {
            final VersionManager versionManager = session.getWorkspace().getVersionManager();
            final VersionHistory history = versionManager.getVersionHistory(resource.getPath());
            for (final String label : history.getVersionLabels()) {
                if (PUBLISHED_LABEL.equals(label)) {
                    return true;
                }
            }
        } catch (RepositoryException e) {
            // Skip pages without version history. Continue processing other pages.
            logger.warn("Could not inspect Published version of {}", resource.getPath(), e);
        }
        return false;
    }

    private void createPublishedVersion(final Resource resource, final Map<String, String> previousVersions)
            throws RepositoryException, PersistenceException {
        if (resource == null) {
            return;
        }
        final Node node = resource.adaptTo(Node.class);
        if (node == null) {
            throw new RepositoryException("Cannot version resource " + resource.getPath());
        }
        final VersionManager versionManager = node.getSession().getWorkspace().getVersionManager();
        VersionHistory history;
        try {
            history = versionManager.getVersionHistory(node.getPath());
        } catch (final RepositoryException notVersionable) {
            node.addMixin("mix:versionable");
            resource.getResourceResolver().commit();
            history = node.getSession().getWorkspace().getVersionManager().getVersionHistory(node.getPath());
        }

        if (versionManager.isCheckedOut(node.getPath())) {
            previousVersions.put(node.getPath(), history.getVersionByLabel(PUBLISHED_LABEL).getName());
            final javax.jcr.version.Version version = versionManager.checkin(node.getPath());
            versionManager.checkout(node.getPath());
            history.addVersionLabel(version.getName(), PUBLISHED_LABEL, true);
        }
    }

    private void restorePublishedLabels(final ResourceResolver resolver, final Map<String, String> previousVersions) {
        final Session session = resolver.adaptTo(Session.class);
        if (session == null) return;
        for (final Map.Entry<String, String> entry : previousVersions.entrySet()) {
            try {
                session.getWorkspace().getVersionManager().getVersionHistory(entry.getKey())
                        .addVersionLabel(entry.getValue(), PUBLISHED_LABEL, true);
            } catch (final RepositoryException e) {
                logger.error("Cannot restore Published label after failed republish of {}", entry.getKey(), e);
            }
        }
    }
}
