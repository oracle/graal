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
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoContext;

public final class ArrayKlass extends Klass {
    private final Klass componentType;

    ArrayKlass(Klass componentType) {
        super("[" + componentType.getName());
        this.componentType = componentType;
    }

    @Override
    public ConstantPool getConstantPool() {
        return getComponentType().getConstantPool();
    }

    @Override
    public EspressoContext getContext() {
        return getComponentType().getContext();
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public int getModifiers() {
        return (getElementalType().getModifiers() & (Modifier.PUBLIC | Modifier.PRIVATE | Modifier.PROTECTED)) | Modifier.FINAL | Modifier.ABSTRACT;
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
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isInitialized() {
        return getComponentType().isInitialized();
    }

    @Override
    public void initialize() {
        getComponentType().initialize();
    }

    @Override
    public boolean isLinked() {
        return getComponentType().isLinked();
    }

    @Override
    public boolean isAssignableFrom(Klass other) {
        throw EspressoError.unimplemented();
    }

    @Override
    public Klass getHostClass() {
        return null;
    }

    @Override
    public Klass getSuperclass() {
        return getContext().getMeta().OBJECT.rawKlass();
    }

    @Override
    public Klass[] getInterfaces() {
        Klass cloneable = getContext().getMeta().CLONEABLE.rawKlass();
        Klass serializable = getContext().getMeta().SERIALIZABLE.rawKlass();
        return new Klass[]{cloneable, serializable};
    }

    @Override
    public Klass findLeastCommonAncestor(Klass otherType) {
        throw EspressoError.unimplemented();
    }

    @Override
    public Klass getComponentType() {
        return componentType;
    }

    @Override
    public MethodInfo resolveMethod(MethodInfo method, Klass callerType) {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public Object getClassLoader() {
        return getComponentType().getClassLoader();
    }

    @Override
    public FieldInfo[] getInstanceFields(boolean includeSuperclasses) {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo[] getStaticFields() {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo findInstanceFieldWithOffset(long offset, JavaKind expectedKind) {
        throw EspressoError.unimplemented();
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
    public MethodInfo[] getDeclaredConstructors() {
        throw EspressoError.unimplemented();
    }

    @Override
    public MethodInfo[] getDeclaredMethods() {
        return MethodInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo[] getDeclaredFields() {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public MethodInfo getClassInitializer() {
        return null;
    }

}
