package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtils;
import jdk.graal.compiler.debug.DebugContext;
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

    protected final ControlFlowGraph cfgGraph;
    protected final IteratorPolicy policy;
    protected final Domain initialDomain;
    protected final DebugContext debug;
    protected final AbstractInterpretationLogger logger;
    protected final AbstractStateMap<Domain> abstractStateMap;
    protected final TransferFunction<Domain> transferFunction;

    protected FixpointIteratorBase(AnalysisContext<Domain> payload, TransferFunction<Domain> transferFunction) {
        this.debug = payload.getDebugContext();
        this.cfgGraph = GraphUtils.getGraph(payload.getRoot(), debug);
        this.policy = payload.getIteratorPolicy();
        this.initialDomain = payload.getInitialDomain();
        this.logger = payload.getLogger();
        this.abstractStateMap = new AbstractStateMap<>(initialDomain);
        this.transferFunction = transferFunction;
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
    public IteratorPolicy getPolicy() {
        return policy;
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