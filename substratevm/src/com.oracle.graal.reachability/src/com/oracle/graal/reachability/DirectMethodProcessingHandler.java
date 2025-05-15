/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.flow.MethodTypeFlowBuilder;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisType;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;
import jdk.graal.compiler.nodes.extended.FieldOffsetProvider;
import jdk.graal.compiler.nodes.extended.GetClassNode;
import jdk.graal.compiler.nodes.extended.RawLoadNode;
import jdk.graal.compiler.nodes.extended.RawStoreNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndAddNode;
import jdk.graal.compiler.nodes.java.AtomicReadAndWriteNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndExchangeNode;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.AllocatedObjectNode;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualInstanceNode;
import jdk.graal.compiler.replacements.nodes.MacroInvokable;
import jdk.vm.ci.code.BytecodePosition;

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
        /* First, reuse all the registrations done before the type flow graph creation. */
        MethodTypeFlowBuilder.registerUsedElements(bb, graph, true);

        /* Then, perform extra registrations that happen in PTA during the analysis. */
        if (method != null) {
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            int parameterCount = method.getSignature().getParameterCount(!isStatic);
            int offset = isStatic ? 0 : 1;
            for (int i = offset; i < parameterCount; i++) {
                method.getSignature().getParameterType(i - offset).registerAsReachable(
                                "Parameter type for " + method.format("%H.%n(%p)"));
            }

            method.getSignature().getReturnType().registerAsReachable("Return type for " + method.format("%H.%n(%p)"));
        }

        for (Node n : graph.getNodes()) {
            if (n instanceof NewInstanceNode node) {
                ((AnalysisType) node.instanceClass()).registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof VirtualInstanceNode node) {
                ((AnalysisType) node.type()).registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof VirtualArrayNode node) {
                ((AnalysisType) node.componentType()).getArrayClass().registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof CommitAllocationNode node) {
                for (AllocatedObjectNode allocatedObjectNode : node.usages().filter(AllocatedObjectNode.class)) {
                    var type = ((AnalysisType) allocatedObjectNode.getVirtualObject().type());
                    type.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(allocatedObjectNode));
                }
            } else if (n instanceof DynamicNewInstanceNode node && node.getInstanceType() instanceof GetClassNode getClassNode) {
                var receiverType = (AnalysisType) StampTool.typeOrNull(getClassNode.getObject(), bb.getMetaAccess());
                receiverType.registerAsInstantiated(AbstractAnalysisEngine.sourcePosition(node));
            } else if (n instanceof RawLoadNode node) {
                processUnsafeField(node, node.offset());
            } else if (n instanceof RawStoreNode node) {
                processUnsafeField(node, node.offset());
            } else if (n instanceof UnsafeCompareAndSwapNode node) {
                processUnsafeField(node, node.offset());
            } else if (n instanceof UnsafeCompareAndExchangeNode node) {
                processUnsafeField(node, node.offset());
            } else if (n instanceof AtomicReadAndWriteNode node) {
                processUnsafeField(node, node.offset());
            } else if (n instanceof AtomicReadAndAddNode node) {
                processUnsafeField(node, node.offset());
            } else if (n instanceof Invoke node) {
                processInvoke(bb, method, ((ReachabilityAnalysisMethod) node.getTargetMethod()), node.getInvokeKind(), node);
            } else if (n instanceof MacroInvokable node) {
                processInvoke(bb, method, ((ReachabilityAnalysisMethod) node.getTargetMethod()), node.getInvokeKind(), node);
            }
        }
    }

    private static void processInvoke(ReachabilityAnalysisEngine bb, ReachabilityAnalysisMethod method, ReachabilityAnalysisMethod targetMethod, CallTargetNode.InvokeKind kind,
                    ValueNodeInterface node) {
        if (targetMethod == null || AnnotationAccess.isAnnotationPresent(targetMethod, Node.NodeIntrinsic.class)) {
            return;
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
    }

    private static void processUnsafeField(ValueNode node, ValueNode offset) {
        if (offset instanceof FieldOffsetProvider provider) {
            var field = ((AnalysisField) provider.getField());
            field.registerAsUnsafeAccessed(AbstractAnalysisEngine.sourcePosition(node));
        }
    }
}
