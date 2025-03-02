package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IterationStrategy;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Factory class for creating different types of fixpoint iterators
 * NOTE:
 *      We have to differentiate between creating fixpoint iterators for intra-procedural and inter-procedural analysis payload.
 *      Because inter-procedural analysis takes into account that the analysis can encounter same methods multiple times and tries
 *      to save time by caching important internal structures of iterators for a analysisMethod, we first check these caches and call the appropriate constructor.
 *      On the other hand intra-procedural analysis should run on a single analysisMethod and does not need to cache anything, so we call the appropriate constructor directly.
 */
public final class FixpointIteratorFactory {

    public static <Domain extends AbstractDomain<Domain>> FixpointIterator<Domain> createIterator(AnalysisMethod method,
                                                                                                  DebugContext debug,
                                                                                                  Domain initialDomain,
                                                                                                  TransferFunction<Domain> transferFunction,
                                                                                                  IteratorPayload iteratorPayload) {
        return switch (iteratorPayload.getIterationStrategy()) {
            case IterationStrategy.WTO ->
                    new SequentialWtoFixpointIterator<>(method, debug, initialDomain, transferFunction, iteratorPayload);
            case IterationStrategy.WPO ->
                    new ConcurrentWpoFixpointIterator<>(method, debug, initialDomain, transferFunction, iteratorPayload);
            default -> new WorkListFixpointIterator<>(method, debug, initialDomain, transferFunction, iteratorPayload);
        };
    }
}
