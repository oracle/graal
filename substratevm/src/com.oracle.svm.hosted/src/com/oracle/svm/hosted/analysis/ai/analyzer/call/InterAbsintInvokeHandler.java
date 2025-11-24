package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
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
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.Optional;

/**
 * Inter-procedural invoke handler using per-call context and summary caching.
 */
public final class InterAbsintInvokeHandler<Domain extends AbstractDomain<Domain>> extends AbsintInvokeHandler<Domain> {

    private final CallStack methodStack;
    private final SummaryManager<Domain> summaryManager;

    public InterAbsintInvokeHandler(
            Domain initialDomain,
            AbstractInterpreter<Domain> abstractInterpreter,
            AnalysisContext analysisContext,
            SummaryFactory<Domain> summaryFactory,
            int maxRecursionDepth) {
        super(initialDomain, abstractInterpreter, analysisContext);
        this.methodStack = new CallStack(maxRecursionDepth);
        this.summaryManager = new SummaryManager<>(summaryFactory);
    }

    @Override
    public AnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        Invoke invoke = invokeInput.invoke();

        AnalysisMethod current = invokeInput.callerMethod() != null
                ? invokeInput.callerMethod()
                : methodStack.getCurrentAnalysisMethod();

        AnalysisMethod targetAnalysisMethod;
        try {
            targetAnalysisMethod = getInvokeTargetAnalysisMethod(current, invoke);
            assert targetAnalysisMethod != null;
        } catch (Exception e) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        if (methodFilterManager.shouldSkipMethod(targetAnalysisMethod)) {
            AbstractState<Domain> callerState = invokeInput.callerState();
            Domain callerPreAtInvoke = callerState.getPreCondition(invoke.asNode());
            Summary<Domain> skipped = summaryManager.createSummary(invoke, callerPreAtInvoke, invokeInput.actualArgDomains());
            return new AnalysisOutcome<>(AnalysisResult.IN_SKIP_LIST, skipped);
        }

        AbstractState<Domain> callerState = invokeInput.callerState();
        Domain callerPreAtInvoke = callerState.getPreCondition(invoke.asNode());
        /* Build a pre-condition summary for this call. */
        Summary<Domain> preSummary = summaryManager.createSummary(invoke, callerPreAtInvoke, invokeInput.actualArgDomains());

        /* Try to reuse an existing summary that subsumes this one. */
        Summary<Domain> cached = summaryManager.getSummary(targetAnalysisMethod, preSummary);
        if (cached != null) {
            logger.log("Summary cache contains targetMethod: " + targetAnalysisMethod, LoggerVerbosity.SUMMARY);
            return AnalysisOutcome.ok(cached);
        }

        if (methodStack.countConsecutiveCalls(targetAnalysisMethod) >= methodStack.getMaxRecursionDepth()) {
            logger.log("Recursion limit of: " + methodStack.getMaxRecursionDepth() + " exceeded", LoggerVerbosity.INFO);
            return AnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
        }

        if (methodStack.hasMethodCallCycle(targetAnalysisMethod)) {
            logger.log("Analysis has encountered a mutual recursion cycle on: " + targetAnalysisMethod.getQualifiedName() + ", skipping analysis", LoggerVerbosity.INFO);
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.MUTUAL_RECURSION_CYCLE);
            logger.log(methodStack.formatCycleWithMethod(targetAnalysisMethod), LoggerVerbosity.INFO);
            return outcome;
        }

        /* Analyze callee under this context. */
        methodStack.push(targetAnalysisMethod);
        try {
            FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(targetAnalysisMethod, initialDomain, abstractTransformer, analysisContext);
            String ctxSig = invokeInput.contextSignature().orElseGet(
                    () -> CallContextHolder.buildKCFASignature(methodStack.getCallStack(), 2));
            fixpointIterator.getIteratorContext().setCallContextSignature(ctxSig);

            fixpointIterator.getAbstractState().setStartNodeState(preSummary.getPreCondition());
            AbstractState<Domain> calleeAbstractState = fixpointIterator.iterateUntilFixpoint();
            preSummary.finalizeSummary(calleeAbstractState);

            ContextKey ctxKey = invokeInput.contextKey()
                    .orElse(new ContextKey(targetAnalysisMethod, ctxSig.hashCode(), methodStack.getDepth()));

            /* Merge the different contexts for the callee method */
            summaryManager.putSummary(targetAnalysisMethod, ctxKey, preSummary);
            summaryManager.getSummaryRepository().get(targetAnalysisMethod).joinWithContextState(calleeAbstractState);
        } finally {
            methodStack.pop();
        }

        return AnalysisOutcome.ok(preSummary);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        if (methodFilterManager.shouldSkipMethod(root)) {
            return;
        }

        String ctxSig = CallContextHolder.buildKCFASignature(methodStack.getCallStack(), 2);
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, abstractTransformer, analysisContext);
        fixpointIterator.getIteratorContext().setCallContextSignature(ctxSig);
        methodStack.push(root);
        try {
            AbstractState<Domain> abstractState = fixpointIterator.iterateUntilFixpoint();
            // We have to run checkers on the main method manually, since we don't have a summary entry for the root method unfortunately
            checkerManager.runCheckersOnSingleMethod(root, abstractState, analysisContext.getMethodGraphCache().getMethodGraphMap().get(root));
            checkerManager.runCheckersOnMethodSummaries(summaryManager.getSummaryRepository().getMethodSummaryMap(), analysisContext.getMethodGraphCache().getMethodGraphMap());
            var logger = AbstractInterpretationLogger.getInstance();
            // TODO: print abstract interpretation checkers
        } finally {
            methodStack.pop();
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
        return a.getName().equals(b.getName()) &&
                a.getSignature().toMethodDescriptor().equals(b.getSignature().toMethodDescriptor()) &&
                a.getDeclaringClass().toJavaName().equals(b.getDeclaringClass().toJavaName());
    }
}
