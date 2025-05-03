package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.util.ClassUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;

public class IgvDumper {

    @SuppressWarnings("try")
    public static StructuredGraph dumpPhase(AnalysisMethod method, StructuredGraph graph, String phaseName) {
        BigBang bb = BigBangUtil.getInstance().getBigBang();
        DebugContext.Description description = new DebugContext.Description(method, ClassUtil.getUnqualifiedName(method.getClass()) + ":" + method.getId());
        DebugContext debug = new DebugContext.Builder(bb.getOptions(), new GraalDebugHandlersFactory(bb.getSnippetReflectionProvider())).description(description).build();

        try (DebugContext.Scope s = debug.scope("AbstractInterpretationAnalysis", graph)) {
            debug.dump(DebugContext.BASIC_LEVEL, graph, phaseName);
            return graph;
        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }
}
