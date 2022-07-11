/* (c) 2014 - 2015 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.logging.Logger;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

public class BufferedRequestWrapper extends HttpServletRequestWrapper {

    protected byte[] requestBody;

    protected HttpServletRequest origRequest;

    protected Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.filters");

    public BufferedRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        origRequest = request;
        requestBody = StreamUtils.copyToByteArray(request.getInputStream());
    }

    public String getRequestBodyString() {
        String body;
        String requestEncoding = origRequest.getCharacterEncoding();
        if (null != requestEncoding) {
            try {
                body = new String(requestBody, Charset.forName(requestEncoding));
            } catch (UnsupportedCharsetException e) {
                logger.warning(
                        "unable to decode request body - unsupported character set: "
                                + requestEncoding);
                body = null;
            }
        } else {
            body = new String(requestBody);
        }
        return body;
    }

    public byte[] getRequestBodyBytes() {
        return requestBody;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new BufferedRequestStream(requestBody);
    }
}
