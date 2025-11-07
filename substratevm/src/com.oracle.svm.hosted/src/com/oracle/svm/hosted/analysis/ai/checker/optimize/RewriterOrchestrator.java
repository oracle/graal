package com.oracle.svm.hosted.analysis.ai.checker.optimize;

import com.oracle.graal.pointsto.flow.AnalysisParsedGraph;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.Fact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.IndexSafetyFact;
import com.oracle.svm.hosted.analysis.ai.checker.core.facts.ConditionTruthFact;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.GraphEncoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions.NO;
import static jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions.YES;

/**
 * Applies graph rewrites based on aggregated facts in a fixed, safe order, directly on the live graph.
 */
public final class RewriterOrchestrator {

    public static void apply(AnalysisMethod method, StructuredGraph graph, FactAggregator aggregator) {
        var logger = AbstractInterpretationLogger.getInstance();

        eliminateBoundsChecks(aggregator, graph);
        GraphRewrite.sweepUnreachableFixed(graph);

        applyAbstractInterpretationResults(method, graph);
    }

    private static void applyAbstractInterpretationResults(AnalysisMethod method, StructuredGraph graph) {
        var logger = AbstractInterpretationLogger.getInstance();
        if (!graph.verify()) {
            logger.log("[RewriterOrchestrator] Graph verification failed before re-encode; aborting persistence.", LoggerVerbosity.CHECKER_ERR);
            return;
        }

        try {
            var encoded = GraphEncoder.encodeSingleGraph(graph, AnalysisParsedGraph.HOST_ARCHITECTURE);
            var debug = graph.getDebug();
            var testGraph = new StructuredGraph.Builder(debug.getOptions(), debug, graph.getAssumptions() != null ? YES : NO)
                    .method(method)
                    .trackNodeSourcePosition(graph.trackNodeSourcePosition())
                    .recordInlinedMethods(graph.isRecordingInlinedMethods())
                    .build();
            new GraphDecoder(AnalysisParsedGraph.HOST_ARCHITECTURE, testGraph).decode(encoded);
            method.setAnalyzedGraph(encoded);
            logger.log("[RewriterOrchestrator] Persisted re-encoded graph (ai.persistRewrites=true), nodeCount=" + testGraph.getNodeCount(), LoggerVerbosity.CHECKER);
        } catch (Throwable ex) {
            logger.log("[RewriterOrchestrator] Re-encode validation failed: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + " (graph not persisted)", LoggerVerbosity.CHECKER_WARN);
        }
    }

    private static void eliminateBoundsChecks(FactAggregator aggregator, StructuredGraph graph) {
        List<Fact> idxFacts = aggregator.factsOfKind("IndexSafety");
        for (Fact f : idxFacts.reversed()) {
            IndexSafetyFact isf = (IndexSafetyFact) f;
            var n = isf.getArrayAccess();
            if (!isf.isInBounds()) continue;
            if (!(n instanceof LoadIndexedNode) && !(n instanceof StoreIndexedNode)) {
                continue;
            }
            Node guardIf = findGuardingIf(n);
            if (!(guardIf instanceof IfNode ifn)) {
                continue;
            }
            // FIXME: We are only folding index-bounds checks, not null checks.
            var cond = ifn.condition();
            if (cond instanceof jdk.graal.compiler.nodes.calc.IntegerBelowNode ||
                cond instanceof jdk.graal.compiler.nodes.calc.IntegerLessThanNode) {
                GraphRewrite.foldIfTrue(graph, ifn);
            }
        }
    }

    private static Node findGuardingIf(Node n) {
        Set<Node> seen = new HashSet<>();
        ArrayDeque<Node> work = new ArrayDeque<>();
        work.add(n);
        while (!work.isEmpty()) {
            Node cur = work.poll();
            if (!seen.add(cur)) continue;
            for (var pred : cur.cfgPredecessors()) {
                if (pred instanceof IfNode ifn) {
                    return ifn;
                }
                work.add(pred);
            }
        }
        return null;
    }
}
