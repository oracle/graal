package com.oracle.svm.hosted.analysis.ai.access;

/**
 * Represents a field access in an access path (e.g., .name, .address)
 */
public class FieldAccess implements AccessPathElement {

    private final String fieldName;

    public FieldAccess(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String toString() {
        return "." + fieldName;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FieldAccess)) return false;
        return fieldName.equals(((FieldAccess) o).fieldName);
    }

    @Override
    public int hashCode() {
        return fieldName.hashCode();
    }
}