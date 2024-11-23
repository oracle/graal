package com.oracle.svm.hosted.analysis.ai.transfer;

import com.oracle.svm.hosted.analysis.ai.domain.ConstantDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class BasicResourceLeakTransferFunction implements TransferFunction<ConstantDomain<Integer>> {

    @Override
    public ConstantDomain<Integer> computePostCondition(Node node, Environment<ConstantDomain<Integer>> environment) {
        ConstantDomain<Integer> currentDomain = environment.getPostCondition(node).copyOf();
        if (node instanceof Invoke invoke) {
            String methodName = invoke.callTarget().targetName();
            if (methodName.contains("init")) {
                currentDomain.incrementValue();
            } else if (methodName.contains("close")) {
                currentDomain.decrementValue();
            }
        }

        return currentDomain;
    }
}