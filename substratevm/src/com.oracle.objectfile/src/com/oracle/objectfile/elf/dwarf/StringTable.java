/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020 Red Hat, Inc. All rights reserved.
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

package com.oracle.objectfile.elf.dwarf;

import java.util.HashMap;
import java.util.Iterator;

// class which reduces incoming strings to unique
// instances and also marks strings which need
// to be written to the debug_str section
public class StringTable implements Iterable<StringEntry> {

    private final HashMap<String, StringEntry> table;

    public StringTable() {
        this.table = new HashMap<>();
    }

    public String uniqueString(String string) {
        return ensureString(string, false);
    }

    public String uniqueDebugString(String string) {
        return ensureString(string, true);
    }

    private String ensureString(String string, boolean addToStrSection) {
        StringEntry stringEntry = table.get(string);
        if (stringEntry == null) {
            stringEntry = new StringEntry(string);
            table.put(string, stringEntry);
        }
        if (addToStrSection && !stringEntry.isAddToStrSection()) {
            stringEntry.setAddToStrSection();
        }
        return stringEntry.getString();
    }

    public int debugStringIndex(String string) {
        StringEntry stringEntry = table.get(string);
        assert stringEntry != null;
        if (stringEntry == null) {
            return -1;
        }
        return stringEntry.getOffset();
    }
    @Override
    public Iterator<StringEntry> iterator() {
        return table.values().iterator();
    }
}
