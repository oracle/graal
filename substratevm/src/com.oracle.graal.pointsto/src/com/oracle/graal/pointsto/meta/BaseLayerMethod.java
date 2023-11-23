/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.meta;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import jdk.graal.compiler.debug.GraalError;
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
 * This type is used in the context of Layered Image, when loading a base layer in another layer.
 * <p>
 * An {@link AnalysisMethod} used in a {@code RelocatableConstant} from the base layer might not be
 * created yet in the new layer. If this method cannot be looked up by name, a
 * {@link BaseLayerMethod} is created and put in an {@link AnalysisMethod} to represent this missing
 * method, using the information from the base layer.
 * <p>
 * At the moment, very little information about the method from the base layer is needed, but in the
 * future, if those methods are used in other places, more information could be persisted from the
 * base layer in the {@link com.oracle.graal.pointsto.heap.ImageLayerWriter} and the
 * {@link com.oracle.graal.pointsto.heap.ImageLayerLoader}
 */
public class BaseLayerMethod implements ResolvedJavaMethod {
    @Override
    public byte[] getCode() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public int getCodeSize() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public String getName() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public Signature getSignature() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public int getMaxLocals() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public int getMaxStackSize() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isSynthetic() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isVarArgs() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isBridge() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isDefault() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isClassInitializer() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isConstructor() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean canBeStaticallyBound() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public void reprofile() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public ConstantPool getConstantPool() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean canBeInlined() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean hasNeverInlineDirective() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean shouldBeInlined() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public Constant getEncoding() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public Annotation[] getAnnotations() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }

    @Override
    public int getModifiers() {
        throw GraalError.unimplemented("This method is incomplete and should not be used.");
    }
}
