package com.peregrine.admin.servlets;

import com.peregrine.replication.Replication.ReplicationException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.factory.ExportException;
import org.apache.sling.models.factory.MissingExporterException;
import org.apache.sling.models.factory.ModelFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.peregrine.commons.util.PerConstants.JACKSON;
import static com.peregrine.commons.util.PerConstants.JCR_CONTENT;
import static com.peregrine.commons.util.PerConstants.OBJECT_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.PAGE_PRIMARY_TYPE;

final class ReplicationValidationUtil {

    private ReplicationValidationUtil() {
    }

    static void validateRenderableContent(
            final List<Resource> toBeReplicated,
            final ModelFactory modelFactory
    ) throws ReplicationException {
        final Set<String> validatedPaths = new HashSet<>();

        for (final Resource resource : toBeReplicated) {
            final Resource renderable = getRenderableContent(resource);
            if (renderable == null || !validatedPaths.add(renderable.getPath())) {
                continue;
            }

            validateRenderableContent(renderable, modelFactory);
        }
    }

    static void validateRenderableContent(
            final Resource renderable,
            final ModelFactory modelFactory
    ) throws ReplicationException {
        try {
            modelFactory.exportModelForResource(renderable, JACKSON, java.util.Map.class, Collections.emptyMap());
        } catch (final ExportException | MissingExporterException | RuntimeException e) {
            throw new ReplicationException("Cannot publish page with model errors: " + renderable.getPath(), e);
        }
    }

    static Resource getRenderableContent(final Resource resource) {
        if (resource == null) {
            return null;
        }

        if (JCR_CONTENT.equals(resource.getName())) {
            return resource;
        }

        final String resourceType = resource.getResourceType();
        if (PAGE_PRIMARY_TYPE.equals(resourceType) || OBJECT_PRIMARY_TYPE.equals(resourceType)) {
            return resource.getChild(JCR_CONTENT);
        }

        return null;
    }
}
