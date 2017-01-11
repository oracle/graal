/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * A specification for a location in guest language source, expressed as a line number in a specific
 * instance of {@link Source}, suitable for hash table keys with equality defined in terms of
 * content.
 *
 * @since 0.8 or earlier
 * @deprecated without replacement
 */
@Deprecated
public final class LineLocation implements Comparable<LineLocation> {
    private final Source source;
    private final int line;

    LineLocation(Source source, int line) {
        assert source != null;
        this.source = source;
        this.line = line;
    }

    /** @since 0.8 or earlier */
    public Source getSource() {
        return source;
    }

    /**
     * Gets the 1-based number of a line in the source.
     *
     * @return value from 1 to infinity
     * @since 0.8 or earlier
     */
    public int getLineNumber() {
        return line;
    }

    /** @since 0.8 or earlier */
    public String getShortDescription() {
        return source.getName() + ":" + line;
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        return "Line[" + getShortDescription() + "]";
    }

    /** @since 0.8 or earlier */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + line;
        result = prime * result + source.getHashKey().hashCode();
        return result;
    }

    /** @since 0.8 or earlier */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof LineLocation)) {
            return false;
        }
        LineLocation other = (LineLocation) obj;
        if (line != other.line) {
            return false;
        }
        return source.getHashKey().equals(other.source.getHashKey());
    }

    /** @since 0.8 or earlier */
    @Override
    public int compareTo(LineLocation o) {
        int sourceResult = 0;
        final Source thisSource = this.getSource();
        final String thisPath = thisSource.getPath();
        final String otherPath = o.getSource().getPath();

        if (thisPath == null || otherPath == null) {
            sourceResult = thisSource.getCode().compareTo(o.getSource().getCode());
        } else {
            final String thatPath = otherPath;
            sourceResult = thisPath.compareTo(thatPath);
        }
        if (sourceResult != 0) {
            return sourceResult;
        }
        return Integer.compare(this.getLineNumber(), o.getLineNumber());
    }
}
