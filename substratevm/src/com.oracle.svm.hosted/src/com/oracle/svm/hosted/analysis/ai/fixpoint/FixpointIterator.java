package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.LatticeDomain;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.Queue;
import java.util.LinkedList;

/**
 * A fixpoint iterator for dataflow analysis.
 * The analysis is performed on a graph where each node is  associated with a domain element.
 *
 * @param <Value>  type of the derived abstract value
 * @param <Domain> type of the derived abstract domain
 */

// TODO start developing actual analyses and find better algorithms for fixpoint iteration
// TODO will probably need to implement widening and join policy to avoid infinite loops and find fixpoint faster
public class FixpointIterator<
        Value extends AbstractValue<Value>,
        Domain extends LatticeDomain<Value, Domain>> {

    private final Environment<Value, Domain> environment;
    private final TransferFunction transferFunction;

    public FixpointIterator(Environment<Value, Domain> environment, TransferFunction transferFunction) {
        this.environment = environment;
        this.transferFunction = transferFunction;
    }

    public void analyze(StructuredGraph graph) {
        Queue<Node> worklist = new LinkedList<>();
        for (Node node : graph.getNodes()) {
            worklist.add(node);
        }

        while (!worklist.isEmpty()) {
            Node node = worklist.poll();
            transferFunction.analyzeNode(node);

            for (Node successor : node.successors()) {
                if (stateChanged(node, successor)) {
                    worklist.add(successor);
                }
            }
        }
    }

    private boolean stateChanged(Node node, Node successor) {
        Domain oldState = environment.get(successor);
        Domain newState = environment.get(node).join(oldState);
        if (!newState.equals(oldState)) {
            environment.set(successor, newState);
            return true;
        }
        return false;
    }
}