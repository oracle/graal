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

import java.net.URI;
import java.net.URL;

import org.graalvm.polyglot.io.ByteSequence;

final class SubSourceImpl extends Source {

    private final Key key;

    static Source create(Source base, int baseIndex, int length) {
        if (baseIndex < 0 || length < 0 || baseIndex + length > base.getLength()) {
            throw new IllegalArgumentException("text positions out of range");
        }
        return new SubSourceImpl(new Key(base, baseIndex, length));
    }

    private SubSourceImpl(Key key) {
        this.key = key;
    }

    @Override
    Source copy() {
        return new SubSourceImpl(key);
    }

    @Override
    public boolean hasBytes() {
        return key.base.hasBytes();
    }

    @Override
    public boolean hasCharacters() {
        return key.base.hasCharacters();
    }

    @Override
    public boolean isCached() {
        return key.base.isCached();
    }

    @Override
    protected Object getSourceId() {
        return key;
    }

    @Override
    public String getName() {
        return key.base.getName();
    }

    @Override
    boolean isLegacy() {
        return key.base.isLegacy();
    }

    @Override
    public String getPath() {
        return key.base.getPath();
    }

    @Override
    public URL getURL() {
        return key.base.getURL();
    }

    @Override
    public URI getOriginalURI() {
        return key.base.getURI();
    }

    @Override
    public ByteSequence getBytes() {
        return key.base.getBytes().subSequence(key.baseIndex, key.baseIndex + key.subLength);
    }

    @Override
    public CharSequence getCharacters() {
        return key.base.getCharacters().subSequence(key.baseIndex, key.baseIndex + key.subLength);
    }

    @Override
    public boolean isInternal() {
        return key.base.isInternal();
    }

    @Override
    public boolean isInteractive() {
        return key.base.isInteractive();
    }

    @Override
    public String getMimeType() {
        return key.base.getMimeType();
    }

    @Override
    public String getLanguage() {
        return key.base.getLanguage();
    }

    private static final class Key {

        final Source base;
        final int baseIndex;
        final int subLength;

        Key(Source base, int baseIndex, int length) {
            this.base = base;
            this.baseIndex = baseIndex;
            this.subLength = length;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            return base.equals(other.base) && baseIndex == other.baseIndex && subLength == other.subLength;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + base.hashCode();
            result = prime * result + baseIndex;
            result = prime * result + subLength;
            return result;
        }

    }

}
