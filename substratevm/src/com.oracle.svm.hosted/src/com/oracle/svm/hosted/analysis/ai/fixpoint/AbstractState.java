package com.oracle.svm.hosted.analysis.ai.fixpoint;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

public class AbstractState<Domain extends AbstractDomain<Domain>> {
    private Domain domain;
    private int visitedCount = 0;

    public AbstractState(Domain domain) {
        this.domain = domain;
    }

    public Domain getDomain() {
        return domain;
    }

    public int getVisitedCount() {
        return visitedCount;
    }

    public void incrementVisitedCount() {
        ++visitedCount;
    }

    public void setDomain(Domain domain) {
        this.domain = domain;
    }

    public boolean isVisited() {
        return visitedCount > 0;
    }
}
