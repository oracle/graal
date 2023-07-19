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
 * {@link org.graalvm.compiler.nodes.calc.IntegerTestNode}.
 */
@NodeInfo(cycles = CYCLES_1, size = SIZE_1)
public class OpMaskTestNode extends BinaryOpLogicNode {
    public static final NodeClass<OpMaskTestNode> TYPE = NodeClass.create(OpMaskTestNode.class);

    private final boolean invertX;

    public OpMaskTestNode(ValueNode x, ValueNode y) {
        this(x, y, false);
    }

    public OpMaskTestNode(ValueNode x, ValueNode y, boolean invertX) {
        super(TYPE, x, y);
        this.invertX = invertX;
    }

    public boolean invertX() {
        return invertX;
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
