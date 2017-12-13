/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.core.common.type.TypeReference;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.InvokeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderTool;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.type.StampTool;

import com.oracle.svm.core.c.enums.EnumRuntimeData;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.info.EnumInfo;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CInterfaceEnumTool {
    interface CallTargetFactory {
        MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, int bci);

        static CallTargetFactory from(BytecodeParser p) {
            return (invokeKind, targetMethod, args, returnStamp, bci) -> p.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, null);
        }

        static CallTargetFactory from(HostedGraphKit kit) {
            return kit::createMethodCallTarget;
        }
    }

    private final SnippetReflectionProvider snippetReflection;
    private final ResolvedJavaMethod convertJavaToCLongMethod;
    private final ResolvedJavaMethod convertJavaToCIntMethod;
    private final ResolvedJavaMethod convertCToJavaMethod;

    public CInterfaceEnumTool(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
        try {
            convertJavaToCLongMethod = metaAccess.lookupJavaMethod(EnumRuntimeData.class.getDeclaredMethod("convertJavaToCLong", Enum.class));
            convertJavaToCIntMethod = metaAccess.lookupJavaMethod(EnumRuntimeData.class.getDeclaredMethod("convertJavaToCInt", Enum.class));
            convertCToJavaMethod = metaAccess.lookupJavaMethod(EnumRuntimeData.class.getDeclaredMethod("convertCToJava", long.class));
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    private ResolvedJavaMethod getValueMethodForKind(JavaKind kind) {
        return (kind == JavaKind.Long) ? convertJavaToCLongMethod : convertJavaToCIntMethod;
    }

    public InvokeNode createEnumValueInvoke(HostedGraphKit kit, EnumInfo enumInfo, JavaKind resultKind, ValueNode arg) {
        ResolvedJavaMethod valueMethod = getValueMethodForKind(resultKind);
        return invokeEnumValue(kit, CallTargetFactory.from(kit), kit.getFrameState(), kit.bci(), enumInfo, valueMethod, arg);
    }

    boolean replaceEnumValueInvoke(BytecodeParser p, EnumInfo enumInfo, ResolvedJavaMethod method, ValueNode[] args) {
        ResolvedJavaMethod valueMethod = getValueMethodForKind(method.getSignature().getReturnKind());
        JavaType originalReturnType = method.getSignature().getReturnType(null);

        assert args.length == 1;
        InvokeNode invoke = invokeEnumValue(p, CallTargetFactory.from(p), p.getFrameStateBuilder(), p.bci(), enumInfo, valueMethod, args[0]);

        ValueNode adapted = CInterfaceInvocationPlugin.adaptPrimitiveType(p.getGraph(), invoke, invoke.stamp(NodeView.DEFAULT).getStackKind(), originalReturnType.getJavaKind(), false);
        Stamp originalStamp = p.getInvokeReturnStamp(null).getTrustedStamp();
        adapted = CInterfaceInvocationPlugin.adaptPrimitiveType(p.getGraph(), adapted, originalReturnType.getJavaKind(), originalStamp.getStackKind(), false);
        p.push(CInterfaceInvocationPlugin.pushKind(method), adapted);
        return true;
    }

    private InvokeNode invokeEnumValue(GraphBuilderTool b, CallTargetFactory callTargetFactory, FrameStateBuilder frameState,
                    int bci, EnumInfo enumInfo, ResolvedJavaMethod valueMethod, ValueNode arg) {

        ResolvedJavaType returnType = (ResolvedJavaType) valueMethod.getSignature().getReturnType(null);
        ValueNode[] args = new ValueNode[2];
        args[0] = ConstantNode.forConstant(snippetReflection.forObject(enumInfo.getRuntimeData()), b.getMetaAccess(), b.getGraph());
        args[1] = arg;

        StampPair returnStamp = StampFactory.forDeclaredType(null, returnType, false);
        MethodCallTargetNode callTargetNode = b.append(callTargetFactory.createMethodCallTarget(InvokeKind.Virtual, valueMethod, args, returnStamp, bci));

        Stamp invokeStamp = StampFactory.forKind(returnType.getJavaKind());
        InvokeNode invoke = b.append(new InvokeNode(callTargetNode, bci, invokeStamp));

        frameState.push(returnType.getJavaKind(), invoke);
        FrameState stateWithInvoke = frameState.create(bci, invoke);
        frameState.pop(returnType.getJavaKind());
        invoke.setStateAfter(stateWithInvoke);
        return invoke;
    }

    public ValueNode createEnumLookupInvoke(HostedGraphKit kit, ResolvedJavaType enumType, EnumInfo enumInfo, JavaKind parameterKind, ValueNode arg) {
        InvokeNode invoke = invokeEnumLookup(kit, CallTargetFactory.from(kit), kit.getFrameState(), kit.bci(), enumInfo, parameterKind, arg);
        ObjectStamp resultStamp = StampFactory.object(TypeReference.create(null, enumType), false);
        return kit.unique(new PiNode(invoke, resultStamp));
    }

    boolean replaceEnumLookupInvoke(BytecodeParser p, EnumInfo enumInfo, ResolvedJavaMethod method, ValueNode[] args) {
        assert Modifier.isStatic(method.getModifiers()) && method.getSignature().getParameterCount(false) == 1;
        assert args.length == 1;
        JavaKind methodParameterKind = method.getSignature().getParameterType(0, null).getJavaKind();

        InvokeNode invokeNode = invokeEnumLookup(p, CallTargetFactory.from(p), p.getFrameStateBuilder(), p.bci(), enumInfo, methodParameterKind, args[0]);

        Stamp returnStamp = p.getInvokeReturnStamp(null).getTrustedStamp();
        assert returnStamp.getStackKind() == JavaKind.Object && invokeNode.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Object;
        assert StampTool.typeOrNull(invokeNode.stamp(NodeView.DEFAULT)).isAssignableFrom(StampTool.typeOrNull(returnStamp));

        ValueNode adapted = p.getGraph().unique(new PiNode(invokeNode, returnStamp));
        p.push(CInterfaceInvocationPlugin.pushKind(method), adapted);
        return true;
    }

    private InvokeNode invokeEnumLookup(GraphBuilderTool b, CallTargetFactory callTargetFactory, FrameStateBuilder frameState, int bci, EnumInfo enumInfo, JavaKind parameterKind, ValueNode arg) {
        ValueNode[] args = new ValueNode[2];
        args[0] = ConstantNode.forConstant(snippetReflection.forObject(enumInfo.getRuntimeData()), b.getMetaAccess(), b.getGraph());
        assert !Modifier.isStatic(convertCToJavaMethod.getModifiers()) && convertCToJavaMethod.getSignature().getParameterCount(false) == 1;
        JavaKind expectedKind = convertCToJavaMethod.getSignature().getParameterType(0, null).getJavaKind();
        args[1] = CInterfaceInvocationPlugin.adaptPrimitiveType(b.getGraph(), arg, parameterKind, expectedKind, false);

        ResolvedJavaType convertReturnType = (ResolvedJavaType) convertCToJavaMethod.getSignature().getReturnType(null);
        StampPair returnStamp = StampFactory.forDeclaredType(null, convertReturnType, false);
        MethodCallTargetNode callTargetNode = b.append(
                        callTargetFactory.createMethodCallTarget(InvokeKind.Virtual, convertCToJavaMethod, args, returnStamp, bci));

        Stamp invokeStamp = StampFactory.object(TypeReference.createWithoutAssumptions(convertReturnType));
        InvokeNode invoke = b.append(new InvokeNode(callTargetNode, bci, invokeStamp));

        frameState.push(convertReturnType.getJavaKind(), invoke);
        FrameState stateWithInvoke = frameState.create(bci, invoke);
        frameState.pop(convertReturnType.getJavaKind());
        invoke.setStateAfter(stateWithInvoke);
        return invoke;
    }
}
