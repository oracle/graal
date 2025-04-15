package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;

/**
 * Base class for call handlers.
 *
 * @param <Domain> the type of the abstract domain used for the analysis.
 */
public abstract class BaseCallHandler<Domain extends AbstractDomain<Domain>> implements CallHandler<Domain> {

    protected final Domain initialDomain;
    protected final TransferFunction<Domain> transferFunction;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;
    protected final IteratorPayload iteratorPayload;

    @SuppressWarnings("this-escape")
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
}
