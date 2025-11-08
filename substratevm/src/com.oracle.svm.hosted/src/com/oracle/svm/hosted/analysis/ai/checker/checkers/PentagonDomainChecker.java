package com.oracle.svm.hosted.analysis.ai.checker.checkers;

import com.oracle.svm.hosted.analysis.ai.checker.core.Checker;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.FactKind;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.PentagonDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;

import java.util.Set;

public class PentagonDomainChecker implements Checker<PentagonDomain<AccessPath>> {

    @Override
    public String getDescription() {
        return "Pentagon domain checker";
    }

    // TODO: add check() implementation

    @Override
    public boolean isCompatibleWith(AbstractState<?> abstractState) {
        return abstractState.getInitialDomain() instanceof PentagonDomain;
    }
}
