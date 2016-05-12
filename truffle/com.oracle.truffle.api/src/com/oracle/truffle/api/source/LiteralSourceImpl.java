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
import java.io.StringReader;
import java.net.URL;
import java.util.Objects;

final class LiteralSourceImpl extends Content {

    private final String description;
    private final String code;

    LiteralSourceImpl(String description, String code) {
        this.description = description;
        this.code = code;
    }

    @Override
    public String getName() {
        return description;
    }

    @Override
    public String getShortName() {
        return description;
    }

    @Override
    public String getCode() {
        return code;
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
    public Reader getReader() {
        return new StringReader(code);
    }

    @Override
    void reset() {
    }

    @Override
    public int hashCode() {
        return Objects.hash(description, code);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj instanceof LiteralSourceImpl) {
            LiteralSourceImpl other = (LiteralSourceImpl) obj;
            return Objects.equals(description, other.description) && code.equals(other.code);
        }
        return false;
    }

    @Override
    String findMimeType() throws IOException {
        return null;
    }

    @Override
    Object getHashKey() {
        return code;
    }

}
