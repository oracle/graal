package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import jdk.graal.compiler.graph.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * This class maps Nodes of StructuredGraph to a common abstract domain,
 * acting as a memory of FixpointIterator.
 * An example could be mapping Nodes to intervals or constants.
 *
 * @param <Domain> type of the derived abstract domain
 */
public final class Environment<
        Domain extends AbstractDomain<Domain>> {
    private final Map<Node, Domain> nodeToDomainMap;
    private final Domain topValue;

    public Environment(Domain topValue) {
        this.nodeToDomainMap = new HashMap<>();
        this.topValue = topValue.copyOf();
    }

    public Environment(Domain topValue, Map<Node, Domain> initialBindings) {
        this.nodeToDomainMap = new HashMap<>();
        this.topValue = topValue;
        for (Map.Entry<Node, Domain> entry : initialBindings.entrySet()) {
            if (entry.getValue().isBot()) {
                return;
            }
            this.nodeToDomainMap.put(entry.getKey(), entry.getValue());
        }
    }

    public int size() {
        return nodeToDomainMap.size();
    }

    public Domain get(Node node) {
        return this.nodeToDomainMap.getOrDefault(node, topValue);
    }

    public void set(Node node, Domain value) {
        this.nodeToDomainMap.put(node, value);
    }

    public void reset() {
        this.nodeToDomainMap.clear();
    }
}