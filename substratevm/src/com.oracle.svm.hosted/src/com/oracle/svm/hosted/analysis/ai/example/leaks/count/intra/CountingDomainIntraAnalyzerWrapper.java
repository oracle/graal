package com.oracle.svm.hosted.analysis.ai.example.leaks.count.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.count.LeaksCountingDomainAbstractInterpreter;

/**
 * Example of a simple intra-procedural leaks analysis domain that
 * counts the number of FileInputStream objects opened in a analysisMethod.
 */
public class CountingDomainIntraAnalyzerWrapper {

    private final IntraProceduralAnalyzer<CountDomain> analyzer;

    public CountingDomainIntraAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new CountDomain(1024),
                new LeaksCountingDomainAbstractInterpreter())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public Analyzer<CountDomain> getAnalyzer() {
        return analyzer;
    }
}
