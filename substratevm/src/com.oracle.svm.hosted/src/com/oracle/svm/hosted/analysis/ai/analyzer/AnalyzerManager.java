package com.oracle.svm.hosted.analysis.ai.analyzer;

import java.util.ArrayList;
import java.util.List;

/**
 * A wrapper around the list of {@link Analyzer}s,
 */
public class AnalyzerManager {

    private final List<Analyzer<?>> analyzers = new ArrayList<>();

    public void registerAnalyzer(Analyzer<?> analyzer) {
        analyzers.add(analyzer);
    }

    public List<Analyzer<?>> getAnalyzers() {
        return analyzers;
    }
}
