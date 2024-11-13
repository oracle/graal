package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

public interface Analyzer<Domain extends AbstractDomain<Domain>> {
    Domain analyze();
}