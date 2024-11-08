package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.transfer.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.Queue;
import java.util.LinkedList;

/**
 * A fixpoint iterator for dataflow analysis.
 * The analysis is performed on a graph where each node is associated with a domain element.
 *
 * @param <Value>  type of the derived abstract value
 * @param <Domain> type of the derived abstract domain
 */
public class FixpointIterator<
        Value extends AbstractValue<Value>,
        Domain extends AbstractDomain<Domain>> {
    private final Environment<Domain> environment;
    private final TransferFunction<Domain> transferFunction;

    public FixpointIterator(Domain domain, TransferFunction<Domain> transferFunction) {
        Domain topValue = domain.copyOf();
        topValue.setToTop();
        this.environment = new Environment<>(topValue);
        this.transferFunction = transferFunction;
    }

    public void analyze(StructuredGraph graph, DebugContext debug) {
        Queue<Node> workList = new LinkedList<>();
        for (Node node : graph.getNodes()) {
            workList.add(node);
        }

        while (!workList.isEmpty()) {
            Node node = workList.poll();
            Domain newDomain = transferFunction.analyzeNode(node, environment);
            debug.log("\t" + node);
            debug.log("\t" + newDomain.toString());
            for (Node successor : node.successors()) {
                if (stateChanged(successor, newDomain)) {
                    workList.add(successor);
                }
            }
        }
    }

    private boolean stateChanged(Node successor, Domain newDomain) {
        Domain oldState = environment.get(successor);
        Domain updatedState = newDomain.join(oldState);
        if (!updatedState.equals(oldState)) {
            environment.set(successor, updatedState);
            return true;
        }
        return false;
    }
}