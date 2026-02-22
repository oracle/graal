package com.oracle.svm.hosted.analysis.ai.analysis.invokehandle;

import com.oracle.svm.hosted.analysis.ai.analysis.InvokeAnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Callback interface for handling method invocations during abstract interpretation.
 * Developers can use this callback when they want to analyze the effects of method calls,
 *
 * @param <Domain> the type of the derived {@link AbstractDomain} used in the analysis
 */
@FunctionalInterface
public interface InvokeCallBack<Domain extends AbstractDomain<Domain>> {
    /**
     * Handles the invocation of a method during abstract interpretation.
     *
     * @param invokeInput the relevant information needed to perform abstract interpretation of a given invocation
     * @return the analysis outcome
     */
    InvokeAnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput);
}
