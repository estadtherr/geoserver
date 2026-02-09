/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.util.RequestUtils;

/**
 * Filter to log requests for debugging or statistics-gathering purposes.
 *
 * @author David Winslow dwinslow@openplans.org
 */
public class LoggingFilter implements GeoServerFilter {
    protected Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.filters");

    public static final String LOG_REQUESTS_ENABLED = "logRequestsEnabled";
    public static final String LOG_HEADERS_ENABLED = "logHeadersEnabled";
    public static final String LOG_BODIES_ENABLED = "logBodiesEnabled";

    public static final String REQUEST_LOG_BUFFER_SIZE = "requestLogBufferSize";

    /** subtypes of the "application" media type that can safely be interpreted as text */
    private static final List<String> APPLICATION_MEDIA_TYPE_TEXT_SUBTYPES =
            List.of("xml", "json", "gml", "html", "x-www-form-urlencoded");

    public static final Integer REQUEST_LOG_BUFFER_SIZE_DEFAULT = 1024;

    protected boolean enabled = false;
    protected boolean logBodies = false;

    protected Integer requestLogBufferSize = REQUEST_LOG_BUFFER_SIZE_DEFAULT;
    protected boolean logHeaders = false;

    private final GeoServer geoServer;

    public LoggingFilter(GeoServer geoServer) {
        this.geoServer = geoServer;
    }

    /**
     * Check if body can be logged, or is it a known binary type.
     *
     * <p>At the time of writing used to suppress application/zip logging (which would render the console unusable).
     *
     * @param contentType Media type (formerly MIME type) of body content
     * @return Whether content type indicates binary content (which therefore shouldn't be logged)
     */
    protected boolean isBinary(String contentType) {
        if (contentType == null) {
            return true;
        }
        int sub = contentType.indexOf('/');
        String mediaType =
                sub == -1 ? contentType : contentType.substring(0, sub).toLowerCase();
        String subType = sub == -1 ? "" : contentType.substring(sub + 1).toLowerCase();

        if (mediaType.equals("image") && !subType.contains("svg")) {
            return true;
        } else if ("application".equals(mediaType)
                && APPLICATION_MEDIA_TYPE_TEXT_SUBTYPES.stream().noneMatch(subType::contains)) {
            return true;
        } else if (mediaType.equals("multipart")) {
            // probably a file upload - assume binary to prevent binary part content from spamming the log output
            return true;
        } else {
            return false; // assume text by default
        }
    }

    @Override
    @SuppressWarnings("PMD.CloseResource")
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        // Pulling setting from global settings object
        boolean geoServerHasMetadata = (geoServer != null
                && geoServer.getGlobal() != null
                && geoServer.getGlobal().getMetadata() != null);

        if (geoServerHasMetadata) {
            MetadataMap metadataMap = geoServer.getGlobal().getMetadata();
            enabled = (metadataMap.containsKey(LOG_REQUESTS_ENABLED)
                    && metadataMap.get(LOG_REQUESTS_ENABLED, Boolean.class));
            logBodies =
                    (metadataMap.containsKey(LOG_BODIES_ENABLED) && metadataMap.get(LOG_BODIES_ENABLED, Boolean.class));
            logHeaders = (metadataMap.containsKey(LOG_HEADERS_ENABLED)
                    && metadataMap.get(LOG_HEADERS_ENABLED, Boolean.class));
            // Grabbed from global directly, not metadatamap for backwards compatibility
            requestLogBufferSize = geoServer.getGlobal().getXmlPostRequestLogBufferSize() != null
                    ? geoServer.getGlobal().getXmlPostRequestLogBufferSize()
                    : REQUEST_LOG_BUFFER_SIZE_DEFAULT;
        }

        StringBuilder message;
        String body;
        String path = "";

        if (enabled) {
            if (req instanceof HttpServletRequest hreq) {

                path = RequestUtils.getRemoteAddr(hreq) + " \"" + hreq.getMethod() + " " + hreq.getRequestURI();
                if (hreq.getQueryString() != null) {
                    path += "?" + hreq.getQueryString();
                }
                path += "\"";

                message = new StringBuilder(path);
                message.append(" \"").append(noNull(hreq.getHeader("User-Agent")));
                message.append("\" \"").append(noNull(hreq.getHeader("Referer")));
                message.append("\" \"")
                        .append(noNull(hreq.getHeader("Content-type")))
                        .append("\" ");

                if (logHeaders) {
                    Enumeration<String> headerNames = hreq.getHeaderNames();
                    message.append("\n  Headers:");
                    while (headerNames.hasMoreElements()) {
                        String headerName = headerNames.nextElement();
                        message.append("\n    ").append(headerName).append(": ").append(hreq.getHeader(headerName));
                    }
                }

                if (logBodies
                        && requestLogBufferSize > 0
                        && (hreq.getMethod().equals("PUT")
                                || hreq.getMethod().equals("POST")
                                || hreq.getMethod().equals("PATCH"))) {
                    message.append(" request-size: ").append(hreq.getContentLength());
                    message.append(" body: ");

                    String encoding = hreq.getCharacterEncoding();
                    if (encoding == null) {
                        // the default encoding for HTTP 1.1
                        encoding = "ISO-8859-1";
                    }
                    // The HTTPServletResponse stream should not be closed (Tomcat would complain),
                    // avoid close() and try-with-resources
                    InputStream is = hreq.getInputStream();

                    Charset charset = Charset.defaultCharset();
                    try {
                        charset = Charset.forName(encoding);
                    } catch (IllegalCharsetNameException icn) {
                        logger.info(
                                "Request character set (" + encoding + ") not recognized, using default character set");
                    }
                    float maxBytesPerCharacter = charset.newEncoder().maxBytesPerChar();
                    int byteSize = (int) (requestLogBufferSize * maxBytesPerCharacter);
                    byte[] reqCharacters = new byte[byteSize];
                    BufferedInputStream bufferedStream = new BufferedInputStream(is);
                    bufferedStream.mark(byteSize);
                    bufferedStream.read(reqCharacters, 0, byteSize);
                    body = new String(reqCharacters, encoding).trim();
                    bufferedStream.reset();

                    req = new BufferedRequestWrapper(hreq, encoding, bufferedStream);

                    if (isBinary(hreq.getHeader("Content-type"))) {
                        message.append(" bytes (binary content)\n");
                    } else {
                        message.append("\n").append(body).append("\n");
                    }
                }
            } else {
                message = new StringBuilder(req.getRemoteHost() + " made a non-HTTP request");
            }
            logger.info(message.toString());

            long startTime = System.currentTimeMillis();
            chain.doFilter(req, res);
            long requestTime = System.currentTimeMillis() - startTime;
            logger.info(path + " took " + requestTime + "ms");
        } else {
            chain.doFilter(req, res);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
        enabled = getConfigBool("enabled", filterConfig);
        logBodies = getConfigBool("log-request-bodies", filterConfig);
        logHeaders = getConfigBool("log-request-headers", filterConfig);
    }

    protected boolean getConfigBool(String name, FilterConfig conf) {
        try {
            String value = conf.getInitParameter(name);
            return Boolean.parseBoolean(value);
        } catch (Exception e) {
            return false;
        }
    }

    protected String noNull(String s) {
        if (s == null) return "";
        return s;
    }

    @Override
    public void destroy() {}

    /** @return the enabled */
    public boolean isEnabled() {
        return enabled;
    }

    /** @param enabled the enabled to set */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /** @return the logBodies */
    public boolean isLogBodies() {
        return logBodies;
    }

    /** @param logBodies the logBodies to set */
    public void setLogBodies(boolean logBodies) {
        this.logBodies = logBodies;
    }
}
