package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralConcurrentAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralConcurrentAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.MethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

public class AnalyzerFactory {

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createInterProceduralConcurrentAnalyzer(
            AnalysisMethod root, DebugContext debug, SummarySupplier<Domain> summarySupplier) {
        return new InterProceduralConcurrentAnalyzer<>(root, debug, summarySupplier);
    }

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createInterProceduralConcurrentAnalyzer(
            AnalysisMethod root, DebugContext debug, SummarySupplier<Domain> summarySupplier, MethodFilter methodFilter) {
        return new InterProceduralConcurrentAnalyzer<>(root, debug, summarySupplier, methodFilter);
    }

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createInterProceduralSequentialAnalyzer(
            AnalysisMethod root, DebugContext debug, SummarySupplier<Domain> summarySupplier) {
        return new InterProceduralSequentialAnalyzer<>(root, debug, summarySupplier);
    }

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createIntraProceduralConcurrentAnalyzer(
            AnalysisMethod root, DebugContext debug) {
        return new IntraProceduralConcurrentAnalyzer<>(root, debug);
    }

    public static <Domain extends AbstractDomain<Domain>> Analyzer<Domain> createIntraProceduralSequentialAnalyzer(
            AnalysisMethod root, DebugContext debug) {
        return new IntraProceduralSequentialAnalyzer<>(root, debug);
    }
}