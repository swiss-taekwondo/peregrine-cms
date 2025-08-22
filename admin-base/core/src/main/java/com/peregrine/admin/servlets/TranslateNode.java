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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSortedMap;
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

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_TRANSLATE;
import static com.peregrine.admin.util.AdminConstants.LANG_PREFIX;
import static com.peregrine.admin.util.AdminConstants.PER_TRANSLATED_AT;
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
                name = "Gemini Prompt",
                description = "Gemini Prompt details to generate AI translations",
                required = false
        )
        String gemini_prompt() default "";
    }

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String LANG = "lang";
    private static final String OVERRIDE = "override";
    private static final String EXPERIENCES = "experiences";
    private static final String PROPERTIES = "properties";
    private static final String NODE_PATH_NOT_FOUND = "Node path not found";
    private static final String LANGUAGE_ERROR = "Language missing or not supported";
    private static final String PROPERTIES_MISSING = "Properties missing";
    private static final String GEMINI_API_KEY_MISSING = "Gemini API Key missing";

    // Supported ISO 639-1 language codes with their matching language names
    private static final Map<String, String> LANGUAGE_MAP = ImmutableSortedMap.<String, String>naturalOrder()
            .put("fr", "French")
            .put("de", "German")
            .put("it", "Italian")
            .put("en", "English")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String geminiAPIKey;
    private String geminiPrompt;

    @Override
    protected Response handleRequest(Request request) throws IOException {
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
            if (isEmpty(language) || LANGUAGE_MAP.get(language) == null) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(LANGUAGE_ERROR);
            }

            // Get properties parameters
            String[] properties = request.getParameterValues(PROPERTIES + "[]", ",");
            if (isNull(properties)) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(PROPERTIES_MISSING);
            }

            // Get override parameters
            boolean override = parseBoolean(request.getParameter(OVERRIDE, "false"));

            if (isEmpty(geminiAPIKey)) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(GEMINI_API_KEY_MISSING);
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

            // Generate Gemini request body
            ObjectNode payload = objectMapper.createObjectNode();

            ObjectNode generationConfig = payload.putObject("generationConfig");
            // Request is faster if thinking is disabled
            generationConfig.putObject("thinkingConfig").put("thinkingBudget", 0);
            // Return a JSON array with translated values to limit output tokens
            generationConfig.put("responseMimeType", "application/json");
            // TODO this causes infinite line breaks or tabs until max token is reached randomly
//            generationConfig.putObject("responseSchema")
//                    .put("type", "array")
//                    .putObject("items")
//                    .put("type", "string");

            ArrayNode contents = payload.putArray("contents");
            ObjectNode contentItem = contents.addObject();
            contentItem.put("role", "user");
            ArrayNode parts = contentItem.putArray("parts");

            // Prompt
            String prompt = "Translate the array in \""+ LANGUAGE_MAP.get(language) +"\": "+ objectMapper.writeValueAsString(valuesToTranslate) +"\\nReturn the translations as array of strings.If a translation is not possible,use empty string." + geminiPrompt;
            logger.info("Gemini Prompt: " + prompt);
            parts.addObject().put("text", prompt);

            String requestBody = objectMapper.writeValueAsString(payload);

            // Gemini request
            HttpRequest geminiRequest = HttpRequest.newBuilder()
                    .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
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

            String[] translations = objectMapper.readValue(translationsJsonString, new TypeReference<>() {});

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
                            }
                        }
                    }
                } catch (ArrayIndexOutOfBoundsException e) {
                    logger.info("Accessing translation failed at index: " + i);
                }
            }

            // Add timestamp
            languageNode.setProperty(PER_TRANSLATED_AT, Calendar.getInstance());

            resourceResolver.adaptTo(Session.class).save();

            JsonResponse response = new JsonResponse();
            response.writeAttribute("path", languageNode.getPath());
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

    @Activate
    @SuppressWarnings("unused")
    void activate(TranslateNode.Configuration configuration) { setup(configuration); }

    @Modified
    @SuppressWarnings("unused")
    void modified(TranslateNode.Configuration configuration) { setup(configuration); }

    private void setup(TranslateNode.Configuration configuration) {
        geminiAPIKey = configuration.gemini_api_key();
        geminiPrompt = configuration.gemini_prompt();
    }
}

