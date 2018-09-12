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

/**
 * Implementation of {@link Klass} for primitive types.
 */
public final class PrimitiveKlass extends Klass {
    private final JavaKind kind;
    private final EspressoContext context;

    /**
     * Creates Espresso type for a primitive {@link JavaKind}.
     *
     * @param kind the kind to create the type for
     */
    public PrimitiveKlass(EspressoContext context, JavaKind kind) {
        super(String.valueOf(kind.getTypeChar()));
        this.kind = kind;
        this.context = context;
        assert kind.isPrimitive() : kind + " not a primitive type";
    }

    @Override
    public int getModifiers() {
        return Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC;
    }

    @Override
    public ArrayKlass getArrayClass() {
        if (kind == JavaKind.Void) {
            return null;
        }
        return super.getArrayClass();
    }

    public Klass getElementalType() {
        return this;
    }

    @Override
    public Klass getComponentType() {
        return null;
    }

    @Override
    public Klass getSuperclass() {
        return null;
    }

    @Override
    public Klass[] getInterfaces() {
        return Klass.EMPTY_ARRAY;
    }

    @Override
    public Klass findLeastCommonAncestor(Klass otherType) {
        return null;
    }

    @Override
    public boolean hasFinalizer() {
        return false;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public Object getClassLoader() {
        return null; // BCL
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isInitialized() {
        return true;
    }

    public boolean isLinked() {
        return true;
    }

    @Override
    public boolean isInstanceClass() {
        return false;
    }

    @Override
    public boolean isInterface() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(Klass other) {
        assert other != null;
        // TODO(peterssen): Reference equality should be enough per context.
        return other.equals(this);
    }

    @Override
    public Klass getHostClass() {
        return null;
    }

    @Override
    public JavaKind getJavaKind() {
        return kind;
    }

    @Override
    public boolean isJavaLangObject() {
        return false;
    }

    @Override
    public MethodInfo resolveMethod(MethodInfo method, Klass callerType) {
        return null;
    }

    @Override
    public String toString() {
        return "PrimitiveKlass<" + kind + ">";
    }

    @Override
    public FieldInfo[] getInstanceFields(boolean includeSuperclasses) {
        return FieldInfo.EMPTY_ARRAY;
    }

    @Override
    public FieldInfo[] getStaticFields() {
        return FieldInfo.EMPTY_ARRAY;
    }

    /**
     * Performs a fast-path check that this type is resolved in the context of a given accessing
     * class. A negative result does not mean this type is not resolved with respect to
     * {@code accessingClass}. That can only be determined by re-resolving the type.
     */
    public boolean isDefinitelyResolvedWithRespectTo(Klass accessingClass) {
        assert accessingClass != null;
        Klass elementType = getElementalType();
        if (elementType.isPrimitive()) {
            // Primitive type resolution is context free.
            return true;
        }

        throw EspressoError.unimplemented();

// if (elementType.getName().startsWith("Ljava/")) {
// // Classes in a java.* package can only be defined by the
// // boot class loader. This is enforced by ClassLoader.preDefineClass()
// assert mirror().getClassLoader() == null;
// return true;
// }

// ClassLoader thisCl = mirror().getClassLoader();
// ClassLoader accessingClassCl = ((HotSpotResolvedObjectTypeImpl)
// accessingClass).mirror().getClassLoader();
// return thisCl == accessingClassCl;
    }

    @Override
    public void initialize() {
        // nop
    }

    @Override
    public FieldInfo findInstanceFieldWithOffset(long offset, JavaKind expectedType) {
        return null;
    }

    @Override
    public ConstantPool getConstantPool() {
        return null;
    }

    @Override
    public EspressoContext getContext() {
        return context;
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
        return MethodInfo.EMPTY_ARRAY;
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
