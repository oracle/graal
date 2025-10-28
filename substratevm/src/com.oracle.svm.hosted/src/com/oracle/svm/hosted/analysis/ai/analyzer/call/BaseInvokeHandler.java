package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalyzerMetadata;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;

/**
 * Base class for invoke handlers.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis.
 */
public abstract class BaseInvokeHandler<Domain extends AbstractDomain<Domain>> implements InvokeHandler<Domain> {

    protected final Domain initialDomain;
    protected final AbstractTransformer<Domain> abstractTransformer;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;
    protected final AnalyzerMetadata analyzerMetadata;

    @SuppressWarnings("this-escape")
    public BaseInvokeHandler(Domain initialDomain,
                             AbstractInterpreter<Domain> abstractInterpreter,
                             CheckerManager checkerManager,
                             AnalysisMethodFilterManager methodFilterManager,
                             AnalyzerMetadata analyzerMetadata) {
        this.initialDomain = initialDomain;
        this.abstractTransformer = new AbstractTransformer<>(abstractInterpreter, this::handleInvoke);
        this.checkerManager = checkerManager;
        this.methodFilterManager = methodFilterManager;
        this.analyzerMetadata = analyzerMetadata;
    }
}
