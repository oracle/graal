/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflect.hosted;

// Checkstyle: allow reflection

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.ResolvedJavaMethodBytecode;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.phases.common.inlining.InliningUtil;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.SubstrateIntrinsicGraphBuilder;
import com.oracle.svm.core.invoke.MethodHandleUtils;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReflectiveInvokeMethod extends ReflectionMethod {

    private final Method method;
    private final boolean specialInvoke;

    public ReflectiveInvokeMethod(String name, ResolvedJavaMethod prototype, Method method, boolean specialInvoke) {
        super(name, prototype);
        this.method = method;
        this.specialInvoke = specialInvoke;
    }

    @Override
    public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod m, HostedProviders providers, Purpose purpose) {
        HostedGraphKit graphKit = new HostedGraphKit(ctx, providers, m);

        ResolvedJavaMethod targetMethod;
        ValueNode[] args;
        if (!specialInvoke && method.getDeclaringClass() == MethodHandle.class && (method.getName().equals("invoke") || method.getName().equals("invokeExact"))) {
            targetMethod = MethodHandleUtils.getThrowUnsupportedOperationException(providers.getMetaAccess());
            args = new ValueNode[0];
        } else {
            targetMethod = providers.getMetaAccess().lookupJavaMethod(method);
            Class<?>[] argTypes = method.getParameterTypes();

            int receiverOffset = targetMethod.isStatic() ? 0 : 1;
            args = new ValueNode[argTypes.length + receiverOffset];
            if (targetMethod.isStatic()) {
                graphKit.emitEnsureInitializedCall(targetMethod.getDeclaringClass());
            } else {
                ValueNode receiver = graphKit.loadLocal(0, JavaKind.Object);
                args[0] = createCheckcast(graphKit, receiver, targetMethod.getDeclaringClass(), true);
            }

            ValueNode argumentArray = graphKit.loadLocal(1, JavaKind.Object);
            fillArgsArray(graphKit, argumentArray, receiverOffset, args, argTypes);
        }

        InvokeKind invokeKind;
        if (specialInvoke) {
            invokeKind = InvokeKind.Special;
        } else if (targetMethod.isStatic()) {
            invokeKind = InvokeKind.Static;
        } else if (targetMethod.isInterface()) {
            invokeKind = InvokeKind.Interface;
        } else if (targetMethod.canBeStaticallyBound() || targetMethod.isConstructor()) {
            invokeKind = InvokeKind.Special;
        } else {
            invokeKind = InvokeKind.Virtual;
        }

        InvokeWithExceptionNode invoke = graphKit.createJavaCallWithException(invokeKind, targetMethod, args);
        ValueNode ret = invoke;

        graphKit.noExceptionPart();

        JavaKind retKind = targetMethod.getSignature().getReturnKind();
        if (retKind == JavaKind.Void) {
            ret = graphKit.createObject(null);
        } else if (retKind.isPrimitive()) {
            ResolvedJavaType boxedRetType = providers.getMetaAccess().lookupJavaType(retKind.toBoxedJavaClass());
            ret = graphKit.createBoxing(ret, retKind, boxedRetType);
        }

        graphKit.createReturn(ret, JavaKind.Object);

        graphKit.exceptionPart();
        graphKit.throwInvocationTargetException(graphKit.exceptionObject());

        graphKit.endInvokeWithException();

        if (invokeKind.isDirect()) {
            InvocationPlugin invocationPlugin = providers.getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
            if (invocationPlugin != null && !invocationPlugin.inlineOnly()) {
                /*
                 * The BytecodeParser applies invocation plugins directly during bytecode parsing.
                 * We cannot do that because GraphKit is not a GraphBuilderContext. To get as close
                 * as possible to the BytecodeParser behavior, we create a new graph for the
                 * intrinsic and inline it immediately.
                 */
                Bytecode code = new ResolvedJavaMethodBytecode(targetMethod);
                StructuredGraph intrinsicGraph = new SubstrateIntrinsicGraphBuilder(graphKit.getOptions(), graphKit.getDebug(), providers, code).buildGraph(invocationPlugin);
                if (intrinsicGraph != null) {
                    InliningUtil.inline(invoke, intrinsicGraph, false, targetMethod);
                }
            }
        }

        return graphKit.finalizeGraph();
    }
}
