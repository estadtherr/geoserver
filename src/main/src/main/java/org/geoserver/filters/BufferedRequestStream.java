/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.filters;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

/**
 * Wrap a request body as a ServletInputStream so we can read it multiple times.
 *
 * @author David Winslow <dwinslow@openplans.org>
 */
public class BufferedRequestStream extends ServletInputStream {

    ByteArrayInputStream myInputStream;

    public BufferedRequestStream(byte[] buff) throws IOException {
        myInputStream = new ByteArrayInputStream(buff);
    }

    @Override
    public int read() throws IOException {
        return myInputStream.read();
    }

    @Override
    public boolean isFinished() {
        return myInputStream.available() == 0;
    }

    @Override
    public boolean isReady() {
        return myInputStream.available() > 0;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("cannot use read listener with buffered request");
    }
}
