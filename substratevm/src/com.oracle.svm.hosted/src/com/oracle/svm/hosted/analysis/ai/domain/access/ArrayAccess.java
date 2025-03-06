package com.oracle.svm.hosted.analysis.ai.domain.access;

/**
 * Represents an array access in an access path (e.g., [0], [i])
 */
public final class ArrayAccess implements AccessPathElement {

    private final String index; // Could be a constant or symbolic

    public ArrayAccess(String index) {
        this.index = index;
    }

    @Override
    public Kind getKind() {
        return Kind.ARRAY;
    }

    @Override
    public String getName() {
        return index;
    }

    @Override
    public String toString() {
        return "[" + index + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ArrayAccess)) return false;
        return index.equals(((ArrayAccess) o).index);
    }

    @Override
    public int hashCode() {
        return index.hashCode();
    }
}
