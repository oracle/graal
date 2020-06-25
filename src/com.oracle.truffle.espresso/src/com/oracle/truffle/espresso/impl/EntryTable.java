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

import java.util.ArrayList;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;

public abstract class EntryTable<T extends EntryTable.NamedEntry, K> {
    public abstract Object getLock();

    public abstract T createEntry(Symbol<Name> name, K appendix);

    private ArrayList<T> entries = new ArrayList<>();

    public interface NamedEntry {
        Symbol<Name> getName();
    }

    public T lookup(Symbol<Name> name) {
        for (T entry : entries) {
            if (entry.getName().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    public T lookupOrCreate(Symbol<Name> name, K appendix) {
        T pkg = lookup(name);
        if (pkg != null) {
            return pkg;
        }
        synchronized (getLock()) {
            pkg = lookup(name);
            if (pkg != null) {
                return pkg;
            }
            return addEntry(name, appendix);
        }
    }

    public T createAndAddEntry(Symbol<Name> pkg, K appendix) {
        synchronized (getLock()) {
            if (lookup(pkg) != null) {
                return null;
            }
            return addEntry(pkg, appendix);
        }
    }

    public T addEntry(Symbol<Name> pkg, K appendix) {
        T entry = createEntry(pkg, appendix);
        entries.add(entry);
        return entry;
    }

}
