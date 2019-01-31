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

import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Host;

public final class ArrayKlass extends Klass {

    private final Klass componentType;
    private final Klass elementalType;

    ArrayKlass(Klass componentType) {
        super(componentType.getContext(),
                        componentType.getContext().getTypes().arrayOf(componentType.getType()),
                        componentType.getMeta().OBJECT,
                        componentType.getMeta().ARRAY_SUPERINTERFACES);
        this.componentType = componentType;
        this.elementalType = componentType.getElementalType();
    }

// @Override
// public ConstantPool getConstantPool() {
// return getElementalType().getConstantPool();
// }

    @Override
    public StaticObject tryInitializeAndGetStatics() {
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
        return !isArray() && !isInterface();
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
//
// @Override
// public Method resolveMethod(Method method, Klass callerType) {
// return null;
// }

    @Override
    public Field[] getInstanceFields(boolean includeSuperclasses) {
        return new Field[0];
    }

    @Override
    public Field[] getStaticFields() {
        return new Field[0];
    }

//
// @Override
// public ObjectKlass[] getInterfaces() {
// Klass cloneable = getMeta().CLONEABLE;
// Klass serializable = getMeta().SERIALIZABLE;
// return new ObjectKlass[]{cloneable, serializable};
// }

// @Override
// public Klass findLeastCommonAncestor(Klass otherType) {
// throw EspressoError.unimplemented();
// }

    @Override
    public Klass getComponentType() {
        return componentType;
    }

    @Override
    public StaticObject getClassLoader() {
        return getElementalType().getClassLoader();
    }

// @Override
// public Field[] getInstanceFields(boolean includeSuperclasses) {
// return FieldInfo.EMPTY_ARRAY;
// }
//
// @Override
// public Field[] getStaticFields() {
// return FieldInfo.EMPTY_ARRAY;
// }

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
        return new Method[0];
    }

    @Override
    public Method[] getDeclaredMethods() {
        return new Method[0];
    }

    @Override
    public Field[] getDeclaredFields() {
        return new Field[0];
    }

    @Override
    public final @Host(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return elementalType.getDefiningClassLoader();
    }

    // @Override
// public Method[] getDeclaredConstructors() {
// return Method.EMPTY_ARRAY;
// }
//
// @Override
// public Method[] getDeclaredMethods() {
// return Method.EMPTY_ARRAY;
// }
//
// @Override
// public Field[] getDeclaredFields() {
// return Field.EMPTY_ARRAY;
// }
}
