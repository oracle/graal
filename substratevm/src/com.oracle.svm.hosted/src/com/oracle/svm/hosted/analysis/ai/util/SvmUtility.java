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
 * get more sophisticated access to svm metadata.
 */
public final class SvmUtility {

    private static SvmUtility instance;
    private final BigBang bb;

    private SvmUtility(BigBang bb) {
        this.bb = bb;
    }

    public static SvmUtility getInstance(BigBang bb) {
        if (instance == null) {
            instance = new SvmUtility(bb);
        }
        return instance;
    }

    public static SvmUtility getInstance() {
        if (instance == null) {
            throw new IllegalStateException("SvmUtility not initialized.");
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
