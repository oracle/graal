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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

final class URLSourceImpl extends Content {

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
    private final URI uri;
    private final String name;

    URLSourceImpl(URL url, String name) throws IOException {
        this(url, url.openConnection(), name);
    }

    URLSourceImpl(URL url, URLConnection c, String name) throws IOException {
        this.url = url;
        this.name = name;
        try {
            this.uri = url.toURI();
        } catch (URISyntaxException ex) {
            throw new IOException("Bad URL: " + url, ex);
        }
        this.code = Source.read(new InputStreamReader(c.getInputStream()));
    }

    URLSourceImpl(URL url, String code, String name) throws IOException {
        this.url = url;
        this.name = name;
        try {
            this.uri = url.toURI();
        } catch (URISyntaxException ex) {
            throw new IOException("Bad URL: " + url, ex);
        }
        if (code != null) {
            this.code = code;
        } else {
            this.code = Source.read(new InputStreamReader(url.openStream()));
        }
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
        return url.toExternalForm();
    }

    @Override
    public URL getURL() {
        return url;
    }

    @Override
    URI getURI() {
        return uri;
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
    String findMimeType() throws IOException {
        Path path;
        try {
            path = Paths.get(url.toURI());
            String firstGuess = FileSourceImpl.findMimeType(path);
            if (firstGuess != null) {
                return firstGuess;
            }
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException ex) {
            // swallow and go on
        }
        return url.openConnection().getContentType();
    }

    @Override
    Object getHashKey() {
        return url;
    }

}
