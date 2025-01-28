package com.oracle.svm.hosted.analysis.ai.domain.access;

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
}