package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analyzer.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.analyzer.mode.IntraAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationServices;

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
        var analysisServices = AbstractInterpretationServices.getInstance(bb);
        this.analyzerManager = analyzerManager;
        this.rootMethods = AnalysisUniverse.getCallTreeRoots(bb.getUniverse());
        this.analysisRoot = analysisServices.getMainMethod(mainEntryPoint).orElse(null);
        this.invokedMethods = analysisServices.getInvokedMethods();
    }

    /* TODO: remove/refactor analyzerMode to not contain intra/inter */
    public void executeAbstractInterpretation() {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        for (var analyzer : analyzerManager.getAnalyzers()) {
            executeAnalyzer(analyzer);
        }
        logger.close();
    }

    private void executeAnalyzer(Analyzer<?> analyzer) {
        if (analyzer instanceof InterProceduralAnalyzer<?> interProceduralAnalyzer) {
            executeInterProceduralAnalysis(interProceduralAnalyzer);
            return;
        }
        if (analyzer instanceof IntraProceduralAnalyzer<?> intraProceduralAnalyzer) {
            executeIntraProceduralAnalysis(intraProceduralAnalyzer);
            return;
        }
        AnalysisError.shouldNotReachHere("The provided absint analyzer is neither an Intra nor Inter procedural analyzer");
    }

    private void executeIntraProceduralAnalysis(IntraProceduralAnalyzer<?> analyzer) {
        var logger = AbstractInterpretationLogger.getInstance();
        IntraAnalyzerMode mode = analyzer.getAnalyzerMode();
        if (mode == IntraAnalyzerMode.ANALYZE_MAIN_ENTRYPOINT_ONLY && analysisRoot != null) {
            logger.log("Running intraprocedural analyzer on the main entry point only", LoggerVerbosity.INFO);
            AbstractInterpretationServices.getInstance().markMethodTouched(analysisRoot);
            analyzer.runAnalysis(analysisRoot);
            return;
        }

        logger.log("Running intraprocedural analyzer on all trivially invoked methods", LoggerVerbosity.INFO);
        invokedMethods.parallelStream().forEach(m -> {
            AbstractInterpretationServices.getInstance().markMethodTouched(m);
            analyzer.runAnalysis(m);
        });
    }

    private void executeInterProceduralAnalysis(InterProceduralAnalyzer<?> analyzer) {
        var logger = AbstractInterpretationLogger.getInstance();
        InterAnalyzerMode mode = analyzer.getAnalyzerMode();

        if (mode == InterAnalyzerMode.ANALYZE_FROM_MAIN_ENTRYPOINT && analysisRoot != null) {
            logger.log("Running interprocedural analyzer from the main entry point only", LoggerVerbosity.INFO);
            AbstractInterpretationServices.getInstance().markMethodTouched(analysisRoot);
            analyzer.runAnalysis(analysisRoot);
            return;
        }

        logger.log("Running interprocedural analyzer from all root methods", LoggerVerbosity.INFO);
        for (var method : rootMethods) {
            AbstractInterpretationServices.getInstance().markMethodTouched(method);
            analyzer.runAnalysis(method);
        }
    }
}
