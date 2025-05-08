package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformers;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.BigBangUtil;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Provides a base implementation for methods used both by
 * {@link WpoFixpointIterator} and {@link WtoFixpointIterator}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public abstract class FixpointIteratorBase<
        Domain extends AbstractDomain<Domain>>
        implements FixpointIterator<Domain> {

    protected final Domain initialDomain;
    protected final AbstractTransformers<Domain> abstractTransformers;
    protected final IteratorPayload iteratorPayload;
    protected final ControlFlowGraph cfgGraph;
    protected final AbstractState<Domain> abstractState;
    protected final AbstractInterpretationLogger logger;
    protected final AnalysisMethod analysisMethod;
    protected final GraphTraversalHelper graphTraversalHelper;

    protected FixpointIteratorBase(AnalysisMethod method,
                                   Domain initialDomain,
                                   AbstractTransformers<Domain> abstractTransformers,
                                   IteratorPayload iteratorPayload) {

        this.logger = AbstractInterpretationLogger.getInstance();
        this.analysisMethod = method;
        this.initialDomain = initialDomain;
        this.abstractTransformers = abstractTransformers;
        this.iteratorPayload = iteratorPayload;
        if (iteratorPayload.containsMethodGraph(method)) {
            this.cfgGraph = iteratorPayload.getMethodGraph().get(method);
        } else {
            var bbUtil = BigBangUtil.getInstance();
            this.cfgGraph = bbUtil.getGraph(method);
            iteratorPayload.addToMethodGraphMap(method, cfgGraph);
        }
        this.abstractState = new AbstractState<>(initialDomain, cfgGraph);
        graphTraversalHelper = new GraphTraversalHelper(cfgGraph, iteratorPayload.getIteratorPolicy().direction());
    }

    @Override
    public AbstractState<Domain> getAbstractState() {
        return abstractState;
    }

    @Override
    public void clear() {
        logger.log("Clearing the abstract state", LoggerVerbosity.DEBUG);
        if (abstractState != null) {
            abstractState.clear();
        }
    }

    /**
     * This method is called on the head of a cycle in each iteration.
     * Performs widen/join of post and pre-condition of the {@code node} according to the {@link IteratorPolicy}.
     * Has to be performed in order for the analysis to converge.
     *
     * @param node to extrapolate
     */
    protected void extrapolate(Node node) {
        var state = abstractState.getState(node);
        int visitedAmount = abstractState.getState(node).getVisitedCount();
        if (visitedAmount < iteratorPayload.getMaxJoinIterations()) {
            abstractState.setPreCondition(node, abstractState.getPostCondition(node).join(abstractState.getPreCondition(node)));
        } else {
            abstractState.setPreCondition(node, abstractState.getPostCondition(node).widen(abstractState.getPreCondition(node)));
        }

        abstractState.getState(node).incrementVisitedCount();
        if (state.getVisitedCount() > iteratorPayload.getMaxWidenIterations() + iteratorPayload.getMaxJoinIterations()) {
            throw AnalysisError.shouldNotReachHere("Exceeded maximum amount of extrapolating iterations." +
                    " Consider increasing the limit, or refactor your widening/join operator");
        }
    }
}
