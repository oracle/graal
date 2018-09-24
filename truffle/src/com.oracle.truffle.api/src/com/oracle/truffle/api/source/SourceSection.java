/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Description of contiguous section of text within a {@link Source} of program code.; supports
 * multiple modes of access to the text and its location.
 * <p>
 * Two available source sections are considered equal if their sources, start and length are equal.
 * {@link #isAvailable() Unavailable} source sections are compared by identity. Source sections can
 * be used as keys in hash maps.
 *
 * @see Source#createSection(int)
 * @see Source#createSection(int, int)
 * @see Source#createSection(int, int, int)
 * @see Source#createUnavailableSection()
 * @since 0.8 or earlier
 */
public final class SourceSection {

    private final Source source;
    private final int charIndex;
    private final int charLength; // -1 indicates unavailable

    SourceSection(Source source, int charIndex, int charLength) {
        this.source = source;
        this.charIndex = charIndex;
        this.charLength = charLength;
    }

    /**
     * Returns whether this is a special instance that signifies that source information is
     * available. Unavailable source sections can be created using
     * {@link Source#createUnavailableSection()}. Available source sections are never equal to
     * unavailable source sections. Unavailable source sections return the same indices and lengths
     * as empty source sections starting at character index <code>0</code>.
     *
     * @see Source#createUnavailableSection()
     * @since 0.18
     */
    public boolean isAvailable() {
        return charLength != -1;
    }

    /**
     * Returns whether the source section is in bounds of the {@link #getSource() source}
     * {@link Source#getCharacters() code}. Please note that calling this method causes the
     * {@link Source#getCharacters() code} of the {@link #getSource() source} to be loaded if it was
     * not yet loaded.
     */
    boolean isValid() {
        return isAvailable() ? (charIndex + charLength <= getSource().getCharacters().length()) : false;
    }

    /**
     * Representation of the source program that contains this section.
     *
     * @return the source object
     * @since 0.8 or earlier
     */
    public Source getSource() {
        return source;
    }

    /**
     * Returns 1-based line number of the first character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections.
     * Please note that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting line number
     * @since 0.8 or earlier
     */
    public int getStartLine() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        return source.getLineNumber(getCharIndex());
    }

    /**
     * Returns the 1-based column number of the first character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections.
     * Please note that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting column number
     * @since 0.8 or earlier
     */
    public int getStartColumn() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        return source.getColumnNumber(getCharIndex());
    }

    /**
     * Returns 1-based line number of the last character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections.
     * Please note that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting line number
     * @since 0.8 or earlier
     */
    public int getEndLine() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        return source.getLineNumber(getCharIndex() + Math.max(0, getCharLength() - 1));
    }

    /**
     * Returns the 1-based column number of the last character in this section (inclusive). Returns
     * <code>1</code> for out of bounds or {@link #isAvailable() unavailable} source sections.
     * Please note that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the starting column number
     * @since 0.8 or earlier
     */
    public int getEndColumn() {
        if (source == null) {
            return -1;
        }
        if (!isValid()) {
            return 1;
        }
        return source.getColumnNumber(getCharIndex() + Math.max(0, getCharLength() - 1));
    }

    /**
     * Returns the 0-based index of the first character in this section. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections. Please note that calling this method does
     * not cause the {@link Source#getCharacters() code} of the {@link #getSource() source} to be
     * loaded. The returned index might be out of bounds of the source code if assertions (-ea) are
     * not enabled.
     *
     * @return the starting character index
     * @since 0.8 or earlier
     */
    public int getCharIndex() {
        return charIndex;
    }

    /**
     * Returns the length of this section in characters. Returns <code>0</code> for
     * {@link #isAvailable() unavailable} source sections. Please note that calling this method does
     * not cause the {@link Source#getCharacters() code} of the {@link #getSource() source} to be
     * loaded. The returned length might be out of bounds of the source code if assertions (-ea) are
     * not enabled.
     *
     * @return the number of characters in the section
     * @since 0.8 or earlier
     */
    public int getCharLength() {
        if (source == null) {
            return -1;
        }
        return charLength == -1 ? 0 : charLength;
    }

    /**
     * Returns the index of the text position immediately following the last character in the
     * section. Returns <code>0</code> for {@link #isAvailable() unavailable} source sections.
     * Please note that calling this method does not cause the {@link Source#getCharacters() code}
     * of the {@link #getSource() source} to be loaded. The returned index might be out of bounds of
     * the source code if assertions (-ea) are not enabled.
     *
     * @return the end position of the section
     * @since 0.8 or earlier
     */
    public int getCharEndIndex() {
        if (source == null) {
            return -1;
        }
        return getCharIndex() + getCharLength();
    }

    /**
     * Returns the source code fragment described by this section. Returns an empty character
     * sequence for out of bounds or {@link #isAvailable() unavailable} source sections. Please note
     * that calling this method causes the {@link Source#getCharacters() code} of the
     * {@link #getSource() source} to be loaded if it was not yet loaded.
     *
     * @return the code as a CharSequence
     * @since 0.28
     */
    public CharSequence getCharacters() {
        if (!isValid()) {
            return "";
        }
        return source.getCharacters().subSequence(getCharIndex(), getCharEndIndex());
    }

    /**
     * Returns an implementation-defined string representation of this source section to be used for
     * debugging purposes only.
     *
     * @see #getCharacters()
     * @since 0.8 or earlier
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("SourceSection(source=").append(getSource().getName());
        if (isAvailable()) {
            b.append(", index=").append(getCharIndex());
            b.append(", length=").append(getCharLength());
            if (isValid()) {
                b.append(", characters=").append(getCharacters().toString().replaceAll("\\n", "\\\\n"));
            } else {
                b.append(", valid=false");
            }
        } else {
            b.append(" available=false");
        }
        b.append(")");
        return b.toString();
    }

    /** @since 0.8 or earlier */
    @Override
    public int hashCode() {
        if (!isAvailable()) {
            return System.identityHashCode(this);
        }
        final int prime = 31;
        int result = 1;
        result = prime * result + charIndex;
        result = prime * result + charLength;
        result = prime * result + source.hashCode();
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
        if (!(obj instanceof SourceSection)) {
            return false;
        }
        SourceSection other = (SourceSection) obj;
        if (!isAvailable()) {
            // Unavailable SourceSections are compared by identity
            return this == obj;
        }
        if (charIndex != other.charIndex) {
            return false;
        }
        if (charLength != other.charLength) {
            return false;
        }
        if (source == null) {
            if (other.source != null) {
                return false;
            }
        } else if (!source.equals(other.source)) {
            return false;
        }
        return true;
    }

}
