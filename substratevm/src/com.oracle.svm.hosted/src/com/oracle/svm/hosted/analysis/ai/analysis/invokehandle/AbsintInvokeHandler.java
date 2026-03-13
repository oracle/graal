package com.oracle.svm.hosted.analysis.ai.analysis.invokehandle;

import com.oracle.svm.hosted.analysis.ai.analysis.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.core.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;

/**
 * Base class for invoke handler callbacks.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis.
 */
public abstract class AbsintInvokeHandler<Domain extends AbstractDomain<Domain>> implements InvokeHandler<Domain> {

    protected final Domain initialDomain;
    protected final AbstractTransformer<Domain> abstractTransformer;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;
    protected final AnalysisContext<Domain> analysisContext;

    @SuppressWarnings("this-escape")
    public AbsintInvokeHandler(Domain initialDomain,
                               AbstractInterpreter<Domain> abstractInterpreter,
                               AnalysisContext<Domain> analysisContext) {
        this.initialDomain = initialDomain;
        this.abstractTransformer = new AbstractTransformer<>(abstractInterpreter, this::handleInvoke);
        this.analysisContext = analysisContext;
        this.checkerManager = analysisContext.getCheckerManager();
        this.methodFilterManager = analysisContext.getMethodFilterManager();
    }
}
