package com.peregrine.replication.impl;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.JobManager;
import org.apache.sling.event.jobs.consumer.JobConsumer;
import com.peregrine.replication.Replication;
import com.peregrine.replication.PerReplicable;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.Session;
import javax.jcr.RepositoryException;
import javax.jcr.Workspace;
import javax.jcr.version.Version;
import javax.jcr.version.VersionHistory;
import javax.jcr.version.VersionManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static com.peregrine.commons.util.PerConstants.PUBLISHED_LABEL;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Matchers.*;

public class AssetAltTextRepublishListenerTest {
    private static final String ASSET = "/content/site/assets/image.jpg";

    @Test
    public void terminalReportWithoutRepublishedPagesIsNotProcessedAgain() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource report = mock(Resource.class);
        ValueMap values = mock(ValueMap.class);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/token")).thenReturn(report);
        when(report.getValueMap()).thenReturn(values);
        when(values.get("completed", false)).thenReturn(true);
        when(values.get("error", "")).thenReturn("Image is unavailable or not published; referencing pages were not republished");
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("hasCompletedRepublishReport",
                ResourceResolver.class, String[].class, String.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(listener, resolver, new String[]{ASSET}, "token"));
        when(values.get("completed", false)).thenReturn(false);
        assertFalse((Boolean) method.invoke(listener, resolver, new String[]{ASSET}, "token"));
    }

    @Test
    public void retryableFailureDoesNotMarkReportCompleted() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource report = mock(Resource.class);
        ModifiableValueMap values = mock(ModifiableValueMap.class);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/token")).thenReturn(report);
        when(report.adaptTo(ModifiableValueMap.class)).thenReturn(values);
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("saveRetryableReport",
                ResourceResolver.class, String[].class, String.class, String.class);
        method.setAccessible(true);

        method.invoke(listener, resolver, new String[]{ASSET}, "token", "Temporary failure");

        verify(values).put("completed", false);
        verify(values).put("retrying", true);
        verify(values).put("error", "Temporary failure");
        verify(resolver).commit();
    }

    @Test
    public void publishedVersionStillCountsWhenReplicationReferenceWasCleared() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        Resource page = mock(Resource.class);
        Resource content = mock(Resource.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        ValueMap values = mock(ValueMap.class);
        PerReplicable replicable = mock(PerReplicable.class);
        Session session = mock(Session.class);
        Workspace workspace = mock(Workspace.class);
        VersionManager manager = mock(VersionManager.class);
        VersionHistory history = mock(VersionHistory.class);
        when(page.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(values);
        when(values.get("jcr:primaryType", String.class)).thenReturn("per:PageContent");
        when(content.adaptTo(PerReplicable.class)).thenReturn(replicable);
        when(replicable.isReplicated()).thenReturn(false);
        when(content.getResourceResolver()).thenReturn(resolver);
        when(content.getPath()).thenReturn("/content/site/pages/home/jcr:content");
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getWorkspace()).thenReturn(workspace);
        when(workspace.getVersionManager()).thenReturn(manager);
        when(manager.getVersionHistory(content.getPath())).thenReturn(history);
        when(history.getVersionLabels()).thenReturn(new String[] {PUBLISHED_LABEL});
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("isPublished", Resource.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(listener, page));
    }

    @Test
    public void missingVersionHistoryDoesNotAbortPageClassification() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        Resource page = mock(Resource.class);
        Resource content = mock(Resource.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        ValueMap values = mock(ValueMap.class);
        Session session = mock(Session.class, RETURNS_DEEP_STUBS);
        when(page.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(values);
        when(values.get("jcr:primaryType", String.class)).thenReturn("per:PageContent");
        when(content.getResourceResolver()).thenReturn(resolver);
        when(content.getPath()).thenReturn("/content/site/pages/unpublished/jcr:content");
        when(resolver.adaptTo(Session.class)).thenReturn(session);
        when(session.getWorkspace().getVersionManager().getVersionHistory(content.getPath()))
                .thenThrow(new RepositoryException("No version history"));
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("isPublished", Resource.class);
        method.setAccessible(true);

        assertFalse((Boolean) method.invoke(listener, page));
    }

    @Test
    public void replicatedPageWithoutPublishedVersionIsStillReported() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        Resource page = mock(Resource.class);
        Resource content = mock(Resource.class);
        ValueMap values = mock(ValueMap.class);
        PerReplicable replicable = mock(PerReplicable.class);
        when(page.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(values);
        when(values.get("jcr:primaryType", String.class)).thenReturn("per:PageContent");
        when(content.adaptTo(PerReplicable.class)).thenReturn(replicable);
        when(replicable.isReplicated()).thenReturn(true);
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("isPublished", Resource.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(listener, page));
    }

    @Test
    public void failedPendingRequestDoesNotBlockFollowingRequests() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pendingRoot = mock(Resource.class);
        Resource broken = mock(Resource.class);
        Resource next = mock(Resource.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(pendingRoot.getChildren()).thenReturn(Arrays.asList(broken, next));
        when(broken.getValueMap()).thenThrow(new IllegalStateException("broken record"));
        when(next.getValueMap()).thenReturn(mock(ValueMap.class));

        listener.run();

        verify(resolver).revert();
        verify(resolver).delete(next);
        verify(resolver).commit();
    }

    @Test
    public void removesPendingRequestWithoutAssetPath() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pendingRoot = mock(Resource.class);
        Resource pending = mock(Resource.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(pendingRoot.getChildren()).thenReturn(Collections.singletonList(pending));
        when(pending.getValueMap()).thenReturn(mock(ValueMap.class));

        listener.run();

        verify(resolver).delete(pending);
        verify(resolver).commit();
    }

    @Test
    public void reportsPendingRequestWithoutCreationTime() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pendingRoot = mock(Resource.class);
        Resource pending = mock(Resource.class);
        Resource report = mock(Resource.class);
        ValueMap pendingValues = mock(ValueMap.class);
        ValueMap status = mock(ValueMap.class);
        ModifiableValueMap reportValues = mock(ModifiableValueMap.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(pendingRoot.getChildren()).thenReturn(Collections.singletonList(pending));
        when(pending.getName()).thenReturn("no-time-token");
        when(pending.getValueMap()).thenReturn(pendingValues);
        when(pendingValues.get("assetPath", String.class)).thenReturn(ASSET);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/no-time-token"))
                .thenReturn(report);
        when(report.getValueMap()).thenReturn(status);
        when(status.get("completed", false)).thenReturn(false);
        when(report.adaptTo(ModifiableValueMap.class)).thenReturn(reportValues);

        listener.run();

        verify(reportValues).put("completed", true);
        verify(reportValues).put(eq("error"), contains("could not be confirmed"));
        verify(resolver).delete(pending);

        reset(reportValues);
        when(status.get("completed", false)).thenReturn(true);
        listener.run();
        verifyZeroInteractions(reportValues);
    }

    @Test
    public void settlesLegacyRequestWithoutExpectedAltAfterRecoveryWindow() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pendingRoot = mock(Resource.class);
        Resource pending = mock(Resource.class);
        Resource asset = mock(Resource.class);
        Resource content = mock(Resource.class);
        Resource report = mock(Resource.class);
        ValueMap pendingValues = mock(ValueMap.class);
        ValueMap status = mock(ValueMap.class);
        ModifiableValueMap reportValues = mock(ModifiableValueMap.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(pendingRoot.getChildren()).thenReturn(Collections.singletonList(pending));
        when(pending.getName()).thenReturn("legacy-token");
        when(pending.getValueMap()).thenReturn(pendingValues);
        when(pendingValues.get("assetPath", String.class)).thenReturn(ASSET);
        when(pendingValues.get("createdAt", Long.class))
                .thenReturn(System.currentTimeMillis() - 25L * 60 * 60 * 1000);
        when(resolver.getResource(ASSET)).thenReturn(asset);
        when(asset.getChild("jcr:content")).thenReturn(content);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/legacy-token"))
                .thenReturn(report);
        when(report.getValueMap()).thenReturn(status);
        when(status.get("completed", false)).thenReturn(false);
        when(report.adaptTo(ModifiableValueMap.class)).thenReturn(reportValues);

        listener.run();

        verify(reportValues).put("completed", true);
        verify(reportValues).put(eq("error"), contains("could not be confirmed"));
        verify(resolver).delete(pending);
    }

    @Test
    public void reportsMissingAssetInsteadOfLeavingRequestPending() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pendingRoot = mock(Resource.class);
        Resource pending = mock(Resource.class);
        Resource report = mock(Resource.class);
        ValueMap pendingValues = mock(ValueMap.class);
        ModifiableValueMap reportValues = mock(ModifiableValueMap.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(pendingRoot.getChildren()).thenReturn(Collections.singletonList(pending));
        when(pending.getName()).thenReturn("missing-asset-token");
        when(pending.getValueMap()).thenReturn(pendingValues);
        when(pendingValues.get("assetPath", String.class)).thenReturn(ASSET);
        when(pendingValues.get("createdAt", Long.class)).thenReturn(System.currentTimeMillis() - 60_000);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/missing-asset-token"))
                .thenReturn(report);
        ValueMap status = mock(ValueMap.class);
        when(report.getValueMap()).thenReturn(status);
        when(status.get("completed", false)).thenReturn(false);
        when(report.adaptTo(ModifiableValueMap.class)).thenReturn(reportValues);

        listener.run();

        verify(reportValues).put("completed", true);
        verify(reportValues).put(eq("error"), contains("removed"));
        verify(resolver).delete(pending);
    }

    @Test
    public void abortRemovesIncompleteReportMarker() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pending = mock(Resource.class);
        Resource report = mock(Resource.class);
        ValueMap reportValues = mock(ValueMap.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending/aborted-token")).thenReturn(pending);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/aborted-token"))
                .thenReturn(report);
        when(report.getValueMap()).thenReturn(reportValues);
        when(reportValues.get("completed", false)).thenReturn(false);

        listener.abortEditorSave(ASSET, "aborted-token");

        verify(resolver).delete(pending);
        verify(resolver).delete(report);
    }

    @Test
    public void cleanupFindsOrphanedIncompleteReportMarker() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        inject(listener, "jobManager", mock(JobManager.class));
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource root = mock(Resource.class);
        Resource report = mock(Resource.class);
        ValueMap rootValues = mock(ValueMap.class);
        ValueMap reportValues = mock(ValueMap.class);
        when(root.getValueMap()).thenReturn(rootValues);
        when(root.getChildren()).thenReturn(Collections.singletonList(report));
        when(report.getValueMap()).thenReturn(reportValues);
        when(reportValues.get("reportToken", String.class)).thenReturn("orphan-token");
        when(reportValues.get("completed", false)).thenReturn(false);
        when(reportValues.get("requestCreatedAt", Long.class)).thenReturn(1L);
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("collectExpiredReports",
                ResourceResolver.class, Resource.class, long.class, java.util.List.class);
        method.setAccessible(true);
        java.util.List<Resource> expired = new java.util.ArrayList<>();

        method.invoke(listener, resolver, root, 2L, expired);

        assertEquals(Collections.singletonList(report), expired);
    }

    @Test
    public void findsNewerCompletedReportForSupersededRequest() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource reports = mock(Resource.class);
        Resource newerReport = mock(Resource.class);
        ValueMap values = mock(ValueMap.class);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET)).thenReturn(reports);
        when(reports.getChildren()).thenReturn(Collections.singletonList(newerReport));
        when(newerReport.getName()).thenReturn("newer-token");
        when(newerReport.getValueMap()).thenReturn(values);
        when(values.get("completed", false)).thenReturn(true);
        when(values.get("requestCreatedAt", Long.class)).thenReturn(2L);
        when(values.get("createdAt", Long.class)).thenReturn(100L);
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("hasNewerCompletedReport",
                ResourceResolver.class, String.class, String.class, long.class);
        method.setAccessible(true);

        assertEquals(true, method.invoke(listener, resolver, ASSET, "older-token", 1L));
        assertEquals(false, method.invoke(listener, resolver, ASSET, "older-token", 3L));
    }

    @Test
    public void skipsDuplicateJobAfterSuccessfulReport() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource report = mock(Resource.class);
        ValueMap values = mock(ValueMap.class);
        Job job = mock(Job.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/report"))
                .thenReturn(report);
        when(report.getValueMap()).thenReturn(values);
        when(values.get("completed", false)).thenReturn(true);
        when(values.get("error", "")).thenReturn("");
        when(values.get(eq("republishedPages"), any(String[].class)))
                .thenReturn(new String[]{"/content/site/pages/home"});
        when(job.getProperty("assetPaths", String[].class)).thenReturn(new String[]{ASSET});
        when(job.getProperty("reportToken", String.class)).thenReturn("report");

        assertEquals(JobConsumer.JobResult.OK, listener.process(job));
    }

    @Test
    public void blockedPagesRemainAvailableForManualReview() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource report = mock(Resource.class);
        Resource reportRoot = mock(Resource.class);
        Resource manualRoot = mock(Resource.class);
        ModifiableValueMap reportValues = mock(ModifiableValueMap.class);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET + "/report")).thenReturn(report);
        when(resolver.getResource("/var/asset-alttext-republish")).thenReturn(reportRoot);
        when(resolver.create(eq(reportRoot), eq("manual"), anyMap())).thenReturn(manualRoot);
        when(report.adaptTo(ModifiableValueMap.class)).thenReturn(reportValues);
        Method saveReport = AssetAltTextRepublishListener.class.getDeclaredMethod("saveReport",
                ResourceResolver.class, String[].class, String.class, String[].class, String[].class, String.class);
        saveReport.setAccessible(true);

        saveReport.invoke(listener, resolver, new String[]{ASSET}, "report", new String[0],
                new String[]{"/content/site/pages/home"}, null);

        ArgumentCaptor<Map> marker = ArgumentCaptor.forClass(Map.class);
        verify(resolver).create(eq(manualRoot), eq("report"), marker.capture());
        assertEquals(ASSET, marker.getValue().get("assetPath"));
        assertArrayEquals(new String[]{"/content/site/pages/home"}, (String[]) marker.getValue().get("blockedPages"));
    }

    @Test
    public void retriesFailedEditorEnqueueOnlyAfterSaveIsReady() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        JobManager manager = mock(JobManager.class);
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        inject(listener, "jobManager", manager);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        stubPendingRepository(resolver);

        listener.expectEditorSave(ASSET, "report", "New text");
        listener.run();
        verify(manager, never()).addJob(anyString(), anyMap());

        assertFalse(listener.queueEditorSave(ASSET, "report"));
        when(manager.addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap())).thenReturn(mock(Job.class));
        listener.run();
        verify(manager, times(2)).addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap());
        assertTrue(listener.queueEditorSave(ASSET, "report"));
        verify(manager, times(2)).addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap());
    }

    @Test
    public void deferredEditorSaveDoesNotSchedulePublication() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        JobManager manager = mock(JobManager.class);
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        inject(listener, "jobManager", manager);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        stubPendingRepository(resolver);

        listener.expectDeferredEditorSave(ASSET, "report", "New text");
        ArgumentCaptor<Map> properties = ArgumentCaptor.forClass(Map.class);
        Resource pendingRoot = resolver.getResource("/var/asset-alttext-republish/pending");
        verify(resolver).create(eq(pendingRoot), eq("report"), properties.capture());
        assertEquals(true, properties.getValue().get("deferred"));

        listener.run();
        verify(manager, never()).addJob(anyString(), anyMap());
    }

    @Test
    public void recoversPendingRequestAfterInMemoryStateIsLost() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        JobManager manager = mock(JobManager.class);
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource pendingRoot = mock(Resource.class);
        Resource pending = mock(Resource.class);
        Resource asset = mock(Resource.class);
        ValueMap values = mock(ValueMap.class);
        inject(listener, "jobManager", manager);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        stubPendingRepository(resolver);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(pendingRoot.getChildren()).thenReturn(Collections.singletonList(pending));
        when(pending.getValueMap()).thenReturn(values);
        when(pending.getName()).thenReturn("report-after-restart");
        when(values.get("assetPath", String.class)).thenReturn(ASSET);
        when(values.get("createdAt", Long.class)).thenReturn(System.currentTimeMillis() - 60_000);
        when(values.get("expectedAlt", String.class)).thenReturn("New text");
        when(values.get("committed", false)).thenReturn(false);
        when(resolver.getResource(ASSET)).thenReturn(asset);
        Resource content = mock(Resource.class);
        ValueMap contentValues = mock(ValueMap.class);
        when(asset.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(contentValues);
        when(contentValues.get("alt", "")).thenReturn("Old text");
        Session recoverySession = mock(Session.class, RETURNS_DEEP_STUBS);
        when(resolver.adaptTo(Session.class)).thenReturn(recoverySession);
        when(pending.getPath()).thenReturn("/var/asset-alttext-republish/pending/report-after-restart");
        Resource reports = mock(Resource.class);
        Resource newerReport = mock(Resource.class);
        ValueMap newerValues = mock(ValueMap.class);
        when(resolver.getResource("/var/asset-alttext-republish" + ASSET)).thenReturn(reports);
        when(reports.getChildren()).thenReturn(Collections.singletonList(newerReport));
        when(newerReport.getName()).thenReturn("newer-token");
        when(newerReport.getValueMap()).thenReturn(newerValues);
        when(newerValues.get("completed", false)).thenReturn(true);
        when(newerValues.get("requestCreatedAt", Long.class)).thenReturn(System.currentTimeMillis());
        when(manager.findJobs(eq(JobManager.QueryType.QUEUED), eq(AssetAltTextRepublishListener.TOPIC),
                eq(1L), anyMap())).thenReturn(Collections.singletonList(mock(Job.class)));

        listener.run();
        verify(manager, never()).findJobs(any(JobManager.QueryType.class), anyString(), anyLong(), anyMap());
        verify(resolver, never()).delete(pending);
        when(contentValues.get("alt", "")).thenReturn("New text");
        listener.run();
        verify(manager, never()).addJob(anyString(), anyMap());
        verify(resolver).delete(pending);

        reset(manager);
        when(manager.addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap())).thenReturn(mock(Job.class));

        listener.run();

        ArgumentCaptor<Map> properties = ArgumentCaptor.forClass(Map.class);
        verify(manager).addJob(eq(AssetAltTextRepublishListener.TOPIC), properties.capture());
        assertEquals("report-after-restart", properties.getValue().get("reportToken"));
        verify(resolver, times(2)).delete(pending);
    }

    @Test
    public void queuesOnlyPublishedAltTextChanges() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        JobManager manager = mock(JobManager.class);
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        ResourceResolver resolver = mock(ResourceResolver.class);
        Resource asset = mock(Resource.class);
        Resource content = mock(Resource.class);
        ValueMap values = mock(ValueMap.class);
        ValueMap contentValues = mock(ValueMap.class);
        Node current = mock(Node.class);
        Node frozen = mock(Node.class);
        Property currentAlt = mock(Property.class);
        Property publishedAlt = mock(Property.class);
        Session session = mock(Session.class, RETURNS_DEEP_STUBS);
        Version publishedVersion = mock(Version.class);
        inject(listener, "jobManager", manager);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenReturn(resolver);
        stubPendingRepository(resolver);
        when(resolver.getResource(ASSET)).thenReturn(asset);
        when(asset.getValueMap()).thenReturn(values);
        when(values.get("jcr:primaryType", String.class)).thenReturn("per:Asset");
        when(asset.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(contentValues);
        when(contentValues.get("alt", "")).thenReturn("New text");
        when(content.adaptTo(Node.class)).thenReturn(current);
        when(current.getSession()).thenReturn(session);
        when(current.getPath()).thenReturn(ASSET + "/jcr:content");
        when(session.getWorkspace().getVersionManager().getVersionHistory(ASSET + "/jcr:content")
                .getVersionByLabel(PUBLISHED_LABEL)).thenReturn(publishedVersion);
        when(publishedVersion.getFrozenNode()).thenReturn(frozen);
        when(current.hasProperty("alt")).thenReturn(true);
        when(frozen.hasProperty("alt")).thenReturn(true);
        when(current.getProperty("alt")).thenReturn(currentAlt);
        when(frozen.getProperty("alt")).thenReturn(publishedAlt);
        when(currentAlt.getString()).thenReturn("New text");
        when(publishedAlt.getString()).thenReturn("Old text");
        ResourceChange contentChange = mock(ResourceChange.class);
        ResourceChange renditionChange = mock(ResourceChange.class);
        when(contentChange.getPath()).thenReturn(ASSET + "/jcr:content");
        when(renditionChange.getPath()).thenReturn(ASSET + "/renditions/thumb/jcr:content");
        listener.onChange(Arrays.asList(contentChange, renditionChange));
        ArgumentCaptor<Map> props = ArgumentCaptor.forClass(Map.class);
        verify(manager).addJob(eq(AssetAltTextRepublishListener.TOPIC), props.capture());
        assertArrayEquals(new String[]{ASSET}, (String[]) props.getValue().get("assetPaths"));

        reset(manager);
        when(manager.addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap())).thenReturn(mock(Job.class));
        listener.expectEditorSave(ASSET, "first-report", "New text");
        listener.expectEditorSave(ASSET, "second-report", "New text");
        listener.expectEditorSave(ASSET, "uncommitted-report", "Different text");
        listener.onChange(Arrays.asList(contentChange, renditionChange));
        verify(manager, times(2)).addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap());
        assertTrue(listener.queueEditorSave(ASSET, "first-report"));
        verify(manager, times(2)).addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap());

        reset(manager);
        ResourceChange metadataChange = mock(ResourceChange.class);
        when(metadataChange.getPath()).thenReturn(ASSET + "/jcr:content/metadata");
        listener.onChange(Arrays.asList(metadataChange));
        verify(manager, never()).addJob(anyString(), anyMap());

        assertTrue(listener.cancelEditorSave(ASSET, "first-report"));
        listener.onChange(Arrays.asList(contentChange, metadataChange));
        verify(manager, never()).addJob(anyString(), anyMap());

        assertTrue(listener.cancelEditorSave(ASSET, "second-report"));
        assertFalse(listener.cancelEditorSave(ASSET, "second-report"));
        assertFalse(listener.cancelEditorSave(ASSET, "uncommitted-report"));
        listener.onChange(Arrays.asList(contentChange, renditionChange));
        verify(manager).addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap());

        reset(manager);
        Method recheck = AssetAltTextRepublishListener.class.getDeclaredMethod("enqueueIfAltTextStillChanged", String.class);
        recheck.setAccessible(true);
        recheck.invoke(listener, ASSET);
        verify(manager).addJob(eq(AssetAltTextRepublishListener.TOPIC), anyMap());

        reset(manager);
        when(currentAlt.getString()).thenReturn("Old text");
        recheck.invoke(listener, ASSET);
        verify(manager, never()).addJob(anyString(), anyMap());
        listener.onChange(Arrays.asList(contentChange, renditionChange));
        verify(manager, never()).addJob(anyString(), anyMap());
    }

    @Test
    public void usesTheConfiguredSubserviceMapping() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        ResourceResolverFactory factory = mock(ResourceResolverFactory.class);
        inject(listener, "resourceResolverFactory", factory);
        when(factory.getServiceResourceResolver(anyMap())).thenThrow(new LoginException("test"));
        Job job = mock(Job.class);
        when(job.getProperty("assetPaths", String[].class)).thenReturn(new String[]{ASSET});
        assertEquals(JobConsumer.JobResult.FAILED, listener.process(job));
        ArgumentCaptor<Map> auth = ArgumentCaptor.forClass(Map.class);
        verify(factory).getServiceResourceResolver(auth.capture());
        assertEquals("asset-alttext-republish", auth.getValue().get(ResourceResolverFactory.SUBSERVICE));
    }

    @Test
    public void comparesDirectAltWithPublishedTextIncludingClearingIt() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        Resource asset = mock(Resource.class);
        Resource content = mock(Resource.class);
        Node current = mock(Node.class);
        Node frozen = mock(Node.class);
        Property currentAlt = mock(Property.class);
        Property publishedAlt = mock(Property.class);
        Session session = mock(Session.class, RETURNS_DEEP_STUBS);
        when(asset.getChild("jcr:content")).thenReturn(content);
        when(content.adaptTo(Node.class)).thenReturn(current);
        when(current.getSession()).thenReturn(session);
        when(current.getPath()).thenReturn(ASSET + "/jcr:content");
        when(session.getWorkspace().getVersionManager().getVersionHistory(ASSET + "/jcr:content")
                .getVersionByLabel(PUBLISHED_LABEL).getFrozenNode()).thenReturn(frozen);
        when(current.hasProperty("alt")).thenReturn(true);
        when(frozen.hasProperty("alt")).thenReturn(true);
        when(current.getProperty("alt")).thenReturn(currentAlt);
        when(frozen.getProperty("alt")).thenReturn(publishedAlt);
        when(currentAlt.getString()).thenReturn("New text");
        when(publishedAlt.getString()).thenReturn("Old text");
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("isAltTextChangeCandidate", Resource.class);
        method.setAccessible(true);
        assertEquals(true, method.invoke(listener, asset));
        when(currentAlt.getString()).thenReturn("Old text");
        assertEquals(false, method.invoke(listener, asset));
        when(currentAlt.getString()).thenReturn("");
        assertEquals(true, method.invoke(listener, asset));
    }

    @Test
    public void matchesReplicatedSourceAndLocalTargetPathsButNotMissingPages() throws Exception {
        AssetAltTextRepublishListener listener = new AssetAltTextRepublishListener();
        Method method = AssetAltTextRepublishListener.class.getDeclaredMethod("wasReplicated",
                Replication.class, java.util.List.class, Resource.class);
        method.setAccessible(true);
        Resource source = mock(Resource.class);
        Resource localTarget = mock(Resource.class);
        Replication replication = mock(Replication.class);
        when(source.getPath()).thenReturn("/content/site/pages/news");
        when(localTarget.getPath()).thenReturn("/content/publish/pages/news");
        when(replication.getReplicatedPath(source)).thenReturn("/content/publish/pages/news");
        assertEquals(true, method.invoke(listener, replication, Arrays.asList(localTarget), source));
        Resource otherSite = mock(Resource.class);
        when(otherSite.getPath()).thenReturn("/content/other/pages/news");
        assertEquals(false, method.invoke(listener, replication, Arrays.asList(otherSite), source));
    }

    private void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void stubPendingRepository(ResourceResolver resolver) throws Exception {
        Resource reportRoot = mock(Resource.class);
        Resource pendingRoot = mock(Resource.class);
        when(resolver.getResource("/var/asset-alttext-republish")).thenReturn(reportRoot);
        when(resolver.getResource("/var/asset-alttext-republish/pending")).thenReturn(pendingRoot);
        when(reportRoot.getValueMap()).thenReturn(mock(ValueMap.class));
        when(reportRoot.getChildren()).thenReturn(Collections.emptyList());
        when(pendingRoot.getChildren()).thenReturn(Collections.emptyList());
        Resource created = mock(Resource.class);
        when(resolver.create(any(Resource.class), anyString(), anyMap())).thenReturn(created);
        when(resolver.create(eq(pendingRoot), anyString(), anyMap())).thenReturn(created);
        when(created.adaptTo(Node.class)).thenReturn(mock(Node.class));
        when(created.adaptTo(ModifiableValueMap.class)).thenReturn(mock(ModifiableValueMap.class));
        when(resolver.getResource(startsWith("/var/asset-alttext-republish/pending/"))).thenReturn(created);
        when(created.getPath()).thenReturn("/var/asset-alttext-republish/pending/report");
        when(resolver.adaptTo(Session.class)).thenReturn(mock(Session.class, RETURNS_DEEP_STUBS));
    }
}
