package org.graalvm.compiler.phases;

import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

import java.util.Map;
import java.util.Set;

public class RemovingUnneededClassInitNodesPhase extends Phase {

    @Override
    protected void run(StructuredGraph graph) {
        ControlFlowGraph cfg = ControlFlowGraph.compute(graph, true, true, true, false);
        RemovingUnneededClassInitClosure.removeUnneededNodes(cfg.getStartBlock());

        Set<Map.Entry<InvokeWithExceptionNode, AbstractBeginNode>> nodesToBeRemoved = RemovingUnneededClassInitClosure.getNodesToBeRemoved().entrySet();
        for (Map.Entry<InvokeWithExceptionNode, AbstractBeginNode> entry : nodesToBeRemoved) {
            InvokeWithExceptionNode invokeWithExceptionNode = entry.getKey();
            AbstractBeginNode survivingSuccessor = invokeWithExceptionNode.killKillingBegin();

            invokeWithExceptionNode.graph().removeSplitPropagate(invokeWithExceptionNode, survivingSuccessor);
        }
    }
}
