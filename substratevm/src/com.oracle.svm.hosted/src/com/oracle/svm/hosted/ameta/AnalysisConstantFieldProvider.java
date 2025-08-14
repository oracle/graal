/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.ameta;

import java.lang.invoke.VarHandle;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.classinitialization.SimulateClassInitializerSupport;
import com.oracle.svm.hosted.jdk.JDKInitializationFeature;
import com.oracle.svm.hosted.jdk.VarHandleFeature;
import com.oracle.svm.hosted.meta.SharedConstantFieldProvider;

import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.internal.vm.annotation.Stable;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

@Platforms(Platform.HOSTED_ONLY.class)
public class AnalysisConstantFieldProvider extends SharedConstantFieldProvider {

    public AnalysisConstantFieldProvider(MetaAccessProvider metaAccess, SVMHost hostVM) {
        super(metaAccess, hostVM);
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField f, ConstantFieldTool<T> analysisTool) {
        AnalysisField field = (AnalysisField) f;

        T foldedValue = super.readConstantField(field, analysisTool);

        if (foldedValue != null) {
            if (!BuildPhaseProvider.isAnalysisFinished()) {
                field.registerAsFolded(nonNullReason(analysisTool.getReason()));
            }
        }
        return foldedValue;
    }

    /**
     * Before analysis, we fold only explicitly registered {@link Stable} fields (see
     * {@link SVMHost#allowStableFieldFoldingBeforeAnalysis}) that should always be initialized by
     * then (contain a non-default value).
     * <p>
     * There are two edge cases:
     * <p>
     * 1) {@code Module#enableNativeAccess}: We optimistically try to fold this field even though we
     * cannot fold it for every Module, because it significantly reduces the size of smaller images
     * like helloworld, as discussed in {@link JDKInitializationFeature#beforeAnalysis}. Folding
     * this particular field only on some accesses does not result in non-determinism, because in
     * those cases where it is set at build time, it happens before analysis.
     * <p>
     * 2) We allow folding default values from virtualized objects. If an object is virtualized by
     * {@link PartialEscapePhase}, its fields cannot be reassigned, so it is safe to fold its
     * fields. This sometimes happens with {@link VarHandle}s. Fields whose
     * {@link ImageHeapConstant#isBackedByHostedObject} returns {@code false}, are the result of
     * class initializer simulation.
     *
     * @see SVMHost#allowConstantFolding(ResolvedJavaField)
     * @see JDKInitializationFeature#beforeAnalysis
     * @see PartialEscapePhase
     * @see VarHandleFeature
     * @see SimulateClassInitializerSupport
     */
    @Override
    protected void onStableFieldRead(ResolvedJavaField field, JavaConstant value, ConstantFieldTool<?> tool) {
        if (value.isDefaultForKind() && !BuildPhaseProvider.isAnalysisFinished() && !field.isFinal() && field.isAnnotationPresent(Stable.class)) {
            if (field.getName().equals("enableNativeAccess") && field.getDeclaringClass().getName().equals("Ljava/lang/Module;")) {
                /* Edge case 1) */
                return;
            }
            if (!field.isStatic() && tool.getReceiver() instanceof ImageHeapConstant heapConstant && !heapConstant.isBackedByHostedObject()) {
                /* Edge case 2) */
                return;
            }
            throw VMError.shouldNotReachHere("Attempting to fold an uninitialized @Stable field before analysis: '%s'. " +
                            "This suggests that the code that is supposed to initialize this field was not executed yet. " +
                            "Please ensure that the initialization code for the field runs before any method that accesses it is parsed.", field.format("%H.%n"));
        }
    }

    @Override
    protected AnalysisField asAnalysisField(ResolvedJavaField field) {
        return (AnalysisField) field;
    }

    private static Object nonNullReason(Object reason) {
        return reason == null ? "Unknown constant fold location." : reason;
    }
}
