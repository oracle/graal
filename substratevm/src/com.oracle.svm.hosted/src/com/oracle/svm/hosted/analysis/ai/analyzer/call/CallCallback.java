package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

@FunctionalInterface
public interface CallCallback<Domain extends AbstractDomain<Domain>> {
    
    AnalysisOutcome<Domain> handleCall(Invoke invoke, Node node, AbstractStateMap<Domain> abstractStateMap);
}
