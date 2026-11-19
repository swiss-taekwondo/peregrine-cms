package com.peregrine.admin.servlets;

import com.peregrine.admin.resource.SmartPublishCoordinator;
import com.peregrine.replication.Replication;
import com.sun.net.httpserver.HttpServer;
import junitx.util.PrivateAccessor;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static com.peregrine.admin.servlets.ReplicationServlet.DEACTIVATE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class ReplicationServletTest extends ReplicationServletTestBase {

    private static final String DRAFT = "draft";

    private final SmartPublishCoordinator coordinator = new SmartPublishCoordinator();
    private HttpServer mockServer;
    private int dynamicPort;

    public ReplicationServletTest() throws NoSuchFieldException, Replication.ReplicationException {
        super(new ReplicationServlet());
        setField("coordinator", coordinator);
    }

    @Before
    public void startCoordinator() throws Throwable {
        PrivateAccessor.invoke(coordinator, "activate", new Class[0], new Object[0]);
    }

    @After
    public void stopCoordinator() throws Throwable {
        PrivateAccessor.invoke(coordinator, "deactivate", new Class[0], new Object[0]);
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
        request.putParameter(DRAFT, false);
        performReplicationResponseContains(jcrContent);
    }

    @Test
    public void smartPublishIsQueuedByDefault() throws IOException {
        performReplicationResponseContains("jobId", page.getPath());
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

        // Execute the standard (synchronous) replication. The internal HttpClient will hit mock port and succeed.
        request.putParameter(DRAFT, false);
        performReplicationResponseContains(jcrContent);
    }

}
