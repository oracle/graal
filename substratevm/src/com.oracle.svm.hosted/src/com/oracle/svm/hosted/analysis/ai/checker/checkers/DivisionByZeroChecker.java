package com.oracle.svm.hosted.analysis.ai.checker.checkers;

import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

/**
 * Represents a simple example of how a checker can be implemented.
 * This DivisionByZeroChecker works on IntInterval domain.
 */
public final class DivisionByZeroChecker implements Checker<IntInterval> {

    @Override
    public String getDescription() {
        return "Division By Zero Checker";
    }

    // TODO: implement check here
//    @Override
//    public List<CheckerResult> check(AnalysisMethod method, AbstractState<IntInterval> abstractState) {
//        List<CheckerResult> checkerResults = new ArrayList<>();
//
//        var stateMap = abstractState.getStateMap();
//        for (Node node : stateMap.keySet()) {
//            if (!(stateMap.get(node).getPreCondition() instanceof IntInterval)) {
//                checkerResults.add(new CheckerResult(CheckerStatus.ERROR, "DivisionByZeroChecker works only on IntInterval domain"));
//            }
//
//            if (node instanceof FloatDivNode || node instanceof RemNode) {
//                var divisorNode = ((BinaryArithmeticNode<?>) node).getY();
//                IntInterval divisorInterval = abstractState.getPostCondition(divisorNode);
//                if (divisorInterval.containsValue(0)) {
//                    // NOTE: getNodeSourcePosition is very vague, we probably should print more information like where the 0 was assigned last etc.
//                    checkerResults.add(new CheckerResult(CheckerStatus.ERROR, "Division by zero on line: " + node.getNodeSourcePosition().toString()));
//                }
//            }
//        }
//
//        return checkerResults;
//    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof IntInterval;
    }
}
