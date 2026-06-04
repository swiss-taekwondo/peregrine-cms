package com.peregrine.admin.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peregrine.SlingServletTest;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import com.peregrine.intra.IntraSlingCaller;
import com.peregrine.intra.IntraSlingCallerService;
import com.peregrine.mock.ResourceMock;
import com.peregrine.render.RenderService;
import junitx.util.PrivateAccessor;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.peregrine.commons.util.PerConstants.JACKSON;
import static com.peregrine.commons.util.PerConstants.PATH;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public final class IsPublishableServletTest extends SlingServletTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IsPublishableServlet servlet = new IsPublishableServlet();
    private final ModelFactory modelFactory = mock(ModelFactory.class);
    private final IntraSlingCaller intraSlingCaller = mock(IntraSlingCaller.class);
    private final RenderService renderService = mock(RenderService.class);

    @Before
    public void setUp() throws NoSuchFieldException {
        PrivateAccessor.setField(servlet, "modelFactory", modelFactory);
        PrivateAccessor.setField(servlet, "intraSlingCaller", intraSlingCaller);
        PrivateAccessor.setField(servlet, "renderService", renderService);
        try {
            when(intraSlingCaller.createContext()).thenReturn(new IntraSlingCallerService.CallerContextImpl());
            when(intraSlingCaller.call(any(IntraSlingCaller.CallerContext.class))).thenReturn(new byte[0]);
        } catch (final IntraSlingCaller.CallException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void publishableWhenModelExports() throws Exception {
        request.putParameter(PATH, page.getPath());
        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertTrue(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().isEmpty());
    }

    @Test
    public void notPublishableWhenModelExportFails() throws Exception {
        request.putParameter(PATH, page.getPath());
        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenThrow(new RuntimeException("model error"));

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with model errors"));
        assertTrue(response.get("reason").asText().contains(jcrContent.getPath()));
    }

    @Test
    public void notPublishableWhenChildComponentModelExportFails() throws Exception {
        final ResourceMock cards = jcrContent.createChild("cards");
        cards.setResourceType("com/stkdtheme/components/cards");
        request.putParameter(PATH, page.getPath());

        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(modelFactory.exportModelForResource(eq(cards), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenThrow(new RuntimeException("OOPS"));

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with model errors"));
        assertTrue(response.get("reason").asText().contains(cards.getPath()));
        assertTrue(response.get("reason").asText().contains("stkdtheme/components/cards"));
        assertTrue(response.get("reason").asText().contains("OOPS"));
    }

    @Test
    public void notPublishableWhenNestedComponentModelExportFails() throws Exception {
        final ResourceMock wrapper = jcrContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final ResourceMock header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");
        request.putParameter(PATH, page.getPath());

        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(modelFactory.exportModelForResource(eq(header), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenThrow(new RuntimeException("OOPS"));

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with model errors"));
        assertTrue(response.get("reason").asText().contains(header.getPath()));
        assertTrue(response.get("reason").asText().contains("stkdtheme/components/header"));
        assertTrue(response.get("reason").asText().contains("OOPS"));
    }

    @Test
    public void notPublishableWhenNestedComponentRenderFails() throws Exception {
        final ResourceMock wrapper = jcrContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final ResourceMock header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");
        request.putParameter(PATH, page.getPath());

        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(modelFactory.exportModelForResource(eq(header), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(renderService.renderInternally(eq(header), eq("html")))
            .thenThrow(new RenderService.RenderException("Failed to render resource", new RuntimeException("OOPS")));

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with render errors"));
        assertTrue(response.get("reason").asText().contains(header.getPath()));
        assertTrue(response.get("reason").asText().contains("stkdtheme/components/header"));
        assertTrue(response.get("reason").asText().contains("OOPS"));
    }

    @Test
    public void notPublishableWhenComponentVueTemplateContainsThrow() throws Exception {
        final ResourceMock wrapper = jcrContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final ResourceMock header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");
        addComponentTemplate("stkdtheme/components/header",
            "<template><div></div></template><script>export default { data: function () { throw new Error(\"OOPS\"); } }</script>");
        request.putParameter(PATH, page.getPath());

        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(modelFactory.exportModelForResource(eq(header), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with javascript errors"));
        assertTrue(response.get("reason").asText().contains(header.getPath()));
        assertTrue(response.get("reason").asText().contains("stkdtheme/components/header"));
        assertTrue(response.get("reason").asText().contains("Explicit JavaScript throw"));
    }

    @Test
    public void notPublishableWhenReferencedTemplateComponentVueTemplateContainsThrow() throws Exception {
        final ResourceMock templateHeader = addReferencedTemplateHeader("/content/templates/header-footer");
        addComponentTemplate("stkdtheme/components/header",
            "<template><div></div></template><script>export default { data: function () { throw new Error(\"OOPS\"); } }</script>");
        jcrContent.putProperty("template", "/content/templates/header-footer");
        request.putParameter(PATH, page.getPath());

        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with javascript errors"));
        assertTrue(response.get("reason").asText().contains(templateHeader.getPath()));
        assertTrue(response.get("reason").asText().contains("stkdtheme/components/header"));
        assertTrue(response.get("reason").asText().contains("Explicit JavaScript throw"));
    }

    @Test
    public void notPublishableWhenSsrRenderFails() throws Exception {
        request.putParameter(PATH, page.getPath());
        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(intraSlingCaller.call(any(IntraSlingCaller.CallerContext.class)))
            .thenThrow(new IntraSlingCaller.CallException("Failed to render resource", new RuntimeException("OOPS")));

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertFalse(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().contains("Cannot publish page with render errors"));
        assertTrue(response.get("reason").asText().contains(page.getPath()));
        assertTrue(response.get("reason").asText().contains("OOPS"));
    }

    @Test
    public void ssrRenderUsesPageAndContentPathsAndSelector() throws Exception {
        request.putParameter(PATH, page.getPath());
        final List<IntraSlingCaller.CallerContext> contexts = new ArrayList<>();
        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(intraSlingCaller.call(any(IntraSlingCaller.CallerContext.class))).thenAnswer(invocation -> {
            final IntraSlingCaller.CallerContext context = (IntraSlingCaller.CallerContext) invocation.getArguments()[0];
            final IntraSlingCaller.CallerContext snapshot = new IntraSlingCallerService.CallerContextImpl()
                .setPath(context.getPath())
                .setSelectors(context.getSelectors())
                .setExtension(context.getExtension());
            contexts.add(snapshot);
            return new byte[0];
        });

        servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response));

        final var captor = org.mockito.ArgumentCaptor.forClass(IntraSlingCaller.CallerContext.class);
        verify(intraSlingCaller, org.mockito.Mockito.times(2)).call(captor.capture());
        assertTrue(contexts.stream().anyMatch(context -> jcrContent.getPath().equals(context.getPath())));
        assertTrue(contexts.stream().anyMatch(context -> page.getPath().equals(context.getPath())));
        assertTrue(contexts.stream().allMatch(context -> "ssr".equals(context.getSelectors())));
        assertTrue(contexts.stream().allMatch(context -> "html".equals(context.getExtension())));
    }

    @Test
    public void ignoresStructuralUnstructuredWrappers() throws Exception {
        final ResourceMock wrapper = jcrContent.createChild("cards");
        wrapper.setResourceType("nt:unstructured");
        final ResourceMock cards = wrapper.createChild("cards-item");
        cards.setResourceType("stkdtheme/components/cards");
        request.putParameter(PATH, page.getPath());

        when(modelFactory.exportModelForResource(eq(jcrContent), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());
        when(modelFactory.exportModelForResource(eq(cards), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap())))
            .thenReturn(Collections.emptyMap());

        final JsonNode response = OBJECT_MAPPER.readTree(
            servlet.handleRequest(new AbstractBaseServlet.Request(request, this.response)).getContent()
        );

        assertTrue(response.get("result").asBoolean());
        assertTrue(response.get("reason").asText().isEmpty());
        verify(modelFactory, never()).exportModelForResource(eq(wrapper), eq(JACKSON), eq(Map.class), eq(Collections.emptyMap()));
    }

    private void addComponentTemplate(final String resourceType, final String source) {
        ResourceMock current = repo.getRoot();
        for (final String segment : ("apps/" + resourceType + "/template.vue/jcr:content").split("/")) {
            ResourceMock child = current.getChild(segment);
            if (child == null) {
                child = new ResourceMock(segment);
                child.setPath(("/".equals(current.getPath()) ? "" : current.getPath()) + "/" + segment);
                current.addChild(segment, child);
            }
            current = child;
        }
        current.putProperty("jcr:data", new ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    private ResourceMock addReferencedTemplateHeader(final String templatePath) {
        final ResourceMock templatePage = new ResourceMock("Template");
        templatePage.setPath(templatePath);
        templatePage.setResourceType("per:Page");
        repo.init(templatePage);
        final ResourceMock templateContent = templatePage.createChild("jcr:content");
        templateContent.setResourceType("stkdtheme/components/page");
        final ResourceMock wrapper = templateContent.createChild("content");
        wrapper.setResourceType("nt:unstructured");
        final ResourceMock header = wrapper.createChild("header");
        header.setResourceType("stkdtheme/components/header");
        return header;
    }
}
