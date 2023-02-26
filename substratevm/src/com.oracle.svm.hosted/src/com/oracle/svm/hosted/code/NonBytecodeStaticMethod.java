/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.AnnotationWrapper;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Abstract base class for methods with generated Graal IR, i.e., methods that do not originate from
 * bytecode.
 */
public abstract class NonBytecodeStaticMethod implements GraphProvider, ResolvedJavaMethod, AnnotationWrapper {

    /**
     * Line numbers are bogus because this is generated code, but we need to include them in our
     * debug information. Otherwise, when setting a breakpoint, GDB will just pick a "nearby" code
     * location that has line number information, which can be in a different function.
     */
    private static final LineNumberTable lineNumberTable = new LineNumberTable(new int[]{1}, new int[]{0});

    private final String name;
    private final ResolvedJavaType declaringClass;
    private final Signature signature;
    private final ConstantPool constantPool;

    private StackTraceElement stackTraceElement;

    public NonBytecodeStaticMethod(String name, ResolvedJavaType declaringClass, Signature signature, ConstantPool constantPool) {
        this.name = name;
        this.declaringClass = declaringClass;
        this.signature = signature;
        this.constantPool = constantPool;
    }

    @Override
    public boolean allowRuntimeCompilation() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    public Signature getSignature() {
        return signature;
    }

    @Override
    public Parameter[] getParameters() {
        throw VMError.unimplemented();
    }

    @Override
    public ConstantPool getConstantPool() {
        return constantPool;
    }

    @Override
    public byte[] getCode() {
        return null;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    public int getMaxLocals() {
        return 2 * getSignature().getParameterCount(true);
    }

    @Override
    public int getMaxStackSize() {
        return 2;
    }

    @Override
    public boolean isSynthetic() {
        return false;
    }

    @Override
    public boolean isVarArgs() {
        return false;
    }

    @Override
    public boolean isBridge() {
        return false;
    }

    @Override
    public boolean isDefault() {
        return false;
    }

    @Override
    public boolean isClassInitializer() {
        return false;
    }

    @Override
    public boolean isConstructor() {
        return false;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return true;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return new ExceptionHandler[0];
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        if (stackTraceElement == null) {
            stackTraceElement = new StackTraceElement(getDeclaringClass().toJavaName(true), getName(), "generated", 0);
        }
        return stackTraceElement;
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw VMError.unimplemented();
    }

    @Override
    public void reprofile() {
        throw VMError.unimplemented();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw VMError.unimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean canBeInlined() {
        return false;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return false;
    }

    @Override
    public boolean shouldBeInlined() {
        return false;
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return lineNumberTable;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return null;
    }

    @Override
    public Constant getEncoding() {
        throw VMError.unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw VMError.unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw VMError.unimplemented();
    }

    @Override
    public AnnotatedElement getAnnotationRoot() {
        return null;
    }

    @Override
    public int getModifiers() {
        return Modifier.PUBLIC | Modifier.STATIC;
    }
}
