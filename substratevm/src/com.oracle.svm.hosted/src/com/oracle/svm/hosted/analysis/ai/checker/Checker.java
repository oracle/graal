package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.List;

/**
 * Interface for checkers that can be used to check the results of the abstract interpretation.
 */
public interface Checker<Domain extends AbstractDomain<Domain>> {

    /**
     * Get a simple description of the checker.
     * E.g "Check for null pointer dereference"
     *
     * @return a description of the checker
     */
    String getDescription();

    /**
     * Check the given abstract state map for errors or inconsistencies.
     *
     * @param abstractStateMap the abstract state map to check
     * @return a list of {@link CheckerResult} for this check
     */
    List<CheckerResult> check(AbstractStateMap<Domain> abstractStateMap, StructuredGraph graph);

    /**
     * Check if the checker is compatible with the given abstract state map.
     * The domain of the {@code  abstractStateMap} should be the same
     * ( or convertable ) to the {@link Domain} used in the checker.
     * This is used to avoid ClassCastExceptions when checking the results.
     *
     * @param abstractStateMap the abstract state map to check compatibility with
     * @return true if the checker is compatible, false otherwise
     */
    boolean isCompatibleWith(AbstractStateMap<?> abstractStateMap);
}