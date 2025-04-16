package com.oracle.svm.hosted.analysis.ai.example.backward;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.EmptyDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This class demonstrates a backward analysis using the IntraProceduralAnalyzer.
 * It uses an empty domain and an empty domain interpreter for the analysis.
 */
public class BackwardAnalyzer {

    private final IntraProceduralAnalyzer<EmptyDomain> analyzer;

    public BackwardAnalyzer() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new EmptyDomain(),
                new EmptyDomainInterpreter())
                .iteratorPolicy(IteratorPolicy.DEFAULT_BACKWARD_WORKLIST)
                .build();
    }

    public void run(AnalysisMethod method, DebugContext debug) {
        analyzer.analyzeMethod(method, debug);
    }
}
