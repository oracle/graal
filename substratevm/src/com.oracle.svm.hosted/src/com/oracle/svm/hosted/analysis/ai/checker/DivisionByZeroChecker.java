package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.RemNode;

public class DivisionByZeroChecker implements Checker {

    @Override
    public String getDescription() {
        return "Division By Zero Checker";
    }

    @Override
    public CheckerResult check(Node node, AbstractStateMap<?> abstractStateMap) {
        var domain = abstractStateMap.getPostCondition(node);

        if (!(domain instanceof IntInterval)) {
            return new CheckerResult(CheckStatus.UNKNOWN, "Unsupported domain");
        }

        if (node instanceof FloatDivNode || node instanceof RemNode) {
            var divisorNode = ((BinaryArithmeticNode<?>) node).getY();
            IntInterval divisorInterval = (IntInterval) abstractStateMap.getPostCondition(divisorNode);

            if (divisorInterval.containsValue(0)) {
                return new CheckerResult(CheckStatus.ERROR, "Division by zero on line: " + node.getNodeSourcePosition().toString());
            }
        }

        return new CheckerResult(CheckStatus.OK, "No division by zero");
    }
}
