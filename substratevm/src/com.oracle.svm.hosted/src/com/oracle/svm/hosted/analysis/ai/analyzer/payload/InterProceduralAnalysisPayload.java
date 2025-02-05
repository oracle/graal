package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.DefaultMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.MethodFilter;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

import java.util.List;

/**
 * Represents the payload of an inter-procedural abstract interpretation analysis.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public class InterProceduralAnalysisPayload<
        Domain extends AbstractDomain<Domain>>
        extends AnalysisPayload<Domain> {

    private final SummaryCache<Domain> summaryCache = new SummaryCache<>();
    private final CallStack<Domain> callStack = new CallStack<>();
    private final SummarySupplier<Domain> summarySupplier;
    private final MethodFilter methodFilter;

    public InterProceduralAnalysisPayload(
            Domain initialDomain,
            IteratorPolicy iteratorPolicy,
            AnalysisMethod root,
            DebugContext debugContext,
            NodeInterpreter<Domain> nodeInterpreter,
            SummarySupplier<Domain> summarySupplier,
            CheckerManager checkerManager
    ) {
        super(initialDomain, iteratorPolicy, root, debugContext, nodeInterpreter, checkerManager);
        this.summarySupplier = summarySupplier;
        this.methodFilter = new DefaultMethodFilter();
        // TODO consult how to do this
        callStack.push(root, null);
    }

    public InterProceduralAnalysisPayload(
            Domain initialDomain,
            IteratorPolicy iteratorPolicy,
            AnalysisMethod root,
            DebugContext debugContext,
            NodeInterpreter<Domain> nodeInterpreter,
            SummarySupplier<Domain> summarySupplier,
            CheckerManager checkerManager,
            MethodFilter methodFilter
    ) {
        super(initialDomain, iteratorPolicy, root, debugContext, nodeInterpreter, checkerManager);
        this.summarySupplier = summarySupplier;
        this.methodFilter = methodFilter;
        // TODO consult how to do this
        callStack.push(root, null);
    }

    public SummaryCache<Domain> getSummaryCache() {
        return summaryCache;
    }

    public CallStack<Domain> getCallStack() {
        return callStack;
    }

    public String getCallStackString() {
        return callStack.toString();
    }

    public SummarySupplier<Domain> getSummarySupplier() {
        return summarySupplier;
    }

    public AnalysisMethod getRoot() {
        return callStack.getCurrentMethod();
    }

    public MethodFilter getMethodFilter() {
        return methodFilter;
    }

    public Summary<Domain> getCurrentSummaryPreCondition() {
        return callStack.getCurrentPreConditionSummary();
    }

    public List<Domain> getCurrentActualArguments() {
        return callStack.getCurrentPreConditionSummary().getActualArguments();
    }

    public Domain getCurrentActualArgumentAt(int index) {
        Summary<Domain> summaryPreCondition = callStack.getCurrentPreConditionSummary();
        if (summaryPreCondition == null) {
            return Domain.createTop(initialDomain);
        }

        List<Domain> arguments = summaryPreCondition.getActualArguments();
        if (index < 0 || index >= arguments.size()) {
            return Domain.createTop(initialDomain);
        }
        return summaryPreCondition.getActualArgumentAt(index);
    }
}