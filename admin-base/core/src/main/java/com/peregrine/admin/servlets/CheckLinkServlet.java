package com.peregrine.admin.servlets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.apache.sling.api.SlingHttpServletRequest;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

import java.util.regex.Pattern;

import static com.peregrine.commons.util.PerUtil.EQUALS;
import static com.peregrine.commons.util.PerUtil.GET;
import static com.peregrine.commons.util.PerUtil.PER_PREFIX;
import static com.peregrine.commons.util.PerUtil.PER_VENDOR;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_METHODS;
import static org.apache.sling.api.servlets.ServletResolverConstants.SLING_SERVLET_PATHS;
import static org.osgi.framework.Constants.SERVICE_DESCRIPTION;
import static org.osgi.framework.Constants.SERVICE_VENDOR;

@Component(
    service = Servlet.class,
    property = {
        SERVICE_DESCRIPTION + EQUALS + PER_PREFIX + "Check Link Servlet",
        SERVICE_VENDOR + EQUALS + PER_VENDOR,
        SLING_SERVLET_METHODS + EQUALS + GET,
        SLING_SERVLET_PATHS + EQUALS + "/extension/check-link"
    }
)
@Designate(ocd = CheckLinkServlet.Configuration.class)
@SuppressWarnings("serial")
public final class CheckLinkServlet extends AbstractBaseServlet {

    @ObjectClassDefinition(name = "Peregrine: Check Link Servlet",
            description = "Validates internal and external links")
    @interface Configuration {
        @AttributeDefinition(name = "URL",
                description = "URL of the external link validation service")
        String url() default "";

        @AttributeDefinition(name = "Token",
                description = "Bearer token for the external link validation service")
        String token() default "";
    }

    private static final Logger logger = LoggerFactory.getLogger(CheckLinkServlet.class);

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CONCURRENT_REQUESTS = 10;
    private static final Semaphore REQUEST_SEMAPHORE = new Semaphore(MAX_CONCURRENT_REQUESTS);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private volatile String checkerUrl;
    private volatile String checkerToken;

    @Activate
    @SuppressWarnings("unused")
    void activate(final Configuration configuration) {
        setup(configuration);
    }

    @Modified
    @SuppressWarnings("unused")
    void modified(final Configuration configuration) {
        setup(configuration);
    }

    private void setup(final Configuration configuration) {
        checkerUrl = configuration.url();
        checkerToken = configuration.token();
    }

    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<script\\b[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SPA_ROOT_PATTERN = Pattern.compile("<div[^>]*\\bid\\s*=\\s*[\"'](?:app|root|peregrine-app|nuxt|vue-app|__nuxt|__next)[\"']", Pattern.CASE_INSENSITIVE);

    private static final List<String> ERROR_PAGE_INDICATORS = List.of(
        "<title>404", "<title>500", "<title>Error", "<title>Page Not Found",
        "<title>not found", "<title>error",
        "error-404", "error-500", "page-not-found", "not-found",
        "404 - Page Not Found",
        "The requested URL was not found",
        "Page not found",
        "404 Not Found",
        "class=\"error\"",
        "class=\"404\"",
        "class=\"page-not-found\"",
        "class=\"notfound\"",
        "class=\"error-page\"",
        "status-code-404",
        "resource-not-found",
        "content not found",
        "does not exist",
        "could not be found",
        "no longer exists",
        "has been removed",
        "moved or deleted",
        "resulted in an error",
        "script error",
        "failed to include",
        "handleerror",
        "defaulterrorhandlerservlet"
    );

    private static Set<InetAddress> resolveServerAddresses(final SlingHttpServletRequest slingRequest) {
        final Set<InetAddress> addresses = new HashSet<>();
        try {
            addresses.add(InetAddress.getByName(slingRequest.getLocalAddr()));
            addresses.add(InetAddress.getByName(slingRequest.getLocalName()));
            addresses.add(InetAddress.getByName(slingRequest.getServerName()));
            addresses.add(InetAddress.getLocalHost());
        } catch (final Exception e) {
            logger.warn("CheckLink: failed to resolve server addresses", e);
        }
        return addresses;
    }

    private static boolean isServerAddress(final String host, final Set<InetAddress> serverAddresses) {
        try {
            final InetAddress target = InetAddress.getByName(host);
            return serverAddresses.contains(target);
        } catch (final Exception e) {
            return false;
        }
    }

    private static boolean isPrivateAddress(final String host) {
        try {
            final InetAddress addr = InetAddress.getByName(host);
            if (addr.isLoopbackAddress()) return true;
            if (addr.isLinkLocalAddress()) return true;
            if (addr.isSiteLocalAddress()) return true;
            final byte[] raw = addr.getAddress();
            if (raw.length == 4) {
                final int first = raw[0] & 0xff;
                if (first == 0) return true;
                if (first == 100 && (raw[1] & 0xff) >= 64 && (raw[1] & 0xff) <= 127) return true;
                if (first == 169 && (raw[1] & 0xff) == 254) return true;
            }
            return false;
        } catch (final Exception e) {
            return true;
        }
    }

    private static boolean isAllowedContentType(final String contentType) {
        if (contentType == null) return true;
        final String lower = contentType.toLowerCase();
        if (lower.contains("text/html")) return true;
        if (lower.contains("text/plain")) return true;
        if (lower.contains("application/xhtml")) return true;
        if (lower.contains("application/xml")) return true;
        if (lower.contains("text/xml")) return true;
        if (lower.startsWith("text/")) return true;
        return false;
    }

    private static String stripStyleTags(final String html) {
        if (html == null) return "";
        final StringBuilder result = new StringBuilder(html.length());
        int i = 0;
        while (i < html.length()) {
            final int styleStart = indexOfIgnoreCase(html, "<style", i);
            if (styleStart < 0) {
                result.append(html, i, html.length());
                break;
            }
            result.append(html, i, styleStart);
            final int styleEnd = indexOfIgnoreCase(html, "</style>", styleStart);
            if (styleEnd < 0) {
                break;
            }
            i = styleEnd + 8;
        }
        return result.toString();
    }

    private static int indexOfIgnoreCase(final String str, final String search, final int fromIndex) {
        if (str == null || search == null) return -1;
        final int searchLen = search.length();
        if (fromIndex + searchLen > str.length()) return -1;
        for (int i = fromIndex; i <= str.length() - searchLen; i++) {
            boolean match = true;
            for (int j = 0; j < searchLen; j++) {
                if (Character.toLowerCase(str.charAt(i + j)) != Character.toLowerCase(search.charAt(j))) {
                    match = false;
                    break;
                }
            }
            if (match) return i;
        }
        return -1;
    }

    private static String readBodyUpToLimit(final InputStream in, final int limit) throws IOException {
        final byte[] buffer = new byte[8192];
        final StringBuilder sb = new StringBuilder();
        int total = 0;
        while (total < limit) {
            final int remaining = limit - total;
            final int toRead = Math.min(buffer.length, remaining);
            final int n = in.read(buffer, 0, toRead);
            if (n < 0) break;
            sb.append(new String(buffer, 0, n, java.nio.charset.StandardCharsets.UTF_8));
            total += n;
        }
        return sb.toString();
    }

    @Override
    protected Response handleRequest(final Request request) throws IOException {
        final String urlParam = request.getParameter("url");
        if (isBlank(urlParam)) {
            return new ErrorResponse()
                .setHttpErrorCode(SC_BAD_REQUEST)
                .setErrorMessage("No URL provided");
        }

        final Set<InetAddress> serverAddresses = resolveServerAddresses(request.getRequest());

        try {
            final URI originalUri = URI.create(urlParam);
            final String host = originalUri.getHost();
            if (host != null && !isServerAddress(host, serverAddresses) && isPrivateAddress(host)) {
                logger.warn("CheckLink: blocked private address for url={}", urlParam);
                return new JsonResponse()
                    .writeAttribute("ok", false)
                    .writeAttribute("status", 0)
                    .writeAttribute("error", "Blocked private or internal address");
            }
        } catch (final Exception e) {
            return new JsonResponse()
                .writeAttribute("ok", false)
                .writeAttribute("status", 0)
                .writeAttribute("error", "Invalid URL");
        }

        final URI uri = URI.create(urlParam);
        final String host = uri.getHost();
        if (host != null && !isServerAddress(host, serverAddresses)) {
            return proxyExternalToWorker(urlParam);
        }

        try {
            final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

            String currentUrl = urlParam;
            int redirectCount = 0;
            String redirectUrl = null;
            String finalUrl = urlParam;

            while (redirectCount < MAX_REDIRECTS) {
                final URI targetUri = URI.create(currentUrl);
                final String redirectHost = targetUri.getHost();
                if (redirectHost != null && !isServerAddress(redirectHost, serverAddresses) && isPrivateAddress(redirectHost)) {
                    logger.warn("CheckLink: blocked private address during redirect for url={}", urlParam);
                    return new JsonResponse()
                        .writeAttribute("ok", false)
                        .writeAttribute("status", 0)
                        .writeAttribute("error", "Blocked private or internal address during redirect");
                }

                REQUEST_SEMAPHORE.acquireUninterruptibly();
                try {
                    final HttpRequest httpRequest = HttpRequest.newBuilder()
                        .uri(targetUri)
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(java.time.Duration.ofSeconds(10))
                        .header("User-Agent", "Mozilla/5.0 (compatible; PeregrineCMS/1.0)")
                        .build();

                    final HttpResponse<Void> response = client.send(httpRequest, HttpResponse.BodyHandlers.discarding());
                    final int status = response.statusCode();

                    if (status >= 300 && status < 400) {
                        final String location = response.headers().firstValue("Location").orElse(null);
                        if (location != null) {
                            final String locationLower = location.toLowerCase();
                            if (locationLower.contains("/login") || locationLower.contains("/authenticate") || locationLower.contains("/signin") || locationLower.contains("/sling/form/login")) {
                                finalUrl = currentUrl;
                                logger.info("CheckLink: url={}, redirects to login page, marking as incorrect", urlParam);
                                return new JsonResponse()
                                    .writeAttribute("ok", false)
                                    .writeAttribute("status", status)
                                    .writeAttribute("redirect", true)
                                    .writeAttribute("redirectUrl", location)
                                    .writeAttribute("finalUrl", location)
                                    .writeAttribute("finalStatus", 401)
                                    .writeAttribute("finalOk", false)
                                    .writeAttribute("loginRedirect", true);
                            }
                            redirectUrl = location;
                            currentUrl = location.startsWith("http") ? location : targetUri.resolve(location).toString();
                            redirectCount++;
                            continue;
                        }
                    }

                    finalUrl = currentUrl;
                    boolean looksLikeErrorPage = false;

                    final String finalUrlLower = finalUrl.toLowerCase();
                    if (finalUrlLower.contains("/error") || finalUrlLower.contains("/404") || finalUrlLower.contains("/notfound") || finalUrlLower.contains("/page-not-found") || finalUrlLower.contains("/errorpage") || finalUrlLower.contains("/error-page") || finalUrlLower.contains("/missing") || finalUrlLower.contains("/deleted")) {
                        looksLikeErrorPage = true;
                    }

                    if (status >= 200 && status < 300 && !looksLikeErrorPage) {
                        final String contentType = response.headers().firstValue("Content-Type").orElse("");
                        if (!isAllowedContentType(contentType)) {
                            logger.info("CheckLink: skipping body fetch for non-HTML content type: {}", contentType);
                            return new JsonResponse()
                                .writeAttribute("ok", true)
                                .writeAttribute("status", status);
                        }

                        final long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                        if (contentLength > MAX_BODY_BYTES) {
                            logger.info("CheckLink: skipping body fetch for oversized content: {} bytes", contentLength);
                            return new JsonResponse()
                                .writeAttribute("ok", true)
                                .writeAttribute("status", status);
                        }

                        final HttpRequest getRequest = HttpRequest.newBuilder()
                            .uri(URI.create(currentUrl))
                            .timeout(java.time.Duration.ofSeconds(10))
                            .header("User-Agent", "Mozilla/5.0 (compatible; PeregrineCMS/1.0)")
                            .GET()
                            .build();

                        try {
                            final HttpResponse<InputStream> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofInputStream());
                            final String body;
                            try (final InputStream bodyStream = getResponse.body()) {
                                body = readBodyUpToLimit(bodyStream, MAX_BODY_BYTES);
                            }
                            if (body.length() >= MAX_BODY_BYTES) {
                                logger.info("CheckLink: body truncated at {} bytes for url={}", MAX_BODY_BYTES, urlParam);
                            }
                            final String strippedBody = stripScriptTags(body);
                            final String bodyLower = strippedBody.toLowerCase();
                            final int bodyLength = strippedBody.length();

                            final boolean isSpaShell = isSpaShell(body, strippedBody);

                            if (bodyLength < 500 && !looksLikeErrorPage && !isSpaShell) {
                                looksLikeErrorPage = true;
                            }

                            if (!looksLikeErrorPage) {
                                for (final String indicator : ERROR_PAGE_INDICATORS) {
                                    if (bodyLower.contains(indicator.toLowerCase())) {
                                        looksLikeErrorPage = true;
                                        break;
                                    }
                                }

                                if (!looksLikeErrorPage) {
                                    final int titleStart = bodyLower.indexOf("<title>");
                                    if (titleStart >= 0) {
                                        final int titleEnd = bodyLower.indexOf("</title>", titleStart);
                                        if (titleEnd >= 0) {
                                            final String title = bodyLower.substring(titleStart + 7, titleEnd).trim();
                                            if (title.contains("404") || title.contains("not found") || title.contains("error") || title.contains("page not found")) {
                                                looksLikeErrorPage = true;
                                            }
                                        }
                                    }
                                }

                                if (!looksLikeErrorPage && (bodyLower.contains("slingerrortitle") || bodyLower.contains("slingerrormessage") || bodyLower.contains("slingerrorstatus"))) {
                                    looksLikeErrorPage = true;
                                }

                                if (!looksLikeErrorPage && bodyLower.contains("404")) {
                                    int idx = bodyLower.indexOf("404");
                                    while (idx >= 0 && !looksLikeErrorPage) {
                                        final String before404 = idx > 0 ? bodyLower.substring(Math.max(0, idx - 1), idx) : "";
                                        final boolean isStandalone404 = idx > 0 ? !Character.isDigit(before404.charAt(before404.length() - 1)) : true;
                                        if (isStandalone404) {
                                            final int start = Math.max(0, idx - 100);
                                            final int end = Math.min(bodyLower.length(), idx + 100);
                                            final String context = bodyLower.substring(start, end);
                                            if (context.contains("page") || context.contains("not found") || context.contains("error") || context.contains("resource")) {
                                                looksLikeErrorPage = true;
                                            }
                                        }
                                        idx = bodyLower.indexOf("404", idx + 3);
                                    }
                                }

                                if (!looksLikeErrorPage && bodyLower.contains("not found")) {
                                    int idx = bodyLower.indexOf("not found");
                                    while (idx >= 0 && !looksLikeErrorPage) {
                                        final int start = Math.max(0, idx - 100);
                                        final int end = Math.min(bodyLower.length(), idx + 100);
                                        final String context = bodyLower.substring(start, end);
                                        if (context.contains("resource") || context.contains("page") || context.contains("404") || context.contains("error")) {
                                            looksLikeErrorPage = true;
                                        }
                                        idx = bodyLower.indexOf("not found", idx + 9);
                                    }
                                }

                                if (!looksLikeErrorPage && bodyLower.contains("request progress")) {
                                    looksLikeErrorPage = true;
                                }
                            }
                        } catch (final Exception e) {
                        }
                    }

                    final boolean finalOk = status >= 200 && status < 400 && !looksLikeErrorPage;

                    logger.debug("CheckLink: url={}, redirects={}, finalUrl={}, status={}, finalOk={}, errorPage={}", urlParam, redirectCount, finalUrl, status, finalOk, looksLikeErrorPage);

                    final JsonResponse jsonResponse = new JsonResponse()
                        .writeAttribute("ok", finalOk)
                        .writeAttribute("status", status);

                    if (redirectCount > 0) {
                        jsonResponse.writeAttribute("redirect", true)
                            .writeAttribute("redirectUrl", redirectUrl)
                            .writeAttribute("finalUrl", finalUrl)
                            .writeAttribute("finalStatus", status)
                            .writeAttribute("finalOk", finalOk);
                    }

                    if (looksLikeErrorPage) {
                        jsonResponse.writeAttribute("errorPage", true);
                    }

                    return jsonResponse;
                } finally {
                    REQUEST_SEMAPHORE.release();
                }
            }

            return new JsonResponse()
                .writeAttribute("ok", false)
                .writeAttribute("status", 0)
                .writeAttribute("error", "Too many redirects");
        } catch (final Exception e) {
            return new JsonResponse()
                .writeAttribute("ok", false)
                .writeAttribute("status", 0)
                .writeAttribute("error", e.getMessage());
        }
    }

    private static String stripScriptTags(final String html) {
        if (html == null) return "";
        return SCRIPT_TAG_PATTERN.matcher(html).replaceAll("");
    }

    private Response proxyExternalToWorker(final String url) throws IOException {
        if (isBlank(checkerUrl) || isBlank(checkerToken)) {
            return new JsonResponse()
                .writeAttribute("ok", false)
                .writeAttribute("status", 0)
                .writeAttribute("checkerError", true)
                .writeAttribute("error", "External link checker is not configured");
        }
        try {
            final HttpClient client = HttpClient.newHttpClient();
            final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(checkerUrl + "?url=" + java.net.URLEncoder.encode(url, "UTF-8")))
                .header("Authorization", "Bearer " + checkerToken)
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return new JsonResponse()
                    .writeAttribute("ok", false)
                    .writeAttribute("status", 0)
                    .writeAttribute("checkerError", true)
                    .writeAttribute("error", "External link checker returned " + response.statusCode());
            }
            final JsonNode data = MAPPER.readTree(response.body());
            final boolean valid = data.path("valid").asBoolean(false);
            final boolean redirected = data.path("redirected").asBoolean(false);
            final int status = data.path("status").asInt(0);
            final String finalUrl = data.path("finalUrl").asText(null);

            final JsonResponse jsonResponse = new JsonResponse()
                .writeAttribute("ok", valid)
                .writeAttribute("status", status);

            if (redirected && finalUrl != null) {
                jsonResponse.writeAttribute("redirect", true)
                    .writeAttribute("redirectUrl", url)
                    .writeAttribute("finalUrl", finalUrl)
                    .writeAttribute("finalStatus", status)
                    .writeAttribute("finalOk", valid);
            }

            logger.debug("CheckLink: external url={}, checker valid={}, redirected={}, finalUrl={}", url, valid, redirected, finalUrl);
            return jsonResponse;
        } catch (final Exception e) {
            logger.warn("CheckLink: checker request failed for url={}", url, e);
            return new JsonResponse()
                .writeAttribute("ok", false)
                .writeAttribute("status", 0)
                .writeAttribute("checkerError", true)
                .writeAttribute("error", "External link checker is not available");
        }
    }

    private static boolean isSpaShell(final String rawHtml, final String strippedHtml) {
        if (rawHtml == null) return false;
        if (!SPA_ROOT_PATTERN.matcher(rawHtml).find()) return false;
        final String noStyles = stripStyleTags(strippedHtml);
        final String text = noStyles.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() < 100;
    }

}
