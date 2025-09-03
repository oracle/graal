package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.intra.LeaksIdSetIntraAnalyzerWrapper;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * The entry point of the abstract interpretation framework.
 * This class is responsible for all the necessary setup and configuration of the framework, which will then be executed
 * by the {@link AbstractInterpretationEngine}.
 */
public class AbstractInterpretationDriver {

    private final DebugContext debug;
    private final AnalyzerManager analyzerManager;
    private final AbstractInterpretationEngine engine;
    private final Inflation inflation;

    public AbstractInterpretationDriver(DebugContext debug, Inflation inflation) {
        this.inflation = inflation;
        this.debug = debug;
        this.analyzerManager = new AnalyzerManager();
        this.engine = new AbstractInterpretationEngine(analyzerManager, inflation);
    }

    /* To see the output of the abstract interpretation, run with -H:Log=AbstractInterpretation */
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
         /** We can creat the {@link AbstractInterpretationLogger} instance here. */
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance(debug, "myLogger", LoggerVerbosity.INFO);
        logger.log("Hello from the abstract interpretation", LoggerVerbosity.INFO);

        /* We can instantiate an existing analyzer or implement a new one in {@link IntraProceduralAnalyzer} and {@link InterProceduralAnalyzer}
         * If we wish to use analyzers during the native image build, we must register them here.
         * To get started quickly, we can use the sample analyzer wrappers provided in {@link com.oracle.svm.hosted.analysis.ai.example}.
         * */
        var analyzer = new LeaksIdSetIntraAnalyzerWrapper().getAnalyzer();
        analyzerManager.registerAnalyzer(analyzer);

        /* We can set what methods we want to analyze by engine.setAnalyzeMainOnly(); */
        engine.setAnalyzeMainOnly(true);
    }
}
