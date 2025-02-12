package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.RemNode;

/**
 * Represents a simple example of how a checker can be implemented.
 * This DivisionByZeroChecker works on IntInterval domain.
 */
public class DivisionByZeroChecker implements Checker {

    @Override
    public String getDescription() {
        return "Division By Zero Checker";
    }

    @Override
    public CheckerResult check(AbstractStateMap<?> abstractStateMap) {
        var stateMap = abstractStateMap.getStateMap();
        for (Node node : stateMap.keySet()) {
            if (!(stateMap.get(node).getPreCondition() instanceof IntInterval)) {
                return new CheckerResult(CheckerStatus.ERROR, "DivisionByZeroChecker works only on IntInterval domain");
            }

            if (node instanceof FloatDivNode || node instanceof RemNode) {
                var divisorNode = ((BinaryArithmeticNode<?>) node).getY();
                IntInterval divisorInterval = (IntInterval) abstractStateMap.getPostCondition(divisorNode);
                if (divisorInterval.containsValue(0)) {
                    // TODO: getNodeSourcePosition is very vague, we probably should print more information like where the 0 was assigned last etc.
                    return new CheckerResult(CheckerStatus.ERROR, "Division by zero on line: " + node.getNodeSourcePosition().toString());
                }
            }
        }

        return new CheckerResult(CheckerStatus.OK, "No potential division by zero found");
    }
}
