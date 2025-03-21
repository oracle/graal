package jdk.graal.compiler.lir.gen;

import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahLoadBarrierNode;
import jdk.vm.ci.meta.Value;

public interface ShenandoahBarrierSetLIRGeneratorTool extends BarrierSetLIRGeneratorTool {
    Value emitLoadReferenceBarrier(LIRGeneratorTool tool, Value obj, Value address, ShenandoahLoadBarrierNode.ReferenceStrength strength, boolean narrow);
}
