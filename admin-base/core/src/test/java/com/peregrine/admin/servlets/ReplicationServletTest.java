package com.peregrine.admin.servlets;

import com.peregrine.intra.IntraSlingCaller;
import com.peregrine.render.RenderService;
import com.peregrine.replication.Replication;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.peregrine.admin.servlets.ReplicationServlet.DEACTIVATE;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public final class ReplicationServletTest extends ReplicationServletTestBase {

    public ReplicationServletTest() throws NoSuchFieldException, Replication.ReplicationException {
        super(new ReplicationServlet());
    }

    @Test
    public void performDeactivation() throws IOException {
        request.putParameter(DEACTIVATE, true);
        performReplicationResponseContains(jcrContent);
    }

    @Test
    public void performActivation() throws IOException {
        performReplicationResponseContains(jcrContent);
    }

    @Test
    public void publishIsBlockedWhenModelExportFails() throws Exception {
        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenThrow(new RuntimeException("model error"));

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with model errors"));
        assertTrue(response.contains(jcrContent.getPath()));
    }

    @Test
    public void publishIsBlockedWhenNestedModelExportFails() throws Exception {
        final var wrapper = jcrContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final var header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");

        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());
        when(getModelFactory().exportModelForResource(header, "jackson", Map.class, Collections.emptyMap()))
                .thenThrow(new RuntimeException("OOPS"));

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with model errors"));
        assertTrue(response.contains(header.getPath()));
        assertTrue(response.contains("stkdtheme/components/header"));
        assertTrue(response.contains("OOPS"));
        verify(getModelFactory(), never()).exportModelForResource(wrapper, "jackson", Map.class, Collections.emptyMap());
    }

    @Test
    public void publishIsBlockedWhenSsrRenderFails() throws Exception {
        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());
        when(getIntraSlingCaller().call(any(IntraSlingCaller.CallerContext.class)))
                .thenThrow(new IntraSlingCaller.CallException("Failed to render resource", new RuntimeException("OOPS")));

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with render errors"));
        assertTrue(response.contains(page.getPath()));
        assertTrue(response.contains("OOPS"));
    }

    @Test
    public void publishIsBlockedWhenNestedComponentRenderFails() throws Exception {
        final var wrapper = jcrContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final var header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");

        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());
        when(getModelFactory().exportModelForResource(header, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());
        when(getRenderService().renderInternally(header, "html"))
                .thenThrow(new RenderService.RenderException("Failed to render resource", new RuntimeException("OOPS")));

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with render errors"));
        assertTrue(response.contains(header.getPath()));
        assertTrue(response.contains("stkdtheme/components/header"));
        assertTrue(response.contains("OOPS"));
    }

    @Test
    public void publishIsBlockedWhenComponentVueTemplateContainsThrow() throws Exception {
        final var wrapper = jcrContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final var header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");
        addComponentTemplate("stkdtheme/components/header",
                "<template><div></div></template><script>export default { data: function () { throw new Error(\"OOPS\"); } }</script>");

        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());
        when(getModelFactory().exportModelForResource(header, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with javascript errors"));
        assertTrue(response.contains(header.getPath()));
        assertTrue(response.contains("stkdtheme/components/header"));
        assertTrue(response.contains("Explicit JavaScript throw"));
    }

    @Test
    public void publishIsBlockedWhenReferencedTemplateComponentVueTemplateContainsThrow() throws Exception {
        final var templateHeader = addReferencedTemplateHeader("/content/templates/header-footer");
        jcrContent.putProperty("templatePath", "/content/templates/header-footer");
        addComponentTemplate("stkdtheme/components/header",
                "<template><div></div></template><script>export default { data: function () { throw new Error(\"OOPS\"); } }</script>");

        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenReturn(Collections.emptyMap());

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with javascript errors"));
        assertTrue(response.contains(templateHeader.getPath()));
        assertTrue(response.contains("stkdtheme/components/header"));
        assertTrue(response.contains("Explicit JavaScript throw"));
    }

}
