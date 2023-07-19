package jdk.graal.compiler.nodes.calc;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.BinaryOpLogicNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.vm.ci.meta.TriState;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_1;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

/**
 * This node will perform a "test" operation on its arguments similar to
 * {@link OpMaskTestNode} but using bitwise OR on its inputs instead
 * of AND. The field "allZeros" selects if the operation tests for all ones or all zeros.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class OpMaskOrTestNode extends BinaryOpLogicNode {
    public static final NodeClass<OpMaskOrTestNode> TYPE = NodeClass.create(OpMaskOrTestNode.class);

    private final boolean allZeros;

    public OpMaskOrTestNode(ValueNode x, ValueNode y, boolean allZeros) {
        super(TYPE, x, y);
        this.allZeros = allZeros;
    }

    public boolean allZeros() {
        return allZeros;
    }

    @Override
    public Stamp getSucceedingStampForX(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public Stamp getSucceedingStampForY(boolean negated, Stamp xStamp, Stamp yStamp) {
        return null;
    }

    @Override
    public TriState tryFold(Stamp xStamp, Stamp yStamp) {
        return TriState.UNKNOWN;
    }

    @Override
    public ValueNode canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY) {
        return this;
    }
}
