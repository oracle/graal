package com.oracle.svm.hosted.analysis.ai.analysis;

import java.util.ArrayList;
import java.util.List;

public final class AnalyzerManager {

    private final List<Analyzer<?>> analyzers = new ArrayList<>();

    public void registerAnalyzer(Analyzer<?> analyzer) {
        analyzers.add(analyzer);
    }

    public List<Analyzer<?>> getAnalyzers() {
        return analyzers;
    }
}
