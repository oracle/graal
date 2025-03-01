package com.oracle.svm.hosted.analysis.ai.domain.access;

/**
 * Represents a field access in an access path (e.g., .name, .address)
 */
public final class FieldAccess implements AccessPathElement {

    private final String fieldName;
    private final boolean isStatic;

    public FieldAccess(String fieldName, boolean isStatic) {
        this.fieldName = fieldName;
        this.isStatic = isStatic;
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


    @Override
    public Kind getKind() {
        return Kind.FIELD;
    }

    @Override
    public boolean isStatic() {
        return isStatic;
    }
}
