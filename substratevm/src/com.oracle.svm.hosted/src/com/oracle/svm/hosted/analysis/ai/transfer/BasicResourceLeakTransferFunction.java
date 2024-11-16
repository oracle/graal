package com.oracle.svm.hosted.analysis.ai.transfer;

import com.oracle.svm.hosted.analysis.ai.domain.ConstantDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class BasicResourceLeakTransferFunction implements TransferFunction<ConstantDomain<Integer>> {
    @Override
    public ConstantDomain<Integer> analyzeNode(Node node, Environment<ConstantDomain<Integer>> environment) {
        if (node instanceof Invoke invoke) {
            String methodName = invoke.callTarget().targetName();
            ConstantDomain<Integer> currentDomain = environment.getDomain(node);

            if (methodName.contains("init")) {
                currentDomain.incrementValue();
            } else if (methodName.contains("close")) {
                currentDomain.decrementValue();
            }
        }
        return environment.getDomain(node);
    }
}