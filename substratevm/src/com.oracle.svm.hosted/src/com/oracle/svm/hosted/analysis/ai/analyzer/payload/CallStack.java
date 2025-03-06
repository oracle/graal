package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Represents a call stack used to keep track and manage invoked methods during analysis.
 */
// TODO: analysisMethod probably is too broad, we can have like resolvedJavaMethod or something lighter
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

    public boolean containsMethod(AnalysisMethod method) {
        return callStack.contains(method);
    }

    public String formatCycleWithMethod(AnalysisMethod method) {
        StringBuilder sb = new StringBuilder();
        int index = 0;
        AnalysisMethod firstOccurrence = null;

        for (AnalysisMethod callStackMethod : callStack) {
            if (callStackMethod.equals(method)) {
                firstOccurrence = callStackMethod;
                break;
            }
            index++;
        }

        if (firstOccurrence != null) {
            // Format the cycle path
            int count = 0;
            for (AnalysisMethod callStackMethod : callStack) {
                if (count >= index) {
                    sb.append(callStackMethod.getQualifiedName());
                    sb.append(" → ");
                }
                count++;
            }
            sb.append(method.getQualifiedName());
        } else {
            sb.append("Unknown cycle with ").append(method.getQualifiedName());
        }

        return sb.toString();
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
