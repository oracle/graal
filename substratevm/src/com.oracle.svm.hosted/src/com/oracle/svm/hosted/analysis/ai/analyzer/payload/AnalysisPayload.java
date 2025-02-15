package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import jdk.graal.compiler.debug.DebugContext;

import java.io.IOException;

/**
 * Represents the payload of an abstract interpretation analysis,
 * containing the necessary information about the analysis configuration.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is using
 */
public final class AnalysisPayload<Domain extends AbstractDomain<Domain>> {

    private final Domain initialDomain;
    private final IteratorPayload iteratorPayload;
    private final AnalysisMethod root;
    private final DebugContext debugContext;
    private final AbstractInterpretationLogger logger;
    private final TransferFunction<Domain> transferFunction;
    private final CheckerManager checkerManager;
    private final AnalysisMethodFilterManager methodFilterManager;

    public AnalysisPayload(
            Domain initialDomain,
            IteratorPayload iteratorPayload,
            AnalysisMethod root,
            DebugContext debugContext,
            TransferFunction<Domain> transferFunction,
            CheckerManager checkerManager,
            AnalysisMethodFilterManager methodFilterManager
    ) {
        this.initialDomain = initialDomain;
        this.iteratorPayload = iteratorPayload;
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

    public IteratorPayload getIteratorPayload() {
        return iteratorPayload;
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

    public CheckerManager getCheckerManager() {
        return checkerManager;
    }

    public AnalysisMethodFilterManager getAnalysisMethodFilterManager() {
        return methodFilterManager;
    }
}