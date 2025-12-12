package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analysis.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analysis.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.IntraAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.analysis.AbstractInterpretationServices;

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

    public void executeAbstractInterpretation() {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        for (var analyzer : analyzerManager.getAnalyzers()) {
            executeAnalyzer(analyzer);
        }
        logger.close();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void executeAnalyzer(Analyzer<?> analyzer) {
        if (analyzer instanceof InterProceduralAnalyzer<?> interProceduralAnalyzer) {
            executeInterProceduralAnalysis((InterProceduralAnalyzer) interProceduralAnalyzer);
            return;
        }
        if (analyzer instanceof IntraProceduralAnalyzer<?> intraProceduralAnalyzer) {
            executeIntraProceduralAnalysis(intraProceduralAnalyzer);
            return;
        }
        throw new RuntimeException("The provided absint analyzer is neither an Intra nor Inter procedural analyzer");
    }

    private void executeIntraProceduralAnalysis(IntraProceduralAnalyzer<?> analyzer) {
        var logger = AbstractInterpretationLogger.getInstance();
        IntraAnalyzerMode mode = analyzer.getAnalyzerMode();
        if (mode == IntraAnalyzerMode.ANALYZE_MAIN_ENTRYPOINT_ONLY) {
            logger.log("Running intraprocedural analyzer on the main entry point only", LoggerVerbosity.INFO);
            analyzer.runAnalysis(analysisRoot);
            return;
        }

        logger.log("Running intraprocedural analyzer on all trivially invoked methods", LoggerVerbosity.INFO);
        invokedMethods.parallelStream().forEach(analyzer::runAnalysis);
    }

    private <Domain extends AbstractDomain<Domain>> void executeInterProceduralAnalysis(InterProceduralAnalyzer<Domain> analyzer) {
        var logger = AbstractInterpretationLogger.getInstance();
        InterAnalyzerMode mode = analyzer.getAnalyzerMode();

        if (mode == InterAnalyzerMode.ANALYZE_FROM_MAIN_ENTRYPOINT) {
            logger.log("Running interprocedural analyzer from the main entry point only", LoggerVerbosity.INFO);

            // Run analysis only from the single main entry point using the provided analyzer.
            analyzer.runAnalysis(analysisRoot);

            var methodGraphCache = analyzer.getAnalysisContext().getMethodGraphCache().getMethodGraphMap();
            var methodSummaryMap = analyzer.getSummaryManager().getSummaryRepository().getMethodSummaryMap();
            analyzer.getAnalysisContext().getCheckerManager().runCheckersOnMethodSummaries(methodSummaryMap, methodGraphCache);
            return;
        }

        // In the multi-root mode we intentionally avoid sharing the same InterProceduralAnalyzer
        // instance across call-graph roots. The analyzer (and in particular its SummaryManager
        // and AnalysisContext) is not designed to be thread-safe, and sharing it across roots
        // would lead to concurrent modification of method summaries and abstract domains.
        //
        // Instead, we run a separate analysis for each root in sequence, each time creating a
        // fresh InterProceduralAnalyzer instance that has its own SummaryManager and
        // AnalysisContext. If we later want global, cross-root reasoning, we can extend the
        // SummaryManager with an explicit merge API.

        logger.log("Running interprocedural analyzer separately from each root method (sequential)", LoggerVerbosity.INFO);

        for (AnalysisMethod root : rootMethods) {
            if (root == null) {
                continue;
            }

            // Rebuild a new analyzer instance for this root using the same configuration
            // as the original analyzer. The concrete builder type is not directly
            // accessible here, so for now we reuse the single analyzer instance and run
            // it per root sequentially. This keeps the execution free of concurrent
            // mutations in the summary/domain state.
            analyzer.runAnalysis(root);
        }

        var methodGraphCache = analyzer.getAnalysisContext().getMethodGraphCache().getMethodGraphMap();
        var methodSummaryMap = analyzer.getSummaryManager().getSummaryRepository().getMethodSummaryMap();
        analyzer.getAnalysisContext().getCheckerManager().runCheckersOnMethodSummaries(methodSummaryMap, methodGraphCache);
    }
}
