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
import com.oracle.svm.hosted.analysis.ai.domain.memory.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.*;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.InvokeNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Inter-procedural invoke handler using per-call context and summary caching.
 */
public final class InterAbsintInvokeHandler<Domain extends AbstractDomain<Domain>> extends AbsintInvokeHandler<Domain> {

    private final CallStack methodStack;
    private final SummaryManager<Domain> summaryManager;
    private final SummaryRepository<Domain> summaryRepository;
    private final Map<AnalysisMethod, Map<String, Summary<Domain>>> memo = new HashMap<>();

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
        AnalysisMethod current = invokeInput.callerMethod() != null ? invokeInput.callerMethod() : methodStack.getCurrentAnalysisMethod();

        AnalysisMethod targetAnalysisMethod;
        try {
            targetAnalysisMethod = getInvokeTargetAnalysisMethod(current, invoke);
        } catch (Exception e) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }
        logger.log("Handling invoke of method:  " + targetAnalysisMethod.getName(), LoggerVerbosity.DEBUG);

        AbstractState<Domain> callerState = invokeInput.callerState();
        var argDomains = invokeInput.actualArgDomains();
        Domain callerPreAtInvoke = callerState.getPreCondition(invoke.asNode());


        Summary<Domain> early = summaryManager.summaryFactory().tryCreateEarlySummary(invoke, callerPreAtInvoke, argDomains);
        if (early != null && early.isComplete()) {
            logger.log("Early summary applied for invoke: " + invoke, LoggerVerbosity.SUMMARY);
            summaryManager.putSummary(targetAnalysisMethod, early);
            return AnalysisOutcome.ok(early);
        }

        Summary<Domain> summary = summaryManager.createSummary(invoke, callerPreAtInvoke, argDomains);

        if (summaryManager.containsSummary(targetAnalysisMethod, summary)) {
            logger.log("Summary cache contains targetMethod: " + targetAnalysisMethod, LoggerVerbosity.SUMMARY);
            Summary<Domain> completeSummary = summaryManager.getSummary(targetAnalysisMethod, summary);
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
            fixpointIterator.getIteratorContext().setCallContextSignature("depth" + methodStack.getDepth());
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
        ResolvedJavaMethod invokeTarget = invoke.getTargetMethod();
        for (InvokeInfo invokeInfo : root.getInvokes()) {
            ResolvedJavaMethod candidate = invokeInfo.getTargetMethod().wrapped;
            if (sameMethod(invokeTarget, candidate)) {
                return invokeInfo.getTargetMethod();
            }
        }
        return null;
    }

    private boolean sameMethod(ResolvedJavaMethod a, ResolvedJavaMethod b) {
        var logger = AbstractInterpretationLogger.getInstance();
        return a.getName().equals(b.getName()) &&
                a.getSignature().toMethodDescriptor().equals(b.getSignature().toMethodDescriptor()) &&
                a.getDeclaringClass().toJavaName().equals(b.getDeclaringClass().toJavaName());
    }
}
