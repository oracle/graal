package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalyzerMetadata;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.BasicIteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.context.IteratorContext;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.WideningOverflowPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.SvmUtility;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

/**
 * This class provides the common functionality shared among different fixpoint iterator implementations.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public abstract class FixpointIteratorBase<
        Domain extends AbstractDomain<Domain>>
        implements FixpointIterator<Domain> {

    protected final Domain initialDomain;
    protected final AbstractTransformer<Domain> abstractTransformer;
    protected final AnalyzerMetadata analyzerMetadata;
    protected final ControlFlowGraph cfgGraph;
    protected final AbstractState<Domain> abstractState;
    protected final AbstractInterpretationLogger logger;
    protected final AnalysisMethod analysisMethod;
    protected final GraphTraversalHelper graphTraversalHelper;
    protected final BasicIteratorContext iteratorContext;

    protected FixpointIteratorBase(AnalysisMethod method,
                                   Domain initialDomain,
                                   AbstractTransformer<Domain> abstractTransformer,
                                   AnalyzerMetadata analyzerMetadata) {

        this.logger = AbstractInterpretationLogger.getInstance();
        this.analysisMethod = method;
        this.initialDomain = initialDomain;
        this.abstractTransformer = abstractTransformer;
        this.analyzerMetadata = analyzerMetadata;
        if (analyzerMetadata.containsMethodGraph(method)) {
            this.cfgGraph = analyzerMetadata.getMethodGraph().get(method);
        } else {
            var bbUtil = SvmUtility.getInstance();
            this.cfgGraph = bbUtil.getGraph(method);
            analyzerMetadata.addToMethodGraphMap(method, cfgGraph);
        }
        logger.exportGraphToJson(cfgGraph, analysisMethod, analysisMethod + ":CFG_BeforeAbsint");
        this.abstractState = new AbstractState<>(initialDomain, cfgGraph);
        this.graphTraversalHelper = new GraphTraversalHelper(cfgGraph, analyzerMetadata.getIteratorPolicy().direction());
        this.iteratorContext = new BasicIteratorContext(graphTraversalHelper);
    }

    @Override
    public AbstractState<Domain> getAbstractState() {
        return abstractState;
    }

    /**
     * Gets the iterator context that provides information to interpreters.
     *
     * @return the iterator context
     */
    public IteratorContext getIteratorContext() {
        return iteratorContext;
    }

    @Override
    public void clear() {
        logger.log("Clearing the abstract state", LoggerVerbosity.DEBUG);
        if (abstractState != null) {
            abstractState.clear();
        }
        if (iteratorContext != null) {
            iteratorContext.reset();
        }
    }

    /**
     * This method is called on the head of a cycle in each iteration.
     * Performs widen/join of post- and pre-condition of the {@code node} according to the {@link IteratorPolicy}.
     * It has to be performed in order for the analysis to converge.
     *
     * @param node to extrapolate
     */
    protected void extrapolate(Node node) {
        var state = abstractState.getState(node);
        int visitedAmount = iteratorContext.getNodeVisitCount(node);
        logger.log("Before extrapolation: pre = " + abstractState.getPreCondition(node), LoggerVerbosity.DEBUG);
        logger.log("Before extrapolation: post = " + abstractState.getPostCondition(node), LoggerVerbosity.DEBUG);
        var newPre = abstractState.getPreCondition(node).copyOf();

        if (visitedAmount < analyzerMetadata.getMaxJoinIterations()) {
            logger.log("Extrapolating (join) at visit " + visitedAmount + " for node: " + node, LoggerVerbosity.DEBUG);
            newPre.joinWith(abstractState.getPostCondition(node));
        } else {
            logger.log("Extrapolating (widen) at visit " + visitedAmount + " for node: " + node, LoggerVerbosity.DEBUG);
            newPre.widenWith(abstractState.getPostCondition(node));
        }

        logger.log("After extrapolation: newPre = " + newPre, LoggerVerbosity.DEBUG);
        abstractState.setPreCondition(node, newPre);

        iteratorContext.incrementNodeVisitCount(node);
        if (iteratorContext.getNodeVisitCount(node) > analyzerMetadata.getMaxWidenIterations() + analyzerMetadata.getMaxJoinIterations()) {
            logger.log("Extrapolation limit exceeded for node: " + node, LoggerVerbosity.INFO);
            if (analyzerMetadata.getIteratorPolicy().wideningOverflowPolicy() == WideningOverflowPolicy.ERROR) {
                throw AnalysisError.shouldNotReachHere("Exceeded maximum amount of extrapolating iterations." +
                        " Consider increasing the limit, or refactor your widening/join operator");
            } else if (analyzerMetadata.getIteratorPolicy().wideningOverflowPolicy() == WideningOverflowPolicy.SET_TO_TOP) {
                state.getPreCondition().setToTop();
            }
        }
    }
}
