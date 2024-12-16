package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.InvokeNode;

/**
 * Represents a function call interpreter.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface CallInterpreter<Domain extends AbstractDomain<Domain>> {

    /**
     * Executes the given invoke node.
     * We need to be able to create a new fixpointIterator in place to iterate over the function graph
     * TODO: this is pretty ugly, consult this how to potentially rewrite this
     *
     * @param invokeNode             the invoke node to execute
     * @param abstractStateMap       the abstract state map
     * @param summaryCache          the fixpoint cache
     * @param domainTransferFunction the domain transfer function
     * @param initialDomain          the initial domain
     * @param policy                 the iterator policy
     * @param debug                  the debug context
     */
    void execInvoke(InvokeNode invokeNode,
                    AbstractStateMap<Domain> abstractStateMap,
                    SummaryCache<Domain> summaryCache,
                    TransferFunction<Domain> domainTransferFunction,
                    Domain initialDomain,
                    IteratorPolicy policy,
                    DebugContext debug);
}
