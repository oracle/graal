/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Modifier;

import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public final class ArrayKlass extends Klass {

    private final Klass componentType;
    private final Klass elementalType;

    ArrayKlass(Klass componentType) {
        super(componentType.getContext(),
                        null, // TODO(peterssen): Internal, , or / name?
                        componentType.getTypes().arrayOf(componentType.getType()),
                        componentType.getMeta().Object,
                        componentType.getMeta().ARRAY_SUPERINTERFACES);
        this.componentType = componentType;
        this.elementalType = componentType.getElementalType();
    }

    @Override
    public StaticObject getStatics() {
        throw EspressoError.shouldNotReachHere("Arrays do not have static fields");
    }

    @Override
    public final int getFlags() {
        return (getElementalType().getFlags() & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.FINAL | Modifier.ABSTRACT;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return getElementalType().isInitialized();
    }

    @Override
    public void initialize() {
        getElementalType().initialize();
    }

    @Override
    public Klass getHostClass() {
        return null;
    }

    @Override
    public Klass getElementalType() {
        return elementalType;
    }

    @Override
    public Klass getComponentType() {
        return componentType;
    }

    @Override
    public final Method vtableLookup(int vtableIndex) {
        return getSuperKlass().vtableLookup(vtableIndex);
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public boolean isMember() {
        return false;
    }

    @Override
    public Klass getEnclosingType() {
        return null;
    }

    @Override
    public Method[] getDeclaredConstructors() {
        return Method.EMPTY_ARRAY;
    }

    @Override
    public Method[] getDeclaredMethods() {
        return Method.EMPTY_ARRAY;
    }

    @Override
    public Field[] getDeclaredFields() {
        return Field.EMPTY_ARRAY;
    }

    @Override
    public final Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, Klass accessingKlass) {
        methodLookupCount.inc();
        return getSuperKlass().lookupMethod(methodName, signature, accessingKlass);
    }

    @Override
    public final Field lookupFieldTable(int slot) {
        return getSuperKlass().lookupFieldTable(slot);
    }

    @Override
    public final Field lookupStaticFieldTable(int slot) {
        return getSuperKlass().lookupStaticFieldTable(slot);
    }

    @Override
    public final @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return elementalType.getDefiningClassLoader();
    }

    @Override
    public ConstantPool getConstantPool() {
        return getElementalType().getConstantPool();
    }
}
