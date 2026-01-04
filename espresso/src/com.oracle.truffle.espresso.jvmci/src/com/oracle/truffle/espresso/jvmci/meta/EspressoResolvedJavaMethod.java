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

import java.lang.reflect.Executable;
import java.lang.reflect.Type;

import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class EspressoResolvedJavaMethod extends AbstractEspressoResolvedJavaMethod {
    private Executable mirrorCache;

    EspressoResolvedJavaMethod(EspressoResolvedInstanceType holder, boolean poisonPill) {
        super(holder, poisonPill);
    }

    @Override
    public native int getMaxLocals();

    @Override
    public native int getMaxStackSize();

    @Override
    public native ExceptionHandler[] getExceptionHandlers();

    @Override
    public native StackTraceElement asStackTraceElement(int bci);

    @Override
    public native boolean hasNeverInlineDirective();

    @Override
    public native LineNumberTable getLineNumberTable();

    @Override
    public native LocalVariableTable getLocalVariableTable();

    @Override
    protected native byte[] getCode0();

    @Override
    protected native int getCodeSize0();

    @Override
    protected native String getName0();

    @Override
    protected AbstractEspressoSignature getSignature0() {
        return new EspressoSignature(getRawSignature());
    }

    private native String getRawSignature();

    @Override
    protected native boolean isForceInline();

    @Override
    protected native int getVtableIndexForInterfaceMethod(EspressoResolvedInstanceType resolved);

    @Override
    protected native int getVtableIndex();

    @Override
    protected native int getFlags();

    @Override
    protected native boolean isLeafMethod();

    @Override
    protected boolean equals0(AbstractEspressoResolvedJavaMethod that) {
        if (that instanceof EspressoResolvedJavaMethod espressoResolvedJavaMethod) {
            return equals0(espressoResolvedJavaMethod);
        }
        return false;
    }

    private native boolean equals0(EspressoResolvedJavaMethod that);

    @Override
    protected native int hashCode0();

    public Executable getMirror() {
        if (mirrorCache == null) {
            mirrorCache = getMirror0();
        }
        return mirrorCache;
    }

    private native Executable getMirror0();

    @Override
    public Type[] getGenericParameterTypes() {
        return getMirror().getGenericParameterTypes();
    }

    @Override
    public Parameter[] getParameters() {
        if (getSignature().getParameterCount(false) == 0) {
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

    @Override
    protected native boolean hasAnnotations();

    @Override
    protected native byte[] getRawAnnotationBytes(int category);
}
