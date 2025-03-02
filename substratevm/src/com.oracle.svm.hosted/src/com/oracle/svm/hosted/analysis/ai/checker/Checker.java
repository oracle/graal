package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;

import java.util.List;

/**
 * Interface for checkers that can be used to check the results of the abstract interpretation.
 */
public interface Checker {

    /**
     * Get a simple description of the checker.
     * E.g "Check for null pointer dereference"
     *
     * @return a description of the checker
     */
    String getDescription();

    /**
     * Check the given node with the given abstract state map.
     * This map will be created during fixpoint iteration of a analysisMethod's body
     *
     * @param abstractStateMap the abstract state map after the fixpoint iteration
     * @return the result of the check
     */

    /**
     * NOTE: Abstract interpretation sometimes produces a lot of caught warnings/errors ( sometimes even false positives )
     * this can be discouraging for the person trying to analyze their program. We need to think of a way to restrict this amount.
     * There are few possibilities here: we can either sort the warnings according to how severe they are and limit this amount,
     * or we won't show the  errors propagated from caller to callees
     */
    List<CheckerResult> check(AbstractStateMap<? extends AbstractDomain<?>> abstractStateMap);
}