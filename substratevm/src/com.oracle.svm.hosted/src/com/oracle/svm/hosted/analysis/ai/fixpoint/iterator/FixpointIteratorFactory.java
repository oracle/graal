package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analysis.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorStrategy;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractTransformer;

/**
 * Factory class for creating different types of fixpoint iterators.
 */
public final class FixpointIteratorFactory {

    public static <Domain extends AbstractDomain<Domain>> FixpointIterator<Domain> createIterator(AnalysisMethod method,
                                                                                                  Domain initialDomain,
                                                                                                  AbstractTransformer<Domain> abstractTransformer,
                                                                                                  AnalysisContext<Domain> analysisContext) {
        assert method.getAnalyzedGraph() != null;
        return switch (analysisContext.getIteratorPolicy().strategy()) {
            case IteratorStrategy.WTO -> new WtoFixpointIterator<>(method, initialDomain, abstractTransformer, analysisContext);
            case IteratorStrategy.WPO -> new WpoFixpointIterator<>(method, initialDomain, abstractTransformer, analysisContext);
        };
    }
}
