package com.peregrine.admin.servlets;

import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.testing.mock.sling.ResourceResolverType;
import org.apache.sling.testing.mock.sling.junit.SlingContext;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

public class CheckLinkServletTest {

    private final SlingContext context = new SlingContext(ResourceResolverType.JCR_MOCK);
    private ResourceResolver resourceResolver;

    @Before
    public void setUp() throws Exception {
        resourceResolver = context.resourceResolver();

        context.create().resource("/var/linkchecker/urls/u-a",
            "url", "https://example.com/a",
            "json", "{\"ok\":true}",
            "checkedAt", 1L);
        context.create().resource("/var/linkchecker/pages/content/stkd/pages/Test/u-a",
            "url", "https://example.com/a",
            "checkedAt", 1L,
            "pagePath", "/content/stkd/pages/Test",
            "cachePath", "/var/linkchecker/urls/u-a");

        context.create().resource("/var/linkchecker/urls/u-b",
            "url", "https://example.com/b",
            "json", "{\"ok\":true}",
            "checkedAt", 1L);
        context.create().resource("/var/linkchecker/pages/content/stkd/pages/Test/u-b",
            "url", "https://example.com/b",
            "checkedAt", 1L,
            "pagePath", "/content/stkd/pages/Test",
            "cachePath", "/var/linkchecker/urls/u-b");
        context.create().resource("/var/linkchecker/pages/content/stkd/pages/Other/u-b",
            "url", "https://example.com/b",
            "checkedAt", 1L,
            "pagePath", "/content/stkd/pages/Other",
            "cachePath", "/var/linkchecker/urls/u-b");

        resourceResolver.commit();
    }

    @Test
    public void cleanupPageCacheRemovesOrphanedPageReferencesAndCanonicalEntries() throws Exception {
        CheckLinkServlet.cleanupPageCache(resourceResolver, "/content/stkd/pages/Test");
        resourceResolver.commit();

        assertNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Test"));
        assertNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Test/u-a"));
        assertNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Test/u-b"));
        assertNull(resourceResolver.getResource("/var/linkchecker/urls/u-a"));

        assertNotNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Other/u-b"));
        assertNotNull(resourceResolver.getResource("/var/linkchecker/urls/u-b"));
    }

    @Test
    public void movePageCacheMovesPageReferencesWithoutDuplicatingCanonicalEntries() throws Exception {
        CheckLinkServlet.movePageCache(resourceResolver, "/content/stkd/pages/Test", "/content/stkd/pages/home");
        resourceResolver.commit();

        assertNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Test"));
        assertNotNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/home/u-a"));
        assertNotNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/home/u-b"));
        assertNotNull(resourceResolver.getResource("/var/linkchecker/urls/u-a"));
        assertNotNull(resourceResolver.getResource("/var/linkchecker/urls/u-b"));

        assertNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Test/u-a"));
        assertNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Test/u-b"));
        assertNotNull(resourceResolver.getResource("/var/linkchecker/pages/content/stkd/pages/Other/u-b"));
    }
}
