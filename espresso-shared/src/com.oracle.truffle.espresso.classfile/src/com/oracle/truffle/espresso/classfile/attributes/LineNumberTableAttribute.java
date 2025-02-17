/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import java.util.AbstractList;
import java.util.List;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

/**
 * Maps bytecode indexes to source line numbers.
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.12"
 */
public final class LineNumberTableAttribute extends Attribute implements LineNumberTableRef {

    public static final Symbol<Name> NAME = ParserNames.LineNumberTable;

    // Use an empty char array rather than null to throw IooB exceptions, rather than NPE.
    public static final LineNumberTableAttribute EMPTY = new LineNumberTableAttribute(NAME, new char[0]);

    private final char[] bciToLineEntries;

    private int lastLine = -1;

    public LineNumberTableAttribute(Symbol<Name> name, char[] entries) {
        assert name == NAME;
        assert entries.length % 2 == 0;
        this.bciToLineEntries = entries;
    }

    @Override
    public List<Entry> getEntries() {
        return new ListWrapper();
    }

    private int length() {
        return bciToLineEntries.length >> 1;
    }

    /**
     * Gets a source line number for bytecode index {@code atBci}.
     */
    public int getLineNumber(int atBci) {
        for (int i = 0; i < length() - 1; i++) {
            if (bciAt(i) <= atBci && atBci < bciAt(i + 1)) {
                return lineAt(i);
            }
        }
        return lineAt(length() - 1);
    }

    public long getBCI(int line) {
        for (int i = 0; i < length(); i++) {
            if (lineAt(i) == line) {
                return bciAt(i);
            }
        }
        return -1;
    }

    public int getLastLine() {
        if (lastLine != -1) {
            return lastLine;
        }
        int max = -1;
        for (int i = 0; i < length(); i++) {
            max = Math.max(max, lineAt(i));
        }
        return lastLine = max;
    }

    private int bciAt(int i) {
        return bciToLineEntries[i * 2];
    }

    private int lineAt(int i) {
        return bciToLineEntries[i * 2 + 1];
    }

    public static final class Entry implements EntryRef {

        private final int bci;
        private final int lineNumber;

        /**
         *
         * @param lineNumber an array of source line numbers. This array is now owned by this object
         *            and should not be mutated by the caller.
         * @param bci an array of bytecode indexes the same length at {@code lineNumbers} whose
         *            entries are sorted in ascending order. This array is now owned by this object
         *            and must not be mutated by the caller.
         */
        public Entry(int bci, int lineNumber) {
            this.bci = bci;
            this.lineNumber = lineNumber;
        }

        @Override
        public int getBCI() {
            return bci;
        }

        @Override
        public int getLineNumber() {
            return lineNumber;
        }
    }

    private final class ListWrapper extends AbstractList<Entry> {
        @Override
        public Entry get(int index) {
            if (index >= 0 && index < size()) {
                return new Entry(bciAt(index), lineAt(index));
            }
            throw new IndexOutOfBoundsException("index " + index + " out of bounds for list of size " + length() + ".");
        }

        @Override
        public int size() {
            return length();
        }
    }

    @Override
    public Symbol<Name> getName() {
        return NAME;
    }
}
