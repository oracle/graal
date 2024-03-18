/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.c.CInterfaceWrapper;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.EnumLookupInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class CCallStubMethod extends CustomSubstitutionMethod {
    private static final JavaKind cEnumKind = JavaKind.Int;

    protected final int newThreadStatus;

    CCallStubMethod(ResolvedJavaMethod original, int newThreadStatus) {
        super(original);
        this.newThreadStatus = newThreadStatus;
    }

    protected abstract String getCorrespondingAnnotationName();

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        NativeLibraries nativeLibraries = CEntryPointCallStubSupport.singleton().getNativeLibraries();
        boolean deoptimizationTarget = MultiMethod.isDeoptTarget(method);
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        FrameStateBuilder state = kit.getFrameState();
        List<ValueNode> arguments = new ArrayList<>(kit.getInitialArguments());
        ValueNode callAddress = createTargetAddressNode(kit, arguments);
        AnalysisType[] paramTypes = method.toParameterList().toArray(AnalysisType[]::new);
        var signature = adaptSignatureAndConvertArguments(nativeLibraries, kit, method, method.getSignature().getReturnType(), paramTypes, arguments);
        state.clearLocals();

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCFunctionCallPrologue(kit, method);
        }

        ValueNode returnValue = kit.createCFunctionCall(callAddress, arguments, signature, newThreadStatus, deoptimizationTarget);

        if (ImageSingletons.contains(CInterfaceWrapper.class)) {
            ImageSingletons.lookup(CInterfaceWrapper.class).tagCFunctionCallEpilogue(kit, method);
        }

        returnValue = adaptReturnValue(method, nativeLibraries, kit, returnValue);
        kit.createReturn(returnValue, signature.getReturnKind());

        return kit.finalizeGraph();
    }

    protected abstract ValueNode createTargetAddressNode(HostedGraphKit kit, List<ValueNode> arguments);

    protected static boolean isPrimitiveOrWord(HostedGraphKit kit, JavaType type) {
        return type.getJavaKind().isPrimitive() || kit.getWordTypes().isWord(type);
    }

    protected ResolvedSignature<AnalysisType> adaptSignatureAndConvertArguments(NativeLibraries nativeLibraries,
                    HostedGraphKit kit, @SuppressWarnings("unused") AnalysisMethod method, AnalysisType returnType, AnalysisType[] parameterTypes, List<ValueNode> arguments) {

        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isPrimitiveOrWord(kit, parameterTypes[i])) {
                ElementInfo typeInfo = nativeLibraries.findElementInfo(parameterTypes[i]);
                if (typeInfo instanceof EnumInfo) {
                    ValueNode argumentValue = kit.maybeCreateExplicitNullCheck(arguments.get(i));
                    CInterfaceEnumTool tool = new CInterfaceEnumTool(kit.getMetaAccess());
                    argumentValue = tool.createEnumValueInvoke(kit, (EnumInfo) typeInfo, cEnumKind, argumentValue);

                    arguments.set(i, argumentValue);
                    parameterTypes[i] = kit.getMetaAccess().lookupJavaType(cEnumKind.toJavaClass());
                } else {
                    throw UserError.abort("@%s parameter types are restricted to primitive types, word types and enumerations (@%s): %s",
                                    getCorrespondingAnnotationName(), CEnum.class.getSimpleName(), getOriginal());
                }
            }
        }
        /* Actual checks and conversion are in adaptReturnValue() */
        AnalysisType actualReturnType = isPrimitiveOrWord(kit, returnType) ? returnType : (AnalysisType) kit.getWordTypes().getWordImplType();
        return ResolvedSignature.fromArray(parameterTypes, actualReturnType);
    }

    private ValueNode adaptReturnValue(AnalysisMethod method, NativeLibraries nativeLibraries, HostedGraphKit kit, ValueNode invokeValue) {
        ValueNode returnValue = invokeValue;
        AnalysisType declaredReturnType = method.getSignature().getReturnType();
        if (isPrimitiveOrWord(kit, declaredReturnType)) {
            return returnValue;
        }
        ElementInfo typeInfo = nativeLibraries.findElementInfo(declaredReturnType);
        if (typeInfo instanceof EnumInfo) {
            UserError.guarantee(typeInfo.getChildren().stream().anyMatch(EnumLookupInfo.class::isInstance),
                            "Enum class %s needs a method that is annotated with @%s because it is used as the return type of a method annotated with @%s: %s",
                            declaredReturnType,
                            CEnumLookup.class.getSimpleName(),
                            getCorrespondingAnnotationName(),
                            getOriginal());

            // We take a word return type because checks expect word type replacements, but it is
            // narrowed to cEnumKind here.
            CInterfaceEnumTool tool = new CInterfaceEnumTool(kit.getMetaAccess());
            returnValue = tool.createEnumLookupInvoke(kit, declaredReturnType, (EnumInfo) typeInfo, cEnumKind, returnValue);
        } else {
            throw UserError.abort("Return types of methods annotated with @%s are restricted to primitive types, word types and enumerations (@%s): %s",
                            getCorrespondingAnnotationName(), CEnum.class.getSimpleName(), getOriginal());
        }
        return returnValue;
    }
}
