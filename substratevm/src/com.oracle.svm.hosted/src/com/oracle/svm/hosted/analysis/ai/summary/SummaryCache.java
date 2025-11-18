package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a mapping of methods to their summaries.
 * This is used to cache the summaries of methods during the fixpoint computation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in abstract interpretation
 */
public final class SummaryCache<Domain extends AbstractDomain<Domain>> {

    private final Map<AnalysisMethod, List<Summary<Domain>>> cache = new HashMap<>();
    private int cacheCalls = 0;
    private int cacheHits = 0;

    public Summary<Domain> getSummary(AnalysisMethod method, Summary<Domain> summaryPrecondition) {
        Summary<Domain> mostGeneralSummary = null;
        List<Summary<Domain>> list = cache.get(method);
        if (list == null) {
            return null;
        }
        for (Summary<Domain> existingSummary : list) {
            if (existingSummary.subsumesSummary(summaryPrecondition)) {
                if (mostGeneralSummary == null || existingSummary.subsumesSummary(mostGeneralSummary)) {
                    mostGeneralSummary = existingSummary;
                }
            }
        }
        return mostGeneralSummary;
    }

    public int getMethodSummariesAmount(AnalysisMethod method) {
        List<Summary<Domain>> list = cache.get(method);
        return list == null ? 0 : list.size();
    }

    public int getCacheCalls() {
        return cacheCalls;
    }

    public int getCacheHits() {
        return cacheHits;
    }

    public boolean contains(AnalysisMethod method, Summary<Domain> summaryPrecondition) {
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

    public void put(AnalysisMethod method, Summary<Domain> summary) {
        cache.computeIfAbsent(method, k -> new ArrayList<>()).add(summary);
    }

    public List<Summary<Domain>> getAllSummaries() {
        return cache.entrySet().stream().flatMap(e -> e.getValue().stream()).toList();
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
