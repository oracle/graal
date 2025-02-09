package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Base class for an abstract interpretation analyzer.
 *
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public abstract class Analyzer<Domain extends AbstractDomain<Domain>> {

    protected final AnalysisMethod root;
    protected final DebugContext debug;
    protected final CheckerManager checkerManager = new CheckerManager();
    protected final IteratorPolicy iteratorPolicy;

    public Analyzer(AnalysisMethod root, DebugContext debug, IteratorPolicy iteratorPolicy) {
        this.root = root;
        this.debug = debug;
        this.iteratorPolicy = iteratorPolicy;
    }

    /**
     * Registers a new checker that will be used by the {@link Analyzer}
     *
     * @param checker to register
     */
    protected void registerChecker(Checker checker) {
        checkerManager.registerChecker(checker);
    }

    /**
     * This method is used in analyzer subclasses to create a complete payload for the analysis.
     *
     * @param initialDomain         initial domain for the analysis
     * @param domainNodeInterpreter interpreter to use
     */
    public abstract void run(Domain initialDomain, NodeInterpreter<Domain> domainNodeInterpreter);

    /**
     * Common method to run the analysis from the root {@link AnalysisMethod}.
     *
     * @param payload               analyzer payload
     * @param iterator              fixpoint iterator
     */
    protected void doRun(AnalysisPayload<Domain> payload, FixpointIterator<Domain> iterator) {
        payload.getLogger().logDebugInfo("Analysing method " + root);
        payload.getLogger().logDebugWarning("With provided checkers: " + checkerManager.getCheckers());

        /* Run the fixpoint iteration */
        var stateMap = iterator.iterateUntilFixpoint();
        payload.getLogger().logHighlightedDebugInfo("Analysis finished, you can see the analysis output at " + payload.getLogger().fileName());
        payload.getLogger().logToFile("Abstract state map after analysis: ");
        payload.getLogger().logToFile(stateMap.toString());
        AbstractState<Domain> returnState = stateMap.getReturnState();
        payload.getLogger().logHighlightedDebugInfo("The post-condition of the analysis: " + returnState.getPostCondition());
    }
}