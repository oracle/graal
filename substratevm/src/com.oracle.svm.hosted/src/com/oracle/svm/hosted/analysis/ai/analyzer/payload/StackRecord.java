package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;

/**
 * Represents a record of {@link CallStack} that is used to store the current method and its pre-condition summary.
 *
 * @param method the analysis method
 * @param preConditionSummary the pre-condition summary of the method
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public record StackRecord<Domain extends AbstractDomain<Domain>>(
        AnalysisMethod method,
        Summary<Domain> preConditionSummary) {

}
