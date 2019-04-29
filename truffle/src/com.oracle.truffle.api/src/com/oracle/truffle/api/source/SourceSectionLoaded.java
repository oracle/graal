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
 * SourceSection with a loaded content. {@link Source#hasCharacters()} = true.
 */
final class SourceSectionLoaded extends SourceSection {

    final int charIndex;
    final int charLength;

    SourceSectionLoaded(Source source, int charIndex, int charLength) {
        super(source);
        this.charIndex = charIndex;
        this.charLength = charLength;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean hasLines() {
        return true;
    }

    @Override
    public boolean hasColumns() {
        return true;
    }

    @Override
    public boolean hasCharIndex() {
        return true;
    }

    /**
     * Returns whether the source section is in bounds of the {@link #getSource() source}
     * {@link Source#getCharacters() code}. Please note that calling this method causes the
     * {@link Source#getCharacters() code} of the {@link #getSource() source} to be loaded if it was
     * not yet loaded.
     */
    @Override
    boolean isValid() {
        return charIndex + charLength <= getSource().getCharacters().length();
    }

    @Override
    public int getStartLine() {
        if (!isValid()) {
            return 1;
        }
        return source.getLineNumber(getCharIndex());
    }

    @Override
    public int getStartColumn() {
        if (!isValid()) {
            return 1;
        }
        return source.getColumnNumber(getCharIndex());
    }

    @Override
    public int getEndLine() {
        if (!isValid()) {
            return 1;
        }
        return source.getLineNumber(getCharIndex() + Math.max(0, getCharLength() - 1));
    }

    @Override
    public int getEndColumn() {
        if (!isValid()) {
            return 1;
        }
        return source.getColumnNumber(getCharIndex() + Math.max(0, getCharLength() - 1));
    }

    @Override
    public int getCharIndex() {
        return charIndex;
    }

    @Override
    public int getCharLength() {
        return charLength;
    }

    @Override
    public int getCharEndIndex() {
        return getCharIndex() + getCharLength();
    }

    @Override
    public CharSequence getCharacters() {
        if (!isValid()) {
            return "";
        }
        return source.getCharacters().subSequence(getCharIndex(), getCharEndIndex());
    }

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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (obj.getClass() != SourceSectionLoaded.class) {
            return false;
        }
        SourceSectionLoaded other = (SourceSectionLoaded) obj;
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
