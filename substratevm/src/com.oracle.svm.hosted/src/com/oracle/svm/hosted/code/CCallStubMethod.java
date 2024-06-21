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
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.c.CInterfaceWrapper;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public abstract class CCallStubMethod extends CustomSubstitutionMethod {
    protected final int newThreadStatus;

    CCallStubMethod(ResolvedJavaMethod original, int newThreadStatus) {
        super(original);
        this.newThreadStatus = newThreadStatus;
    }

    protected abstract String getCorrespondingAnnotationName();

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        NativeLibraries nativeLibraries = NativeLibraries.singleton();
        boolean deoptimizationTarget = SubstrateCompilationDirectives.isDeoptTarget(method);
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

    /**
     * The signature may contain Java object types. The only Java object types that we support at
     * the moment are Java enums (annotated with @CEnum). This method replaces all Java enums with
     * suitable primitive types.
     */
    protected ResolvedSignature<AnalysisType> adaptSignatureAndConvertArguments(NativeLibraries nativeLibraries,
                    HostedGraphKit kit, @SuppressWarnings("unused") AnalysisMethod method, AnalysisType returnType, AnalysisType[] parameterTypes, List<ValueNode> arguments) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!CInterfaceEnumTool.isPrimitiveOrWord(parameterTypes[i])) {
                /* Replace the CEnum with the corresponding primitive type. */
                EnumInfo enumInfo = getEnumInfo(nativeLibraries, parameterTypes[i], false);
                ValueNode argumentValue = kit.maybeCreateExplicitNullCheck(arguments.get(i));

                AnalysisType cValueType = CInterfaceEnumTool.getCEnumValueType(enumInfo, kit.getMetaAccess());
                argumentValue = CInterfaceEnumTool.singleton().createInvokeEnumToValue(kit, enumInfo, cValueType, argumentValue);

                arguments.set(i, argumentValue);
                parameterTypes[i] = cValueType;
            }
        }

        AnalysisType patchedReturnType = returnType;
        if (!CInterfaceEnumTool.isPrimitiveOrWord(patchedReturnType)) {
            /*
             * The return type is a @CEnum. Change the return type to Word because the C function
             * call will return some primitive value (and checks expect Word type replacements).
             * adaptReturnValue() below does the actual conversion from the primitive value to the
             * Java object.
             */
            assert getEnumInfo(nativeLibraries, patchedReturnType, true) != null;
            patchedReturnType = (AnalysisType) kit.getWordTypes().getWordImplType();
        }
        return ResolvedSignature.fromArray(parameterTypes, patchedReturnType);
    }

    private ValueNode adaptReturnValue(AnalysisMethod method, NativeLibraries nativeLibraries, HostedGraphKit kit, ValueNode value) {
        AnalysisType declaredReturnType = method.getSignature().getReturnType();
        if (CInterfaceEnumTool.isPrimitiveOrWord(declaredReturnType)) {
            return value;
        }

        /* The C function call returned a primitive value. Now, we convert it to a Java enum. */
        EnumInfo enumInfo = getEnumInfo(nativeLibraries, declaredReturnType, true);
        UserError.guarantee(enumInfo.hasCEnumLookupMethods(),
                        "Enum class %s needs a method that is annotated with @%s because it is used as the return type of a method annotated with @%s: %s.",
                        declaredReturnType, CEnumLookup.class.getSimpleName(), getCorrespondingAnnotationName(), getOriginal());

        return CInterfaceEnumTool.singleton().createInvokeLookupEnum(kit, declaredReturnType, enumInfo, value);
    }

    private EnumInfo getEnumInfo(NativeLibraries nativeLibraries, AnalysisType type, boolean isReturnType) {
        ElementInfo typeInfo = nativeLibraries.findElementInfo(type);
        if (typeInfo instanceof EnumInfo enumInfo) {
            return enumInfo;
        }

        if (isReturnType) {
            throw UserError.abort("Return types of methods annotated with @%s are restricted to primitive types, word types and enumerations (@%s): %s",
                            getCorrespondingAnnotationName(), CEnum.class.getSimpleName(), getOriginal());
        }
        throw UserError.abort("@%s parameter types are restricted to primitive types, word types and enumerations (@%s): %s",
                        getCorrespondingAnnotationName(), CEnum.class.getSimpleName(), getOriginal());
    }
}
