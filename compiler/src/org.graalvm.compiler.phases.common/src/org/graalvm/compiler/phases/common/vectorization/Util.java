package org.graalvm.compiler.phases.common.vectorization;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.AddNode;
import org.graalvm.compiler.nodes.calc.LeftShiftNode;
import org.graalvm.compiler.nodes.calc.SignExtendNode;
import org.graalvm.compiler.nodes.memory.WriteNode;
import org.graalvm.compiler.nodes.memory.address.AddressNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

class Util {
    private Util() { }

    static Stamp getStamp(ValueNode node, NodeView view) {
        return (node instanceof WriteNode ? ((WriteNode) node).value() : node).stamp(view);
    }

    static Set<Node> getInductionVariables(AddressNode address) {
        final Set<Node> ivs = new HashSet<>();
        final Deque<Node> bfs = new ArrayDeque<>();
        bfs.add(address);

        while (!bfs.isEmpty()) {
            final Node node = bfs.remove();
            if (node instanceof AddressNode) {
                final AddressNode addressNode = (AddressNode) node;
                if (addressNode.getIndex() != null) {
                    bfs.add(addressNode.getIndex());
                }
            } else if (node instanceof AddNode) {
                final AddNode addNode = (AddNode) node;
                bfs.add(addNode.getX());
                bfs.add(addNode.getY());
            } else if (node instanceof LeftShiftNode) {
                final LeftShiftNode leftShiftNode = (LeftShiftNode) node;
                bfs.add(leftShiftNode.getX());
                bfs.add(leftShiftNode.getY());
            } else if (node instanceof SignExtendNode) {
                final SignExtendNode signExtendNode = (SignExtendNode) node;
                bfs.add(signExtendNode.getValue());
            } else if (node instanceof ConstantNode) {
                // constant nodes are leaf nodes
            } else {
                ivs.add(node);
            }
        }

        return ivs;
    }
}
