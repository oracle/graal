package org.graalvm.compiler.loop;

import java.util.List;

import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

import jdk.vm.ci.meta.MetaAccessProvider;

public class VectorizationLoopPolicies implements LoopPolicies {
    @Override
    public boolean shouldPeel(LoopEx loop, ControlFlowGraph cfg, MetaAccessProvider metaAccess) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for peeling");
    }

    @Override
    public boolean shouldFullUnroll(LoopEx loop) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for full unrolling");
    }

    @Override
    public boolean shouldPartiallyUnroll(LoopEx loop) {
        return loop.loopBegin().getUnrollFactor() < 4;
    }

    @Override
    public boolean shouldTryUnswitch(LoopEx loop) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for trying unswitching");
    }

    @Override
    public boolean shouldUnswitch(LoopEx loop, List<ControlSplitNode> controlSplits) {
        throw GraalError.unimplemented("vectorization loop policies are not designed to be used for unswitching");
    }
}
