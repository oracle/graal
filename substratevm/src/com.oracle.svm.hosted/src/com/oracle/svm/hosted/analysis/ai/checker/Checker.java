package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.debug.DebugContext;

public class Checker<Domain extends AbstractDomain<Domain>> {
    private final Analyzer<Domain> analyzer;
    private final DebugContext debug;
    private final Domain postCondition;

    public Checker(Analyzer<Domain> analyzer, DebugContext debug, Domain postCondition) {
        this.analyzer = analyzer;
        this.debug = debug;
        this.postCondition = postCondition;
    }

    public void runAnalysis() {
        debug.log("\t" + "Running the analysis");
        Domain analysisResult = analyzer.analyze();
        checkPostCondition(analysisResult);
    }

    private void checkPostCondition(Domain analysisResult) {
        if (analysisResult == null) {
            return;
        }

        debug.log("\t" + "Computed post condition: " + analysisResult);
        debug.log("\t" + "Expected post condition: " + postCondition);
        if (analysisResult.equals(postCondition)) {
            debug.log("\t" + "Post condition satisfied");
        } else {
            debug.log("\t" + "Post condition not satisfied");
        }
    }
}