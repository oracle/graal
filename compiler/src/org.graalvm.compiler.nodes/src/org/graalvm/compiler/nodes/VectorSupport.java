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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public final class VectorSupport {
    private VectorSupport() { }

    /**
     * This node is a node whose input is a vector value and whose output is a scalar element from
     *  that vector.
     */
    @NodeInfo(nameTemplate = "VectorExtract@{p#index/s}")
    public static final class VectorExtractNode extends FloatingNode implements LIRLowerable {
        public static final NodeClass<VectorExtractNode> TYPE = NodeClass.create(VectorExtractNode.class);

        @Input private ValueNode vectorValue;
        private final int index;

        public VectorExtractNode(Stamp stamp, ValueNode vectorValue, int index) {
            this(TYPE, stamp, vectorValue, index);
        }

        private VectorExtractNode(NodeClass<? extends VectorExtractNode> c, Stamp stamp, ValueNode vectorValue, int index) {
            super(TYPE, stamp);
            this.vectorValue = vectorValue;
            this.index = index;
        }

        public ValueNode value() {
            return vectorValue;
        }

        public int index() {
            return index;
        }

        @Override
        public boolean verify() {
            assertTrue(vectorValue.isVector(), "VectorExtractNode requires a vector ValueNode input");
            return super.verify();
        }

        @Override
        public void generate(NodeLIRBuilderTool gen) {
            throw GraalError.unimplemented("vector extraction not yet implemented");
        }
    }

    @NodeInfo(nameTemplate = "VectorPack")
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

            // All inputs need to be VectorExtractNode
            // All VectorExtractNode have exactly one usage to be deleted
            // All inputs need to refer to the same vector
            // All inputs need to be in increasing order, starting at 0
            // TODO: Input count needs to be the same as the type of the vector.
            //       there isn't yet a way to express this.

            // Obtain the vector value
            if (!(inputs().first() instanceof VectorExtractNode)) {
                return this;
            }
            final ValueNode vectorValue = ((VectorExtractNode) inputs().first()).value();

            // Ensure that all inputs refer to the same vector
            final boolean allEqualVector = StreamSupport.stream(inputs().spliterator(), false)
                    .allMatch(x -> x instanceof VectorExtractNode && ((VectorExtractNode) x).value() == vectorValue);
            if (!allEqualVector) {
                return this;
            }

            final List<VectorExtractNode> nodes = inputs().snapshot().stream()
                    .map(x -> (VectorExtractNode) x)
                    .sorted(Comparator.comparingInt(VectorExtractNode::index))
                    .collect(Collectors.toList());

            // Ensure presence of zero
            if (nodes.get(0).index() != 0) {
                return this;
            }

            // Ensure there are no gaps
            int lastIndex = 0;
            for (int i = 1; i < nodes.size(); i++) {
                if (nodes.get(i).index() - lastIndex > 1) {
                    return this;
                }
                lastIndex = i;
            }

            replaceAtUsages(vectorValue);
            for (VectorExtractNode node : nodes) {
                if (node.getUsageCount() == 1 && node.getUsageAt(0) == this) {
                    node.safeDelete();
                }
            }

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
