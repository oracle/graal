package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.List;

/**
 * Interface for a checker that verifies the correctness of an abstract state.
 * The checker can be used to identify potential issues in the abstract state
 * and suggest modifications to the associated {@link StructuredGraph}.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface Checker<Domain extends AbstractDomain<Domain>> {

    /**
     * Get a simple description of the checker.
     * This description will then be displayed in logs.
     * E.g "Check for null pointer dereference"
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
}