package com.oracle.svm.hosted.analysis.ai.summary;

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
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public final class SummaryCache<Domain extends AbstractDomain<Domain>> {

    private final Map<String, List<Summary<Domain>>> cache = new HashMap<>();
    private int cacheCalls = 0;
    private int cacheHits = 0;

    /**
     * Gets the complete summary of the function with the given target name and summary precondition.
     * NOTE:
     *      If we want to get a complete summary, we first should call {@link #contains(String, Summary)}
     *      When there are multiple summaries that are subsuming {@param summaryPrecondition}, we should return the most general one.
     *      However, there are many ways how to do this, we can choose the most precise summary,
     *      or we can take all the subsuming summaries, perform some kind of merge
     *      (would require meet operation in {@link Summary} api) and return the result.
     *
     * @param targetName          the name of the function
     * @param summaryPrecondition the precondition of the function
     * @return the summary for targetName with given summaryPrecondition
     */
    public Summary<Domain> getSummary(String targetName, Summary<Domain> summaryPrecondition) {
        Summary<Domain> mostGeneralSummary = null;

        for (Summary<Domain> existingSummary : cache.get(targetName)) {
            if (existingSummary.subsumes(summaryPrecondition)) {
                if (mostGeneralSummary == null ||
                        existingSummary.subsumes(mostGeneralSummary)) {
                    mostGeneralSummary = existingSummary;
                }
            }
        }

        return mostGeneralSummary;
    }

    public int getMethodSummariesAmount(String targetName) {
        return cache.get(targetName).size();
    }

    public int getCacheCalls() {
        return cacheCalls;
    }

    public int getCacheHits() {
        return cacheHits;
    }

    /**
     * Checks if the cache contains a summary for the given target name and precondition.
     *
     * @param targetName          the name of the function
     * @param summaryPrecondition the precondition of the function
     * @return true if the cache contains the summary, false otherwise
     */
    public boolean contains(String targetName, Summary<Domain> summaryPrecondition) {
        cacheCalls++;
        List<Summary<Domain>> summaries = cache.get(targetName);
        if (summaries != null) {
            for (Summary<Domain> summary : summaries) {
                if (summary.subsumes(summaryPrecondition)) {
                    cacheHits++;
                    return true;
                }
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
    public void put(String targetName, Summary<Domain> summary) {
        cache.computeIfAbsent(targetName, k -> new ArrayList<>()).add(summary);
    }

    @Override
    public String toString() {
        return "SummaryCache{" +
                "cache=" + cache +
                ", cacheCalls=" + cacheCalls +
                ", cacheHits=" + cacheHits +
                '}';
    }
}