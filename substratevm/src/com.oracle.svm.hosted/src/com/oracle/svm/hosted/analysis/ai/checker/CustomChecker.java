package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This is a custom checker that is able to run an analysis using the provided analyzer.
 * instead of using checkers with already predefined behavior, this checker allows the user to define their own analysis.
 * @param <Domain> type of the derived AbstractDomain
 */

public class CustomChecker<Domain extends AbstractDomain<Domain>> implements Checker {
    private final Analyzer<Domain> analyzer;
    private final DebugContext debug;

    public CustomChecker(Analyzer<Domain> analyzer, DebugContext debug) {
        this.analyzer = analyzer;
        this.debug = debug;
    }

    public Environment<Domain> runAnalysis() {
        debug.log("\t" + "Running the analysis");
        var analysisResult = analyzer.analyze();
        debug.log("\t" + "Analysis result: " + analysisResult);
        return analysisResult;
    }
}