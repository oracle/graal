package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Static fields will not have an object as their base but a class, which has no bytecode position
 */
public record ClassAccessPathBase(ResolvedJavaType type) implements AccessPathBase {

    @Override
    public NodeSourcePosition getByteCodePosition() {
        return null;
    }

    /**
     * This base should always stay the same so this method does nothing
     */
    @Override
    public ClassAccessPathBase addPrefix(String prefix) {
        return this;
    }

    /**
     * This base should always stay the same so this method does nothing
     */
    @Override
    public ClassAccessPathBase removePrefix(String regex) {
        return this;
    }

    @Override
    public String toString() {
        return type.toJavaName();
    }
}
