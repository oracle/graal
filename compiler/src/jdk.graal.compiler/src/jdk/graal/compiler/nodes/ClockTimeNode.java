package jdk.graal.compiler.nodes;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_2;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_32;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_1;

import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;

/**
 * The emits a call to RDTSC.
 */
// @formatter:off
@NodeInfo(cycles = CYCLES_32,
          cyclesRationale = "",
          size = SIZE_1)
// @formatter:on
public final class ClockTimeNode extends FixedWithNextNode implements LIRLowerable {

    public static final NodeClass<ClockTimeNode> TYPE = NodeClass.create(ClockTimeNode.class);


    public ClockTimeNode() {
        super(TYPE, StampFactory.forInteger(64));

    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        // the stamp for this need need to be checked
        generator.setResult(this, generator.getLIRGeneratorTool().emitTSC());
    }
}