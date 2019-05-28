package org.graalvm.compiler.nodes;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;

import java.util.List;
import java.util.stream.StreamSupport;

public final class VectorSupport {
    private VectorSupport() { }

    // TODO: Use these for packing

    @NodeInfo
    public static final class VectorToScalarValueNode extends ValueNode {

        public static final NodeClass<VectorToScalarValueNode> TYPE = NodeClass.create(VectorToScalarValueNode.class);

        @Input private ValueNode value;

        public VectorToScalarValueNode(Stamp stamp, ValueNode value) {
            this(TYPE, stamp, value);
        }

        private VectorToScalarValueNode(NodeClass<? extends VectorToScalarValueNode> c, Stamp stamp, ValueNode value) {
            super(c, stamp);
            this.value = value;
        }

        public ValueNode value() {
            return value;
        }

        @Override
        public boolean verify() {
            assertTrue(value.isVector(), "VectorToScalarValueNode requires a vector ValueNode input");
            return super.verify();
        }
    }

    @NodeInfo
    public static final class ScalarToVectorValueNode extends ValueNode /*implements Canonicalizable */{

        public static final NodeClass<ScalarToVectorValueNode> TYPE = NodeClass.create(ScalarToVectorValueNode.class);

        @Input private NodeInputList<ValueNode> values;

        public ScalarToVectorValueNode(Stamp stamp, List<ValueNode> values) {
            this(TYPE, stamp, values);
        }

        private ScalarToVectorValueNode(NodeClass<? extends ScalarToVectorValueNode> c, Stamp stamp, List<ValueNode> values) {
            super(c, stamp);
            this.values = new NodeInputList<>(this, values);
        }

        public NodeInputList<ValueNode> values() {
            return values;
        }

        @Override
        public boolean verify() {
            assertTrue(values.stream().noneMatch(ValueNode::isVector), "ScalarToVectorValueNode requires scalar inputs");
            return super.verify();
        }

        @Override
        public boolean isVector() {
            return true;
        }

        //        @Override
        public Node canonical(CanonicalizerTool tool) {
            // Do nothing if no inputs
            if (inputs().isEmpty()) {
                return this;
            }

            final Node first = inputs().first();

            // If all values are VectorToScalarValueNode, then we can delete this node and the input node
            final boolean allEqual = StreamSupport.stream(inputs().spliterator(), false).allMatch(first::equals);

            // Do nothing if not all inputs are equal and instance of VTS
            if (!allEqual || !(first instanceof VectorToScalarValueNode)) {
                return this;
            }

            final VectorToScalarValueNode firstVTS = (VectorToScalarValueNode) first;

            // What happens when we return null?
            replaceAtUsagesAndDelete(firstVTS.value);

            return null; // to delete the current node
        }
    }
}
