package com.oracle.svm.hosted.analysis.ai.access;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.vm.ci.meta.ResolvedJavaField;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a complete access path, consisting of a base variable and a sequence of accesses
 */
public class AccessPath {

    private final String baseVariable;
    private final List<AccessPathElement> elements;

    public AccessPath(String baseVariable) {
        this.baseVariable = baseVariable;
        this.elements = new ArrayList<>();
    }

    public AccessPath appendField(String fieldName) {
        AccessPath newPath = new AccessPath(baseVariable);
        newPath.elements.addAll(this.elements);
        newPath.elements.add(new FieldAccess(fieldName));
        return newPath;
    }

    public AccessPath appendArrayAccess(String index) {
        AccessPath newPath = new AccessPath(baseVariable);
        newPath.elements.addAll(this.elements);
        newPath.elements.add(new ArrayAccess(index));
        return newPath;
    }

    /***
     * Check if two access paths share the same base variable and prefix
     *
     * @param other AccessPath
     * @return true if the two access paths share the same base variable and prefix
     */
    public boolean sharesPrefix(AccessPath other) {
        if (!baseVariable.equals(other.baseVariable)) {
            return false;
        }

        int minLength = Math.min(elements.size(), other.elements.size());
        for (int i = 0; i < minLength - 1; i++) {
            if (!elements.get(i).equals(other.elements.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(baseVariable);
        for (AccessPathElement element : elements) {
            sb.append(element.toString());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AccessPath other)) return false;
        return baseVariable.equals(other.baseVariable) && elements.equals(other.elements);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseVariable, elements);
    }

    /**
     * Create an AccessPath from a StoreFieldNode.
     * @param storeFieldNode StoreFieldNode
     * @return AccessPath representing the storeFieldNode
     */
    public static AccessPath fromStoreFieldNode(StoreFieldNode storeFieldNode) {
        ResolvedJavaField field = storeFieldNode.field();
        String baseVariable;
        if (field.isStatic()) {
            // For static fields, use the declaring class as the base variable
            baseVariable = field.getDeclaringClass().toJavaName();
        } else {
            String declaringClass = field.getDeclaringClass().toJavaName();
            String nodeSourcePosition = storeFieldNode.object().getNodeSourcePosition().toString();
            String[] lst = nodeSourcePosition.split(" ");
            /* lst[ 0 ] = "at", lst[ 1 ] = "actual source position", lst[ 2 ] = bci */
            baseVariable = declaringClass + "@" + lst[1];
        }

        return new AccessPath(baseVariable).appendField(field.getName());
    }


    /**
     * Create an AccessPath from a LoadFieldNode.
     * @param loadFieldNode LoadFieldNode
     * @return AccessPath representing the loadFieldNode
     */
    public static AccessPath fromLoadFieldNode(LoadFieldNode loadFieldNode) {
        ResolvedJavaField field = loadFieldNode.field();
        String baseVariable;
        if (field.isStatic()) {
            baseVariable = field.getDeclaringClass().toJavaName();
        } else {
            String declaringClass = field.getDeclaringClass().toJavaName();
            String nodeSourcePosition = loadFieldNode.object().getNodeSourcePosition().toString();
            String[] lst = nodeSourcePosition.split(" ");
            /* lst[ 0 ] = "at", lst[ 1 ] = "actual source position", lst[ 2 ] = bci */
            baseVariable = declaringClass + "@" + lst[1];
        }
        return new AccessPath(baseVariable).appendField(field.getName());
    }

    /**
     * Create an AccessPath from a StoreIndexedNode.
     * @param storeIndexedNode StoreIndexedNode
     * @return AccessPath representing the storeIndexedNode
     */
    public static AccessPath fromStoreIndexedNode(StoreIndexedNode storeIndexedNode) {
        ValueNode array = storeIndexedNode.array();
        ValueNode index = storeIndexedNode.index();
        String baseVariable = array.toString(); // Use the array's name as the base variable
        return new AccessPath(baseVariable).appendArrayAccess(index.toString());
    }

    /**
     * Create an AccessPath from a LoadIndexedNode.
     * @param loadIndexedNode LoadIndexedNode
     * @return AccessPath representing the loadIndexedNode
     */
    public static AccessPath fromLoadIndexedNode(LoadIndexedNode loadIndexedNode) {
        ValueNode array = loadIndexedNode.array();
        ValueNode index = loadIndexedNode.index();
        String baseVariable = array.toString();
        return new AccessPath(baseVariable).appendArrayAccess(index.toString());
    }

}