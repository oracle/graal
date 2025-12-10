package com.oracle.svm.hosted.analysis.ai.analysis.context;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wpo.WeakPartialOrdering;
import com.oracle.svm.hosted.analysis.ai.fixpoint.wto.WeakTopologicalOrdering;
import jdk.graal.compiler.nodes.StructuredGraph;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central cache for per-method graph artifacts (CFG, WTO, WPO), so they are built once per method
 * and shared across fixpoint iterators.
 */
public final class MethodGraphCache {

    private final Map<AnalysisMethod, StructuredGraph> methodGraphMap = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, WeakTopologicalOrdering> methodWtoMap = new ConcurrentHashMap<>();
    private final Map<AnalysisMethod, WeakPartialOrdering> methodWpoMap = new ConcurrentHashMap<>();

    public Map<AnalysisMethod, StructuredGraph> getMethodGraphMap() {
        return methodGraphMap;
    }

    public boolean containsMethodGraph(AnalysisMethod method) {
        return methodGraphMap.containsKey(method);
    }

    public void addToMethodGraphMap(AnalysisMethod method, StructuredGraph graph) {
        methodGraphMap.put(method, graph);
    }

    public Map<AnalysisMethod, WeakTopologicalOrdering> getMethodWtoMap() {
        return methodWtoMap;
    }

    public boolean containsMethodWto(AnalysisMethod method) {
        return methodWtoMap.containsKey(method);
    }

    public void setMethodWtoMap(Map<AnalysisMethod, WeakTopologicalOrdering> map) {
        methodWtoMap.clear();
        methodWtoMap.putAll(map);
    }

    public void addToMethodWtoMap(AnalysisMethod method, WeakTopologicalOrdering wto) {
        methodWtoMap.put(method, wto);
    }

    public Map<AnalysisMethod, WeakPartialOrdering> getMethodWpoMap() {
        return methodWpoMap;
    }

    public boolean containsMethodWpo(AnalysisMethod method) {
        return methodWpoMap.containsKey(method);
    }

    public void setMethodWpoMap(Map<AnalysisMethod, WeakPartialOrdering> map) {
        methodWpoMap.clear();
        methodWpoMap.putAll(map);
    }

    public void addToMethodWpoMap(AnalysisMethod method, WeakPartialOrdering wpo) {
        methodWpoMap.put(method, wpo);
    }
}

