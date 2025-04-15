package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.vm.ci.meta.ResolvedJavaMethod;

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

    private final Map<ResolvedJavaMethod, List<Summary<Domain>>> cache = new HashMap<>();
    private int cacheCalls = 0;
    private int cacheHits = 0;

    /**
     * Gets the summary of the {@param calleeName} with the given target name and summary precondition.
     * NOTE:
     * When there are multiple summaries that are subsuming {@param summaryPrecondition}, we should return the most general one.
     * However, there are many ways how to do this, we can choose the most precise summary,
     * or we can take all the subsuming summaries, perform some kind of merge
     * (would require meet operation in {@link Summary} api) and return the result.
     *
     * @param method              the method we are searching summary for
     * @param summaryPrecondition the precondition of the callee
     * @return the summary for targetName with given summaryPrecondition
     */
    public Summary<Domain> getSummary(ResolvedJavaMethod method, Summary<Domain> summaryPrecondition) {
        Summary<Domain> mostGeneralSummary = null;

        for (Summary<Domain> existingSummary : cache.get(method)) {
            if (existingSummary.subsumesSummary(summaryPrecondition)) {
                if (mostGeneralSummary == null ||
                        existingSummary.subsumesSummary(mostGeneralSummary)) {
                    mostGeneralSummary = existingSummary;
                }
            }
        }

        return mostGeneralSummary;
    }

    public int getMethodSummariesAmount(ResolvedJavaMethod method) {
        return cache.get(method).size();
    }

    public int getCacheCalls() {
        return cacheCalls;
    }

    public int getCacheHits() {
        return cacheHits;
    }

    /**
     * Checks if the cache contains a summary for the given {@param method} and precondition.
     *
     * @param method              the method we are searching summary for
     * @param summaryPrecondition the precondition of the callee
     * @return true if the cache contains the summary, false otherwise
     */
    public boolean contains(ResolvedJavaMethod method, Summary<Domain> summaryPrecondition) {
        cacheCalls++;
        List<Summary<Domain>> summaries = cache.get(method);
        if (summaries != null) {
            for (Summary<Domain> summary : summaries) {
                if (summary.subsumesSummary(summaryPrecondition)) {
                    cacheHits++;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Puts a summary for a callee into the cache.
     * The summary must be complete, meaning {@code finalizeSummary} must be called before putting it into the cache.
     *
     * @param method  the method we are putting the summary for
     * @param summary the summary to put
     */
    public void put(ResolvedJavaMethod method, Summary<Domain> summary) {
        cache.computeIfAbsent(method, k -> new ArrayList<>()).add(summary);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (var entry : cache.entrySet()) {
            sb.append(entry.getKey()).append(System.lineSeparator());
            for (var summary : entry.getValue()) {
                sb.append(summary.toString()).append(System.lineSeparator());
            }

            sb.append(System.lineSeparator());
        }
        return sb.toString();
    }
}
