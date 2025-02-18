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
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.Arrays;

/**
 * The InterProceduralCallHandler class is responsible for handling analysisMethod invocations in the context
 * of inter-procedural static analysis. This handler manages recursive analysisMethod calls, retrieves or computes analysisMethod
 * summaries, and updates the abstract state of the analysis accordingly. It effectively integrates the functionality
 * of processing calls while adhering to constraints such as recursion depth and existing summaries.
 *
 * @param <Domain> type of the derived {@link AbstractDomain} used in the analysis
 */
public final class InterProceduralCallHandler<Domain extends AbstractDomain<Domain>>
        extends BaseCallHandler<Domain> {

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
        logger.logToFile("InterProceduralCallHandler::execInvoke invokeNode: " + invoke + "with arguments: " + invoke.callTarget().arguments());
        logger.logToFile("InterProceduralCallHandler::execInvoke params: " + Arrays.toString(invoke.getTargetMethod().getParameters()));

        ResolvedJavaMethod resolvedJavaMethod = invoke.getTargetMethod();
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

        
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        System.out.println(args);
        Summary<Domain> summary = summaryManager.createSummary(invoke, callerStateMap.getPreCondition(invokeNode));
        logger.logHighlightedDebugInfo("Analyzing AnalysisMethod: " + analysisMethod.getQualifiedName());

        /* If the summaryCache contains the summary for the target analysisMethod, we can use it */
        if (summaryManager.containsSummary(resolvedJavaMethod.getName(), summary)) {
            logger.logToFile("Summary cache contains targetMethod: " + resolvedJavaMethod.getName());
            Summary<Domain> completeSummary = summaryManager.getSummary(resolvedJavaMethod.getName(), summary);
            logger.logToFile("The summary is: " + completeSummary);
            completeSummary.applySummary(invoke, invokeNode, callerStateMap);
            return AnalysisOutcome.ok(completeSummary);
        }

        /* At this point we know that we don't have a complete summary for this analysisMethod, therefore we must compute it.
         * However, we need to check if we have surpassed our recursion limit */
        if (callStack.countRecursiveCalls(analysisMethod) > callStack.getMaxRecursionDepth()) {
            logger.logToFile("Recursion limit reached for analysisMethod: " + resolvedJavaMethod.getName() + " clearing the call stack");
            callStack.pop();
            return AnalysisOutcome.error(AnalysisResult.RECURSION_LIMIT_OVERFLOW);
        }

        /* Push the analysisMethod to the call stack */
        callStack.push(analysisMethod);
        logger.logToFile("Call stack: " + callStack);

        /* Create new fixpoint iterator for the target analysisMethod and run the fixpoint iteration */
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(analysisMethod, debug, initialDomain, transferFunction, iteratorPayload);
        AbstractStateMap<Domain> invokeAbstractStateMap = fixpointIterator.iterateUntilFixpoint();
        AbstractState<Domain> returnAbstractState = invokeAbstractStateMap.getReturnState();
        logger.logToFile("Analyzing AnalysisMethod [" + resolvedJavaMethod.getName() + "] finished with abstract context: " + returnAbstractState.toString());

        /* Update the summary in the cache and apply it */
        summary.finalizeSummary(returnAbstractState.getPostCondition());
        summaryManager.putSummary(resolvedJavaMethod.getName(), summary);

        logger.logToFile("The complete summary is: " + summary);
        callStack.pop();

        String qualifiedName = analysisMethod.getQualifiedName();
        logger.logToFile("Abstract state map after fixpoint iteration for analysisMethod " + qualifiedName + ":\n" + callerStateMap);
        logger.logDebugInfo("Running the provided checkers : " + checkerManager);
        checkerManager.checkAll(callerStateMap);
        logger.logToFile("Checker results for analysisMethod " + qualifiedName + ":\n");
        return new AnalysisOutcome<>(AnalysisResult.OK, summary);
    }

    @Override
    public void handleRootCall(AnalysisMethod root, DebugContext debug) {
        FixpointIterator<Domain> fixpointIterator = FixpointIteratorFactory.createIterator(root, debug, initialDomain, transferFunction, iteratorPayload);
        AbstractInterpretationLogger logger = AbstractInterpretationLogger.getInstance();

        callStack.push(root);
        AbstractStateMap<Domain> abstractStateMap = fixpointIterator.iterateUntilFixpoint();
        callStack.pop();

        String qualifiedName = root.getQualifiedName();
        logger.logToFile("Abstract state map after fixpoint iteration for analysisMethod " + qualifiedName + ":\n" + abstractStateMap);
        logger.logDebugInfo("Running the provided checkers : " + checkerManager);
        checkerManager.checkAll(abstractStateMap);
        logger.logToFile("Checker results for analysisMethod " + qualifiedName + ":\n");
    }
}
