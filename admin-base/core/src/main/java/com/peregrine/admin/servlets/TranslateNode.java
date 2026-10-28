package com.peregrine.admin.servlets;

/*-
 * #%L
 * admin base - Core
 * %%
 * Copyright (C) 2025 headwire inc.
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

import com.peregrine.admin.resource.GeminiGeneration;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_TRANSLATE;
import static com.peregrine.admin.util.AdminConstants.*;
import static com.peregrine.commons.ResourceUtils.isPropertyAllowedOnExistingNode;
import static com.peregrine.commons.util.PerConstants.*;
import static com.peregrine.commons.util.PerUtil.*;
import static java.lang.Boolean.parseBoolean;
import static java.util.Objects.isNull;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * Translates a Node by producing an experiences child node with the given language identifier
 *
 * The API Definition can be found in the Swagger Editor configuration:
 *    ui.apps/src/main/content/jcr_root/perapi/definitions/admin.yaml
 */
@Component(
        service = Servlet.class,
        property = {
                SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "translate node at servlet",
                SERVICE_VENDOR + EQUALS + PER_VENDOR,
                SLING_SERVLET_METHODS + EQUALS + POST,
                SLING_SERVLET_METHODS + EQUALS + GET,
                SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_TRANSLATE
        }
)
@SuppressWarnings("serial")
public class TranslateNode extends AbstractBaseServlet {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String LANG = "lang";
    private static final String OVERRIDE = "override";
    private static final String DELETE = "delete";
    private static final String EXPERIENCES = "experiences";
    private static final String PROPERTIES = "properties";
    private static final String TRANSLATIONS = "translations";
    private static final String NODE_PATH_NOT_FOUND = "Node path not found";
    private static final String LANGUAGE_ERROR = "Language missing or not supported";
    private static final String PROPERTIES_MISSING = "Properties missing";
    private static final String NO_PROPERTIES_FOUND = "No properties found";
    private static final String GEMINI_API_ERROR = "Gemini API failed to translate";

    private static final Pattern PATH_PATTERN = Pattern.compile("^/content/([a-z0-9_]+)/(pages|templates|objects|assets)(/.*)?$");

    @Reference
    private GeminiGeneration geminiGeneration;

    @Override
    protected Response handleRequest(Request request) throws IOException {
        if ("GET".equalsIgnoreCase(request.getRequest().getMethod())) {
            JsonResponse jsonResponse = new JsonResponse();
            jsonResponse.writeAttribute("translationConfigured", geminiGeneration.isTranslationConfigured());
            jsonResponse.writeObject("languageMap");

            Map<String, String> languageMap = geminiGeneration.getLanguageMap();
            if (languageMap != null) {
                for (Map.Entry<String, String> entry : languageMap.entrySet()) {
                    jsonResponse.writeAttribute(entry.getKey(), entry.getValue());
                }
            }

            jsonResponse.writeClose();
            return jsonResponse;
        }

        try {
            ResourceResolver resourceResolver = request.getResourceResolver();

            // Get path parameters
            String path = request.getParameter(PATH);
            Node nodeToTranslate = getNode(resourceResolver, path);

            if(isNull(nodeToTranslate)) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(NODE_PATH_NOT_FOUND)
                        .setRequestPath(path);
            }

            // Get language parameters
            String language = request.getParameter(LANG);
            Map<String, String> languageMap = geminiGeneration.getLanguageMap();
            if (isEmpty(language) || languageMap == null || languageMap.get(language) == null) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(LANGUAGE_ERROR);
            }

            // Get properties parameters
            String[] properties = request.getParameterValues(PROPERTIES + "[]");
            if (isNull(properties)) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(PROPERTIES_MISSING);
            }

            // Get optional default translations
            String[] defaultTranslations = request.getParameterValues(TRANSLATIONS + "[]");

            // Get override and delete parameters
            boolean override = parseBoolean(request.getParameter(OVERRIDE, "false"));
            boolean delete = parseBoolean(request.getParameter(DELETE, "false"));

            // Only delete requested props
            if (delete) {
                String experiencePath = path + "/experiences/lang_" + language;
                Node nodeToDeleteProps = getNode(resourceResolver, experiencePath);
                List<String> deletedProps = new ArrayList<>();

                if (isNull(nodeToDeleteProps)) {
                    return new ErrorResponse()
                            .setHttpErrorCode(SC_BAD_REQUEST)
                            .setErrorMessage(NODE_PATH_NOT_FOUND)
                            .setRequestPath(experiencePath);
                }

                List<String> propertiesList = Arrays.asList(properties);
                PropertyIterator propertyIterator = nodeToDeleteProps.getProperties();
                while (propertyIterator.hasNext()) {
                    Property property = propertyIterator.nextProperty();
                    String propertyName = property.getName();
                    if (propertiesList.contains(propertyName)) {
                        property.remove();
                        deletedProps.add(propertyName);
                    }

                    String translatedAt = PER_TRANSLATED_AT + "_" + propertyName.replaceAll(":", "_");
                    if (propertiesList.contains(translatedAt)) {
                        property.remove();
                    }
                }

                if (deletedProps.isEmpty()) {
                    return new ErrorResponse()
                            .setHttpErrorCode(SC_BAD_REQUEST)
                            .setErrorMessage(NO_PROPERTIES_FOUND);
                }

                Calendar timestamp = Calendar.getInstance();
                this.updateRootNodeTranslatedAt(path, timestamp, resourceResolver);

                // Record deletions as translation actions
                nodeToDeleteProps.setProperty(PER_TRANSLATED_AT, timestamp);
                nodeToDeleteProps.setProperty(PER_TRANSLATED_BY, resourceResolver.getUserID());

                resourceResolver.adaptTo(Session.class).save();

                JsonResponse response = new JsonResponse();
                response.writeAttribute(PATH, experiencePath);
                response.writeArray(TRANSLATIONS);
                for (String deletedProp : deletedProps) {
                    response.writeString(deletedProp);
                }
                response.writeClose();
                return response;
            }

            // String translations can map to multiple properties
            Map<String, Set<String>> propertiesToTranslate = new HashMap<>();
            String[] valuesToTranslate;

            // Find properties to translate
            PropertyIterator propertyIterator = nodeToTranslate.getProperties();
            while (propertyIterator.hasNext()) {
                Property property = propertyIterator.nextProperty();
                String propertyName = property.getName();

                // Look for single non-empty string values
                if (isPropertyAllowedOnExistingNode(propertyName) && !property.isMultiple() && Arrays.asList(properties).contains(propertyName)) {
                    String value = property.getString();
                    if (!isEmpty(value)) {
                        if (propertiesToTranslate.containsKey(value)) {
                            propertiesToTranslate.get(value).add(propertyName);
                        } else {
                            propertiesToTranslate.put(value, new HashSet<>(Arrays.asList(propertyName)));
                        }
                    }
                }
            }

            // Values to translate passed to Gemini are unique to limit input tokens
            Set<String> keySet = propertiesToTranslate.keySet();
            valuesToTranslate = keySet.toArray(new String[0]);
            String[] translations;

            if (defaultTranslations != null && defaultTranslations.length > 0) {
                translations = defaultTranslations;
            } else {
                if (!geminiGeneration.isTranslationConfigured()) {
                    return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage("Translation service is not fully configured");
                }
                try {
                    translations = geminiGeneration.translateValues(valuesToTranslate, language);
                } catch (Exception e) {
                    logger.error("Error calling Gemini generation service", e);
                    return new ErrorResponse().setHttpErrorCode(SC_BAD_REQUEST).setErrorMessage(GEMINI_API_ERROR);
                }
            }

            Node experiencesNode = getOrCreateChildNode(resourceResolver, nodeToTranslate, EXPERIENCES);
            Node languageNode = getOrCreateChildNode(resourceResolver, experiencesNode, LANG_PREFIX + language);

            for (int i = 0; i < propertiesToTranslate.size(); i++) {
                try {
                    String translation = translations[i];
                    // Apply translation to experience language node if not empty
                    if (!isEmpty(translation)) {
                        String key = valuesToTranslate[i];
                        for (String propertyName : propertiesToTranslate.get(key)) {
                            if (!languageNode.hasProperty(propertyName) || override) {
                                languageNode.setProperty(propertyName, translation);
                                // Add property timestamp
                                languageNode.setProperty(PER_TRANSLATED_AT + "_" + propertyName.replaceAll(":", "_"), Calendar.getInstance());
                            }
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    logger.info("Accessing translation failed at index: " + i);
                }
            }

            // Add timestamp
            Calendar timestamp = Calendar.getInstance();
            languageNode.setProperty(PER_TRANSLATED_AT, timestamp);
            languageNode.setProperty(PER_TRANSLATED_BY, resourceResolver.getUserID());

            // Update jcr last modified and jcr last modified by
            this.updateRootNodeTranslatedAt(path, timestamp, resourceResolver);

            resourceResolver.adaptTo(Session.class).save();

            JsonResponse response = new JsonResponse();
            response.writeAttribute(PATH, languageNode.getPath());
            response.writeArray(TRANSLATIONS);
            for (String translation : translations) {
                response.writeString(translation);
            }
            response.writeClose();
            return response;

        } catch (RepositoryException e) {
            return new ErrorResponse()
                    .setHttpErrorCode(SC_BAD_REQUEST)
                    .setErrorMessage(e.getMessage())
                    .setException(e);
        }
    }

    private Node getOrCreateChildNode(ResourceResolver resourceResolver, Node parent, String name) throws RepositoryException {
        Node child = getNode(resourceResolver, parent.getPath() + "/" + name);

        if (isNull(child)) {
            child = parent.addNode(name, NT_UNSTRUCTURED);
        }

        return child;
    }

    private void updateRootNodeTranslatedAt(String path, Calendar timestamp, ResourceResolver resourceResolver) throws RepositoryException {
        resourceResolver.refresh();
        Matcher matcher = PATH_PATTERN.matcher(path);
        if (matcher.matches()) {
            String rawType = matcher.group(2);
            String rootPath = path;

            if ("pages".equals(rawType) || "templates".equals(rawType)) {
                String target = "/jcr:content";
                int index = path.indexOf(target);
                if (index != -1) {
                    rootPath = path.substring(0, index + target.length());
                }
            }

            Node rootNode = getNode(resourceResolver, rootPath);
            if (!isNull(rootNode)) {
                rootNode.setProperty(PER_TRANSLATED_AT, timestamp);
                rootNode.setProperty(PER_TRANSLATED_BY, resourceResolver.getUserID());
            }
        }
    }
}
