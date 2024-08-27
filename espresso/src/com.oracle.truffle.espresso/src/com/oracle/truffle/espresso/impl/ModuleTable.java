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

import java.util.concurrent.locks.ReadWriteLock;

import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.jdwp.api.ModuleRef;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class ModuleTable extends com.oracle.truffle.espresso.impl.shared.ModuleTable<StaticObject, ClassRegistry, ModuleTable.ModuleEntry> {
    public ModuleTable(ReadWriteLock lock) {
        super(lock);
    }

    @Override
    protected ModuleEntry createEntry(Symbol<Name> name, ClassRegistry registry) {
        return new ModuleEntry(name, registry);
    }

    public static final class ModuleEntry extends com.oracle.truffle.espresso.impl.shared.ModuleTable.ModuleEntry<StaticObject, ClassRegistry> implements ModuleRef {
        ModuleEntry(Symbol<Name> name, ClassRegistry registry) {
            super(name, registry);
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
            return registry().getClassLoader();
        }
    }
}
