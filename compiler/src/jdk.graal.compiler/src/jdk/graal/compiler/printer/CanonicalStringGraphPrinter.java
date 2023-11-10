/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.printer;

import static jdk.graal.compiler.debug.DebugOptions.CanonicalGraphStringsCheckConstants;
import static jdk.graal.compiler.debug.DebugOptions.CanonicalGraphStringsExcludeVirtuals;
import static jdk.graal.compiler.debug.DebugOptions.CanonicalGraphStringsRemoveIdentities;
import static jdk.graal.compiler.debug.DebugOptions.PrintCanonicalGraphStringFlavor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.PathUtilities;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.meta.ResolvedJavaMethod;

public class CanonicalStringGraphPrinter implements GraphPrinter {
    private static final Pattern IDENTITY_PATTERN = Pattern.compile("([A-Za-z0-9$_]+)@[0-9a-f]+");
    private final SnippetReflectionProvider snippetReflection;

    public CanonicalStringGraphPrinter(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    @Override
    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflection;
    }

    private static String removeIdentities(String str) {
        return IDENTITY_PATTERN.matcher(str).replaceAll("$1");
    }

    protected static void writeCanonicalGraphExpressionString(ValueNode node, boolean checkConstants, boolean removeIdentities, PrintWriter writer) {
        writer.print(node.getClass().getSimpleName());
        writer.print("(");
        Fields properties = node.getNodeClass().getData();
        for (int i = 0; i < properties.getCount(); i++) {
            String dataStr = String.valueOf(properties.get(node, i));
            if (removeIdentities) {
                dataStr = removeIdentities(dataStr);
            }
            writer.print(dataStr);
            if (i + 1 < properties.getCount() || node.inputPositions().iterator().hasNext()) {
                writer.print(", ");
            }
        }
        Iterator<Position> iterator = node.inputPositions().iterator();
        while (iterator.hasNext()) {
            Position position = iterator.next();
            Node input = position.get(node);
            if (checkConstants && input instanceof ConstantNode) {
                ConstantNode constantNode = (ConstantNode) input;
                String valueString = constantNode.getValue().toValueString();
                if (removeIdentities) {
                    valueString = removeIdentities(valueString);
                }
                writer.print(valueString);
            } else if (input instanceof ValueNode && !(input instanceof PhiNode) && !(input instanceof FixedNode)) {
                writeCanonicalGraphExpressionString((ValueNode) input, checkConstants, removeIdentities, writer);
            } else if (input == null) {
                writer.print("null");
            } else {
                writer.print(input.getClass().getSimpleName());
            }
            if (iterator.hasNext()) {
                writer.print(", ");
            }
        }
        writer.print(")");
    }

    protected static void writeCanonicalExpressionCFGString(StructuredGraph graph, boolean checkConstants, boolean removeIdentities, PrintWriter writer) {
        ControlFlowGraph controlFlowGraph = getControlFlowGraph(graph);
        if (controlFlowGraph == null) {
            return;
        }
        try {
            for (HIRBlock block : controlFlowGraph.getBlocks()) {
                writer.print("Block ");
                writer.print(block);
                writer.print(" ");
                if (block == controlFlowGraph.getStartBlock()) {
                    writer.print("* ");
                }
                writer.print("-> ");
                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    HIRBlock successor = block.getSuccessorAt(i);
                    writer.print(successor);
                    writer.print(" ");
                }
                writer.println();
                FixedNode node = block.getBeginNode();
                while (node != null) {
                    writeCanonicalGraphExpressionString(node, checkConstants, removeIdentities, writer);
                    writer.println();
                    if (node instanceof FixedWithNextNode) {
                        node = ((FixedWithNextNode) node).next();
                    } else {
                        node = null;
                    }
                }
            }
        } catch (Throwable e) {
            writer.println();
            e.printStackTrace(writer);
        }
    }

    protected static ControlFlowGraph getControlFlowGraph(StructuredGraph graph) {
        try {
            return ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeFrequency(true).build();
        } catch (Throwable e) {
            // Ignore a non-well formed graph
            return null;
        }
    }

    protected static void writeCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants, PrintWriter writer) {
        StructuredGraph.ScheduleResult scheduleResult = GraphPrinter.getScheduleOrNull(graph);
        if (scheduleResult == null) {
            return;
        }
        try {

            NodeMap<Integer> canonicalId = graph.createNodeMap();
            int nextId = 0;

            List<String> constantsLines = null;
            if (checkConstants) {
                constantsLines = new ArrayList<>();
            }

            for (HIRBlock block : scheduleResult.getCFG().getBlocks()) {
                writer.print("Block ");
                writer.print(block);
                writer.print(" ");
                if (block == scheduleResult.getCFG().getStartBlock()) {
                    writer.print("* ");
                }
                writer.print("-> ");
                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    HIRBlock successor = block.getSuccessorAt(i);
                    writer.print(successor);
                    writer.print(" ");
                }
                writer.println();
                for (Node node : scheduleResult.getBlockToNodesMap().get(block)) {
                    if (node instanceof ValueNode && node.isAlive()) {
                        if (!excludeVirtual || !(node instanceof VirtualObjectNode || node instanceof ProxyNode || node instanceof FullInfopointNode)) {
                            if (node instanceof ConstantNode) {
                                if (constantsLines != null) {
                                    String name = node.toString(Verbosity.Name);
                                    String str = name + (excludeVirtual ? "" : "    (" + filteredUsageCount(node) + ")");
                                    constantsLines.add(str);
                                }
                            } else {
                                int id;
                                if (canonicalId.get(node) != null) {
                                    id = canonicalId.get(node);
                                } else {
                                    id = nextId++;
                                    canonicalId.set(node, id);
                                }
                                String name = node.getClass().getSimpleName();
                                writer.print("  ");
                                writer.print(id);
                                writer.print("|");
                                writer.print(name);
                                if (!excludeVirtual) {
                                    writer.print("    (");
                                    writer.print(filteredUsageCount(node));
                                    writer.print(")");
                                }
                                writer.println();
                            }
                        }
                    }
                }
            }
            if (constantsLines != null) {
                writer.print(constantsLines.size());
                writer.println(" constants:");
                Collections.sort(constantsLines);
                for (String s : constantsLines) {
                    writer.println(s);
                }
            }
        } catch (Throwable t) {
            writer.println();
            t.printStackTrace(writer);
        }
    }

    public static String getCanonicalGraphString(StructuredGraph graph, boolean excludeVirtual, boolean checkConstants) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        writeCanonicalGraphString(graph, excludeVirtual, checkConstants, writer);
        writer.flush();
        return stringWriter.toString();
    }

    private static int filteredUsageCount(Node node) {
        return node.usages().filter(n -> !(n instanceof FrameState)).count();
    }

    @Override
    public void beginGroup(DebugContext debug, String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException {
    }

    private StructuredGraph currentGraph;
    private String currentDirectory;

    private String getDirectory(DebugContext debug, StructuredGraph graph) {
        if (graph == currentGraph) {
            return currentDirectory;
        }
        currentDirectory = debug.getDumpPath(".graph-strings", true);
        currentGraph = graph;
        return currentDirectory;
    }

    @Override
    public void print(DebugContext debug, Graph graph, Map<Object, Object> properties, int id, String format, Object... args) throws IOException {
        if (graph instanceof StructuredGraph) {
            OptionValues options = graph.getOptions();
            StructuredGraph structuredGraph = (StructuredGraph) graph;
            String outDirectory = getDirectory(debug, structuredGraph);
            String title = String.format("%03d-%s.txt", id, String.format(format, simplifyClassArgs(args)));
            String filePath = PathUtilities.getPath(outDirectory, PathUtilities.sanitizeFileName(title));
            try (PrintWriter writer = new PrintWriter(new BufferedWriter(new OutputStreamWriter(PathUtilities.openOutputStream(filePath))))) {
                switch (PrintCanonicalGraphStringFlavor.getValue(options)) {
                    case 1:
                        writeCanonicalExpressionCFGString(structuredGraph, CanonicalGraphStringsCheckConstants.getValue(options), CanonicalGraphStringsRemoveIdentities.getValue(options), writer);
                        break;
                    case 0:
                    default:
                        writeCanonicalGraphString(structuredGraph, CanonicalGraphStringsExcludeVirtuals.getValue(options), CanonicalGraphStringsCheckConstants.getValue(options), writer);
                        break;
                }
            }
        }
    }

    @Override
    public void endGroup() throws IOException {
    }

    @Override
    public void close() {
    }
}
