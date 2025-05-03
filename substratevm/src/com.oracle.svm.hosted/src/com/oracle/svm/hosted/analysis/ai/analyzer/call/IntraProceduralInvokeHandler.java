package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents an intra-procedural invoke handler.
 * It ignores methods in the sense that every {@link AnalysisMethod} is a black box,
 * and therefore produces the {@link AnalysisResult#ANALYSIS_FAILED}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public final class IntraProceduralInvokeHandler<Domain extends AbstractDomain<Domain>>
        extends BaseInvokeHandler<Domain> {

    public IntraProceduralInvokeHandler(Domain initialDomain,
                                        NodeInterpreter<Domain> nodeInterpreter,
                                        CheckerManager checkerManager,
                                        AnalysisMethodFilterManager methodFilterManager,
                                        IteratorPayload iteratorPayload) {
        super(initialDomain, nodeInterpreter, checkerManager, methodFilterManager, iteratorPayload);
    }

    @Override
    public AnalysisOutcome<Domain> handleInvoke(Invoke invoke, Node invokeNode, AbstractState<Domain> callerState) {
        /* Intra-procedural call handling is not supported, this means that we treat all calls to methods as black box and don't analyze them further */
        return AnalysisOutcome.error(AnalysisResult.ANALYSIS_FAILED);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        if (methodFilterManager.shouldSkipMethod(root)) {
            return;
        }
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, transferFunction, iteratorPayload);
        AbstractState<Domain> abstractState = fixpointIterator.iterateUntilFixpoint();
        checkerManager.runCheckers(root, abstractState);
    }
}
