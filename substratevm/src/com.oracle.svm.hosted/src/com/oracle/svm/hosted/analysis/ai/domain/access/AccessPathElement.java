package com.oracle.svm.hosted.analysis.ai.domain.access;

/**
 * Represents an element in an access path - either a field access or an array index
 */
public interface AccessPathElement {

    enum Kind {
        FIELD,
        ARRAY
    }

    boolean equals(Object other);

    int hashCode();

    Kind getKind();

    boolean isStatic();
}
