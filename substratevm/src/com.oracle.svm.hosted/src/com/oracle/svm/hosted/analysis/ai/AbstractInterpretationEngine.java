package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.SvmUtility;

import java.io.IOException;
import java.util.List;

/**
 * This class is responsible for running the abstract interpretation analyses,
 * that were configured by {@link AbstractInterpretationDriver}.
 */
public class AbstractInterpretationEngine {

    private final AnalyzerManager analyzerManager; /* Wrapper for used analyzers */
    private final List<AnalysisMethod> rootMethods; /* Roots of the call-graph */
    private final List<AnalysisMethod> invokedMethods; /* Methods that may-be invoked according to points-to analysis */
    private boolean analyzeMainOnly; /* If true, only the main method will be analyzed */
    private AnalysisMethod root = null; /* The main method, if present */

    public AbstractInterpretationEngine(AnalyzerManager analyzerManager, Inflation inflation) {

        AnalysisUniverse universe = inflation.getUniverse();
        this.analyzerManager = analyzerManager;
        this.analyzeMainOnly = true;
        this.rootMethods = AnalysisUniverse.getCallTreeRoots(universe);
        this.invokedMethods = universe.getMethods().stream().filter(AnalysisMethod::isSimplyImplementationInvoked).toList();

        /* TODO: what if I have multiple of public static void main(String[] args) ? */
        inflation.getUniverse().getMethods().forEach(method -> {
            if (method.getName().equals("main") && method.getParameters().length == 1 && method.toParameterList().getFirst().getWrapped().getName().equals("[Ljava/lang/String;")) {
                this.root = method;
            }
        });

        SvmUtility.getInstance(inflation);
    }

    public void setAnalyzeMainOnly(boolean analyzeMainOnly) {
        this.analyzeMainOnly = analyzeMainOnly;
    }

    public void execute() throws IOException {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
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
            executeInterProceduralAnalysis(analyzer);
        } else {
            executeIntraProceduralAnalysis(analyzer);
        }
    }

    /**
     * If there is a main method defined, run the intra-procedural analysis on it.
     * Otherwise, run the intra-procedural analysis on all methods that can be potentially invoked.
     *
     * @param analyzer the analyzer to be used for the analysis.
     */
    private void executeIntraProceduralAnalysis(Analyzer<?> analyzer) {
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
    private void executeInterProceduralAnalysis(Analyzer<?> analyzer) {
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
