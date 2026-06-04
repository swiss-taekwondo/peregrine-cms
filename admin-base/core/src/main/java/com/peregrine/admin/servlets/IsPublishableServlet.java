package com.peregrine.admin.servlets;

import com.peregrine.commons.servlets.AbstractBaseServlet;
import com.peregrine.intra.IntraSlingCaller;
import com.peregrine.render.RenderService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ModelFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_IS_PUBLISHABLE;
import static com.peregrine.commons.util.PerConstants.PATH;
import static com.peregrine.commons.util.PerUtil.EQUALS;
import static com.peregrine.commons.util.PerUtil.GET;
import static com.peregrine.commons.util.PerUtil.PER_PREFIX;
import static com.peregrine.commons.util.PerUtil.PER_VENDOR;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

@Component(
    service = Servlet.class,
    property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "Is Publishable Servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + GET,
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_IS_PUBLISHABLE
    }
)
@SuppressWarnings("serial")
public final class IsPublishableServlet extends AbstractBaseServlet {

    @Reference
    private ModelFactory modelFactory;

    @Reference
    private IntraSlingCaller intraSlingCaller;

    @Reference
    private RenderService renderService;

    @Override
    protected Response handleRequest(final Request request) throws IOException {
        final String path = request.getParameter(PATH);
        if (isBlank(path)) {
            return new ErrorResponse()
                .setHttpErrorCode(SC_BAD_REQUEST)
                .setErrorMessage("No Path provided")
                .setRequestPath(path);
        }

        final Resource resource = request.getResourceResolver().getResource(path);
        if (resource == null) {
            return new JsonResponse()
                .writeAttribute("result", false)
                .writeAttribute("reason", "Path not found: " + path);
        }

        final Resource renderable = ReplicationValidationUtil.getRenderableContent(resource);
        if (renderable == null) {
            return new JsonResponse()
                .writeAttribute("result", true)
                .writeAttribute("reason", "");
        }

        try {
            ReplicationValidationUtil.validateRenderableContent(renderable, modelFactory);
            ReplicationValidationUtil.validateRenderableComponentSources(renderable);
            ReplicationValidationUtil.validateRenderableContent(renderable, renderService);
            ReplicationValidationUtil.validateRenderablePage(resource, intraSlingCaller);
            return new JsonResponse()
                .writeAttribute("result", true)
                .writeAttribute("reason", "");
        } catch (final com.peregrine.replication.Replication.ReplicationException e) {
            return new JsonResponse()
                .writeAttribute("result", false)
                .writeAttribute("reason", e.getMessage());
        }
    }
}
