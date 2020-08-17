/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
