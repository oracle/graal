package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;

import java.util.List;
import java.util.stream.StreamSupport;

public final class VectorSupport {
    private VectorSupport() { }

    // TODO: Use these for packing

    @NodeInfo
    public static final class VectorUnpackNode extends FloatingNode {

        public static final NodeClass<VectorUnpackNode> TYPE = NodeClass.create(VectorUnpackNode.class);

        @Input private ValueNode value;

        public VectorUnpackNode(Stamp stamp, ValueNode value) {
            this(TYPE, stamp, value);
        }

        private VectorUnpackNode(NodeClass<? extends VectorUnpackNode> c, Stamp stamp, ValueNode value) {
            super(c, stamp);
            this.value = value;
        }

        public ValueNode value() {
            return value;
        }

        @Override
        public boolean verify() {
            assertTrue(value.isVector(), "VectorUnpackNode requires a vector ValueNode input");
            return super.verify();
        }
    }

    @NodeInfo
    public static final class VectorPackNode extends FloatingNode implements Canonicalizable {

        public static final NodeClass<VectorPackNode> TYPE = NodeClass.create(VectorPackNode.class);

        @Input private NodeInputList<ValueNode> values;

        public VectorPackNode(Stamp stamp, List<ValueNode> values) {
            this(TYPE, stamp, values);
        }

        private VectorPackNode(NodeClass<? extends VectorPackNode> c, Stamp stamp, List<ValueNode> values) {
            super(c, stamp);
            this.values = new NodeInputList<>(this, values);
        }

        public NodeInputList<ValueNode> values() {
            return values;
        }

        @Override
        public boolean verify() {
            assertTrue(values.stream().noneMatch(ValueNode::isVector), "VectorPackNode requires scalar inputs");
            return super.verify();
        }

        @Override
        public boolean isVector() {
            return true;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // Do nothing if no inputs
            if (inputs().isEmpty()) {
                return this;
            }

            final Node first = inputs().first();

            // If all values are VectorUnpackNode, then we can delete this node and the input node
            final boolean allEqual = StreamSupport.stream(inputs().spliterator(), false).allMatch(first::equals);

            // Do nothing if not all inputs are equal and instance of VTS
            if (!allEqual || !(first instanceof VectorUnpackNode) || first.getUsageCount() != inputs().count()) {
                return this;
            }

            final VectorUnpackNode firstVTS = (VectorUnpackNode) first;

            // What happens when we return null?
            replaceAtUsages(firstVTS.value);

            return null; // to delete the current node
        }
    }
}
