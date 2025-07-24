/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.cenum;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.reflect.Modifier;

import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.AnnotationValue;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.annotation.SubstrateAnnotationExtractor;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.CInterfaceInvocationPlugin;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Create a synthetic graph to substitute native methods that are annotated with {@link CEnumLookup}
 * or {@link CEnumValue}.
 */
public class CEnumCallWrapperMethod extends CustomSubstitutionMethod {
    private static final AnnotationValue[] INJECTED_ANNOTATIONS = SubstrateAnnotationExtractor.prepareInjectedAnnotations(
                    Uninterruptible.Utils.getAnnotation(ReflectionUtil.lookupMethod(CEnumCallWrapperMethod.class, "uninterruptibleAnnotationHolder")));

    private final NativeLibraries nativeLibraries;

    CEnumCallWrapperMethod(NativeLibraries nativeLibraries, ResolvedJavaMethod method) {
        super(method);
        this.nativeLibraries = nativeLibraries;
    }

    @Override
    public int getModifiers() {
        return original.getModifiers() & ~Modifier.NATIVE;
    }

    @Override
    public AnnotationValue[] getInjectedAnnotations() {
        /* Annotate @CEnumValue methods with @Uninterruptible. */
        if (original.getAnnotation(CEnumValue.class) != null) {
            return INJECTED_ANNOTATIONS;
        }
        return null;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        AnalysisType returnType = method.getSignature().getReturnType();
        ValueNode arg = kit.getInitialArguments().get(0);

        ValueNode returnValue = createInvoke(method, kit, returnType, arg);

        JavaKind pushKind = CInterfaceInvocationPlugin.pushKind(method);
        kit.getFrameState().push(pushKind, returnValue);
        kit.createReturn(returnValue, pushKind);

        return kit.finalizeGraph();
    }

    private ValueNode createInvoke(AnalysisMethod method, HostedGraphKit kit, AnalysisType returnType, ValueNode arg) {
        if (method.getAnnotation(CEnumLookup.class) != null) {
            /* Call a method that converts the primitive value to a Java enum. */
            EnumInfo enumInfo = (EnumInfo) nativeLibraries.findElementInfo(returnType);
            return CInterfaceEnumTool.singleton().createInvokeLookupEnum(kit, returnType, enumInfo, arg, true);
        } else if (method.getAnnotation(CEnumValue.class) != null) {
            /* Call a method that converts a Java enum to a primitive value. */
            ResolvedJavaType declaringType = method.getDeclaringClass();
            EnumInfo enumInfo = (EnumInfo) nativeLibraries.findElementInfo(declaringType);
            return CInterfaceEnumTool.singleton().createInvokeEnumToValue(kit, enumInfo, returnType, arg);
        }

        throw VMError.shouldNotReachHereUnexpectedInput(method); // ExcludeFromJacocoGeneratedReport
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("unused")
    private static void uninterruptibleAnnotationHolder() {
    }
}
