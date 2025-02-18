package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import jdk.graal.compiler.debug.DebugContext;

public abstract class BaseCallHandler<Domain extends AbstractDomain<Domain>>
        implements CallHandler<Domain> {

    protected final Domain initialDomain;
    protected final TransferFunction<Domain> transferFunction;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;
    protected final IteratorPayload iteratorPayload;

    public BaseCallHandler(Domain initialDomain,
                           NodeInterpreter<Domain> nodeInterpreter,
                           CheckerManager checkerManager,
                           AnalysisMethodFilterManager methodFilterManager,
                           IteratorPayload iteratorPayload) {
        this.initialDomain = initialDomain;
        this.transferFunction = new TransferFunction<>(nodeInterpreter, this::handleCall);
        this.checkerManager = checkerManager;
        this.methodFilterManager = methodFilterManager;
        this.iteratorPayload = iteratorPayload;
    }

    public Domain getInitialDomain() {
        return initialDomain;
    }

    public CheckerManager getCheckerManager() {
        return checkerManager;
    }

    public AnalysisMethodFilterManager getMethodFilterManager() {
        return methodFilterManager;
    }

    public IteratorPayload getIteratorPayload() {
        return iteratorPayload;
    }

    @Override
    public void handleRootCall(AnalysisMethod root, DebugContext debug) {
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, debug, initialDomain, transferFunction, iteratorPayload);
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        String qualifiedName = root.getQualifiedName();
        AbstractStateMap<Domain> abstractStateMap = fixpointIterator.iterateUntilFixpoint();
        logger.logToFile("Abstract state map after fixpoint iteration for analysisMethod " + qualifiedName + ":\n" + abstractStateMap);
        logger.logDebugInfo("Running the provided checkers : " + checkerManager);
        checkerManager.checkAll(abstractStateMap);
        logger.logToFile("Checker results for analysisMethod " + qualifiedName + ":\n");
    }
}
