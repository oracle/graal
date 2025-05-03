package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This is a singleton class that provides utility methods for working with {@link ResolvedJavaType}.
 * Fundamentally, its purpose is to act as a wrapper around {@link BigBang} to
 * avoid passing this as an argument to every method that needs it.
 */
public final class BigBangUtil {

    private static BigBangUtil instance;
    private final BigBang bb;

    private BigBangUtil(BigBang bb) {
        this.bb = bb;
    }

    public static BigBangUtil getInstance(BigBang bb) {
        if (instance == null) {
            instance = new BigBangUtil(bb);
        }
        return instance;
    }

    public static BigBangUtil getInstance() {
        if (instance == null) {
            throw new IllegalStateException("BigBangUtil not initialized. Call getInstance(BigBang) first.");
        }
        return instance;
    }

    public BigBang getBigBang() {
        return bb;
    }

    public ResolvedJavaType lookUpType(Class<?> clazz) {
        return bb.getMetaAccess().lookupJavaType(clazz);
    }

    public ControlFlowGraph getGraph(AnalysisMethod root) {
        DebugContext debug = getBigBang().getDebug();
        StructuredGraph structuredGraph = root.decodeAnalyzedGraph(debug, null);
        if (structuredGraph == null) {
            throw AnalysisError.interruptAnalysis("Unable to get graph for analysisMethod: " + root);
        }
        return new ControlFlowGraphBuilder(structuredGraph).build();
    }
}
