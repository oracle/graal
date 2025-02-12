package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralConcurrentAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralConcurrentAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Factory class for creating different types of analyzers
 */
public final class AnalyzerFactory {

    public static <Domain extends AbstractDomain<Domain>> InterProceduralConcurrentAnalyzer<Domain> createInterProceduralConcurrentAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory) {
        return new InterProceduralConcurrentAnalyzer<>(root, debug, summaryFactory);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralConcurrentAnalyzer<Domain> createInterProceduralConcurrentAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory,
            IteratorPolicy iteratorPolicy) {
        return new InterProceduralConcurrentAnalyzer<>(root, debug, summaryFactory, iteratorPolicy);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralConcurrentAnalyzer<Domain> createInterProceduralConcurrentAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory,
            AnalysisMethodFilterManager methodFilterManager) {
        return new InterProceduralConcurrentAnalyzer<>(root, debug, summaryFactory, methodFilterManager);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralConcurrentAnalyzer<Domain> createInterProceduralConcurrentAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory,
            IteratorPolicy iteratorPolicy,
            AnalysisMethodFilterManager methodFilterManager) {
        return new InterProceduralConcurrentAnalyzer<>(root, debug, summaryFactory, iteratorPolicy, methodFilterManager);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralSequentialAnalyzer<Domain> createInterProceduralSequentialAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory) {
        return new InterProceduralSequentialAnalyzer<>(root, debug, summaryFactory);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralSequentialAnalyzer<Domain> createInterProceduralSequentialAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory,
            IteratorPolicy iteratorPolicy) {
        return new InterProceduralSequentialAnalyzer<>(root, debug, summaryFactory, iteratorPolicy);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralSequentialAnalyzer<Domain> createInterProceduralSequentialAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory,
            AnalysisMethodFilterManager methodFilterManager) {
        return new InterProceduralSequentialAnalyzer<>(root, debug, summaryFactory, methodFilterManager);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralSequentialAnalyzer<Domain> createInterProceduralSequentialAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            SummaryFactory<Domain> summaryFactory,
            IteratorPolicy iteratorPolicy,
            AnalysisMethodFilterManager methodFilterManager) {
        return new InterProceduralSequentialAnalyzer<>(root, debug, summaryFactory, iteratorPolicy, methodFilterManager);
    }

    public static <Domain extends AbstractDomain<Domain>> IntraProceduralConcurrentAnalyzer<Domain> createIntraProceduralConcurrentAnalyzer(
            AnalysisMethod root,
            DebugContext debug) {
        return new IntraProceduralConcurrentAnalyzer<>(root, debug);
    }

    public static <Domain extends AbstractDomain<Domain>> IntraProceduralConcurrentAnalyzer<Domain> createIntraProceduralConcurrentAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            IteratorPolicy iteratorPolicy) {
        return new IntraProceduralConcurrentAnalyzer<>(root, debug, iteratorPolicy);
    }

    public static <Domain extends AbstractDomain<Domain>> IntraProceduralSequentialAnalyzer<Domain> createIntraProceduralSequentialAnalyzer(
            AnalysisMethod root,
            DebugContext debug) {
        return new IntraProceduralSequentialAnalyzer<>(root, debug);
    }

    public static <Domain extends AbstractDomain<Domain>> IntraProceduralSequentialAnalyzer<Domain> createIntraProceduralSequentialAnalyzer(
            AnalysisMethod root,
            DebugContext debug,
            IteratorPolicy iteratorPolicy) {
        return new IntraProceduralSequentialAnalyzer<>(root, debug, iteratorPolicy);
    }
}