package com.oracle.svm.hosted.analysis.ai.example.intervals.dataflow;

import com.oracle.svm.hosted.analysis.ai.analyzer.payload.AnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.InterProceduralAnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IntraProceduralAnalysisPayload;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IntegerLessThanNode;

public class DataFlowIntIntervalNodeInterpreter implements NodeInterpreter<IntInterval> {

    @Override
    public IntInterval execEdge(Node source,
                                Node destination,
                                AbstractStateMap<IntInterval> abstractStateMap,
                                AnalysisPayload<IntInterval> payload) {
        payload.getLogger().logToFile("DataFlowIntIntervalNodeInterpreter::execEdge source: " + source + " destination: " + destination);
        if (source instanceof IfNode ifNode) {
            if (!abstractStateMap.isVisited(ifNode.condition())) {
                execNode(ifNode.condition(), abstractStateMap, payload);
            }

            if (destination.equals(ifNode.trueSuccessor())) {
                abstractStateMap.getPreCondition(destination).
                        joinWith(abstractStateMap.getPreCondition(source).meet(abstractStateMap.getPostCondition(ifNode.condition())));
            } else if (destination.equals(ifNode.falseSuccessor())) {
                var condition = abstractStateMap.getPostCondition(ifNode.condition());
                condition.inverse();
                abstractStateMap.getPreCondition(destination).meetWith(condition);
            }
        } else {
            abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
        }

        return abstractStateMap.getPostCondition(destination);
    }

    @Override
    public IntInterval execNode(Node node,
                                AbstractStateMap<IntInterval> abstractStateMap,
                                AnalysisPayload<IntInterval> payload) {

        abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        if (node instanceof ConstantNode constantNode) {
            assert constantNode.asJavaConstant() != null;
            abstractStateMap.setPostCondition(node, new IntInterval(constantNode.asJavaConstant().asLong()));
        } else if (node instanceof ParameterNode parameterNode) {
            IntInterval result = new IntInterval();
            if (payload instanceof IntraProceduralAnalysisPayload<IntInterval>) {
                result.setToTop();
            } else {
                InterProceduralAnalysisPayload<IntInterval> interPayload = (InterProceduralAnalysisPayload<IntInterval>) payload;
                result = interPayload.getCurrentActualArgumentAt(parameterNode.index());
            }
            abstractStateMap.setPostCondition(node, result);
        } else if (node instanceof AddNode addNode) {
            if (!abstractStateMap.isVisited(addNode.getX())) {
                execNode(addNode.getX(), abstractStateMap, payload);
            }
            if (!abstractStateMap.isVisited(addNode.getY())) {
                execNode(addNode.getY(), abstractStateMap, payload);
            }

            IntInterval x = abstractStateMap.getPostCondition(addNode.getX());
            IntInterval y = abstractStateMap.getPostCondition(addNode.getY());
            abstractStateMap.setPostCondition(node, x.add(y));
        } else if (node instanceof IntegerEqualsNode integerEqualsNode) {
            if (!abstractStateMap.isVisited(integerEqualsNode.getY())) {
                execNode(integerEqualsNode.getY(), abstractStateMap, payload);
            }
            IntInterval y = abstractStateMap.getPostCondition(integerEqualsNode.getY());
            abstractStateMap.setPostCondition(node, y);
        } else if (node instanceof IntegerLessThanNode integerLessThanNode) {
            if (!abstractStateMap.isVisited(integerLessThanNode.getY())) {
                execNode(integerLessThanNode.getY(), abstractStateMap, payload);
            }
            IntInterval y = abstractStateMap.getPostCondition(integerLessThanNode.getY());
            abstractStateMap.setPostCondition(node, new IntInterval(IntInterval.MIN, y.getUpperBound() - 1));
        } else if (node instanceof PhiNode || node instanceof FrameState || node instanceof LoopBeginNode) {
            abstractStateMap.getState(node);
            for (Node input : node.inputs()) {
                if (!abstractStateMap.isVisited(input)) {
                    execNode(input, abstractStateMap, payload);
                }
                abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPostCondition(input));
            }
        }

        return abstractStateMap.getPostCondition(node);
    }
}
