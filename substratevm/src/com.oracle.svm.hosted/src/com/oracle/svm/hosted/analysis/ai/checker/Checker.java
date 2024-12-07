package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

/*
 * API for a checker that can be used to check program properties.
 */
public interface Checker {

    String getDescription();

    CheckerResult check(Node node, AbstractStateMap<?> abstractStateMap);
}