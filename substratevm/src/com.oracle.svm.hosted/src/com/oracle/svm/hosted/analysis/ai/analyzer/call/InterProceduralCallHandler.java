package com.oracle.svm.hosted.analysis.ai.analyzer.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisOutcome;
import com.oracle.svm.hosted.analysis.ai.analyzer.AnalysisResult;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.CallStack;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.IteratorPayload;
import com.oracle.svm.hosted.analysis.ai.analyzer.payload.filter.AnalysisMethodFilterManager;
import com.oracle.svm.hosted.analysis.ai.checker.CheckerManager;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIteratorFactory;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * The InterProceduralCallHandler class is responsible for handling analysisMethod invocations in the context
 * of inter-procedural static analysis. This handler manages recursive analysisMethod calls, retrieves or computes analysisMethod
 * summaries, and updates the abstract state of the analysis accordingly. It effectively integrates the functionality
 * of processing calls while adhering to constraints such as recursion depth and existing summaries.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public final class InterProceduralCallHandler<Domain extends AbstractDomain<Domain>> extends BaseCallHandler<Domain> {

    private final CallStack callStack;
    private final SummaryManager<Domain> summaryManager;

    public InterProceduralCallHandler(
            Domain initialDomain,
            NodeInterpreter<Domain> nodeInterpreter,
            CheckerManager checkerManager,
            AnalysisMethodFilterManager methodFilterManager,
            IteratorPayload iteratorPayload,
            SummaryFactory<Domain> summaryFactory,
            int maxRecursionDepth) {
        super(initialDomain, nodeInterpreter, checkerManager, methodFilterManager, iteratorPayload);
        this.callStack = new CallStack(maxRecursionDepth);
        this.summaryManager = new SummaryManager<>(summaryFactory);
    }

    @Override
    public AnalysisOutcome<Domain> handleCall(Invoke invoke,
                                              Node invokeNode,
                                              AbstractStateMap<Domain> callerStateMap) {
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        ResolvedJavaMethod calleeMethod = invoke.getTargetMethod();
        DebugContext debug = invokeNode.getDebug();
        AnalysisMethod analysisMethod;

        try {
            analysisMethod = GraphUtil.getInvokeAnalysisMethod(callStack.getCurrentAnalysisMethod(), invoke);
        } catch (Exception e) {
            /* For some reason we are not able to get the AnalysisMethod of the Invoke */
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        logger.log("Handling call to: " + calleeMethod.getName(), LoggerVerbosity.INFO);
        if (methodFilterManager.shouldSkipMethod(analysisMethod)) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.IN_SKIP_LIST);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        List<Domain> actualArgs = convertActualArgs(invoke, callerStateMap);
        Summary<Domain> summary = summaryManager.createSummary(invoke, callerStateMap.getPreCondition(invokeNode), actualArgs);

        /* If the summaryCache contains the summary for the target analysisMethod, we return it */
        if (summaryManager.containsSummary(calleeMethod, summary)) {
            logger.log("Summary cache contains targetMethod: " + calleeMethod.getName(), LoggerVerbosity.SUMMARY);
            Summary<Domain> completeSummary = summaryManager.getSummary(calleeMethod, summary);
            return AnalysisOutcome.ok(completeSummary);
        }

        /* At this point we know that we don't have a complete summary for this analysisMethod, therefore we must compute it.
         * However, we need to check if we have reached our recursion limit */
        if (callStack.countRecursiveCalls(analysisMethod) >= callStack.getMaxRecursionDepth()) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            return outcome;
        }

        /* Or if we have a mutual recursion cycle */
        if (callStack.hasMethodCallCycle(analysisMethod)) {
            AnalysisOutcome<Domain> outcome = AnalysisOutcome.error(AnalysisResult.MUTUAL_RECURSION_CYCLE);
            logger.log(outcome.toString(), LoggerVerbosity.INFO);
            logger.log(callStack.formatCycleWithMethod(analysisMethod), LoggerVerbosity.INFO);
            return outcome;
        }

        /* Run the fixpoint iteration on the invoked method */
        callStack.push(analysisMethod);
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(analysisMethod, debug, summary.getPreCondition(), transferFunction, iteratorPayload);
        logger.log("Running fixpoint iteration on: " + analysisMethod.getQualifiedName(), LoggerVerbosity.INFO);
        logger.log("The current call stack: " + callStack, LoggerVerbosity.INFO);
        AbstractStateMap<Domain> invokeAbstractStateMap = fixpointIterator.iterateUntilFixpoint();
        Domain returnDomain = invokeAbstractStateMap.getReturnDomain();
        summary.finalizeSummary(returnDomain);
        summaryManager.putSummary(calleeMethod, summary);
        callStack.pop();

        /* Here we are finished with the fixpoint iteration and updated the summary cache */
        logger.log("Computed summary for " + calleeMethod.getName(), LoggerVerbosity.SUMMARY);
        logger.log("The summary pre-condition", LoggerVerbosity.SUMMARY);
        logger.log(summary.getPreCondition().toString(), LoggerVerbosity.SUMMARY);
        logger.log("The summary post-condition", LoggerVerbosity.SUMMARY);
        logger.log(summary.getPostCondition().toString(), LoggerVerbosity.SUMMARY);
        checkerManager.runCheckers(calleeMethod, callerStateMap);
        return new AnalysisOutcome<>(AnalysisResult.OK, summary);
    }

    @Override
    public void handleRootCall(AnalysisMethod root, DebugContext debug) {
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, debug, initialDomain, transferFunction, iteratorPayload);

        callStack.push(root);
        AbstractStateMap<Domain> abstractStateMap = fixpointIterator.iterateUntilFixpoint();
        callStack.pop();

        checkerManager.runCheckers(root.wrapped, abstractStateMap);
        GraphUtil.printInferredGraph(iteratorPayload.getMethodGraph().get(root).graph, root, abstractStateMap);
    }

    private List<Domain> convertActualArgs(Invoke invoke, AbstractStateMap<Domain> callerStateMap) {
        List<Domain> result = new ArrayList<>();
        for (Node argument : invoke.callTarget().arguments()) {
            result.add(transferFunction.analyzeNode(argument, callerStateMap));
        }
        return result;
    }
}
