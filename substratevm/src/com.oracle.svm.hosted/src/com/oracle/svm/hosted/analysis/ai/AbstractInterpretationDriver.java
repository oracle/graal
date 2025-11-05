package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.DataFlowIntervalAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.ConditionTruthChecker;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.ConstantPropagationChecker;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.IndexSafetyChecker;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * The entry point of the abstract interpretation framework.
 * This class is responsible for all the necessary setup and configuration of the framework, which will then be executed
 * The most important component of the framework is the {@link AnalyzerManager}, which manages all registered analyzers
 * and coordinates their execution on the program being analyzed.
 * The actual analysis is performed by the {@link AbstractInterpretationEngine},
 * which uses the registered analyzers to analyze the program.
 */
public class AbstractInterpretationDriver {

    private final DebugContext debug;
    private final AnalyzerManager analyzerManager;
    private final AbstractInterpretationEngine engine;


    public AbstractInterpretationDriver(DebugContext debug, AnalysisMethod mainEntryPoint, Inflation bb) {
        this.debug = debug;
        this.analyzerManager = new AnalyzerManager();
        this.engine = new AbstractInterpretationEngine(analyzerManager, mainEntryPoint, bb);
    }

    /* To see the output of the abstract interpretation, run with -H:Log=AbstractInterpretation */
    @SuppressWarnings("try")
    public void run() {
        try (ProgressReporter.ReporterClosable c = ProgressReporter.singleton().printAbstractInterpretation()) {
            /* Creating a new scope for logging, run with -H:Log=AbstractInterpretation to activate it */
            try (var scope = debug.scope("AbstractInterpretation")) {
                prepareAnalyses();
                engine.executeAbstractInterpretation(AnalyzerMode.INTRA_ANALYZE_MAIN_ONLY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * This is the entry method for setting up analyses in GraalAF.
     * We can:
     * 1. Provide the {@link com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer} to the {@link AnalyzerManager}.
     * These analyzers will then run as a part of the Native Image compilation process.
     * 2. Create and configure the {@link AbstractInterpretationLogger}, most importantly the name of the dump-file and verbosity.
     *
     * @throws IOException in case of I/O errors during logger initialization.
     */
    private void prepareAnalyses() throws IOException {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance("myLogger", LoggerVerbosity.DEBUG)
                .setConsoleEnabled(false)            // only write to file
                .setFileEnabled(true)                // ensure file logging is on
                .setFileThreshold(LoggerVerbosity.DEBUG)   // keep detailed logs in file
                .setConsoleThreshold(LoggerVerbosity.INFO); // irrelevant since console disabled
        debug.log("Abstract Interpretation Logger initialized: %s", logger.getLogFilePath());

        /* 1. Define the abstract domain */
        AbstractMemory initialDomain = new AbstractMemory();

        /* 2. Create an interpreter */
        DataFlowIntervalAbstractInterpreter interpreter =
                new DataFlowIntervalAbstractInterpreter();

        /* 3. Build analyzer */
        var intraDataFlowAnalyzer = new IntraProceduralAnalyzer.Builder<>(initialDomain, interpreter)
                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
//                .registerChecker(new ConstantPropagationChecker())
//                .registerChecker(new ConditionTruthChecker())
                .registerChecker(new IndexSafetyChecker())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();

        /* 4. Register with manager */
        analyzerManager.registerAnalyzer(intraDataFlowAnalyzer);
    }
}
