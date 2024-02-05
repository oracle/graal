/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.c.enums.EnumRuntimeData;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.info.EnumInfo;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.vm.ci.meta.JavaKind;

public class CInterfaceEnumTool {
    interface CallTargetFactory {
        MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, AnalysisMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci);

        static CallTargetFactory from(BytecodeParser p) {
            return (invokeKind, targetMethod, args, returnStamp, bci) -> p.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, null);
        }

        static CallTargetFactory from(HostedGraphKit kit) {
            return kit::createMethodCallTarget;
        }
    }

    private final AnalysisMethod convertJavaToCLongMethod;
    private final AnalysisMethod convertJavaToCIntMethod;
    private final AnalysisMethod convertCToJavaMethod;

    public CInterfaceEnumTool(AnalysisMetaAccess metaAccess) {
        try {
            convertJavaToCLongMethod = metaAccess.lookupJavaMethod(EnumRuntimeData.class.getDeclaredMethod("convertJavaToCLong", Enum.class));
            convertJavaToCIntMethod = metaAccess.lookupJavaMethod(EnumRuntimeData.class.getDeclaredMethod("convertJavaToCInt", Enum.class));
            convertCToJavaMethod = metaAccess.lookupJavaMethod(EnumRuntimeData.class.getDeclaredMethod("convertCToJava", long.class));
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private AnalysisMethod getValueMethodForKind(JavaKind kind) {
        return (kind == JavaKind.Long) ? convertJavaToCLongMethod : convertJavaToCIntMethod;
    }

    public ValueNode createEnumValueInvoke(HostedGraphKit kit, EnumInfo enumInfo, JavaKind resultKind, ValueNode arg) {
        int invokeBci = kit.bci();
        MethodCallTargetNode callTarget = invokeEnumValue(kit, CallTargetFactory.from(kit), invokeBci, enumInfo, resultKind, arg);
        return kit.createInvokeWithExceptionAndUnwind(callTarget, kit.getFrameState(), invokeBci);
    }

    public ValueNode startEnumValueInvokeWithException(HostedGraphKit kit, EnumInfo enumInfo, JavaKind resultKind, ValueNode arg) {
        int invokeBci = kit.bci();
        MethodCallTargetNode callTarget = invokeEnumValue(kit, CallTargetFactory.from(kit), invokeBci, enumInfo, resultKind, arg);
        return kit.startInvokeWithException(callTarget, kit.getFrameState(), invokeBci);
    }

    private MethodCallTargetNode invokeEnumValue(GraphBuilderTool b, CallTargetFactory callTargetFactory, int bci, EnumInfo enumInfo, JavaKind resultKind, ValueNode arg) {
        AnalysisMethod valueMethod = getValueMethodForKind(resultKind);
        AnalysisType returnType = valueMethod.getSignature().getReturnType();
        ValueNode[] args = new ValueNode[2];
        args[0] = ConstantNode.forConstant(b.getSnippetReflection().forObject(enumInfo.getRuntimeData()), b.getMetaAccess(), b.getGraph());
        args[1] = arg;

        StampPair returnStamp = StampFactory.forDeclaredType(null, returnType, false);
        return b.append(callTargetFactory.createMethodCallTarget(InvokeKind.Virtual, valueMethod, args, returnStamp, bci));
    }

    public ValueNode createEnumLookupInvoke(HostedGraphKit kit, AnalysisType enumType, EnumInfo enumInfo, JavaKind parameterKind, ValueNode arg) {
        // Create the invoke to the actual target method: EnumRuntimeData.convertCToJava
        int invokeBci = kit.bci();
        MethodCallTargetNode callTarget = invokeEnumLookup(kit, CallTargetFactory.from(kit), invokeBci, enumInfo, parameterKind, arg);
        InvokeWithExceptionNode invoke = kit.createInvokeWithExceptionAndUnwind(callTarget, kit.getFrameState(), invokeBci);

        // Create the instanceof guard to narrow the return type for the analysis
        LogicNode instanceOfNode = kit.append(InstanceOfNode.createAllowNull(TypeReference.createExactTrusted(enumType), invoke, null, null));
        ConstantNode enumClass = kit.createConstant(kit.getConstantReflection().asJavaClass(enumType), JavaKind.Object);
        GuardingNode guard = kit.createCheckThrowingBytecodeException(instanceOfNode, false, BytecodeExceptionNode.BytecodeExceptionKind.CLASS_CAST, invoke, enumClass);

        // Create the PiNode anchored at the guard to narrow the return type for compilation
        ObjectStamp resultStamp = StampFactory.object(TypeReference.create(null, enumType), false);
        return kit.unique(new PiNode(invoke, resultStamp, guard.asNode()));
    }

    private MethodCallTargetNode invokeEnumLookup(GraphBuilderTool b, CallTargetFactory callTargetFactory, int bci, EnumInfo enumInfo, JavaKind parameterKind, ValueNode arg) {
        ValueNode[] args = new ValueNode[2];
        args[0] = ConstantNode.forConstant(b.getSnippetReflection().forObject(enumInfo.getRuntimeData()), b.getMetaAccess(), b.getGraph());
        assert !Modifier.isStatic(convertCToJavaMethod.getModifiers()) && convertCToJavaMethod.getSignature().getParameterCount(false) == 1;
        JavaKind expectedKind = convertCToJavaMethod.getSignature().getParameterType(0).getJavaKind();
        args[1] = CInterfaceInvocationPlugin.adaptPrimitiveType(b.getGraph(), arg, parameterKind, expectedKind, false);

        AnalysisType convertReturnType = convertCToJavaMethod.getSignature().getReturnType();
        StampPair returnStamp = StampFactory.forDeclaredType(null, convertReturnType, false);
        return b.append(callTargetFactory.createMethodCallTarget(InvokeKind.Virtual, convertCToJavaMethod, args, returnStamp, bci));
    }
}
