package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;

/**
 * Represents the outcome of an analysis, consisting of a result and an optional summary.
 *
 * @param result   the result of the analysis, represented as an instance of {@link AnalysisResult}
 * @param summary  the summary produced by the analysis, represented as an instance of {@link Summary}
 * @param <Domain> the type of the derived {@link AbstractDomain} used in the analysis
 */
public record AnalysisOutcome<Domain extends AbstractDomain<Domain>>(AnalysisResult result, Summary<Domain> summary) {

    public static <Domain extends AbstractDomain<Domain>> AnalysisOutcome<Domain> ok(Summary<Domain> summary) {
        return new AnalysisOutcome<>(AnalysisResult.OK, summary);
    }

    public static <Domain extends AbstractDomain<Domain>> AnalysisOutcome<Domain> error(AnalysisResult result) {
        return new AnalysisOutcome<>(result, null);
    }

    public boolean isOk() {
        return result == AnalysisResult.OK;
    }

    public boolean isError() {
        return result != AnalysisResult.OK;
    }
}
