package org.graalvm.compiler.phases;

import jdk.vm.ci.meta.JavaConstant;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.cfg.Loop;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.InvokeWithExceptionNode;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.phases.graph.ReentrantBlockIterator;

import java.util.HashMap;
import java.util.List;

public class RemovingUnneededClassInitClosure extends ReentrantBlockIterator.BlockIteratorClosure<EconomicSet<String>> {

    private static HashMap<InvokeWithExceptionNode, AbstractBeginNode> nodesToBeRemoved;

    public static void removeUnneededNodes(Block start) {
        ReentrantBlockIterator.apply(new RemovingUnneededClassInitClosure(), start);
    }

    public static HashMap<InvokeWithExceptionNode, AbstractBeginNode> getNodesToBeRemoved() {
        return nodesToBeRemoved;
    }

    @Override
    protected EconomicSet<String> getInitialState() {
        nodesToBeRemoved = new HashMap<>();
        return EconomicSet.create(Equivalence.DEFAULT);
    }

    @Override
    protected EconomicSet<String> processBlock(Block block, EconomicSet<String> currentState) {
        for (Node node : block.getNodes()) {
            if (node instanceof InvokeWithExceptionNode) {
                InvokeWithExceptionNode invokeWithExceptionNode = (InvokeWithExceptionNode) node;
                if (invokeWithExceptionNode.callTarget() != null && invokeWithExceptionNode.callTarget().targetMethod() != null
                        && invokeWithExceptionNode.callTarget().targetMethod().getName().equals("ensureInitialized")
                        && invokeWithExceptionNode.callTarget().targetMethod().getDeclaringClass().getUnqualifiedName().equals("Class")) {

                    JavaConstant javaConstant = invokeWithExceptionNode.callTarget().arguments().get(0).asJavaConstant();
                    if (javaConstant != null) {
                        String hashCode = Integer.toString(javaConstant.hashCode());
                        if (!currentState.contains(hashCode)) {
                            currentState.add(hashCode);
                        } else {
                            nodesToBeRemoved.put(invokeWithExceptionNode, invokeWithExceptionNode.next());
                        }
                    }
                }
            }
        }

        return currentState;
    }

    @Override
    protected EconomicSet<String> merge(Block merge, List<EconomicSet<String>> states) {
        // Find intersection of all branches which are merging into current merge node.
        EconomicSet<String> result = states.get(0);
        for (int i = 1; i < states.size(); i++) {
            result.retainAll(states.get(i));
        }
        return result;
    }

    @Override
    protected EconomicSet<String> cloneState(EconomicSet<String> oldState) {
        // Clone current state of iteration.
        EconomicSet<String> result = EconomicSet.create(Equivalence.DEFAULT);
        if (oldState != null) {
            result.addAll(oldState);
        }
        return result;
    }

    @Override
    protected List<EconomicSet<String>> processLoop(Loop<Block> loop, EconomicSet<String> initialState) {
        // No special job to be done here. Just continue into loop with processLoop method.
        return ReentrantBlockIterator.processLoop(this, loop, initialState).exitStates;
    }
}
