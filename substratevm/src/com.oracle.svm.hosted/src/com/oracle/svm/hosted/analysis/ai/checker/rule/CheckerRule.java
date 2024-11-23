package com.oracle.svm.hosted.analysis.ai.checker.rule;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;

/**
 * Represents a checker rule that can be evaluated on a {@link Node} with a given {@link AbstractDomain}
 * @param <Domain> type of the derived AbstractDomain
 */
public interface CheckerRule<Domain extends AbstractDomain<Domain>> {

    /**
     * Evaluates the rule on the given node and domain
     * @param node the node to evaluate the rule on
     * @param domain the domain to evaluate the rule with
     * @return the result of the rule evaluation
     */
    CheckerRuleResult evaluateRule(Node node, Domain domain);
}