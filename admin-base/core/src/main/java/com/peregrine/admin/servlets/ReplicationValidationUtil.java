package com.peregrine.admin.servlets;

import com.peregrine.replication.Replication.ReplicationException;
import com.peregrine.intra.IntraSlingCaller;
import com.peregrine.render.RenderService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.models.factory.ExportException;
import org.apache.sling.models.factory.MissingExporterException;
import org.apache.sling.models.factory.ModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.peregrine.commons.util.PerConstants.JACKSON;
import static com.peregrine.commons.util.PerConstants.JCR_CONTENT;
import static com.peregrine.commons.util.PerConstants.OBJECT_PRIMARY_TYPE;
import static com.peregrine.commons.util.PerConstants.NT_UNSTRUCTURED;
import static com.peregrine.commons.util.PerConstants.PAGE_PRIMARY_TYPE;

final class ReplicationValidationUtil {

    private static final Logger log = LoggerFactory.getLogger(ReplicationValidationUtil.class);
    private static final Pattern EXPLICIT_JS_THROW = Pattern.compile("\\bthrow\\s+(new\\s+)?[A-Za-z_$][A-Za-z0-9_$]*\\b");

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
            final List<Resource> toBeReplicated,
            final RenderService renderService
    ) throws ReplicationException {
        final Set<String> validatedPaths = new HashSet<>();

        for (final Resource resource : toBeReplicated) {
            final Resource renderable = getRenderableContent(resource);
            if (renderable == null || !validatedPaths.add(renderable.getPath())) {
                continue;
            }

            validateRenderableContentRender(renderable, renderService, new HashSet<>());
        }
    }

    static void validateRenderableComponentSources(final List<Resource> toBeReplicated) throws ReplicationException {
        final Set<String> validatedPaths = new HashSet<>();

        for (final Resource resource : toBeReplicated) {
            final Resource renderable = getRenderableContent(resource);
            if (renderable == null || !validatedPaths.add(renderable.getPath())) {
                continue;
            }

            validateRenderableComponentSources(renderable, new HashSet<>(), new HashSet<>());
        }
    }

    static void validateRenderablePage(
            final Resource resource,
            final IntraSlingCaller intraSlingCaller
    ) throws ReplicationException {
        final Resource renderablePage = getRenderablePage(resource);
        if (renderablePage == null || intraSlingCaller == null) {
            return;
        }

        final Resource renderableContent = getRenderableContent(renderablePage);
        if (renderableContent != null) {
            validateSsrRender(renderableContent, intraSlingCaller);
        }
        validateSsrRender(renderablePage, intraSlingCaller);
    }

    private static void validateSsrRender(
            final Resource renderable,
            final IntraSlingCaller intraSlingCaller
    ) throws ReplicationException {
        try {
            log.debug("Validating page render for publish: {} [{}]", renderable.getPath(), renderable.getResourceType());
            intraSlingCaller.call(
                intraSlingCaller.createContext()
                    .setResourceResolver(renderable.getResourceResolver())
                    .setResource(renderable)
                    .setPath(renderable.getPath())
                    .setSelectors("ssr")
                    .setExtension("html")
            );
        } catch (final IntraSlingCaller.CallException e) {
            log.warn("Publish render validation failed for {} [{}]", renderable.getPath(), renderable.getResourceType(), e);
            throw new ReplicationException(buildPublishErrorMessage(renderable, e, "render"), e);
        }
    }

    static void validateRenderableContent(
            final Resource renderable,
            final ModelFactory modelFactory
    ) throws ReplicationException {
        validateRenderableContent(renderable, modelFactory, new HashSet<>());
    }

    static void validateRenderableContent(
            final Resource renderable,
            final RenderService renderService
    ) throws ReplicationException {
        validateRenderableContentRender(renderable, renderService, new HashSet<>());
    }

    static void validateRenderableComponentSources(final Resource renderable) throws ReplicationException {
        validateRenderableComponentSources(renderable, new HashSet<>(), new HashSet<>());
    }

    private static void validateRenderableContent(
            final Resource renderable,
            final ModelFactory modelFactory,
            final Set<String> validatedPaths
    ) throws ReplicationException {
        if (renderable == null || !validatedPaths.add(renderable.getPath())) {
            return;
        }

        if (shouldValidateRenderableResource(renderable)) {
            try {
                modelFactory.exportModelForResource(renderable, JACKSON, java.util.Map.class, Collections.emptyMap());
            } catch (final ExportException | MissingExporterException | RuntimeException e) {
                log.warn("Publish model validation failed for {} [{}]", renderable.getPath(), renderable.getResourceType(), e);
                throw new ReplicationException(buildPublishErrorMessage(renderable, e, "model"), e);
            }
        }

        for (final Resource child : renderable.getChildren()) {
            if (child == null) {
                continue;
            }
            if (child.hasChildren() || shouldValidateRenderableResource(child)) {
                validateRenderableContent(child, modelFactory, validatedPaths);
            }
        }
    }

    private static void validateRenderableContentRender(
            final Resource renderable,
            final RenderService renderService,
            final Set<String> validatedPaths
    ) throws ReplicationException {
        if (renderable == null || renderService == null || !validatedPaths.add(renderable.getPath())) {
            return;
        }

        if (shouldValidateRenderableResource(renderable)) {
            try {
                log.debug("Validating render for publish: {} [{}]", renderable.getPath(), renderable.getResourceType());
                renderService.renderInternally(renderable, "html");
            } catch (final RenderService.RenderException e) {
                log.warn("Publish render validation failed for {} [{}]", renderable.getPath(), renderable.getResourceType(), e);
                throw new ReplicationException(buildPublishErrorMessage(renderable, e, "render"), e);
            } catch (final RuntimeException e) {
                log.warn("Publish render validation failed for {} [{}]", renderable.getPath(), renderable.getResourceType(), e);
                throw new ReplicationException(buildPublishErrorMessage(renderable, e, "render"), e);
            }
        }

        for (final Resource child : renderable.getChildren()) {
            if (child == null) {
                continue;
            }
            if (child.hasChildren() || shouldValidateRenderableResource(child)) {
                validateRenderableContentRender(child, renderService, validatedPaths);
            }
        }
    }

    private static void validateRenderableComponentSources(
            final Resource renderable,
            final Set<String> validatedResourcePaths,
            final Set<String> validatedComponentTypes
    ) throws ReplicationException {
        if (renderable == null || !validatedResourcePaths.add(renderable.getPath())) {
            return;
        }

        if (shouldValidateRenderableResource(renderable)) {
            final String resourceType = renderable.getResourceType();
            if (resourceType != null && validatedComponentTypes.add(resourceType)) {
                validateComponentTemplateSource(renderable, resourceType);
            }
        }

        for (final Resource child : renderable.getChildren()) {
            if (child == null) {
                continue;
            }
            if (child.hasChildren() || shouldValidateRenderableResource(child)) {
                validateRenderableComponentSources(child, validatedResourcePaths, validatedComponentTypes);
            }
        }

        final Resource templateContent = getReferencedTemplateContent(renderable);
        if (templateContent != null) {
            validateRenderableComponentSources(templateContent, validatedResourcePaths, validatedComponentTypes);
        }
    }

    private static Resource getReferencedTemplateContent(final Resource renderable) {
        if (renderable == null) {
            return null;
        }
        final ValueMap valueMap = renderable.getValueMap();
        String templatePath = valueMap.get("templatePath", String.class);
        if (templatePath == null || templatePath.trim().isEmpty()) {
            templatePath = valueMap.get("template", String.class);
        }
        if (templatePath == null || templatePath.trim().isEmpty()) {
            return null;
        }
        final Resource template = renderable.getResourceResolver().getResource(templatePath.trim());
        return getRenderableContent(template);
    }

    private static void validateComponentTemplateSource(
            final Resource renderable,
            final String resourceType
    ) throws ReplicationException {
        final Resource templateContent = renderable.getResourceResolver()
            .getResource("/apps/" + resourceType + "/template.vue/" + JCR_CONTENT);
        if (templateContent == null) {
            return;
        }

        final String source = readTemplateSource(templateContent);
        if (source != null && EXPLICIT_JS_THROW.matcher(source).find()) {
            final IllegalStateException cause = new IllegalStateException("Explicit JavaScript throw found in /apps/" + resourceType + "/template.vue");
            log.warn("Publish JavaScript validation failed for {} [{}]", renderable.getPath(), resourceType, cause);
            throw new ReplicationException(buildPublishErrorMessage(renderable, cause, "javascript"), cause);
        }
    }

    private static String readTemplateSource(final Resource templateContent) throws ReplicationException {
        final ValueMap valueMap = templateContent.getValueMap();
        final String stringData = valueMap.get("jcr:data", String.class);
        if (stringData != null) {
            return stringData;
        }

        final InputStream inputStream = valueMap.get("jcr:data", InputStream.class);
        if (inputStream == null) {
            return null;
        }

        try (InputStream in = inputStream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } catch (final IOException e) {
            throw new ReplicationException("Cannot validate component JavaScript: " + templateContent.getPath(), e);
        }
    }

    private static boolean shouldValidateRenderableResource(final Resource resource) {
        if (resource == null) {
            return false;
        }
        final String resourceType = resource.getResourceType();
        return resourceType != null
            && !resourceType.trim().isEmpty()
            && !NT_UNSTRUCTURED.equals(resourceType.trim());
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

    private static Resource getRenderablePage(final Resource resource) {
        if (resource == null) {
            return null;
        }

        final String resourceType = resource.getResourceType();
        if (PAGE_PRIMARY_TYPE.equals(resourceType) || OBJECT_PRIMARY_TYPE.equals(resourceType)) {
            return resource;
        }

        if (JCR_CONTENT.equals(resource.getName()) && resource.getParent() != null) {
            final String parentType = resource.getParent().getResourceType();
            if (PAGE_PRIMARY_TYPE.equals(parentType) || OBJECT_PRIMARY_TYPE.equals(parentType)) {
                return resource.getParent();
            }
        }

        return null;
    }

    private static String buildPublishErrorMessage(final Resource renderable, final Exception e, final String errorType) {
        final StringBuilder message = new StringBuilder("Cannot publish page with ")
            .append(errorType)
            .append(" errors: ")
            .append(renderable.getPath());

        final String resourceType = renderable.getResourceType();
        if (resourceType != null && !resourceType.trim().isEmpty()) {
            message.append(" [").append(resourceType).append(']');
        }

        final String causeMessage = extractCauseMessage(e);
        if (causeMessage != null && !causeMessage.trim().isEmpty()) {
            message.append(" - ").append(causeMessage.trim());
        }

        return message.toString();
    }

    private static String extractCauseMessage(final Throwable throwable) {
        Throwable current = throwable;
        Throwable last = throwable;
        while (current != null) {
            last = current;
            current = current.getCause();
        }
        return last == null ? null : last.getMessage();
    }
}
