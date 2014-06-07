/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * A special subtype of {@link SourceSection} that represents unavailable source, e.g. for language
 * <em>builtins</em>.
 */
public class NullSourceSection implements SourceSection {

    private final String kind;
    private final String name;
    private final String asCode;

    /**
     * Placeholder for source that is unavailable, e.g. for language <em>builtins</em>.
     *
     * @param kind the general category, e.g. "JS builtin"
     * @param name specific name for this section
     */
    public NullSourceSection(String kind, String name) {
        this(kind, name, kind);
    }

    /**
     * Placeholder for source that is unavailable, e.g. for language <em>builtins</em>.
     *
     * @param kind the general category, e.g. "JS builtin"
     * @param name specific name for this section
     * @param asCode string to return when {@link #getCode()} is called
     */
    public NullSourceSection(String kind, String name, String asCode) {
        this.kind = kind;
        this.name = name;
        this.asCode = asCode;
    }

    public final Source getSource() {
        return null;
    }

    public final int getStartLine() {
        throw new UnsupportedOperationException(this.toString());
    }

    public final LineLocation getLineLocation() {
        throw new UnsupportedOperationException(this.toString());
    }

    public final int getStartColumn() {
        throw new UnsupportedOperationException(this.toString());
    }

    public final int getCharIndex() {
        throw new UnsupportedOperationException(this.toString());
    }

    public final int getCharLength() {
        throw new UnsupportedOperationException(this.toString());
    }

    public final int getCharEndIndex() {
        throw new UnsupportedOperationException(this.toString());
    }

    public final String getIdentifier() {
        return name;
    }

    public final String getCode() {
        return asCode;
    }

    public final String getShortDescription() {
        return kind + ": " + name;
    }

    @Override
    public String toString() {
        return getShortDescription();
    }

}
