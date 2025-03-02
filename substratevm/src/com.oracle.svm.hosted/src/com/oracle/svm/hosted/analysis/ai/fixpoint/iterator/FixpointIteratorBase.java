package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
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

    protected final Domain initialDomain;
    protected final TransferFunction<Domain> transferFunction;
    protected final IteratorPayload iteratorPayload;
    protected final ControlFlowGraph cfgGraph;
    protected final AbstractStateMap<Domain> abstractStateMap;
    protected final AbstractInterpretationLogger logger;

    protected FixpointIteratorBase(AnalysisMethod method,
                                   DebugContext debug,
                                   Domain initialDomain,
                                   TransferFunction<Domain> transferFunction,
                                   IteratorPayload iteratorPayload) {
        this.initialDomain = initialDomain;
        this.transferFunction = transferFunction;
        this.iteratorPayload = iteratorPayload;
        this.abstractStateMap = new AbstractStateMap<>(initialDomain);
        if (iteratorPayload.containsMethodGraph(method)) {
            this.cfgGraph = iteratorPayload.getMethodGraph().get(method);
        } else {
            this.cfgGraph = GraphUtils.getGraph(method, debug);
            iteratorPayload.addToMethodGraphMap(method, cfgGraph);
        }
        this.logger = AbstractInterpretationLogger.getInstance();
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
     * This analysisMethod should be called on the head of a cycle in each iteration.
     * Performs widen/join of post and pre-condition of the {@code node} according to the {@link IteratorPolicy}.
     * Has to be performed in order for the analysis to converge.
     *
     * @param node to extrapolate
     */
    protected void extrapolate(Node node) {
        var state = abstractStateMap.getState(node);
        int visitedAmount = abstractStateMap.getState(node).getVisitedCount();
        if (visitedAmount < iteratorPayload.getMaxJoinIterations()) {
            abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        } else {
            abstractStateMap.getPostCondition(node).widenWith(abstractStateMap.getPreCondition(node));
        }

        if (state.getVisitedCount() > iteratorPayload.getMaxWidenIterations() + iteratorPayload.getMaxJoinIterations()) {
            throw AnalysisError.shouldNotReachHere("Exceeded maxWidenIterations!" +
                    " Consider increasing the limit, or refactor your widening operator");
        }
    }
}
