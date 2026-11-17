package com.peregrine.admin.servlets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ObjectFolderSettingsServletTest {

    @Test
    public void parsesCommaSeparatedFields() {
        assertArrayEquals(new String[] {"fromdate", "location"},
            ObjectFolderSettingsServlet.parseListFields(" fromdate , location "));
    }

    @Test
    public void dropsBlanksAndDuplicates() {
        assertArrayEquals(new String[] {"fromdate", "location"},
            ObjectFolderSettingsServlet.parseListFields("fromdate,,location,fromdate,"));
    }

    @Test
    public void emptyValueMeansNoFields() {
        assertEquals(0, ObjectFolderSettingsServlet.parseListFields("").length);
        assertEquals(0, ObjectFolderSettingsServlet.parseListFields(" , ").length);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNamespacedProperties() {
        ObjectFolderSettingsServlet.parseListFields("jcr:title");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsPathsAndMarkup() {
        ObjectFolderSettingsServlet.parseListFields("../secret,<b>");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsTooManyFields() {
        StringBuilder value = new StringBuilder();
        for (int i = 0; i <= ObjectFolderSettingsServlet.MAX_LIST_FIELDS; i++) {
            value.append("field").append(i).append(',');
        }
        ObjectFolderSettingsServlet.parseListFields(value.toString());
    }
}
