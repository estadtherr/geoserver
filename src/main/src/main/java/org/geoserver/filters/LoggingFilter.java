/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import java.io.IOException;
import java.util.Enumeration;
import java.util.logging.Logger;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import org.geoserver.catalog.MetadataMap;
import org.geoserver.config.GeoServer;
import org.geoserver.ows.util.RequestUtils;

/**
 * Filter to log requests for debugging or statistics-gathering purposes.
 *
 * @author David Winslow <dwinslow@openplans.org>
 */
public class LoggingFilter implements GeoServerFilter {
    protected Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.filters");

    public static final String LOG_REQUESTS_ENABLED = "logRequestsEnabled";
    public static final String LOG_HEADERS_ENABLED = "logHeadersEnabled";
    public static final String LOG_BODIES_ENABLED = "logBodiesEnabled";

    public static final String REQUEST_LOG_BUFFER_SIZE = "requestLogBufferSize";

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
     * <p>At the time of writing used to suppress application/zip logging (which would render the
     * console unusable).
     *
     * @param contentType
     * @return
     */
    protected boolean isBinary(String contentType) {
        if (contentType == null) {
            return true;
        }
        int sub = contentType.indexOf('/');
        String mimeType = sub == -1 ? contentType : contentType.substring(0, sub).toLowerCase();
        String subType = sub == -1 ? "" : contentType.substring(sub + 1).toLowerCase();

        if (mimeType.equals("image") && !subType.contains("svg")) {
            return true;
        } else if ("application".equals(mimeType)
                && !(subType.contains("xml")
                        || subType.contains("json")
                        || subType.contains("gml"))) {
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
        boolean geoServerHasMetadata =
                (geoServer != null
                        && geoServer.getGlobal() != null
                        && geoServer.getGlobal().getMetadata() != null);

        if (geoServerHasMetadata) {
            MetadataMap metadataMap = geoServer.getGlobal().getMetadata();
            enabled =
                    (metadataMap.containsKey(LOG_REQUESTS_ENABLED)
                            && metadataMap.get(LOG_REQUESTS_ENABLED, Boolean.class));
            logBodies =
                    (metadataMap.containsKey(LOG_BODIES_ENABLED)
                            && metadataMap.get(LOG_BODIES_ENABLED, Boolean.class));
            logHeaders =
                    (metadataMap.containsKey(LOG_HEADERS_ENABLED)
                            && metadataMap.get(LOG_HEADERS_ENABLED, Boolean.class));
            // Grabbed from global directly, not metadatamap for backwards compatibility
            requestLogBufferSize =
                    geoServer.getGlobal().getXmlPostRequestLogBufferSize() != null
                            ? geoServer.getGlobal().getXmlPostRequestLogBufferSize()
                            : REQUEST_LOG_BUFFER_SIZE_DEFAULT;
        }

        String message = "";
        String path = "";

        if (enabled) {
            if (req instanceof HttpServletRequest) {
                HttpServletRequest hreq = (HttpServletRequest) req;

                path =
                        RequestUtils.getRemoteAddr(hreq)
                                + " \""
                                + hreq.getMethod()
                                + " "
                                + hreq.getRequestURI();
                if (hreq.getQueryString() != null) {
                    path += "?" + hreq.getQueryString();
                }
                path += "\"";

                message = "" + path;
                message += " \"" + noNull(hreq.getHeader("User-Agent"));
                message += "\" \"" + noNull(hreq.getHeader("Referer"));
                message += "\" \"" + noNull(hreq.getHeader("Content-type")) + "\" ";

                if (logHeaders) {
                    Enumeration<String> headerNames = hreq.getHeaderNames();
                    message += "\n  Headers:";
                    while (headerNames.hasMoreElements()) {
                        String headerName = headerNames.nextElement();
                        message += "\n    " + headerName + ": " + hreq.getHeader(headerName);
                    }
                }

                if (logBodies
                        && requestLogBufferSize > 0
                        && (hreq.getMethod().equals("PUT")
                                || hreq.getMethod().equals("POST")
                                || hreq.getMethod().equals("PATCH"))) {
                    message += " request-size: " + hreq.getContentLength();
                    message += " body: ";

                    BufferedRequestWrapper bodyCachingRequest = new BufferedRequestWrapper(hreq);
                    if (isBinary(hreq.getHeader("Content-type"))) {
                        byte[] body = bodyCachingRequest.getRequestBodyBytes();
                        message += (body != null ? body.length : 0) + " bytes (binary content)\n";
                    } else {
                        String body = bodyCachingRequest.getRequestBodyString();
                        message += (body == null ? "" : "\n" + body + "\n");
                    }
                    req = bodyCachingRequest;
                }
            } else {
                message = "" + req.getRemoteHost() + " made a non-HTTP request";
            }
            logger.info(message);

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
