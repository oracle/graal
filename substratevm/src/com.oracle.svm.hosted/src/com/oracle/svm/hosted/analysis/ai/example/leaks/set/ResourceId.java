package com.oracle.svm.hosted.analysis.ai.example.leaks.set;

import jdk.graal.compiler.graph.NodeSourcePosition;

import java.util.Objects;

/**
 * In this example the ID's of resources are represented by their source position in the Graal IR.
 */
public class ResourceId {

    private final String id;

    public ResourceId(String id) {
        this.id = id;
    }

    public ResourceId(NodeSourcePosition position) {
        String[] parts = position.toString().split(" ");
        id = parts[1];
    }

    public String getId() {
        return id;
    }

    public ResourceId addPrefix(String prefix) {
        return new ResourceId(prefix + id);
    }

    public ResourceId removePrefix(String regex) {
        return new ResourceId(id.replaceAll(regex, ""));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        ResourceId that = (ResourceId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return id;
    }
}
