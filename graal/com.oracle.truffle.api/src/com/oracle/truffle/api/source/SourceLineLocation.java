/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;

/**
 * A specification for a location in guest language source, expressed as a line number in a specific
 * instance of {@link Source}, suitable for hash table keys with equality defined in terms of
 * content.
 */
public class SourceLineLocation implements Comparable {

    private final Source source;
    private final int line;

    public SourceLineLocation(Source source, int line) {
        assert source != null;
        this.source = source;
        this.line = line;
    }

    public SourceLineLocation(SourceSection sourceSection) {
        this(sourceSection.getSource(), sourceSection.getStartLine());
    }

    public Source getSource() {
        return source;
    }

    public int getLine() {
        return line;
    }

    @Override
    public String toString() {
        return "SourceLine [" + source.getName() + ", " + line + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + line;
        result = prime * result + source.hashCode();
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof SourceLineLocation)) {
            return false;
        }
        SourceLineLocation other = (SourceLineLocation) obj;
        if (line != other.line) {
            return false;
        }
        return source.equals(other.source);
    }

    @Override
    public int compareTo(Object o) {
        final SourceLineLocation other = (SourceLineLocation) o;
        final int nameOrder = source.getName().compareTo(other.source.getName());
        if (nameOrder != 0) {
            return nameOrder;
        }
        return Integer.compare(line, other.line);
    }

}
