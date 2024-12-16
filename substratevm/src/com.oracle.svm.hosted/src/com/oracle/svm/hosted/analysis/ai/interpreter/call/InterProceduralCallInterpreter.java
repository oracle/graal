package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;

public final class InterProceduralCallInterpreter<
        Domain extends AbstractDomain<Domain>>
        implements CallInterpreter<Domain> {

    /* TODO implement summaries */
    @Override
    public void execInvoke(InvokeNode invokeNode,
                           AbstractStateMap<Domain> abstractStateMap,
                           SummaryCache<Domain> summaryCache,
                           TransferFunction<Domain> transferFunction,
                           Domain initialDomain,
                           IteratorPolicy policy,
                           DebugContext debug) {

        var callTargetCfg = new ControlFlowGraphBuilder(invokeNode.callTarget().graph()).build();
        switch (policy.strategy()) {
            case CONCURRENT:
                var concurrentIterator = new ConcurrentWpoFixpointIterator<>(callTargetCfg, policy, transferFunction, initialDomain, debug);
                concurrentIterator.iterateUntilFixpoint();
                break;
            case SEQUENTIAL:
                var sequentialIterator = new SequentialWtoFixpointIterator<>(callTargetCfg, policy, transferFunction, initialDomain, debug);
                sequentialIterator.iterateUntilFixpoint();
                break;
        }

    }
}
