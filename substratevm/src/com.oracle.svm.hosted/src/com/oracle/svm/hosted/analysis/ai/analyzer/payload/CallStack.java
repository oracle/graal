package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Represents the call stack of an abstract interpretation analysis
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public final class CallStack<Domain extends AbstractDomain<Domain>> {

    private final Deque<StackRecord<Domain>> callStack = new LinkedList<>();

    public void push(StackRecord<Domain> stackRecord) {
        callStack.push(stackRecord);
    }

    public void push(AnalysisMethod method, Summary<Domain> preConditionSummary) {
        callStack.push(new StackRecord<>(method, preConditionSummary));
    }

    public void pop() {
        callStack.pop();
    }

    StackRecord<Domain> getCurrentStackRecord() {
        return callStack.peek();
    }

    AnalysisMethod getCurrentMethod() {
        return getCurrentStackRecord().method();
    }

    Summary<Domain> getCurrentPreConditionSummary() {
        return getCurrentStackRecord().preConditionSummary();
    }

    /**
     * Counts the number of recursive calls of the specified method in the call stack.
     *
     * @param method the method to count recursive calls for
     * @return the number of recursive calls of the specified method
     */
    public int countRecursiveCalls(AnalysisMethod method) {
        int count = 0;
        String qualifiedName = method.getQualifiedName();
        for (StackRecord<Domain> record : callStack) {
            if (record.method().getQualifiedName().equals(qualifiedName)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (StackRecord<Domain> record : callStack) {
            Summary<Domain> preConditionSummary = record.preConditionSummary();
            sb.append("Method: ").append(record.method().toString()).append(", Summary: ");
            if (preConditionSummary == null) {
                sb.append("[ ]");
            } else {
                sb.append(record.preConditionSummary());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
