package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.ai.example.access.inter.AccessPathIntervalInterAnalyzer;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.util.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

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
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // TODO: rebase master
    // TODO: StrengthenGraph integerStamps, makeUnreachable
    private void doRun() throws IOException {
        /* Firstly, create a logger instance, the default logger is initialized to Verbosity INFO  */
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance(root, debug, LoggerVerbosity.SUMMARY);

        /* We can instantiate an existing analyzer or build our own from the builders in {@link IntraProceduralAnalyzer} and {@link InterProceduralAnalyzer} */
        var analyzer = new AccessPathIntervalInterAnalyzer();
        analyzer.run(root, debug);
    }
}
