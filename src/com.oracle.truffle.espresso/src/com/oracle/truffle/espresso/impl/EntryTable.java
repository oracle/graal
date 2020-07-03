/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.impl;

import java.util.HashMap;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;

public abstract class EntryTable<T extends EntryTable.NamedEntry, K> {
    public abstract Object getLock();

    protected abstract T createEntry(Symbol<Name> name, K data);

    private final HashMap<Symbol<Name>, T> entries = new HashMap<>();

    public abstract static class NamedEntry {
        protected NamedEntry(Symbol<Name> name) {
            this.name = name;
        }

        protected final Symbol<Name> name;

        Symbol<Name> getName() {
            return name;
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof NamedEntry) {
                return ((NamedEntry) obj).getName().equals(this.getName());
            }
            return false;
        }
    }

    /**
     * Lookups the EntryTable for the given name. Returns the corresponding entry if it exists, null
     * otherwise.
     */
    public T lookup(Symbol<Name> name) {
        return entries.get(name);
    }

    /**
     * Lookups the EntryTable for the given name. If an entry is found, returns it. Else, an entry
     * is created and added into the table. This entry is then returned.
     */
    public T lookupOrCreate(Symbol<Name> name, K data) {
        T entry = lookup(name);
        if (entry != null) {
            return entry;
        }
        synchronized (getLock()) {
            entry = lookup(name);
            if (entry != null) {
                return entry;
            }
            return addEntry(name, data);
        }
    }

    /**
     * Created and adds an entry in the table. If an entry already exists, this is a nop, and
     * returns null
     */
    public T createAndAddEntry(Symbol<Name> name, K data) {
        synchronized (getLock()) {
            if (lookup(name) != null) {
                return null;
            }
            return addEntry(name, data);
        }
    }

    private T addEntry(Symbol<Name> name, K data) {
        T entry = createEntry(name, data);
        entries.put(name, entry);
        return entry;
    }

}
