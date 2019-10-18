/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;

/**
 * A utility for converting between coordinate systems in a string of text interspersed with newline
 * characters. The coordinate systems are:
 * <ul>
 * <li>0-based character offset from the beginning of the text, where newline characters count as a
 * single character and the first character in the text occupies position 0.</li>
 * <li>1-based position in the 2D space of lines and columns, in which the first position in the
 * text is at (1,1).</li>
 * </ul>
 * <p>
 * This utility is based on positions occupied by characters, not text stream positions as in a text
 * editor. The distinction shows up in editors where you can put the cursor just past the last
 * character in a buffer; this is necessary, among other reasons, so that you can put the edit
 * cursor in a new (empty) buffer. For the purposes of this utility, however, there are no character
 * positions in an empty text string and there are no lines in an empty text string.
 * <p>
 * A newline character designates the end of a line and occupies a column position.
 * <p>
 * If the text ends with a character other than a newline, then the characters following the final
 * newline character count as a line, even though not newline-terminated. Following line delimiters
 * are used: "\n", "\r", "\r\n"
 * <p>
 * <strong>Limitations:</strong>
 * <ul>
 * <li>Does not handle multiple character encodings correctly.</li>
 * <li>Treats tabs as occupying 1 column.</li>
 * </ul>
 */
final class TextMap {

    // 0-based offsets of newline characters in the text, with sentinel
    private final int[] nlOffsets;
    // The number of characters in the text, including newlines.
    private final int textLength;
    // Length of newline characters (1 for '\n', or 2 for "\r\n") valid unless newlineLengths is set
    private final int newlineLength;
    // Lengths of newlines, if newlines with different lengths are present.
    private final int[] newlineLengths;
    // Is the final text character a newline?
    final boolean finalNL;

    TextMap(int[] nlOffsets, int textLength, int newlineLength, int[] newlineLengths, boolean finalNL) {
        this.nlOffsets = nlOffsets;
        this.textLength = textLength;
        this.newlineLength = newlineLength;
        this.newlineLengths = newlineLengths;
        this.finalNL = finalNL;
    }

    /**
     * Constructs map permitting translation between 0-based character offsets and 1-based
     * lines/columns.
     */
    public static TextMap fromCharSequence(CharSequence text) {
        final int textLength = text.length();
        ArrayList<Integer> lines;
        int newlineLength = 0; // 0 - unset, > 0 equal length, < 0 variable length
        ArrayList<Integer> nlLengths = null;
        // Suppose that all newlines have the same length.
        // If not, we'll set nlLengths in the second pass.
        do {
            lines = new ArrayList<>();
            lines.add(0);
            int offset = 0;
            if (newlineLength == -1) {
                // There are newlines of different lengths
                nlLengths = new ArrayList<>();
                newlineLength = -2;
            }
            while (offset < textLength) {
                int nlIndex = offset;
                char c = 0;
                while (nlIndex < textLength) {
                    c = text.charAt(nlIndex);
                    if (c == '\n' || c == '\r') {
                        break;
                    }
                    nlIndex++;
                }
                if (nlIndex < textLength) {
                    int nlLength = getNewlineLength(c, text, textLength, nlIndex);
                    // Store the length of newline
                    newlineLength = adjustNewlineLength(nlLength, newlineLength, nlLengths);
                    if (newlineLength == -1) {
                        // variable length of newlines.
                        break;
                    }
                    offset = nlIndex + nlLength;
                    lines.add(offset);
                } else {
                    break;
                }
            }
        } while (newlineLength == -1);
        lines.add(Integer.MAX_VALUE);
        final int[] nlOffsets = list2ints(lines);
        final int[] newlineLengths;
        if (nlLengths != null) {
            assert nlLengths.size() == lines.size() - 2;
            newlineLengths = list2ints(nlLengths);
        } else {
            newlineLengths = null;
        }
        final boolean finalNL = textLength > 0 && (textLength == nlOffsets[nlOffsets.length - 2]);
        return new TextMap(nlOffsets, textLength, newlineLength, newlineLengths, finalNL);
    }

    private static int getNewlineLength(char c, CharSequence text, int textLength, int nlIndex) {
        if (c == '\r' && (nlIndex + 1) < textLength && text.charAt(nlIndex + 1) == '\n') {
            return 2;
        } else {
            return 1;
        }
    }

    private static int adjustNewlineLength(int nlLength, int oldNewlineLength, ArrayList<Integer> nlLengths) {
        int newlineLength = oldNewlineLength;
        if (newlineLength >= 0) {
            if (newlineLength == 0) {
                newlineLength = nlLength;
            } else if (newlineLength != nlLength) {
                newlineLength = -1;
            }
        } else {
            nlLengths.add(nlLength);
        }
        return newlineLength;
    }

    private static int[] list2ints(ArrayList<Integer> list) {
        int size = list.size();
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = list.get(i);
        }
        return array;
    }

    /**
     * Converts 0-based character offset to 1-based number of the line containing the character.
     *
     * @throws IllegalArgumentException if the offset is outside the string.
     */
    public int offsetToLine(int offset) throws IllegalArgumentException {
        if (offset < 0 || offset > textLength) {
            throw new IllegalArgumentException("offset out of bounds");
        }
        return binarySearchLine(nlOffsets, offset) + 1;
    }

    private static int binarySearchLine(int[] a, int key) {
        int low = 0;
        int high = a.length - 1;

        int mid = 0;
        int midVal;
        while (low <= high) {
            mid = (low + high) >>> 1;
            midVal = a[mid];

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                high = mid;
                break; // direct hit
            }
        }
        return high;  // return high index
    }

    /**
     * Converts 0-based character offset to 1-based number of the column occupied by the character.
     * <p>
     * Tabs are not expanded; they occupy 1 column.
     *
     * @throws IllegalArgumentException if the offset is outside the string.
     */
    public int offsetToCol(int offset) throws IllegalArgumentException {
        return 1 + offset - nlOffsets[offsetToLine(offset) - 1];
    }

    /**
     * The number of characters in the mapped text.
     */
    public int length() {
        return textLength;
    }

    /**
     * The number of lines in the text; if characters appear after the final newline, then they also
     * count as a line, even though not newline-terminated.
     */
    public int lineCount() {
        if (textLength == 0) {
            return 0;
        }
        return finalNL ? nlOffsets.length - 2 : nlOffsets.length - 1;
    }

    /**
     * Converts 1-based line number to the 0-based offset of the line's first character; this would
     * be the offset of a newline if the line is empty.
     *
     * @throws IllegalArgumentException if there is no such line in the text.
     */
    public int lineStartOffset(int line) throws IllegalArgumentException {
        if (lineOutOfRange(line)) {
            throw new IllegalArgumentException("line out of bounds");
        }
        return nlOffsets[line - 1];
    }

    /**
     * Gets the number of characters in a line, identified by 1-based line number; <em>does not</em>
     * include the final newline, if any.
     *
     * @throws IllegalArgumentException if there is no such line in the text.
     */
    public int lineLength(int line) throws IllegalArgumentException {
        if (lineOutOfRange(line)) {
            throw new IllegalArgumentException("line out of bounds");
        }
        if (line == nlOffsets.length - 1) {
            return textLength - nlOffsets[line - 1];
        }
        int nlLength;
        if (newlineLengths != null) {
            nlLength = newlineLengths[line - 1];
        } else {
            nlLength = newlineLength;
        }
        return (nlOffsets[line] - nlOffsets[line - 1]) - nlLength;
    }

    /**
     * Converts 1-based line number and 1-based column number to the 0-based offset of the character
     * position at the line:column.
     *
     * @throws IllegalArgumentException if the column is out of range.
     */
    public int lineColumnToOffset(int line, int column) {
        final int lineStartOffset = lineStartOffset(line);
        if (column > (lineLength(line) + 1)) {
            throw new IllegalArgumentException("column out of range");
        }
        final int charIndex = lineStartOffset + column - 1;
        return charIndex;
    }

    /**
     * Is the line number out of range.
     */
    private boolean lineOutOfRange(int line) {
        return line <= 0 || line >= nlOffsets.length;
    }

}
