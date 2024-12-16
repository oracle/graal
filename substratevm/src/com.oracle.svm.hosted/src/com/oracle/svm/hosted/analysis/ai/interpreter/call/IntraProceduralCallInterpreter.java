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

/**
 * Represents a context insensitive call interpreter.
 * It ignores function calls except for direct native calls.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public final class IntraProceduralCallInterpreter<Domain extends AbstractDomain<Domain>>
        implements CallInterpreter<Domain> {

    @Override
    public void execInvoke(InvokeNode invokeNode,
                           AbstractStateMap<Domain> abstractStateMap,
                           SummaryCache<Domain> summaryCache,
                           TransferFunction<Domain> transferFunction,
                           Domain initialDomain,
                           IteratorPolicy policy,
                           DebugContext debug) {

        /* If the method is native java method, execute it */
        if (invokeNode.callTarget().targetMethod().isDefault()) {
            var callTargetCfg = new ControlFlowGraphBuilder(invokeNode.callTarget().graph()).build();
            switch (policy.strategy()) {
                case CONCURRENT:
                    var concurrentIterator = new ConcurrentWpoFixpointIterator<>(callTargetCfg, policy, transferFunction, initialDomain, debug);
                    concurrentIterator.iterateUntilFixpoint();
                case SEQUENTIAL:
                    var sequentialIterator = new SequentialWtoFixpointIterator<>(callTargetCfg, policy, transferFunction, initialDomain, debug);
                    sequentialIterator.iterateUntilFixpoint();
            }
        }
        abstractStateMap.setPostCondition(invokeNode, initialDomain);
        /* Otherwise, do nothing */
    }
}
