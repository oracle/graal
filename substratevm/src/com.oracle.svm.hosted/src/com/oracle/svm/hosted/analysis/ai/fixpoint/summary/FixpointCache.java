package com.oracle.svm.hosted.analysis.ai.fixpoint.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a mapping of function names to their summaries.
 * This is used to cache the summaries of functions during the fixpoint computation.
 * This way we avoid reanalyzing the function's body at every call site.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */

/* TODO: temporary string, but later should be access path to the method */
public final class FixpointCache<Domain extends AbstractDomain<Domain>> {

    private final Map<String, List<FunctionSummary<Domain>>> cache = new HashMap<>();

    /**
     * Checks if the cache contains a summary for the given target name and precondition.
     *
     * @param targetName          the name of the function
     * @param summaryPrecondition the precondition of the function
     * @return true if the cache contains the summary, false otherwise
     */
    public boolean contains(String targetName, FunctionSummary<Domain> summaryPrecondition) {
        List<FunctionSummary<Domain>> summaries = cache.get(targetName);
        for (FunctionSummary<Domain> summary : summaries) {
            if (summary.subsumes(summaryPrecondition)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Puts a summary into the cache.
     * The summary should be complete, meaning it has a computed post-condition.
     *
     * @param targetName the name of the function
     * @param summary    the summary to put
     */
    public void put(String targetName, FunctionSummary<Domain> summary) {
        cache.computeIfAbsent(targetName, k -> new ArrayList<>()).add(summary);
    }

    /**
     * Gets the post-condition of the function with the given target name and precondition.
     * NOTE: This should be called only if the cache contains the summary.
     *
     * @param targetName          the name of the function
     * @param summaryPrecondition the precondition of the function
     * @return the post-condition of the function
     */
    public Domain getPostCondition(String targetName, FunctionSummary<Domain> summaryPrecondition) {
        List<FunctionSummary<Domain>> summaries = cache.get(targetName);
        for (FunctionSummary<Domain> summary : summaries) {
            if (summary.subsumes(summaryPrecondition)) {
                return summary.getPostCondition();
            }
        }
        return null;
    }
}