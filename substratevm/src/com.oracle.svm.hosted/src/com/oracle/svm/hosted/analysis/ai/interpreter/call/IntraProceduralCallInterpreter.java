package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.FixpointCache;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.InvokeNode;

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
                           FixpointCache<Domain> fixpointCache,
                           TransferFunction<Domain> transferFunction,
                           Domain initialDomain,
                           IteratorPolicy policy,
                           DebugContext debug) {

        CallTargetNode callTargetNode = invokeNode.callTarget();

        /* If the method is native java method, execute it */
        if (callTargetNode.targetMethod().isDefault()) {
            /* TODO get structured Graph of the method */
            switch (policy.strategy()) {
                case CONCURRENT:
                    var concurrentIterator = new ConcurrentWpoFixpointIterator<>(null, policy, transferFunction, initialDomain, debug);
                    concurrentIterator.iterateUntilFixpoint();
                    break;
                case SEQUENTIAL:
                    var sequentialIterator = new SequentialWtoFixpointIterator<>(null, policy, transferFunction, initialDomain, debug);
                    transferFunction.analyzeNode(invokeNode, abstractStateMap, fixpointCache);
                    break;
            }
        }

        /* Otherwise, do nothing */
    }
}
