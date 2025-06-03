package jdk.graal.compiler.nodes.gc.shenandoah;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LogicCompareAndSwapNode;
import jdk.graal.compiler.nodes.memory.address.AddressNode;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.Value;
import org.graalvm.word.LocationIdentity;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_16;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_64;

/**
 * Replaces LogicCompareAndSwapNode for Shenandoah on reference-CAS.
 * Shenandoah requires some special treatment of reference-CAS to avoid
 * false negatives because from- and to-space references may not match,
 * even though they point to the same object.
 */
@NodeInfo(cycles = CYCLES_16, size = SIZE_64)
public class ShenandoahLogicCompareAndSwapNode extends LogicCompareAndSwapNode {
    public static final NodeClass<ShenandoahLogicCompareAndSwapNode> TYPE = NodeClass.create(ShenandoahLogicCompareAndSwapNode.class);

    public ShenandoahLogicCompareAndSwapNode(AddressNode address, ValueNode expectedValue, ValueNode newValue, LocationIdentity location, BarrierType barrierType, MemoryOrderMode memoryOrder) {
        super(TYPE, address, expectedValue, newValue, location, barrierType, memoryOrder);
    }

    @Override
    public void generate(NodeLIRBuilderTool gen) {
        assert getNewValue().stamp(NodeView.DEFAULT).isCompatible(getExpectedValue().stamp(NodeView.DEFAULT));
        assert !this.canDeoptimize();
        LIRGeneratorTool tool = gen.getLIRGeneratorTool();
        ShenandoahBarrierSetLIRGeneratorTool shenandoahTool = (ShenandoahBarrierSetLIRGeneratorTool) gen.getLIRGeneratorTool().getBarrierSet();
        LIRKind resultKind = tool.getLIRKind(stamp(NodeView.DEFAULT));
        Value trueResult = tool.emitConstant(resultKind, JavaConstant.TRUE);
        Value falseResult = tool.emitConstant(resultKind, JavaConstant.FALSE);
        Value result = shenandoahTool.emitLogicCompareAndSwap(tool, tool.getLIRKind(getAccessStamp(NodeView.DEFAULT)), gen.operand(getAddress()), gen.operand(getExpectedValue()), gen.operand(getNewValue()), trueResult, falseResult, memoryOrder);
        gen.setResult(this, result);
    }
}
