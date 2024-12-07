package com.oracle.svm.hosted.analysis.ai.interpreter;

import jdk.graal.compiler.nodes.InvokeNode;

public interface CallInterpreter {
    void exec(InvokeNode node);
}
