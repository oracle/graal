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

final class SubSourceImpl extends Content {

    private final Source base;
    private final int baseIndex;
    private final int subLength;

    static SubSourceImpl create(Source base, int baseIndex, int length) {
        if (baseIndex < 0 || length < 0 || baseIndex + length > base.getLength()) {
            throw new IllegalArgumentException("text positions out of range");
        }
        return new SubSourceImpl(base, baseIndex, length);
    }

    private SubSourceImpl(Source base, int baseIndex, int length) {
        this.base = base;
        this.baseIndex = baseIndex;
        this.subLength = length;
    }

    @Override
    public String getName() {
        return base.getName();
    }

    @Override
    public String getPath() {
        return base.getPath();
    }

    @Override
    public URL getURL() {
        return null;
    }

    @Override
    URI getURI() {
        return base.getURI();
    }

    @Override
    public Reader getReader() {
        assert false;
        return null;
    }

    @Override
    public CharSequence getCode() {
        return base.getCodeSequence().subSequence(baseIndex, baseIndex + subLength);
    }

    @Override
    String findMimeType() throws IOException {
        return base.getMimeType();
    }

    @Override
    Object getHashKey() {
        return base;
    }

}
