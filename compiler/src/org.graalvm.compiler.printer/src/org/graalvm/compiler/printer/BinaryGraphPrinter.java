/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import static org.graalvm.compiler.graph.Edges.Type.Inputs;
import static org.graalvm.compiler.graph.Edges.Type.Successors;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalDebugConfig.Options;
import org.graalvm.compiler.graph.CachedGraph;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.InputEdges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeList;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.ScheduleResult;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;
import org.graalvm.compiler.graph.NodeSourcePosition;

public class BinaryGraphPrinter extends AbstractGraphPrinter implements GraphPrinter {
    private SnippetReflectionProvider snippetReflection;

    public BinaryGraphPrinter(WritableByteChannel channel) throws IOException {
        super(channel);
    }

    @Override
    public void setSnippetReflectionProvider(SnippetReflectionProvider snippetReflection) {
        this.snippetReflection = snippetReflection;
    }

    @Override
    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflection;
    }

    @Override
    void writeGraph(Graph graph, Map<Object, Object> properties) throws IOException {
        ScheduleResult scheduleResult = null;
        if (graph instanceof StructuredGraph) {

            StructuredGraph structuredGraph = (StructuredGraph) graph;
            scheduleResult = structuredGraph.getLastSchedule();
            if (scheduleResult == null) {

                // Also provide a schedule when an error occurs
                if (Options.PrintGraphWithSchedule.getValue(graph.getOptions()) || Debug.contextLookup(Throwable.class) != null) {
                    try {
                        SchedulePhase schedule = new SchedulePhase(graph.getOptions());
                        schedule.apply(structuredGraph);
                        scheduleResult = structuredGraph.getLastSchedule();
                    } catch (Throwable t) {
                    }
                }

            }
        }
        ControlFlowGraph cfg = scheduleResult == null ? Debug.contextLookup(ControlFlowGraph.class) : scheduleResult.getCFG();
        BlockMap<List<Node>> blockToNodes = scheduleResult == null ? null : scheduleResult.getBlockToNodesMap();
        NodeMap<Block> nodeToBlocks = scheduleResult == null ? null : scheduleResult.getNodeToBlockMap();
        List<Block> blocks = cfg == null ? null : Arrays.asList(cfg.getBlocks());
        writeProperties(properties);
        writeNodes(graph, nodeToBlocks, cfg);
        writeBlocks(blocks, blockToNodes);
    }

    @SuppressWarnings("deprecation")
    private static int getNodeId(Node node) {
        return node.getId();
    }

    private Object getBlockForNode(Node node, NodeMap<Block> nodeToBlocks) {
        if (nodeToBlocks.isNew(node)) {
            return "NEW (not in schedule)";
        } else {
            Block block = nodeToBlocks.get(node);
            if (block != null) {
                return block.getId();
            } else if (node instanceof PhiNode) {
                return getBlockForNode(((PhiNode) node).merge(), nodeToBlocks);
            }
        }
        return null;
    }

    private void writeNodes(Graph graph, NodeMap<Block> nodeToBlocks, ControlFlowGraph cfg) throws IOException {
        Map<Object, Object> props = new HashMap<>();

        writeInt(graph.getNodeCount());

        for (Node node : graph.getNodes()) {
            NodeClass<?> nodeClass = node.getNodeClass();
            node.getDebugProperties(props);
            if (cfg != null && Options.PrintGraphProbabilities.getValue(graph.getOptions()) && node instanceof FixedNode) {
                try {
                    props.put("probability", cfg.blockFor(node).probability());
                } catch (Throwable t) {
                    props.put("probability", 0.0);
                    props.put("probability-exception", t);
                }
            }

            try {
                props.put("NodeCost-Size", node.estimatedNodeSize());
                props.put("NodeCost-Cycles", node.estimatedNodeCycles());
            } catch (Throwable t) {
                props.put("node-cost-exception", t.getMessage());
            }

            if (nodeToBlocks != null) {
                Object block = getBlockForNode(node, nodeToBlocks);
                if (block != null) {
                    props.put("node-to-block", block);
                }
            }

            if (node instanceof ControlSinkNode) {
                props.put("category", "controlSink");
            } else if (node instanceof ControlSplitNode) {
                props.put("category", "controlSplit");
            } else if (node instanceof AbstractMergeNode) {
                props.put("category", "merge");
            } else if (node instanceof AbstractBeginNode) {
                props.put("category", "begin");
            } else if (node instanceof AbstractEndNode) {
                props.put("category", "end");
            } else if (node instanceof FixedNode) {
                props.put("category", "fixed");
            } else if (node instanceof VirtualState) {
                props.put("category", "state");
            } else if (node instanceof PhiNode) {
                props.put("category", "phi");
            } else if (node instanceof ProxyNode) {
                props.put("category", "proxy");
            } else {
                if (node instanceof ConstantNode) {
                    ConstantNode cn = (ConstantNode) node;
                    updateStringPropertiesForConstant(props, cn);
                }
                props.put("category", "floating");
            }

            writeInt(getNodeId(node));
            writePoolObject(nodeClass);
            writeByte(node.predecessor() == null ? 0 : 1);
            writeProperties(props);
            writeEdges(node, Inputs);
            writeEdges(node, Successors);

            props.clear();
        }
    }

    private void writeEdges(Node node, Edges.Type type) throws IOException {
        NodeClass<?> nodeClass = node.getNodeClass();
        Edges edges = nodeClass.getEdges(type);
        final long[] curOffsets = edges.getOffsets();
        for (int i = 0; i < edges.getDirectCount(); i++) {
            writeNodeRef(Edges.getNode(node, curOffsets, i));
        }
        for (int i = edges.getDirectCount(); i < edges.getCount(); i++) {
            NodeList<Node> list = Edges.getNodeList(node, curOffsets, i);
            if (list == null) {
                writeShort((char) 0);
            } else {
                int listSize = list.count();
                assert listSize == ((char) listSize);
                writeShort((char) listSize);
                for (Node edge : list) {
                    writeNodeRef(edge);
                }
            }
        }
    }

    private void writeNodeRef(Node edge) throws IOException {
        if (edge != null) {
            writeInt(getNodeId(edge));
        } else {
            writeInt(-1);
        }
    }

    private void writeBlocks(List<Block> blocks, BlockMap<List<Node>> blockToNodes) throws IOException {
        if (blocks != null && blockToNodes != null) {
            for (Block block : blocks) {
                List<Node> nodes = blockToNodes.get(block);
                if (nodes == null) {
                    writeInt(0);
                    return;
                }
            }
            writeInt(blocks.size());
            for (Block block : blocks) {
                List<Node> nodes = blockToNodes.get(block);
                List<Node> extraNodes = new LinkedList<>();
                writeInt(block.getId());
                for (Node node : nodes) {
                    if (node instanceof AbstractMergeNode) {
                        AbstractMergeNode merge = (AbstractMergeNode) node;
                        for (PhiNode phi : merge.phis()) {
                            if (!nodes.contains(phi)) {
                                extraNodes.add(phi);
                            }
                        }
                    }
                }
                writeInt(nodes.size() + extraNodes.size());
                for (Node node : nodes) {
                    writeInt(getNodeId(node));
                }
                for (Node node : extraNodes) {
                    writeInt(getNodeId(node));
                }
                writeInt(block.getSuccessors().length);
                for (Block sux : block.getSuccessors()) {
                    writeInt(sux.getId());
                }
            }
        } else {
            writeInt(0);
        }
    }

}
