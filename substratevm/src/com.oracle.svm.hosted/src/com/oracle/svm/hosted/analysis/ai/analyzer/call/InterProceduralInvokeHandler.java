package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.InvokeInfo;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.CallStack;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.interpreter.AbstractInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * The InterProceduralInvokeHandler class is responsible for handling analysisMethod invocations in the context
 * of inter-procedural static analysis. This handler manages recursive analysisMethod calls, retrieves or computes analysisMethod
 * summaries, and updates the abstract state of the analysis accordingly. It effectively integrates the functionality
 * of processing calls while adhering to constraints such as recursion depth and existing summaries.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public final class InterProceduralInvokeHandler<Domain extends AbstractDomain<Domain>> extends BaseInvokeHandler<Domain> {

    private final CallStack callStack;
    private final SummaryManager<Domain> summaryManager;

    public InterProceduralInvokeHandler(
            Domain initialDomain,
            AbstractInterpreter<Domain> abstractInterpreter,
            CheckerManager checkerManager,
            AnalysisMethodFilterManager methodFilterManager,
            IteratorPayload iteratorPayload,
            SummaryFactory<Domain> summaryFactory,
            int maxRecursionDepth) {
        super(initialDomain, abstractInterpreter, checkerManager, methodFilterManager, iteratorPayload);
        this.callStack = new CallStack(maxRecursionDepth);
        this.summaryManager = new SummaryManager<>(summaryFactory);
    }

    @Override
    public AnalysisOutcome<Domain> handleInvoke(Invoke invoke,
                                                Node invokeNode,
                                                AbstractState<Domain> callerState) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        ResolvedJavaMethod calleeMethod = invoke.getTargetMethod();
        AnalysisMethod targetAnalysisMethod;

        try {
            targetAnalysisMethod = getInvokeTargetAnalysisMethod(callStack.getCurrentAnalysisMethod(), invoke);
        } catch (Exception e) {
            /* For some reason we are not able to get the AnalysisMethod of the Invoke */
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        if (methodFilterManager.shouldSkipMethod(targetAnalysisMethod)) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.IN_SKIP_LIST);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        List<Domain> actualArgs = convertActualArgs(invoke, callerState);
        Summary<Domain> summary = summaryManager.createSummary(invoke, callerState.getPreCondition(invokeNode), actualArgs);

        /* If the summaryCache contains the summary for the target analysisMethod, we return it */
        if (summaryManager.containsSummary(calleeMethod, summary)) {
            logger.log("Summary cache contains targetMethod: " + calleeMethod.getName(), LoggerVerbosity.SUMMARY);
            Summary<Domain> completeSummary = summaryManager.getSummary(calleeMethod, summary);
            return AnalysisOutcome.ok(completeSummary);
        }

        /* At this point we know that we don't have a complete summary for this analysisMethod, therefore we must compute it.
         * However, we need to check if we have reached our recursion limit */
        if (callStack.countRecursiveCalls(targetAnalysisMethod) >= callStack.getMaxRecursionDepth()) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        /* Or if we have a mutual recursion cycle */
        if (callStack.hasMethodCallCycle(targetAnalysisMethod)) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.MUTUAL_RECURSION_CYCLE);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            logger.log(callStack.formatCycleWithMethod(targetAnalysisMethod), LoggerVerbosity.INFO);
            return outcome;
        }

        /* Set-up and run the analysis on the invoked method */
        callStack.push(targetAnalysisMethod);
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(targetAnalysisMethod, initialDomain, abstractTransformers, iteratorPayload);
        fixpointIterator.getAbstractState().setStartNodeState(summary.getPreCondition());
        logger.log("The current call stack: " + callStack, LoggerVerbosity.INFO);
        AbstractState<Domain> invokeAbstractState = fixpointIterator.iterateUntilFixpoint();
        Domain returnDomain = invokeAbstractState.getReturnDomain();
        summary.finalizeSummary(returnDomain);
        summaryManager.putSummary(calleeMethod, summary);
        callStack.pop();

        /* At this point, we are finished with the fixpoint iteration and updated the summary cache */
        checkerManager.runCheckers(targetAnalysisMethod, callerState);
        return new AnalysisOutcome<>(AnalysisResult.OK, summary);
    }

    @Override
    public void handleRootInvoke(AnalysisMethod root) {
        if (methodFilterManager.shouldSkipMethod(root)) {
            return;
        }

        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, initialDomain, abstractTransformers, iteratorPayload);

        callStack.push(root);
        AbstractState<Domain> abstractState = fixpointIterator.iterateUntilFixpoint();
        callStack.pop();

        checkerManager.runCheckers(root, abstractState);
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logSummariesStats(summaryManager);
    }

    private List<Domain> convertActualArgs(Invoke invoke, AbstractState<Domain> callerState) {
        List<Domain> result = new ArrayList<>();
        for (Node argument : invoke.callTarget().arguments()) {
            result.add(abstractTransformers.analyzeNode(argument, callerState));
        }
        return result;
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
