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
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryFactory;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryManager;
import com.oracle.svm.hosted.analysis.ai.util.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtils;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;

import java.util.ArrayList;
import java.util.Arrays;
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

    public CallStack getCallStack() {
        return callStack;
    }

    public SummaryFactory<Domain> getSummaryFactory() {
        return summaryManager.summaryFactory();
    }

    public SummaryCache<Domain> getSummaryCache() {
        return summaryManager.summaryCache();
    }

    @Override
    public AnalysisOutcome<Domain> handleCall(Invoke invoke,
                                              Node invokeNode,
                                              AbstractStateMap<Domain> callerStateMap) {

        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();
        logger.logToFile("InterProceduralCallHandler::execInvoke invokeNode: " + invoke + " with arguments: " + invoke.callTarget().arguments());
        logger.logToFile("InterProceduralCallHandler::execInvoke params: " + Arrays.toString(invoke.getTargetMethod().getParameters()));

        String calleeName = invoke.callTarget().targetName();
        AnalysisMethod analysisMethod;
        DebugContext debug = invokeNode.getDebug();

        try {
            analysisMethod = GraphUtils.getInvokeAnalysisMethod(callStack.getCurrentAnalysisMethod(), invoke);
        } catch (Exception e) {
            /* For some reason we are not able to get the AnalysisMethod of the Invoke */
            logger.logToFile("Could not get the target analysis analysisMethod for invoke: " + invoke);
            return AnalysisOutcome.error(AnalysisResult.UNKNOWN_METHOD);
        }

        if (methodFilterManager.shouldSkipMethod(analysisMethod)) {
            return AnalysisOutcome.error(AnalysisResult.IN_SKIP_LIST);
        }

        // TODO: think if passing nodeInterpreter to summaryFactory would be better
        List<Domain> actualArgs = convertActualArgs(invoke, callerStateMap);
        Summary<Domain> summary = summaryManager.createSummary(invoke, callerStateMap.getPreCondition(invokeNode), actualArgs);
        logger.logHighlightedDebugInfo("Analyzing AnalysisMethod: " + analysisMethod.getQualifiedName());

        /* If the summaryCache contains the summary for the target analysisMethod, we return it */
        if (summaryManager.containsSummaryForResolvedJavaName(calleeName, summary)) {
            logger.logToFile("Summary cache contains targetMethod: " + calleeName);
            Summary<Domain> completeSummary = summaryManager.getSummary(calleeName, summary);
            logger.logToFile("The summary is: " + completeSummary);
            return AnalysisOutcome.ok(completeSummary);
        }

        /* At this point we know that we don't have a complete summary for this analysisMethod, therefore we must compute it.
         * However, we need to check if we have surpassed our recursion limit */
        if (callStack.countRecursiveCalls(analysisMethod) > callStack.getMaxRecursionDepth()) {
            logger.logToFile("Recursion limit reached for analysisMethod: " + calleeName + " clearing the call stack");
            callStack.pop();
            return AnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
        }

        /* Push the analysisMethod to the call stack */
        callStack.push(analysisMethod);
        logger.logToFile("Call stack: " + callStack);

        /* Create new fixpoint iterator for the target analysisMethod and run the fixpoint iteration */
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(analysisMethod, debug, summary.getPreCondition(), transferFunction, iteratorPayload);
        AbstractStateMap<Domain> invokeAbstractStateMap = fixpointIterator.iterateUntilFixpoint();
        AbstractState<Domain> returnAbstractState = invokeAbstractStateMap.getReturnState();

//        logger.logToFile("Analyzing AnalysisMethod [" + calleeName + "] finished with abstract context: " + System.lineSeparator() + returnAbstractState.toString());

        /* Update the summary in the cache and apply it */
        summary.finalizeSummary(returnAbstractState.getPostCondition());
        summaryManager.putSummary(calleeName, summary);
        callStack.pop();

        String qualifiedName = analysisMethod.getQualifiedName();
        GraphUtils.printInferredGraph(iteratorPayload.getMethodGraph().get(analysisMethod).graph, analysisMethod, invokeAbstractStateMap);

        checkerManager.checkAll(calleeName, callerStateMap);
        invoke.getTargetMethod().getName();
        // TODO: this checking is kinda awkward at the end
        logger.logToFile("Checker results for analysisMethod " + qualifiedName + ": " + System.lineSeparator());
        return new AnalysisOutcome<>(AnalysisResult.OK, summary);
    }

    @Override
    public void handleRootCall(AnalysisMethod root, DebugContext debug) {
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, debug, initialDomain, transferFunction, iteratorPayload);

        callStack.push(root);
        AbstractStateMap<Domain> abstractStateMap = fixpointIterator.iterateUntilFixpoint();
        callStack.pop();

        checkerManager.checkAll(root.getName(), abstractStateMap);
        GraphUtils.printInferredGraph(iteratorPayload.getMethodGraph().get(root).graph, root, abstractStateMap);
    }

    private List<Domain> convertActualArgs(Invoke invoke, AbstractStateMap<Domain> callerStateMap) {
        List<Domain> result = new ArrayList<>();
        for (Node argument : invoke.callTarget().arguments()) {
            result.add(transferFunction.analyzeNode(argument, callerStateMap));
        }

        return result;
    }
}
