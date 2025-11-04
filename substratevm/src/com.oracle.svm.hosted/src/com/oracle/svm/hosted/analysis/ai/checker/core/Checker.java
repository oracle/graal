package com.oracle.svm.hosted.analysis.ai.checker.core;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.List;

/**
 * Interface for a checker on an annotated CFG ( CFG with computed pre-post conditions from abstract interpretation ).
 * The checker can in theory be used to:
 * 1. Identify potential issues in the abstract state
 * 2. Modify the abstract state to reflect some findings
 * 3. Modify the CFG ( {@link StructuredGraph} ) based on the abstract state
 * 4. Report findings to users
 * 5. Produce facts based on the abstract state, and insert assertions in the CFG
 * and suggest modifications to the associated {@link StructuredGraph}.
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
     * Check the given abstract state for potential erroneous behavior.
     * Implementations can define what is considered erroneous.
     * Based on the information in the {@link AbstractState},
     * it is possible to modify the given {@link StructuredGraph} associated with the abstract state.
     * For example, we can remove unreachable branches/nodes, replace some nodes with constants, etc.
     *
     * @param method        the method to check
     * @param abstractState the abstract state map to check
     * @return a list of {@link CheckerResult} for this check
     */
    List<CheckerResult> check(AnalysisMethod method, AbstractState<Domain> abstractState);

    /**
     * Decide if the checker is compatible with the given abstract state.
     * The domain of the {@param abstractState} should be the same
     * (or convertable) to the {@link Domain} used in the checker.
     * This is used to avoid ClassCastExceptions when checking the results.
     *
     * @param abstractState the abstract state map to check compatibility with
     * @return true if the checker is compatible, false otherwise
     */
    boolean isCompatibleWith(AbstractState<?> abstractState);

    /**
     * Optional: produce facts derived from the analysis that other tools or subsequent
     * checkers/phases can consume. By default, checkers that don't produce facts can ignore this.
     *
     * @param method the analysis method
     * @param abstractState the abstract state computed by the analysis
     * @return a list of CheckerFact objects describing derived facts
     */
    default List<Fact> produceFacts(AnalysisMethod method, AbstractState<Domain> abstractState) {
        return List.of();
    }
}