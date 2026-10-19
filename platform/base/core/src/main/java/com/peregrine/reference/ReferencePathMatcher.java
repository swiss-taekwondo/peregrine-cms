package com.peregrine.reference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Matches resource paths in stored references. **/
public final class ReferencePathMatcher {
    private ReferencePathMatcher() { }

    public static Pattern patternFor(final String path) {
        return Pattern.compile("(^|[\\s\"'=,(])" + Pattern.quote(path)
                + "(?=$|[\\s\"')>,;?&#]|\\.html(?=$|[\\s\"'?&#>]))");
    }

    public static String replace(final String text, final String oldPath, final String newPath) {
        if (text == null || oldPath == null || oldPath.isEmpty() || newPath == null) return text;
        final Matcher matcher = patternFor(oldPath).matcher(text);
        if (!matcher.find()) return text;
        final StringBuffer result = new StringBuffer();
        do {
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + newPath));
        } while (matcher.find());
        matcher.appendTail(result);
        return result.toString();
    }
}
