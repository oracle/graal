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
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents an intra-procedural call interpreter.
 * It ignores methods in the sense that every analysisMethod is a black box,
 * and therefore produces the `top` value of the used abstract domain.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public final class IntraProceduralCallHandler<Domain extends AbstractDomain<Domain>>
        extends BaseCallHandler<Domain> {

    public IntraProceduralCallHandler(Domain initialDomain,
                                      NodeInterpreter<Domain> nodeInterpreter,
                                      CheckerManager checkerManager,
                                      AnalysisMethodFilterManager methodFilterManager,
                                      IteratorPayload iteratorPayload) {
        super(initialDomain, nodeInterpreter, checkerManager, methodFilterManager, iteratorPayload);
    }

    @Override
    public AnalysisOutcome<Domain> handleCall(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> callerStateMap) {
        /* Intra-procedural call handling is not supported -> we treat all calls to methods as black box and don't analyze them further */
        return AnalysisOutcome.error(AnalysisResult.ANALYSIS_FAILED);
    }

    @Override
    public void handleRootCall(AnalysisMethod root, DebugContext debug) {
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, debug, initialDomain, transferFunction, iteratorPayload);
        AbstractStateMap<Domain> abstractStateMap = fixpointIterator.iterateUntilFixpoint();
        checkerManager.runCheckers(root.wrapped, abstractStateMap);
        GraphUtil.printInferredGraph(iteratorPayload.getMethodGraph().get(root).graph, root, abstractStateMap);
    }
}
