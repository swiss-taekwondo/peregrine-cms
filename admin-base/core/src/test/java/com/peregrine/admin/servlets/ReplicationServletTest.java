package com.peregrine.admin.servlets;

import com.peregrine.intra.IntraSlingCaller;
import com.peregrine.render.RenderService;
import com.peregrine.replication.Replication;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.peregrine.admin.servlets.ReplicationServlet.DEACTIVATE;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

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
    public void publishContinuesWhenValidationDependenciesThrow() throws Exception {
        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenThrow(new RuntimeException("model error"));
        when(getIntraSlingCaller().call(any(IntraSlingCaller.CallerContext.class)))
                .thenThrow(new IntraSlingCaller.CallException("Failed to render resource", new RuntimeException("OOPS")));
        when(getRenderService().renderInternally(any(), anyString()))
                .thenThrow(new RenderService.RenderException("Failed to render resource", new RuntimeException("OOPS")));

        final String response = performReplicationResponse();

        assertTrue(response.contains(jcrContent.getPath()));
        assertFalse(response.contains("Replication Failed"));
    }

}
