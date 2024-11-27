package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.WorkListFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic implementation of an analyzer.
 * Analyzers will create their corresponding fixpointIterator
 *
 * @param <Derived> the type of the abstract domain
 */
public class Analyzer<Derived extends AbstractDomain<Derived>> {

    private final DebugContext debug;
    private final List<Checker> checkers;

    public Analyzer(DebugContext debug) {
        this.debug = debug;
        this.checkers = new ArrayList<>();
    }

    public void registerChecker(Checker checker) {
        checkers.add(checker);
    }

    public void run(StructuredGraph graph, Derived initialDomain, TransferFunction<Derived> transferFunction) {
        debug.log("Starting analysis");
        FixpointIterator<Derived> fixpointIterator = new WorkListFixpointIterator<>(graph, transferFunction, checkers, initialDomain, debug);
        Environment<Derived> environment = fixpointIterator.iterateUntilFixpoint();
        debug.log("Analysis completed");
        debug.log("Printing the environment" + environment.toString());
    }
}

