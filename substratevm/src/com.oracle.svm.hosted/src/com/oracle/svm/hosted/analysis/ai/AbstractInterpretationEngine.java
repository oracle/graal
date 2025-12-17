package com.oracle.svm.hosted.analysis.ai;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.analysis.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.AnalyzerManager;
import com.oracle.svm.hosted.analysis.ai.analysis.InterProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analysis.context.MethodGraphCache;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.InterAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.analysis.mode.IntraAnalyzerMode;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.analysis.AbstractInterpretationServices;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;

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
        invokedMethods.parallelStream().filter(method -> !analyzer.getMethodFilterManager().shouldSkipMethod(method)).forEach(analyzer::runAnalysis);
    }

    private <Domain extends AbstractDomain<Domain>> void executeInterProceduralAnalysis(InterProceduralAnalyzer<Domain> analyzer) {
        var logger = AbstractInterpretationLogger.getInstance();
        InterAnalyzerMode mode = analyzer.getAnalyzerMode();

        if (mode == InterAnalyzerMode.ANALYZE_FROM_MAIN_ENTRYPOINT) {
            logger.log("Running interprocedural analyzer from the main entry point only", LoggerVerbosity.INFO);
            analyzer.runAnalysis(analysisRoot);
            var methodGraphCache = analyzer.getAnalysisContext().getMethodGraphCache().getMethodGraphMap();
            var methodSummaryMap = analyzer.getSummaryManager().getSummaryRepository().getMethodSummaryMap();
            analyzer.getAnalysisContext().getCheckerManager().runCheckersOnMethodSummaries(methodSummaryMap, methodGraphCache);
            return;
        }

        // Compute per-root analyses in parallel, then merge results sequentially to avoid races.
        logger.log("Running interprocedural analyzer from all root methods", LoggerVerbosity.INFO);
        for (var root : rootMethods) {
            logger.log("Running analysis of root:" + root.getQualifiedName(), LoggerVerbosity.INFO);
            analyzer.runAnalysis(root);
        }

        var methodGraphCache = analyzer.getAnalysisContext().getMethodGraphCache().getMethodGraphMap();
        var methodSummaryMap = analyzer.getSummaryManager().getSummaryRepository().getMethodSummaryMap();

        // debugging purposes only additionally run the root manually if it wasn't found bt the interproc analysis
        if (!methodGraphCache.containsKey(analysisRoot)) {
            analyzer.runAnalysis(analysisRoot);
        }
        analyzer.getAnalysisContext().getCheckerManager().runCheckersOnMethodSummaries(methodSummaryMap, methodGraphCache);

        // wip: PARALLEL VERSION, for now it is slow af
        // Build local analyzers and run analyses in parallel
        //        java.util.List<InterProceduralAnalyzer<Domain>> locals = rootMethods
        //            .parallelStream()
        //            .map(root -> {
        //                if (root == null) {
        //                    return null;
        //                }
        //                InterProceduralAnalyzer<Domain> local =
        //                    new InterProceduralAnalyzer.Builder<>(
        //                            analyzer.getInitialDomain().copyOf(),
        //                            analyzer.getAbstractInterpreter(),
        //                            analyzer.getAnalysisContext().getSummaryFactory(),
        //                            analyzer.getAnalyzerMode())
        //                        .iteratorPolicy(analyzer.getAnalysisContext().getIteratorPolicy())
        //                        .checkerManager(analyzer.getAnalysisContext().getCheckerManager())
        //                        .methodFilterManager(analyzer.getAnalysisContext().getMethodFilterManager())
        //                        .maxRecursionDepth(analyzer.getMaxRecursionDepth())
        //                        .maxCallStackDepth(analyzer.getMaxCallStackDepth())
        //                        .build();
        //                local.runAnalysis(root);
        //                return local;
        //            })
        //            .filter(java.util.Objects::nonNull)
        //            .toList();
        //
        //        for (InterProceduralAnalyzer<Domain> local : locals) {
        //            analyzer.getSummaryManager().mergeAggregateOnly(local.getSummaryManager());
        //            analyzer.getAnalysisContext().getMethodGraphCache().joinWith(local.getAnalysisContext().getMethodGraphCache());
        //        }
        //  var methodGraphCache = analyzer.getAnalysisContext().getMethodGraphCache().getMethodGraphMap();
        //        var methodSummaryMap = analyzer.getSummaryManager().getSummaryRepository().getMethodSummaryMap();
        //        analyzer.getAnalysisContext().getCheckerManager().runCheckersOnMethodSummaries(methodSummaryMap, methodGraphCache);
    }

    private static final class InterResult<Domain extends AbstractDomain<Domain>> {
        private final SummaryManager<Domain> summaries;
        private final MethodGraphCache graphs;

        InterResult(SummaryManager<Domain> s, MethodGraphCache g) {
            this.summaries = s;
            this.graphs = g;
        }

        void merge(InterResult<Domain> other) {
            this.summaries.mergeFrom(other.summaries);
            this.graphs.joinWith(other.graphs);
        }

        public MethodGraphCache getMethodGraphCache() {
            return graphs;
        }

        public SummaryManager<Domain> getSummaryManager() {
            return summaries;
        }
    }
}
