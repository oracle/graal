package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.fixpoint.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Provides a base implementation for methods used both by
 * {@link ConcurrentWpoFixpointIterator} and {@link SequentialWtoFixpointIterator}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public abstract class FixpointIteratorBase<Domain extends AbstractDomain<Domain>> implements FixpointIterator<Domain> {

    protected final ControlFlowGraph cfgGraph;
    protected final com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy policy;
    protected final TransferFunction<Domain> transferFunction;
    protected final Domain initialDomain;
    protected final AbstractStateMap<Domain> abstractStateMap;
    protected final SummaryCache<Domain> summaryCache;
    protected final DebugContext debug;

    protected FixpointIteratorBase(ControlFlowGraph cfgGraph,
                                   com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy policy,
                                   TransferFunction<Domain> transferFunction,
                                   Domain initialDomain,
                                   DebugContext debug) {
        this.cfgGraph = cfgGraph;
        this.policy = policy;
        this.transferFunction = transferFunction;
        this.initialDomain = initialDomain;
        this.abstractStateMap = new AbstractStateMap<>(initialDomain);
        this.summaryCache = new SummaryCache<>();
        this.debug = debug;
    }

    protected FixpointIteratorBase(ControlFlowGraph cfgGraph,
                                   com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy policy,
                                   TransferFunction<Domain> transferFunction,
                                   Domain initialDomain,
                                   AbstractStateMap<Domain> abstractStateMap,
                                   SummaryCache<Domain> summaryCache,
                                   DebugContext debug) {
        this.cfgGraph = cfgGraph;
        this.policy = policy;
        this.transferFunction = transferFunction;
        this.initialDomain = initialDomain;
        this.abstractStateMap = abstractStateMap;
        this.summaryCache = summaryCache;
        this.debug = debug;
    }


    @Override
    public void clear() {
        if (abstractStateMap != null) {
            abstractStateMap.clear();
        }
    }

    @Override
    public Domain getPreCondition(Node node) {
        return abstractStateMap.getPreCondition(node);
    }

    @Override
    public Domain getPostCondition(Node node) {
        return abstractStateMap.getPostCondition(node);
    }

    @Override
    public com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy getPolicy() {
        return policy;
    }

    @Override
    public SummaryCache<Domain> getFixpointCache() {
        return summaryCache;
    }

    @Override
    public AbstractStateMap<Domain> getAbstractStateMap() {
        return abstractStateMap;
    }

    protected void setPrecondition(Node node, Domain domain) {
        abstractStateMap.setPrecondition(node, domain);
    }

    /**
     * This method should be called on the head of a cycle in each iteration.
     * Performs widen/join of post and pre-condition of the {@code node} according to the {@link IteratorPolicy}.
     * Has to be performed in order for the analysis to converge.
     *
     * @param node to extrapolate
     */
    protected void extrapolate(Node node) {
        var state = abstractStateMap.getState(node);
        int visitedAmount = abstractStateMap.getState(node).getVisitedCount();
        if (visitedAmount < policy.maxJoinIterations()) {
            abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        } else {
            abstractStateMap.getPostCondition(node).widenWith(abstractStateMap.getPreCondition(node));
        }

        if (state.getVisitedCount() > policy.maxWidenIterations() + policy.maxJoinIterations()) {
            throw AnalysisError.shouldNotReachHere("Exceeded maxWidenIterations!" +
                    " Consider increasing the limit, or refactor your widening operator");
        }
    }
}