package com.oracle.svm.hosted.analysis.ai.analyzer;

import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;

/**
 * Basic interface for all analyzers
 * @param <Domain> type of derived AbstractDomain
 */

public interface Analyzer<Domain extends AbstractDomain<Domain>> {
    Domain analyze();
}