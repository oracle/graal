package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralConcurrentAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.inter.InterProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralConcurrentAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.intra.IntraProceduralSequentialAnalyzer;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.debug.DebugContext;

public class AnalyzerFactory {

    public static <Domain extends AbstractDomain<Domain>> InterProceduralSequentialAnalyzer<Domain> createInterProceduralSequentialAnalyzer(DebugContext debug) {
        return new InterProceduralSequentialAnalyzer<>(debug);
    }

    public static <Domain extends AbstractDomain<Domain>> InterProceduralConcurrentAnalyzer<Domain> createInterProceduralConcurrentAnalyzer(DebugContext debug) {
        return new InterProceduralConcurrentAnalyzer<>(debug);
    }

    public static <Domain extends AbstractDomain<Domain>> IntraProceduralSequentialAnalyzer<Domain> createIntraProceduralSequentialAnalyzer(DebugContext debug) {
        return new IntraProceduralSequentialAnalyzer<>(debug);
    }

    public static <Domain extends AbstractDomain<Domain>> IntraProceduralConcurrentAnalyzer<Domain> createIntraProceduralConcurrentAnalyzer(DebugContext debug) {
        return new IntraProceduralConcurrentAnalyzer<>(debug);
    }
}