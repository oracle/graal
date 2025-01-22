package com.oracle.svm.hosted.analysis.ai.analyzer.example.intervals.mapping;

import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.MapDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.AddNode;
import jdk.graal.compiler.nodes.calc.BinaryArithmeticNode;
import jdk.graal.compiler.nodes.calc.FloatDivNode;
import jdk.graal.compiler.nodes.calc.MulNode;
import jdk.graal.compiler.nodes.calc.SubNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;

public class IdIntervalMapNodeInterpreter implements NodeInterpreter<MapDomain<String, IntInterval>> {
    @Override
    public void execEdge(Node source, Node destination, AbstractStateMap<MapDomain<String, IntInterval>> abstractStateMap) {
        abstractStateMap.getPreCondition(destination).joinWith(abstractStateMap.getPostCondition(source));
    }

    @Override
    public void execNode(Node node, AbstractStateMap<MapDomain<String, IntInterval>> abstractStateMap) {
        /* a lot of copies going on here, think how to do this more efficiently */

        abstractStateMap.getPostCondition(node).joinWith(abstractStateMap.getPreCondition(node));
        MapDomain<String, IntInterval> postCondition = abstractStateMap.getPostCondition(node);
        if (node instanceof StoreFieldNode storeFieldNode) {
            String key = storeFieldNode.field().getName();
            postCondition.put(key, evaluateDataFlowNode(storeFieldNode.value(), abstractStateMap));
        }
    }

    private IntInterval evaluateDataFlowNode(Node node, AbstractStateMap<MapDomain<String, IntInterval>> abstractStateMap) {
        if (node instanceof LoadFieldNode loadFieldNode) {
            String key = loadFieldNode.field().getName();
            return abstractStateMap.getPreCondition(loadFieldNode).get(key);
        }
        if (node instanceof ConstantNode constantNode) {
            assert constantNode.asJavaConstant() != null;
            return new IntInterval(constantNode.asJavaConstant().asLong());
        }
        if (node instanceof BinaryArithmeticNode<?> binaryArithmeticNode) {
            IntInterval first = evaluateDataFlowNode(binaryArithmeticNode.getX(), abstractStateMap);
            IntInterval second = evaluateDataFlowNode(binaryArithmeticNode.getX(), abstractStateMap);
            if (binaryArithmeticNode instanceof AddNode) {
                return first.add(second);
            }
            if (binaryArithmeticNode instanceof SubNode) {
                return first.sub(second);
            }
            if (binaryArithmeticNode instanceof MulNode) {
                return first.mul(second);
            }
            if (binaryArithmeticNode instanceof FloatDivNode) {
                return first.div(second);
            }
        }

        /* Edge case -> return TOP value of Interval domain */
        IntInterval result = new IntInterval();
        result.setToTop();
        return result;
    }
}
