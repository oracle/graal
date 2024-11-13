package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.transfer.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.transfer.policy.PolicyBuilder;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

public class AnalyzerFactory {

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createInterProceduralAnalyzer(
            StructuredGraph graph,
            TransferFunction<Domain> transferFunction,
            Domain initialDomain,
            DebugContext debug) {
        PolicyBuilder builder = new PolicyBuilder();
        builder.setShouldSkipCallNodes(false);
        IteratorPolicy policy = builder.build();
        return new InterProceduralAnalyzer<>(graph, transferFunction, policy, initialDomain, debug);
    }

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createIntraProceduralAnalyzer(
            StructuredGraph graph,
            TransferFunction<Domain> transferFunction,
            Domain initialDomain,
            DebugContext debug) {
        PolicyBuilder builder = new PolicyBuilder();
        builder.setShouldSkipCallNodes(true);
        IteratorPolicy policy = builder.build();
        return new IntraProceduralAnalyzer<>(graph, transferFunction, policy, initialDomain, debug);
    }
}