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
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class ModuleTable extends EntryTable<ModuleTable.ModuleEntry, ClassRegistry> {

    public ModuleTable(ReadWriteLock lock) {
        super(lock);
    }

    @Override
    protected ModuleEntry createEntry(Symbol<Name> name, ClassRegistry registry) {
        return new ModuleEntry(name, registry);
    }

    public ModuleEntry createAndAddEntry(Symbol<Name> name, ClassRegistry registry, boolean isOpen, StaticObject module) {
        ModuleEntry moduleEntry = createAndAddEntry(name, registry);
        if (moduleEntry == null) {
            return null;
        }
        moduleEntry.setModule(module);
        moduleEntry.isOpen = isOpen;
        return moduleEntry;
    }

    public static class ModuleEntry extends EntryTable.NamedEntry implements ModuleRef {

        // TODO: module versions.
        ModuleEntry(Symbol<Name> name, ClassRegistry data) {
            super(name);
            this.registry = data;
        }

        public String jdwpName() {
            if (name == null) {
                // JDWP expects the unnamed module to return empty string
                return "";
            } else {
                return name.toString();
            }
        }

        public Object classLoader() {
            return registry.getClassLoader();
        }

        public static ModuleEntry createUnnamedModuleEntry(StaticObject module, ClassRegistry registry) {
            ModuleEntry result = new ModuleEntry(null, registry);
            result.setCanReadAllUnnamed();
            if (!StaticObject.isNull(module)) {
                result.setModule(module);
            }
            result.isOpen = true;
            return result;
        }

        private final ClassRegistry registry;
        private StaticObject module = StaticObject.NULL;
        private boolean isOpen = false;

        private boolean canReadAllUnnamed = false;

        private ArrayList<ModuleEntry> reads;

        public ClassRegistry registry() {
            return registry;
        }

        public void addReads(ModuleEntry from) {
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

        public boolean canRead(ModuleEntry m, EspressoContext context) {
            if (!isNamed() || m.isJavaBase(context)) {
                return true;
            }
            synchronized (this) {
                if (!hasReads()) {
                    return false;
                } else {
                    return contains(m);
                }
            }
        }

        private boolean contains(ModuleEntry from) {
            return reads.contains(from);
        }

        public void setModule(StaticObject module) {
            this.module = module;
        }

        public StaticObject module() {
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

        public boolean isJavaBase(EspressoContext context) {
            return this == context.getRegistries().getJavaBaseModule();
        }

        public boolean hasReads() {
            return reads != null && !reads.isEmpty();
        }

    }
}
