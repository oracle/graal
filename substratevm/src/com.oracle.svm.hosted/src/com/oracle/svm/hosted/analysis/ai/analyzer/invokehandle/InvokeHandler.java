package com.oracle.svm.hosted.analysis.ai.analyzer.invokehandle;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.InvokeAnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Interface for handling method invocations during abstract interpretation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface InvokeHandler<Domain extends AbstractDomain<Domain>> {

    /**
     * Handles the invocation of a method during abstract interpretation.
     *
     * @param invokeInput the relevant information needed to perform abstract interpretation of a given invocation
     * @return the analysis outcome
     */
    InvokeAnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput);

    /**
     * The starting point of the analysis
     * We receive an {@link AnalysisMethod} and we start our abstract interpretation from this analysisMethod as the starting point.
     *
     * @param root the {@link AnalysisMethod} that the abstract interpretation analysis starts from
     */
    void handleRootInvoke(AnalysisMethod root);
}
