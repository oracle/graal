package com.oracle.graal.truffle;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.NodeUtil.*;

public class DefaultTruffleSplittingStrategyNew implements TruffleSplittingStrategy {
    private static int splitChangeCount;

    private final int splitStart;
    private final OptimizedDirectCallNode call;
    private final boolean splittingEnabled;
    private boolean splittingForced;
    private TruffleStamp argumentStamp;

    public DefaultTruffleSplittingStrategyNew(OptimizedDirectCallNode call) {
        this.call = call;
        this.splitStart = TruffleCompilerOptions.TruffleSplittingStartCallCount.getValue();
        this.splittingEnabled = isSplittingEnabled();
        this.argumentStamp = DefaultTruffleStamp.getInstance();
        if (TruffleCompilerOptions.TruffleSplittingAggressive.getValue()) {
            splittingForced = true;
        }
    }

    private boolean isSplittingEnabled() {
        if (!TruffleCompilerOptions.TruffleSplitting.getValue()) {
            return false;
        }
        if (!call.isSplittable()) {
            return false;
        }
        if (TruffleCompilerOptions.TruffleSplittingAggressive.getValue()) {
            return true;
        }
        int size = OptimizedCallUtils.countNonTrivialNodes(call.getCallTarget(), false);
        if (size > TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
            return false;
        }
        return true;
    }

    public void forceSplitting() {
        splittingForced = true;
    }

    public void beforeCall(Object[] arguments) {
        newSplitting(arguments);
    }

    public void afterCall(Object returnValue) {
    }

    private void newSplitting(Object[] arguments) {
        CompilerAsserts.neverPartOfCompilation();
        OptimizedCallTarget currentTarget = call.getCurrentCallTarget();

        if (splittingForced) {
            if (!call.isSplit()) {
                call.installSplitCallTarget(currentTarget.split());
            }
            return;
        }

        TruffleStamp oldStamp = argumentStamp;
        TruffleStamp newStamp = oldStamp;
        if (!oldStamp.isCompatible(arguments)) {
            newStamp = oldStamp.joinValue(arguments);
            assert newStamp != oldStamp;
        }

        int calls = call.getCallCount();

        if (oldStamp != newStamp || calls == splitStart || !call.getCurrentCallTarget().getArgumentStamp().equals(newStamp)) {
            currentTarget = runSplitIteration(oldStamp, newStamp, calls);
            currentTarget.mergeArgumentStamp(newStamp);
            argumentStamp = newStamp;
            assert call.getCurrentCallTarget().getArgumentStamp().equals(newStamp);
        }
    }

    private OptimizedCallTarget runSplitIteration(TruffleStamp oldProfile, TruffleStamp newProfile, int calls) {
        OptimizedCallTarget currentTarget = call.getCurrentCallTarget();
        if (!splittingEnabled || calls < splitStart) {
            return currentTarget;
        }

        OptimizedCallTarget target = call.getCallTarget();
        Map<TruffleStamp, OptimizedCallTarget> profiles = target.getSplitVersions();
        OptimizedCallTarget newTarget = currentTarget;

        boolean split = false;
        if (!currentTarget.getArgumentStamp().equals(newProfile)) {
            if (target.getArgumentStamp().equals(newProfile)) {
                // the original target is compatible again.
                // -> we can use the original call target.
                newTarget = target;
            } else if (currentTarget.getKnownCallSiteCount() == 1 && currentTarget.getArgumentStamp().equals(oldProfile)) {
                // we are the only caller + the profile is not polluted by other call sites
                // -> reuse the currentTarget but update the profile if necessary
                newTarget = currentTarget;
                if (currentTarget.getSplitSource() != null) {
                    profiles.remove(oldProfile);
                    profiles.put(newProfile, newTarget);
                }
            } else {
                newTarget = profiles.get(newProfile);
                if (newTarget == null) {
                    // in case no compatible target was found we need to split
                    newTarget = target.split();
                    profiles.put(newProfile, newTarget);
                    split = true;
                }
            }
        }

        call.installSplitCallTarget(newTarget);

        if (split && TruffleCompilerOptions.TraceTruffleSplitting.getValue()) {
            traceSplit(currentTarget.getSplitSource() != null ? oldProfile : currentTarget.getArgumentStamp(), newProfile);
        }

        cleanup(currentTarget);
        return newTarget;
    }

    private void traceSplit(TruffleStamp oldStamp, TruffleStamp newStamp) {
        OptimizedCallTarget callTarget = call.getCallTarget();
        Map<TruffleStamp, OptimizedCallTarget> splitTargets = callTarget.getSplitVersions();
        String label = String.format("split %3s-%-4s-%-4s ", splitChangeCount++, call.getCurrentCallTarget().getSplitIndex(), call.getCallCount());
        OptimizedCallTargetLog.log(0, label, callTarget.toString(), callTarget.getDebugProperties());
        logProfile(callTarget.getArgumentStamp(), callTarget, oldStamp, newStamp);
        for (TruffleStamp profile : splitTargets.keySet()) {
            logProfile(profile, splitTargets.get(profile), oldStamp, newStamp);
        }
    }

    private static void logProfile(TruffleStamp stamp, OptimizedCallTarget target, TruffleStamp oldStamp, TruffleStamp newStamp) {
        String id = String.format("@%8h %s", target.hashCode(), target.getSplitSource() == null ? "orig." : "split");
        String plusMinus = stamp.equals(newStamp) ? "+ " : (stamp.equals(oldStamp) ? "- " : "");
        System.out.printf("%16s%-20sCallers: %3d, Nodes:%10s %s%n", plusMinus, id, target.getKnownCallSiteCount(), //
                        String.format("%d (%d/%d)", count(target, NodeCost.MONOMORPHIC), count(target, NodeCost.POLYMORPHIC), count(target, NodeCost.MEGAMORPHIC)),//
                        stamp);
    }

    private static int count(OptimizedCallTarget target, final NodeCost otherCost) {
        return NodeUtil.countNodes(target.getRootNode(), new NodeCountFilter() {
            public boolean isCounted(Node node) {
                return node.getCost() == otherCost;
            }
        });
    }

    private static void cleanup(OptimizedCallTarget currentTarget) {
        if (currentTarget.getKnownCallSiteCount() == 0 && currentTarget.getSplitSource() != null) {
            OptimizedCallTarget removed = currentTarget.getSplitSource().getSplitVersions().remove(currentTarget.getArgumentStamp());
            if (removed != null) {
                disposeTarget(removed);
            }
        }
    }

    private static void disposeTarget(OptimizedCallTarget removed) {
        removed.getRootNode().accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode call = ((OptimizedDirectCallNode) node);
                    call.getCurrentCallTarget().decrementKnownCallSites();
                    cleanup(call.getCurrentCallTarget());
                }
                return true;
            }
        });

    }

}
