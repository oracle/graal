package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisServices;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Manages summaries for methods.
 *
 * <p>This is a thin facade over {@link SummaryFactory} and {@link SummaryRepository}.
 * It is responsible for
 * <ul>
 *   <li>creating pre-condition summaries for calls,</li>
 *   <li>looking up an existing context-sensitive summary for a callee,</li>
 *   <li>and registering finalized summaries once a callee has been analyzed.</li>
 * </ul>
 *
 * @param <Domain> the type of derived {@link AbstractDomain} used in the analysis
 */
public final class SummaryManager<Domain extends AbstractDomain<Domain>> {

    private final SummaryFactory<Domain> summaryFactory;
    private final SummaryRepository<Domain> summaryRepository;

    public SummaryManager(SummaryFactory<Domain> summaryFactory) {
        this(summaryFactory, new SummaryRepository<>());
    }

    public SummaryManager(SummaryFactory<Domain> summaryFactory, SummaryRepository<Domain> summaryRepository) {
        this.summaryFactory = summaryFactory;
        this.summaryRepository = summaryRepository;
    }

    public SummaryFactory<Domain> getSummaryFactory() {
        return summaryFactory;
    }

    public SummaryRepository<Domain> getSummaryRepository() {
        return summaryRepository;
    }

    /**
     * Create a new pre-condition-only summary for a given invoke.
     */
    public Summary<Domain> createSummary(Invoke invoke,
                                         Domain callerPreCondition,
                                         List<Domain> domainArguments) {
        return summaryFactory.createSummary(invoke, callerPreCondition, domainArguments);
    }

    /**
     * Lookup the most general summary for {@code calleeMethod} that subsumes the given
     * {@code summaryPrecondition}, or {@code null} if none exists.
     */
    public Summary<Domain> getSummary(AnalysisMethod calleeMethod, Summary<Domain> summaryPrecondition) {
        MethodSummary<Domain> methodSummary = summaryRepository.get(calleeMethod);
        if (methodSummary == null) {
            return null;
        }

        Summary<Domain> mostGeneral = null;
        for (ContextSummary<Domain> ctx : methodSummary.getAllContexts().values()) {
            Summary<Domain> existing = ctx.summary();
            if (existing.subsumesSummary(summaryPrecondition)) {
                if (mostGeneral == null || existing.subsumesSummary(mostGeneral)) {
                    mostGeneral = existing;
                }
            }
        }
        return mostGeneral;
    }

    /**
     * Register a finalized summary for a given context.
     */
    public void putSummary(AnalysisMethod calleeMethod, ContextKey contextKey, Summary<Domain> summary) {
        MethodSummary<Domain> methodSummary = summaryRepository.getOrCreate(calleeMethod);
        methodSummary.getOrCreate(contextKey, summary);
    }

    /**
     * Convenience overload when the analysis uses a synthetic/default context key.
     */
    public void putSummary(AnalysisMethod calleeMethod, Summary<Domain> summary) {
        ContextKey key = new ContextKey(calleeMethod, 0, 0);
        putSummary(calleeMethod, key, summary);
    }
}
