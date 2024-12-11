package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Basic class for an abstract interpretation analyzer.
 *
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public abstract class Analyzer<Domain extends AbstractDomain<Domain>> {
    protected DebugContext debug;
    protected final List<Checker> checkers = new ArrayList<>();

    /**
     * Registers a new checker that will be used by the {@link Analyzer}
     *
     * @param checker to register
     */
    protected void registerChecker(Checker checker) {
        checkers.add(checker);
    }

    /**
     * Runs the analysis on the given {@link ControlFlowGraph} with the provided initial domain and interpreter.
     *
     * @param graph                 to analyze
     * @param initialDomain         initial domain for the analysis
     * @param domainNodeInterpreter interpreter to use
     */
    public abstract void run(ControlFlowGraph graph, Domain initialDomain, NodeInterpreter<Domain> domainNodeInterpreter);
}
