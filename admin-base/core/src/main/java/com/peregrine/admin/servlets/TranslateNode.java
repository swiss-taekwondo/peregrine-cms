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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.servlet.Servlet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
@Designate(ocd = TranslateNode.Configuration.class)
@SuppressWarnings("serial")
public class TranslateNode extends AbstractBaseServlet {
    @ObjectClassDefinition(
            name = "Peregrine: Translate Node Servlet",
            description = "Translates Node via Experiences"
    )
    @interface Configuration {
        @AttributeDefinition(
                name = "Gemini API Key",
                description = "Gemini API Key to generate AI translations",
                required = true
        )
        String gemini_api_key() default "";

        @AttributeDefinition(
                name = "Gemini Model",
                description = "Gemini Model to use for AI translations",
                required = false
        )
        String gemini_model() default "";

        @AttributeDefinition(
                name = "Gemini Prompt",
                description = "Gemini Prompt details to generate AI translations",
                required = false
        )
        String gemini_prompt() default "";

        @AttributeDefinition(
                name = "Language Map",
                description = "Language Map Configuration. Format: ISO 639-1 language code = language name",
                required = true
        )
        String[] language_map();
    }

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
    private static final String GEMINI_API_KEY_MISSING = "Gemini API Key missing";
    private static final String GEMINI_MODEL_MISSING = "Gemini Model missing";

    private static final Pattern PATH_PATTERN = Pattern.compile("^/content/([a-z0-9_]+)/(pages|templates|objects)(/.*)?$");

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String geminiAPIKey;
    private String geminiModel;
    private String geminiPrompt;
    private Map<String, String> languageMap;

    @Override
    protected Response handleRequest(Request request) throws IOException {
        // Handle GET request to expose the language map
        if ("GET".equalsIgnoreCase(request.getRequest().getMethod())) {
            JsonResponse jsonResponse = new JsonResponse();
            jsonResponse.writeObject("languageMap");

            // Iterate over the private memory map and write it to the JSON response
            for (Map.Entry<String, String> entry : languageMap.entrySet()) {
                jsonResponse.writeAttribute(entry.getKey(), entry.getValue());
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
            if (isEmpty(language) || languageMap.get(language) == null) {
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
                        }
                        else {
                            propertiesToTranslate.put(property.getString(), new HashSet<>(Arrays.asList(propertyName)));
                        }
                    }
                }
            }

            // Values to translate passed to Gemini are unique to limit input tokens
            Set<String> keySet = propertiesToTranslate.keySet();
            valuesToTranslate = keySet.toArray(new String[keySet.size()]);

            // Holds the translations (either provided by Gemini or by request)
            String[] translations;
            if (defaultTranslations != null && defaultTranslations.length > 0) {
                translations = defaultTranslations;
            }
            else {
                if (isEmpty(geminiAPIKey)) {
                    return new ErrorResponse()
                            .setHttpErrorCode(SC_BAD_REQUEST)
                            .setErrorMessage(GEMINI_API_KEY_MISSING);
                }

                if (isEmpty(geminiModel)) {
                    return new ErrorResponse()
                            .setHttpErrorCode(SC_BAD_REQUEST)
                            .setErrorMessage(GEMINI_MODEL_MISSING);
                }

                // Generate Gemini request body
                ObjectNode payload = objectMapper.createObjectNode();

                ObjectNode generationConfig = payload.putObject("generationConfig");
                // Request is faster if thinking is disabled
                generationConfig.putObject("thinkingConfig").put("thinkingBudget", 0);
                // Return a JSON array with translated values to limit output tokens
                generationConfig.put("responseMimeType", "application/json");
                // TODO this caused infinite line breaks or tabs until max token is reached randomly with Gemini Flash 2.5
                generationConfig.putObject("responseSchema")
                        .put("type", "array")
                        .putObject("items")
                        .put("type", "string");

                ArrayNode contents = payload.putArray("contents");
                ObjectNode contentItem = contents.addObject();
                contentItem.put("role", "user");
                ArrayNode parts = contentItem.putArray("parts");

                // Prompt
                String prompt = "Translate the array in \""+ languageMap.get(language) +"\": "+ objectMapper.writeValueAsString(valuesToTranslate) +"\\nReturn the translations as array of strings.If a translation is not possible,use empty string." + geminiPrompt;
                logger.info("Gemini Prompt: " + prompt);
                parts.addObject().put("text", prompt);

                String requestBody = objectMapper.writeValueAsString(payload);

                // Gemini request
                HttpRequest geminiRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"+ geminiModel +":generateContent"))
                        .header("x-goog-api-key", geminiAPIKey)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpClient httpClient = HttpClient.newHttpClient();
                HttpResponse<String> httpResponse = httpClient.send(geminiRequest, HttpResponse.BodyHandlers.ofString());;

                if (httpResponse.statusCode() != 200) {
                    return new ErrorResponse()
                            .setHttpErrorCode(SC_BAD_REQUEST)
                            .setErrorMessage(httpResponse.body());
                }

                // Parse Gemini translation response
                JsonNode responseNode = objectMapper.readTree(httpResponse.body());
                String translationsJsonString = responseNode
                        .path("candidates").path(0)
                        .path("content").path("parts").path(0)
                        .path("text").asText();

                logger.info("Gemini Response: " + translationsJsonString);

                translations = objectMapper.readValue(translationsJsonString, new TypeReference<>() {});
            }

            // Create translation experiences nodes
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
        }
        catch (RepositoryException | InterruptedException | MismatchedInputException e) {
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

    @Activate
    @SuppressWarnings("unused")
    void activate(TranslateNode.Configuration configuration) { setup(configuration); }

    @Modified
    @SuppressWarnings("unused")
    void modified(TranslateNode.Configuration configuration) { setup(configuration); }

    private void setup(TranslateNode.Configuration configuration) {
        geminiAPIKey = configuration.gemini_api_key();
        geminiModel = configuration.gemini_model();
        geminiPrompt = configuration.gemini_prompt();

        languageMap = new HashMap<>();
        String[] languages = configuration.language_map();
        for (String language : languages) {
            String[] tokens = language.split("=");
            if (tokens.length == 2 && isNotEmpty(tokens[0]) && isNotEmpty(tokens[1])) {
                languageMap.put(tokens[0], tokens[1]);
            }
        }
    }
}

