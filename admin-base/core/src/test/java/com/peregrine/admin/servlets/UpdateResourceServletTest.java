package com.peregrine.admin.servlets;

import com.peregrine.admin.resource.AdminResourceHandler;
import com.peregrine.commons.servlets.AbstractBaseServlet.Request;
import com.peregrine.commons.servlets.AbstractBaseServlet.Response;
import com.peregrine.replication.AssetAltTextRepublishCoordinator;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpdateResourceServletTest {
    @Test
    public void trackingFailureDoesNotPreventAssetSave() throws Exception {
        final UpdateResourceServlet servlet = new UpdateResourceServlet();
        servlet.resourceManagement = mock(AdminResourceHandler.class);
        servlet.altTextRepublishCoordinator = mock(AssetAltTextRepublishCoordinator.class);
        final Request request = mock(Request.class);
        final ResourceResolver resolver = mock(ResourceResolver.class);
        final Resource asset = mock(Resource.class);
        final Resource content = mock(Resource.class);
        final Resource updated = mock(Resource.class);
        final ValueMap assetValues = mock(ValueMap.class);
        final ValueMap contentValues = mock(ValueMap.class);
        final String assetPath = "/content/site/assets/image.jpg";
        final String contentPath = assetPath + "/jcr:content";
        final String json = "{\"alt\":\"New text\"}";
        when(request.getParameter("path")).thenReturn(contentPath);
        when(request.getParameter("content")).thenReturn(json);
        when(request.getResourceResolver()).thenReturn(resolver);
        when(resolver.getResource(assetPath)).thenReturn(asset);
        when(asset.getValueMap()).thenReturn(assetValues);
        when(assetValues.get("jcr:primaryType", String.class)).thenReturn("per:Asset");
        when(asset.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(contentValues);
        when(contentValues.get("alt", String.class)).thenReturn("Old text");
        when(servlet.resourceManagement.updateResource(resolver, contentPath, json)).thenReturn(updated);
        when(updated.getName()).thenReturn("jcr:content");
        when(updated.getPath()).thenReturn(contentPath);
        doThrow(new IllegalStateException("tracking unavailable"))
                .when(servlet.altTextRepublishCoordinator).expectDeferredEditorSave(anyString(), anyString(), anyString());

        final Response response = servlet.handleRequest(request);

        verify(servlet.resourceManagement).updateResource(resolver, contentPath, json);
        verify(resolver).commit();
        assertFalse(response.getContent().contains("reportToken"));
        assertTrue(response.getContent().contains("republishWarning"));
    }

    @Test
    public void missingAssetVersionHistoryDoesNotFailCommittedSave() throws Exception {
        final UpdateResourceServlet servlet = new UpdateResourceServlet();
        servlet.resourceManagement = mock(AdminResourceHandler.class);
        servlet.altTextRepublishCoordinator = mock(AssetAltTextRepublishCoordinator.class);
        servlet.referenceLister = mock(com.peregrine.reference.ReferenceLister.class);
        final Request request = mock(Request.class);
        final ResourceResolver resolver = mock(ResourceResolver.class);
        final Resource asset = mock(Resource.class);
        final Resource content = mock(Resource.class);
        final Resource updated = mock(Resource.class);
        final ValueMap assetValues = mock(ValueMap.class);
        final ValueMap contentValues = mock(ValueMap.class);
        final String assetPath = "/content/site/assets/image.jpg";
        final String contentPath = assetPath + "/jcr:content";
        final String json = "{\"alt\":\"New text\"}";
        when(request.getParameter("path")).thenReturn(contentPath);
        when(request.getParameter("content")).thenReturn(json);
        when(request.getResourceResolver()).thenReturn(resolver);
        when(resolver.getResource(assetPath)).thenReturn(asset);
        when(asset.getValueMap()).thenReturn(assetValues);
        when(assetValues.get("jcr:primaryType", String.class)).thenReturn("per:Asset");
        when(asset.getChild("jcr:content")).thenReturn(content);
        when(content.getValueMap()).thenReturn(contentValues);
        when(contentValues.get("alt", String.class)).thenReturn("Old text");
        when(servlet.resourceManagement.updateResource(resolver, contentPath, json)).thenReturn(updated);
        when(updated.getName()).thenReturn("jcr:content");
        when(updated.getPath()).thenReturn(contentPath);

        final Response response = servlet.handleRequest(request);

        verify(servlet.resourceManagement).updateResource(resolver, contentPath, json);
        verify(resolver).commit();
        assertTrue(response.getContent().contains("\"imageChanged\" : true"));
        assertTrue(response.getContent().contains("\"reportToken\""));
    }
}
