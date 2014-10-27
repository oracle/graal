package com.oracle.graal.truffle.debug;

import static com.oracle.graal.truffle.TruffleCompilerOptions.*;

import java.util.*;

import com.oracle.graal.truffle.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.*;

public class TraceSplittingListener extends AbstractDebugCompilationListener {

    private TraceSplittingListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TraceTruffleSplitting.getValue()) {
            runtime.addCompilationListener(new TraceSplittingListener());
        }
    }

    private int splitCount;

    @Override
    public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
        OptimizedCallTarget callTarget = callNode.getCallTarget();
        String label = String.format("split %3s-%-4s-%-4s ", splitCount++, callNode.getCurrentCallTarget().getCloneIndex(), callNode.getCallCount());
        AbstractDebugCompilationListener.log(0, label, callTarget.toString(), callTarget.getDebugProperties());

        if (TruffleSplittingNew.getValue()) {
            Map<TruffleStamp, OptimizedCallTarget> splitTargets = callTarget.getSplitVersions();
            logProfile(callTarget.getArgumentStamp(), callTarget);
            for (TruffleStamp profile : splitTargets.keySet()) {
                logProfile(profile, splitTargets.get(profile));
            }
        }
    }

    private static void logProfile(TruffleStamp stamp, OptimizedCallTarget target) {
        String id = String.format("@%8h %s", target.hashCode(), target.getSourceCallTarget() == null ? "orig." : "split");
        OUT.printf("%16s%-20sCallers: %3d, Nodes:%10s %s%n", "", id, target.getKnownCallSiteCount(), //
                        String.format("%d (%d/%d)", count(target, NodeCost.MONOMORPHIC), count(target, NodeCost.POLYMORPHIC), count(target, NodeCost.MEGAMORPHIC)), stamp);
    }

    private static int count(OptimizedCallTarget target, final NodeCost otherCost) {
        return NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == otherCost;
            }
        });
    }

}
