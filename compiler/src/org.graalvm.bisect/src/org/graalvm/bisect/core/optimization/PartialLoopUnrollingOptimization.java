package org.graalvm.bisect.core.optimization;

public class PartialLoopUnrollingOptimization extends OptimizationImpl {

    public PartialLoopUnrollingOptimization(Integer bci) {
        super(bci);
    }

    @Override
    public String getDescription() {
        return "Loop Unrolling";
    }
}
