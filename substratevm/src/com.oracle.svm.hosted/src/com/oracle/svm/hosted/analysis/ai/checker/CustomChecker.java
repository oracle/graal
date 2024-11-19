package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.fixpoint.FixpointIterator;
import jdk.graal.compiler.debug.DebugContext;

/**
 * This is a custom checker that is able to run an analysis using the provided analyzer.
 * instead of using checkers with already predefined behavior, this checker allows the user to define their own analysis.
 *
 * @param <Domain> type of the derived AbstractDomain
 */

public class CustomChecker<Domain extends AbstractDomain<Domain>> implements Checker<Domain> {
    private final FixpointIterator<Domain> fixpointIterator;
    private final DebugContext debug;

    public CustomChecker(FixpointIterator<Domain> fixpointIterator, DebugContext debug) {
        this.fixpointIterator = fixpointIterator;
        this.debug = debug;
    }

    public Environment<Domain> check() {
        debug.log("\t" + "Running the analysis");
        var analysisResult = fixpointIterator.iterateUntilFixpoint();
        debug.log("\t" + "Analysis result: " + analysisResult);
        debug.log("\t" + analysisResult.toString());
        return analysisResult;
    }
}