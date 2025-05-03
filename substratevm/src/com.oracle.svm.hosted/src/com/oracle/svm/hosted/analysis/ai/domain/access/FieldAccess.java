package com.oracle.svm.hosted.analysis.ai.domain.access;

/**
 * Represents a field access in an access path (e.g., .name, .address)
 */
public final class FieldAccess implements AccessPathElement {

    private final String fieldName;
    private final int modifiers;

    public FieldAccess(String fieldName, int modifiers) {
        this.fieldName = fieldName;
        this.modifiers = modifiers;
    }

    public int getModifiers() {
        return modifiers;
    }

    @Override
    public Kind getKind() {
        return Kind.FIELD;
    }

    @Override
    public String getName() {
        return fieldName;
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
