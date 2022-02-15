/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.runtime.Attribute;

public final class MethodParametersAttribute extends Attribute {

    public static final Symbol<Name> NAME = Name.MethodParameters;

    public static final MethodParametersAttribute EMPTY = new MethodParametersAttribute(NAME, Entry.EMPTY_ARRAY);

    public Entry[] getEntries() {
        return entries;
    }

    public static final class Entry {

        public static final Entry[] EMPTY_ARRAY = new Entry[0];

        private final int nameIndex;
        private final int accessFlags;

        public Entry(int nameIndex, int accessFlags) {
            this.nameIndex = nameIndex;
            this.accessFlags = accessFlags;
        }

        public int getNameIndex() {
            return nameIndex;
        }

        public int getAccessFlags() {
            return accessFlags;
        }

        public boolean sameAs(Entry otherEntry) {
            return nameIndex == otherEntry.nameIndex && accessFlags == otherEntry.accessFlags;
        }
    }

    private final Entry[] entries;

    public MethodParametersAttribute(Symbol<Name> name, Entry[] entries) {
        super(name, null);
        this.entries = entries;
    }

    @Override
    public boolean sameAs(Attribute other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        if (!super.sameAs(other)) {
            return false;
        }
        MethodParametersAttribute that = (MethodParametersAttribute) other;
        return entriesSameAs(that.entries);
    }

    private boolean entriesSameAs(Entry[] otherEntries) {
        if (entries.length != otherEntries.length) {
            return false;
        }
        for (int i = 0; i < entries.length; i++) {
            if (!entries[i].sameAs(otherEntries[i])) {
                return false;
            }
        }
        return true;
    }
}
