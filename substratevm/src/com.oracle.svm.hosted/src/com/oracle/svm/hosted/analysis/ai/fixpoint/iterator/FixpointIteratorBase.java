package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.BasicIteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.WideningOverflowPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisServices;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * Common functionality for fixpoint iterators.
 */
public abstract class FixpointIteratorBase<Domain extends AbstractDomain<Domain>> implements FixpointIterator<Domain> {

    protected final Domain initialDomain;
    protected final AbstractTransformer<Domain> abstractTransformer;
    protected final AnalysisContext analysisContext;
    protected final ControlFlowGraph cfgGraph;
    protected final AbstractState<Domain> abstractState;
    protected final AbstractInterpretationLogger logger;
    protected final AnalysisMethod analysisMethod;
    protected final GraphTraversalHelper graphTraversalHelper;
    protected final BasicIteratorContext iteratorContext;

    protected FixpointIteratorBase(AnalysisMethod method,
                                   Domain initialDomain,
                                   AbstractTransformer<Domain> abstractTransformer,
                                   AnalysisContext analysisContext) {
        this.logger = AbstractInterpretationLogger.getInstance();
        this.analysisMethod = method;
        this.initialDomain = initialDomain;
        this.abstractTransformer = abstractTransformer;
        this.analysisContext = analysisContext;
        var cache = analysisContext.getMethodGraphCache();
        if (cache.containsMethodGraph(method)) {
            this.cfgGraph = cache.getMethodGraph().get(method);
        } else {
            var services = AnalysisServices.getInstance();
            this.cfgGraph = services.getGraph(method);
            cache.addToMethodGraphMap(method, cfgGraph);
        }
        logger.exportGraphToJson(cfgGraph, analysisMethod, analysisMethod.getName() + ":CFG_BeforeAbsint");
        this.abstractState = new AbstractState<>(initialDomain, cfgGraph);
        this.graphTraversalHelper = new GraphTraversalHelper(cfgGraph, analysisContext.getIteratorPolicy().direction());
        this.iteratorContext = new BasicIteratorContext(graphTraversalHelper);
    }

    @Override
    public AbstractState<Domain> getAbstractState() {
        return abstractState;
    }

    public IteratorContext getIteratorContext() {
        return iteratorContext;
    }

    @Override
    public void clear() {
        logger.log("Clearing the abstract state", LoggerVerbosity.DEBUG);
        if (abstractState != null) abstractState.clear();
        if (iteratorContext != null) iteratorContext.reset();
    }

    protected void extrapolate(Node node) {
        var state = abstractState.getState(node);
        int visitedAmount = iteratorContext.getNodeVisitCount(node);
        logger.log("Before extrapolation: pre = " + abstractState.getPreCondition(node), LoggerVerbosity.DEBUG);
        logger.log("Before extrapolation: post = " + abstractState.getPostCondition(node), LoggerVerbosity.DEBUG);
        var newPre = abstractState.getPreCondition(node).copyOf();
        IteratorPolicy policy = analysisContext.getIteratorPolicy();

        if (visitedAmount < policy.maxJoinIterations()) {
            logger.log("Extrapolating (join) at visit " + visitedAmount + " for node: " + node, LoggerVerbosity.DEBUG);
            newPre.joinWith(abstractState.getPostCondition(node));
        } else {
            logger.log("Extrapolating (widen) at visit " + visitedAmount + " for node: " + node, LoggerVerbosity.DEBUG);
            newPre.widenWith(abstractState.getPostCondition(node));
        }

        logger.log("After extrapolation: newPre = " + newPre, LoggerVerbosity.DEBUG);
        abstractState.setPreCondition(node, newPre);
        iteratorContext.incrementNodeVisitCount(node);

        if (iteratorContext.getNodeVisitCount(node) > policy.maxWidenIterations() + policy.maxJoinIterations()) {
            logger.log("Extrapolation limit exceeded for node: " + node, LoggerVerbosity.INFO);
            if (policy.wideningOverflowPolicy() == WideningOverflowPolicy.ERROR) {
                throw AnalysisError.shouldNotReachHere("Exceeded maximum amount of extrapolating iterations. Consider increasing the limit, or refactor your widening/join operator");
            } else if (policy.wideningOverflowPolicy() == WideningOverflowPolicy.SET_TO_TOP) {
                state.getPreCondition().setToTop();
            }
        }
    }
}
