package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.WorkListFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.fixpoint.policy.IteratorPolicy;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

public class InterProceduralAnalyzer<Domain extends AbstractDomain<Domain>> implements Analyzer<Domain> {
    private final FixpointIterator<Domain> fixpointIterator;

    public InterProceduralAnalyzer(StructuredGraph graph,
                                   TransferFunction<Domain> transferFunction,
                                   IteratorPolicy policy,
                                   Domain initialDomain,
                                   DebugContext debug) {
        this.fixpointIterator = new WorkListFixpointIterator<>(graph, transferFunction, policy, initialDomain, debug);
    }

    @Override
    public Environment<Domain> analyze() {
        return fixpointIterator.iterateUntilFixpoint();
    }
}