package com.oracle.svm.hosted.analysis.ai.analyzer.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.Deque;
import java.util.LinkedList;

/**
 * A stack of methods that are currently being analyzed.
 */
public class CallStack {

    private final Deque<AnalysisMethod> callStack = new LinkedList<>();

    public void push(AnalysisMethod method) {
        callStack.push(method);
    }

    public void pop() {
        callStack.pop();
    }

    AnalysisMethod getCurrentMethod() {
        return callStack.peek();
    }

    public String toString() {
        return callStack.toString();
    }

}
