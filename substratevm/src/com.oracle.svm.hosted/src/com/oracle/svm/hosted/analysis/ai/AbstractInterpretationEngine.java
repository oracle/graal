package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.config.AbsintMode;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;

import java.io.IOException;
import java.util.List;

/**
 * This class is responsible for running the abstract interpretation analyses,
 * that were configured by {@link AbstractInterpretationDriver}.
 */
public class AbstractInterpretationEngine {

    private final AnalyzerManager analyzerManager;
    private final List<AnalysisMethod> rootMethods;
    private final List<AnalysisMethod> invokedMethods;
    private final AnalysisMethod root;
    private final Inflation bb;

    public AbstractInterpretationEngine(AnalyzerManager analyzerManager, AnalysisMethod root, Inflation bb) {
        this.analyzerManager = analyzerManager;
        this.root = root;
        this.bb = bb;
        this.rootMethods = AnalysisUniverse.getCallTreeRoots(bb.getUniverse());
        this.invokedMethods = bb.getUniverse().getMethods().stream().filter(AnalysisMethod::isSimplyImplementationInvoked).toList();
    }

    public void executeAbstractInterpretation(AbsintMode absintMode) throws IOException {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        switch (absintMode) {
            case INTRA_ANALYZE_MAIN_ONLY -> {
                logger.log("Running intra-procedural analysis on main method only.", LoggerVerbosity.INFO);

            }
            case INTRA_ANALYZE_ALL_INVOKED_METHODS -> {
                logger.log("Running intra-procedural analysis on all potentially invoked methods.", LoggerVerbosity.INFO);
            }
            case INTER_ANALYZE_MAIN_ONLY -> {
                logger.log("Running inter-procedural analysis from main method only.", LoggerVerbosity.INFO);
            }
            case INTER_ANALYZE_ALL_ENTRY_POINTS -> {
                logger.log("Running inter-procedural analysis from all entry points of the call graph.", LoggerVerbosity.INFO);
            }
        }

        if (absintMode.isMainOnly() && (root == null)) {
            AnalysisError.shouldNotReachHere("Main method not provided in 'main-only' mode");
        }

        for (var analyzer : analyzerManager.getAnalyzers()) {
            executeAnalyzer(analyzer, absintMode);
        }

        logger.log("Finished Abstract Interpretation, the output can be found at: " + logger.getLogFilePath(), LoggerVerbosity.INFO);
    }

    private void executeAnalyzer(Analyzer<?> analyzer, AbsintMode absintMode) {
        if (analyzer instanceof InterProceduralAnalyzer) {
            executeInterProceduralAnalysis(analyzer, absintMode);
            return;
        }
        executeIntraProceduralAnalysis(analyzer, absintMode);
    }

    private void executeIntraProceduralAnalysis(Analyzer<?> analyzer, AbsintMode absintMode) {
        if (absintMode.isMainOnly()) {
            try {
                analyzer.runAnalysis(root);
                return;
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere("IOException during analysis of main method", e);
            }
        }

        invokedMethods.parallelStream().forEach(method -> {
            try {
                analyzer.runAnalysis(method);
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere("IOException during analysis of main method", e);
            }
        });
    }

    private void executeInterProceduralAnalysis(Analyzer<?> analyzer, AbsintMode absintMode) {
        if (absintMode.isMainOnly()) {
            try {
                analyzer.runAnalysis(root);
                return;
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere("IOException during analysis of main method", e);
            }
        }

        for (var method : rootMethods) {
            try {
                analyzer.runAnalysis(method);
            } catch (IOException e) {
                AnalysisError.shouldNotReachHere("IOException during analysis of main method", e);
            }
        }
    }
}
