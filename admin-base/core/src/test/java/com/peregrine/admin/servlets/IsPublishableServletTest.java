package com.peregrine.admin.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peregrine.SlingServletTest;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import junitx.util.PrivateAccessor;
import org.apache.sling.models.factory.ModelFactory;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static com.peregrine.commons.util.PerConstants.JACKSON;
import static com.peregrine.commons.util.PerConstants.PATH;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public final class IsPublishableServletTest extends SlingServletTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final IsPublishableServlet servlet = new IsPublishableServlet();
    private final ModelFactory modelFactory = mock(ModelFactory.class);

    @Before
    public void setUp() throws NoSuchFieldException {
        PrivateAccessor.setField(servlet, "modelFactory", modelFactory);
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
}
