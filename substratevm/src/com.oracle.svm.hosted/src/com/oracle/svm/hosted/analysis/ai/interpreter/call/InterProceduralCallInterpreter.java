package com.oracle.svm.hosted.analysis.ai.interpreter.call;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.InterProceduralAnalysisContext;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.FixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.SequentialWtoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractStateMap;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import com.oracle.svm.hosted.analysis.ai.summary.SummaryCache;
import com.oracle.svm.hosted.analysis.ai.util.GraphUtils;
import com.oracle.svm.hosted.analysis.ai.util.AnalysisMethodUtils;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class InterProceduralCallInterpreter<
        Domain extends AbstractDomain<Domain>>
        implements CallInterpreter<Domain> {

    private final InterProceduralAnalysisContext<Domain> analysisContext;

    public InterProceduralCallInterpreter(InterProceduralAnalysisContext<Domain> analysisContext) {
        this.analysisContext = analysisContext;
    }

    @Override
    public void execInvoke(Invoke invoke, Node invokeNode, AbstractStateMap<Domain> abstractStateMap) {
        analysisContext.getLogger().logToFile("InterProceduralCallInterpreter::execInvoke invokeNode: " + invoke);
        ResolvedJavaMethod targetMethod = invoke.getTargetMethod();
        SummaryCache<Domain> summaryCache = analysisContext.getSummaryCache();
        Summary<Domain> invokeSummary = analysisContext.getSummarySupplier().get(invoke, abstractStateMap.getState(invokeNode));
        AnalysisMethod targetAnalysisMethod = GraphUtils.getInvokeAnalysisMethod(analysisContext.getRoot(), invoke);
        analysisContext.getLogger().logHighlightedDebugInfo("Analyzing AnalysisMethod: " + targetAnalysisMethod.getQualifiedName());

        if (analysisContext.getMethodFilter().shouldSkip(targetAnalysisMethod)) {
            return;
        }

        if (summaryCache.contains(targetMethod.getName(), invokeSummary)) {
            analysisContext.getLogger().logToFile("Summary cache contains targetMethod: " + targetMethod.getName());
            Domain domain = summaryCache.getPostCondition(targetMethod.getName(), invokeSummary);
            analysisContext.getLogger().logToFile("The summary is: " + domain);
            abstractStateMap.setPostCondition(invokeNode, domain);
            return;
        }

        /* Create new fixpoint iterator for the target method */
        TransferFunction<Domain> transferFunction = new TransferFunction<>(analysisContext.getNodeInterpreter(), new InterProceduralCallInterpreter<>(analysisContext), analysisContext.getLogger());
        FixpointIterator<Domain> fixpointIterator;
        analysisContext.getCallStack().push(targetAnalysisMethod);
        analysisContext.getLogger().logToFile("Call stack: " + analysisContext.getCallStack());

        if (analysisContext.getIteratorPolicy().isConcurrent()) {
            fixpointIterator = new ConcurrentWpoFixpointIterator<>(analysisContext, transferFunction);
        } else {
            fixpointIterator = new SequentialWtoFixpointIterator<>(analysisContext, transferFunction);
        }

        AbstractStateMap<Domain> invokeAbstractStateMap = fixpointIterator.iterateUntilFixpoint();
        AbstractState<Domain> returnAbstractState = invokeAbstractStateMap.getReturnState();
        analysisContext.getLogger().logToFile("Analyzing AnalysisMethod finished with abstract context: " + returnAbstractState.toString());
        Summary<Domain> completeSummary = analysisContext.getSummarySupplier().createCompleteSummary(invokeSummary, returnAbstractState);
        summaryCache.put(targetMethod.getName(), completeSummary);
        analysisContext.getCallStack().pop();
    }
}