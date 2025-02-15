package com.oracle.svm.hosted.analysis.ai.analyzer.payload;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IterationStrategy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.iterator.policy.IteratorPolicy;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wpo.WeakPartialOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WeakTopologicalOrdering;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the payload for the fixpoint iteration process, managing mappings
 * between analysis methods and their corresponding control flow graphs, weak
 * topological orderings, and weak partial orderings. This class enables efficient
 * reuse of computations during analysis.
 */
public final class IteratorPayload {

    private final IteratorPolicy iteratorPolicy;

    /* analysis method to the corresponding control flow graph mapping -> to avoid getting the cfg on every fixpoint creation */
    private final Map<AnalysisMethod, ControlFlowGraph> methodGraphMap = new HashMap<>();

    /* analysis method to the corresponding weak topological ordering mapping -> to avoid computing WTO on every fixpoint creation */
    private final Map<AnalysisMethod, WeakTopologicalOrdering> methodWtoMap = new HashMap<>();

    /* analysis method to the corresponding weak partial ordering mapping -> to avoid computing WPO on every fixpoint creation */

    private final Map<AnalysisMethod, WeakPartialOrdering> methodWpoMap = new HashMap<>();

    public IteratorPolicy getIteratorPolicy() {
        return iteratorPolicy;
    }

    public void setMethodGraphMap(Map<AnalysisMethod, ControlFlowGraph> methodGraphMap) {
        this.methodGraphMap.clear();
        this.methodGraphMap.putAll(methodGraphMap);
    }

    public Map<AnalysisMethod, ControlFlowGraph> getMethodGraph() {
        return methodGraphMap;
    }

    public boolean containsMethodGraph(AnalysisMethod method) {
        return methodGraphMap.containsKey(method);
    }

    public void addToMethodGraphMap(AnalysisMethod method, ControlFlowGraph controlFlowGraph) {
        this.methodGraphMap.put(method, controlFlowGraph);
    }

    public Map<AnalysisMethod, WeakTopologicalOrdering> getMethodWtoMap() {
        return methodWtoMap;
    }

    public boolean containsMethodWto(AnalysisMethod method) {
        return methodWtoMap.containsKey(method);
    }

    public void setMethodWtoMap(Map<AnalysisMethod, WeakTopologicalOrdering> methodWtoMap) {
        this.methodWtoMap.clear();
        this.methodWtoMap.putAll(methodWtoMap);
    }

    public void addToMethodWtoMap(AnalysisMethod method, WeakTopologicalOrdering wto) {
        this.methodWtoMap.put(method, wto);
    }

    public Map<AnalysisMethod, WeakPartialOrdering> getMethodWpoMap() {
        return methodWpoMap;
    }

    public boolean containsMethodWpo(AnalysisMethod method) {
        return methodWpoMap.containsKey(method);
    }

    public void setMethodWpoMap(Map<AnalysisMethod, WeakPartialOrdering> methodWpoMap) {
        this.methodWpoMap.clear();
        this.methodWpoMap.putAll(methodWpoMap);
    }

    public void addToMethodWpoMap(AnalysisMethod method, WeakPartialOrdering wpo) {
        this.methodWpoMap.put(method, wpo);
    }

    public IteratorPayload(IteratorPolicy iteratorPolicy) {
        this.iteratorPolicy = iteratorPolicy;
    }

    public int getMaxJoinIterations() {
        return iteratorPolicy.maxJoinIterations();
    }

    public int getMaxWidenIterations() {
        return iteratorPolicy.maxWidenIterations();
    }

    public IterationStrategy getIterationStrategy() {
        return iteratorPolicy.strategy();
    }
}
