package com.oracle.svm.hosted.analysis.ai.analyzer.inter;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.analyzer.Analyzer;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.InterProceduralAnalysisContext;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.filter.DefaultMethodFilter;
import com.oracle.svm.hosted.analysis.ai.analyzer.context.filter.MethodFilter;
import com.oracle.svm.hosted.analysis.ai.domain.AbstractDomain;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.ConcurrentWpoFixpointIterator;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.interpreter.TransferFunction;
import com.oracle.svm.hosted.analysis.ai.interpreter.call.InterProceduralCallInterpreter;
import com.oracle.svm.hosted.analysis.ai.interpreter.node.NodeInterpreter;
import com.oracle.svm.hosted.analysis.ai.summary.SummarySupplier;
import jdk.graal.compiler.debug.DebugContext;

/**
 * Represents an inter-procedural concurrent analyzer
 * @param <Domain> the type of derived {@link AbstractDomain}
 */
public class InterProceduralConcurrentAnalyzer<Domain extends AbstractDomain<Domain>> extends Analyzer<Domain> {

    private final SummarySupplier<Domain> summarySupplier;
    private final MethodFilter methodFilter;

    public InterProceduralConcurrentAnalyzer(AnalysisMethod root,
                                             DebugContext debug,
                                             SummarySupplier<Domain> summarySupplier) {
        this(root, debug, summarySupplier, IteratorPolicy.DEFAULT_SEQUENTIAL, new DefaultMethodFilter());
    }

    public InterProceduralConcurrentAnalyzer(AnalysisMethod root,
                                             DebugContext debug,
                                             SummarySupplier<Domain> summarySupplier,
                                             IteratorPolicy iteratorPolicy) {
        this(root, debug, summarySupplier, iteratorPolicy, new DefaultMethodFilter());
    }

    public InterProceduralConcurrentAnalyzer(AnalysisMethod root,
                                             DebugContext debug,
                                             SummarySupplier<Domain> summarySupplier,
                                             MethodFilter methodFilter) {
        this(root, debug, summarySupplier, IteratorPolicy.DEFAULT_SEQUENTIAL, methodFilter);
    }

    public InterProceduralConcurrentAnalyzer(AnalysisMethod root,
                                             DebugContext debug,
                                             SummarySupplier<Domain> summarySupplier,
                                             IteratorPolicy iteratorPolicy,
                                             MethodFilter methodFilter) {
        super(root, debug, iteratorPolicy);
        this.summarySupplier = summarySupplier;
        this.methodFilter = methodFilter;
    }

    @Override
    public void run(Domain initialDomain, NodeInterpreter<Domain> nodeInterpreter) {
        InterProceduralAnalysisContext<Domain> payload = new InterProceduralAnalysisContext<>(initialDomain, iteratorPolicy, root, debug, nodeInterpreter, summarySupplier, checkerManager, methodFilter);
        payload.getLogger().logHighlightedDebugInfo("Running inter-procedural concurrent analysis");
        TransferFunction<Domain> transferFunction = new TransferFunction<>(nodeInterpreter, new InterProceduralCallInterpreter<>(payload), payload.getLogger());
        ConcurrentWpoFixpointIterator<Domain> iterator = new ConcurrentWpoFixpointIterator<>(payload, transferFunction);
        doRun(payload, iterator);
        payload.getLogger().logHighlightedDebugInfo("The computed summaries are: ");
        payload.getLogger().logDebugInfo(payload.getSummaryCache().toString());
    }
}