package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.CallContextHolder;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.CallStack;
import com.oracle.svm.hosted.analysis.ai.analyzer.metadata.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.*;
import jdk.graal.compiler.nodes.Invoke;

import java.util.List;

/**
 * Inter-procedural invoke handler using per-call context and summary caching.
 */
public final class InterAbsintInvokeHandler<Domain extends AbstractDomain<Domain>> extends AbsintInvokeHandler<Domain> {

    private final CallStack methodStack;
    private final SummaryManager<Domain> summaryManager;
    private final SummaryRepository<Domain> summaryRepository;

    public InterAbsintInvokeHandler(
            Domain initialDomain,
            AbstractInterpreter<Domain> abstractInterpreter,
            AnalysisContext analysisContext,
            SummaryFactory<Domain> summaryFactory,
            int maxRecursionDepth) {
        super(initialDomain, abstractInterpreter, analysisContext);
        this.methodStack = new CallStack(maxRecursionDepth);
        this.summaryManager = new SummaryManager<>(summaryFactory);
        this.summaryRepository = new SummaryRepository<>();
    }

    @Override
    public AnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        Invoke invoke = invokeInput.invoke();

        // Prefer callerMethod from input; fall back to current stack top.
        AnalysisMethod current = invokeInput.callerMethod() != null ? invokeInput.callerMethod() : methodStack.getCurrentAnalysisMethod();
        if (current == null) {
            return AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
        }

        AnalysisMethod targetAnalysisMethod;
        try {
            targetAnalysisMethod = getInvokeTargetAnalysisMethod(current, invoke);
        } catch (Exception e) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        AbstractState<Domain> callerState = invokeInput.callerState();
        // Use already evaluated argument domains from InvokeInput (Phase 1 integration)
        List<Domain> actualArgDomains = invokeInput.actualArgDomains();

        // Pre-condition for summary may be caller pre at invoke node
        Domain callerPreAtInvoke = callerState.getPreCondition(invoke.asNode());
        Summary<Domain> summary = summaryManager.createSummary(invoke, callerPreAtInvoke, actualArgDomains);

        String ctxSig = CallContextHolder.buildKCFASignature(methodStack.getCallStack(), 2);
        ContextKey calleeKey = new ContextKey(targetAnalysisMethod, ctxSig.hashCode(), methodStack.getDepth());
        MethodSummary<Domain> methodSummary = summaryRepository.getOrCreate(targetAnalysisMethod);
        methodSummary.getOrCreate(calleeKey, summary.getPreCondition());

        if (summaryManager.containsSummary(targetAnalysisMethod, summary)) {
            logger.log("Summary cache contains targetMethod: " + targetAnalysisMethod, LoggerVerbosity.SUMMARY);
            Summary<Domain> completeSummary = summaryManager.getSummary(targetAnalysisMethod, summary);
            ContextSummary<Domain> ctxSummary = methodSummary.get(calleeKey);
            if (ctxSummary != null && completeSummary.getPostCondition() != null) {
                ctxSummary.updateWith(completeSummary.getPostCondition(), null);
            }
            return AnalysisOutcome.ok(completeSummary);
        }

        if (methodStack.countConsecutiveCalls(targetAnalysisMethod) >= methodStack.getMaxRecursionDepth()) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }
        if (methodStack.hasMethodCallCycle(targetAnalysisMethod)) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.MUTUAL_RECURSION_CYCLE);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            logger.log(methodStack.formatCycleWithMethod(targetAnalysisMethod), LoggerVerbosity.INFO);
            return outcome;
        }

        methodStack.push(targetAnalysisMethod);
        try {
            FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(targetAnalysisMethod, initialDomain, abstractTransformer, analysisContext);
            fixpointIterator.getIteratorContext().setCallContextSignature(ctxSig);
            fixpointIterator.getAbstractState().setStartNodeState(summary.getPreCondition());
            logger.log("The current call stack: " + methodStack, LoggerVerbosity.INFO);
            AbstractState<Domain> invokeAbstractState = fixpointIterator.iterateUntilFixpoint();
            summary.finalizeSummary(invokeAbstractState);
            summaryManager.putSummary(targetAnalysisMethod, summary);
        } finally {
            methodStack.pop();
        }
        return AnalysisOutcome.ok(summary);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        if (methodFilterManager.shouldSkipMethod(root)) {
            return;
        }

        String ctxSig = CallContextHolder.buildKCFASignature(methodStack.getCallStack(), 2);
        try {
            FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, abstractTransformer, analysisContext);
            fixpointIterator.getIteratorContext().setCallContextSignature(ctxSig);

            methodStack.push(root);
            AbstractState<Domain> abstractState = fixpointIterator.iterateUntilFixpoint();
            methodStack.pop();
            var logger = AbstractInterpretationLogger.getInstance();
            logger.printLabelledGraph(analysisContext.getMethodGraphCache().getMethodGraph().get(root), root, abstractState);
            checkerManager.runCheckers(root, abstractState);
            logger.logSummariesStats(summaryManager);
        } finally {
            // stack cleanup handled in pop
        }
    }

    private AnalysisMethod getInvokeTargetAnalysisMethod(AnalysisMethod root, Invoke invoke) {
        for (InvokeInfo invokeInfo : root.getInvokes()) {
            if (invoke.getTargetMethod().equals(invokeInfo.getTargetMethod())) {
                return invokeInfo.getTargetMethod();
            }
        }
        throw AnalysisError.interruptAnalysis(invoke + " not found in: " + root);
    }
}
