/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import static com.oracle.graal.hotspot.meta.HotSpotMethodHandleAccessProvider.*;

import java.lang.invoke.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.MethodHandleAccessProvider.IntrinsicMethod;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.MethodIdMap.Receiver;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.phases.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.replacements.StandardGraphBuilderPlugins.BoxPlugin;

/**
 * Extension of {@link InvocationPlugins} that disables plugins based on runtime configuration.
 */
final class HotSpotInvocationPlugins extends InvocationPlugins {
    final HotSpotVMConfig config;
    final MethodHandleAccessProvider methodHandleAccess;

    public HotSpotInvocationPlugins(HotSpotVMConfig config, MetaAccessProvider metaAccess, MethodHandleAccessProvider methodHandleAccess) {
        super(metaAccess);
        this.config = config;
        this.methodHandleAccess = methodHandleAccess;
    }

    @Override
    public void register(InvocationPlugin plugin, Class<?> declaringClass, String name, Class<?>... argumentTypes) {
        if (!config.usePopCountInstruction) {
            if (name.equals("bitCount")) {
                assert declaringClass.equals(Integer.class) || declaringClass.equals(Long.class);
                return;
            }
        }
        if (config.useHeapProfiler) {
            if (plugin instanceof BoxPlugin) {
                // The heap profiler wants to see all allocations related to boxing
                return;
            }
        }

        super.register(plugin, declaringClass, name, argumentTypes);
    }

    private ResolvedJavaType methodHandleClass;
    private final Map<IntrinsicMethod, InvocationPlugin> methodHandlePlugins = new EnumMap<>(IntrinsicMethod.class);

    @Override
    public InvocationPlugin lookupInvocation(ResolvedJavaMethod method) {
        if (methodHandleClass == null) {
            methodHandleClass = plugins.getMetaAccess().lookupJavaType(MethodHandle.class);
        }
        if (method.getDeclaringClass().equals(methodHandleClass)) {
            HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
            int intrinsicId = hsMethod.intrinsicId();
            if (intrinsicId != 0) {
                /*
                 * The methods of MethodHandle that need substitution are signature-polymorphic,
                 * i.e., the VM replicates them for every signature that they are actually used for.
                 */
                IntrinsicMethod intrinsicMethod = getMethodHandleIntrinsic(intrinsicId);
                if (intrinsicMethod != null) {
                    InvocationPlugin plugin = methodHandlePlugins.get(intrinsicMethod);
                    if (plugin == null) {
                        plugin = new InvocationPlugin() {
                            public boolean applyPolymorphic(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode... argsIncludingReceiver) {
                                InvokeKind invokeKind = b.getInvokeKind();
                                if (invokeKind != InvokeKind.Static) {
                                    receiver.get();
                                }
                                JavaType invokeReturnType = b.getInvokeReturnType();
                                InvokeNode invoke = MethodHandleNode.tryResolveTargetInvoke(b.getAssumptions(), b.getConstantReflection().getMethodHandleAccess(), intrinsicMethod, targetMethod,
                                                b.bci(), invokeReturnType, argsIncludingReceiver);
                                if (invoke == null) {
                                    MethodHandleNode methodHandleNode = new MethodHandleNode(intrinsicMethod, invokeKind, targetMethod, b.bci(), invokeReturnType, argsIncludingReceiver);
                                    if (invokeReturnType.getKind() == Kind.Void) {
                                        b.add(methodHandleNode);
                                    } else {
                                        b.addPush(methodHandleNode);
                                    }
                                } else {
                                    CallTargetNode callTarget = invoke.callTarget();
                                    NodeInputList<ValueNode> argumentsList = callTarget.arguments();
                                    ValueNode[] args = argumentsList.toArray(new ValueNode[argumentsList.size()]);
                                    for (ValueNode arg : args) {
                                        b.recursiveAppend(arg);
                                    }
                                    b.handleReplacedInvoke(invoke.getInvokeKind(), callTarget.targetMethod(), args);
                                }
                                return true;
                            }

                            public boolean isSignaturePolymorphic() {
                                return true;
                            }
                        };
                        methodHandlePlugins.put(intrinsicMethod, plugin);
                    }
                    return plugin;
                }
            }

        }
        return super.lookupInvocation(method);
    }

    @Override
    public void checkNewNodes(GraphBuilderContext b, InvocationPlugin plugin, NodeIterable<Node> newNodes) {
        if (GraalOptions.ImmutableCode.getValue()) {
            for (Node node : newNodes) {
                if (node.hasUsages() && node instanceof ConstantNode) {
                    ConstantNode c = (ConstantNode) node;
                    if (c.getKind() == Kind.Object && !AheadOfTimeVerificationPhase.isLegalObjectConstant(c)) {
                        throw new AssertionError("illegal constant node in AOT: " + node);
                    }
                }
            }
        }
        super.checkNewNodes(b, plugin, newNodes);
    }
}
