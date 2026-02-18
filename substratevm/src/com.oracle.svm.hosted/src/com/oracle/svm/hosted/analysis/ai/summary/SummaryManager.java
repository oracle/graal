package com.oracle.svm.hosted.analysis.ai.summary;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;
import java.util.Objects;

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

    public void mergeFrom(SummaryManager<Domain> other) {
        if (other == null || other == this) {
            return;
        }

        SummaryRepository<Domain> otherRepo = other.getSummaryRepository();
        if (otherRepo == null) {
            return;
        }

        for (var entry : otherRepo.getMethodSummaryMap().entrySet()) {
            AnalysisMethod method = entry.getKey();
            MethodSummary<Domain> otherMethodSummary = entry.getValue();
            if (otherMethodSummary == null) {
                continue;
            }

            MethodSummary<Domain> thisMethodSummary = summaryRepository.getOrCreate(method);

            // Merge aggregate state across contexts
            if (otherMethodSummary.getStateAcrossAllContexts() != null) {
                thisMethodSummary.joinWithContextState(otherMethodSummary.getStateAcrossAllContexts());
            }

            // Merge individual contexts with subsumption-aware reconciliation
            for (var ctxEntry : otherMethodSummary.getAllContexts().entrySet()) {
                ContextKey ctxKey = ctxEntry.getKey();
                ContextSummary<Domain> otherCtx = ctxEntry.getValue();
                if (otherCtx == null) {
                    continue;
                }

                Summary<Domain> otherSummary = otherCtx.summary();
                ContextSummary<Domain> thisCtx = thisMethodSummary.getContexts().get(ctxKey);

                if (thisCtx == null) {
                    // No existing entry under this key: insert as-is
                    thisMethodSummary.getOrCreate(ctxKey, otherSummary);
                    continue;
                }

                Summary<Domain> thisSummary = thisCtx.summary();
                if (thisSummary == null && otherSummary != null) {
                    // Prefer non-null
                    thisCtx.setSummary(otherSummary);
                    continue;
                }
                if (otherSummary == null) {
                    // Nothing to merge
                    continue;
                }

                // If identical by reference or equals, skip duplicate
                if (thisSummary == otherSummary || Objects.equals(thisSummary, otherSummary)) {
                    continue;
                }

                // Subsumption checks: keep the more general one under the same key
                if (thisSummary.subsumesSummary(otherSummary)) {
                    // Existing is as general or more general: keep it
                    continue;
                }
                if (otherSummary.subsumesSummary(thisSummary)) {
                    // Replace with more general summary
                    thisCtx.setSummary(otherSummary);
                    continue;
                }

                // Incomparable: keep both by inserting other under an alternate deterministic key
                ContextKey altKey = deriveAlternateKey(method, ctxKey, otherSummary);
                if (!thisMethodSummary.getContexts().containsKey(altKey)) {
                    thisMethodSummary.getOrCreate(altKey, otherSummary);
                }
            }
        }
    }

    public void mergeAggregateOnly(SummaryManager<Domain> other) {
        if (other == null || other == this) {
            return;
        }
        SummaryRepository<Domain> otherRepo = other.getSummaryRepository();
        if (otherRepo == null) {
            return;
        }

        for (var entry : otherRepo.getMethodSummaryMap().entrySet()) {
            AnalysisMethod method = entry.getKey();
            MethodSummary<Domain> otherMethodSummary = entry.getValue();
            if (otherMethodSummary == null) {
                continue;
            }
            MethodSummary<Domain> thisMethodSummary = summaryRepository.getOrCreate(method);
            if (otherMethodSummary.getStateAcrossAllContexts() != null) {
                thisMethodSummary.joinWithContextState(otherMethodSummary.getStateAcrossAllContexts());
            }
            // Intentionally skip merging per-context entries.
        }
    }

    private ContextKey deriveAlternateKey(AnalysisMethod method, ContextKey baseKey, Summary<Domain> otherSummary) {
        // Use base depth and a stable composite hash of baseKey and other pre-condition
        int depth = baseKey.depth();
        int preHash = 0;
        if (otherSummary != null && otherSummary.getPreCondition() != null) {
            preHash = Objects.hash(otherSummary.getPreCondition());
        }
        int composite = Objects.hash(baseKey, preHash);
        return new ContextKey(method, composite, depth);
    }
}
