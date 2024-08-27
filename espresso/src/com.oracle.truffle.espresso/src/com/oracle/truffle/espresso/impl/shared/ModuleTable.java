/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl.shared;

import java.util.ArrayList;
import java.util.concurrent.locks.ReadWriteLock;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;

public abstract class ModuleTable<M, R, ME extends ModuleTable.ModuleEntry<M, R>> extends EntryTable<ME, R> {
    public ModuleTable(ReadWriteLock lock) {
        super(lock);
    }

    public ME createAndAddEntry(Symbol<Name> name, R registry, boolean isOpen, M module) {
        ME moduleEntry = createAndAddEntry(name, registry);
        if (moduleEntry == null) {
            return null;
        }
        moduleEntry.setModule(module);
        ((ModuleEntry<M, R>) moduleEntry).isOpen = isOpen;
        return moduleEntry;
    }

    public ME createUnnamedModuleEntry(M module, R registry) {
        ME result = createEntry(null, registry);
        result.setCanReadAllUnnamed();
        if (module != null) {
            result.setModule(module);
        }
        ((ModuleEntry<M, R>) result).isOpen = true;
        return result;
    }

    public abstract static class ModuleEntry<M, R> extends EntryTable.NamedEntry {
        // TODO: module versions.
        protected ModuleEntry(Symbol<Name> name, R data) {
            super(name);
            this.registry = data;
        }

        private final R registry;
        private M module;
        private boolean isOpen;

        private boolean canReadAllUnnamed;

        private ArrayList<ModuleEntry<M, R>> reads;

        public R registry() {
            return registry;
        }

        public void addReads(ModuleEntry<M, R> from) {
            if (!isNamed()) {
                return;
            }
            synchronized (this) {
                if (from == null) {
                    setCanReadAllUnnamed();
                    return;
                }
                if (reads == null) {
                    reads = new ArrayList<>();
                }
                if (!contains(from)) {
                    reads.add(from);
                }
            }
        }

        public boolean canRead(ModuleEntry<M, R> m, boolean mIsJavaBase) {
            if (!isNamed() || mIsJavaBase) {
                return true;
            }
            synchronized (this) {
                if (hasReads()) {
                    return contains(m);
                } else {
                    return false;
                }
            }
        }

        private boolean contains(ModuleEntry<M, R> from) {
            return reads.contains(from);
        }

        public void setModule(M module) {
            assert this.module == null;
            this.module = module;
        }

        public M module() {
            return module;
        }

        public void setCanReadAllUnnamed() {
            canReadAllUnnamed = true;
        }

        public boolean canReadAllUnnamed() {
            return canReadAllUnnamed;
        }

        public boolean isOpen() {
            return isOpen;
        }

        public boolean isNamed() {
            return getName() != null;
        }

        public boolean hasReads() {
            return reads != null && !reads.isEmpty();
        }
    }
}
