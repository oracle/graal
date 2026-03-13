package com.oracle.svm.hosted.analysis.ai.domain.util;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Represents an empty {@link AbstractDomain}.
 * Mostly used for testing purposes.
 * This class is used as a placeholder for an abstract domain that does not contain any elements.
 * It is useful in scenarios where the framework requires an abstract domain, but no actual
 * domain-specific logic is needed.
 */
public final class EmptyDomain implements AbstractDomain<EmptyDomain> {

    public EmptyDomain() {
    }

    public EmptyDomain(EmptyDomain other) {
    }

    @Override
    public EmptyDomain copyOf() {
        return new EmptyDomain();
    }

    @Override
    public boolean isBot() {
        return true;
    }

    @Override
    public boolean isTop() {
        return true;
    }

    @Override
    public boolean leq(EmptyDomain other) {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public void setToBot() {
    }

    @Override
    public void setToTop() {
    }

    @Override
    public void joinWith(EmptyDomain other) {
    }

    @Override
    public void widenWith(EmptyDomain other) {
    }

    @Override
    public void meetWith(EmptyDomain other) {
    }

    @Override
    public String toString() {
        return "Empty";
    }
}
