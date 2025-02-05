package com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow.DataFlowIntIntervalNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This analyzer maps nodes to intervals.
 * The main purpose of this analyzer is to test some simple inter-procedural programs
 * that work with numbers
 */
public class IntervalInterAnalyzer {
    private final InterProceduralSequentialAnalyzer<IntInterval> analyzer;
    private final NodeInterpreter<IntInterval> nodeInterpreter;

    public IntervalInterAnalyzer(AnalysisMethod root, DebugContext debug) {
        SummarySupplier<IntInterval> supplier = new IntervalSummarySupplier();
        analyzer = new InterProceduralSequentialAnalyzer<>(root, debug, supplier, new SkipJavaLangMethodFilter());
        nodeInterpreter = new DataFlowIntIntervalNodeInterpreter();
    }

    public void run() {
        IntInterval initialDomain = new IntInterval();
        analyzer.run(initialDomain, nodeInterpreter);
    }
}
