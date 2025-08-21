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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.servlet.Servlet;
import java.io.IOException;
import java.util.*;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_LIST_TRANSLATIONS;
import static com.peregrine.admin.util.AdminConstants.*;
import static com.peregrine.commons.ResourceUtils.isPropertyAllowedOnExistingNode;
import static com.peregrine.commons.util.PerConstants.*;
import static com.peregrine.commons.util.PerUtil.*;
import static java.util.Objects.isNull;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/**
 * List translations experiences given a translation model.
 *
 * The API Definition can be found in the Swagger Editor configuration:
 *    ui.apps/src/main/content/jcr_root/perapi/definitions/admin.yaml
 */
@Component(
    service = Servlet.class,
    property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "list translations servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + POST,
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_LIST_TRANSLATIONS,
        SLING_SERVLET_SELECTORS + EQUALS + JSON
    }
)
@SuppressWarnings("serial")
public class ListTranslations extends AbstractBaseServlet {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String PER_PAGE = "per:Page";
    private static final String PER_OBJECT = "per:Object";
    private static final String NODE_PATH_NOT_FOUND = "Node path not found";
    private static final String MODEL_PATH_NOT_FOUND = "Model path not found";
    private static final String WRONG_NODE_TYPE = "Wrong Node type";
    private static final List<String> EXCLUDED_PROPERTIES = Arrays.asList(NAME, PATH, COMPONENT);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected Response handleRequest(Request request) throws IOException {
        try {
            ResourceResolver resourceResolver = request.getResourceResolver();
            Session session = request.getResourceResolver().adaptTo(Session.class);

            // Get path parameters
            String path = request.getParameter(PATH);
            Node pageOrObject = getNode(resourceResolver, path);

            if(isNull(pageOrObject)) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(NODE_PATH_NOT_FOUND)
                        .setRequestPath(path);
            }

            // Get model parameters
            String data = request.getParameter(MODEL);
            if (isEmpty(data)) {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(MODEL_PATH_NOT_FOUND)
                        .setRequestPath(path);
            }

            Map<String, List<String>> model = new HashMap<>();
            model.putAll(objectMapper.readValue(data, Map.class));

            String nodeType = pageOrObject.getPrimaryNodeType().toString();
            List<Node> nodes = new ArrayList<>();

            if (PER_PAGE.equals(nodeType)) {
                List<String> conditions = new ArrayList<>();
                for (String translationRef : new ArrayList<>(model.keySet())) {
                    conditions.add("["+ PER_TRANSLATE_REF +"]='" + translationRef + "'");
                }

                // Find all page nodes flagged with "per:TranslateRef"
                String statement =  "SELECT * from [nt:unstructured] as n " +
                        "WHERE ("+ String.join(" OR ", conditions) +") " +
                        "AND ISDESCENDANTNODE(n, '" + path + "/jcr:content')";

                QueryManager queryManager = session.getWorkspace().getQueryManager();
                Query query = queryManager.createQuery(statement, Query.JCR_SQL2);
                QueryResult queryResult = query.execute();

                NodeIterator nodeIterator = queryResult.getNodes();

                while (nodeIterator.hasNext()) {
                    Node node = nodeIterator.nextNode();
                    nodes.add(node);
                }
            }
            else if (PER_OBJECT.equals(nodeType)){
                nodes.add(pageOrObject);
            }
            else {
                return new ErrorResponse()
                        .setHttpErrorCode(SC_BAD_REQUEST)
                        .setErrorMessage(WRONG_NODE_TYPE)
                        .setRequestPath(path);
            }

            JsonResponse response = new JsonResponse();
            ArrayNode translationNodes = getTranslationNodes(nodes, model, resourceResolver);
            response.writeAttributeRaw("nodes", objectMapper.writeValueAsString(translationNodes));

            return response;
        }
        catch (RepositoryException e) {
            return new ErrorResponse()
                    .setHttpErrorCode(SC_BAD_REQUEST)
                    .setErrorMessage(e.getMessage())
                    .setException(e);
        }
    }

    private ArrayNode getTranslationNodes(List<Node> nodes, Map<String, List<String>> model, ResourceResolver resourceResolver) throws RepositoryException {
        ArrayNode foundNodes = objectMapper.createArrayNode();

        for (Node node : nodes) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            objectNode.put(PATH, node.getPath());

            String translationRef = node.getProperty(PER_TRANSLATE_REF).getString();
            objectNode.put(REFERENCE_NAME, translationRef);
            List<String> properties = model.get(translationRef);

            ObjectNode originalNode = objectMapper.createObjectNode();

            // Find original properties based on the model
            for (String propertyName : properties) {
                if (node.hasProperty(propertyName )) {
                    Property property = node.getProperty(propertyName);

                    // Look for single non-empty string values
                    if (isPropertyAllowedOnExistingNode(propertyName) && !property.isMultiple()) {
                        String value = property.getString();
                        if (!isEmpty(value)) {
                            originalNode.put(propertyName, value);
                        }
                    }
                }
            }

            // Skip empty nodes
            if (originalNode.isEmpty()) {
                continue;
            }

            objectNode.set("original", originalNode);

            // Find translated properties for each experience language
            Resource resource = resourceResolver.getResource(node.getPath());
            Resource experiences = resource.getChild("experiences");

            if (!isNull(experiences)) {
                ObjectNode translationNode = objectMapper.createObjectNode();

                for (Resource experienceResource : experiences.getChildren()){
                    ObjectNode langNode = objectMapper.createObjectNode();

                    if (experienceResource.getName().startsWith(LANG_PREFIX)) {
                        Node experienceNode = experienceResource.adaptTo(Node.class);

                        // Get Timestamp
                        if (experienceNode.hasProperty(PER_TRANSLATED_AT)) {
                            langNode.put(PER_TRANSLATED_AT, experienceNode.getProperty(PER_TRANSLATED_AT).getString());
                        }

                        PropertyIterator propertyIterator = experienceNode.getProperties();
                        while (propertyIterator.hasNext()) {
                            Property property = propertyIterator.nextProperty();
                            String propertyName = property.getName();

                            // Look for single non-empty string values
                            if (isPropertyAllowedOnExistingNode(propertyName) && !property.isMultiple() && !EXCLUDED_PROPERTIES.contains(propertyName)) {
                                String value = property.getString();
                                if (!isEmpty(value)) {
                                    langNode.put(propertyName, value);
                                }
                            }
                        }

                        // Remove lang prefix from lang
                        String lang = experienceResource.getName().substring(LANG_PREFIX.length());
                        translationNode.set(lang, langNode);
                    }
                }

                objectNode.set("translations", translationNode);
            }

            foundNodes.add(objectNode);
        }

        return foundNodes;
    }
}
