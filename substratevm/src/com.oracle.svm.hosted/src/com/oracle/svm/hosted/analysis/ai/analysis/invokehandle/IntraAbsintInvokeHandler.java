package com.oracle.svm.hosted.analysis.ai.analysis.invokehandle;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analysis.InvokeAnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analysis.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analysis.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
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
                                    AnalysisContext<Domain> analysisContext) {
        super(initialDomain, abstractInterpreter, analysisContext);
    }

    @Override
    public InvokeAnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput) {
        return InvokeAnalysisOutcome.error(AnalysisResult.ANALYSIS_FAILED);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, abstractTransformer, analysisContext);
        AbstractState<Domain> abstractState = fixpointIterator.runFixpointIteration();
        StructuredGraph graph = analysisContext.getMethodGraphCache().getMethodGraphMap().get(root);

        if (graph == null) {
            return;
        }
        checkerManager.runCheckersOnSingleMethod(root, abstractState, graph);
    }
}
