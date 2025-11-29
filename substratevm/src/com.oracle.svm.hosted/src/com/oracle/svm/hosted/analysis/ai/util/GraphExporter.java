package com.oracle.svm.hosted.analysis.ai.util;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

public class GraphExporter {

    public static void exportToJson(ControlFlowGraph cfg, AnalysisMethod method, String outputPath) throws IOException {
        StructuredGraph graph = cfg.graph;
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("{");
            writer.println("  \"method\": \"" + method.format("%H.%n(%p)") + "\",");
            writer.println("  \"nodes\": [");

            Map<Node, Integer> nodeIds = new HashMap<>();
            int id = 0;
            for (Node node : graph.getNodes()) {
                nodeIds.put(node, id++);
            }

            boolean first = true;
            for (Node node : graph.getNodes()) {
                if (!first) writer.println(",");
                first = false;

                writer.println("    {");
                writer.println("      \"node\": \"" + node.toString().replace("\"", "\\\"") + "\",");
                writer.println("      \"type\": \"" + node.getNodeClass().shortName() + "\",");
                writer.println("      \"class\": \"" + node.getClass().getSimpleName() + "\",");

                writer.print("      \"successors\": [");
                boolean firstSucc = true;
                for (Node succ : node.successors()) {
                    if (!firstSucc) writer.print(", ");
                    writer.print(nodeIds.get(succ));
                    firstSucc = false;
                }
                writer.println("],");

                writer.print("      \"inputs\": [");
                boolean firstInput = true;
                for (Node input : node.inputs()) {
                    if (!firstInput) writer.print(", ");
                    writer.print(nodeIds.get(input));
                    firstInput = false;
                }
                writer.println("],");

                writer.print("      \"usages\": [");
                boolean firstUsage = true;
                for (Node usage : node.usages()) {
                    if (!firstUsage) writer.print(", ");
                    writer.print(nodeIds.get(usage));
                    firstUsage = false;
                }
                writer.println("],");

                writer.print("      \"properties\": {");
                switch (node) {
                    case PhiNode phi -> writer.print("\"valueCount\": " + phi.valueCount());
                    case LoopBeginNode loopBeginNode -> writer.print("\"loopHeader\": true");
                    case LoopEndNode loopEnd ->
                            writer.print("\"loopEnd\": true, \"loopBegin\": " + nodeIds.get(loopEnd.loopBegin()));
                    default -> {
                    }
                }
                writer.println("}");

                writer.print("    }");
            }
            writer.println();
            writer.println("  ],");

            writer.println("  \"cfg\": [");
            first = true;
            for (HIRBlock block : cfg.getBlocks()) {
                if (!first) writer.println(",");
                first = false;

                writer.println("    {");
                writer.println("      \"id\": " + block.getId() + ",");
                writer.print("      \"predecessors\": [");
                for (int i = 0; i < block.getPredecessorCount(); i++) {
                    if (i > 0) writer.print(", ");
                    writer.print(block.getPredecessorAt(i).getId());
                }
                writer.println("],");

                writer.print("      \"successors\": [");
                for (int i = 0; i < block.getSuccessorCount(); i++) {
                    if (i > 0) writer.print(", ");
                    writer.print(block.getSuccessorAt(i).getId());
                }
                writer.println("],");

                writer.print("      \"nodes\": [");
                boolean firstNode = true;
                for (Node n : block.getNodes()) {
                    if (!firstNode) writer.print(", ");
                    writer.print(nodeIds.get(n));
                    firstNode = false;
                }
                writer.println("]");

                writer.print("    }");
            }
            writer.println();
            writer.println("  ]");
            writer.println("}");
        }
    }

    public void exportToText(ControlFlowGraph cfg, AnalysisMethod method, String outputPath) throws IOException {
        StructuredGraph graph = cfg.graph;
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("=".repeat(80));
            writer.println("GRAPH EXPORT FOR ABSTRACT INTERPRETATION ANALYSIS");
            writer.println("=".repeat(80));
            writer.println();
            writer.println("Method: " + method.format("%H.%n(%p)"));
            writer.println("Node Count: " + graph.getNodeCount());
            writer.println("Block Count: " + cfg.getBlocks().length);
            writer.println();

            writer.println("=".repeat(80));
            writer.println("NODES (Data Flow Graph)");
            writer.println("=".repeat(80));
            writer.println();

            for (Node node : graph.getNodes()) {
                writer.println("Node: " + node.toString());
                writer.println("  Class: " + node.getClass().getSimpleName());
                writer.println("  String: " + node);

                // Control flow successors
                if (node.successors().iterator().hasNext()) {
                    writer.print("  Successors (control): ");
                    boolean first = true;
                    for (Node succ : node.successors()) {
                        if (!first) writer.print(", ");
                        writer.print(succ.toString());
                        first = false;
                    }
                    writer.println();
                }

                // Data flow inputs
                if (node.inputs().iterator().hasNext()) {
                    writer.print("  Inputs (data): ");
                    boolean first = true;
                    for (Node input : node.inputs()) {
                        if (!first) writer.print(", ");
                        writer.print(input.toString() + " (" + input.getNodeClass().shortName() + ")");
                        first = false;
                    }
                    writer.println();
                }

                // Usages
                if (node.usages().iterator().hasNext()) {
                    writer.print("  Usages: ");
                    boolean first = true;
                    for (Node usage : node.usages()) {
                        if (!first) writer.print(", ");
                        writer.print(usage.toString());
                        first = false;
                    }
                    writer.println();
                }

                // Special handling for important node types
                switch (node) {
                    case PhiNode phi -> {
                        writer.println("  >>> PHI NODE <<<");
                        writer.println("  Value count: " + phi.valueCount());
                        for (int i = 0; i < phi.valueCount(); i++) {
                            Node value = phi.valueAt(i);
                            writer.println("    [" + i + "]: " + value.toString() + " (" + value.getNodeClass().shortName() + ")");
                        }
                    }
                    case LoopBeginNode loopBeginNode -> writer.println("  >>> LOOP HEADER <<<");
                    case LoopEndNode loopEnd -> {
                        writer.println("  >>> LOOP END <<<");
                        writer.println("  Loop Begin: " + loopEnd.loopBegin().toString());
                    }
                    default -> {
                    }
                }

                writer.println();
            }

            writer.println("=".repeat(80));
            writer.println("CONTROL FLOW GRAPH (CFG Blocks)");
            writer.println("=".repeat(80));
            writer.println();

            for (HIRBlock block : cfg.getBlocks()) {
                writer.println("Block B" + block.getId() + ":");
                writer.println("  Loop Depth: " + block.getLoopDepth());

                writer.print("  Predecessors: ");
                if (block.getPredecessorCount() == 0) {
                    writer.print("(entry)");
                } else {
                    for (int i = 0; i < block.getPredecessorCount(); i++) {
                        if (i > 0) writer.print(", ");
                        writer.print("B" + block.getPredecessorAt(i).getId());
                    }
                }
                writer.println();

                writer.print("  Successors: ");
                if (block.getSuccessorCount() == 0) {
                    writer.print("(exit)");
                } else {
                    for (int i = 0; i < block.getSuccessorCount(); i++) {
                        if (i > 0) writer.print(", ");
                        writer.print("B" + block.getSuccessorAt(i).getId());
                    }
                }
                writer.println();

                writer.println("  Nodes:");
                for (Node node : block.getNodes()) {
                    writer.println("    " + node.toString() + " - " + node.getNodeClass().shortName());
                }
                writer.println();
            }

            writer.println("=".repeat(80));
            writer.println("LOOP ANALYSIS");
            writer.println("=".repeat(80));
            writer.println();

            boolean foundLoop = false;
            for (Node node : graph.getNodes()) {
                if (node instanceof LoopBeginNode loopBegin) {
                    foundLoop = true;
                    writer.println("Loop at node: " + loopBegin.toString());

                    // Find loop end
                    for (Node n : graph.getNodes()) {
                        if (n instanceof LoopEndNode loopEnd && loopEnd.loopBegin() == loopBegin) {
                            writer.println("  Back-edge: " + loopEnd.toString() + " -> " + loopBegin.toString());
                        }
                    }

                    // Find phis
                    writer.println("  Phi nodes:");
                    for (Node usage : loopBegin.usages()) {
                        if (usage instanceof PhiNode phi) {
                            writer.println("    " + phi.toString());
                        }
                    }
                    writer.println();
                }
            }

            if (!foundLoop) {
                writer.println("No loops detected.");
            }

            writer.println("=".repeat(80));
            writer.println("END OF GRAPH EXPORT");
            writer.println("=".repeat(80));
        }
    }

    public void exportToCompact(StructuredGraph graph, ControlFlowGraph cfg, AnalysisMethod method, String outputPath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("Method: " + method.format("%H.%n(%p)"));
            writer.println();

            // Create node ID mapping
            Map<Node, Integer> nodeIds = new HashMap<>();
            int id = 0;
            for (Node node : graph.getNodes()) {
                nodeIds.put(node, id++);
            }

            writer.println("NODES:");
            for (Node node : graph.getNodes()) {
                writer.print("  n" + nodeIds.get(node) + ": " + node.getNodeClass().shortName());

                if (node instanceof PhiNode phi) {
                    writer.print(" [phi, values=" + phi.valueCount() + "]");
                } else if (node instanceof LoopBeginNode) {
                    writer.print(" [LOOP-HEADER]");
                } else if (node instanceof LoopEndNode loopEnd) {
                    writer.print(" [LOOP-END -> n" + nodeIds.get(loopEnd.loopBegin()) + "]");
                }

                writer.println();
            }

            writer.println();
            writer.println("DATA EDGES:");
            for (Node node : graph.getNodes()) {
                for (Node input : node.inputs()) {
                    writer.println("  n" + nodeIds.get(input) + " -> n" + nodeIds.get(node) + " (data)");
                }
            }

            writer.println();
            writer.println("CONTROL EDGES:");
            for (Node node : graph.getNodes()) {
                for (Node succ : node.successors()) {
                    writer.println("  n" + nodeIds.get(node) + " -> n" + nodeIds.get(succ) + " (control)");
                }
            }

            writer.println();
            writer.println("CFG BLOCKS:");
            for (HIRBlock block : cfg.getBlocks()) {
                writer.print("  B" + block.getId() + ": [");
                boolean first = true;
                for (Node n : block.getNodes()) {
                    if (!first) writer.print(", ");
                    writer.print("n" + nodeIds.get(n));
                    first = false;
                }
                writer.print("] -> ");

                if (block.getSuccessorCount() == 0) {
                    writer.print("(exit)");
                } else {
                    for (int i = 0; i < block.getSuccessorCount(); i++) {
                        if (i > 0) writer.print(", ");
                        writer.print("B" + block.getSuccessorAt(i).getId());
                    }
                }
                writer.println();
            }
        }
    }

    public void exportToDot(StructuredGraph graph, AnalysisMethod method, String outputPath) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            writer.println("digraph G {");
            writer.println("  rankdir=TB;");
            writer.println("  node [shape=box];");
            writer.println("  label=\"" + method.format("%H.%n(%p)") + "\";");
            writer.println();

            Map<Node, Integer> nodeIds = new HashMap<>();
            int id = 0;
            for (Node node : graph.getNodes()) {
                nodeIds.put(node, id++);
            }

            for (Node node : graph.getNodes()) {
                String label = node.getNodeClass().shortName();
                String color = "black";
                String style = "solid";

                if (node instanceof PhiNode) {
                    color = "blue";
                    style = "filled";
                } else if (node instanceof LoopBeginNode) {
                    color = "red";
                    style = "filled";
                } else if (node instanceof LoopEndNode) {
                    color = "orange";
                    style = "filled";
                }

                writer.println("  n" + nodeIds.get(node) + " [label=\"" + label + "\\nn" + nodeIds.get(node) +
                        "\", color=" + color + ", style=" + style + "];");
            }

            writer.println();

            for (Node node : graph.getNodes()) {
                for (Node succ : node.successors()) {
                    writer.println("  n" + nodeIds.get(node) + " -> n" + nodeIds.get(succ) + " [style=solid, color=black];");
                }
            }

            for (Node node : graph.getNodes()) {
                for (Node input : node.inputs()) {
                    writer.println("  n" + nodeIds.get(input) + " -> n" + nodeIds.get(node) + " [style=dashed, color=blue];");
                }
            }

            writer.println("}");
        }
    }
}

