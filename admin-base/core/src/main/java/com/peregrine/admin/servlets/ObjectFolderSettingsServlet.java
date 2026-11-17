package com.peregrine.admin.servlets;

/*-
 * #%L
 * admin base - Core
 * %%
 * Copyright (C) 2017 headwire inc.
 * %%
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * #L%
 */

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_OBJECT_FOLDER_SETTINGS;
import static com.peregrine.commons.util.PerConstants.JCR_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.OBJECT_PATH;
import static com.peregrine.commons.util.PerConstants.OBJECT_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.PATH;
import static com.peregrine.commons.util.PerConstants.SLING_FOLDER;
import static com.peregrine.commons.util.PerConstants.SLING_ORDERED_FOLDER;
import static com.peregrine.commons.util.PerUtil.EQUALS;
import static com.peregrine.commons.util.PerUtil.GET;
import static com.peregrine.commons.util.PerUtil.PER_PREFIX;
import static com.peregrine.commons.util.PerUtil.PER_VENDOR;
import static com.peregrine.commons.util.PerUtil.POST;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

import com.peregrine.commons.servlets.AbstractBaseServlet;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import javax.servlet.Servlet;
import org.apache.sling.api.resource.ModifiableValueMap;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;

/**
 * Reads and changes the list settings of an object folder: which object fields the admin's object
 * list shows next to each object ({@code listFields}). A folder without its own setting inherits
 * the one of the closest ancestor folder that has one.
 *
 * GET  /perapi/admin/objectFolderSettings.json/content/tenant/objects/folder
 * POST /perapi/admin/objectFolderSettings.json/content/tenant/objects/folder
 *      listFields=fromdate,location   own setting (empty value = show no fields)
 *      inherit=true                   remove the own setting, use the parent folder's again
 *
 * The API Definition can be found in the Swagger Editor configuration:
 *    ui.apps/src/main/content/jcr_root/perapi/definitions/admin.yaml
 */
@Component(
    service = Servlet.class,
    property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "Object Folder Settings Servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + GET,
        SLING_SERVLET_METHODS + EQUALS + POST,
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_OBJECT_FOLDER_SETTINGS
    }
)
@SuppressWarnings("serial")
public class ObjectFolderSettingsServlet extends AbstractBaseServlet {

    public static final String LIST_FIELDS = NodesServlet.LIST_FIELDS;
    public static final String INHERIT = "inherit";
    public static final String INHERITED = "inherited";
    public static final String INHERITED_FROM = "inheritedFrom";
    public static final String EFFECTIVE_LIST_FIELDS = "effectiveListFields";
    public static final String OBJECT_DEFINITIONS = "objectDefinitions";

    static final int MAX_LIST_FIELDS = 12;
    /** How many nodes below the folder are looked at to find the object definitions in use */
    static final int MAX_SCANNED_NODES = 5000;

    private static final Pattern OBJECTS_FOLDER_PATH = Pattern.compile("^/content/[^/]+/objects(/.+)?$");
    private static final Pattern FIELD_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    @Override
    protected Response handleRequest(Request request) throws IOException {
        String path = request.getParameter(PATH);
        if (path == null || !OBJECTS_FOLDER_PATH.matcher(path).matches()) {
            return error(SC_BAD_REQUEST, "Path must be an object folder (/content/<tenant>/objects/...)", path);
        }
        Resource folder = request.getResourceResolver().getResource(path);
        if (folder == null || !isFolder(folder)) {
            return error(SC_BAD_REQUEST, "Not an object folder", path);
        }
        if (request.isPost()) {
            try {
                if (request.getBooleanParameter(INHERIT, false)) {
                    removeListFields(folder);
                } else {
                    String value = request.getParameter(LIST_FIELDS);
                    if (value == null) {
                        return error(SC_BAD_REQUEST, "Provide listFields (comma separated) or inherit=true", path);
                    }
                    String[] fields = parseListFields(value);
                    setListFields(folder, fields);
                }
                request.getResourceResolver().commit();
            } catch (IllegalArgumentException e) {
                return error(SC_BAD_REQUEST, e.getMessage(), path);
            } catch (PersistenceException | UnsupportedOperationException e) {
                logger.warn("Could not save the list fields of {}", path, e);
                request.getResourceResolver().revert();
                return error(SC_FORBIDDEN, "Could not save the folder settings", path);
            }
            folder = request.getResourceResolver().getResource(path);
        }
        return describe(folder);
    }

    private Response describe(Resource folder) throws IOException {
        JsonResponse answer = new JsonResponse();
        answer.writeAttribute(PATH, folder.getPath());
        ValueMap props = folder.getValueMap();
        boolean own = props.containsKey(LIST_FIELDS);
        if (own) {
            writeArray(answer, LIST_FIELDS, ownListFields(folder));
        }
        answer.writeAttribute(INHERITED, !own);
        Resource source = findListFieldsSource(folder);
        if (!own && source != null) {
            answer.writeAttribute(INHERITED_FROM, source.getPath());
        }
        writeArray(answer, EFFECTIVE_LIST_FIELDS, NodesServlet.findListFields(folder));
        writeArray(answer, OBJECT_DEFINITIONS, findObjectDefinitions(folder).toArray(new String[0]));
        return answer;
    }

    private static void writeArray(JsonResponse json, String name, String[] values) throws IOException {
        json.writeArray(name);
        for (String value : values) {
            json.writeString(value);
        }
        json.writeClose();
    }

    private ErrorResponse error(int code, String message, String path) throws IOException {
        return new ErrorResponse().setHttpErrorCode(code).setErrorMessage(message).setRequestPath(path);
    }

    static boolean isFolder(Resource resource) {
        String type = resource.getValueMap().get(JCR_PRIMARY_TYPE, String.class);
        return SLING_FOLDER.equals(type) || SLING_ORDERED_FOLDER.equals(type);
    }

    /**
     * Parses a comma separated list of field names. Blank entries are dropped, duplicates removed.
     * @throws IllegalArgumentException for invalid names or too many fields
     */
    static String[] parseListFields(String value) {
        Set<String> fields = new LinkedHashSet<>();
        for (String part : value.split(",")) {
            String field = part.trim();
            if (field.isEmpty()) continue;
            if (!FIELD_NAME.matcher(field).matches()) {
                throw new IllegalArgumentException("Invalid field name: " + field);
            }
            fields.add(field);
        }
        if (fields.size() > MAX_LIST_FIELDS) {
            throw new IllegalArgumentException("At most " + MAX_LIST_FIELDS + " fields can be shown");
        }
        return fields.toArray(new String[0]);
    }

    static String[] ownListFields(Resource folder) {
        String[] fields = folder.getValueMap().get(LIST_FIELDS, String[].class);
        return fields == null ? new String[0] : fields;
    }

    static void setListFields(Resource folder, String[] fields) {
        ModifiableValueMap props = folder.adaptTo(ModifiableValueMap.class);
        if (props == null) throw new UnsupportedOperationException("Folder is not modifiable");
        props.put(LIST_FIELDS, fields);
    }

    static void removeListFields(Resource folder) {
        ModifiableValueMap props = folder.adaptTo(ModifiableValueMap.class);
        if (props == null) throw new UnsupportedOperationException("Folder is not modifiable");
        props.remove(LIST_FIELDS);
    }

    /** The folder (this one or an ancestor) whose listFields apply, or null */
    static Resource findListFieldsSource(Resource folder) {
        for (Resource current = folder; current != null; current = current.getParent()) {
            if (current.getValueMap().containsKey(LIST_FIELDS)) return current;
        }
        return null;
    }

    /** Object definitions (objectPath) used by the objects below the folder, in the order found */
    static Set<String> findObjectDefinitions(Resource folder) {
        Set<String> definitions = new LinkedHashSet<>();
        Deque<Resource> queue = new ArrayDeque<>();
        queue.add(folder);
        int scanned = 0;
        while (!queue.isEmpty() && scanned < MAX_SCANNED_NODES) {
            Resource current = queue.poll();
            List<Resource> folders = new ArrayList<>();
            for (Resource child : current.getChildren()) {
                if (++scanned > MAX_SCANNED_NODES) break;
                ValueMap props = child.getValueMap();
                if (OBJECT_PRIMARY_TYPE.equals(props.get(JCR_PRIMARY_TYPE, String.class))) {
                    String definition = props.get(OBJECT_PATH, String.class);
                    if (definition != null && !definition.isEmpty()) definitions.add(definition);
                } else if (isFolder(child)) {
                    folders.add(child);
                }
            }
            queue.addAll(folders);
        }
        return definitions;
    }
}
