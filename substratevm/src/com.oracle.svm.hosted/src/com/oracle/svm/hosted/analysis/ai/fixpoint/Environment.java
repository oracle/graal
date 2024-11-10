package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import jdk.graal.compiler.graph.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * This class maps Nodes of StructuredGraph to a common abstract domain
 * and also keeps track of some metadata about nodes like visit count.
 * acting as a memory of FixpointIterator.
 * An example could be mapping Nodes to intervals or constants.
 *
 * @param <Domain> type of the derived abstract domain
 */

public class Environment<Domain extends AbstractDomain<Domain>> {
    private final Domain defaultDomain;
    private final Map<Node, AbstractState<Domain>> stateMap;

    public Environment(Domain defaultDomain, int nodeCount) {
        this.defaultDomain = defaultDomain;
        this.stateMap = new HashMap<>(nodeCount);
    }

    public AbstractState<Domain> getState(Node node) {
        return stateMap.get(node);
    }

    public Domain getDomain(Node node) {
        var state = stateMap.getOrDefault(node, null);
        if (state == null) {
            return defaultDomain;
        }
        return state.getDomain();
    }

    public void setDomain(Node node, Domain domain) {
        var state = stateMap.getOrDefault(node, null);
        if (state == null) {
            return;
        }
        state.setDomain(domain);
    }

    public int getVisitCount(Node node) {
        var state = stateMap.getOrDefault(node, null);
        if (state == null) {
            return 0;
        }
        return state.getVisitedCount();
    }
}