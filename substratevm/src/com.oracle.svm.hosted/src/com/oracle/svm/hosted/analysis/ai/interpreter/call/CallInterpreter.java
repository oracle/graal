package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

/**
 * Represents an interpreter of method calls.
 *
 * @param <Domain> type of the derived {@link AbstractDomain}
 */
public interface CallInterpreter<Domain extends AbstractDomain<Domain>> {

    void execInvoke(Invoke invoke,
                    Node invokeNode,
                    AbstractStateMap<Domain> abstractStateMap,
                    AnalysisPayload<Domain> payload);
}