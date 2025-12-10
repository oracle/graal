package com.oracle.svm.hosted.analysis.ai.analysis.invokehandle;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.svm.hosted.analysis.ai.analysis.InvokeAnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analysis.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analysis.context.CallContextHolder;
import com.oracle.svm.hosted.analysis.ai.analysis.context.CallStack;
import com.oracle.svm.hosted.analysis.ai.analysis.context.AnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.*;
import com.oracle.svm.hosted.analysis.ai.analysis.AbstractInterpretationServices;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Inter-procedural invoke handler which uses per-call method context and summary caching.
 */
public final class InterAbsintInvokeHandler<Domain extends AbstractDomain<Domain>> extends AbsintInvokeHandler<Domain> {

    private final CallStack callStack;
    private final SummaryManager<Domain> summaryManager;

    public InterAbsintInvokeHandler(
            Domain initialDomain,
            AbstractInterpreter<Domain> abstractInterpreter,
            AnalysisContext analysisContext,
            CallStack callStack,
            SummaryManager<Domain> summaryManager) {
        super(initialDomain, abstractInterpreter, analysisContext);
        this.callStack = callStack;
        this.summaryManager = summaryManager;
    }

    @Override
    public InvokeAnalysisOutcome<Domain> handleInvoke(InvokeInput<Domain> invokeInput) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        Invoke invoke = invokeInput.invoke();

        AnalysisMethod current = invokeInput.callerMethod() != null
                ? invokeInput.callerMethod()
                : callStack.getCurrentAnalysisMethod();

        AnalysisMethod targetAnalysisMethod;
        try {
            targetAnalysisMethod = getInvokeTargetAnalysisMethod(current, invoke);
            assert targetAnalysisMethod != null;
            StructuredGraph graph = AbstractInterpretationServices.getInstance().getGraph(targetAnalysisMethod);
            assert graph != null;
        } catch (Exception e) {
            InvokeAnalysisOutcome<Domain> outcome = InvokeAnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        if (methodFilterManager.shouldSkipMethod(targetAnalysisMethod)) {
            AbstractState<Domain> callerState = invokeInput.callerState();
            Domain callerPreAtInvoke = callerState.getPreCondition(invoke.asNode());
            Summary<Domain> skipped = summaryManager.createSummary(invoke, callerPreAtInvoke, invokeInput.actualArgDomains());
            return new InvokeAnalysisOutcome<>(AnalysisResult.IN_SKIP_LIST, skipped);
        }

        AbstractState<Domain> callerState = invokeInput.callerState();
        Domain callerPreAtInvoke = callerState.getPreCondition(invoke.asNode());
        /* Build a pre-condition summary for this call. */
        Summary<Domain> preSummary = summaryManager.createSummary(invoke, callerPreAtInvoke, invokeInput.actualArgDomains());

        /* Try to reuse an existing summary that subsumes this one. */
        Summary<Domain> cached = summaryManager.getSummary(targetAnalysisMethod, preSummary);
        if (cached != null) {
            logger.log("Summary cache contains targetMethod: " + targetAnalysisMethod, LoggerVerbosity.SUMMARY);
            return InvokeAnalysisOutcome.ok(cached);
        }

        if (callStack.getDepth() >= callStack.getMaxCallStackDepth()) {
            logger.log("Recursion limit of: " + callStack.getMaxCallStackDepth() + " exceeded", LoggerVerbosity.INFO);
            return InvokeAnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
        }

        if (callStack.hasMethodCallCycle(targetAnalysisMethod)) {
            logger.log("Analysis has encountered a mutual recursion cycle on: " + targetAnalysisMethod.getQualifiedName() + ", skipping analysis", LoggerVerbosity.INFO);
            InvokeAnalysisOutcome<Domain> outcome = InvokeAnalysisOutcome.error(AnalysisResult.MUTUAL_RECURSION_CYCLE);
            logger.log(callStack.formatCycleWithMethod(targetAnalysisMethod), LoggerVerbosity.INFO);
            return outcome;
        }

        /* Analyze callee under this context. */
        callStack.push(targetAnalysisMethod);
        try {
            FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(targetAnalysisMethod, initialDomain, abstractTransformer, analysisContext);
            String ctxSig = invokeInput.contextSignature().orElseGet(
                    () -> CallContextHolder.buildKCFASignature(callStack.getCallStack(), 2));
            fixpointIterator.getIteratorContext().setCallContextSignature(ctxSig);

            AbstractState<Domain> calleeAbstractState = fixpointIterator.runFixpointIteration(preSummary.getPreCondition());
            preSummary.finalizeSummary(calleeAbstractState);

            ContextKey ctxKey = invokeInput.contextKey()
                    .orElse(new ContextKey(targetAnalysisMethod, ctxSig.hashCode(), callStack.getDepth()));

            /* Merge the different contexts for the callee method */
            summaryManager.putSummary(targetAnalysisMethod, ctxKey, preSummary);
            summaryManager.getSummaryRepository().get(targetAnalysisMethod).joinWithContextState(calleeAbstractState);
        } finally {
            callStack.pop();
        }

        return InvokeAnalysisOutcome.ok(preSummary);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        if (methodFilterManager.shouldSkipMethod(root)) {
            return;
        }

        String ctxSig = CallContextHolder.buildKCFASignature(callStack.getCallStack(), 2);
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, abstractTransformer, analysisContext);
        fixpointIterator.getIteratorContext().setCallContextSignature(ctxSig);
        callStack.push(root);
        try {
            AbstractState<Domain> abstractState = fixpointIterator.runFixpointIteration();
            // TODO: we should even create a summary for this root method, then we can run checkers only in the engine
            checkerManager.runCheckersOnSingleMethod(root, abstractState, analysisContext.getMethodGraphCache().getMethodGraphMap().get(root));
        } finally {
            callStack.pop();
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
