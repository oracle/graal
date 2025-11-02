package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;

/**
 * Base class for invoke handler callbacks.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis.
 */
public abstract class BaseInvokeHandler<Domain extends AbstractDomain<Domain>> implements InvokeHandler<Domain> {

    protected final Domain initialDomain;
    protected final AbstractTransformer<Domain> abstractTransformer;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;
    protected final AnalysisContext analysisContext;

    @SuppressWarnings("this-escape")
    public BaseInvokeHandler(Domain initialDomain,
                             AbstractInterpreter<Domain> abstractInterpreter,
                             AnalysisContext analysisContext) {
        this.initialDomain = initialDomain;
        this.abstractTransformer = new AbstractTransformer<>(abstractInterpreter, this::handleInvoke);
        this.analysisContext = analysisContext;
        this.checkerManager = analysisContext.getCheckerManager();
        this.methodFilterManager = analysisContext.getMethodFilterManager();
    }
}
