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
package com.oracle.svm.hosted.reflect;

import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.nodes.CallTargetNode;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.IndirectCallTargetNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.graal.code.SubstrateCallingConventionKind;
import com.oracle.svm.core.reflect.ReflectionAccessorHolder.MethodInvokeFunctionPointer;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor;
import com.oracle.svm.hosted.code.NonBytecodeStaticMethod;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class ReflectionExpandSignatureMethod extends NonBytecodeStaticMethod {

    private final boolean isStatic;
    private final Class<?>[] argTypes;
    private final JavaKind returnKind;
    private final boolean callerSensitiveAdapter;

    public ReflectionExpandSignatureMethod(String name, ResolvedJavaMethod prototype, boolean isStatic, Class<?>[] argTypes, JavaKind returnKind, boolean callerSensitiveAdapter) {
        super(name, prototype.getDeclaringClass(), prototype.getSignature(), prototype.getConstantPool());
        this.isStatic = isStatic;
        this.argTypes = argTypes;
        this.returnKind = returnKind;
        this.callerSensitiveAdapter = callerSensitiveAdapter;
    }

    /**
     * Builds the graph that is invoked via {@link SubstrateMethodAccessor} and
     * {@link SubstrateConstructorAccessor}. The signature is defined by
     * {@link MethodInvokeFunctionPointer}.
     *
     * Based on the signature of the invoked method, the Object[] for the arguments is unpacked and
     * the arguments are type checked. The target method is then invoked using a function pointer
     * call. This allows sharing of these graphs for methods with the same signature.
     *
     * Note that there is no check at all performed for the receiver. It is assumed that the
     * receiver was already null-checked and type-checked by the caller. This minimizes the size of
     * the graph and allows sharing of these graphs for methods with the same signature but declared
     * in different classes.
     */
    @Override
    public StructuredGraph buildGraph(DebugContext ctx, ResolvedJavaMethod m, HostedProviders providers, Purpose purpose) {
        ReflectionGraphKit graphKit = new ReflectionGraphKit(ctx, providers, m);

        ValueNode receiver = graphKit.loadLocal(0, JavaKind.Object);
        ValueNode argumentArray = graphKit.loadLocal(1, JavaKind.Object);
        /* The invokedMethod is a Word type, so not yet a primitive in the signature. */
        ValueNode invokedMethod = graphKit.loadLocal(2, JavaKind.Object);
        /* Caller-sensitive-adapter methods have an additional Class parameter. */
        ValueNode callerClass = callerSensitiveAdapter ? graphKit.loadLocal(3, JavaKind.Object) : null;

        /* Clear all locals, so that they are not alive and spilled at method calls. */
        graphKit.getFrameState().clearLocals();

        int receiverOffset = isStatic ? 0 : 1;
        int argsCount = argTypes.length + receiverOffset + (callerSensitiveAdapter ? 1 : 0);
        ValueNode[] args = new ValueNode[argsCount];
        ResolvedJavaType[] signature = new ResolvedJavaType[argsCount];
        if (!isStatic) {
            /*
             * The receiver is already null-checked and type-checked at the call site in
             * SubstrateMethodAccessor.
             */
            signature[0] = providers.getMetaAccess().lookupJavaType(Object.class);
            args[0] = receiver;
        }
        if (callerSensitiveAdapter) {
            signature[argsCount - 1] = providers.getMetaAccess().lookupJavaType(Class.class);
            args[argsCount - 1] = callerClass;
        }

        graphKit.fillArgsArray(argumentArray, receiverOffset, args, argTypes);
        for (int i = 0; i < argTypes.length; i++) {
            signature[i + receiverOffset] = providers.getMetaAccess().lookupJavaType(argTypes[i]);
        }

        CallTargetNode callTarget = graphKit.append(new IndirectCallTargetNode(invokedMethod, args, StampPair.createSingle(StampFactory.forKind(returnKind)), signature, null,
                        SubstrateCallingConventionKind.Java.toType(true), InvokeKind.Static));

        InvokeWithExceptionNode invoke = graphKit.startInvokeWithException(callTarget, graphKit.getFrameState(), graphKit.bci());
        graphKit.exceptionPart();
        graphKit.branchToInvocationTargetException(graphKit.exceptionObject());
        graphKit.endInvokeWithException();

        ValueNode returnValue;
        if (returnKind == JavaKind.Void) {
            returnValue = graphKit.createObject(null);
        } else {
            returnValue = invoke;
            if (returnKind.isPrimitive()) {
                ResolvedJavaType boxedRetType = graphKit.getMetaAccess().lookupJavaType(returnKind.toBoxedJavaClass());
                returnValue = graphKit.createBoxing(returnValue, returnKind, boxedRetType);
            }
        }
        graphKit.createReturn(returnValue, JavaKind.Object);

        graphKit.emitIllegalArgumentException(isStatic ? null : receiver, argumentArray);
        graphKit.emitInvocationTargetException();

        return graphKit.finalizeGraph();
    }
}
