package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.fixpoint.AbstractStateMap;
import jdk.graal.compiler.graph.Node;

/*
 * Interface for a checker that can be used to check the desired program property.
 */
public interface Checker {

    String getDescription();

    CheckerResult check(Node node, AbstractStateMap<?> abstractStateMap);
}