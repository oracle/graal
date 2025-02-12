package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;

/**
 * Interface for checkers that can be used to check the results of the abstract interpretation.
 */
public interface Checker {

    /**
     * Get a simple description of the checker.
     * E.g "Check for null pointer dereference"
     * @return a simple description of the checker
     */
    String getDescription();

    /**
     * Check the given node with the given abstract state map (this map will be created during fixpoint iteration of a method's body)
     * @param abstractStateMap the abstract state map after the fixpoint iteration
     * @return the result of the check
     */
    CheckerResult check(AbstractStateMap<? extends AbstractDomain<?>> abstractStateMap);
}