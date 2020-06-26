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

import static com.oracle.truffle.espresso.impl.ModuleTable.moduleLock;

import java.util.ArrayList;

import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;

public class PackageTable extends EntryTable<PackageTable.PackageEntry, ModuleEntry> {
    private static final Object packageLock = moduleLock;

    @Override
    public Object getLock() {
        return packageLock;
    }

    @Override
    public PackageEntry createEntry(Symbol<Name> name, ModuleEntry appendix) {
        return new PackageEntry(name, appendix);
    }

    public static class PackageEntry implements EntryTable.NamedEntry {

        @Override
        public Symbol<Name> getName() {
            return name;
        }

        public PackageEntry(Symbol<Name> name, ModuleEntry module) {
            this.name = name;
            this.module = module;
        }

        private final Symbol<Name> name;
        private final ModuleEntry module;
        private ArrayList<ModuleEntry> exports = null;
        private boolean isUnqualifiedExports = false;
        private boolean isExportedAllUnnamed = false;

        public void addExports(ModuleEntry module) {
            if (isUnqualifiedExports()) {
                return;
            }
            synchronized (packageLock) {
                if (module == null) {
                    setUnqualifiedExports();
                }
                if (exports == null) {
                    exports = new ArrayList<>();
                }
                if (!exports.contains(module)) {
                    exports.add(module);
                }
            }
        }

        public boolean isQualifiedExportTo(ModuleEntry m) {
            if (isExportedAllUnnamed() && !m.isNamed()) {
                return true;
            }
            if (isUnqualifiedExports() || exports == null) {
                return false;
            }
            return exports.contains(m);
        }

        public boolean isUnqualifiedExports() {
            return module().isOpen() || isUnqualifiedExports;
        }

        public void setUnqualifiedExports() {
            if (isUnqualifiedExports()) {
                return;
            }
            isUnqualifiedExports = true;
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
            synchronized (packageLock) {
                isExportedAllUnnamed = true;
            }
        }

        public ModuleEntry module() {
            return module;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PackageEntry)) {
                return false;
            }
            PackageEntry pkg = (PackageEntry) obj;
            return this.name == pkg.name;
        }
    }
}
