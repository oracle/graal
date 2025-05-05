package com.oracle.svm.hosted.analysis.ai.example.leaks.pair.intra;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.IntraProceduralAnalyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.SkipJavaLangAnalysisMethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.BooleanOrDomain;
import com.oracle.svm.hosted.analysis.ai.domain.CountDomain;
import com.oracle.svm.hosted.analysis.ai.domain.composite.PairDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.pair.LeaksPairDomainAbstractInterpreter;

public class PairDomainIntraAnalyzerWrapper {

    private final IntraProceduralAnalyzer<PairDomain<CountDomain, BooleanOrDomain>> analyzer;

    public PairDomainIntraAnalyzerWrapper() {
        analyzer = new IntraProceduralAnalyzer.Builder<>(
                new PairDomain<>(new CountDomain(1024), new BooleanOrDomain(false)),
                new LeaksPairDomainAbstractInterpreter())
                .addMethodFilter(new SkipJavaLangAnalysisMethodFilter())
                .build();
    }

    public Analyzer<PairDomain<CountDomain, BooleanOrDomain>> getAnalyzer() {
        return analyzer;
    }
}
