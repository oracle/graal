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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MockURLConnection extends URLConnection {
    private final URLConnection clu;
    private final IOException theException;

    public MockURLConnection(URLConnection clu, URL url, IOException theException) {
        super(url);
        this.clu = clu;
        this.theException = theException != null ? theException : new FileNotFoundException();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        connect();
        return clu.getInputStream();
    }

    @Override
    public String getHeaderField(int n) {
        try {
            connect();
        } catch (IOException ex) {
            Logger.getLogger(CatalogIterableTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        return super.getHeaderField(n);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        try {
            connect();
        } catch (IOException ex) {
            Logger.getLogger(CatalogIterableTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        return super.getHeaderFields();
    }

    @Override
    public void connect() throws IOException {
        throw theException;
    }
}
