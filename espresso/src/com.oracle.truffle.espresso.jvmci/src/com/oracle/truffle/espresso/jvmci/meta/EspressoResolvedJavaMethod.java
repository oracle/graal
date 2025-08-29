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
package com.oracle.truffle.espresso.jvmci.meta;

import static com.oracle.truffle.espresso.jvmci.EspressoJVMCIRuntime.runtime;
import static com.oracle.truffle.espresso.jvmci.meta.EspressoResolvedJavaType.NO_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.BRIDGE;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.SCOPED_METHOD;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.SYNTHETIC;
import static com.oracle.truffle.espresso.jvmci.meta.ExtendedModifiers.VARARGS;
import static java.lang.reflect.Modifier.ABSTRACT;
import static java.lang.reflect.Modifier.FINAL;
import static java.lang.reflect.Modifier.NATIVE;
import static java.lang.reflect.Modifier.PRIVATE;
import static java.lang.reflect.Modifier.PROTECTED;
import static java.lang.reflect.Modifier.PUBLIC;
import static java.lang.reflect.Modifier.STATIC;
import static java.lang.reflect.Modifier.STRICT;
import static java.lang.reflect.Modifier.SYNCHRONIZED;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;

public final class EspressoResolvedJavaMethod implements ResolvedJavaMethod {
    private static final int JVM_METHOD_MODIFIERS = PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | SYNCHRONIZED | BRIDGE | VARARGS | NATIVE | ABSTRACT | STRICT | SYNTHETIC;
    public static final Parameter[] NO_PARAMETERS = new Parameter[0];

    private final EspressoResolvedInstanceType holder;
    private final boolean poisonPill;
    private Executable mirrorCache;
    private String nameCache;
    private byte[] code;
    private EspressoSignature signature;

    private EspressoResolvedJavaMethod(EspressoResolvedInstanceType holder, boolean poisonPill) {
        this.holder = holder;
        this.poisonPill = poisonPill;
    }

    @Override
    public byte[] getCode() {
        if (getCodeSize() == 0) {
            return null;
        }
        if (code == null && holder.isLinked()) {
            code = getCode0();
            assert code.length == getCodeSize() : "expected: " + getCodeSize() + ", actual: " + code.length;
        }
        return code;
    }

    private native byte[] getCode0();

    @Override
    public int getCodeSize() {
        int codeSize = getCodeSize0();
        if (codeSize > 0 && !getDeclaringClass().isLinked()) {
            return -1;
        }
        return codeSize;
    }

    private native int getCodeSize0();

    @Override
    public String getName() {
        if (nameCache == null) {
            nameCache = getName0();
        }
        return nameCache;
    }

    private native String getName0();

    @Override
    public EspressoResolvedInstanceType getDeclaringClass() {
        return holder;
    }

    @Override
    public EspressoSignature getSignature() {
        if (signature == null) {
            signature = new EspressoSignature(getRawSignature());
        }
        return signature;
    }

    private native String getRawSignature();

    @Override
    public native int getMaxLocals();

    @Override
    public native int getMaxStackSize();

    @Override
    public boolean isSynthetic() {
        return (getFlags() & SYNTHETIC) != 0;
    }

    @Override
    public boolean isVarArgs() {
        return (getFlags() & VARARGS) != 0;
    }

    @Override
    public boolean isBridge() {
        return (getFlags() & BRIDGE) != 0;
    }

    @Override
    public boolean isDefault() {
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    @Override
    public boolean isDeclared() {
        if (isConstructor() || isClassInitializer()) {
            return false;
        }
        return !poisonPill;
    }

    @Override
    public boolean isClassInitializer() {
        return isStatic() && "<clinit>".equals(getName());
    }

    @Override
    public boolean isConstructor() {
        return !isStatic() && "<init>".equals(getName());
    }

    @Override
    public boolean canBeStaticallyBound() {
        return (isFinal() || isPrivate() || isStatic() || holder.isLeaf() || isConstructor()) && isConcrete();
    }

    @Override
    public native ExceptionHandler[] getExceptionHandlers();

    @Override
    public native StackTraceElement asStackTraceElement(int bci);

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        // Be optimistic and return false for exceptionSeen?
        return DefaultProfilingInfo.get(TriState.FALSE);
    }

    @Override
    public void reprofile() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public EspressoConstantPool getConstantPool() {
        return holder.getConstantPool();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return getMirror().getParameterAnnotations();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return getMirror().getGenericParameterTypes();
    }

    @Override
    public boolean canBeInlined() {
        if (isForceInline()) {
            return true;
        }
        if (hasNeverInlineDirective()) {
            return false;
        }
        return hasBytecodes();
    }

    private native boolean isForceInline();

    @Override
    public native boolean hasNeverInlineDirective();

    @Override
    public boolean shouldBeInlined() {
        return isForceInline();
    }

    @Override
    public native LineNumberTable getLineNumberTable();

    @Override
    public native LocalVariableTable getLocalVariableTable();

    @Override
    public Constant getEncoding() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        EspressoResolvedInstanceType espressoResolved;
        if (resolved instanceof EspressoResolvedInstanceType) {
            espressoResolved = (EspressoResolvedInstanceType) resolved;
        } else if (resolved instanceof EspressoResolvedArrayType) {
            espressoResolved = runtime().getJavaLangObject();
        } else {
            return false;
        }
        int vtableIndex = getVtableIndex(espressoResolved);
        return vtableIndex >= 0 && vtableIndex < espressoResolved.getVtableLength();
    }

    private int getVtableIndex(EspressoResolvedObjectType resolved) {
        if (!holder.isLinked()) {
            return -1;
        }
        if (holder.isInterface()) {
            if (resolved.isInterface() || !resolved.isLinked() || !getDeclaringClass().isAssignableFrom(resolved)) {
                return -1;
            }
            EspressoResolvedInstanceType type;
            if (resolved instanceof EspressoResolvedArrayType) {
                type = runtime().getJavaLangObject();
            } else {
                type = (EspressoResolvedInstanceType) resolved;
            }
            return getVtableIndexForInterfaceMethod(type);
        }
        return getVtableIndex();
    }

    private native int getVtableIndexForInterfaceMethod(EspressoResolvedInstanceType resolved);

    private native int getVtableIndex();

    @Override
    public SpeculationLog getSpeculationLog() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getMirror().getAnnotation(annotationClass);
    }

    private native boolean hasAnnotations();

    @Override
    public Annotation[] getAnnotations() {
        if (!hasAnnotations()) {
            return NO_ANNOTATIONS;
        }
        return getMirror().getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        if (!hasAnnotations()) {
            return NO_ANNOTATIONS;
        }
        return getMirror().getDeclaredAnnotations();
    }

    @Override
    public int getModifiers() {
        return getFlags() & JVM_METHOD_MODIFIERS;
    }

    private native int getFlags();

    @Override
    public Parameter[] getParameters() {
        if (signature.getParameterCount(false) == 0) {
            return NO_PARAMETERS;
        }
        java.lang.reflect.Parameter[] javaParameters = getMirror().getParameters();
        ResolvedJavaMethod.Parameter[] res = new ResolvedJavaMethod.Parameter[javaParameters.length];
        for (int i = 0; i < res.length; i++) {
            java.lang.reflect.Parameter src = javaParameters[i];
            String paramName = src.isNamePresent() ? src.getName() : null;
            res[i] = new ResolvedJavaMethod.Parameter(paramName, src.getModifiers(), this, i);
        }
        return res;
    }

    public Executable getMirror() {
        if (mirrorCache == null) {
            mirrorCache = getMirror0();
        }
        return mirrorCache;
    }

    private native Executable getMirror0();

    public native boolean isLeafMethod();

    @Override
    public boolean isScoped() {
        return (getFlags() & SCOPED_METHOD) != 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EspressoResolvedJavaMethod that = (EspressoResolvedJavaMethod) o;
        return this.poisonPill == that.poisonPill && equals0(that);
    }

    private native boolean equals0(EspressoResolvedJavaMethod that);

    @Override
    public int hashCode() {
        return 13 * Boolean.hashCode(poisonPill) + hashCode0();
    }

    private native int hashCode0();

    @Override
    public String toString() {
        return format("EspressoResolvedJavaMethod<%h.%n(%p)>");
    }
}
