package com.oracle.svm.hosted.analysis.ai.fixpoint.iterator;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.InterProceduralAnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IntraProceduralAnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;

/**
 * Factory class for creating different types of fixpoint iterators
 * NOTE:
 *      We have to differentiate between creating fixpoint iterators for intra-procedural and inter-procedural analysis payload.
 *      Because inter-procedural analysis takes into account that the analysis can encounter same methods multiple times and tries
 *      to save time by caching important internal structures of iterators for a method, we first check these caches and call the appropriate constructor.
 *      On the other hand intra-procedural analysis should run on a single method and does not need to cache anything, so we call the appropriate constructor directly.
 */
public class FixpointIteratorFactory {

    public static <Domain extends AbstractDomain<Domain>> FixpointIterator<Domain> createFromIntraPayload(IntraProceduralAnalysisPayload<Domain> payload) {
        IteratorPolicy policy = payload.getIteratorPolicy();
        if (policy.isSequential()) {
            return new SequentialWtoFixpointIterator<>(payload);
        }

        if (policy.isConcurrent()) {
            return new ConcurrentWpoFixpointIterator<>(payload);
        }

        return new WorkListFixpointIterator<>(payload);
    }

    public static <Domain extends AbstractDomain<Domain>> FixpointIterator<Domain> createFromInterPayload(InterProceduralAnalysisPayload<Domain> payload) {
        IteratorPolicy policy = payload.getIteratorPolicy();
        AnalysisMethod currentMethod = payload.getCurrentAnalysisMethod();

        if (policy.isSequential()) {
            if (payload.containsMethodGraph(currentMethod) && payload.containsMethodWto(currentMethod)) {
                return new SequentialWtoFixpointIterator<>(payload, payload.getMethodGraph(currentMethod), payload.getMethodWto(currentMethod));
            }
            return new SequentialWtoFixpointIterator<>(payload);
        }

        if (policy.isConcurrent()) {
            if (payload.containsMethodGraph(currentMethod) && payload.containsMethodWpo(currentMethod)) {
                return new ConcurrentWpoFixpointIterator<>(payload, payload.getMethodGraph(currentMethod), payload.getMethodWpo(currentMethod));
            }
            return new ConcurrentWpoFixpointIterator<>(payload);
        }

        return new WorkListFixpointIterator<>(payload);
    }
}
