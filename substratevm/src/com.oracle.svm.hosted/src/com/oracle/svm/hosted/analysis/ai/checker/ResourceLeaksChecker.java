package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.SetDomain;
import com.oracle.svm.hosted.analysis.ai.example.leaks.set.ResourceId;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ReturnNode;

import java.util.ArrayList;
import java.util.List;

public class ResourceLeaksChecker implements Checker<SetDomain<ResourceId>> {

    @Override
    public String getDescription() {
        return "Checking for potential resource leaks";
    }

    @Override
    public List<CheckerResult> check(AnalysisMethod method, AbstractState<SetDomain<ResourceId>> abstractState) {
        List<CheckerResult> results = new ArrayList<>();
        for (Node node : abstractState.getStateMap().keySet()) {
            if (!(node instanceof ReturnNode returnNode)) {
                continue;
            }
            var returnPost = abstractState.getPostCondition(returnNode);
            if (returnPost.empty()) {
                continue;
            }

            if (isMain(method)) {
                var checkerRes = new CheckerResult(CheckerStatus.ERROR, "Unclosed resource(s) at program exit point: " + returnPost);
                results.add(checkerRes);
            }

            /* We can for example define that whenever a resource escapes to a caller it is considered a warning,
             * Even though this is probably too harsh in real world usage.
             */
            else {
                var checkerRes = new CheckerResult(CheckerStatus.WARNING, "Resource(s) escaping from " + method + " -> " + returnPost);
                results.add(checkerRes);
            }
        }

        return results;
    }

    private boolean isMain(AnalysisMethod method) {
        if (!method.isStatic() || !method.isPublic()) {
            return false;
        }

        return method.getSignature().getReturnKind().toString().equals("void")
                && method.getName().equals("main")
                && method.getParameters().length == 1;
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof SetDomain<?>;
    }
}
