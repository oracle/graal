package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.DataFlowIntervalAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyses.dataflow.inter.DataFlowIntervalAnalysisSummaryFactory;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.ConstantValueChecker;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.IndexSafetyChecker;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.util.AbsintException;
import jdk.graal.compiler.debug.DebugContext;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;

/**
 * The entry point of the abstract interpretation framework.
 * This class is responsible for all the necessary setup and configuration of the framework, which will then be executed
 * The most important component of the framework is the {@link AnalyzerManager}, which manages all registered analyzers
 * and coordinates their execram being analyzed.
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
            } catch (AbsintException e) {
                debug.log("Abstract interpretation encountered a runtime error: ", e);
            }
        }
    }

    /**
     * This is the entry method for setting up analyses in GraalAF.
     * We can:
     * 1. Register {@link Analyzer} instances to the {@link AnalyzerManager}.
     * 2. Create and configure the {@link AbstractInterpretationLogger}.
     */
    private void prepareAnalyses() {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance("GraalAF", LoggerVerbosity.DEBUG)
                .setConsoleEnabled(false)             /* only write to file */
                .setFileEnabled(true)                /* ensure file logging is on */
                .setFileThreshold(LoggerVerbosity.DEBUG)
                .setConsoleThreshold(LoggerVerbosity.INFO); /* irrelevant since console disabled */
        debug.log("Abstract Interpretation Logger initialized: %s", logger.getLogFilePath());

        /* 1. Define the abstract domain */
        AbstractMemory initialDomain = new AbstractMemory();

        /* 2. Create an interpreter */
        DataFlowIntervalAbstractInterpreter interpreter =
                new DataFlowIntervalAbstractInterpreter();

        /* 3. Example of building an intraprocedural analyzer */
//        var intraDataFlowAnalyzer = new IntraProceduralAnalyzer.Builder<>(initialDomain, interpreter, IntraAnalyzerMode.ANALYZE_MAIN_ENTRYPOINT_ONLY)
//                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
//                .registerChecker(new ConstantValueChecker())
//                .registerChecker(new IndexSafetyChecker())
//                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
//                .build();

        /* 4. Example of building an interprocedural analyzer */
        SummaryFactory<AbstractMemory> summaryFactory = new DataFlowIntervalAnalysisSummaryFactory();
        var interDataFlowAnalyzer = new InterProceduralAnalyzer.Builder<>(initialDomain, interpreter, summaryFactory, InterAnalyzerMode.ANALYZE_FROM_MAIN_ENTRYPOINT)
                .iteratorPolicy(IteratorPolicy.DEFAULT_FORWARD_WTO)
                .registerChecker(new ConstantValueChecker())
                .registerChecker(new IndexSafetyChecker())
                .maxRecursionDepth(64)
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();

        /* 5. Register with manager */
        analyzerManager.registerAnalyzer(interDataFlowAnalyzer);
        // TODO: right now we need to think of a way to :
        // 1. When to apply checkers during interprocedural analysis (right now we run checkers everytime after absint)
        // 2. When to export graphs to json and also when to dump graphs to IGV interprocedural analysis ( we export everytime we reach a method )
        // 3. (This is tied together with 1.) If analysis of a method yields facts that will lead to modification of the given StructuredGraph of a method
        //          when should we really modify the method ? It is not safe to modify it becase there could be other calls (with different parameters) that
        //          would not produce given facts
        // 4. Find optimizations that can either imporove performance or reduce the size of native images that we can do with out analyses
    }
}
