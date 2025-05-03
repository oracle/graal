package com.oracle.svm.hosted.analysis.ai.checker.example;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerResult;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerStatus;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.PentagonDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.util.IgvDumper;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.ValueAnchorNode;
import jdk.graal.compiler.nodes.util.GraphUtil;

import java.util.ArrayList;
import java.util.List;

public class PentagonDomainChecker implements Checker<PentagonDomain<AccessPath>> {

    /**
     * NOTE: This checker performs optimizations of a graph by deleting parts of the GraalIR that are unreachable at runtime
     * This is done for demonstration purposes, the checkers should generally not modify the actual graphs of methods
     * In the future, a specialized component can be done to handle the optimization logic based on the information
     * inferred from the abstract interpretation.
     */
    @Override
    public String getDescription() {
        return "Pentagon domain checker";
    }

    @Override
    public List<CheckerResult> check(AnalysisMethod method, AbstractState<PentagonDomain<AccessPath>> abstractState) {
        List<CheckerResult> checkerResults = new ArrayList<>();
        StructuredGraph graph = abstractState.getCfgGraph().graph;

        for (Node node : abstractState.getStateMap().keySet()) {
            if (!(node instanceof IfNode ifNode)) {
                continue;
            }

            if (abstractState.getPreCondition(node).isBot()) {
                CheckerResult res = new CheckerResult(CheckerStatus.WARNING, "Unreachable node: " + ifNode.trueSuccessor().toString());
                checkerResults.add(res);
                makeIfBranchUnreachable(ifNode, true, graph);
            } else if (abstractState.getPreCondition(node).isTop()) {
                CheckerResult res = new CheckerResult(CheckerStatus.WARNING, "Unreachable node: " + ifNode.falseSuccessor().toString());
                checkerResults.add(res);
                makeIfBranchUnreachable(ifNode, false, graph);
            }
        }

        IgvDumper.dumpPhase(method, graph, "After phase Abstract Interpretation Pentagon Analysis");
        return checkerResults;
    }

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof PentagonDomain;
    }

    private void makeIfBranchUnreachable(IfNode node, boolean trueUnreachable, StructuredGraph graph) {
        // FIXME: this doesn't get propagated to other phases...
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
