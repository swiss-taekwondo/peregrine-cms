package com.peregrine.admin.servlets;

import com.peregrine.replication.Replication;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static com.peregrine.admin.servlets.ReplicationServlet.DEACTIVATE;
import static org.junit.Assert.assertTrue;
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
    public void publishIsBlockedWhenModelExportFails() throws Exception {
        when(getModelFactory().exportModelForResource(jcrContent, "jackson", Map.class, Collections.emptyMap()))
                .thenThrow(new RuntimeException("model error"));

        final String response = performReplicationResponse();

        assertTrue(response.contains("Replication Failed"));
        assertTrue(response.contains("Cannot publish page with model errors"));
        assertTrue(response.contains(jcrContent.getPath()));
    }

}
