package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Represents a call stack used to manage stack frames during execution or analysis processes.
 * This class provides functionality to push, pop, and query stack records. It also manages
 * recursion depth and provides mechanisms to count recursive invocations of specified methods.
 * Instances of this class are immutable with respect to their maximum recursion depth.
 */
public final class CallStack {

    private final Deque<AnalysisMethod> callStack = new LinkedList<>();
    private final int maxRecursionDepth;

    public CallStack(int maxRecursionDepth) {
        this.maxRecursionDepth = maxRecursionDepth;
    }

    public CallStack() {
        this(10);
    }

    public void push(AnalysisMethod analysisMethod) {
        callStack.push(analysisMethod);
    }

    public void pop() {
        callStack.pop();
    }

    public AnalysisMethod getCurrentAnalysisMethod() {
        return callStack.peek();
    }

    public int getMaxRecursionDepth() {
        return maxRecursionDepth;
    }

    /**
     * Counts the number of recursive calls of the specified analysisMethod in the call stack.
     *
     * @param method the analysisMethod to count recursive calls for
     * @return the number of recursive calls of the specified analysisMethod
     */
    public int countRecursiveCalls(AnalysisMethod method) {
        int count = 0;
        String qualifiedName = method.getQualifiedName();
        for (AnalysisMethod callStackMethod : callStack) {
            if (callStackMethod.getQualifiedName().equals(qualifiedName)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (AnalysisMethod method : callStack) {
            sb.append(method.getQualifiedName()).append(" <- ");
        }
        return sb.toString();
    }
}
