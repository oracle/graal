package com.oracle.svm.hosted.analysis.ai.checker.example;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerStatus;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.RemNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a simple example of how a checker can be implemented.
 * This DivisionByZeroChecker works on IntInterval domain.
 */
public final class DivisionByZeroChecker implements Checker<IntInterval> {

    @Override
    public String getDescription() {
        return "Division By Zero Checker";
    }

    @Override
    public List<CheckerResult> check(AbstractStateMap<IntInterval> abstractStateMap, StructuredGraph graph) {
        List<CheckerResult> checkerResults = new ArrayList<>();

        var stateMap = abstractStateMap.getStateMap();
        for (Node node : stateMap.keySet()) {
            if (!(stateMap.get(node).getPreCondition() instanceof IntInterval)) {
                checkerResults.add(new CheckerResult(CheckerStatus.ERROR, "DivisionByZeroChecker works only on IntInterval domain"));
            }

            if (node instanceof FloatDivNode || node instanceof RemNode) {
                var divisorNode = ((BinaryArithmeticNode<?>) node).getY();
                IntInterval divisorInterval = abstractStateMap.getPostCondition(divisorNode);
                if (divisorInterval.containsValue(0)) {
                    // NOTE: getNodeSourcePosition is very vague, we probably should print more information like where the 0 was assigned last etc.
                    checkerResults.add(new CheckerResult(CheckerStatus.ERROR, "Division by zero on line: " + node.getNodeSourcePosition().toString()));
                }
            }
        }

        return checkerResults;
    }

    @Override
    public boolean isCompatibleWith(AbstractStateMap<?> abstractStateMap) {
        return abstractStateMap.getInitialDomain() instanceof IntInterval;
    }
}
