/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.printer;

import static org.graalvm.compiler.debug.DebugConfig.asJavaMethod;
import static org.graalvm.compiler.debug.DebugOptions.PrintUnmodifiedGraphs;

import java.io.IOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugDumpScope;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.debug.DebugOptions.PrintGraphTarget;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.contract.NodeCostUtil;
import org.graalvm.compiler.serviceprovider.GraalServices;

import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.services.Services;

//JaCoCo Exclude

/**
 * Observes compilation events and uses {@link BinaryGraphPrinter} to generate a graph
 * representation that can be inspected with the Graph Visualizer.
 */
public final class GraphPrinterDumpHandler implements DebugDumpHandler {

    private static final int FAILURE_LIMIT = 8;
    private final GraphPrinterSupplier printerSupplier;
    protected GraphPrinter printer;
    private List<String> previousInlineContext;
    private CompilationIdentifier previousCompilationID = CompilationIdentifier.INVALID_COMPILATION_ID;
    private Graph lastGraph;
    private int lastModCount;
    private int[] dumpIds = {};
    private int failuresCount;
    private Map<Graph, List<String>> inlineContextMap;
    private final String jvmArguments;
    private final String sunJavaCommand;

    @FunctionalInterface
    public interface GraphPrinterSupplier {
        GraphPrinter get(DebugContext ctx, Graph graph) throws IOException;
    }

    /**
     * Creates a new {@link GraphPrinterDumpHandler}.
     *
     * @param printerSupplier Supplier used to create the GraphPrinter. Should supply an optional or
     *            null in case of failure.
     */
    public GraphPrinterDumpHandler(GraphPrinterSupplier printerSupplier) {
        this.printerSupplier = printerSupplier;
        /* Add the JVM and Java arguments to the graph properties to help identify it. */
        this.jvmArguments = jvmArguments();
        this.sunJavaCommand = Services.getSavedProperties().get("sun.java.command");
    }

    private static String jvmArguments() {
        List<String> inputArguments = GraalServices.getInputArguments();
        if (inputArguments != null) {
            return String.join(" ", inputArguments);
        }
        return "unknown";
    }

    private void ensureInitialized(DebugContext ctx, Graph graph) {
        if (printer == null) {
            if (failuresCount >= FAILURE_LIMIT) {
                return;
            }
            previousInlineContext = new ArrayList<>();
            inlineContextMap = new WeakHashMap<>();
            DebugContext debug = graph.getDebug();
            try {
                printer = printerSupplier.get(ctx, graph);
            } catch (IOException e) {
                handleException(debug, e);
            }
        }
    }

    private int nextDumpId() {
        int depth = previousInlineContext.size();
        if (dumpIds.length < depth + 1) {
            dumpIds = Arrays.copyOf(dumpIds, depth + 1);
        }
        return dumpIds[depth]++;
    }

    @Override
    @SuppressWarnings("try")
    public void dump(Object object, DebugContext debug, boolean forced, final String format, Object... arguments) {
        OptionValues options = debug.getOptions();
        if (object instanceof Graph && DebugOptions.PrintGraph.getValue(options) != PrintGraphTarget.Disable) {
            final Graph graph = (Graph) object;
            ensureInitialized(debug, graph);
            if (printer == null) {
                return;
            }

            // Get all current JavaMethod instances in the context.
            List<String> inlineContext = getInlineContext(graph);

            if (graph instanceof StructuredGraph) {
                CompilationIdentifier compilationID = ((StructuredGraph) graph).compilationId();
                // If the graph to be dumped is with an invalid compilation id, it is likely derived
                // from inlining.
                if (compilationID != CompilationIdentifier.INVALID_COMPILATION_ID) {
                    if (previousCompilationID != CompilationIdentifier.INVALID_COMPILATION_ID && !compilationID.equals(previousCompilationID)) {
                        // Compilation ID does not match, close existing scopes.
                        for (int inlineDepth = previousInlineContext.size() - 1; inlineDepth >= 0; --inlineDepth) {
                            closeScope(debug, inlineDepth);
                        }
                        previousInlineContext = new ArrayList<>();
                    }
                    previousCompilationID = compilationID;
                }
            }

            if (!inlineContext.equals(previousInlineContext)) {
                Map<Object, Object> properties = new HashMap<>();
                properties.put("graph", graph.toString());
                addCompilationId(properties, graph);
                // Check for method scopes that must be closed since the previous dump.
                for (int i = 0; i < previousInlineContext.size(); ++i) {
                    if (i >= inlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                        for (int inlineDepth = previousInlineContext.size() - 1; inlineDepth >= i; --inlineDepth) {
                            closeScope(debug, inlineDepth);
                        }
                        break;
                    }
                }
                // Check for method scopes that must be opened since the previous dump.
                for (int i = 0; i < inlineContext.size(); ++i) {
                    if (i >= previousInlineContext.size() || !inlineContext.get(i).equals(previousInlineContext.get(i))) {
                        for (int inlineDepth = i; inlineDepth < inlineContext.size(); ++inlineDepth) {
                            openScope(debug, inlineContext.get(inlineDepth), inlineDepth, inlineDepth == inlineContext.size() - 1 ? properties : null);
                        }
                        break;
                    }
                }
            }

            // Save inline context for next dump.
            previousInlineContext = inlineContext;

            // Capture before creating the sandbox
            String currentScopeName = debug.getCurrentScopeName();
            try (DebugContext.Scope s = debug.sandbox("PrintingGraph", null)) {
                // Finally, output the graph.
                Map<Object, Object> properties = new HashMap<>();
                properties.put("scope", currentScopeName);
                graph.getDebugProperties(properties);
                if (graph instanceof StructuredGraph) {
                    try {
                        int size = NodeCostUtil.computeGraphSize((StructuredGraph) graph);
                        properties.put("node-cost graph size", size);
                    } catch (Throwable t) {
                        properties.put("node-cost-exception", t.getMessage());
                    }
                    properties.put("StageFlags", ((StructuredGraph) graph).getStageFlags());
                }
                if (PrintUnmodifiedGraphs.getValue(options) || lastGraph != graph || lastModCount != graph.getModificationCount()) {
                    printer.print(debug, graph, properties, nextDumpId(), format, arguments);
                    lastGraph = graph;
                    lastModCount = graph.getModificationCount();
                }
            } catch (IOException e) {
                handleException(debug, e);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
        }
    }

    void handleException(DebugContext debug, IOException e) {
        if (debug != null && DebugOptions.DumpingErrorsAreFatal.getValue(debug.getOptions())) {
            throw new GraalError(e);
        }
        if (e instanceof ClosedByInterruptException) {
            /*
             * The current dumping was aborted by an interrupt so treat this as a transient failure.
             */
            failuresCount = 0;
        } else {
            failuresCount++;
        }
        printer = null;
        e.printStackTrace(TTY.out);
        if (failuresCount > FAILURE_LIMIT) {
            TTY.println("Too many failures with dumping. Disabling dump in thread " + Thread.currentThread());
        }
    }

    private static void addCompilationId(Map<Object, Object> properties, final Graph graph) {
        if (graph instanceof StructuredGraph) {
            properties.put("compilationId", ((StructuredGraph) graph).compilationId());
        }
    }

    private List<String> getInlineContext(Graph graph) {
        List<String> result = inlineContextMap.get(graph);
        if (result == null) {
            result = new ArrayList<>();
            Object lastMethodOrGraph = null;
            boolean graphSeen = false;
            DebugContext debug = graph.getDebug();
            for (Object o : debug.context()) {
                if (o == graph) {
                    graphSeen = true;
                }

                if (o instanceof DebugDumpScope) {
                    DebugDumpScope debugDumpScope = (DebugDumpScope) o;
                    if (debugDumpScope.decorator && !result.isEmpty()) {
                        result.set(result.size() - 1, debugDumpScope.name + ":" + result.get(result.size() - 1));
                    } else {
                        result.add(debugDumpScope.name);
                    }
                } else {
                    addMethodContext(result, o, lastMethodOrGraph);
                }
                if (o instanceof JavaMethod || o instanceof Graph) {
                    lastMethodOrGraph = o;
                }
            }
            // Truffle compilations don't have a standard inline context.
            // Since TruffleDebugJavaMethod specifies the declaring class for truffle compilations
            // as "LTruffleGraal" we identify truffle compilations as starting with "TruffleGraal"
            if (result.size() == 2 && result.get(1).startsWith("TruffleGraal")) {
                String name = result.get(1).replace("TruffleGraal.", "TruffleIR::");
                result.clear();
                result.add(name);
            }
            if (result.isEmpty()) {
                result.add(graph.toString());
                graphSeen = true;
            }
            // Reverse list such that inner method comes after outer method.
            Collections.reverse(result);
            if (!graphSeen) {
                /*
                 * The graph isn't in any context but is being processed within another graph so add
                 * it to the end of the scopes.
                 */
                if (asJavaMethod(graph) != null) {
                    addMethodContext(result, graph, lastMethodOrGraph);
                } else {
                    result.add(graph.toString());
                }
            }
            inlineContextMap.put(graph, result);
        }
        return result;
    }

    private static void addMethodContext(List<String> result, Object o, Object lastMethodOrGraph) {
        JavaMethod method = asJavaMethod(o);
        if (method != null) {
            /*
             * Include the current method in the context if there was no previous JavaMethod or
             * JavaMethodContext or if the method is different or if the method is the same but it
             * comes from two different graphs. This ensures that recursive call patterns are
             * handled properly.
             */
            if (lastMethodOrGraph == null || asJavaMethod(lastMethodOrGraph) == null || !asJavaMethod(lastMethodOrGraph).equals(method) ||
                            (lastMethodOrGraph != o && lastMethodOrGraph instanceof Graph && o instanceof Graph)) {
                result.add(method.format("%H.%n(%p)"));
            } else {
                /*
                 * This prevents multiple adjacent method context objects for the same method from
                 * resulting in multiple IGV tree levels. This works on the assumption that real
                 * inlining debug scopes will have a graph context object between the inliner and
                 * inlinee context objects.
                 */
            }
        }
    }

    private void openScope(DebugContext debug, String name, int inlineDepth, Map<Object, Object> properties) {
        try {
            Map<Object, Object> props = properties;
            if (inlineDepth == 0) {
                /* Include some VM specific properties at the root. */
                if (props == null) {
                    props = new HashMap<>();
                }
                props.put("jvmArguments", jvmArguments);
                if (sunJavaCommand != null) {
                    props.put("sun.java.command", sunJavaCommand);
                }
                props.put("date", new Date().toString());
            }
            printer.beginGroup(debug, name, name, debug.contextLookup(ResolvedJavaMethod.class), -1, props);
        } catch (IOException e) {
            handleException(debug, e);
        }
    }

    private void closeScope(DebugContext debug, int inlineDepth) {
        dumpIds[inlineDepth] = 0;
        try {
            if (printer != null) {
                printer.endGroup();
            }
        } catch (IOException e) {
            handleException(debug, e);
        }
    }

    @Override
    public void close() {
        if (previousInlineContext != null) {
            for (int inlineDepth = 0; inlineDepth < previousInlineContext.size(); inlineDepth++) {
                closeScope(null, inlineDepth);
            }
        }
        if (printer != null) {
            printer.close();
            printer = null;
        }
    }
}
