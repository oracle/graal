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
import static com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType.ANNOTATION_DEFAULT_VALUE;
import static com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType.DECLARED_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType.PARAMETER_ANNOTATIONS;
import static com.oracle.truffle.espresso.jvmci.meta.AbstractEspressoResolvedInstanceType.TYPE_ANNOTATIONS;
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

import java.lang.reflect.Modifier;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.SpeculationLog;
import jdk.vm.ci.meta.TriState;
import jdk.vm.ci.meta.annotation.AbstractAnnotated;
import jdk.vm.ci.meta.annotation.AnnotationsInfo;

public abstract class AbstractEspressoResolvedJavaMethod extends AbstractAnnotated implements ResolvedJavaMethod {
    private static final int JVM_METHOD_MODIFIERS = PUBLIC | PRIVATE | PROTECTED | STATIC | FINAL | SYNCHRONIZED | BRIDGE | VARARGS | NATIVE | ABSTRACT | STRICT | SYNTHETIC;
    public static final Parameter[] NO_PARAMETERS = new Parameter[0];

    private final AbstractEspressoResolvedInstanceType holder;
    private final boolean poisonPill;
    private String nameCache;
    private byte[] code;
    private AbstractEspressoSignature signature;

    protected AbstractEspressoResolvedJavaMethod(AbstractEspressoResolvedInstanceType holder, boolean poisonPill) {
        this.holder = holder;
        this.poisonPill = poisonPill;
    }

    @Override
    public final byte[] getCode() {
        if (getCodeSize() == 0) {
            return null;
        }
        if (code == null && holder.isLinked()) {
            code = getCode0();
            assert code.length == getCodeSize() : "expected: " + getCodeSize() + ", actual: " + code.length;
        }
        return code;
    }

    protected abstract byte[] getCode0();

    @Override
    public final int getCodeSize() {
        int codeSize = getCodeSize0();
        if (codeSize > 0 && !getDeclaringClass().isLinked()) {
            return -1;
        }
        return codeSize;
    }

    protected abstract int getCodeSize0();

    @Override
    public final String getName() {
        if (nameCache == null) {
            nameCache = getName0();
        }
        return nameCache;
    }

    protected abstract String getName0();

    @Override
    public final AbstractEspressoResolvedInstanceType getDeclaringClass() {
        return holder;
    }

    @Override
    public final AbstractEspressoSignature getSignature() {
        if (signature == null) {
            signature = getSignature0();
        }
        return signature;
    }

    protected abstract AbstractEspressoSignature getSignature0();

    @Override
    public final boolean isSynthetic() {
        return (getFlags() & SYNTHETIC) != 0;
    }

    @Override
    public final boolean isVarArgs() {
        return (getFlags() & VARARGS) != 0;
    }

    @Override
    public final boolean isBridge() {
        return (getFlags() & BRIDGE) != 0;
    }

    @Override
    public final boolean isDefault() {
        // Copied from java.lang.Method.isDefault()
        int mask = Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC;
        return ((getModifiers() & mask) == Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    @Override
    public final boolean isDeclared() {
        if (isConstructor() || isClassInitializer()) {
            return false;
        }
        return !poisonPill;
    }

    @Override
    public final boolean isClassInitializer() {
        return isStatic() && "<clinit>".equals(getName());
    }

    @Override
    public final boolean isConstructor() {
        return !isStatic() && "<init>".equals(getName());
    }

    @Override
    public final boolean canBeStaticallyBound() {
        return (isFinal() || isPrivate() || isStatic() || holder.isLeaf() || isConstructor()) && isConcrete();
    }

    @Override
    public final ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        // Be optimistic and return false for exceptionSeen?
        return DefaultProfilingInfo.get(TriState.FALSE);
    }

    @Override
    public final void reprofile() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public final AbstractEspressoConstantPool getConstantPool() {
        return holder.getConstantPool();
    }

    @Override
    public final boolean canBeInlined() {
        if (isForceInline()) {
            return true;
        }
        if (hasNeverInlineDirective()) {
            return false;
        }
        return hasBytecodes();
    }

    protected abstract boolean isForceInline();

    @Override
    public final boolean shouldBeInlined() {
        return isForceInline();
    }

    @Override
    public final Constant getEncoding() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public final boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
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

    protected abstract int getVtableIndexForInterfaceMethod(EspressoResolvedInstanceType resolved);

    protected abstract int getVtableIndex();

    @Override
    public final SpeculationLog getSpeculationLog() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public final int getModifiers() {
        return getFlags() & JVM_METHOD_MODIFIERS;
    }

    protected abstract int getFlags();

    protected abstract boolean isLeafMethod();

    @Override
    public final boolean isScoped() {
        return (getFlags() & SCOPED_METHOD) != 0;
    }

    @Override
    public AnnotationsInfo getRawDeclaredAnnotationInfo() {
        if (!hasAnnotations()) {
            return null;
        }
        byte[] bytes = getRawAnnotationBytes(DECLARED_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getConstantPool(), getDeclaringClass());
    }

    @Override
    public AnnotationsInfo getTypeAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(TYPE_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getConstantPool(), getDeclaringClass());
    }

    @Override
    public AnnotationsInfo getAnnotationDefaultInfo() {
        byte[] bytes = getRawAnnotationBytes(ANNOTATION_DEFAULT_VALUE);
        return AnnotationsInfo.make(bytes, getConstantPool(), getDeclaringClass());
    }

    @Override
    public AnnotationsInfo getParameterAnnotationInfo() {
        byte[] bytes = getRawAnnotationBytes(PARAMETER_ANNOTATIONS);
        return AnnotationsInfo.make(bytes, getConstantPool(), getDeclaringClass());
    }

    protected abstract byte[] getRawAnnotationBytes(int category);

    protected abstract boolean hasAnnotations();

    @Override
    public abstract Parameter[] getParameters();

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractEspressoResolvedJavaMethod that = (AbstractEspressoResolvedJavaMethod) o;
        return this.poisonPill == that.poisonPill && equals0(that);
    }

    protected abstract boolean equals0(AbstractEspressoResolvedJavaMethod that);

    @Override
    public final int hashCode() {
        return 13 * Boolean.hashCode(poisonPill) + hashCode0();
    }

    protected abstract int hashCode0();

    @Override
    public final String toString() {
        return format("EspressoResolvedJavaMethod<%h.%n(%p)>");
    }
}
