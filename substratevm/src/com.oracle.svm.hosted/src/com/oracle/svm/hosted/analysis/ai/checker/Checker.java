package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;

public interface Checker<Domain extends AbstractDomain<Domain>> {
    Environment<Domain> check();
}