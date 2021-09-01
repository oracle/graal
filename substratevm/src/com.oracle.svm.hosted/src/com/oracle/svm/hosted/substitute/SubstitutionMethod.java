/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;
import static com.oracle.svm.core.util.VMError.unimplemented;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Type;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.StructuredGraph;

import com.oracle.graal.pointsto.infrastructure.GraphProvider;
import com.oracle.graal.pointsto.infrastructure.OriginalMethodProvider;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import jdk.vm.ci.meta.SpeculationLog;

public class SubstitutionMethod implements ResolvedJavaMethod, GraphProvider, OriginalMethodProvider {

    private final ResolvedJavaMethod original;
    private final ResolvedJavaMethod annotated;
    private final LocalVariableTable localVariableTable;
    private final boolean inClassSubstitution;

    /**
     * This field is used in the {@link com.oracle.svm.hosted.SubstitutionReportFeature} class to
     * determine {@link SubstitutionMethod} objects which correspond to annotated substitutions.
     */
    private final boolean isUserSubstitution;

    public SubstitutionMethod(ResolvedJavaMethod original, ResolvedJavaMethod annotated, boolean inClassSubstitution, boolean isUserSubstitution) {
        this.original = original;
        this.annotated = annotated;
        this.inClassSubstitution = inClassSubstitution;
        this.isUserSubstitution = isUserSubstitution;

        LocalVariableTable newLocalVariableTable = null;
        if (annotated.getLocalVariableTable() != null) {
            /*
             * Local variable types must be resolved from the point of view of the annotated class.
             * So do the resolution early, because users of the local variable table only have
             * access to the original.
             */
            Local[] origLocals = annotated.getLocalVariableTable().getLocals();
            Local[] newLocals = new Local[origLocals.length];
            ResolvedJavaType accessingClass = annotated.getDeclaringClass();
            for (int i = 0; i < newLocals.length; i++) {
                Local origLocal = origLocals[i];
                newLocals[i] = new Local(origLocal.getName(), origLocal.getType().resolve(accessingClass), origLocal.getStartBCI(), origLocal.getEndBCI(), origLocal.getSlot());
            }
            newLocalVariableTable = new LocalVariableTable(newLocals);
        }
        localVariableTable = newLocalVariableTable;
    }

    public boolean isUserSubstitution() {
        return isUserSubstitution;
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
        if (annotated instanceof GraphProvider) {
            return ((GraphProvider) annotated).buildGraph(debug, method, providers, purpose);
        }
        return null;
    }

    @Override
    public boolean allowRuntimeCompilation() {
        if (annotated instanceof GraphProvider) {
            return ((GraphProvider) annotated).allowRuntimeCompilation();
        }
        return true;
    }

    @Override
    public byte[] getCode() {
        return annotated.getCode();
    }

    @Override
    public int getCodeSize() {
        return annotated.getCodeSize();
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return (inClassSubstitution ? annotated.getDeclaringClass() : original.getDeclaringClass());
    }

    @Override
    public int getMaxLocals() {
        return annotated.getMaxLocals();
    }

    @Override
    public int getMaxStackSize() {
        return annotated.getMaxStackSize();
    }

    @Override
    public int getModifiers() {
        return annotated.getModifiers();
    }

    @Override
    public boolean isSynthetic() {
        return annotated.isSynthetic();
    }

    @Override
    public boolean isVarArgs() {
        return annotated.isVarArgs();
    }

    @Override
    public boolean isBridge() {
        return annotated.isBridge();
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
        return annotated.getExceptionHandlers();
    }

    @Override
    public StackTraceElement asStackTraceElement(int bci) {
        return annotated.asStackTraceElement(bci);
    }

    @Override
    public ProfilingInfo getProfilingInfo(boolean includeNormal, boolean includeOSR) {
        throw unimplemented();
    }

    @Override
    public ConstantPool getConstantPool() {
        return annotated.getConstantPool();
    }

    @Override
    public Annotation[] getAnnotations() {
        return annotated.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return annotated.getDeclaredAnnotations();
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return annotated.getAnnotation(annotationClass);
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return annotated.getParameterAnnotations();
    }

    @Override
    public Parameter[] getParameters() {
        return original.getParameters();
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return original.getGenericParameterTypes();
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
        return annotated.getLineNumberTable();
    }

    @Override
    public String toString() {
        return "SubstitutionMethod<definition " + original.toString() + ", implementation " + annotated.toString() + ">";
    }

    @Override
    public LocalVariableTable getLocalVariableTable() {
        return localVariableTable;
    }

    @Override
    public void reprofile() {
        throw unimplemented();
    }

    @Override
    public Constant getEncoding() {
        throw unimplemented();
    }

    @Override
    public boolean isInVirtualMethodTable(ResolvedJavaType resolved) {
        return false;
    }

    @Override
    public boolean isDefault() {
        throw unimplemented();
    }

    @Override
    public SpeculationLog getSpeculationLog() {
        throw shouldNotReachHere();
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
