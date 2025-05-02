package com.oracle.svm.hosted.analysis.ai.checker.example;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerStatus;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.ArrayList;
import java.util.List;

public class ResourceLeaksChecker implements Checker<SetDomain<ResourceId>> {

    @Override
    public String getDescription() {
        return "Checking for potential resource leaks";
    }

    @Override
    public List<CheckerResult> check(AbstractStateMap<SetDomain<ResourceId>> abstractStateMap, StructuredGraph graph) {
        ResolvedJavaMethod method = graph.method();
        List<CheckerResult> results = new ArrayList<>();
        for (Node node : abstractStateMap.getStateMap().keySet()) {
            if (!(node instanceof ReturnNode returnNode)) {
                continue;
            }
            var returnPost = abstractStateMap.getPostCondition(returnNode);
            if (returnPost.empty()) {
                continue;
            }

            if (isMain(method)) {
                var checkerRes = new CheckerResult(CheckerStatus.ERROR, "Unclosed resource(s) at program exit point: " + returnPost);
                results.add(checkerRes);
            }
            /* Resources escaping from other methods are not considered leaks, but rather a potential problem */
            else {
                var checkerRes = new CheckerResult(CheckerStatus.WARNING, "Resource(s) escaping from method: " + returnPost);
                results.add(checkerRes);
            }
        }

        return results;
    }

    private boolean isMain(ResolvedJavaMethod method) {
        if (!method.isStatic() || !method.isPublic()) {
            return false;
        }

        return method.getSignature().getReturnKind().toString().equals("void")
                && method.getName().equals("main")
                && method.getParameters().length == 1;
    }

    @Override
    public boolean isCompatibleWith(AbstractStateMap<?> abstractStateMap) {
        return abstractStateMap.getInitialDomain() instanceof SetDomain<?>;
    }
}
