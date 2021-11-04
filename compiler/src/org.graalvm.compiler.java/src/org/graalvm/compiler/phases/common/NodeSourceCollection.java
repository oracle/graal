package org.graalvm.compiler.phases.common;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.BytecodeExceptionNode;
import org.graalvm.compiler.nodes.java.ExceptionObjectNode;
import org.graalvm.compiler.phases.Phase;

import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NodeSourceCollection extends Phase {

    private static final ConcurrentLinkedQueue<NodeSourcePosition> originals = new ConcurrentLinkedQueue<>();
    private static final ConcurrentLinkedQueue<NodeSourcePosition> exceptionObjectsSourcePosition = new ConcurrentLinkedQueue<>();

    @Override
    public void run(StructuredGraph graph) {
        for (BytecodeExceptionNode node: graph.getNodes().filter(BytecodeExceptionNode.class)) {
            originals.add(node.getNodeSourcePosition());
        }
        /*
         * These source positions are also important because ExceptionObjectNode is
         * the entry to an exception handler with the exception coming from a call.
         */
        for (ExceptionObjectNode node: graph.getNodes().filter(ExceptionObjectNode.class)) {
            exceptionObjectsSourcePosition.add(node.getNodeSourcePosition());
        }
    }

    public static NodeSourceCollection create() {
        return new NodeSourceCollection();
    }

    public static boolean isOriginal(NodeSourcePosition nodeSourcePosition) {
        return originals.contains(nodeSourcePosition);
    }

    private static boolean isExceptionObjectPosition(NodeSourcePosition nodeSourcePosition) {
        return exceptionObjectsSourcePosition.contains(nodeSourcePosition);
    }

    private static NodeSourcePosition getRootNodeSourcePosition(NodeSourcePosition nodeSourcePosition) {
        ResolvedJavaMethod rootMethod = nodeSourcePosition.getRootMethod();
        return new NodeSourcePosition(nodeSourcePosition.getSourceLanguage(), null, rootMethod, getRootBci(nodeSourcePosition));
    }

    private static int getRootBci(NodeSourcePosition nodeSourcePosition) {
        NodeSourcePosition cur = nodeSourcePosition;
        while (cur.getCaller() != null) {
            cur = cur.getCaller();
        }
        return cur.getBCI();
    }

    private static boolean equals(NodeSourcePosition position1, NodeSourcePosition position2) {
        return position1.getBCI() == position2.getBCI() && Objects.equals(position1.getMethod(), position2.getMethod()) &&
                Objects.equals(position1.getSourceLanguage(), position2.getSourceLanguage());
    }

    private static boolean foundPrefix(NodeSourcePosition original, NodeSourcePosition newNodeSourcePosition) {
        if (original.depth() > newNodeSourcePosition.depth()) {
            return false;
        }

        NodeSourcePosition position1 = original;
        NodeSourcePosition position2 = newNodeSourcePosition;

        while (position1 != null) {
            if (!equals(original, newNodeSourcePosition)) {
                return false;
            }
            position1 = position1.getCaller();
            position2 = position2.getCaller();
        }
        return true;
    }

    public static boolean hasOriginalPrefix(NodeSourcePosition nodeSourcePosition) {
        for (NodeSourcePosition org: originals) {
            if (foundPrefix(org, nodeSourcePosition)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasOriginalRoot(NodeSourcePosition nodeSourcePosition) {
        return isOriginal(getRootNodeSourcePosition(nodeSourcePosition));
    }

    public static boolean hasRootFromExceptionObject(NodeSourcePosition nodeSourcePosition) {
        return isExceptionObjectPosition(getRootNodeSourcePosition(nodeSourcePosition));
    }
}
