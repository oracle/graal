package com.oracle.svm.hosted.analysis.ai.domain.access;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.Objects;

/**
 * Represents base of an access path to an object
 */
public final class ObjectAccessPathBase implements AccessPathBase {

    private final String prefix;
    private final ResolvedJavaType type;
    private final NodeSourcePosition position;

    public ObjectAccessPathBase(ResolvedJavaType type, NodeSourcePosition position) {
        this.prefix = "";
        this.type = type;
        this.position = position;
    }

    public ObjectAccessPathBase(ResolvedJavaType type, NodeSourcePosition position, String prefix) {
        this.prefix = prefix;
        this.type = type;
        this.position = position;
    }

    @Override
    public ResolvedJavaType type() {
        return type;
    }

    /**
     * When passing object to a method as an argument, we do a renaming ( we add prefix ) to the object's access path
     * We don't want to create a placeHolder base because we would essentially lose information about type and position
     *
     * @param prefix to add
     */
    @Override
    public ObjectAccessPathBase addPrefix(String prefix) {
        String newPrefix = prefix + this.prefix;
        return new ObjectAccessPathBase(type, position, newPrefix);
    }

    /**
     * This is done in order to remove some prefix when joining this object back to the caller environment
     *
     * @param regex to remove
     */
    @Override
    public ObjectAccessPathBase removePrefix(String regex) {
        String newPrefix = this.prefix.replaceFirst(regex, "");
        return new ObjectAccessPathBase(type, position, newPrefix);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ObjectAccessPathBase that = (ObjectAccessPathBase) o;
        return Objects.equals(type, that.type) && Objects.equals(position, that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, position);
    }

    @Override
    public String toString() {
        if (position == null) {
            return prefix + type.toJavaName();
        }

        return prefix + type.toJavaName() + '(' + position + ')';
    }
}
