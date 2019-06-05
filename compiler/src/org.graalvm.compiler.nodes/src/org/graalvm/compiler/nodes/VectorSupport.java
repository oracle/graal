package org.graalvm.compiler.nodes;

import jdk.vm.ci.meta.PrimitiveConstant;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeInputList;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.graph.spi.Canonicalizable;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.calc.FloatingNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.stream.StreamSupport;

public final class VectorSupport {
    private VectorSupport() { }

    // TODO: Use these for packing

    /**
     * This node is a placeholder node that is either removed by a corresponding pack (no-op)
     *  or replaced with nodes for each access to a vector element for the given value.
     *
     *  This node is not LIR lowerable and must be removed during canonicalization.
     */
    @NodeInfo
    public static final class VectorUnpackNode extends FloatingNode implements Canonicalizable {

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

        @Override
        public Node canonical(CanonicalizerTool tool) {
            if (usages().isEmpty()) {
                return null;
            }

            // if all usages are the same vector unpack node, let pack canonicalize
            final Node first = usages().first();
            final boolean allEqual = StreamSupport.stream(usages().spliterator(), false).allMatch(first::equals);

            if (allEqual && !(first instanceof VectorPackNode)) {
                return this;
            }

            // otherwise, split this packing node
            final List<Node> usages = usages().snapshot();
            for (Node usage : usages) {
                final VectorExtractNode extractNode = new VectorExtractNode(stamp, value);
                // this should be fine later == init with specific index
                // how do we figure out the index?
                graph().addWithoutUnique(extractNode);

                // TODO: force a single replacement until we have indices THIS IS REAL HACKY
                final int[] rc = { 0 };
                replaceAtMatchingUsages(extractNode, n -> n == usage && rc[0]++ == 0);
            }

            return null;
        }
    }

    /**
     * This node is a node whose input is a vector value and whose output is a scalar element from
     *  that vector.
     */
    @NodeInfo
    public static final class VectorExtractNode extends FloatingNode implements LIRLowerable {
        public static final NodeClass<VectorExtractNode> TYPE = NodeClass.create(VectorExtractNode.class);

        @Input private ValueNode value;

        public VectorExtractNode(Stamp stamp, ValueNode value) {
            this(TYPE, stamp, value);
        }

        private VectorExtractNode(NodeClass<? extends VectorExtractNode> c, Stamp stamp, ValueNode value) {
            super(TYPE, stamp);
            this.value = value;
        }

        public ValueNode value() {
            return value;
        }

        @Override
        public boolean verify() {
            assertTrue(value.isVector(), "VectorExtractNode requires a vector ValueNode input");
            return super.verify();
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            throw GraalError.unimplemented("vector extraction not yet implemented");
        }
    }

    @NodeInfo
    public static final class VectorPackNode extends FloatingNode implements Canonicalizable, LIRLowerable {

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

            replaceAtUsages(firstVTS.value);

            return null; // to delete the current node
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            // if all the values are constants
            final boolean allPrimitiveConstant =
                    values.stream().allMatch(x -> x.getStackKind().isPrimitive() && x.isConstant());

            if (!allPrimitiveConstant) {
                throw GraalError.shouldNotReachHere("Only primitive constants may be packed.");
            }

            // TODO: don't hardcode for integers
            final ByteBuffer byteBuffer = ByteBuffer.allocate(values.count() * 4);
            // TODO: don't hardcode for Intel
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);

            for (ValueNode node : values) {
                final PrimitiveConstant value = (PrimitiveConstant) ((ConstantNode) node).getValue();
                value.serialize(byteBuffer);
            }

            // TODO: don't hardcode for vector size
            final LIRKind kind = gen.getLIRGeneratorTool().toVectorKind(gen.getLIRGeneratorTool().getLIRKind(stamp), 4);
            gen.setResult(this, gen.getLIRGeneratorTool().emitPackConst(kind, byteBuffer));
        }
    }
}
