/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.component.installer;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.remote.FileDownloaderTest;

public class ChunkedConnection extends HttpURLConnection {
    public InputStream delegate;
    public volatile long nextChunk = Long.MAX_VALUE;
    public Semaphore nextSem = new Semaphore(0);
    public Semaphore reachedSem = new Semaphore(0);
    public URLConnection original;
    public volatile IOException readException;

    public ChunkedConnection(URL url, URLConnection original) {
        super(url);
        this.original = original;
    }

    @Override
    public void connect() throws IOException {
        original.connect();
    }

    @Override
    public String getHeaderField(String name) {
        return original.getHeaderField(name);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        delegate = original.getInputStream();
        return new InputStream() {
            @Override
            public int read() throws IOException {
                synchronized (ChunkedConnection.this) {
                    if (nextChunk == 0) {
                        reachedSem.release();
                        try {
                            nextSem.acquire();
                        } catch (InterruptedException ex) {
                            Logger.getLogger(FileDownloaderTest.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    if (readException != null) {
                        throw readException;
                    }
                    nextChunk--;
                    return delegate.read();
                }
            }
        };
    }

    @Override
    public long getContentLengthLong() {
        return original.getContentLengthLong();
    }

    @Override
    public int getContentLength() {
        return original.getContentLength();
    }

    @Override
    public void disconnect() {
    }

    @Override
    public boolean usingProxy() {
        return false;
    }

}
