package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * Represents the payload of an abstract interpretation analysis,
 * containing all the necessary information about the internal state of the analysis
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public abstract class AnalysisPayload<Domain extends AbstractDomain<Domain>> {

    protected final Domain initialDomain;
    protected final IteratorPolicy iteratorPolicy;
    protected final AnalysisMethod root;
    protected final DebugContext debugContext;
    protected final AbstractInterpretationLogger logger;
    protected final NodeInterpreter<Domain> nodeInterpreter;
    protected final CheckerManager checkerManager;

    public AnalysisPayload(
            Domain initialDomain,
            IteratorPolicy iteratorPolicy,
            AnalysisMethod root,
            DebugContext debugContext,
            NodeInterpreter<Domain> nodeInterpreter,
            CheckerManager checkerManager
    ) {
        this.initialDomain = initialDomain;
        this.iteratorPolicy = iteratorPolicy;
        this.root = root;
        this.debugContext = debugContext;
        this.nodeInterpreter = nodeInterpreter;
        this.checkerManager = checkerManager;
        try {
            this.logger = new AbstractInterpretationLogger(root, debugContext);
        } catch (IOException e) {
            throw AnalysisError.interruptAnalysis("Failed to create abstract interpretation logger");
        }
    }

    public Domain getInitialDomain() {
        return initialDomain;
    }

    public IteratorPolicy getIteratorPolicy() {
        return iteratorPolicy;
    }

    public AnalysisMethod getRoot() {
        return root;
    }

    public DebugContext getDebugContext() {
        return debugContext;
    }

    public AbstractInterpretationLogger getLogger() {
        return logger;
    }

    public NodeInterpreter<Domain> getNodeInterpreter() {
        return nodeInterpreter;
    }

    public CheckerManager getCheckerManager() {
        return checkerManager;
    }
}