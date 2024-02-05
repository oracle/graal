/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.reachability;

import java.lang.reflect.Modifier;
import java.util.Optional;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ForeignCall;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.replacements.nodes.BinaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This handler walks the structured graphs of methods and directly calls back into the
 * ReachabilityAnalysisEngine instead of creating summaries.
 */
public class DirectMethodProcessingHandler implements ReachabilityMethodProcessingHandler {

    @Override
    public void onMethodReachable(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method) {
        StructuredGraph decoded = ReachabilityAnalysisMethod.getDecodedGraph(bb, method);
        analyzeStructuredGraph(bb, method, decoded);
    }

    @Override
    public void processGraph(ReachabilityAnalysisEngine bb, StructuredGraph graph) {
        analyzeStructuredGraph(bb, (ReachabilityAnalysisMethod) graph.method(), graph);
    }

    private static void analyzeStructuredGraph(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method, StructuredGraph graph) {
        if (method != null) {
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            int parameterCount = method.getSignature().getParameterCount(!isStatic);
            int offset = isStatic ? 0 : 1;
            for (int i = offset; i < parameterCount; i++) {
                bb.registerTypeAsReachable(method.getSignature().getParameterType(i - offset),
                                "Parameter type for " + method.format("%H.%n(%p)"));
            }

            bb.registerTypeAsReachable(method.getSignature().getReturnType(), "Return type for " + method.format("%H.%n(%p)"));
        }

        for (Node n : graph.getNodes()) {
            if (n instanceof NewInstanceNode) {
                NewInstanceNode node = (NewInstanceNode) n;
                bb.registerTypeAsAllocated((ReachabilityAnalysisType) node.instanceClass(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof NewArrayNode) {
                NewArrayNode node = (NewArrayNode) n;
                bb.registerTypeAsAllocated(((ReachabilityAnalysisType) node.elementType()).getArrayClass(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof NewMultiArrayNode) {
                NewMultiArrayNode node = (NewMultiArrayNode) n;
                ResolvedJavaType type = node.type();
                for (int i = 0; i < node.dimensionCount(); i++) {
                    bb.registerTypeAsAllocated((ReachabilityAnalysisType) type, AbstractAnalysisEngine.sourcePosition(node));
                    type = type.getComponentType();
                }
            } else if (n instanceof VirtualInstanceNode) {
                VirtualInstanceNode node = (VirtualInstanceNode) n;
                bb.registerTypeAsAllocated((ReachabilityAnalysisType) node.type(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof VirtualArrayNode) {
                VirtualArrayNode node = (VirtualArrayNode) n;
                bb.registerTypeAsAllocated(((ReachabilityAnalysisType) node.componentType()).getArrayClass(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof ConstantNode) {
                ConstantNode node = (ConstantNode) n;
                if (!(node.getValue() instanceof JavaConstant)) {
                    /*
                     * The bytecode parser sometimes embeds low-level VM constants for types into
                     * the high-level graph. Since these constants are the result of type lookups,
                     * these types are already marked as reachable. Eventually, the bytecode parser
                     * should be changed to only use JavaConstant.
                     */
                    continue;
                }
                JavaConstant constant = (JavaConstant) node.getValue();
                bb.handleEmbeddedConstant(method, constant, AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof InstanceOfNode) {
                InstanceOfNode node = (InstanceOfNode) n;
                bb.registerTypeAsReachable((ReachabilityAnalysisType) node.type().getType(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof LoadFieldNode) {
                LoadFieldNode node = (LoadFieldNode) n;
                bb.markFieldRead((ReachabilityAnalysisField) node.field(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof StoreFieldNode) {
                StoreFieldNode node = (StoreFieldNode) n;
                bb.markFieldWritten((ReachabilityAnalysisField) node.field(), AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof Invoke) {
                Invoke node = (Invoke) n;
                CallTargetNode.InvokeKind kind = node.getInvokeKind();
                ReachabilityAnalysisMethod targetMethod = (ReachabilityAnalysisMethod) node.getTargetMethod();
                if (targetMethod == null || AnnotationAccess.isAnnotationPresent(targetMethod, Node.NodeIntrinsic.class)) {
                    continue;
                }
                BytecodePosition reason = AbstractAnalysisEngine.sourcePosition(node.asNode());
                if (method != null) {
                    method.addInvoke(new ReachabilityInvokeInfo(targetMethod, reason, kind.isDirect()));
                }
                if (kind == CallTargetNode.InvokeKind.Static) {
                    bb.markMethodImplementationInvoked(targetMethod, reason);
                } else if (kind == CallTargetNode.InvokeKind.Special) {
                    bb.markMethodSpecialInvoked(targetMethod, reason);
                } else {
                    bb.markMethodInvoked(targetMethod, reason);
                }
            } else if (n instanceof FrameState) {
                FrameState node = (FrameState) n;
                ResolvedJavaMethod frameMethod = node.getMethod();
                if (frameMethod != null) {
                    /*
                     * All types referenced in (possibly inlined) frame states must be reachable,
                     * because these classes will be reachable from stack walking metadata. This
                     * metadata is only constructed after AOT compilation, so the image heap
                     * scanning during static analysis does not see these classes.
                     */
                    ReachabilityAnalysisMethod analysisMethod = (ReachabilityAnalysisMethod) frameMethod;
                    bb.registerTypeAsReachable(analysisMethod.getDeclaringClass(), AbstractAnalysisEngine.syntheticSourcePosition(node, method));
                }
            } else if (n instanceof MacroInvokable) {
                MacroInvokable node = (MacroInvokable) n;
                ReachabilityAnalysisMethod targetMethod = (ReachabilityAnalysisMethod) node.getTargetMethod();
                BytecodePosition reason = AbstractAnalysisEngine.syntheticSourcePosition(node.asNode(), method);
                CallTargetNode.InvokeKind kind = node.getInvokeKind();
                if (kind == CallTargetNode.InvokeKind.Static) {
                    bb.markMethodImplementationInvoked(targetMethod, reason);
                } else if (kind == CallTargetNode.InvokeKind.Special) {
                    bb.markMethodSpecialInvoked(targetMethod, reason);
                } else {
                    bb.markMethodInvoked(targetMethod, reason);
                }
            } else if (n instanceof ForeignCall) {
                MultiMethod.MultiMethodKey key = method == null ? MultiMethod.ORIGINAL_METHOD : method.getMultiMethodKey();
                ForeignCallsProvider foreignCallsProvider = bb.getProviders(key).getForeignCalls();
                handleForeignCall(bb, ((ForeignCall) n).getDescriptor(), foreignCallsProvider, graph.method());
            } else if (n instanceof UnaryMathIntrinsicNode) {
                ForeignCallSignature signature = ((UnaryMathIntrinsicNode) n).getOperation().foreignCallSignature;
                MultiMethod.MultiMethodKey key = method == null ? MultiMethod.ORIGINAL_METHOD : method.getMultiMethodKey();
                ForeignCallsProvider foreignCallsProvider = bb.getProviders(key).getForeignCalls();
                handleForeignCall(bb, foreignCallsProvider.getDescriptor(signature), foreignCallsProvider, graph.method());
            } else if (n instanceof BinaryMathIntrinsicNode) {
                ForeignCallSignature signature = ((BinaryMathIntrinsicNode) n).getOperation().foreignCallSignature;
                MultiMethod.MultiMethodKey key = method == null ? MultiMethod.ORIGINAL_METHOD : method.getMultiMethodKey();
                ForeignCallsProvider foreignCallsProvider = bb.getProviders(key).getForeignCalls();
                handleForeignCall(bb, foreignCallsProvider.getDescriptor(signature), foreignCallsProvider, graph.method());

            }
        }
    }

    private static void handleForeignCall(ReachabilityAnalysisEngine bb, ForeignCallDescriptor descriptor, ForeignCallsProvider foreignCallsProvider, ResolvedJavaMethod from) {
        Optional<AnalysisMethod> targetMethod = bb.getHostVM().handleForeignCall(descriptor, foreignCallsProvider);
        targetMethod.ifPresent(method -> bb.addRootMethod(method, false, from));
    }
}
