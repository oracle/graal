package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorStrategy;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformers;

/**
 * Factory class for creating different types of fixpoint iterators
 * NOTE:
 * We have to differentiate between creating fixpoint iterators for intra-procedural and inter-procedural analysis payload.
 * Because inter-procedural analysis takes into account that the analysis can encounter same methods multiple times and tries
 * to save time by caching important internal structures of iterators for a analysisMethod, we first check these caches and call the appropriate constructor.
 * On the other hand intra-procedural analysis should run on a single analysisMethod and does not need to cache anything, so we call the appropriate constructor directly.
 */
public final class FixpointIteratorFactory {

    public static <Domain extends AbstractDomain<Domain>> FixpointIterator<Domain> createIterator(AnalysisMethod method,
                                                                                                  Domain initialDomain,
                                                                                                  AbstractTransformers<Domain> abstractTransformers, IteratorPayload iteratorPayload) {
        return switch (iteratorPayload.getIterationStrategy()) {
            case IteratorStrategy.WTO ->
                    new WtoFixpointIterator<>(method, initialDomain, abstractTransformers, iteratorPayload);
            case IteratorStrategy.WPO ->
                    new WpoFixpointIterator<>(method, initialDomain, abstractTransformers, iteratorPayload);
            default -> new WorkListFixpointIterator<>(method, initialDomain, abstractTransformers, iteratorPayload);
        };
    }
}
