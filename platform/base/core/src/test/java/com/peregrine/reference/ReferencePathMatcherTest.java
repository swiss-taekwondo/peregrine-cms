package com.peregrine.reference;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReferencePathMatcherTest {
    private static final String PATH = "/content/site/assets/photo.jpg";
    private static final String MOVED = "/content/site/assets/archive/photo.jpg";

    @Test
    public void recognizesQuotedAndQueryStringReferences() {
        assertTrue(ReferencePathMatcher.patternFor(PATH).matcher("<img src='" + PATH + "?width=400'>").find());
        assertTrue(ReferencePathMatcher.patternFor(PATH).matcher("<img src=\"" + PATH + "\">").find());
        assertTrue(ReferencePathMatcher.patternFor(PATH).matcher(PATH + ".html").find());
        assertEquals("<img src='" + MOVED + "?width=400'>",
                ReferencePathMatcher.replace("<img src='" + PATH + "?width=400'>", PATH, MOVED));
    }

    @Test
    public void treatsPathAsLiteralAndRequiresARealBoundary() {
        assertFalse(ReferencePathMatcher.patternFor(PATH).matcher("/content/site/assets/photoXjpg").find());
        assertFalse(ReferencePathMatcher.patternFor(PATH).matcher(PATH + "-extra").find());
        assertFalse(ReferencePathMatcher.patternFor(PATH).matcher(PATH + "/child").find());
        assertEquals("/content/site/assets/photoXjpg",
                ReferencePathMatcher.replace("/content/site/assets/photoXjpg", PATH, MOVED));
    }
}
