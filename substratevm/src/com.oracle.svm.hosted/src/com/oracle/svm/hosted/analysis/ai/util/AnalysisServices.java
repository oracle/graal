package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.hosted.analysis.Inflation;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.List;

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

    public AnalysisMethod getMainMethod(AnalysisMethod mainEntryPoint) {
        try {
            AnalysisMethod doRunMethod = findInvokeWithName(mainEntryPoint, "doRun");
            AnalysisMethod runCore0Method = findInvokeWithName(doRunMethod, "runCore0");
            AnalysisMethod invokeMainMethod = findInvokeWithName(runCore0Method, "invokeMain");
            return findInvokeWithName(invokeMainMethod, "main");
        } catch (AnalysisError e) {
            return null;
        }
    }

    private AnalysisMethod findInvokeWithName(AnalysisMethod root, String name) {
        for (var invokeInfo : root.getInvokes()) {
            if (invokeInfo.getTargetMethod().getName().equals(name)) {
                return invokeInfo.getTargetMethod();
            }
        }
        throw AnalysisError.interruptAnalysis("No invoke with name: " + name + " found in: " + root);
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
