package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Interface for handling method invocations during abstract interpretation.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public interface InvokeHandler<Domain extends AbstractDomain<Domain>> {

    /**
     * Handles the invocation of a method during abstract interpretation.
     *
     * @param invokeInput the relevant information needed to perform abstract interpretation of a given invocation
     * @return the analysis outcome
     */
    AnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput);

    /**
     * The starting point of the analysis
     * We receive an {@link AnalysisMethod} and we start our abstract interpretation from this analysisMethod as the starting point.
     *
     * @param root the {@link AnalysisMethod} that the abstract interpretation analysis starts from
     */
    void handleRootInvoke(AnalysisMethod root);
}
