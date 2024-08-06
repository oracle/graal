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
package com.oracle.svm.hosted.code;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.svm.hosted.meta.HostedMethod;

/**
 * A holder for an {@link EncodedGraph} while doing the AOT compilation in {@link CompileQueue}.
 * Encoding the graph is important to reduce the memory footprint of the image generator. But a few
 * properties of the graph need to remain accessible, for example to guide inlining decisions. All
 * such information is stored in separate fields of this class. This ensures that an encoded graph
 * and its extra information are always in sync.
 */
public final class CompilationGraph {

    /** Information about an invocation in the encoded graph. */
    public static class InvokeInfo {
        private final InvokeKind invokeKind;
        private final HostedMethod targetMethod;
        private final HostedMethod directCaller;
        private final NodeSourcePosition nodeSourcePosition;

        InvokeInfo(InvokeKind invokeKind, HostedMethod targetMethod, HostedMethod directCaller, NodeSourcePosition nodeSourcePosition) {
            this.invokeKind = invokeKind;
            this.targetMethod = targetMethod;
            this.directCaller = directCaller;
            this.nodeSourcePosition = nodeSourcePosition;
        }

        public InvokeKind getInvokeKind() {
            return invokeKind;
        }

        public HostedMethod getTargetMethod() {
            return targetMethod;
        }

        public HostedMethod getDirectCaller() {
            return directCaller;
        }

        public NodeSourcePosition getNodeSourcePosition() {
            return nodeSourcePosition;
        }
    }

    /** Information about an allocation in the encoded graph. */
    public static class AllocationInfo {
        private final NodeSourcePosition nodeSourcePosition;

        AllocationInfo(NodeSourcePosition nodeSourcePosition) {
            this.nodeSourcePosition = nodeSourcePosition;
        }

        public NodeSourcePosition getNodeSourcePosition() {
            return nodeSourcePosition;
        }
    }

    private final EncodedGraph encodedGraph;
    private final int nodeCount;
    private final Set<InvokeInfo> invokeInfos;
    private final Set<AllocationInfo> allocationInfos;

    private CompilationGraph(EncodedGraph encodedGraph, int nodeCount, Set<InvokeInfo> invokeInfos, Set<AllocationInfo> allocationInfos) {
        this.encodedGraph = encodedGraph;
        this.nodeCount = nodeCount;
        this.invokeInfos = invokeInfos;
        this.allocationInfos = allocationInfos;
    }

    static CompilationGraph encode(StructuredGraph graph) {
        Set<InvokeInfo> invokeInfos = new HashSet<>();
        Set<AllocationInfo> allocationInfos = new HashSet<>();
        for (var n : graph.getNodes()) {
            if (n instanceof MethodCallTargetNode) {
                MethodCallTargetNode node = (MethodCallTargetNode) n;
                invokeInfos.add(new InvokeInfo(
                                node.invokeKind(),
                                (HostedMethod) node.targetMethod(),
                                (HostedMethod) node.invoke().stateAfter().getMethod(),
                                node.getNodeSourcePosition()));
            }
            if (UninterruptibleAnnotationChecker.isAllocationNode(n)) {
                allocationInfos.add(new AllocationInfo(n.getNodeSourcePosition()));
            }
        }

        return new CompilationGraph(
                        GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE),
                        graph.getNodeCount(),
                        invokeInfos.isEmpty() ? Collections.emptySet() : invokeInfos,
                        allocationInfos.isEmpty() ? Collections.emptySet() : allocationInfos);
    }

    public EncodedGraph getEncodedGraph() {
        return encodedGraph;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    public Set<InvokeInfo> getInvokeInfos() {
        return invokeInfos;
    }

    public Set<AllocationInfo> getAllocationInfos() {
        return allocationInfos;
    }
}
