package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Represents an intra-procedural invoke handler.
 * It ignores methods in the sense that every {@link AnalysisMethod} is a black box,
 * and therefore produces the {@link AnalysisResult#ANALYSIS_FAILED}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public final class IntraAbsintInvokeHandler<Domain extends AbstractDomain<Domain>>
        extends AbsintInvokeHandler<Domain> {

    public IntraAbsintInvokeHandler(Domain initialDomain,
                                    AbstractInterpreter<Domain> abstractInterpreter,
                                    AnalysisContext analysisContext) {
        super(initialDomain, abstractInterpreter, analysisContext);
    }

    @Override
    public AnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput) {
        return AnalysisOutcome.error(AnalysisResult.ANALYSIS_FAILED);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        var logger = AbstractInterpretationLogger.getInstance();
        if (methodFilterManager.shouldSkipMethod(root)) {
            return;
        }

        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, abstractTransformer, analysisContext);
        AbstractState<Domain> abstractState = fixpointIterator.iterateUntilFixpoint();
        StructuredGraph graph = analysisContext.getMethodGraphCache().getMethodGraphMap().get(root);
        logger.printLabelledGraph(graph, root, abstractState);
        checkerManager.runCheckersOnSingleMethod(root, abstractState, graph);
    }
}
