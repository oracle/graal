package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.CountingDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.example.LeaksCountingDomainNodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.example.LeakCountingSummaryProvider;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtils;
import jdk.graal.compiler.debug.DebugContext;

public class AbstractInterpretationDriver {

    private final DebugContext debug;
    private final AnalysisMethod root;

    public AbstractInterpretationDriver(DebugContext debug, AnalysisMethod root) {
        this.debug = debug;
        this.root = root;
    }

    @SuppressWarnings("try")
    public void run() {
        try (ProgressReporter.ReporterClosable c = ProgressReporter.singleton().printAbstractInterpretation()) {
            /*
             * Make a new scope for logging, run with -H:Log=AbstractInterpretation to activate it
             */
            try (var scope = debug.scope("AbstractInterpretation")) {
                doRun();
            }
        }
    }

    private void doRun() {
        GraphUtils.printGraph(root, debug);
        NodeInterpreter<CountingDomain> nodeInterpreter = new LeaksCountingDomainNodeInterpreter();
        LeakCountingSummaryProvider summaryProvider = new LeakCountingSummaryProvider();
        Analyzer<CountingDomain> analyzer = new InterProceduralSequentialAnalyzer<>(root, debug, summaryProvider);
        analyzer.run(new CountingDomain(), nodeInterpreter);
    }
}
