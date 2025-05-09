package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.BigBangUtil;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;
import java.util.List;

/**
 * This class is responsible for running the abstract interpretation analyses,
 * that were configured by {@link AbstractInterpretationDriver}.
 */

// TODO: set an option that can set from where to run the analysis
public class AbstractInterpretationEngine {

    private final AnalyzerManager analyzerManager;
    private final AnalysisMethod root; /* The main method of the analyzed program */
    private final DebugContext debug;
    private final List<AnalysisMethod> rootMethods; /* Roots of the call-graph */
    private final List<AnalysisMethod> invokedMethods; /* Methods that may-be invoked according to points-to analysis */
    private boolean analyzeMainOnly; // New field to control analysis mode

    public AbstractInterpretationEngine(AnalyzerManager analyzerManager,
                                        AnalysisMethod root,
                                        DebugContext debug,
                                        BigBang bigBang) { // Updated constructor
        this.analyzerManager = analyzerManager;
        this.root = root;
        this.debug = debug;
        this.analyzeMainOnly = true;
        var universe = bigBang.getUniverse();
        this.rootMethods = AnalysisUniverse.getCallTreeRoots(universe);
        this.invokedMethods = universe.getMethods().stream().filter(AnalysisMethod::isSimplyImplementationInvoked).toList();
        /* Initialize resolvedJavaTypeUtil so that the developers can use it in the analyses */
        BigBangUtil.getInstance(bigBang);
    }

    public void setAnalyzeMainOnly(boolean analyzeMainOnly) {
        this.analyzeMainOnly = analyzeMainOnly;
    }

    public void execute() throws IOException {
        AbstractInterpretationLogger logger;
        try {
            logger = AbstractInterpretationLogger.getInstance();
            logger.log("Starting Abstract Interpretation Engine", LoggerVerbosity.INFO);
        } catch (Exception e) {
            logger = AbstractInterpretationLogger.getInstance(debug, LoggerVerbosity.INFO);
        }

        if (analyzeMainOnly && root == null) {
            logger.log("Analysis terminated: Main method not provided in 'main-only' mode.", LoggerVerbosity.CHECKER_ERR);
            throw new IllegalStateException("Main method not provided in 'main-only' mode.");
        }

        for (var analyzer : analyzerManager.getAnalyzers()) {
            executeAnalyzer(analyzer);
        }

        logger.log("Finished Abstract Interpretation, the output can be found at: " + logger.getLogFilePath(), LoggerVerbosity.INFO);
    }

    private void executeAnalyzer(Analyzer<?> analyzer) {
        if (analyzer instanceof InterProceduralAnalyzer) {
            executeInterProcedurally(analyzer);
        } else {
            executeIntraProcedurally(analyzer);
        }
    }

    /**
     * If there is a main method defined, run the intra-procedural analysis on it.
     * Otherwise, run the intra-procedural analysis on all methods that can be potentially invoked.
     *
     * @param analyzer the analyzer to be used for the analysis.
     */
    private void executeIntraProcedurally(Analyzer<?> analyzer) {
        if (analyzeMainOnly) {
            try {
                analyzer.runAnalysis(root);
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("No root method found for intra-procedural analysis. Running analysis on all potentially invoked methods instead", LoggerVerbosity.INFO);
        invokedMethods.parallelStream().forEach(method -> {
            try {
                analyzer.runAnalysis(method);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * If there is a main method defined, run the inter-procedural analysis from main it.
     * Else run the inter-procedural analysis from all roots of the call graph.
     *
     * @param analyzer the analyzer to be used for the analysis.
     */
    private void executeInterProcedurally(Analyzer<?> analyzer) {
        if (analyzeMainOnly) {
            try {
                analyzer.runAnalysis(root);
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("No root method found for inter-procedural analysis. Running analysis from all roots of the call graph instead", LoggerVerbosity.SUMMARY);
        for (var method : rootMethods) {
            try {
                analyzer.runAnalysis(method);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
