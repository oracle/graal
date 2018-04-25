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
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Function;

import com.sun.org.apache.bcel.internal.util.ByteSequence;

final class ByteSequenceSourceImpl extends Content implements Content.CreateURI {

    private final String name;
    private final ByteSequence bytes;

    ByteSequenceSourceImpl(String name, ByteSequence bytes) {
        this.name = name;
        this.bytes = bytes;
    }

    @Override
    public String getName() {
        return name;
    }

    private CharSequence materializeCode() {
        if (code == null) {
            synchronized (this) {
                if (code == null) {
                    if (materialize == null) {
                        code = "";
                    } else {
                        code = materialize.apply(bytes);
                    }
                }
            }
        }
        return code;
    }

    @Override
    boolean isBinary() {
        return true;
    }

    @Override
    public CharSequence getCharacters() {
        return materializeCode();
    }

    @Override
    public String getPath() {
        return null;
    }

    @Override
    public URL getURL() {
        return null;
    }

    @Override
    URI getURI() {
        return createURIOnce(this);
    }

    @Override
    public URI createURI() {
        return getNamedURI(name, bytes);
    }

    @Override
    public Reader getReader() {
        return new CharSequenceReader(materializeCode());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, bytes);
    }

    @Override
    String findMimeType() throws IOException {
        return null;
    }

    @Override
    Object getHashKey() {
        return bytes;
    }

}
