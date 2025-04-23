/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.core.BuildPhaseProvider.AfterAnalysis;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.WordBase;

import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class InterpreterResolvedObjectType extends InterpreterResolvedJavaType {

    private final InterpreterResolvedJavaType componentType;
    private final int modifiers;
    private final InterpreterResolvedObjectType superclass;
    private final InterpreterResolvedObjectType[] interfaces;
    private InterpreterResolvedJavaMethod[] declaredMethods;

    // Populated after analysis.
    private InterpreterConstantPool constantPool;

    @Platforms(Platform.HOSTED_ONLY.class) private ResolvedJavaType originalType;

    private final String sourceFileName;

    public static class VTableHolder {
        @UnknownObjectField(availability = AfterAnalysis.class) //
        public InterpreterResolvedObjectType holder;
        @UnknownObjectField(availability = AfterAnalysis.class) //
        public InterpreterResolvedJavaMethod[] vtable;

        public VTableHolder(InterpreterResolvedObjectType holder, InterpreterResolvedJavaMethod[] vtable) {
            this.holder = holder;
            this.vtable = vtable;
        }
    }

    @UnknownObjectField(availability = AfterAnalysis.class) //
    private VTableHolder vtableHolder = null;

    // Debugger side constructor, class is an opaque JavaConstant.
    private InterpreterResolvedObjectType(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass, InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType, String sourceFileName) {
        super(name, clazzConstant, isWordType);
        this.modifiers = modifiers;
        this.componentType = componentType;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.constantPool = constantPool;
        this.sourceFileName = sourceFileName;
    }

    // Interpreter side constructor.
    private InterpreterResolvedObjectType(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass, InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    boolean isWordType) {
        super(name, javaClass, isWordType);
        assert isWordType == WordBase.class.isAssignableFrom(javaClass);
        this.modifiers = modifiers;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.componentType = componentType;
        this.constantPool = constantPool;
        this.sourceFileName = DynamicHub.fromClass(javaClass).getSourceFileName();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterResolvedObjectType(ResolvedJavaType originalType, String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    String sourceFileName) {
        super(name, javaClass);
        this.originalType = originalType;
        this.modifiers = modifiers;
        this.componentType = componentType;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.constantPool = constantPool;
        this.sourceFileName = sourceFileName;
    }

    @Override
    public String getSourceFileName() {
        return sourceFileName;
    }

    // Only used for BuildTimeInterpreterUniverse.
    @Platforms(Platform.HOSTED_ONLY.class)
    public static InterpreterResolvedObjectType createAtBuildTime(ResolvedJavaType originalType, String name, int modifiers, InterpreterResolvedJavaType componentType,
                    InterpreterResolvedObjectType superclass, InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    String sourceFileName) {
        return new InterpreterResolvedObjectType(originalType, name, modifiers, componentType, superclass, interfaces, constantPool, javaClass, sourceFileName);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createForInterpreter(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    boolean isWordType) {
        return new InterpreterResolvedObjectType(name, modifiers, componentType, superclass, interfaces, constantPool, javaClass, isWordType);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createWithOpaqueClass(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType,
                    String sourceFileName) {
        return new InterpreterResolvedObjectType(name, modifiers, componentType, superclass, interfaces, constantPool, clazzConstant, isWordType, sourceFileName);
    }

    public void setConstantPool(InterpreterConstantPool constantPool) {
        VMError.guarantee(this == constantPool.getHolder());
        this.constantPool = MetadataUtil.requireNonNull(constantPool);
    }

    public InterpreterConstantPool getConstantPool() {
        assert !isArray();
        return constantPool;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public ResolvedJavaType getOriginalType() {
        return originalType;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    public InterpreterResolvedJavaType getComponentType() {
        return componentType;
    }

    @Override
    public JavaKind getJavaKind() {
        return JavaKind.Object;
    }

    @Override
    public InterpreterResolvedObjectType getSuperclass() {
        return this.superclass;
    }

    @Override
    public InterpreterResolvedObjectType[] getInterfaces() {
        return this.interfaces;
    }

    @Override
    public boolean isAssignableFrom(ResolvedJavaType other) {
        if (other instanceof InterpreterResolvedObjectType o) {
            return isSubTypeOf(this, o);
        }
        return false;
    }

    private static boolean isSubTypeOf(InterpreterResolvedObjectType superType, InterpreterResolvedObjectType subType) {
        if (subType.equals(superType)) {
            return true;
        }
        if (subType.superclass != null) {
            if (isSubTypeOf(superType, subType.superclass)) {
                return true;
            }
        }
        for (InterpreterResolvedObjectType interf : subType.interfaces) {
            if (isSubTypeOf(superType, interf)) {
                return true;
            }
        }
        return false;
    }

    public InterpreterResolvedJavaMethod[] getVtable() {
        if (vtableHolder == null) {
            return null;
        }
        return vtableHolder.vtable;
    }

    public void setVtable(InterpreterResolvedJavaMethod[] vtable) {
        this.vtableHolder = new VTableHolder(this, vtable);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public VTableHolder getVtableHolder() {
        assert !isArray();
        return vtableHolder;
    }

    @Override
    public ResolvedJavaMethod[] getDeclaredMethods() {
        return declaredMethods;
    }

    public void setDeclaredMethods(InterpreterResolvedJavaMethod[] declaredMethods) {
        this.declaredMethods = declaredMethods;
    }
}
