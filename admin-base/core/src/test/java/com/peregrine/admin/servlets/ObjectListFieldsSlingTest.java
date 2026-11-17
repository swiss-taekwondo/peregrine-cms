package com.peregrine.admin.servlets;

import static com.peregrine.commons.util.PerConstants.JCR_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.OBJECT_PATH;
import static com.peregrine.commons.util.PerConstants.OBJECT_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.PATH;
import static com.peregrine.commons.util.PerConstants.SLING_FOLDER;
import static com.peregrine.commons.util.PerConstants.SLING_ORDERED_FOLDER;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.peregrine.commons.servlets.AbstractBaseServlet;
import com.peregrine.commons.servlets.AbstractBaseServlet.ErrorResponse;
import com.peregrine.commons.servlets.AbstractBaseServlet.Response;
import com.peregrine.reference.ReferenceLister;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import junitx.util.PrivateAccessor;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Object list columns: listFields inheritance, the objectFolderSettings API and the
 * list data in nodes.json, against a mocked repository.
 */
public class ObjectListFieldsSlingTest {

    private static final String OBJECTS = "/content/tenant/objects";
    private static final String EVENTS = OBJECTS + "/events";
    private static final String YEAR = EVENTS + "/2026";
    private static final String NEWS = OBJECTS + "/news";
    private static final String EVENT_DEFINITION = "/content/tenant/object-definitions/event";
    private static final String NEWS_DEFINITION = "/content/tenant/object-definitions/news";
    private static final String SETTINGS_URL = "/perapi/admin/objectFolderSettings.json";

    @Rule
    public final SlingContext context = new SlingContext(ResourceResolverType.RESOURCERESOLVER_MOCK);

    private final ObjectFolderSettingsServlet settingsServlet = new ObjectFolderSettingsServlet();

    @Before
    public void createContent() {
        folder("/content", SLING_FOLDER);
        folder("/content/tenant", SLING_FOLDER);
        folder(OBJECTS, SLING_ORDERED_FOLDER);
        folder(EVENTS, SLING_ORDERED_FOLDER, "listFields", new String[] {"fromdate", "location"});
        folder(YEAR, SLING_ORDERED_FOLDER);
        object(YEAR + "/swiss-open", EVENT_DEFINITION,
            "title", "Swiss Open", "fromdate", "2026-03-14T00:00:00.000Z", "location", "Winterthur",
            "text", "<p>secret details</p>");
        object(YEAR + "/hub-training", EVENT_DEFINITION, "title", "HUB Training", "fromdate", "2026-01-17T00:00:00.000Z");
        folder(NEWS, SLING_ORDERED_FOLDER);
        folder(NEWS + "/2026", SLING_FOLDER);
        object(NEWS + "/2026/article", NEWS_DEFINITION, "title", "Article");
        object(NEWS + "/2026/other", NEWS_DEFINITION, "title", "Other");
        folder("/content/tenant/pages", SLING_ORDERED_FOLDER);
    }

    // --- inheritance -------------------------------------------------------------------------

    @Test
    public void folderUsesItsOwnListFields() {
        assertArrayEquals(new String[] {"fromdate", "location"}, NodesServlet.findListFields(resource(EVENTS)));
    }

    @Test
    public void childFolderInheritsFromClosestAncestor() {
        assertArrayEquals(new String[] {"fromdate", "location"}, NodesServlet.findListFields(resource(YEAR)));
        assertEquals(EVENTS, ObjectFolderSettingsServlet.findListFieldsSource(resource(YEAR)).getPath());
    }

    @Test
    public void ownSettingOverridesInherited() {
        context.resourceResolver().getResource(YEAR).adaptTo(org.apache.sling.api.resource.ModifiableValueMap.class)
            .put("listFields", new String[] {"type"});
        assertArrayEquals(new String[] {"type"}, NodesServlet.findListFields(resource(YEAR)));
    }

    @Test
    public void emptySettingStopsInheritance() {
        context.resourceResolver().getResource(YEAR).adaptTo(org.apache.sling.api.resource.ModifiableValueMap.class)
            .put("listFields", new String[0]);
        assertEquals(0, NodesServlet.findListFields(resource(YEAR)).length);
        assertEquals(YEAR, ObjectFolderSettingsServlet.findListFieldsSource(resource(YEAR)).getPath());
    }

    @Test
    public void noSettingAnywhereMeansNoFields() {
        assertEquals(0, NodesServlet.findListFields(resource(NEWS + "/2026")).length);
        assertNull(ObjectFolderSettingsServlet.findListFieldsSource(resource(NEWS + "/2026")));
    }

    @Test
    public void findsDefinitionsUsedBelowFolderOnce() {
        Set<String> definitions = ObjectFolderSettingsServlet.findObjectDefinitions(resource(OBJECTS));
        assertEquals(Arrays.asList(EVENT_DEFINITION, NEWS_DEFINITION), new ArrayList<>(definitions));
        assertEquals(1, ObjectFolderSettingsServlet.findObjectDefinitions(resource(NEWS)).size());
    }

    @Test
    public void definitionScanIsBounded() {
        for (int i = 0; i < ObjectFolderSettingsServlet.MAX_SCANNED_NODES + 10; i++) {
            object(NEWS + "/2026/bulk-" + i, NEWS_DEFINITION);
        }
        object(NEWS + "/2026/zz-last", "/content/tenant/object-definitions/late");
        Set<String> definitions = ObjectFolderSettingsServlet.findObjectDefinitions(resource(NEWS));
        assertFalse("objects beyond the scan limit are not looked at",
            definitions.contains("/content/tenant/object-definitions/late"));
    }

    // --- objectFolderSettings API ------------------------------------------------------------

    @Test
    public void getReportsInheritedSetting() throws Exception {
        String json = content(get(YEAR));
        assertTrue(json, json.contains("\"inherited\":true"));
        assertTrue(json, json.contains("\"inheritedFrom\":\"" + EVENTS + "\""));
        assertTrue(json, json.contains("\"effectiveListFields\":[\"fromdate\",\"location\"]"));
        assertTrue(json, json.contains("\"objectDefinitions\":[\"" + EVENT_DEFINITION + "\"]"));
        assertFalse("no own listFields when inherited", json.contains("\"listFields\""));
    }

    @Test
    public void getReportsOwnSetting() throws Exception {
        String json = content(get(EVENTS));
        assertTrue(json, json.contains("\"listFields\":[\"fromdate\",\"location\"]"));
        assertTrue(json, json.contains("\"inherited\":false"));
        assertFalse(json, json.contains("inheritedFrom"));
    }

    @Test
    public void postStoresFieldsInOrder() throws Exception {
        String json = content(post(NEWS, "listFields", "date, category,date"));
        assertTrue(json, json.contains("\"listFields\":[\"date\",\"category\"]"));
        assertArrayEquals(new String[] {"date", "category"},
            resource(NEWS).getValueMap().get("listFields", String[].class));
        assertArrayEquals(new String[] {"date", "category"}, NodesServlet.findListFields(resource(NEWS + "/2026")));
    }

    @Test
    public void postEmptyValueStoresNoFields() throws Exception {
        content(post(YEAR, "listFields", ""));
        assertTrue(resource(YEAR).getValueMap().containsKey("listFields"));
        assertEquals(0, NodesServlet.findListFields(resource(YEAR)).length);
    }

    @Test
    public void postInheritRemovesOwnSetting() throws Exception {
        post(YEAR, "listFields", "type");
        String json = content(post(YEAR, "inherit", "true"));
        assertFalse(resource(YEAR).getValueMap().containsKey("listFields"));
        assertTrue(json, json.contains("\"inheritedFrom\":\"" + EVENTS + "\""));
    }

    @Test
    public void postWithoutValueIsRejected() throws Exception {
        assertError(post(YEAR), 400);
    }

    @Test
    public void invalidFieldIsRejectedAndNothingStored() throws Exception {
        assertError(post(YEAR, "listFields", "location,jcr:title"), 400);
        assertFalse(resource(YEAR).getValueMap().containsKey("listFields"));
    }

    @Test
    public void onlyObjectFoldersAreAccepted() throws Exception {
        assertError(get("/content/tenant/pages"), 400);
        assertError(get(OBJECTS + "/../pages"), 400);
        assertError(get(YEAR + "/swiss-open"), 400);
        assertError(get(OBJECTS + "/missing"), 400);
        assertError(get(null), 400);
    }

    // --- nodes.json --------------------------------------------------------------------------

    @Test
    public void nodesJsonListsEffectiveFieldValuesOnly() throws Exception {
        String json = nodes(YEAR);
        assertTrue(json, json.contains("\"listFields\":[\"fromdate\",\"location\"]"));
        assertTrue(json, json.contains(
            "\"listValues\":{\"fromdate\":\"2026-03-14T00:00:00.000Z\",\"location\":\"Winterthur\"}"));
        assertTrue("missing values are left out", json.contains(
            "\"listValues\":{\"fromdate\":\"2026-01-17T00:00:00.000Z\"}"));
        assertTrue(json, json.contains("\"objectPath\":\"" + EVENT_DEFINITION + "\""));
        assertFalse("fields that are not listed are not exposed", json.contains("secret details"));
    }

    @Test
    public void nodesJsonWithoutSettingHasNoListValues() throws Exception {
        String json = nodes(NEWS + "/2026");
        assertFalse(json, json.contains("listValues"));
        assertTrue(json, json.contains("\"objectPath\":\"" + NEWS_DEFINITION + "\""));
    }

    // --- helpers -----------------------------------------------------------------------------

    private void folder(String path, String type, Object... properties) {
        Map<String, Object> props = props(properties);
        props.put(JCR_PRIMARY_TYPE, type);
        context.create().resource(path, props);
    }

    private void object(String path, String definition, Object... properties) {
        Map<String, Object> props = props(properties);
        props.put(JCR_PRIMARY_TYPE, OBJECT_PRIMARY_TYPE);
        props.put(OBJECT_PATH, definition);
        context.create().resource(path, props);
    }

    private static Map<String, Object> props(Object... keyValues) {
        Map<String, Object> props = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            props.put((String) keyValues[i], keyValues[i + 1]);
        }
        return props;
    }

    private Resource resource(String path) {
        return context.resourceResolver().getResource(path);
    }

    private Response get(String path) throws Exception {
        return call("GET", path);
    }

    private Response post(String path, String... params) throws Exception {
        return call("POST", path, params);
    }

    private Response call(String method, String path, String... params) throws Exception {
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
        request.setMethod(method);
        request.setPathInfo(SETTINGS_URL + (path == null ? "" : path));
        Map<String, Object> parameters = new HashMap<>();
        if (path != null) parameters.put(PATH, path);
        for (int i = 0; i + 1 < params.length; i += 2) parameters.put(params[i], params[i + 1]);
        request.setParameterMap(parameters);
        return settingsServlet.handleRequest(new AbstractBaseServlet.Request(request, context.response()));
    }

    private String nodes(String path) throws Exception {
        NodesServlet servlet = new NodesServlet();
        ReferenceLister referenceLister = mock(ReferenceLister.class);
        when(referenceLister.isReferenced(any(Resource.class))).thenReturn(false);
        PrivateAccessor.setField(servlet, "referenceLister", referenceLister);
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(context.resourceResolver(), context.bundleContext());
        Map<String, Object> parameters = new HashMap<>();
        parameters.put(PATH, path);
        request.setParameterMap(parameters);
        return compact(servlet.handleRequest(new AbstractBaseServlet.Request(request, context.response())).getContent());
    }

    private static String content(Response response) throws Exception {
        assertFalse("unexpected error: " + response.getContent(), response instanceof ErrorResponse);
        return compact(response.getContent());
    }

    private static void assertError(Response response, int code) {
        assertTrue("expected an error response", response instanceof ErrorResponse);
        assertEquals(code, ((ErrorResponse) response).getHttpErrorCode());
    }

    /** Pretty-printed JSON without whitespace outside strings is enough for these checks */
    private static String compact(String json) {
        StringBuilder out = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) inString = !inString;
            if (inString || !Character.isWhitespace(c)) out.append(c);
        }
        return out.toString();
    }
}
