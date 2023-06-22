/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile;

import java.io.CharConversionException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.io.Utf8;

/**
 * Representation of a string section that complies to the usual format (plain list of \0-terminated
 * strings). This includes DWARF debug_str sections, ELF strtabs/shstrtabs, etc.. Note that since
 * this class can be used to implement ELF string sections, Mach-O string sections, DWARF string
 * sections, etc., it does not implement a particular Section superclass. Instead, various different
 * section classes (ELFSectionHeaderStringSection, ...) use it via composition.
 */
public class StringTable {

    protected NavigableMap<Integer, String> stringMap = new TreeMap<>();
    private Map<String, Integer> stringToIndexMap = new HashMap<>();
    private int totalSize;

    public StringTable() {
    }

    public StringTable(byte[] bytes) {
        this(ByteBuffer.wrap(bytes));
    }

    @SuppressWarnings("this-escape")
    public StringTable(ByteBuffer buffer) {
        read(buffer);
    }

    public void read(ByteBuffer buffer) {
        try {
            // FIXME: this is wrong if suffix encoding is used (?)
            while (buffer.position() < buffer.limit()) {
                final int index = buffer.position();
                String s = Utf8.utf8ToString(true, buffer);
                if (stringMap.containsKey(index)) {
                    throw new IllegalStateException("Offset cannot be re-used");
                }
                stringMap.put(index, s);
                stringToIndexMap.put(s, index);
            }
            totalSize = buffer.position();
        } catch (CharConversionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Add a string to the table (if it is not already present) and return its index. Use this
     * during from-scratch construction of the string table only!
     */
    public long add(String s) {
        Integer index = stringToIndexMap.get(s);
        if (index != null) {
            return index;
        }
        int newIndex = totalSize;
        stringMap.put(newIndex, s);
        totalSize += Utf8.utf8Length(s) + 1 /* zero termination */;
        return newIndex;
    }

    public int indexFor(String s) {
        Integer index = stringToIndexMap.get(s);
        if (index == null) {
            return -1;
        }
        return index;
    }

    public String get(String s) {
        return stringMap.get(indexFor(s));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(String.format("str%nindex   string%n==========================================================================%n"));
        for (Integer idx : stringMap.keySet()) {
            sb.append(String.format("%5d   %s%n", idx, stringMap.get(idx)));
        }
        return sb.toString();
    }

    public void write(OutputAssembler out) {
        ByteBuffer blob = ByteBuffer.allocate(totalSize);
        for (Integer index : stringMap.keySet()) {
            assert blob.position() == index;
            String s = stringMap.get(index);
            Utf8.substringToUtf8(blob, s, 0, s.length(), true);
        }
        out.writeBlob(blob.array());
    }

}
