package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.ai.analyzer.example.leaks.set.IdentifierSetDomainInterAnalyzer;
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

    /**
     * Run the abstract interpretation analysis
     * Create a custom analyzer (or create your own, and run the analysis)
     */
    private void doRun() {
        GraphUtils.printGraph(root, debug);
        IdentifierSetDomainInterAnalyzer analyzer = new IdentifierSetDomainInterAnalyzer(root, debug);
        analyzer.run();
    }
}
