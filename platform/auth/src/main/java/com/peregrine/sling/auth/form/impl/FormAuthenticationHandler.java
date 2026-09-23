/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.peregrine.sling.auth.form.impl;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

import javax.jcr.Credentials;
import javax.jcr.SimpleCredentials;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.codec.binary.Base64;
import org.apache.felix.jaas.LoginModuleFactory;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.PropertyOption;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.auth.Authenticator;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.auth.core.AuthConstants;
import org.apache.sling.auth.core.AuthUtil;
import org.apache.sling.auth.core.spi.AuthenticationHandler;
import org.apache.sling.auth.core.spi.AuthenticationInfo;
import org.apache.sling.auth.core.spi.DefaultAuthenticationFeedbackHandler;
import com.peregrine.sling.auth.form.FormReason;
import com.peregrine.sling.auth.form.impl.jaas.FormCredentials;
import com.peregrine.sling.auth.form.impl.jaas.JaasHelper;
import org.apache.sling.commons.osgi.OsgiUtil;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The <code>FormAuthenticationHandler</code> class implements the authorization
 * steps based on a cookie.
 */
@Component(label = "%auth.form.name", description = "%auth.form.description", metatype = true, name = "com.peregrine.sling.auth.form.FormAuthenticationHandler")
@Properties( {
    @Property(name = Constants.SERVICE_DESCRIPTION, value = "Peregrine Form Based Authentication Handler"),
    @Property(name = AuthenticationHandler.PATH_PROPERTY, value = "/", cardinality = 50),
    @Property(name = AuthenticationHandler.TYPE_PROPERTY, value = HttpServletRequest.FORM_AUTH, propertyPrivate = true),
    @Property(name = Constants.SERVICE_RANKING, intValue = 1, propertyPrivate = false),

    @Property(name = LoginModuleFactory.JAAS_CONTROL_FLAG, value = "sufficient"),
    @Property(name = LoginModuleFactory.JAAS_REALM_NAME, value = "jackrabbit.oak"),
    @Property(name = LoginModuleFactory.JAAS_RANKING, intValue = 500)
})
@Service
public class FormAuthenticationHandler extends DefaultAuthenticationFeedbackHandler implements AuthenticationHandler {
    /** default log */
    private final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * The resource resolver factory used to resolve the login form as a resource
     */
    @Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL_UNARY)
    private volatile ResourceResolverFactory resourceResolverFactory;

    /**
     * Extracts cookie/session based credentials from the request. Returns
     * <code>null</code> if the handler assumes HTTP Basic authentication would
     * be more appropriate, if no form fields are present in the request and if
     * the secure user data is not present either in the cookie or an HTTP
     * Session.
     */
    @Override
    public AuthenticationInfo extractCredentials(HttpServletRequest request,
            HttpServletResponse response) {

        AuthenticationInfo info = null;
        log.debug("extractCredentials");
        String URI = request.getRequestURI().toString();
        if(URI.equals("/")) {

            // copy of the params, was creating endless loop otherwise
            final Map parameterMap = request.getParameterMap();
            HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(request) {

                @Override
                public String getRequestURI() {
                    return "/content/example/pages/index.html";
                }

                @Override
                public StringBuffer getRequestURL() {
                    return new StringBuffer("http://localhost:8080/content/example/pages/index.html");
                }

                @Override
                public Map getParameterMap() {
                    return parameterMap;
                }
            };

            ResourceResolver resourceResolver = null;
            try {
                log.debug("getting resource resolver");
                resourceResolver = resourceResolverFactory.getAdministrativeResourceResolver(null);
                log.debug("getting resource");
                Resource resource = resourceResolver.resolve("/content/example/pages/index");

                RequestDispatcherOptions rdOtions = new RequestDispatcherOptions(
                        RequestDispatcherOptions.OPT_REPLACE_SELECTORS + "=data"
                );
                try {
                    request.getRequestDispatcher("/content/example/pages/index.html").forward(wrappedRequest, response);
                } catch (ServletException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                log.debug("after forward");
                return null;

            } catch (LoginException e) {
                log.error("Unable to get a resource resolver to include for the login resource. Will redirect instead.");
            } finally {
                if (resourceResolver != null) {
                    resourceResolver.close();
                }
            }
        }

        return null;
    }

    @Override
    public boolean requestCredentials(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws IOException {
        return false;
    }

    @Override
    public String toString() {
        return "Peregrine Form Based Authentication Handler";
    }

    public boolean isValid(final Credentials credentials) {
        // no authdata, not valid
        return false;
    }

    // ---------- SCR Integration ----------------------------------------------

    /**
     * Called by SCR to activate the authentication handler.
     *
     * @throws InvalidKeyException
     * @throws NoSuchAlgorithmException
     * @throws IllegalStateException
     * @throws UnsupportedEncodingException
     */
    protected void activate(ComponentContext componentContext)
            throws InvalidKeyException, NoSuchAlgorithmException,
            IllegalStateException, UnsupportedEncodingException {

        Dictionary<?, ?> properties = componentContext.getProperties();
    }

    @Deactivate
    protected void deactivate() {
    }
}