package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.ProgressReporter;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.aif.dataflow.DataFlowIntervalAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.aif.dataflow.inter.DataFlowIntervalAnalysisSummaryFactory;
import com.oracle.svm.hosted.analysis.ai.analysis.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analysis.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.SkipMicronautMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analysis.methodfilter.SkipSpringMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.IntraAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.IfConditionChecker;
import com.oracle.svm.hosted.analysis.ai.checker.checkers.ConstantValueChecker;
import com.oracle.svm.hosted.analysis.ai.domain.memory.AbstractMemory;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.exception.AbstractInterpretationException;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.OptionValues;

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
    private final OptionValues options;

    public AbstractInterpretationDriver(DebugContext debug, AnalysisMethod mainEntryPoint, Inflation bb, OptionValues options) {
        this.debug = debug;
        this.options = options;
        this.analyzerManager = new AnalyzerManager();
        this.engine = new AbstractInterpretationEngine(analyzerManager, mainEntryPoint, bb, debug);
    }

    /* To see the output of the abstract interpretation, run with -H:Log=AbstractInterpretation */
    @SuppressWarnings("try")
    public void run() {
        try (ProgressReporter.ReporterClosable _ = ProgressReporter.singleton().printAbstractInterpretation()) {
            /* Creating a new scope for logging, run with -H:Log=AbstractInterpretation to activate it */
            try (var _ = debug.scope("AbstractInterpretation")) {
                prepareAnalyses();
                engine.executeAbstractInterpretation();
            } catch (AbstractInterpretationException e) {
                if (AIFOptions.FailOnAnalysisError.getValue(options)) {
                    throw new RuntimeException("Abstract interpretation failed", e);
                }
                debug.log("Abstract interpretation encountered a runtime error: ", e);
            }
        }
    }

    /**
     * This is the entry method for setting up analyses in GraalAF.
     * Configuration is driven by {@link AIFOptions}.
     */
    private void prepareAnalyses() {
        LoggerVerbosity verbosity = parseLogLevel(AIFOptions.AILogLevel.getValue(options));
        String logFilePath = AIFOptions.AILogToFile.getValue(options)
            ? AIFOptions.AILogFilePath.getValue(options)
            : "GraalAF";

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance(logFilePath, verbosity)
                .setConsoleEnabled(AIFOptions.AILogToConsole.getValue(options))
                .setFileEnabled(AIFOptions.AILogToFile.getValue(options))
                .setGraphIgvDumpEnabled(AIFOptions.AIEnableIGVDump.getValue(options))
                .setFileThreshold(verbosity);

        /* 1. Define the abstract domain */
        AbstractMemory initialDomain = new AbstractMemory();

        /* 2. Create an interpreter */
        DataFlowIntervalAbstractInterpreter interpreter = new DataFlowIntervalAbstractInterpreter();

        /* 3. Register checkers based on enabled optimizations */

        // Build intraprocedural analyzer if enabled
        if (AIFOptions.IntraproceduralAnalysis.getValue(options)) {
            var intraBuilder = new IntraProceduralAnalyzer.Builder<>(
                initialDomain,
                interpreter,
                IntraAnalyzerMode.ANALYZE_ALL_INVOKED_METHODS
            );

            configureCheckers(intraBuilder);
            configureFilters(intraBuilder);

            analyzerManager.registerAnalyzer(intraBuilder.build());
        }

        // Build interprocedural analyzer if enabled
        if (AIFOptions.InterproceduralAnalysis.getValue(options)) {
            SummaryFactory<AbstractMemory> summaryFactory = new DataFlowIntervalAnalysisSummaryFactory();

            var interBuilder = new InterProceduralAnalyzer.Builder<>(
                initialDomain,
                interpreter,
                summaryFactory,
                InterAnalyzerMode.ANALYZE_FROM_ALL_ROOTS
            )
            .maxRecursionDepth(AIFOptions.MaxRecursionDepth.getValue(options))
            .maxCallStackDepth(AIFOptions.MaxCallStackDepth.getValue(options));

            configureCheckers(interBuilder);
            configureFilters(interBuilder);

            analyzerManager.registerAnalyzer(interBuilder.build());
        }
    }

    private <T> void configureCheckers(T builder) {
        if (AIFOptions.EnableConstantPropagation.getValue(options)) {
            if (builder instanceof IntraProceduralAnalyzer.Builder) {
                ((IntraProceduralAnalyzer.Builder<?>) builder).registerChecker(new ConstantValueChecker());
            } else if (builder instanceof InterProceduralAnalyzer.Builder) {
                ((InterProceduralAnalyzer.Builder<?>) builder).registerChecker(new ConstantValueChecker());
            }
        }

        if (AIFOptions.EnableDeadBranchElimination.getValue(options)) {
            if (builder instanceof IntraProceduralAnalyzer.Builder) {
                ((IntraProceduralAnalyzer.Builder<?>) builder).registerChecker(new IfConditionChecker());
            } else if (builder instanceof InterProceduralAnalyzer.Builder) {
                ((InterProceduralAnalyzer.Builder<?>) builder).registerChecker(new IfConditionChecker());
            }
        }
    }

    private <T> void configureFilters(T builder) {
        if (AIFOptions.SkipJavaLangMethods.getValue(options)) {
            if (builder instanceof IntraProceduralAnalyzer.Builder) {
                ((IntraProceduralAnalyzer.Builder<?>) builder).addMethodFilter(new SkipJavaLangAnalysisMethodFilter());
            } else if (builder instanceof InterProceduralAnalyzer.Builder) {
                ((InterProceduralAnalyzer.Builder<?>) builder).addMethodFilter(new SkipJavaLangAnalysisMethodFilter());
            }
        }

        if (AIFOptions.SkipMicronautMethods.getValue(options)) {
            if (builder instanceof IntraProceduralAnalyzer.Builder) {
                ((IntraProceduralAnalyzer.Builder<?>) builder).addMethodFilter(new SkipMicronautMethodFilter());
            } else if (builder instanceof InterProceduralAnalyzer.Builder) {
                ((InterProceduralAnalyzer.Builder<?>) builder).addMethodFilter(new SkipMicronautMethodFilter());
            }
        }

        if (AIFOptions.SkipSpringMethods.getValue(options)) {
            if (builder instanceof IntraProceduralAnalyzer.Builder) {
                ((IntraProceduralAnalyzer.Builder<?>) builder).addMethodFilter(new SkipSpringMethodFilter());
            } else if (builder instanceof InterProceduralAnalyzer.Builder) {
                ((InterProceduralAnalyzer.Builder<?>) builder).addMethodFilter(new SkipSpringMethodFilter());
            }
        }
    }

    /**
     * Parse log level string to LoggerVerbosity enum
     */
    private LoggerVerbosity parseLogLevel(String level) {
        return switch (level.toUpperCase()) {
            case "SILENT" -> LoggerVerbosity.SILENT;
            case "ERROR" -> LoggerVerbosity.ERROR;
            case "WARN", "WARNING" -> LoggerVerbosity.WARN;
            case "DEBUG" -> LoggerVerbosity.DEBUG;
            default -> LoggerVerbosity.INFO;
        };
    }
}
