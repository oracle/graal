package com.oracle.graal.microbenchmarks.graal.util;

import static com.oracle.graal.microbenchmarks.graal.util.GraalUtil.*;

import java.util.*;

import org.openjdk.jmh.annotations.*;

import com.oracle.graal.graph.*;

/**
 * State providing the nodes in a graph. Subclasses of this class are annotated with
 * {@link MethodSpec} to specify the Java method that will be parsed to obtain the original graph.
 */
@State(Scope.Benchmark)
public abstract class NodesState {

    public NodesState() {
        this.nodes = getNodes(getGraphFromMethodSpec(getClass()));
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
}