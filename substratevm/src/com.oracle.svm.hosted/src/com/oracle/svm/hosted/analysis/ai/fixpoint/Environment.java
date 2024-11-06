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
 * @param <Value>  type of the derived abstract value
 * @param <Domain> type of the derived abstract domain
 */
public class Environment<
        Value extends AbstractValue<Value>,
        Domain extends AbstractDomain<Domain>> {

    private final Map<Node, Domain> nodeToDomainMap;
    private Domain topValue;
    private AbstractValueKind kind;

    public Environment(Domain topValue) {
        this.nodeToDomainMap = new HashMap<>();
        this.topValue = topValue;
        this.kind = AbstractValueKind.TOP;
    }

    public Environment(Domain topValue, AbstractValueKind kind) {
        this.nodeToDomainMap = new HashMap<>();
        this.topValue = topValue;
        this.kind = kind;
    }

    public Environment(Domain topValue, Map<Node, Domain> initialBindings) {
        this.nodeToDomainMap = new HashMap<>();
        this.topValue = topValue;
        for (Map.Entry<Node, Domain> entry : initialBindings.entrySet()) {
            if (entry.getValue().isBot()) {
                this.kind = AbstractValueKind.BOT;
                return;
            }
            this.nodeToDomainMap.put(entry.getKey(), entry.getValue());
        }
        this.kind = AbstractValueKind.VAL;
    }

    public boolean isVal() {
        return this.kind == AbstractValueKind.VAL;
    }

    public int size() {
        return nodeToDomainMap.size();
    }

    public Domain get(Node node) {
        if (this.kind == AbstractValueKind.BOT) {
            return null;
        }
        return this.nodeToDomainMap.getOrDefault(node, topValue);
    }

    public Environment<Value, Domain> set(Node node, Domain value) {
        if (this.kind == AbstractValueKind.BOT) {
            return this;
        }
        if (value.isBot()) {
            this.kind = AbstractValueKind.BOT;
        } else {
            this.nodeToDomainMap.put(node, value);
            this.kind = AbstractValueKind.VAL;
        }
        return this;
    }

    public void reset() {
        this.nodeToDomainMap.clear();
        this.kind = AbstractValueKind.TOP;
    }
}