package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.DataFlowIntervalAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.inter.DataFlowIntervalAnalysisSummaryFactory;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.SkipSvmMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.IfConditionChecker;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.ConstantValueChecker;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.exception.AbstractInterpretationException;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationServices;
import jdk.graal.compiler.debug.DebugContext;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;

/**
 * The entry point of the abstract interpretation framework.
 * This class is responsible for all the necessary setup and configuration of the framework, which will then be executed
 * The most important component of the framework is the {@link AnalyzerManager}, which manages all registered analyzers
 * and coordinates their execution.
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
                engine.executeAbstractInterpretation();
            } catch (AbstractInterpretationException e) {
                debug.log("Abstract interpretation encountered a runtime error: ", e);
            }
        }
    }

    private void printAbstractInterpretationStats() {
        var stats = AbstractInterpretationServices.getInstance().getStats();
        debug.log(stats.toString());
    }

    /**
     * This is the entry method for setting up analyses in GraalAF.
     * We can:
     * 1. Register {@link Analyzer} instances to the {@link AnalyzerManager}.
     * 2. Create and configure the {@link AbstractInterpretationLogger}.
     */
    private void prepareAnalyses() {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance("GraalAF", LoggerVerbosity.DEBUG)
                .setConsoleEnabled(false)
                .setFileEnabled(false)
                .setFileThreshold(LoggerVerbosity.INFO)
                .setConsoleThreshold(LoggerVerbosity.INFO);

        /* 1. Define the abstract domain */
        AbstractMemory initialDomain = new AbstractMemory();

        /* 2. Create an interpreter */
        DataFlowIntervalAbstractInterpreter interpreter =
                new DataFlowIntervalAbstractInterpreter();

//        /* 3. Example of building an intraprocedural analyzer */
//        var intraDataFlowAnalyzer = new IntraProceduralAnalyzer.Builder<>(initialDomain, interpreter, IntraAnalyzerMode.ANALYZE_ALL_INVOKED_METHODS)
//                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
//                .registerChecker(new ConstantValueChecker())
//                .registerChecker(new BoundsSafetyChecker())
//                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
//                .build();

//        /* 4. Example of building an interprocedural analyzer */
        SummaryFactory<AbstractMemory> summaryFactory = new DataFlowIntervalAnalysisSummaryFactory();
        var interDataFlowAnalyzer = new InterProceduralAnalyzer.Builder<>(initialDomain, interpreter, summaryFactory, InterAnalyzerMode.ANALYZE_FROM_ALL_ROOTS)
                .registerChecker(new ConstantValueChecker())
                .registerChecker(new IfConditionChecker())
                .maxCallStackDepth(128)
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .addMethodFilter(new SkipSvmMethodFilter())
                .build();

        /* 5. Register with manager */
        analyzerManager.registerAnalyzer(interDataFlowAnalyzer);
    }
}
