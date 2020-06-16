/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @since 19.0
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
     * @since 19.0
     */
    public boolean isAvailable() {
        return IMPL.isAvailable(impl);
    }

    /**
     * Returns <code>true</code> if this section has a line number information, <code>false</code>
     * otherwise. When <code>true</code>, {@link #getStartLine()} and {@link #getEndLine()} return
     * valid line numbers, when <code>false</code>, {@link #getStartLine()} and
     * {@link #getEndLine()} return <code>1</code>.
     *
     * @since 19.0
     */
    public boolean hasLines() {
        return IMPL.hasLines(impl);
    }

    /**
     * Returns <code>true</code> if this section has a column number information, <code>false</code>
     * otherwise. When <code>true</code>, {@link #hasLines()} is <code>true</code> as well,
     * {@link #getStartColumn()} and {@link #getEndColumn()} return valid column numbers. When
     * <code>false</code>, {@link #getStartColumn()} and {@link #getEndColumn()} return
     * <code>1</code>.
     *
     * @since 19.0
     */
    public boolean hasColumns() {
        return IMPL.hasColumns(impl);
    }

    /**
     * Returns <code>true</code> if this section has a character index information,
     * <code>false</code> otherwise. When <code>true</code>, {@link #getCharIndex()},
     * {@link #getCharEndIndex()} and {@link #getCharLength()} return valid character indices, when
     * <code>false</code>, {@link #getCharIndex()}, {@link #getCharEndIndex()} and
     * {@link #getCharLength()} return <code>0</code>.
     *
     * @since 19.0
     */
    public boolean hasCharIndex() {
        return IMPL.hasCharIndex(impl);
    }

    /**
     * Representation of the source program that contains this section.
     *
     * @return the source object.
     * @since 19.0
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns 1-based line number of the first character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections, or
     * source sections not {@link #hasLines() having lines}.
     *
     * @return the starting line number.
     * @see #hasLines()
     * @since 19.0
     */
    public int getStartLine() {
        return IMPL.getStartLine(impl);
    }

    /**
     * Returns the 1-based column number of the first character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections, or
     * source sections not {@link #hasColumns() having columns}.
     *
     * @return the starting column number.
     * @see #hasColumns()
     * @since 19.0
     */
    public int getStartColumn() {
        return IMPL.getStartColumn(impl);
    }

    /**
     * Returns 1-based line number of the last character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections, or
     * source sections not {@link #hasLines() having lines}.
     *
     * @return the starting line number.
     * @see #hasLines()
     * @since 19.0
     */
    public int getEndLine() {
        return IMPL.getEndLine(impl);
    }

    /**
     * Returns the 1-based column number of the last character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections, or
     * source sections not {@link #hasColumns() having columns}.
     *
     * @return the starting column number.
     * @see #hasColumns()
     * @since 19.0
     */
    public int getEndColumn() {
        return IMPL.getEndColumn(impl);
    }

    /**
     * Returns the 0-based index of the first character in this section. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections, or sections not {@link #hasCharIndex()
     * having character index}. The returned index might be out of bounds of the source code if
     * assertions (-ea) are not enabled.
     *
     * @return the starting character index.
     * @see #hasCharIndex()
     * @since 19.0
     */
    public int getCharIndex() {
        return IMPL.getCharIndex(impl);
    }

    /**
     * Returns the length of this section in characters. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections, or sections not {@link #hasCharIndex()
     * having character index}. The returned length might be out of bounds of the source code if
     * assertions (-ea) are not enabled.
     *
     * @return the number of characters in the section.
     * @see #hasCharIndex()
     * @since 19.0
     */
    public int getCharLength() {
        return IMPL.getCharLength(impl);
    }

    /**
     * Returns the index of the text position immediately following the last character in the
     * section. Returns <code>0</code> for {@link #isAvailable() unavailable} source sections, or
     * sections not {@link #hasCharIndex() having character index}. The returned index might be out
     * of bounds of the source code if assertions (-ea) are not enabled.
     *
     * @return the end position of the section.
     * @see #hasCharIndex()
     * @since 19.0
     */
    public int getCharEndIndex() {
        return IMPL.getCharEndIndex(impl);
    }

    /**
     * @since 19.0
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
     * @since 19.0
     */
    public CharSequence getCharacters() {
        return IMPL.getCode(impl);
    }

    /**
     * Returns an implementation-defined string representation of this source section to be used for
     * debugging purposes only.
     *
     * @see #getCharacters()
     * @since 19.0
     */
    @Override
    public String toString() {
        return IMPL.toString(impl);
    }

    /** @since 19.0 or earlier */
    @Override
    public int hashCode() {
        return IMPL.hashCode(impl);
    }

    /** @since 19.0 or earlier */
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
