/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile.tables;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;

public abstract class AbstractModuleTable<M, ME extends AbstractModuleTable.AbstractModuleEntry<M>> extends EntryTable<ME, AbstractModuleTable.ModuleData<M>> {
    public AbstractModuleTable(ReadWriteLock lock) {
        super(lock);
    }

    public ME createAndAddEntry(Symbol<Name> name, String version, String location, boolean isOpen, M module) {
        return createAndAddEntry(name, new ModuleData<>(version, location, module, 0, isOpen));
    }

    public ME createUnnamedModuleEntry(M module) {
        ME result = createEntry(null, new ModuleData<>(null, null, module, 0, true));
        result.setCanReadAllUnnamed();
        return result;
    }

    public static final class ModuleData<M> {
        private final String version;
        private final String location;
        private final boolean isOpen;
        private final M module;
        private final int archivedModuleRefId;

        public ModuleData(String version, String location, M module, int archivedModuleRefId, boolean isOpen) {
            this.version = version;
            this.location = location;
            this.isOpen = isOpen;
            this.module = module;
            this.archivedModuleRefId = archivedModuleRefId;
        }
    }

    public abstract static class AbstractModuleEntry<M> extends EntryTable.NamedEntry {
        private final boolean isOpen;
        private M module;

        public int getArchivedModuleRefId() {
            return archivedModuleRefId;
        }

        private int archivedModuleRefId;
        private String version;
        private String location;

        public boolean canReadAllUnnamed() {
            return canReadAllUnnamed;
        }

        private boolean canReadAllUnnamed;

        protected List<AbstractModuleEntry<M>> reads;
        private volatile boolean hasDefaultReads;

        protected AbstractModuleEntry(Symbol<Name> name, ModuleData<M> data) {
            super(name);
            this.version = data.version;
            this.location = data.location;
            this.isOpen = data.isOpen;
            this.module = data.module;
            this.archivedModuleRefId = data.archivedModuleRefId;
        }

        public void addReads(AbstractModuleEntry<M> from) {
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

        public boolean canRead(AbstractModuleEntry<M> m, boolean mIsJavaBase) {
            if (!isNamed() || mIsJavaBase) {
                return true;
            }
            /*
             * Acceptable access to a type in an unnamed module. Note that since unnamed modules can
             * read all unnamed modules, this also handles the case where module_from is also
             * unnamed but in a different class loader.
             */
            if (!m.isNamed() && canReadAllUnnamed) {
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

        private boolean contains(AbstractModuleEntry<M> from) {
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

        public boolean hasDefaultReads() {
            return hasDefaultReads;
        }

        public void setHasDefaultReads() {
            hasDefaultReads = true;
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
