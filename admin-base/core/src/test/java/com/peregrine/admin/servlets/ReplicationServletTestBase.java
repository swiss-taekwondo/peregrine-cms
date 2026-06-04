package com.peregrine.admin.servlets;

import com.peregrine.SlingServletTest;
import com.peregrine.admin.resource.AdminResourceHandler;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import com.peregrine.mock.PageMock;
import com.peregrine.mock.ResourceMock;
import com.peregrine.intra.IntraSlingCaller;
import com.peregrine.intra.IntraSlingCallerService;
import com.peregrine.replication.PerReplicable;
import com.peregrine.replication.Replication;
import com.peregrine.replication.ReplicationsContainerWithDefault;
import com.peregrine.render.RenderService;
import junitx.util.PrivateAccessor;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Assert;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

import static com.peregrine.commons.util.PerConstants.PATH;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ReplicationServletTestBase extends SlingServletTest {

    private final ReplicationServletBase servlet;

    private final AdminResourceHandler resourceManagement = mock(AdminResourceHandler.class);
    private final ReplicationsContainerWithDefault replications = mock(ReplicationsContainerWithDefault.class);
    private final ModelFactory modelFactory = mock(ModelFactory.class);
    private final IntraSlingCaller intraSlingCaller = mock(IntraSlingCaller.class);
    private final RenderService renderService = mock(RenderService.class);

    private final Replication replication = mock(Replication.class);
    private final PerReplicable replicable = mock(PerReplicable.class);

    public ReplicationServletTestBase(final ReplicationServletBase servlet)
            throws NoSuchFieldException, Replication.ReplicationException
    {
        this.servlet = servlet;
        setField("replications", replications);
        setField("resourceManagement", resourceManagement);
        setFieldIfPresent("modelFactory", modelFactory);
        setFieldIfPresent("intraSlingCaller", intraSlingCaller);
        setFieldIfPresent("renderService", renderService);

        when(replications.getOrDefault(anyString())).thenReturn(replication);

        when(replication.prepare(any())).thenAnswer(i -> i.getArguments()[0]);
        when(replication.replicate(any())).thenAnswer(i -> i.getArguments()[0]);
        when(replication.deactivate(any(PageMock.class))).thenAnswer(
                i -> Collections.singletonList(
                        ((PageMock)i.getArguments()[0]).getContent()
                )
        );

        page.addAdapter(replicable);
        jcrContent.addAdapter(replicable);
        when(replicable.getMainResource()).thenReturn(jcrContent);
        try {
            when(intraSlingCaller.createContext()).thenReturn(new IntraSlingCallerService.CallerContextImpl());
            when(intraSlingCaller.call(org.mockito.Matchers.any(IntraSlingCaller.CallerContext.class)))
                .thenReturn(new byte[0]);
        } catch (IntraSlingCaller.CallException e) {
            throw new RuntimeException(e);
        }
    }

    protected void setField(final String name, final Object value) throws NoSuchFieldException {
        PrivateAccessor.setField(servlet, name, value);
    }

    protected void setFieldIfPresent(final String name, final Object value) {
        try {
            setField(name, value);
        } catch (NoSuchFieldException ignored) {
        }
    }

    protected void performReplicationResponseContains(final PageMock page, final String... substrings) throws IOException {
        request.putParameter(PATH, page.getPath());
        final AbstractBaseServlet.Request request = new AbstractBaseServlet.Request(this.request, response);
        final String response = servlet.handleRequest(request).getContent();
        Arrays.stream(substrings)
                .map(response::contains)
                .forEach(Assert::assertTrue);
    }

    protected void performReplicationResponseContains(final String... substrings) throws IOException {
        performReplicationResponseContains(page, substrings);
    }

    protected void performReplicationResponseContains(final Resource... resources) throws IOException {
        performReplicationResponseContains(Arrays.stream(resources)
                .map(Resource::getPath)
                .toArray(String[]::new)
        );
    }

    protected ModelFactory getModelFactory() {
        return modelFactory;
    }

    protected IntraSlingCaller getIntraSlingCaller() {
        return intraSlingCaller;
    }

    protected RenderService getRenderService() {
        return renderService;
    }

    protected void addComponentTemplate(final String resourceType, final String source) {
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

    protected ResourceMock addReferencedTemplateHeader(final String templatePath) {
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

    protected String performReplicationResponse() throws IOException {
        request.putParameter(PATH, page.getPath());
        final AbstractBaseServlet.Request request = new AbstractBaseServlet.Request(this.request, response);
        return servlet.handleRequest(request).getContent();
    }

}
