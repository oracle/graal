/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.impl.ModuleTable.ModuleEntry;
import com.oracle.truffle.espresso.impl.ObjectKlass.KlassVersion;
import com.oracle.truffle.espresso.impl.PackageTable.PackageEntry;
import com.oracle.truffle.espresso.jdwp.api.MethodRef;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

/**
 * Implementation of {@link Klass} for primitive types. Primitive classes don't have a .class
 * representation, so the associated LinkedKlass is null.
 */
public final class PrimitiveKlass extends Klass {
    private final JavaKind primitiveKind;

    /**
     * Creates Espresso type for a primitive {@link JavaKind}.
     *
     * @param primitiveKind the kind to create the type for
     */
    public PrimitiveKlass(EspressoContext context, JavaKind primitiveKind) {
        super(context, primitiveKind.getPrimitiveBinaryName(), primitiveKind.getType(),
                        Modifier.ABSTRACT | Modifier.FINAL | Modifier.PUBLIC);
        assert primitiveKind.isPrimitive() : primitiveKind + " not a primitive kind";
        this.primitiveKind = primitiveKind;
        assert getMeta().java_lang_Class != null;
        initializeEspressoClass();
    }

    public JavaKind getPrimitiveJavaKind() {
        return primitiveKind;
    }

    @Override
    public Klass getElementalType() {
        return this;
    }

    @Override
    public @JavaType(ClassLoader.class) StaticObject getDefiningClassLoader() {
        return StaticObject.NULL; // BCL
    }

    @Override
    public ConstantPool getConstantPool() {
        return null;
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
    public MethodRef[] getDeclaredMethodRefs() {
        return Method.EMPTY_VERSION_ARRAY;
    }

    @Override
    public Method.MethodVersion[] getDeclaredMethodVersions() {
        return Method.EMPTY_VERSION_ARRAY;
    }

    @Override
    public Method lookupMethod(Symbol<Name> methodName, Symbol<Signature> signature, LookupMode lookupMode) {
        return null;
    }

    @Override
    public Field[] getDeclaredFields() {
        return Field.EMPTY_ARRAY;
    }

    @Override
    public ModuleEntry module() {
        return getRegistries().getJavaBaseModule();
    }

    @Override
    public PackageEntry packageEntry() {
        return null;
    }

    @Override
    public Method getClassInitializer() {
        return null;
    }

    @Override
    public String toString() {
        return "PrimitiveKlass<" + getJavaKind() + ">";
    }

    @Override
    public int getClassModifiers() {
        return getModifiers();
    }

    @TruffleBoundary
    public StaticObject allocatePrimitiveArray(int length) {
        GuestAllocator.AllocationChecks.checkCanAllocateArray(getMeta(), length);
        return getAllocator().createNewPrimitiveArray(this, length);
    }

    @Override
    protected Klass[] getSuperTypes() {
        // default implementation for primitive classes
        return new Klass[]{this};
    }

    @Override
    protected int getHierarchyDepth() {
        // default implementation for primitive classes
        return 0;
    }

    @Override
    protected KlassVersion[] getTransitiveInterfacesList() {
        // default implementation for primitive classes
        return ObjectKlass.EMPTY_KLASSVERSION_ARRAY;
    }
}
