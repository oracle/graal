package com.oracle.svm.hosted.analysis.ai.access;

/**
 * Represents an element in an access path - either a field access or an array index
 */
public interface AccessPathElement {

    String toString();

    boolean equals(Object other);

    int hashCode();
}