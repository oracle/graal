package com.oracle.svm.hosted.analysis.ai.checker.example;

import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerStatus;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

import java.util.ArrayList;
import java.util.List;

public class PentagonDomainChecker implements Checker {

    /* NOTE: This is for demonstration purposes, the checkers should generally not modify the actual graphs of methods
     *        In the future, a specialized component can be done to handle the optimization logic based on the information
     *        inferred from the abstract interpretation.
     * */
    private final StructuredGraph graph;

    public PentagonDomainChecker(StructuredGraph graph) {
        this.graph = graph;
    }


    @Override
    public String getDescription() {
        return "Simple pentagon domain checker";
    }

    @Override
    public List<CheckerResult> check(AbstractStateMap<? extends AbstractDomain<?>> abstractStateMap) {
        List<CheckerResult> checkerResults = new ArrayList<>();
        for (Node node : abstractStateMap.getStateMap().keySet()) {
            if (!(node instanceof IfNode ifNode)) {
                continue;
            }

            if (abstractStateMap.getPreCondition(node).isBot()) {
                CheckerResult res = new CheckerResult(CheckerStatus.WARNING, "Unreachable node: " + ifNode.trueSuccessor().toString());
                checkerResults.add(res);
                makeIfBranchUnreachable(ifNode, true);
            } else if (abstractStateMap.getPreCondition(node).isTop()) {
                CheckerResult res = new CheckerResult(CheckerStatus.WARNING, "Unreachable node: " + ifNode.falseSuccessor().toString());
                checkerResults.add(res);
                makeIfBranchUnreachable(ifNode, false);
            }
        }
        return checkerResults;
    }


    private void makeIfBranchUnreachable(IfNode node, boolean trueUnreachable) {
        AbstractBeginNode killedBegin = node.successor(trueUnreachable);
        AbstractBeginNode survivingBegin = node.successor(!trueUnreachable);

        if (survivingBegin.hasUsages()) {
            /*
             * Even when we know that the IfNode is not necessary because the condition
             * is statically proven, all PiNode that are anchored at the surviving
             * branch must remain anchored at exactly this point. It would be wrong to
             * anchor the PiNode at the BeginNode of the preceding block, because at
             * that point the condition is not proven yet.
             */
            ValueAnchorNode anchor = graph.add(new ValueAnchorNode());
            graph.addAfterFixed(survivingBegin, anchor);
            survivingBegin.replaceAtUsages(anchor, InputType.Guard, InputType.Anchor);
        }
        graph.removeSplit(node, survivingBegin);
        GraphUtil.killCFG(killedBegin);
    }
}
