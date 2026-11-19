package com.peregrine.admin.servlets;

import com.peregrine.admin.resource.SmartPublishCoordinator;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_PUBLISH_STATUS;
import static com.peregrine.commons.util.PerConstants.*;
import static com.peregrine.commons.util.PerUtil.*;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_RESOURCE_TYPES;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

/** State of a queued Smart Publish request: GET publishStatus.json?id=jobId. Only its submitter (or admin) can read it. */
@Component(service = Servlet.class, property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "publish status servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + GET,
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_PUBLISH_STATUS
})
@SuppressWarnings("serial")
public class PublishJobStatus extends AbstractBaseServlet {

    @Reference
    private SmartPublishCoordinator coordinator;

    @Override
    protected Response handleRequest(Request request) throws IOException {
        final String userId = request.getResourceResolver().getUserID();
        final Optional<SmartPublishCoordinator.Ticket> ticket = coordinator.getTicket(request.getParameter("id"))
                .filter(t -> "admin".equals(userId) || t.getUserId().equals(userId));
        if (ticket.isEmpty()) {
            return new ErrorResponse().setHttpErrorCode(SC_NOT_FOUND)
                    .setErrorMessage("Publish job not found. It may have expired or the server restarted.");
        }
        final JsonResponse response = new JsonResponse()
                .writeAttribute("state", ticket.get().getState().name().toLowerCase(Locale.ROOT))
                .writeAttribute("message", ticket.get().getMessage())
                .writeAttribute("sourcePath", ticket.get().getSourcePath())
                .writeAttribute("batchSize", ticket.get().getBatchSize())
                .writeAttribute("batchId", ticket.get().getBatchId());
        // All paths published in the same batch (available once it finished)
        response.writeArray("paths");
        for (String path : ticket.get().getBatchPaths()) {
            response.writeString(path);
        }
        response.writeClose();
        return response;
    }
}
