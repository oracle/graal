package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.Objects;

/**
 * Represents access paths that to temporary values,
 * For example this can be used to map actual parameters to formals in a method invocation.
 */
public final class PlaceHolderAccessPathBase implements AccessPathBase {

    private final String name;

    public PlaceHolderAccessPathBase(String name) {
        this.name = name;
    }

    @Override
    public ResolvedJavaType type() {
        return null;
    }

    @Override
    public NodeSourcePosition getByteCodePosition() {
        return null;
    }

    /**
     * This base should always stay the same so this method does nothing
     */
    @Override
    public AccessPathBase addPrefix(String prefix) {
        return this;
    }

    /**
     * This base should always stay the same so this method does nothing
     */
    @Override
    public AccessPathBase removePrefix(String regex) {
        return this;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        PlaceHolderAccessPathBase that = (PlaceHolderAccessPathBase) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name);
    }
}
