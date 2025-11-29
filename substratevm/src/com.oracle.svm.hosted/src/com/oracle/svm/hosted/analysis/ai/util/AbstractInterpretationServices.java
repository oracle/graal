package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.exception.AbstractInterpretationException;
import com.oracle.svm.hosted.analysis.ai.stats.AbstractInterpretationStatistics;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents centralized information about utilities needed by the abstract interpretation analysis
 */
public final class AbstractInterpretationServices {

    private static AbstractInterpretationServices instance;
    private final Inflation inflation;
    private final AbstractInterpretationStatistics stats = new AbstractInterpretationStatistics();
    private final Set<AnalysisMethod> touchedMethods = new HashSet<>();

    private AbstractInterpretationServices(Inflation inflation) {
        this.inflation = inflation;
    }

    public static AbstractInterpretationServices getInstance(Inflation inflation) {
        if (instance == null) {
            instance = new AbstractInterpretationServices(inflation);
        }
        return instance;
    }

    public static AbstractInterpretationServices getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AnalysisServices not initialized. Call getInstance(Inflation) first.");
        }
        return instance;
    }

    public AbstractInterpretationStatistics getStats() {
        return stats;
    }

    public Inflation getInflation() {
        return inflation;
    }

    public void markMethodTouched(AnalysisMethod method) {
        if (method != null) {
            touchedMethods.add(method);
        }
    }

    public int getTouchedMethodsCount() {
        return touchedMethods.size();
    }

    public Set<AnalysisMethod> getTouchedMethods() {
        return Collections.unmodifiableSet(touchedMethods);
    }

    public ResolvedJavaType lookUpType(Class<?> clazz) {
        return inflation.getMetaAccess().lookupJavaType(clazz);
    }

    public StructuredGraph getGraph(AnalysisMethod method) {
        DebugContext debug = inflation.getDebug();
        StructuredGraph graph = method.decodeAnalyzedGraph(debug, null);
        if (graph == null) {
            AbstractInterpretationException.analysisMethodGraphNotFound(method);
        }
        return graph;
    }

    public List<AnalysisMethod> getInvokedMethods() {
        return inflation.getUniverse()
                .getMethods()
                .stream()
                .filter(AnalysisMethod::isSimplyImplementationInvoked).toList();
    }

    public Optional<AnalysisMethod> getMainMethod(AnalysisMethod mainEntryPoint) {
        return findInvokeWithName(mainEntryPoint, "doRun")
                .flatMap(m -> findInvokeWithName(m, "runCore0"))
                .flatMap(m -> findInvokeWithName(m, "invokeMain"))
                .flatMap(m -> findInvokeWithName(m, "main"));
    }

    private Optional<AnalysisMethod> findInvokeWithName(AnalysisMethod root, String name) {
        if (root == null) {
            return Optional.empty();
        }
        for (var invokeInfo : root.getInvokes()) {
            if (invokeInfo.getTargetMethod().getName().equals(name)) {
                return Optional.of(invokeInfo.getTargetMethod());
            }
        }
        return Optional.empty();
    }
}
