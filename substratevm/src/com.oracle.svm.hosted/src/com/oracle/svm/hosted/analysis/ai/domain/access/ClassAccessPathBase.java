package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.Objects;

/**
 * Static fields will not have an object as their base but a class, which has no bytecode position
 */
public record ClassAccessPathBase(ResolvedJavaType type) implements AccessPathBase {

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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ClassAccessPathBase that = (ClassAccessPathBase) o;
        return Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(type);
    }
}
