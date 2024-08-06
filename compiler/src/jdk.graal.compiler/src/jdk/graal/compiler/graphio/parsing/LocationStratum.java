/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.graphio.parsing;

import java.util.Objects;

public final class LocationStratum {
    public final String uri;
    public final String file;
    public final String language;
    public final int line;
    public final int startOffset;
    public final int endOffset;

    private static String intern(String a) {
        if (a != null) {
            return a.intern();
        }
        return null;
    }

    LocationStratum(String uri, String file, String language, int line, int startOffset, int endOffset) {
        this.uri = intern(uri);
        this.file = intern(file);
        this.language = intern(language);
        this.line = line;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.uri);
        hash = 41 * hash + Objects.hashCode(this.file);
        hash = 41 * hash + Objects.hashCode(this.language);
        hash = 41 * hash + this.line;
        hash = 41 * hash + this.startOffset;
        hash = 41 * hash + this.endOffset;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LocationStratum other = (LocationStratum) obj;
        if (this.line != other.line) {
            return false;
        }
        if (this.startOffset != other.startOffset) {
            return false;
        }
        if (this.endOffset != other.endOffset) {
            return false;
        }
        if (!Objects.equals(this.uri, other.uri)) {
            return false;
        }
        if (!Objects.equals(this.file, other.file)) {
            return false;
        }
        if (!Objects.equals(this.language, other.language)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(language).append("/");
        sb.append(uri != null ? uri : file);
        sb.append(":").append(line);
        if (startOffset > -1 || endOffset > -1) {
            sb.append("(");
            if (startOffset > -1) {
                sb.append(startOffset);
            }
            sb.append("-");
            if (endOffset > -1) {
                sb.append(endOffset);
            }
            sb.append(")");
        }
        return sb.toString();
    }

}
