package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;

/**
 * Base class for invoke handlers.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis.
 */
public abstract class BaseInvokeHandler<Domain extends AbstractDomain<Domain>> implements InvokeHandler<Domain> {

    protected final Domain initialDomain;
    protected final TransferFunction<Domain> transferFunction;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;
    protected final IteratorPayload iteratorPayload;

    @SuppressWarnings("this-escape")
    public BaseInvokeHandler(Domain initialDomain,
                             AbstractInterpreter<Domain> abstractInterpreter,
                             CheckerManager checkerManager,
                             AnalysisMethodFilterManager methodFilterManager,
                             IteratorPayload iteratorPayload) {
        this.initialDomain = initialDomain;
        this.transferFunction = new TransferFunction<>(abstractInterpreter, this::handleInvoke);
        this.checkerManager = checkerManager;
        this.methodFilterManager = methodFilterManager;
        this.iteratorPayload = iteratorPayload;
    }
}
