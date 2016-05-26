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
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

final class BytesSourceImpl extends Content implements Content.CreateURI {

    private final String name;
    private final byte[] bytes;
    private final int byteIndex;
    private final int length;
    private final CharsetDecoder decoder;

    BytesSourceImpl(String name, byte[] bytes, int byteIndex, int length, Charset decoder) {
        this.name = name;
        this.bytes = bytes;
        this.byteIndex = byteIndex;
        this.length = length;
        this.decoder = decoder.newDecoder();
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
        return name;
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
        return getNamedURI(name, bytes, byteIndex, length);
    }

    @Override
    public Reader getReader() {
        return null;
    }

    @Override
    public String getCode() {
        ByteBuffer bb = ByteBuffer.wrap(bytes, byteIndex, length);
        CharBuffer chb;
        try {
            chb = decoder.decode(bb);
        } catch (CharacterCodingException ex) {
            return "";
        }
        return code = chb.toString();
    }

    @Override
    String findMimeType() throws IOException {
        return null;
    }

    @Override
    Object getHashKey() {
        int hash = bytes.length;
        if (bytes.length > 0) {
            int oneFourth = bytes.length / 4;
            int oneHalf = bytes.length / 2;
            hash ^= bytes[0];
            hash ^= (bytes[oneFourth] << 8);
            hash ^= (bytes[oneHalf] << 16);
            hash ^= (bytes[oneHalf + oneFourth] << 24);
        }
        return hash;
    }

}
