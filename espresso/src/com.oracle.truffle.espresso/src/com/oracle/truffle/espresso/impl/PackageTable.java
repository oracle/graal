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
import java.util.concurrent.locks.ReadWriteLock;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;

public class PackageTable extends EntryTable<PackageTable.PackageEntry, ModuleEntry> {
    public PackageTable(ReadWriteLock lock) {
        super(lock);
    }

    @Override
    protected PackageEntry createEntry(Symbol<Name> name, ModuleEntry appendix) {
        return new PackageEntry(name, appendix);
    }

    public static class PackageEntry extends EntryTable.NamedEntry {

        @Override
        public Symbol<Name> getName() {
            return name;
        }

        public PackageEntry(Symbol<Name> name, ModuleEntry module) {
            super(name);
            this.module = module;
        }

        private final ModuleEntry module;
        private ArrayList<ModuleEntry> exports = null;
        private boolean isUnqualifiedExported = false;
        private boolean isExportedAllUnnamed = false;

        public void addExports(ModuleEntry m) {
            if (isUnqualifiedExported()) {
                return;
            }
            synchronized (this) {
                if (m == null) {
                    setUnqualifiedExports();
                }
                if (exports == null) {
                    exports = new ArrayList<>();
                }
                if (!contains(m)) {
                    exports.add(m);
                }
            }
        }

        public boolean isQualifiedExportTo(ModuleEntry m) {
            if (isExportedAllUnnamed() && !m.isNamed()) {
                return true;
            }
            if (isUnqualifiedExported() || exports == null) {
                return false;
            }
            return contains(m);
        }

        public boolean isUnqualifiedExported() {
            return module().isOpen() || isUnqualifiedExported;
        }

        public void setUnqualifiedExports() {
            if (isUnqualifiedExported()) {
                return;
            }
            isUnqualifiedExported = true;
            isExportedAllUnnamed = true;
            exports = null;
        }

        public boolean isExportedAllUnnamed() {
            return module().isOpen() || isExportedAllUnnamed;
        }

        public void setExportedAllUnnamed() {
            if (isExportedAllUnnamed()) {
                return;
            }
            synchronized (this) {
                isExportedAllUnnamed = true;
            }
        }

        public boolean contains(ModuleEntry m) {
            return exports.contains(m);
        }

        public ModuleEntry module() {
            return module;
        }
    }
}
