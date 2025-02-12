package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.CallInterpreter;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * Represents the payload of an abstract interpretation analysis,
 * containing the necessary information about the analysis configuration.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is using
 */
public abstract class AnalysisPayload<Domain extends AbstractDomain<Domain>> {

    protected final Domain initialDomain;
    protected final IteratorPolicy iteratorPolicy;
    protected final AnalysisMethod root;
    protected final DebugContext debugContext;
    protected final AbstractInterpretationLogger logger;
    protected final TransferFunction<Domain> transferFunction;
    protected final CheckerManager checkerManager;
    protected final AnalysisMethodFilterManager methodFilterManager;

    public AnalysisPayload(
            Domain initialDomain,
            IteratorPolicy iteratorPolicy,
            AnalysisMethod root,
            DebugContext debugContext,
            TransferFunction<Domain> transferFunction,
            CheckerManager checkerManager,
            AnalysisMethodFilterManager methodFilterManager
    ) {
        this.initialDomain = initialDomain;
        this.iteratorPolicy = iteratorPolicy;
        this.root = root;
        this.debugContext = debugContext;
        this.transferFunction = transferFunction;
        this.methodFilterManager = methodFilterManager;
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

    public int getMaxJoinIterations() {
        return iteratorPolicy.maxJoinIterations();
    }

    public int getMaxWidenIteration() {
        return iteratorPolicy.maxWidenIterations();
    }

    public int getMaxRecursionDepth() {
        return iteratorPolicy.maxRecursionDepth();
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

    public TransferFunction<Domain> getTransferFunction() {
        return transferFunction;
    }

    public NodeInterpreter<Domain> getNodeInterpreter() {
        return transferFunction.nodeInterpreter();
    }

    public CallInterpreter<Domain> getCallInterpreter() {
        return transferFunction.callInterpreter();
    }

    public CheckerManager getCheckerManager() {
        return checkerManager;
    }

    public AnalysisMethodFilterManager getAnalysisMethodFilterManager() {
        return methodFilterManager;
    }
}