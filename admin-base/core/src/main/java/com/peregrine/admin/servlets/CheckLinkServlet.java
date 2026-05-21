package com.peregrine.admin.servlets;

import com.peregrine.commons.servlets.AbstractBaseServlet;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import java.util.regex.Matcher;
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
@SuppressWarnings("serial")
public final class CheckLinkServlet extends AbstractBaseServlet {

    private static final Logger logger = LoggerFactory.getLogger(CheckLinkServlet.class);
    private static final int MAX_REDIRECTS = 5;
    private static final List<String> ERROR_PAGE_INDICATORS = List.of(
        "<title>404", "<title>500", "<title>Error", "<title>Page Not Found",
        "<title>not found", "<title>error",
        "error-404", "error-500", "page-not-found", "not-found",
        "The page you requested could not be found",
        "This page does not exist",
        "The requested URL was not found",
        "Page not found",
        "404 - Page Not Found",
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

    @Override
    protected Response handleRequest(final Request request) throws IOException {
        final String urlParam = request.getParameter("url");
        if (isBlank(urlParam)) {
            return new ErrorResponse()
                .setHttpErrorCode(SC_BAD_REQUEST)
                .setErrorMessage("No URL provided");
        }

        final ResourceResolver resolver = request.getResourceResolver();

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
                final HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(currentUrl))
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
                        currentUrl = location.startsWith("http") ? location : new URI(currentUrl).resolve(location).toString();
                        redirectCount++;
                        continue;
                    }
                }

                finalUrl = currentUrl;
                boolean looksLikeErrorPage = false;
                boolean isSpaDetailPage = false;

                final String finalUrlLower = finalUrl.toLowerCase();
                if (finalUrlLower.contains("/error") || finalUrlLower.contains("/404") || finalUrlLower.contains("/notfound") || finalUrlLower.contains("/page-not-found") || finalUrlLower.contains("/errorpage") || finalUrlLower.contains("/error-page") || finalUrlLower.contains("/missing") || finalUrlLower.contains("/deleted")) {
                    looksLikeErrorPage = true;
                }

                if (!looksLikeErrorPage) {
                    final String[] spaDetailPatterns = {"-details.html", "-detail.html", "-view.html", "-item.html", "-single.html"};
                    for (final String pattern : spaDetailPatterns) {
                        if (finalUrlLower.endsWith(pattern)) {
                            looksLikeErrorPage = true;
                            isSpaDetailPage = true;
                            break;
                        }
                    }
                }

                if (status >= 200 && status < 300 && !looksLikeErrorPage) {
                    final HttpRequest getRequest = HttpRequest.newBuilder()
                        .uri(URI.create(currentUrl))
                        .timeout(java.time.Duration.ofSeconds(10))
                        .header("User-Agent", "Mozilla/5.0 (compatible; PeregrineCMS/1.0)")
                        .GET()
                        .build();

                    try {
                        final HttpResponse<String> getResponse = client.send(getRequest, HttpResponse.BodyHandlers.ofString());
                        final String body = getResponse.body();
                        final String strippedBody = stripScriptTags(body);
                        final String bodyLower = strippedBody.toLowerCase();
                        final int bodyLength = strippedBody.length();

                        final boolean isSpaShellResult = isSpaShell(body, strippedBody);

                        if (!looksLikeErrorPage && isSpaShellResult) {
                            final String pathFromUrl = extractPathFromUrl(finalUrl);
                            final boolean pageExistsInJcr = resolver != null && resolver.getResource(pathFromUrl) != null;
                            if (!pageExistsInJcr && isSpaDetailPattern(finalUrl)) {
                                looksLikeErrorPage = true;
                                isSpaDetailPage = true;
                            }
                        }

                        if (bodyLength < 500 && !looksLikeErrorPage && !isSpaShellResult) {
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
                                    final String after404 = idx + 3 < bodyLower.length() ? bodyLower.substring(idx + 3, idx + 4) : "";
                                    final boolean isStandalone404 = !before404.matches("[0-9]") && !after404.matches("[0-9]");
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

                            if (!looksLikeErrorPage && bodyLower.contains("handleerror:status=")) {
                                looksLikeErrorPage = true;
                            }
                        }
                    } catch (final Exception e) {
                    }
                }

                final boolean finalOk = status >= 200 && status < 400 && !looksLikeErrorPage;

                logger.info("CheckLink: url={}, redirects={}, finalUrl={}, status={}, finalOk={}, errorPage={}", urlParam, redirectCount, finalUrl, status, finalOk, looksLikeErrorPage);

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
                    if (isSpaDetailPage) {
                        jsonResponse.writeAttribute("spaRedirect", true);
                    }
                }

                return jsonResponse;
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

    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile("<script\\b[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SPA_ROOT_PATTERN = Pattern.compile("<div[^>]*\\bid\\s*=\\s*[\"'](?:app|root|peregrine-app|nuxt|vue-app|__nuxt|__next)[\"']", Pattern.CASE_INSENSITIVE);

    private static String stripScriptTags(final String html) {
        if (html == null) return "";
        return SCRIPT_TAG_PATTERN.matcher(html).replaceAll("");
    }

    private static boolean isSpaShell(final String rawHtml, final String strippedHtml) {
        if (rawHtml == null) return false;
        if (!SPA_ROOT_PATTERN.matcher(rawHtml).find()) return false;
        final String noStyles = strippedHtml.replaceAll("(?is)<style\\b[^>]*>.*?</style>", "");
        final String text = noStyles.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return text.length() < 100;
    }

    private static String extractPathFromUrl(final String url) {
        if (url == null) return "";
        try {
            final URI uri = URI.create(url);
            return uri.getPath();
        } catch (final Exception e) {
            return url;
        }
    }

    private static boolean isSpaDetailPattern(final String url) {
        if (url == null) return false;
        final String urlLower = url.toLowerCase();
        final String[] detailPatterns = {"-details.html", "-detail.html", "-view.html", "-item.html", "-single.html"};
        for (final String pattern : detailPatterns) {
            if (urlLower.endsWith(pattern)) {
                return true;
            }
        }
        return false;
    }
}
