package com.oracle.svm.hosted.analysis.ai.domain.access;

import java.lang.reflect.Modifier;

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

    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    public boolean isPrivate() {
        return Modifier.isPrivate(modifiers);
    }

    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    public boolean isProtected() {
        return Modifier.isProtected(modifiers);
    }

    public boolean isSynchronized() {
        return Modifier.isSynchronized(modifiers);
    }

    public boolean isVolatile() {
        return Modifier.isVolatile(modifiers);
    }

    public boolean isTransient() {
        return Modifier.isVolatile(modifiers);
    }

    public boolean isNative() {
        return Modifier.isNative(modifiers);
    }

    public boolean isInterface() {
        return Modifier.isInterface(modifiers);
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(modifiers);
    }

    public boolean isStrict() {
        return Modifier.isStrict(modifiers);
    }

}
