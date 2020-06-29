/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnwindNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.c.NativeLibraries;
import com.oracle.svm.hosted.c.info.ElementInfo;
import com.oracle.svm.hosted.c.info.EnumInfo;
import com.oracle.svm.hosted.c.info.EnumLookupInfo;
import com.oracle.svm.hosted.phases.CInterfaceEnumTool;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public abstract class CCallStubMethod extends CustomSubstitutionMethod {
    private static final JavaKind cEnumKind = JavaKind.Int;

    protected final int newThreadStatus;

    CCallStubMethod(ResolvedJavaMethod original, int newThreadStatus) {
        super(original);
        this.newThreadStatus = newThreadStatus;
    }

    protected abstract String getCorrespondingAnnotationName();

    @Override
    public StructuredGraph buildGraph(DebugContext debug, ResolvedJavaMethod method, HostedProviders providers, Purpose purpose) {
        NativeLibraries nativeLibraries = CEntryPointCallStubSupport.singleton().getNativeLibraries();
        boolean deoptimizationTarget = method instanceof SharedMethod && ((SharedMethod) method).isDeoptTarget();
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        FrameStateBuilder state = kit.getFrameState();
        List<ValueNode> arguments = kit.loadArguments(getParameterTypesForLoad(method));
        ValueNode callAddress = createTargetAddressNode(kit, providers, arguments);
        Signature signature = adaptSignatureAndConvertArguments(providers, nativeLibraries, kit, method,
                        method.getSignature().getReturnType(null), method.toParameterTypes(), arguments);
        state.clearLocals();
        ValueNode returnValue = kit.createCFunctionCall(callAddress, arguments, signature, newThreadStatus, deoptimizationTarget);
        returnValue = adaptReturnValue(method, providers, nativeLibraries, kit, returnValue);
        kit.createReturn(returnValue, signature.getReturnKind());

        return kit.finalizeGraph();
    }

    protected abstract ValueNode createTargetAddressNode(HostedGraphKit kit, HostedProviders providers, List<ValueNode> arguments);

    protected static boolean isPrimitiveOrWord(HostedProviders providers, JavaType type) {
        return type.getJavaKind().isPrimitive() || providers.getWordTypes().isWord(type);
    }

    protected JavaType[] getParameterTypesForLoad(ResolvedJavaMethod method) {
        return method.getSignature().toParameterTypes(/* exclude receiver parameter */ null);
    }

    protected Signature adaptSignatureAndConvertArguments(HostedProviders providers, NativeLibraries nativeLibraries,
                    HostedGraphKit kit, @SuppressWarnings("unused") ResolvedJavaMethod method, JavaType returnType, JavaType[] parameterTypes, List<ValueNode> arguments) {

        MetaAccessProvider metaAccess = providers.getMetaAccess();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isPrimitiveOrWord(providers, parameterTypes[i])) {
                ElementInfo typeInfo = nativeLibraries.findElementInfo((ResolvedJavaType) parameterTypes[i]);
                if (typeInfo instanceof EnumInfo) {
                    ValueNode argumentValue = arguments.get(i);

                    IsNullNode isNull = kit.unique(new IsNullNode(argumentValue));
                    kit.startIf(isNull, BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY);
                    kit.thenPart();
                    ResolvedJavaType enumExceptionType = metaAccess.lookupJavaType(RuntimeException.class);
                    NewInstanceNode enumException = kit.append(new NewInstanceNode(enumExceptionType, true));
                    Iterator<ResolvedJavaMethod> enumExceptionCtor = Arrays.stream(enumExceptionType.getDeclaredConstructors()).filter(
                                    c -> c.getSignature().getParameterCount(false) == 1 && c.getSignature().getParameterType(0, null).equals(metaAccess.lookupJavaType(String.class))).iterator();
                    ConstantNode enumExceptionMessage = kit.createConstant(kit.getConstantReflection().forString("null return value cannot be converted to a C enum value"), JavaKind.Object);
                    kit.createJavaCallWithExceptionAndUnwind(InvokeKind.Special, enumExceptionCtor.next(), enumException, enumExceptionMessage);
                    assert !enumExceptionCtor.hasNext();
                    kit.append(new UnwindNode(enumException));
                    kit.endIf();

                    CInterfaceEnumTool tool = new CInterfaceEnumTool(metaAccess, providers.getSnippetReflection());
                    argumentValue = tool.createEnumValueInvoke(kit, (EnumInfo) typeInfo, cEnumKind, argumentValue);

                    arguments.set(i, argumentValue);
                    parameterTypes[i] = metaAccess.lookupJavaType(cEnumKind.toJavaClass());
                } else {
                    throw UserError.abort("@" + getCorrespondingAnnotationName() + " parameter types are restricted to primitive types, word types and enumerations (@" +
                                    CEnum.class.getSimpleName() + "): " + getOriginal().format("%H.%n(%p)"));
                }
            }
        }
        /* Actual checks and conversion are in adaptReturnValue() */
        JavaType actualReturnType = isPrimitiveOrWord(providers, returnType) ? returnType : providers.getWordTypes().getWordImplType();
        return new SimpleSignature(parameterTypes, actualReturnType);
    }

    private ValueNode adaptReturnValue(ResolvedJavaMethod method, HostedProviders providers, NativeLibraries nativeLibraries, HostedGraphKit kit, ValueNode invokeValue) {
        ValueNode returnValue = invokeValue;
        JavaType declaredReturnType = method.getSignature().getReturnType(null);
        if (isPrimitiveOrWord(providers, declaredReturnType)) {
            return returnValue;
        }
        ElementInfo typeInfo = nativeLibraries.findElementInfo((ResolvedJavaType) declaredReturnType);
        if (typeInfo instanceof EnumInfo) {
            UserError.guarantee(typeInfo.getChildren().stream().anyMatch(EnumLookupInfo.class::isInstance),
                            "Enum class %s needs a method that is annotated with @%s because it is used as the return type of a method annotated with @%s: %s",
                            declaredReturnType,
                            CEnumLookup.class.getSimpleName(),
                            getCorrespondingAnnotationName(),
                            getOriginal());

            // We take a word return type because checks expect word type replacements, but it is
            // narrowed to cEnumKind here.
            CInterfaceEnumTool tool = new CInterfaceEnumTool(providers.getMetaAccess(), providers.getSnippetReflection());
            returnValue = tool.createEnumLookupInvoke(kit, (ResolvedJavaType) declaredReturnType, (EnumInfo) typeInfo, cEnumKind, returnValue);
        } else {
            throw UserError.abort("Return types of methods annotated with @" + getCorrespondingAnnotationName() +
                            " are restricted to primitive types, word types and enumerations (@" +
                            CEnum.class.getSimpleName() + "): " + getOriginal().format("%H.%n(%p)"));
        }
        return returnValue;
    }
}
