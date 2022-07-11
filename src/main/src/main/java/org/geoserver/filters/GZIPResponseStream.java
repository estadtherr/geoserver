/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * A simple streaming gzipping servlet output stream wrapper
 *
 * @author Andrea Aime - GeoSolutions
 */
public class GZIPResponseStream extends ServletOutputStream {
    protected GZIPOutputStream gzipStream;
    protected ServletOutputStream delegateStream;

    public GZIPResponseStream(ServletOutputStream delegateStream) throws IOException {
        super();
        this.delegateStream = delegateStream;
        gzipStream = new GZIPOutputStream(delegateStream, 4096, true);
    }

    @Override
    public void close() throws IOException {
        gzipStream.close();
    }

    @Override
    public void flush() throws IOException {
        gzipStream.flush();
    }

    @Override
    public void write(int i) throws IOException {
        gzipStream.write(i);
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        gzipStream.write(b, off, len);
    }

    @Override
    public boolean isReady() {
        return delegateStream.isReady();
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        delegateStream.setWriteListener(writeListener);
    }
}
