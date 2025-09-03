/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.ParserSymbols.ParserNames;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public final class MethodParametersAttribute extends Attribute {

    public static final Symbol<Name> NAME = ParserNames.MethodParameters;

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

        public boolean isSame(Entry otherEntry, ConstantPool thisPool, ConstantPool otherPool) {
            return thisPool.isSame(nameIndex, otherEntry.nameIndex, otherPool) && accessFlags == otherEntry.accessFlags;
        }
    }

    private final Entry[] entries;

    public MethodParametersAttribute(Symbol<Name> name, Entry[] entries) {
        assert name == NAME;
        this.entries = entries;
    }

    @Override
    public boolean isSame(Attribute other, ConstantPool thisPool, ConstantPool otherPool) {
        if (!super.isSame(other, thisPool, otherPool)) {
            return false;
        }
        MethodParametersAttribute that = (MethodParametersAttribute) other;
        return entriesSameAs(that.entries, thisPool, otherPool);
    }

    private boolean entriesSameAs(Entry[] otherEntries, ConstantPool thisPool, ConstantPool otherPool) {
        if (entries.length != otherEntries.length) {
            return false;
        }
        for (int i = 0; i < entries.length; i++) {
            if (!entries[i].isSame(otherEntries[i], thisPool, otherPool)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Symbol<Name> getName() {
        return NAME;
    }
}
