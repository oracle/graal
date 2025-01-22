package com.oracle.svm.hosted.analysis.ai.domain;

import com.oracle.svm.hosted.analysis.ai.domain.access.Access;
import com.oracle.svm.hosted.analysis.ai.domain.access.Base;

import java.util.List;
import java.util.Objects;

public class AccessPathDomain extends AbstractDomain<AccessPathDomain> {

    private final Base base;
    private final List<Access> accessList;

    public AccessPathDomain(Base base, List<Access> accessList) {
        this.base = base;
        this.accessList = accessList;
    }

    public Base getBase() {
        return base;
    }

    public List<Access> getAccessList() {
        return accessList;
    }

    @Override
    public boolean isBot() {
        return false;
    }

    @Override
    public boolean isTop() {
        return false;
    }

    @Override
    public boolean leq(AccessPathDomain other) {
        return equals(other);
    }

    @Override
    public boolean equals(AccessPathDomain other) {
        return Objects.equals(base, other.base) && Objects.equals(accessList, other.accessList);
    }

    @Override
    public void setToBot() {
        /* no-op */
    }

    @Override
    public void setToTop() {
        /* no-op */
    }

    @Override
    public void joinWith(AccessPathDomain other) {
        /* no-op */
    }

    @Override
    public void widenWith(AccessPathDomain other) {
        /* no-op */
    }

    @Override
    public void meetWith(AccessPathDomain other) {
        /* no-op */
    }

    @Override
    public String toString() {
        return "AccessPathDomain{" +
                "base=" + base +
                ", accessList=" + accessList +
                '}';
    }

    @Override
    public AccessPathDomain copyOf() {
        return new AccessPathDomain(base, accessList);
    }
}
