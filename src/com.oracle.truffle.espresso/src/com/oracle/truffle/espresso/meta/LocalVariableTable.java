/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.meta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;

/**
 * Describes the {@link Entry}s for a Java method.
 *
 * @see "https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.13"
 */
public final class LocalVariableTable {

    public static final LocalVariableTable EMPTY = new LocalVariableTable(Entry.EMPTY_ARRAY);

    @CompilationFinal(dimensions = 1) //
    private final Entry[] entries;

    /**
     * Creates an object describing the {@link Entry}s for a Java method.
     *
     * @param entries array of objects describing local variables. This array is now owned by this
     *            object and must not be mutated by the caller.
     */
    public LocalVariableTable(Entry[] entries) {
        this.entries = entries;
    }

    /**
     * Gets a description of a local variable that occupies the bytecode frame slot indexed by
     * {@code slot} and is live at the bytecode index {@code bci}.
     *
     * @return a description of the requested local variable or null if no such variable matches
     *         {@code slot} and {@code bci}
     */
    public Entry getLocal(int slot, int bci) {
        Entry result = null;
        for (Entry entry : entries) {
            if (entry.getSlot() == slot && entry.getStartBCI() <= bci && entry.getEndBCI() >= bci) {
                if (result == null) {
                    result = entry;
                } else {
                    throw new IllegalStateException("Locals overlap!");
                }
            }
        }
        return result;
    }

    /**
     * Gets a copy of the array of {@link Entry}s that was passed to this object's constructor.
     */
    public Entry[] getEntries() {
        return entries.clone();
    }

    /**
     * Gets a description of all the local variables live at the bytecode index {@code bci}.
     */
    public Entry[] getLocalsAt(int bci) {
        List<Entry> result = new ArrayList<>();
        for (Entry l : entries) {
            if (l.getStartBCI() <= bci && bci <= l.getEndBCI()) {
                result.add(l);
            }
        }
        return result.toArray(Entry.EMPTY_ARRAY);
    }

    /**
     * Describes the type and bytecode index range in which a local variable is live.
     */
    public static final class Entry {

        public static final Entry[] EMPTY_ARRAY = new Entry[0];

        private final Symbol<Name> name;
        private final Symbol<Type> type;
        private final int startBci;
        private final int endBci;
        private final int slot;

        public Entry(Symbol<Name> name, Symbol<Type> type, int startBci, int endBci, int slot) {
            this.name = name;
            this.startBci = startBci;
            this.endBci = endBci;
            this.slot = slot;
            this.type = type;
        }

        public int getStartBCI() {
            return startBci;
        }

        public int getEndBCI() {
            return endBci;
        }

        public Symbol<Name> getName() {
            return name;
        }

        public Symbol<Type> getType() {
            return type;
        }

        public int getSlot() {
            return slot;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry that = (Entry) obj;
            return this.name.equals(that.name) && this.startBci == that.startBci && this.endBci == that.endBci && this.slot == that.slot && this.type.equals(that.type);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, startBci, endBci, slot);
        }

        @Override
        public String toString() {
            return "LocalVariableTable.Entry<name=" + name + ", type=" + type + ", startBci=" + startBci + ", endBci=" + endBci + ", slot=" + slot + ">";
        }
    }
}
