/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;
import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedArrayType.findArrayClass;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class EspressoResolvedInstanceType extends AbstractEspressoResolvedInstanceType {
    private final EspressoConstantPool constantPool;

    @SuppressWarnings("this-escape")
    EspressoResolvedInstanceType() {
        this.constantPool = new EspressoConstantPool(this);
    }

    @Override
    public EspressoConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public native boolean isInitialized();

    @Override
    public native void initialize();

    @Override
    public native boolean isLinked();

    @Override
    public native boolean declaresDefaultMethods();

    @Override
    public native boolean hasDefaultMethods();

    @Override
    public native String getSourceFileName();

    @Override
    public native void link();

    @Override
    public native EspressoResolvedJavaMethod getClassInitializer();

    @Override
    public native int hashCode();

    @Override
    protected native int getVtableLength();

    @Override
    protected native Class<?> getMirror0();

    @Override
    protected native byte[] getRawAnnotationBytes(int category);

    @Override
    protected native int getFlags();

    @Override
    protected native EspressoResolvedInstanceType getSuperclass0();

    @Override
    protected native EspressoResolvedInstanceType[] getInterfaces0();

    @Override
    protected native EspressoResolvedJavaRecordComponent[] getRecordComponents0();

    @Override
    protected native EspressoResolvedInstanceType espressoSingleImplementor();

    @Override
    protected native boolean isLeafClass();

    @Override
    protected native String getName0();

    @Override
    public native boolean isHidden();

    @Override
    public List<JavaType> getPermittedSubclasses() {
        Class<?>[] permittedSubclass = getPermittedSubclasses0(getMirror());
        if (permittedSubclass == null) {
            return null;
        }
        ResolvedJavaType[] permittedSubtypes = new ResolvedJavaType[permittedSubclass.length];
        MetaAccessProvider metaAccess = runtime().getHostJVMCIBackend().getMetaAccess();
        for (int i = 0; i != permittedSubtypes.length; i++) {
            permittedSubtypes[i] = metaAccess.lookupJavaType(permittedSubclass[i]);
        }
        return Collections.unmodifiableList(Arrays.asList(permittedSubtypes));
    }

    @Override
    public native boolean isRecord();

    private static native Class<?>[] getPermittedSubclasses0(Class<?> mirror);

    @Override
    protected boolean hasSameClassLoader(AbstractEspressoResolvedInstanceType otherMirror) {
        return hasSameClassLoader((EspressoResolvedInstanceType) otherMirror);
    }

    private native boolean hasSameClassLoader(EspressoResolvedInstanceType otherMirror);

    @Override
    protected native EspressoResolvedJavaField[] getStaticFields0();

    @Override
    protected native EspressoResolvedJavaField[] getInstanceFields0();

    @Override
    protected native EspressoResolvedJavaMethod[] getDeclaredConstructors0();

    @Override
    protected native EspressoResolvedJavaMethod[] getDeclaredMethods0();

    @Override
    protected native EspressoResolvedJavaMethod[] getAllMethods0();

    @Override
    protected boolean equals0(AbstractEspressoResolvedInstanceType that) {
        if (that instanceof EspressoResolvedInstanceType espressoInstanceType) {
            return equals0(espressoInstanceType);
        }
        return false;
    }

    private native boolean equals0(EspressoResolvedInstanceType that);

    @Override
    public boolean isLocal() {
        return getMirror().isLocalClass();
    }

    @Override
    protected EspressoResolvedInstanceType getJavaLangObject() {
        return runtime().getJavaLangObject();
    }

    @Override
    protected boolean isAssignableFrom(AbstractEspressoResolvedInstanceType other) {
        return getMirror().isAssignableFrom(other.getMirror());
    }

    @Override
    public boolean isMember() {
        return getMirror().isMemberClass();
    }

    @Override
    public ResolvedJavaType[] getDeclaredTypes() {
        Class<?>[] declaredClasses = getMirror().getDeclaredClasses();
        ResolvedJavaType[] declaredTypes = new ResolvedJavaType[declaredClasses.length];
        MetaAccessProvider metaAccess = runtime().getHostJVMCIBackend().getMetaAccess();
        for (int i = 0; i != declaredTypes.length; i++) {
            declaredTypes[i] = metaAccess.lookupJavaType(declaredClasses[i]);
        }
        return declaredTypes;

    }

    @Override
    public ResolvedJavaType getEnclosingType() {
        Class<?> enclosingClass = getMirror().getEnclosingClass();
        if (enclosingClass == null) {
            return null;
        }
        return runtime().getHostJVMCIBackend().getMetaAccess().lookupJavaType(enclosingClass);
    }

    @Override
    public ResolvedJavaMethod getEnclosingMethod() {
        Method enclosingMethod = getMirror().getEnclosingMethod();
        Executable enclosingExecutable = enclosingMethod != null ? enclosingMethod : getMirror().getEnclosingConstructor();
        if (enclosingExecutable != null) {
            return runtime().getHostJVMCIBackend().getMetaAccess().lookupJavaMethod(enclosingExecutable);
        }
        return null;
    }

    @Override
    protected AbstractEspressoResolvedArrayType getArrayClass0() {
        return new EspressoResolvedArrayType(this, 1, this, findArrayClass(getMirror(), 1));
    }

    @Override
    protected AbstractEspressoResolvedInstanceType[] getArrayInterfaces() {
        return runtime().getArrayInterfaces();
    }

    @Override
    protected EspressoResolvedJavaMethod resolveMethod0(AbstractEspressoResolvedJavaMethod method, AbstractEspressoResolvedInstanceType callerType) {
        return runtime().resolveMethod(this, (EspressoResolvedJavaMethod) method, (EspressoResolvedInstanceType) callerType);
    }

    @Override
    protected JavaType lookupType(String name, AbstractEspressoResolvedInstanceType accessingType, boolean resolve) {
        return runtime().lookupType(name, (EspressoResolvedInstanceType) accessingType, resolve);
    }

    @Override
    protected EspressoResolvedObjectType getObjectType(JavaConstant obj) {
        return ((EspressoObjectConstant) obj).getType();
    }
}
