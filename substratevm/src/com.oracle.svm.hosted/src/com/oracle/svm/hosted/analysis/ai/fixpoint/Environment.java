package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.LatticeDomain;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValue;
import com.oracle.svm.hosted.analysis.ai.value.AbstractValueKind;
import jdk.graal.compiler.graph.Node;

import java.util.HashMap;
import java.util.Map;

/**
 * This class maps Nodes of StructuredGraph to a common abstract domain,
 * acting as a memory of FixpointIterator
 * An example could be mapping Nodes to intervals or constants
 * Constructors of Environment require passing the class of domain to which
 * implementations want to map to, because we need to be able to call `top` or `bot` on that domain
 *
 * @param <Value>  type of the derived abstract value
 * @param <Domain> type of the derived abstract domain
 */

public class Environment<
        Value extends AbstractValue<Value>,
        Domain extends LatticeDomain<Value, Domain>>
        extends LatticeDomain<Value, Domain> {

    private final Map<Node, Domain> nodeToDomainMap;
    private final Class<Domain> domainClass;

    public Environment(Class<Domain> domainClass) {
        this.domainClass = domainClass;
        this.nodeToDomainMap = new HashMap<>();
        this.kind = AbstractValueKind.TOP;
    }

    public Environment(AbstractValueKind kind, Class<Domain> domainClass) {
        this.domainClass = domainClass;
        this.nodeToDomainMap = new HashMap<>();
        this.kind = kind;
    }

    public Environment(Map<Node, Domain> initialBindings, Class<Domain> domainClass) {
        this.domainClass = domainClass;
        this.nodeToDomainMap = new HashMap<>();
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
        return this.nodeToDomainMap.getOrDefault(node, Domain.createTop(domainClass));
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