package org.graalvm.compiler.nodes.calc;

import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.LIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

@NodeInfo(shortName = "[+]")
public class VectorAddNode extends ValueNode implements LIRLowerable {

    public static final NodeClass<VectorAddNode> TYPE = NodeClass.create(VectorAddNode.class);

    @Input protected ValueNode x;
    @Input protected ValueNode y;

    // TODO: Improve -- make generic like Arithmetic nodes for scalar values, or make two compatible

    public VectorAddNode(ValueNode x, ValueNode y) {
        this(TYPE, x.stamp(), x, y);
    }

    protected VectorAddNode(NodeClass<? extends VectorAddNode> c, Stamp stamp, ValueNode x, ValueNode y) {
        super(c, stamp);
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean verify() {
        assertTrue(x.isVector(), "[+] x input needs to be vector");
        assertTrue(y.isVector(), "[+] y input needs to be vector");
        return super.verify();
    }

    @Override
    public boolean isVector() {
        return true;
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        gen.setResult(this, gen.getLIRGeneratorTool().getArithmetic().emitVectorAdd(gen.operand(x), gen.operand(y), false));
        // TODO(nvangerow): Implement
    }
}
