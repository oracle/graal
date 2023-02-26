/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

import com.oracle.svm.core.jdk.JavaNetSubstitutions;
import com.oracle.svm.core.jdk.Resources;

public class ResourceURLConnection extends URLConnection {

    private byte[] data;

    public ResourceURLConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() {
        if (connected) {
            return;
        }
        connected = true;

        String urlHost = url.getHost();
        String hostNameOrNull = urlHost != null && !urlHost.isEmpty() ? urlHost : null;
        String urlPath = url.getPath();
        if (urlPath.isEmpty()) {
            throw new IllegalArgumentException("Empty URL path not allowed in " + JavaNetSubstitutions.RESOURCE_PROTOCOL + " URL");
        }
        String resourceName = urlPath.substring(1);
        ResourceStorageEntry entry = Resources.get(hostNameOrNull, resourceName);
        if (entry != null) {
            List<byte[]> bytes = entry.getData();
            String urlRef = url.getRef();
            int index = 0;
            if (urlRef != null) {
                try {
                    index = Integer.valueOf(urlRef);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("URL anchor '#" + urlRef + "' not allowed in " + JavaNetSubstitutions.RESOURCE_PROTOCOL + " URL");
                }
            }
            if (index < bytes.size()) {
                this.data = bytes.get(index);
            } else {
                // This will happen only in case that we are creating one URL with the second URL as
                // a context.
                this.data = bytes.get(0);
            }
        } else {
            this.data = null;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        // Operations that depend on being connected will implicitly perform the connection, if
        // necessary.
        connect();
        if (data == null) {
            throw new FileNotFoundException(url.toString());
        }
        return new ByteArrayInputStream(data);
    }

    @Override
    public long getContentLengthLong() {
        // Operations that depend on being connected will implicitly perform the connection, if
        // necessary.
        connect();
        return data != null ? data.length : -1L;
    }
}
