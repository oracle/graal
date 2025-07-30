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
import com.oracle.truffle.espresso.classfile.tables.AbstractModuleTable;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class ModuleTable extends AbstractModuleTable<StaticObject, ModuleTable.ModuleEntry> {
    public ModuleTable(ReadWriteLock lock) {
        super(lock);
    }

    @Override
    protected ModuleEntry createEntry(Symbol<Name> name, ModuleData<StaticObject> data) {
        return new ModuleEntry(name, data);
    }

    @SuppressWarnings("try")
    public void addModuleEntriesForCDS(List<ModuleEntry> moduleEntries) {
        try (BlockLock block = write()) {
            for (ModuleEntry moduleEntry : moduleEntries) {
                assert moduleEntry != null;
                assert !entries.containsKey(moduleEntry.getName());
                entries.put(moduleEntry.getName(), moduleEntry);
            }
        }
    }

    public static final class ModuleEntry extends AbstractModuleTable.AbstractModuleEntry<StaticObject> implements ModuleRef {
        // Public for CDS de-serialization.
        public ModuleEntry(Symbol<Name> name, ModuleData<StaticObject> data) {
            super(name, data);
        }

        @SuppressWarnings({"unchecked", "raw"})
        public List<ModuleEntry> getReadsForCDS() {
            if (reads == null) {
                return List.of();
            }
            return (List<ModuleEntry>) (List<?>) Collections.unmodifiableList(this.reads);
        }

        @Override
        public String jdwpName() {
            if (name == null) {
                // JDWP expects the unnamed module to return empty string
                return "";
            } else {
                return name.toString();
            }
        }

        @Override
        public Object classLoader() {
            StaticObject module = module();
            if (module != null) {
                assert StaticObject.notNull(module);
                Meta meta = module.getKlass().getMeta();
                return meta.java_lang_Module_loader.getObject(module);
            } else {
                // this must be the early java.base module
                assert name.equals(Names.java_base);
                return StaticObject.NULL;
            }
        }

        public ClassRegistry registry(Meta meta) {
            StaticObject module = module();
            ClassRegistries registries = meta.getContext().getRegistries();
            if (module != null) {
                assert StaticObject.notNull(module);
                StaticObject loader = meta.java_lang_Module_loader.getObject(module);
                return registries.getClassRegistry(loader);
            } else {
                // this must be the early java.base module
                assert name.equals(Names.java_base);
                return registries.getBootClassRegistry();
            }
        }
    }
}
