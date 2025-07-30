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

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MethodHandleAccessProvider.IntrinsicMethod;
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
 */
public class BaseLayerMethod extends BaseLayerElement implements ResolvedJavaMethod {
    private final int id;
    private final ResolvedJavaType declaringClass;
    private final String name;
    private final boolean isVarArgs;
    private final boolean isBridge;
    private final ResolvedSignature<AnalysisType> signature;
    private final boolean canBeStaticallyBound;
    private final boolean isConstructor;
    private final int modifiers;
    private final boolean isSynthetic;
    private final byte[] code;
    private final int codeSize;
    private final IntrinsicMethod methodHandleIntrinsic;

    public BaseLayerMethod(int id, AnalysisType declaringClass, String name, boolean isVarArgs, boolean isBridge, ResolvedSignature<AnalysisType> signature, boolean canBeStaticallyBound,
                    boolean isConstructor, int modifiers, boolean isSynthetic, byte[] code, int codeSize, IntrinsicMethod methodHandleIntrinsic, Annotation[] annotations) {
        super(annotations);
        this.id = id;
        this.declaringClass = declaringClass.getWrapped();
        this.name = name;
        this.isVarArgs = isVarArgs;
        this.isBridge = isBridge;
        this.signature = signature;
        this.canBeStaticallyBound = canBeStaticallyBound;
        this.isConstructor = isConstructor;
        this.modifiers = modifiers;
        this.isSynthetic = isSynthetic;
        this.code = code;
        this.codeSize = codeSize;
        this.methodHandleIntrinsic = methodHandleIntrinsic;
    }

    public int getBaseLayerId() {
        return id;
    }

    public IntrinsicMethod getMethodHandleIntrinsic() {
        return methodHandleIntrinsic;
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    @Override
    public int getCodeSize() {
        return codeSize;
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
    public int getMaxLocals() {
        throw unimplemented();
    }

    @Override
    public int getMaxStackSize() {
        throw unimplemented();
    }

    @Override
    public boolean isSynthetic() {
        return isSynthetic;
    }

    @Override
    public boolean isVarArgs() {
        return isVarArgs;
    }

    @Override
    public boolean isBridge() {
        return isBridge;
    }

    @Override
    public boolean isDefault() {
        throw unimplemented();
    }

    @Override
    public boolean isDeclared() {
        throw unimplemented();
    }

    @Override
    public boolean isClassInitializer() {
        throw unimplemented();
    }

    @Override
    public boolean isConstructor() {
        return isConstructor;
    }

    @Override
    public boolean canBeStaticallyBound() {
        return canBeStaticallyBound;
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return new ExceptionHandler[0];
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return new StackTraceElement(declaringClass.toClassName(), name, declaringClass.getSourceFileName(), -1);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw unimplemented();
    }

    @Override
    public void reprofile() {
        throw unimplemented();
    }

    @Override
    public ConstantPool getConstantPool() {
        throw unimplemented();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw unimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        throw unimplemented();
    }

    @Override
    public boolean canBeInlined() {
        throw unimplemented();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        throw unimplemented();
    }

    @Override
    public boolean shouldBeInlined() {
        throw unimplemented();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return null;
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return null;
    }

    @Override
    public Constant getEncoding() {
        throw unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        throw unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw unimplemented();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        throw unimplemented();
    }

    @Override
    public Annotation[] getAnnotations() {
        throw unimplemented();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        throw unimplemented();
    }

    private RuntimeException unimplemented() {
        return GraalError.unimplemented("This method is incomplete and should not be used. Base layer method: " + format("%H.%n(%p)"));
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }
}
