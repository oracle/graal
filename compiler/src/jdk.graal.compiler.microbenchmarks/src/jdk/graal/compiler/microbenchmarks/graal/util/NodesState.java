/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.microbenchmarks.graal.util;

import static jdk.graal.compiler.microbenchmarks.graal.util.GraalUtil.getGraphFromMethodSpec;
import static jdk.graal.compiler.microbenchmarks.graal.util.GraalUtil.getNodes;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * State providing the nodes in a graph. Subclasses of this class are annotated with
 * {@link MethodSpec} to specify the Java method that will be parsed to obtain the original graph.
 */
@State(Scope.Benchmark)
public abstract class NodesState {

    public NodesState() {
        this.graph = getGraphFromMethodSpec(getClass());
        this.nodes = getNodes(graph);
        this.originalNodes = nodes.clone();
        List<Node> vnln = new ArrayList<>(nodes.length);
        List<NodePair> list2 = new ArrayList<>(nodes.length);
        for (int i = 0; i < nodes.length; i++) {
            Node n = nodes[i];
            NodeClass<?> nc = n.getNodeClass();
            if (nc.valueNumberable() && nc.isLeafNode()) {
                vnln.add(n);
            }
            for (int j = i + i; j < nodes.length; j++) {
                Node o = nodes[j];
                if (o.getClass() == n.getClass()) {
                    list2.add(new NodePair(n, o));
                }
            }
        }
        valueNumberableLeafNodes = vnln.toArray(new Node[vnln.size()]);
        valueEqualsNodePairs = list2.toArray(new NodePair[list2.size()]);
    }

    /**
     * Used to check that benchmark does not mutate {@link #nodes}.
     */
    private final Node[] originalNodes;

    /**
     * The nodes processed by the benchmark. These arrays must be treated as read-only within the
     * benchmark method.
     */
    public final StructuredGraph graph;
    public final Node[] nodes;
    public final Node[] valueNumberableLeafNodes;
    public final NodePair[] valueEqualsNodePairs;

    public final class NodePair {
        public final Node n1;
        public final Node n2;

        public NodePair(Node n1, Node n2) {
            this.n1 = n1;
            this.n2 = n2;
        }
    }

    private int invocation;

    @TearDown(Level.Invocation)
    public void afterInvocation() {
        if (invocation == 0) {
            // Only need to check the first invocation
            invocation++;
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] != originalNodes[i]) {
                    throw new InternalError(String.format("Benchmark method mutated node %d: original=%s, current=%s", i, originalNodes[i], nodes[i]));
                }
            }
        }
    }

    @Setup(Level.Invocation)
    public void beforeInvocation() {
        /*
         * [GR-48937] Reset the progress-based compilation alarm, because repeated invocations of
         * methods which include progress detection (i.e. node.inputs()) on the same graph
         * throughout many benchmark invocations can falsely detect endless loops.
         */
        CompilationAlarm.resetProgressDetection();
    }
}
