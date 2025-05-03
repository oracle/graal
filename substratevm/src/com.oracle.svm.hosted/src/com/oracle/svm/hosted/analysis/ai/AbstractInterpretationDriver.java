package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.example.pentagon.IntraProceduralPentagonAnalyzerWrapper;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * The entry point of the abstract interpretation framework.
 */
public class AbstractInterpretationDriver {

    private final DebugContext debug;
    private final AnalyzerManager analyzerManager;
    private final AbstractInterpretationEngine engine;

    public AbstractInterpretationDriver(DebugContext debug, AnalysisMethod root, BigBang bigBang) {
        this.debug = debug;
        this.analyzerManager = new AnalyzerManager();
        this.engine = new AbstractInterpretationEngine(analyzerManager, root, debug, bigBang);
    }

    @SuppressWarnings("try")
    public void run() {
        try (ProgressReporter.ReporterClosable c = ProgressReporter.singleton().printAbstractInterpretation()) {
            /* Creating a new scope for logging, run with -H:Log=AbstractInterpretation to activate it */
            try (var scope = debug.scope("AbstractInterpretation")) {
                setupFramework();
                engine.execute();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This is the entry method for setting up the abstract interpretation framework.
     * We can:
     * 1. Provide the {@link com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer} to the {@link AnalyzerManager}.
     * These analyzers will then run as a part of the Native Image compilation process.
     * 2. Create and configure the {@link AbstractInterpretationLogger}, most importantly the name of the dump-file and verbosity.
     *
     * @throws IOException in case of I/O errors during logger initialization.
     */
    private void setupFramework() throws IOException {
        /* Firstly, let's create a logger instance  */
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance(debug, "myLogger", LoggerVerbosity.INFO);
        logger.log("Hello from the abstract interpretation", LoggerVerbosity.INFO);

        /* We can instantiate an existing analyzer or implement a new one in {@link IntraProceduralAnalyzer} and {@link InterProceduralAnalyzer} */
//        var accessAnalyzer = new AccessPathIntervalInterAnalyzerWrapper();
//        analyzerManager.registerAnalyzer(accessAnalyzer.getAnalyzer());
        var analyzer = new IntraProceduralPentagonAnalyzerWrapper().getAnalyzer();
        analyzerManager.registerAnalyzer(analyzer);
    }
}
