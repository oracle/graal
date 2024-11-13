package com.oracle.svm.hosted.analysis.ai.transfer.example;

import com.oracle.svm.hosted.analysis.ai.domain.IntegerDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

public class BasicResourceLeakTransferFunction implements TransferFunction<IntegerDomain> {
    @Override
    public IntegerDomain analyzeNode(Node node, Environment<IntegerDomain> environment) {
        if (node instanceof Invoke invoke) {
            String methodName = invoke.callTarget().targetName();
            IntegerDomain currentDomain = environment.getDomain(node);

            if (methodName.contains("init")) {
                currentDomain.incrementValue();
            } else if (methodName.contains("close")) {
                currentDomain.decrementValue();
            }
        }
        return environment.getDomain(node);
    }
}