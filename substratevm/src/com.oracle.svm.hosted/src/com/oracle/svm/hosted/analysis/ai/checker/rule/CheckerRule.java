package com.oracle.svm.hosted.analysis.ai.checker.rule;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;

public interface CheckerRule<Domain extends AbstractDomain<Domain>> {
    CheckerRuleResult check(Node node, Domain domain);
}