package com.oracle.truffle.api.dsl.internal;

import java.util.*;

import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.nodes.*;

/**
 * This is NOT public API. Do not use directly. This code may change without notice.
 */
public abstract class NodeFactoryBase<T> implements NodeFactory<T> {

    private final Class<T> nodeClass;
    private final Class<?>[][] nodeSignatures;
    private final Class<? extends Node>[] executionSignatures;

    @SuppressWarnings("unchecked")
    public NodeFactoryBase(Class<T> nodeClass, Class<?>[] executionSignatures, Class<?>[][] nodeSignatures) {
        this.nodeClass = nodeClass;
        this.nodeSignatures = nodeSignatures;
        this.executionSignatures = (Class<? extends Node>[]) executionSignatures;
    }

    public abstract T createNode(Object... arguments);

    public final Class<T> getNodeClass() {
        return nodeClass;
    }

    public final List<List<Class<?>>> getNodeSignatures() {
        List<List<Class<?>>> signatures = new ArrayList<>();
        for (int i = 0; i < nodeSignatures.length; i++) {
            signatures.add(Arrays.asList(nodeSignatures[i]));
        }
        return signatures;
    }

    public final List<Class<? extends Node>> getExecutionSignature() {
        return Arrays.asList(executionSignatures);
    }

}
