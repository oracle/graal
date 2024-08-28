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

public abstract class ModuleTable<M, R, ME extends ModuleTable.ModuleEntry<M, R>> extends EntryTable<ME, ModuleTable.ModuleData<M, R>> {
    public ModuleTable(ReadWriteLock lock) {
        super(lock);
    }

    public ME createAndAddEntry(Symbol<Name> name, String version, String location, R registry, boolean isOpen, M module) {
        return createAndAddEntry(name, new ModuleData<>(version, location, registry, module, isOpen));
    }

    public ME createUnnamedModuleEntry(M module, R registry) {
        ME result = createEntry(null, new ModuleData<>(null, null, registry, module, true));
        result.setCanReadAllUnnamed();
        return result;
    }

    public static final class ModuleData<M, R> {
        private final String version;
        private final String location;
        private final R registry;
        private final boolean isOpen;
        private final M module;

        public ModuleData(String version, String location, R registry, M module, boolean isOpen) {
            this.registry = registry;
            this.version = version;
            this.location = location;
            this.isOpen = isOpen;
            this.module = module;
        }
    }

    public abstract static class ModuleEntry<M, R> extends EntryTable.NamedEntry {
        private final R registry;
        private final boolean isOpen;
        private M module;
        private String version;
        private String location;
        private boolean canReadAllUnnamed;
        private ArrayList<ModuleEntry<M, R>> reads;

        protected ModuleEntry(Symbol<Name> name, ModuleData<M, R> data) {
            super(name);
            this.version = data.version;
            this.location = data.location;
            this.isOpen = data.isOpen;
            this.registry = data.registry;
            this.module = data.module;
        }

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

        public String version() {
            return version;
        }

        public String location() {
            return location;
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

        public void setVersionAndLocation(String moduleVersion, String moduleLocation) {
            assert version == null && location == null;
            this.version = moduleVersion;
            this.location = moduleLocation;
        }
    }
}
