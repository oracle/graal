/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.substitute;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.GraalAccess;

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

public class AnnotatedMethod implements ResolvedJavaMethod, GraphProvider, OriginalMethodProvider {

    private final ResolvedJavaMethod original;
    private final ResolvedJavaMethod annotated;

    public AnnotatedMethod(ResolvedJavaMethod original, ResolvedJavaMethod annotated) {
        this.original = original;
        this.annotated = annotated;
    }

    public ResolvedJavaMethod getOriginal() {
        return original;
    }

    public ResolvedJavaMethod getAnnotated() {
        return annotated;
    }

    @Override
    public String getName() {
        return original.getName();
    }

    @Override
    public Signature getSignature() {
        return original.getSignature();
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        if (original instanceof GraphProvider) {
            return ((GraphProvider) original).buildGraph(debug, method, providers, purpose);
        }
        return null;
    }

    @Override
    public boolean allowRuntimeCompilation() {
        if (original instanceof GraphProvider) {
            return ((GraphProvider) original).allowRuntimeCompilation();
        }
        return true;
    }

    @Override
    public byte[] getCode() {
        return original.getCode();
    }

    @Override
    public int getCodeSize() {
        return original.getCodeSize();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return original.getDeclaringClass();
    }

    @Override
    public int getMaxLocals() {
        return original.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return original.getMaxStackSize();
    }

    @Override
    public int getModifiers() {
        return original.getModifiers();
    }

    @Override
    public boolean isSynthetic() {
        return original.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return original.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return original.isBridge();
    }

    @Override
    public boolean isClassInitializer() {
        return original.isClassInitializer();
    }

    @Override
    public boolean isConstructor() {
        return original.isConstructor();
    }

    @Override
    public boolean canBeStaticallyBound() {
        return original.canBeStaticallyBound();
    }

    @Override
    public ExceptionHandler[] getExceptionHandlers() {
        return original.getExceptionHandlers();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return original.asStackTraceElement(bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        return original.getProfilingInfo(includeNormal, includeOSR);
    }

    @Override
    public ConstantPool getConstantPool() {
        return original.getConstantPool();
    }

    private Annotation[] getAnnotationsImpl(Function<ResolvedJavaMethod, Annotation[]> src) {
        // Collect all but @AnnotateOriginal from annotated
        // Checkstyle: stop
        Map<Object, Annotation> result = Arrays.stream(src.apply(annotated))//
                        .filter(annotation -> !annotation.getClass().equals(AnnotateOriginal.class))//
                        .collect(Collectors.toMap(annotation -> annotation.getClass(), Function.identity()));
        // Checkstyle: resume
        // Add remaining missing ones from original
        for (Annotation annotation : src.apply(original)) {
            result.putIfAbsent(annotation.getClass(), annotation);
        }
        return result.values().toArray(new Annotation[result.size()]);
    }

    @Override
    public Annotation[] getAnnotations() {
        return getAnnotationsImpl(ResolvedJavaMethod::getAnnotations);
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return getAnnotationsImpl(ResolvedJavaMethod::getDeclaredAnnotations);
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        if (annotationClass.equals(AnnotateOriginal.class)) {
            return null;
        }
        // First look for the Annotation in annotated
        T result = annotated.getAnnotation(annotationClass);
        if (result != null) {
            return result;
        }
        // Consider original if not found in annotated
        return original.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        throw VMError.unimplemented();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return original.getGenericParameterTypes();
    }

    @Override
    public boolean canBeInlined() {
        return original.canBeInlined();
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return original.hasNeverInlineDirective();
    }

    @Override
    public boolean shouldBeInlined() {
        return original.shouldBeInlined();
    }

    @Override
    public LineNumberTable getLineNumberTable() {
        return original.getLineNumberTable();
    }

    @Override
    public String toString() {
        return "AnnotatedMethod<definition/implementation " + original.toString() + ", extra annotations " + Arrays.toString(getAnnotations()) + ">";
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return original.getLocalVariableTable();
    }

    @Override
    public void reprofile() {
        original.reprofile();
    }

    @Override
    public Constant getEncoding() {
        return original.getEncoding();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return original.isInVirtualMethodTable(resolved);
    }

    @Override
    public boolean isDefault() {
        return original.isDefault();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        return original.getSpeculationLog();
    }

    @Override
    public Executable getJavaMethod() {
        return OriginalMethodProvider.getJavaMethod(GraalAccess.getOriginalSnippetReflection(), original);
    }

    @Override
    public boolean hasJavaMethod() {
        return OriginalMethodProvider.hasJavaMethod(GraalAccess.getOriginalSnippetReflection(), original);
    }
}
