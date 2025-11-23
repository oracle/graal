package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.Inflation;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.List;
import java.util.Optional;

/**
 * AnalysisServices centralizes access to svm utilities needed by the analysis framework:
 */
public final class AnalysisServices {

    private static AnalysisServices instance;
    private final Inflation inflation;

    private AnalysisServices(Inflation inflation) {
        this.inflation = inflation;
    }

    public static AnalysisServices getInstance(Inflation inflation) {
        if (instance == null) {
            instance = new AnalysisServices(inflation);
        }
        return instance;
    }

    public static AnalysisServices getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AnalysisServices not initialized. Call getInstance(Inflation) first.");
        }
        return instance;
    }

    public Inflation getInflation() {
        return inflation;
    }

    public ResolvedJavaType lookUpType(Class<?> clazz) {
        return inflation.getMetaAccess().lookupJavaType(clazz);
    }

    public StructuredGraph getGraph(AnalysisMethod method) {
        DebugContext debug = inflation.getDebug();
        StructuredGraph graph = method.decodeAnalyzedGraph(debug, null);
        if (graph == null) {
            throw AnalysisError.interruptAnalysis("Unable to get graph for analysisMethod: " + method);
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
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("Main entry point: " + mainEntryPoint.getName(), LoggerVerbosity.DEBUG);
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

    @Deprecated
    public void printInvokes(AnalysisMethod method, int depth, int maxDepth) {
        if (depth > maxDepth) {
            return;
        }
        for (var invoke : method.getInvokes()) {
            System.out.println("  ".repeat(depth) + "- " + invoke.getTargetMethod().getName());
            printInvokes(invoke.getTargetMethod(), depth + 1, maxDepth);
        }
    }

}
