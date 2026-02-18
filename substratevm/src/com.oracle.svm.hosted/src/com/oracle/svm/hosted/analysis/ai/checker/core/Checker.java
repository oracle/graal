package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

import java.util.List;

/**
 * Checker interface focused on diagnostics and fact production.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */

public interface Checker<Domain extends AbstractDomain<Domain>> {

    /**
     * Get a simple description of the checker.
     * This description will then be displayed in logs.
     * E.g. "Check for null pointer dereference"
     *
     * @return a description of the checker
     */
    String getDescription();

    /**
     * Compatibility guard to avoid domain mismatches.
     * The domain of the {@param abstractState} should be the same
     * (or convertable) to the {@link Domain} used in the checker.
     *
     * @param abstractState the abstract state map to check compatibility with
     * @return true if the checker is compatible, false otherwise
     */
    boolean isCompatibleWith(AbstractState<?> abstractState);

    /**
     * Fact production pass: emit optimization / transformation facts derived
     * from the abstract state. These facts drive the FactApplier pipeline.
     *
     * @param method        the analysis method
     * @param abstractState the abstract state computed by the analysis
     * @return a list of CheckerFact objects describing derived facts
     */
    default List<Fact> produceFacts(AnalysisMethod method, AbstractState<Domain> abstractState) {
        return List.of();
    }
}