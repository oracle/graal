package com.oracle.svm.hosted.analysis.ai.analysis.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Represents a call stack used to keep track and manage invoked methods during analysis.
 */
public final class CallStack {

    private final Deque<AnalysisMethod> callStack = new LinkedList<>();
    private final int maxCallStackDepth;

    public CallStack(int maxCallStackDepth) {
        this.maxCallStackDepth = maxCallStackDepth;
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

    public int getMaxCallStackDepth() {
        return maxCallStackDepth;
    }

    /**
     * Returns a snapshot list of the methods currently on the call stack.
     */
    public List<AnalysisMethod> getMethods() {
        return new ArrayList<>(callStack);
    }

    /**
     * Returns the current depth of the call stack.
     */
    public int getDepth() {
        return callStack.size();
    }

    /**
     * Counts the number of times an analysis method has been consecutively called in the current call stack.
     *
     * @param method the analysisMethod to count recursive calls for
     * @return the number of consecutive the specified analysisMethod
     */
    public int countConsecutiveCalls(AnalysisMethod method) {
        int count = 0;
        String qualifiedName = method.getQualifiedName();
        for (AnalysisMethod callStackMethod : callStack) {
            if (callStackMethod.getQualifiedName().equals(qualifiedName)) {
                count++;
            }
        }
        return count;
    }

    public boolean hasMethodCallCycle(AnalysisMethod method) {
        List<AnalysisMethod> compactedCallStack = new LinkedList<>();
        for (AnalysisMethod callStackMethod : callStack) {
            if (compactedCallStack.isEmpty() || !compactedCallStack.getLast().equals(callStackMethod)) {
                compactedCallStack.add(callStackMethod);
            }
        }

        /*
         *  We don't want a trivial cycles like foo() -> foo(), which means simple recursion.
         *  We are interested in cycles like foo() -> bar() -> foo() or foo() -> bar() -> baz() -> foo()
         */
        List<AnalysisMethod> methodList = compactedCallStack.reversed();
        for (int i = 0; i < methodList.size() - 1; i++) {
            if (methodList.get(i).equals(method)) {
                return true;
            }
        }

        return false;
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
        boolean first = true;
        for (AnalysisMethod method : callStack) {
            if (!first) {
                sb.append(" ← ");
            }
            first = false;
            sb.append(method.getQualifiedName());
        }
        return sb.toString();
    }

    public Deque<AnalysisMethod> getCallStack() {
        return callStack;
    }
}
