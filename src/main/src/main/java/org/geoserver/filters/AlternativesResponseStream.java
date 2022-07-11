/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

/**
 * A response stream that figures out whether to compress the output just before the first write.
 * The decision is based on the mimetype set for the output request.
 *
 * @author David Winslow <dwinslow@openplans.org>
 */
public class AlternativesResponseStream extends ServletOutputStream {
    HttpServletResponse myResponse;
    ServletOutputStream myOriginalResponseStream;
    ServletOutputStream myStream;
    Set<Pattern> myCompressibleTypes;
    Logger logger = org.geotools.util.logging.Logging.getLogger("org.geoserver.filters");
    int contentLength;

    public AlternativesResponseStream(
            HttpServletResponse response, Set<Pattern> compressible, int contentLength)
            throws IOException {
        super();
        myResponse = response;
        myOriginalResponseStream = response.getOutputStream();
        myCompressibleTypes = compressible;
        this.contentLength = contentLength;
    }

    @Override
    public void close() throws IOException {
        if (isDirty()) getStream().close();
    }

    @Override
    public void flush() throws IOException {
        if (isDirty()) getStream().flush();
    }

    @Override
    public void write(int b) throws IOException {
        getStream().write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        getStream().write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        getStream().write(b, off, len);
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        // pass this through to the original ServletOutputStream
        myOriginalResponseStream.setWriteListener(writeListener);
    }

    protected ServletOutputStream getStream() throws IOException {
        if (myStream != null) return myStream;
        String type = myResponse.getContentType();

        //        if (type == null){
        //            logger.warning("Mime type was not set before first write!");
        //        }

        if (type != null && isCompressible(type)) {
            logger.log(Level.FINE, "Compressing output for mimetype: {0}", type);
            myResponse.addHeader("Content-Encoding", "gzip");
            myStream = new GZIPResponseStream(myResponse.getOutputStream());
        } else {
            logger.log(Level.FINE, "Not compressing output for mimetype: {0}", type);
            if (contentLength >= 0) {
                myResponse.setContentLength(contentLength);
            }
            myStream = myResponse.getOutputStream();
        }

        return myStream;
    }

    protected boolean isDirty() {
        return myStream != null;
    }

    @Override
    public boolean isReady() {
        return myOriginalResponseStream.isReady();
    }

    protected boolean isCompressible(String mimetype) {
        String stripped = stripParams(mimetype);

        for (Pattern myCompressibleType : myCompressibleTypes) {
            Matcher matcher = myCompressibleType.matcher(stripped);
            if (matcher.matches()) {
                return true;
            }
        }

        return false;
    }

    protected String stripParams(String mimetype) {
        int firstSemicolon = mimetype.indexOf(";");

        if (firstSemicolon != -1) {
            return mimetype.substring(0, firstSemicolon);
        }

        return mimetype;
    }
}
