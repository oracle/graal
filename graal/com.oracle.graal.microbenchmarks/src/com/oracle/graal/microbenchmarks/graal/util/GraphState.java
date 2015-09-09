package com.oracle.graal.microbenchmarks.graal.util;

import static com.oracle.graal.microbenchmarks.graal.util.GraalUtil.*;
import jdk.internal.jvmci.meta.*;

import org.openjdk.jmh.annotations.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.nodes.*;

/**
 * State providing a new copy of a graph for each invocation of a benchmark. Subclasses of this
 * class are annotated with {@link MethodSpec} to specify the Java method that will be parsed to
 * obtain the original graph.
 */
@State(Scope.Thread)
public abstract class GraphState {

    public GraphState() {
        // Ensure a debug configuration for this thread is initialized
        if (Debug.isEnabled() && DebugScope.getConfig() == null) {
            DebugEnvironment.initialize(System.out);
        }

        GraalState graal = new GraalState();
        ResolvedJavaMethod method = graal.metaAccess.lookupJavaMethod(getMethodFromMethodSpec(getClass()));
        StructuredGraph graph = null;
        try (Debug.Scope s = Debug.scope("GraphState", method)) {
            graph = preprocessOriginal(getGraph(graal, method));
        } catch (Throwable t) {
            Debug.handle(t);
        }
        this.originalGraph = graph;
    }

    protected StructuredGraph preprocessOriginal(StructuredGraph graph) {
        return graph;
    }

    /**
     * Original graph from which the per-benchmark invocation {@link #graph} is cloned.
     */
    private final StructuredGraph originalGraph;

    /**
     * The graph processed by the benchmark.
     */
    public StructuredGraph graph;

    @Setup(Level.Invocation)
    public void beforeInvocation() {
        graph = (StructuredGraph) originalGraph.copy();
    }
}
