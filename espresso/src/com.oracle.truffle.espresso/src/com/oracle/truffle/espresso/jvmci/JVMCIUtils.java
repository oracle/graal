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
package com.oracle.truffle.espresso.jvmci;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.Resolvable;
import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.truffle.espresso.constantpool.RuntimeConstantPool;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public final class JVMCIUtils {
    public static final TruffleLogger LOGGER = TruffleLogger.getLogger(EspressoLanguage.ID, "JVMCI");

    private JVMCIUtils() {
    }

    @TruffleBoundary
    public static ObjectKlass findInstanceType(Symbol<Type> symbol, ObjectKlass accessingKlass, boolean resolve, Meta meta) {
        assert !TypeSymbols.isArray(symbol);
        StaticObject loader = accessingKlass.getDefiningClassLoader();
        if (resolve) {
            return (ObjectKlass) meta.loadKlassOrFail(symbol, loader, accessingKlass.protectionDomain());
        } else {
            return (ObjectKlass) meta.getRegistries().findLoadedClass(symbol, loader);
        }
    }

    @TruffleBoundary
    public static Klass findType(Symbol<Type> symbol, ObjectKlass accessingKlass, boolean resolve, Meta meta) {
        if (TypeSymbols.isPrimitive(symbol)) {
            return meta.resolvePrimitive(symbol);
        } else {
            return findObjectType(symbol, accessingKlass, resolve, meta);
        }
    }

    @TruffleBoundary
    public static Klass findObjectType(Symbol<Type> symbol, ObjectKlass accessingKlass, boolean resolve, Meta meta) {
        if (TypeSymbols.isArray(symbol)) {
            Klass elemental = findType(meta.getTypes().getElementalType(symbol), accessingKlass, resolve, meta);
            if (elemental == null) {
                return null;
            }
            return elemental.getArrayClass(TypeSymbols.getArrayDimensions(symbol));
        } else {
            return findInstanceType(symbol, accessingKlass, resolve, meta);
        }
    }

    public static Klass findObjectType(ClassConstant classConstant, RuntimeConstantPool pool, boolean resolve, Meta meta) {
        if (classConstant instanceof Resolvable.ResolvedConstant resolved) {
            return (Klass) resolved.value();
        }
        Symbol<Name> name = ((ClassConstant.ImmutableClassConstant) classConstant).getName(pool);
        Symbol<Type> type;
        if (resolve) {
            type = meta.getTypes().fromClassNameEntry(name);
        } else {
            type = meta.getTypes().lookupValidType(TypeSymbols.nameToType(name));
        }
        if (type == null || TypeSymbols.isPrimitive(type)) {
            return null;
        }
        return findObjectType(type, pool.getHolder(), resolve, meta);
    }
}
