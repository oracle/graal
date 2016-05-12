/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

final class URLSourceImpl extends Source implements Cloneable {

    private static final Map<URL, WeakReference<URLSourceImpl>> urlToSource = new HashMap<>();

    public static URLSourceImpl get(URL url, String name) throws IOException {
        WeakReference<URLSourceImpl> sourceRef = urlToSource.get(url);
        URLSourceImpl source = sourceRef == null ? null : sourceRef.get();
        if (source == null) {
            source = new URLSourceImpl(url, name);
            urlToSource.put(url, new WeakReference<>(source));
        }
        return source;
    }

    private final URL url;
    private final String name;
    private String code; // A cache of the source contents

    URLSourceImpl(URL url, String name) throws IOException {
        this(url, url.openConnection(), name);
    }

    URLSourceImpl(URL url, URLConnection conn, String name) throws IOException {
        super(conn.getContentType());
        this.url = url;
        this.name = name;
        URLConnection c = url.openConnection();
        code = read(new InputStreamReader(c.getInputStream()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getShortName() {
        return name;
    }

    @Override
    public String getPath() {
        return url.getPath();
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    public Reader getReader() {
        return new StringReader(code);
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    void reset() {
    }

}
