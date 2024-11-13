package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.transfer.policy.IteratorPolicy;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

public class InterProceduralAnalyzer<Domain extends AbstractDomain<Domain>> implements Analyzer<Domain> {
    private final FixpointIterator<Domain> fixpointIterator;

    public InterProceduralAnalyzer(StructuredGraph graph,
                                   TransferFunction<Domain> transferFunction,
                                   IteratorPolicy policy,
                                   Domain initialDomain,
                                   DebugContext debug) {
        this.fixpointIterator = new FixpointIterator<>(graph, transferFunction, policy, initialDomain, debug);
    }

    @Override
    public Domain analyze() {
        return fixpointIterator.analyze();
    }
}