/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.InputDisassembler;
import com.oracle.objectfile.io.OutputAssembler;

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

    public StringTable(byte[] bytes) {
        this(AssemblyBuffer.createInputDisassembler(ByteBuffer.wrap(bytes)), bytes.length);
    }

    public StringTable(InputDisassembler db, int size) {
        read(db, size);
    }

    public void read(InputDisassembler db, int size) {
        final int charsToRead = size;
        int charsRead = 0; // also serves as string section index
        // FIXME: this is wrong if suffix encoding is used
        while (charsRead < charsToRead) {
            final String s = db.readZeroTerminatedString();
            if (stringMap.get(charsRead) != null) {
                throw new IllegalStateException("offset cannot be re-used");
            }
            Integer index = Integer.valueOf(charsRead);
            stringMap.put(index, s);
            stringToIndexMap.put(s, index);
            charsRead += s.length() + 1; // also count the trailing 0 char
        }
        totalSize = charsRead;
    }

    public StringTable() {
        // default constructor, nothing to do
    }

    /**
     * Add a string to the table (if it is not already present) and return its index. Use this
     * during from-scratch construction of the string table only!
     */
    public long add(String s) {
        // TODO improve this entire class: it would probably be better to operate on a blob
        Integer index = stringToIndexMap.get(s);
        if (index != null) {
            return index;
        }
        int newIndex = totalSize;
        stringMap.put(newIndex, s);
        totalSize += s.length() + 1;
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

    /**
     * Retrieve a String from the table.
     */
    public String fromIndex(int index) {
        Integer floorKey = stringMap.floorKey(index);
        Map.Entry<Integer, String> ent = stringMap.floorEntry(index);
        if (ent != null) {
            assert index >= floorKey;
            String toReturn = ent.getValue().substring(index - floorKey);
            if (index != floorKey) {
                // store it in the map for fast subsequent access
                stringMap.put(index, toReturn);
            }
            return toReturn;
        }
        return null;
        //@formatter:off
//        if (str == null) {
//            // Some of those references are into the middle of strings in the section (hack!).
//            // For such indices, the preferred (non-hack!) strategy will obviously return null.
//            // We resolve these by finding the closest index and returning a substring.
//            // The result of this is then stored in the map.
//            List<Integer> keys = sortedKeys();
//            for (int i = 0; i < keys.size() - 1; ++i) {
//                final long ki = keys.get(i);
//                if (ki < index && index < keys.get(i + 1)) {
//                    final String superString = stringMap.get(ki);
//                    str = superString.substring((int) (index - ki));
//                    stringMap.put(index, str);
//                    return str;
//                }
//            }
//            final long ki = keys.get(keys.size() - 1);
//            final String lastString = stringMap.get(ki);
//            if (ki < index && index - ki < lastString.length()) {
//                str = lastString.substring((int) (index - ki));
//                stringMap.put(index, str);
//                return str;
//            }
//            // really, not found
//            return null;
//        } else {
//            return str;
//        }
        //@formatter:on
    }

    private List<Integer> sortedKeys() {
        List<Integer> keys = Arrays.asList(stringMap.keySet().toArray(INTEGER_ARRAY_SENTINEL));
        Collections.sort(keys);
        return keys;
    }

    private static final Integer[] INTEGER_ARRAY_SENTINEL = new Integer[0];

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("str\nindex   string\n==========================================================================\n");
        for (Integer idx : sortedKeys()) {
            sb.append(String.format("%5d   %s\n", idx, stringMap.get(idx)));
        }
        return sb.toString();
    }

    public void write(OutputAssembler out) {
        assert totalSize <= Integer.MAX_VALUE;
        byte[] blob = new byte[totalSize];
        int w = 0;
        for (Integer index : sortedKeys()) {
            assert index == w;
            String s = stringMap.get(index);
            byte[] sb = s.getBytes();
            assert sb.length == s.length();
            System.arraycopy(sb, 0, blob, w, sb.length);
            blob[w + sb.length] = 0;
            w += sb.length + 1;
        }
        assert w == blob.length;
        out.writeBlob(blob);
    }

}
