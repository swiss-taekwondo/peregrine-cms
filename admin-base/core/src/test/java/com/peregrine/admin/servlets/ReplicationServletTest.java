package com.peregrine.admin.servlets;

import com.peregrine.replication.Replication;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static com.peregrine.admin.servlets.ReplicationServlet.DEACTIVATE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ReplicationServletTest extends ReplicationServletTestBase {

    private HttpServer mockServer;
    private int dynamicPort;

    public ReplicationServletTest() throws NoSuchFieldException, Replication.ReplicationException {
        super(new ReplicationServlet());
    }

    @Before
    public void startMockServer() throws Exception {
        // Pass '0' to let the OS assign any available port safely
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);

        // Retrieve the port the OS actually gave us
        dynamicPort = mockServer.getAddress().getPort();

        mockServer.createContext("/api/webhook", exchange -> {
            String response = "Success";
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.close();
        });
        mockServer.start();
    }

    @After
    public void stopMockServer() {
        // Ensure the server shuts down after tests run so mock port is released
        if (mockServer != null) {
            mockServer.stop(0);
        }
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
    public void testReplicationWithWebhook() throws Exception {
        ReplicationServlet replServlet = (ReplicationServlet) servlet;

        // Mock the OSGi Configuration interface
        ReplicationServlet.Configuration config = mock(ReplicationServlet.Configuration.class);

        // Map the webhook to hit our local mock server.
        // We include "test" and "themeclean" to cover standard SlingServletTest mock paths.
        String webhook1 = "themeclean=http://localhost:" + dynamicPort + "/api/webhook";
        String webhook2 = "test=http://localhost:" + dynamicPort + "/api/webhook";

        when(config.pre_publish_webhook_map()).thenReturn(new String[]{ webhook1, webhook2 });
        when(config.post_publish_webhook_map()).thenReturn(new String[0]);

        // Manually trigger the OSGi lifecycle to parse the config and populate the maps
        replServlet.activate(config);

        // Execute the standard replication. The internal HttpClient will hit mock port and succeed.
        performReplicationResponseContains(jcrContent);
    }

}
