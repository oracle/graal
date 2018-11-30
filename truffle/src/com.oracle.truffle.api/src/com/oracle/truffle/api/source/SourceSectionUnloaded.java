/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * Description of contiguous section of positions within a {@link Source} that does not have a
 * content loaded. {@link Source#hasCharacters()} = false.
 */
abstract class SourceSectionUnloaded extends SourceSection {

    SourceSectionUnloaded(Source source) {
        super(source);
    }

    @Override
    public final boolean isAvailable() {
        return true;
    }

    @Override
    final boolean isValid() {
        return true;
    }

    @Override
    public final CharSequence getCharacters() {
        return "";
    }

    static final class Indexed extends SourceSectionUnloaded {

        final int charIndex;
        final int charLength;

        Indexed(Source source, int charIndex, int charLength) {
            super(source);
            this.charIndex = charIndex;
            this.charLength = charLength;
        }

        @Override
        public boolean hasLines() {
            return false;
        }

        @Override
        public boolean hasColumns() {
            return false;
        }

        @Override
        public boolean hasCharIndex() {
            return true;
        }

        @Override
        public int getStartLine() {
            return 1;
        }

        @Override
        public int getStartColumn() {
            return 1;
        }

        @Override
        public int getEndLine() {
            return 1;
        }

        @Override
        public int getEndColumn() {
            return 1;
        }

        @Override
        public int getCharIndex() {
            return charIndex;
        }

        @Override
        public int getCharEndIndex() {
            return charIndex + charLength;
        }

        @Override
        public int getCharLength() {
            return charLength;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + charIndex;
            result = prime * result + charLength;
            result = prime * result + getSource().hashCode();
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
            if (!(obj instanceof Indexed)) {
                return false;
            }
            Indexed other = (Indexed) obj;
            if (charIndex != other.charIndex || charLength != other.charLength) {
                return false;
            }
            if (getSource() == null) {
                if (other.getSource() != null) {
                    return false;
                }
            } else if (!getSource().equals(other.getSource())) {
                return false;
            }
            return true;
        }

    }

    static final class Lines extends SourceSectionUnloaded {

        final int startLine;
        final int endLine;

        Lines(Source source, int startLine, int endLine) {
            super(source);
            this.startLine = startLine;
            this.endLine = endLine;
        }

        @Override
        public boolean hasLines() {
            return true;
        }

        @Override
        public boolean hasColumns() {
            return false;
        }

        @Override
        public boolean hasCharIndex() {
            return false;
        }

        @Override
        public int getStartLine() {
            return startLine;
        }

        @Override
        public int getStartColumn() {
            return 1;
        }

        @Override
        public int getEndLine() {
            return endLine;
        }

        @Override
        public int getEndColumn() {
            return 1;
        }

        @Override
        public int getCharIndex() {
            return 0;
        }

        @Override
        public int getCharEndIndex() {
            return 0;
        }

        @Override
        public int getCharLength() {
            return 0;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + startLine;
            result = prime * result + endLine;
            result = prime * result + getSource().hashCode();
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
            if (!(obj instanceof Lines)) {
                return false;
            }
            Lines other = (Lines) obj;
            if (startLine != other.startLine || endLine != other.endLine) {
                return false;
            }
            if (getSource() == null) {
                if (other.getSource() != null) {
                    return false;
                }
            } else if (!getSource().equals(other.getSource())) {
                return false;
            }
            return true;
        }

    }

    static final class LinesAndColumns extends SourceSectionUnloaded {

        final int startLine;
        final int startColumn;
        final int endLine;
        final int endColumn;

        LinesAndColumns(Source source, int startLine, int startColumn, int endLine, int endColumn) {
            super(source);
            this.startLine = startLine;
            this.startColumn = startColumn;
            this.endLine = endLine;
            this.endColumn = endColumn;
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
            return false;
        }

        @Override
        public int getStartLine() {
            return startLine;
        }

        @Override
        public int getStartColumn() {
            return startColumn;
        }

        @Override
        public int getEndLine() {
            return endLine;
        }

        @Override
        public int getEndColumn() {
            return endColumn;
        }

        @Override
        public int getCharIndex() {
            return 0;
        }

        @Override
        public int getCharEndIndex() {
            return 0;
        }

        @Override
        public int getCharLength() {
            return 0;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + startLine;
            result = prime * result + startColumn;
            result = prime * result + endLine;
            result = prime * result + endColumn;
            result = prime * result + getSource().hashCode();
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
            if (!(obj instanceof LinesAndColumns)) {
                return false;
            }
            LinesAndColumns other = (LinesAndColumns) obj;
            if (startLine != other.startLine || startColumn != other.startColumn ||
                            endLine != other.endLine || endColumn != other.endColumn) {
                return false;
            }
            if (getSource() == null) {
                if (other.getSource() != null) {
                    return false;
                }
            } else if (!getSource().equals(other.getSource())) {
                return false;
            }
            return true;
        }

    }

}
