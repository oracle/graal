package com.oracle.svm.hosted.analysis.ai.analyses.leaks.set.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.checker.ResourceLeaksChecker;
import com.oracle.svm.hosted.analysis.ai.domain.util.SetDomain;
import com.oracle.svm.hosted.analysis.ai.analyses.leaks.set.LeaksIdSetAbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.analyses.leaks.set.ResourceId;

public class LeaksIdSetIntraAnalyzerWrapper {

    private final IntraProceduralAnalyzer<SetDomain<ResourceId>> analyzer;

    public LeaksIdSetIntraAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new SetDomain<>(),
                new LeaksIdSetAbstractInterpreter())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .registerChecker(new ResourceLeaksChecker())
                .build();
    }

    public Analyzer<SetDomain<ResourceId>> getAnalyzer() {
        return analyzer;
    }
}

