/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polyglot;

import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceSectionImpl;

/**
 * Description of contiguous section of text within a {@link Source} of program code.; supports
 * multiple modes of access to the text and its location.
 *
 * Two available source sections are considered equal if their sources, start and length are equal.
 * {@link #isAvailable() Unavailable} source sections are compared by identity. Source sections are
 * designed to be used as keys in hash maps.
 *
 * @since 1.0
 */
public final class SourceSection {

    static volatile AbstractSourceSectionImpl IMPL;

    final Source source;
    final Object impl;

    SourceSection(Source source, Object impl) {
        this.source = source;
        this.impl = impl;
    }

    /**
     * Returns whether this is a special instance that signifies that source information is
     * available. Available source sections are never equal to unavailable source sections.
     * Unavailable source sections return the same indices and lengths as empty source sections
     * starting at character index <code>0</code>.
     *
     * @since 1.0
     */
    public boolean isAvailable() {
        return IMPL.isAvailable(impl);
    }

    /**
     * Representation of the source program that contains this section.
     *
     * @return the source object.
     * @since 1.0
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns 1-based line number of the first character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections. Note
     * that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting line number.
     * @since 1.0
     */
    public int getStartLine() {
        return IMPL.getStartLine(impl);
    }

    /**
     * Returns the 1-based column number of the first character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections. Note
     * that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting column number.
     * @since 1.0
     */
    public int getStartColumn() {
        return IMPL.getStartColumn(impl);
    }

    /**
     * Returns 1-based line number of the last character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections. Note
     * that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting line number.
     * @since 1.0
     */
    public int getEndLine() {
        return IMPL.getEndLine(impl);
    }

    /**
     * Returns the 1-based column number of the last character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections. Note
     * that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting column number.
     * @since 1.0
     */
    public int getEndColumn() {
        return IMPL.getEndColumn(impl);
    }

    /**
     * Returns the 0-based index of the first character in this section. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections. Note that calling this method does not
     * cause the {@link Source#getCharacters() code} of the {@link #getSource() source} to be
     * loaded. The returned index might be out of bounds of the source code if assertions (-ea) are
     * not enabled.
     *
     * @return the starting character index.
     * @since 1.0
     */
    public int getCharIndex() {
        return IMPL.getCharIndex(impl);
    }

    /**
     * Returns the length of this section in characters. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections. Note that calling this method does not
     * cause the {@link Source#getCharacters() code} of the {@link #getSource() source} to be
     * loaded. The returned length might be out of bounds of the source code if assertions (-ea) are
     * not enabled.
     *
     * @return the number of characters in the section.
     * @since 1.0
     */
    public int getCharLength() {
        return IMPL.getCharLength(impl);
    }

    /**
     * Returns the index of the text position immediately following the last character in the
     * section. Returns <code>0</code> for {@link #isAvailable() unavailable} source sections. Note
     * that calling this method does not cause the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded. The returned index might be out of bounds of the
     * source code if assertions (-ea) are not enabled.
     *
     * @return the end position of the section.
     * @since 0.8 or earlier
     */
    public int getCharEndIndex() {
        return IMPL.getCharEndIndex(impl);
    }

    /**
     * @since 1.0
     * @deprecated use {@link #getCharacters()} instead.
     */
    @Deprecated
    public CharSequence getCode() {
        return IMPL.getCode(impl);
    }

    /**
     * Returns the source code fragment described by this section. Returns an empty string for out
     * of bounds or {@link #isAvailable() unavailable} source sections.
     *
     * @return the code as a string.
     * @since 1.0
     */
    public CharSequence getCharacters() {
        return IMPL.getCode(impl);
    }

    /**
     * Returns an implementation-defined string representation of this source section to be used for
     * debugging purposes only.
     *
     * @see #getCharacters()
     * @since 1.0
     */
    @Override
    public String toString() {
        return IMPL.toString(impl);
    }

    /** @since 1.0 or earlier */
    @Override
    public int hashCode() {
        return IMPL.hashCode(impl);
    }

    /** @since 1.0 or earlier */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        Object otherImpl = obj;
        if (otherImpl instanceof SourceSection) {
            otherImpl = ((SourceSection) obj).impl;
        }
        return IMPL.equals(impl, otherImpl);
    }

}
