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
import java.io.Reader;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

abstract class Content {

    private static final String URI_SCHEME = "truffle";

    String code;
    private volatile URI uri;

    abstract String findMimeType() throws IOException;

    abstract Reader getReader() throws IOException;

    abstract String getCode();

    abstract String getName();

    abstract String getShortName();

    abstract Object getHashKey();

    abstract String getPath();

    abstract URL getURL();

    abstract URI getURI();

    @SuppressWarnings("unused")
    void appendCode(CharSequence chars) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Content other = (Content) obj;
        return Objects.equals(getCode(), other.getCode());
    }

    @Override
    public int hashCode() {
        return getHashKey().hashCode();
    }

    protected final URI createURIOnce(CreateURI cu) {
        if (uri == null) {
            synchronized (this) {
                if (uri == null) {
                    uri = cu.createURI();
                }
            }
        }
        return uri;
    }

    protected final URI getNamedURI(String name) {
        return getNamedURI(name, null, 0, 0);
    }

    protected final URI getNamedURI(String name, byte[] bytes) {
        return getNamedURI(name, bytes, 0, bytes.length);
    }

    protected final URI getNamedURI(String name, byte[] bytes, int byteIndex, int length) {
        String digest;
        if (bytes != null) {
            MessageDigest md;
            try {
                md = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException ex) {
                throw new Error("SHA-256 must be supported", ex);
            }
            md.update(bytes, byteIndex, length);
            digest = new BigInteger(1, md.digest()).toString(36);
        } else {
            digest = Integer.toString(System.identityHashCode(this), 36);
        }
        try {
            return new URI(URI_SCHEME, digest + '/' + name, null);
        } catch (URISyntaxException ex) {
            throw new Error(ex);    // Should not happen
        }
    }

    protected interface CreateURI {
        URI createURI();
    }
}
