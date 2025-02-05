package com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow.intra;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow.DataFlowIntIntervalNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Simple intra-procedural analyzer that maps values of nodes to intervals.
 * This is done in order to simulate a data flow analysis on intervals.
 */
public class IntervalIntraAnalyzer {
    private final IntraProceduralSequentialAnalyzer<IntInterval> analyzer;
    private final NodeInterpreter<IntInterval> nodeInterpreter;

    public IntervalIntraAnalyzer(AnalysisMethod root, DebugContext debug) {
        analyzer = new IntraProceduralSequentialAnalyzer<>(root, debug);
        nodeInterpreter = new DataFlowIntIntervalNodeInterpreter();
    }

    public void run() {
        IntInterval initial = new IntInterval();
        analyzer.run(initial, nodeInterpreter);
    }
}
