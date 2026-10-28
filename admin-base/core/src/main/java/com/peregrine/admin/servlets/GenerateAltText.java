package com.peregrine.admin.servlets;

import com.peregrine.admin.resource.GeminiGeneration;
import com.peregrine.admin.util.ImageUtils;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.servlet.Servlet;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import static com.peregrine.admin.servlets.AdminPaths.RESOURCE_TYPE_GENERATE_ALT_TEXT;
import static com.peregrine.commons.util.PerConstants.*;
import static com.peregrine.commons.util.PerUtil.*;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_BAD_GATEWAY;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_SERVICE_UNAVAILABLE;
import static org.apache.sling.api.servlets.ServletResolverConstants.*;
import static org.osgi.framework.Constants.*;

/** Generates an editable alt text suggestion for an image asset. */
@Component(service = Servlet.class, property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "generate alt text servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + POST,
        SLING_SERVLET_METHODS + EQUALS + GET,
        SLING_SERVLET_PATHS + EQUALS + "/perapi/admin/generateAltText",
        SLING_SERVLET_RESOURCE_TYPES + EQUALS + RESOURCE_TYPE_GENERATE_ALT_TEXT
})
public class GenerateAltText extends AbstractBaseServlet {
    private static final long MAX_OPTIMIZED_IMAGE_BYTES = 2L * 1024 * 1024;
    private static final int MAX_ALT_TEXT_LENGTH = 500;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Semaphore REQUEST_PERMITS = new Semaphore(4);

    @Reference
    private GeminiGeneration geminiGeneration;

    @Override
    protected Response handleRequest(Request request) throws IOException {
        if ("GET".equalsIgnoreCase(request.getRequest().getMethod())) {
            return new JsonResponse().writeAttribute("altTextConfigured", geminiGeneration.isAltTextConfigured());
        }

        String path = request.getParameter("path");
        if (path == null || !path.matches("^/content/[a-zA-Z0-9_.-]+/assets/.+")) {
            return error("Invalid asset path");
        }
        if (!geminiGeneration.isAltTextConfigured()) {
            return new ErrorResponse().setHttpErrorCode(SC_SERVICE_UNAVAILABLE)
                    .setErrorMessage("Alt text Gemini service is not configured");
        }
        if (!REQUEST_PERMITS.tryAcquire()) {
            return new ErrorResponse().setHttpErrorCode(SC_SERVICE_UNAVAILABLE)
                    .setErrorMessage("Alt text service is busy; try again shortly");
        }
        try {
            Node asset = getNode(request.getResourceResolver(), path);
            if (asset == null || !asset.isNodeType("per:Asset") || !asset.hasNode(JCR_CONTENT)) {
                return error("Image asset not found", SC_NOT_FOUND);
            }
            Node content = asset.getNode(JCR_CONTENT);
            if (!content.hasProperty(JCR_MIME_TYPE) || !content.hasProperty(JCR_DATA)) {
                return error("Invalid or corrupted asset structure", SC_BAD_REQUEST);
            }
            String mimeType = content.getProperty(JCR_MIME_TYPE).getString();
            if ("image/jpg".equals(mimeType) || "image/pjpeg".equals(mimeType)) {
                mimeType = "image/jpeg";
            } else if ("image/x-png".equals(mimeType)) {
                mimeType = "image/png";
            }
            if (!mimeType.matches("image/(png|jpeg|webp|gif)")) {
                return error("Unsupported image type");
            }
            OptimizedImage optimizedImage = fetchOptimizedImage(path, geminiGeneration.getOptimizedImageOrigin());
            byte[] bytes;
            if (optimizedImage.bytes != null) {
                bytes = optimizedImage.bytes;
            } else {
                Binary binary = content.getProperty(JCR_DATA).getBinary();
                try {
                    if (binary.getSize() > ImageUtils.MAX_IMAGE_BYTES) {
                        return error("Image exceeds the " + (ImageUtils.MAX_IMAGE_BYTES / (1024 * 1024)) + " MB limit");
                    }
                    try (InputStream stream = binary.getStream()) {
                        bytes = stream.readNBytes((int) ImageUtils.MAX_IMAGE_BYTES + 1);
                    }
                    if (bytes.length > ImageUtils.MAX_IMAGE_BYTES) {
                        return error("Image exceeds the " + (ImageUtils.MAX_IMAGE_BYTES / (1024 * 1024)) + " MB limit");
                    }
                } finally {
                    binary.dispose();
                }
            }
            String analyzedMimeType;
            int imageWidth;
            int imageHeight;
            String imageSource;
            String imageSourceDetail;
            if (optimizedImage.bytes != null) {
                bytes = optimizedImage.bytes;
                analyzedMimeType = optimizedImage.mimeType;
                imageWidth = optimizedImage.width;
                imageHeight = optimizedImage.height;
                imageSource = "Cloudflare optimized image";
                imageSourceDetail = "Cloudflare 640 px, quality 50 variant";
            } else {
                ImageUtils.AnalyzedImage analyzedImage = ImageUtils.prepareUploadedImage(bytes, mimeType);
                bytes = analyzedImage.bytes;
                analyzedMimeType = analyzedImage.mimeType;
                imageWidth = analyzedImage.width;
                imageHeight = analyzedImage.height;
                imageSource = "Uploaded asset";
                imageSourceDetail = optimizedImage.failure;
            }
            logger.info("Using {} image ({}x{}, {} bytes) for alt text generation of asset {}",
                    analyzedMimeType, imageWidth, imageHeight, bytes.length, path);

            Node parentNode = asset.getParent();
            String folderName = parentNode != null ? parentNode.getName().replaceAll("\\p{Cntrl}", " ").trim() : "";
            if (folderName.length() > 80) folderName = folderName.substring(0, 80);

            // Fetch Alt Text from centralized service
            String altText;
            try {
                altText = geminiGeneration.generateAltText(bytes, analyzedMimeType, folderName);
            } catch (Exception e) {
                logger.warn("Gemini alt text request failed: {}", e.getMessage());
                return error("Gemini request failed", SC_BAD_GATEWAY);
            }

            if (altText == null || altText.isEmpty()) {
                logger.warn("Gemini returned no alt text for asset {}", path);
                return error("Gemini returned an unexpected response", SC_BAD_GATEWAY);
            }
            if (altText.length() > MAX_ALT_TEXT_LENGTH) {
                return error("Generated alt text exceeds " + MAX_ALT_TEXT_LENGTH + " characters", SC_BAD_GATEWAY);
            }
            if (altText.indexOf('<') >= 0 || altText.indexOf('>') >= 0) {
                return error("Generated alt text contains markup", SC_BAD_GATEWAY);
            }
            logger.debug("Generated alt text for asset {}", path);
            return new JsonResponse()
                    .writeAttribute("altText", altText)
                    .writeAttribute("imageMimeType", analyzedMimeType)
                    .writeAttribute("imageWidth", imageWidth)
                    .writeAttribute("imageHeight", imageHeight)
                    .writeAttribute("imageBytes", bytes.length)
                    .writeAttribute("imageSource", imageSource)
                    .writeAttribute("imageSourceDetail", imageSourceDetail == null ? "" : imageSourceDetail);

        } catch (RepositoryException e) {
            logger.error("Failed to generate alt text for asset", e);
            return error("Failed to generate alt text", SC_INTERNAL_SERVER_ERROR);
        } finally {
            REQUEST_PERMITS.release();
        }
    }

    private Response error(String message) throws IOException {
        return error(message, SC_BAD_REQUEST);
    }

    private Response error(String message, int status) throws IOException {
        return new ErrorResponse().setHttpErrorCode(status).setErrorMessage(message);
    }

    private static final class OptimizedImage {
        final byte[] bytes;
        final String failure;
        final String mimeType;
        final int width;
        final int height;

        OptimizedImage(byte[] bytes, String failure, String mimeType, int width, int height) {
            this.bytes = bytes;
            this.failure = failure;
            this.mimeType = mimeType;
            this.width = width;
            this.height = height;
        }
    }

    private OptimizedImage fetchOptimizedImage(String assetPath, String imageOrigin) {
        try {
            URI origin = URI.create(imageOrigin == null ? "" : imageOrigin.trim());
            if (!isValidOptimizedImageOrigin(imageOrigin)) {
                return new OptimizedImage(null, "Optimized image origin must be a public HTTPS origin on port 443", "", 0, 0);
            }
            String[] segments = assetPath.split("/");
            for (String segment : segments) {
                if (".".equals(segment) || "..".equals(segment)) {
                    return new OptimizedImage(null, "Invalid asset path", "", 0, 0);
                }
            }
            String encodedPath = encodePathSegments(assetPath);
            String optimizedPath = "/cdn-cgi/image/onerror=redirect,format=avif,quality=50,slow-connection-quality=50,fit=scale-down,width="
                    + ImageUtils.MAX_IMAGE_DIMENSION + encodedPath;
            URI optimizedUri = origin.resolve(URI.create(optimizedPath));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(optimizedUri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream responseBody = response.body()) {
                if (response.statusCode() != 200) {
                    return new OptimizedImage(null, "Cloudflare response: HTTP " + response.statusCode(), "", 0, 0);
                }
                byte[] responseBytes = responseBody.readNBytes((int) MAX_OPTIMIZED_IMAGE_BYTES + 1);
                if (responseBytes.length == 0 || responseBytes.length > MAX_OPTIMIZED_IMAGE_BYTES) {
                    return new OptimizedImage(null, "Cloudflare response exceeds the optimized image size limit",
                            "", 0, 0);
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("");
                String normalizedContentType = contentType.split(";", 2)[0].trim().toLowerCase();
                if (!(normalizedContentType.equals("image/avif") || normalizedContentType.equals("image/jpeg")
                        || normalizedContentType.equals("image/png") || normalizedContentType.equals("image/webp")
                        || normalizedContentType.equals("image/gif"))) {
                    return new OptimizedImage(null, "Cloudflare returned " + (contentType.isEmpty() ? "no content type" : contentType), "", 0, 0);
                }
                int[] dimensions = readImageDimensions(responseBytes);
                return new OptimizedImage(responseBytes, "", normalizedContentType, dimensions[0], dimensions[1]);
            }
        } catch (Exception e) {
            return new OptimizedImage(null, "Cloudflare request failed: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"), "", 0, 0);
        }
    }

    private int[] readImageDimensions(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            if (input == null) return new int[]{0, 0};
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return new int[]{0, 0};
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean isValidOptimizedImageOrigin(String value) {
        try {
            URI origin = URI.create(value == null ? "" : value.trim());
            String hostname = origin.getHost();
            if (!"https".equalsIgnoreCase(origin.getScheme()) || hostname == null
                    || origin.getRawUserInfo() != null || origin.getRawQuery() != null
                    || origin.getRawFragment() != null || (origin.getPort() != -1 && origin.getPort() != 443)
                    || (origin.getRawPath() != null && !origin.getRawPath().isEmpty()
                    && !"/".equals(origin.getRawPath()))) return false;

            String normalized = hostname.toLowerCase();
            return normalized.matches("(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}")
                    && !normalized.endsWith(".localhost") && !normalized.endsWith(".local")
                    && !normalized.endsWith(".internal") && !normalized.endsWith(".test")
                    && !normalized.endsWith(".invalid") && !normalized.endsWith(".example");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String encodePathSegments(String path) {
        String[] segments = path.split("/", -1);
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) encoded.append('/');
            encoded.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return encoded.toString();
    }
}
