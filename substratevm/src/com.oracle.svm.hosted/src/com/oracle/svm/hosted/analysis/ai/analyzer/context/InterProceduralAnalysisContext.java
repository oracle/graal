package com.oracle.svm.hosted.analysis.ai.analyzer.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.filter.DefaultMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.filter.MethodFilter;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Represents the context of an inter-procedural abstract interpretation analysis.
 *
 * @param <Domain> the type of derived {@link AbstractDomain} the analysis is running on
 */
public class InterProceduralAnalysisContext<
        Domain extends AbstractDomain<Domain>>
        extends AnalysisContext<Domain> {

    private final SummaryCache<Domain> summaryCache = new SummaryCache<>();
    private final CallStack callStack = new CallStack();
    private final SummarySupplier<Domain> summarySupplier;
    private final MethodFilter methodFilter;

    public InterProceduralAnalysisContext(
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
        callStack.push(root);
    }

    public InterProceduralAnalysisContext(
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
        callStack.push(root);
    }

    public SummaryCache<Domain> getSummaryCache() {
        return summaryCache;
    }

    public CallStack getCallStack() {
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
}