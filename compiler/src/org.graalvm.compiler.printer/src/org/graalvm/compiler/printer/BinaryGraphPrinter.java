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
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.core.common.cfg.BlockMap;
import org.graalvm.compiler.debug.Debug;
import org.graalvm.compiler.debug.GraalDebugConfig;
import org.graalvm.compiler.graph.CachedGraph;
import org.graalvm.compiler.graph.Edges;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.InputEdges;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.NodeMap;
import org.graalvm.compiler.nodeinfo.InputType;
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
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.schedule.SchedulePhase;

public class BinaryGraphPrinter extends AbstractGraphPrinter<BinaryGraphPrinter.GraphInfo, Node, Edges, Block>
                implements GraphPrinter {
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
    public String formatTitle(int id, String format, Object... args) {
        return GraphPrinter.super.formatTitle(id, format, args);
    }

    @Override
    ResolvedJavaMethod findMethod(Object object) {
        if (object instanceof Bytecode) {
            return ((Bytecode) object).getMethod();
        } else if (object instanceof ResolvedJavaMethod) {
            return ((ResolvedJavaMethod) object);
        } else {
            return null;
        }
    }

    @Override
    final GraphInfo findGraph(Object obj) {
        if (obj instanceof Graph) {
            return new GraphInfo((Graph) obj);
        } else if (obj instanceof CachedGraph) {
            return new GraphInfo(((CachedGraph<?>) obj).getReadonlyCopy());
        } else {
            return null;
        }
    }

    @Override
    int findNodeId(Node n) {
        return getNodeId(n);
    }

    @Override
    final Edges findEdges(Node node, boolean dumpInputs) {
        NodeClass<?> nodeClass = node.getNodeClass();
        return findEdges(nodeClass, dumpInputs);
    }

    @Override
    final Edges findEdges(NodeClass<?> nodeClass, boolean dumpInputs) {
        return nodeClass.getEdges(dumpInputs ? Inputs : Successors);
    }

    @SuppressWarnings("deprecation")
    private static int getNodeId(Node node) {
        return node == null ? -1 : node.getId();
    }

    @Override
    List<Node> findBlockNodes(GraphInfo info, Block block) {
        return info.blockToNodes.get(block);
    }

    @Override
    int findBlockId(Block sux) {
        return sux.getId();
    }

    @Override
    List<Block> findBlockSuccessors(Block block) {
        return Arrays.asList(block.getSuccessors());
    }

    @Override
    Iterable<Node> findNodes(GraphInfo info) {
        return info.graph.getNodes();
    }

    @Override
    int findNodesCount(GraphInfo info) {
        return info.graph.getNodeCount();
    }

    @Override
    void findNodeProperties(Node node, Map<Object, Object> props, GraphInfo info) {
        node.getDebugProperties(props);
        Graph graph = info.graph;
        ControlFlowGraph cfg = info.cfg;
        NodeMap<Block> nodeToBlocks = info.nodeToBlocks;
        if (cfg != null && GraalDebugConfig.Options.PrintGraphProbabilities.getValue(graph.getOptions()) && node instanceof FixedNode) {
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

    @Override
    void findExtraNodes(Node node, Collection<? super Node> extraNodes) {
        if (node instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) node;
            for (PhiNode phi : merge.phis()) {
                extraNodes.add(phi);
            }
        }
    }

    @Override
    boolean hasPredecessor(Node node) {
        return node.predecessor() != null;
    }

    @Override
    List<Block> findBlocks(GraphInfo graph) {
        return graph.blocks;
    }

    @Override
    NodeClass<?> findNodeClass(Node node) {
        return node.getNodeClass();
    }

    @Override
    public void print(Graph graph, Map<Object, Object> properties, int id, String format, Object... args) throws IOException {
        print(new GraphInfo(graph), properties, 0, format, args);
    }

    @Override
    int findSize(Edges edges) {
        return edges.getCount();
    }

    @Override
    boolean isDirect(Edges edges, int i) {
        return i < edges.getDirectCount();
    }

    @Override
    String findName(Edges edges, int i) {
        return edges.getName(i);
    }

    @Override
    InputType findType(Edges edges, int i) {
        return ((InputEdges) edges).getInputType(i);
    }

    @Override
    Node findNode(Node node, Edges edges, int i) {
        return Edges.getNode(node, edges.getOffsets(), i);
    }

    @Override
    List<Node> findNodes(Node node, Edges edges, int i) {
        return Edges.getNodeList(node, edges.getOffsets(), i);
    }

    static final class GraphInfo {
        final Graph graph;
        final ControlFlowGraph cfg;
        final BlockMap<List<Node>> blockToNodes;
        final NodeMap<Block> nodeToBlocks;
        final List<Block> blocks;

        private GraphInfo(Graph graph) {
            this.graph = graph;
            StructuredGraph.ScheduleResult scheduleResult = null;
            if (graph instanceof StructuredGraph) {

                StructuredGraph structuredGraph = (StructuredGraph) graph;
                scheduleResult = structuredGraph.getLastSchedule();
                if (scheduleResult == null) {

                    // Also provide a schedule when an error occurs
                    if (GraalDebugConfig.Options.PrintGraphWithSchedule.getValue(graph.getOptions()) || Debug.contextLookup(Throwable.class) != null) {
                        try {
                            SchedulePhase schedule = new SchedulePhase(graph.getOptions());
                            schedule.apply(structuredGraph);
                            scheduleResult = structuredGraph.getLastSchedule();
                        } catch (Throwable t) {
                        }
                    }

                }
            }
            cfg = scheduleResult == null ? Debug.contextLookup(ControlFlowGraph.class) : scheduleResult.getCFG();
            blockToNodes = scheduleResult == null ? null : scheduleResult.getBlockToNodesMap();
            nodeToBlocks = scheduleResult == null ? null : scheduleResult.getNodeToBlockMap();
            blocks = cfg == null ? null : Arrays.asList(cfg.getBlocks());
        }
    }

}
