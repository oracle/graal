package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisServices;

import java.util.List;

/**
 * This class is responsible for running the abstract interpretation analyses,
 * that were configured by {@link AbstractInterpretationDriver}.
 */
public class AbstractInterpretationEngine {

    private final AnalyzerManager analyzerManager;
    private final List<AnalysisMethod> rootMethods;
    private final List<AnalysisMethod> invokedMethods;
    private final AnalysisMethod analysisRoot;

    public AbstractInterpretationEngine(AnalyzerManager analyzerManager, AnalysisMethod mainEntryPoint, Inflation bb) {
        var analysisServices = AnalysisServices.getInstance(bb);
        this.analyzerManager = analyzerManager;
        this.rootMethods = AnalysisUniverse.getCallTreeRoots(bb.getUniverse());
        this.analysisRoot = analysisServices.getMainMethod(mainEntryPoint);
        this.invokedMethods = analysisServices.getInvokedMethods();
    }

    public void executeAbstractInterpretation(AnalyzerMode analyzerMode) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        switch (analyzerMode) {
            case INTRA_ANALYZE_MAIN_ONLY -> {
                logger.log("Running intra-procedural analysis on main method only.", LoggerVerbosity.INFO);
            }
            case INTRA_ANALYZE_ALL_INVOKED_METHODS -> {
                logger.log("Running intra-procedural analysis on all potentially invoked methods.", LoggerVerbosity.INFO);
            }
            case INTER_ANALYZE_FROM_MAIN_ONLY -> {
                logger.log("Running inter-procedural analysis from main method only.", LoggerVerbosity.INFO);
            }
            case INTER_ANALYZE_FROM_ALL_ENTRY_POINTS -> {
                logger.log("Running inter-procedural analysis from all entry points of the call graph.", LoggerVerbosity.INFO);
            }
        }

        AnalyzerMode currentMode = analyzerMode;
        if (analyzerMode.isMainOnly() && (analysisRoot == null)) {
            logger.log("Main method not provided in 'main-only' abstract interpretation mode, defaulting to all invoked methods.", LoggerVerbosity.WARN);
            if (analyzerMode.isInterProcedural()) {
                currentMode = AnalyzerMode.INTER_ANALYZE_FROM_ALL_ENTRY_POINTS;
            } else {
                currentMode = AnalyzerMode.INTRA_ANALYZE_ALL_INVOKED_METHODS;
            }
        }

        for (var analyzer : analyzerManager.getAnalyzers()) {
            executeAnalyzer(analyzer, currentMode);
        }

        logger.log("Finished Abstract Interpretation, the output can be found at: " + logger.getLogFilePath(), LoggerVerbosity.INFO);
    }

    private void executeAnalyzer(Analyzer<?> analyzer, AnalyzerMode analyzerMode) {
        if (analyzer instanceof InterProceduralAnalyzer) {
            executeInterProceduralAnalysis(analyzer, analyzerMode);
            return;
        }
        executeIntraProceduralAnalysis(analyzer, analyzerMode);
    }

    private void executeIntraProceduralAnalysis(Analyzer<?> analyzer, AnalyzerMode analyzerMode) {
        if (analyzerMode.isMainOnly()) {
            analyzer.runAnalysis(analysisRoot);
            return;
        }

        invokedMethods.parallelStream().forEach(analyzer::runAnalysis);
    }

    private void executeInterProceduralAnalysis(Analyzer<?> analyzer, AnalyzerMode analyzerMode) {
        if (analyzerMode.isMainOnly()) {
            analyzer.runAnalysis(analysisRoot);
            return;
        }

        for (var method : rootMethods) {
            analyzer.runAnalysis(method);
        }
    }
}
