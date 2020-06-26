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

package com.oracle.truffle.espresso.vm;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.impl.ModuleTable;
import com.oracle.truffle.espresso.impl.PackageTable;
import com.oracle.truffle.espresso.jni.NativeEnv;
import com.oracle.truffle.espresso.jni.Pointer;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;

/**
 * Helper to reduce cluttering of the {@link VM} class
 */
public class ModulesHelperVM {
    static ModuleTable.ModuleEntry getModuleEntry(@Host(typeName = "Ljava/lang/Module") StaticObject module, Meta meta) {
        return (ModuleTable.ModuleEntry) module.getHiddenField(meta.HIDDEN_MODULE_ENTRY);
    }

    private static final PackageTable.PackageEntry getPackageEntry(ModuleTable.ModuleEntry fromModuleEntry, Symbol<Symbol.Name> nameSymbol, Meta meta) {
        return fromModuleEntry.registry().packages().lookup(nameSymbol);
    }

    static final ModuleTable.ModuleEntry extractToModuleEntry(@Host(typeName = "Ljava/lang/Module") StaticObject to_module, Meta meta,
                    SubstitutionProfiler profiler) {
        ModuleTable.ModuleEntry toModuleEntry = null;
        if (!StaticObject.isNull(to_module)) {
            toModuleEntry = getModuleEntry(to_module, meta);
            if (toModuleEntry == null) {
                profiler.profile(8);
                throw Meta.throwException(meta.java_lang_IllegalArgumentException);
            }
        }
        return toModuleEntry;
    }

    static final ModuleTable.ModuleEntry extractFromModuleEntry(@Host(typeName = "Ljava/lang/Module") StaticObject from_module, Meta meta,
                    SubstitutionProfiler profiler) {
        if (StaticObject.isNull(from_module)) {
            profiler.profile(9);
            throw meta.throwNullPointerException();
        }
        ModuleTable.ModuleEntry fromModuleEntry = getModuleEntry(from_module, meta);
        if (fromModuleEntry == null) {
            profiler.profile(10);
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        return fromModuleEntry;
    }

    static final PackageTable.PackageEntry extractPackageEntry(@Pointer TruffleObject pkgName, ModuleTable.ModuleEntry fromModuleEntry, Meta meta, SubstitutionProfiler profiler) {
        String pkg = NativeEnv.interopPointerToString(pkgName);
        Symbol<Symbol.Name> nameSymbol = meta.getContext().getNames().lookup(pkg);
        if (nameSymbol == null) {
            // If symbol is not found, there is absolutely no chance that we will find a match.
            profiler.profile(11);
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        PackageTable.PackageEntry packageEntry = getPackageEntry(fromModuleEntry, nameSymbol, meta);
        if (packageEntry == null || packageEntry.module() != fromModuleEntry) {
            profiler.profile(12);
            throw Meta.throwException(meta.java_lang_IllegalArgumentException);
        }
        return packageEntry;
    }

    static final void addModuleExports(@Host(typeName = "Ljava/lang/Module") StaticObject from_module,
                    @Pointer TruffleObject pkgName,
                    @Host(typeName = "Ljava/lang/Module") StaticObject to_module,
                    Meta meta,
                    InteropLibrary unchached,
                    SubstitutionProfiler profiler) {
        if (unchached.isNull(pkgName)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        ModuleTable.ModuleEntry fromModuleEntry = extractFromModuleEntry(from_module, meta, profiler);
        if (!fromModuleEntry.isNamed() || fromModuleEntry.isOpen()) {
            // All packages in unnamed and open modules are exported by default.
            return;
        }
        ModuleTable.ModuleEntry toModuleEntry = extractToModuleEntry(to_module, meta, profiler);
        PackageTable.PackageEntry packageEntry = extractPackageEntry(pkgName, fromModuleEntry, meta, profiler);
        if (fromModuleEntry != toModuleEntry) {
            packageEntry.addExports(toModuleEntry);
        }
    }
}
