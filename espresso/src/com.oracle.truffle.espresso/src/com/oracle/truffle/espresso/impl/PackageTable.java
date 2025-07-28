/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.tables.AbstractPackageTable;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class PackageTable extends AbstractPackageTable<StaticObject, PackageTable.PackageEntry, ModuleEntry> {
    public PackageTable(ReadWriteLock lock) {
        super(lock);
    }

    @Override
    protected PackageEntry createEntry(Symbol<Name> name, ModuleEntry data) {
        return new PackageEntry(name, data);
    }

    @SuppressWarnings("try")
    public void addPackageEntriesForCDS(List<PackageEntry> packageEntries) {
        try (BlockLock block = write()) {
            for (PackageEntry packageEntry : packageEntries) {
                assert packageEntry != null;
                assert !entries.containsKey(packageEntry.getName());
                entries.put(packageEntry.getName(), packageEntry);
            }
        }
    }

    public static final class PackageEntry extends AbstractPackageTable.AbstractPackageEntry<StaticObject, ModuleEntry> {
        public PackageEntry(Symbol<Name> name, ModuleEntry module) {
            super(name, module);
        }

        public List<ModuleEntry> getExportsForCDS() {
            if (exports == null) {
                return List.of();
            }
            return Collections.unmodifiableList(exports);
        }
    }
}
