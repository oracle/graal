/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ParserKlass;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.interpreter.metadata.serialization.VisibleForSerialization;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class InterpreterResolvedObjectType extends InterpreterResolvedJavaType {

    private final InterpreterResolvedJavaType componentType;
    private final int modifiers;
    private final InterpreterResolvedObjectType superclass;
    private final InterpreterResolvedObjectType[] interfaces;
    private InterpreterResolvedJavaMethod[] declaredMethods;
    private InterpreterResolvedJavaField[] declaredFields;
    private int afterFieldsOffset;

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
    private InterpreterResolvedObjectType(Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType, String sourceFileName) {
        super(type, clazzConstant, isWordType);
        this.modifiers = modifiers;
        this.componentType = componentType;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.constantPool = constantPool;
        this.sourceFileName = sourceFileName;
    }

    // Interpreter side constructor.
    private InterpreterResolvedObjectType(Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces,
                    InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    boolean isWordType) {
        super(type, javaClass, isWordType);
        assert isWordType == WordBase.class.isAssignableFrom(javaClass);
        this.modifiers = modifiers;
        this.superclass = superclass;
        this.interfaces = interfaces;
        this.componentType = componentType;
        this.constantPool = constantPool;
        this.sourceFileName = DynamicHub.fromClass(javaClass).getSourceFileName();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private InterpreterResolvedObjectType(ResolvedJavaType originalType, Symbol<Type> type, int modifiers, InterpreterResolvedJavaType componentType,
                    InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass,
                    String sourceFileName) {
        super(type, javaClass);
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
        Symbol<Type> type = CremaTypeAccess.jvmciNameToType(name);
        return new InterpreterResolvedObjectType(originalType, type, modifiers, componentType, superclass, interfaces, constantPool, javaClass, sourceFileName);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createForInterpreter(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    Class<?> javaClass, boolean isWordType) {
        Symbol<Type> type = CremaTypeAccess.jvmciNameToType(name);
        return new InterpreterResolvedObjectType(type, modifiers, componentType, superclass, interfaces, constantPool, javaClass, isWordType);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType create(ParserKlass parserKlass, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, Class<?> javaClass, boolean isWordType) {
        return new InterpreterResolvedObjectType(parserKlass.getType(), modifiers, componentType, superclass, interfaces, null, javaClass, isWordType);
    }

    @VisibleForSerialization
    public static InterpreterResolvedObjectType createWithOpaqueClass(String name, int modifiers, InterpreterResolvedJavaType componentType, InterpreterResolvedObjectType superclass,
                    InterpreterResolvedObjectType[] interfaces, InterpreterConstantPool constantPool,
                    JavaConstant clazzConstant,
                    boolean isWordType,
                    String sourceFileName) {
        Symbol<Type> type = CremaTypeAccess.jvmciNameToType(name);
        return new InterpreterResolvedObjectType(type, modifiers, componentType, superclass, interfaces, constantPool, clazzConstant, isWordType,
                        sourceFileName);
    }

    public void setConstantPool(InterpreterConstantPool constantPool) {
        VMError.guarantee(this == constantPool.getHolder());
        this.constantPool = MetadataUtil.requireNonNull(constantPool);
    }

    @Override
    public InterpreterConstantPool getConstantPool() {
        assert !isArray();
        return constantPool;
    }

    @Override
    public InterpreterResolvedJavaType resolveClassConstantInPool(int cpi) {
        return null;
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

    /**
     * Returns the virtual dispatch table. For interfaces this returns the interface dispatch table
     * prototype.
     */
    public InterpreterResolvedJavaMethod[] getVtable() {
        if (vtableHolder == null) {
            return null;
        }
        return vtableHolder.vtable;
    }

    public void setVtable(InterpreterResolvedJavaMethod[] vtable) {
        this.vtableHolder = new VTableHolder(this, vtable);
    }

    @Override
    public InterpreterResolvedJavaMethod lookupVTableEntry(int vtableIndex) {
        assert getVtable() != null;
        return getVtable()[vtableIndex];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public VTableHolder getVtableHolder() {
        assert !isArray();
        return vtableHolder;
    }

    @Override
    public InterpreterResolvedJavaMethod[] getDeclaredMethods(boolean link) {
        if (link) {
            link();
        }
        return declaredMethods;
    }

    public void setDeclaredMethods(InterpreterResolvedJavaMethod[] declaredMethods) {
        this.declaredMethods = declaredMethods;
    }

    public void setDeclaredFields(InterpreterResolvedJavaField[] declaredFields) {
        this.declaredFields = declaredFields;
    }

    public void setAfterFieldsOffset(int afterFieldsOffset) {
        this.afterFieldsOffset = afterFieldsOffset;
    }

    public int getAfterFieldsOffset() {
        return afterFieldsOffset;
    }

    @Override
    public String getJavaName() {
        throw VMError.unimplemented("getJavaName");
    }

    @Override
    public InterpreterResolvedJavaType findLeastCommonAncestor(InterpreterResolvedJavaType other) {
        throw VMError.unimplemented("getJavaName");
    }

    @Override
    public InterpreterResolvedJavaType getSuperClass() {
        return this.superclass;
    }

    @Override
    public InterpreterResolvedJavaType getHostType() {
        throw VMError.unimplemented("getHostType");
    }

    @Override
    public Symbol<Name> getSymbolicRuntimePackage() {
        ByteSequence hostPkgName = TypeSymbols.getRuntimePackage(getSymbolicType());
        return SymbolsSupport.getNames().getOrCreate(hostPkgName);
    }

    @Override
    public InterpreterResolvedJavaField lookupField(Symbol<Name> name, Symbol<Type> type) {
        for (InterpreterResolvedJavaField field : this.declaredFields) {
            if (name.equals(field.getSymbolicName()) && type.equals(field.getType().getSymbolicType())) {
                return field;
            }
        }
        for (InterpreterResolvedJavaType superInterface : getInterfaces()) {
            InterpreterResolvedJavaField result = superInterface.lookupField(name, type);
            if (result != null) {
                return result;
            }
        }
        if (getSuperclass() != null) {
            return getSuperclass().lookupField(name, type);
        }
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupMethod(Symbol<Name> name, Symbol<Signature> signature) {
        InterpreterResolvedObjectType current = this;
        while (current != null) {
            for (InterpreterResolvedJavaMethod method : declaredMethods) {
                if (name.equals(method.getSymbolicName()) && signature.equals(method.getSymbolicSignature())) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupInstanceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        InterpreterResolvedObjectType current = this;
        while (current != null) {
            for (InterpreterResolvedJavaMethod method : declaredMethods) {
                if (!method.isStatic() && name.equals(method.getSymbolicName()) && signature.equals(method.getSymbolicSignature())) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    @Override
    public InterpreterResolvedJavaMethod lookupInterfaceMethod(Symbol<Name> name, Symbol<Signature> signature) {
        assert isInterface();
        for (InterpreterResolvedJavaMethod method : declaredMethods) {
            if (name.equals(method.getSymbolicName()) && signature.equals(method.getSymbolicSignature())) {
                return method;
            }
        }
        throw VMError.unimplemented("lookupInterfaceMethod");
    }
}
