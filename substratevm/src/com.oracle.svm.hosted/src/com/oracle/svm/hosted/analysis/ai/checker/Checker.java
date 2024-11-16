package com.oracle.svm.hosted.analysis.ai.checker;

import com.oracle.svm.hosted.analysis.ai.fixpoint.Environment;

public interface Checker {
    Environment<?> runAnalysis();
}