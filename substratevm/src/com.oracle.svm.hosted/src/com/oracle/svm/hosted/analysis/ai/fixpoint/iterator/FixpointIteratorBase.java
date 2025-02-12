package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtils;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Provides a base implementation for methods used both by
 * {@link ConcurrentWpoFixpointIterator} and {@link SequentialWtoFixpointIterator}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public abstract class FixpointIteratorBase<
        Domain extends AbstractDomain<Domain>>
        implements FixpointIterator<Domain> {

    protected final AnalysisPayload<Domain> payload;
    protected final ControlFlowGraph cfgGraph;
    protected final AbstractStateMap<Domain> abstractStateMap;

    protected FixpointIteratorBase(AnalysisPayload<Domain> payload) {
        this.payload = payload;
        this.cfgGraph = GraphUtils.getGraph(payload.getRoot(), payload.getDebugContext());
        this.abstractStateMap = new AbstractStateMap<>(payload.getInitialDomain());
    }

    /* This ctor is used when we have the graph for a method ready (we analyzed it in the past) */
    protected FixpointIteratorBase(AnalysisPayload<Domain> payload, ControlFlowGraph cfgGraph) {
        this.payload = payload;
        this.cfgGraph = cfgGraph;
        this.abstractStateMap = new AbstractStateMap<>(payload.getInitialDomain());
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
    public AbstractStateMap<Domain> getAbstractStateMap() {
        return abstractStateMap;
    }

    @Override
    public void clear() {
        if (abstractStateMap != null) {
            abstractStateMap.clear();
        }
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
        if (visitedAmount < payload.getIteratorPolicy().maxJoinIterations()) {
            abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        } else {
            abstractStateMap.getPostCondition(node).widenWith(abstractStateMap.getPreCondition(node));
        }

        if (state.getVisitedCount() > payload.getIteratorPolicy().maxWidenIterations() + payload.getIteratorPolicy().maxJoinIterations()) {
            throw AnalysisError.shouldNotReachHere("Exceeded maxWidenIterations!" +
                    " Consider increasing the limit, or refactor your widening operator");
        }
    }
}