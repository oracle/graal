package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.fixpoint.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.policy.PolicyBuilder;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * Factory for creating different kinds of analyzers
 * Users can create their own IteratorPolicy inside create methods
 */
public class AnalyzerFactory {

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createInterProceduralAnalyzer(
            StructuredGraph graph,
            TransferFunction<Domain> transferFunction,
            Domain initialDomain,
            DebugContext debug) {
        PolicyBuilder builder = new PolicyBuilder();
        IteratorPolicy policy = builder.build();
        return new InterProceduralAnalyzer<>(graph, transferFunction, policy, initialDomain, debug);
    }

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createIntraProceduralAnalyzer(
            StructuredGraph graph,
            TransferFunction<Domain> transferFunction,
            Domain initialDomain,
            DebugContext debug) {
        PolicyBuilder builder = new PolicyBuilder();
        IteratorPolicy policy = builder.build();
        return new IntraProceduralAnalyzer<>(graph, transferFunction, policy, initialDomain, debug);
    }
}