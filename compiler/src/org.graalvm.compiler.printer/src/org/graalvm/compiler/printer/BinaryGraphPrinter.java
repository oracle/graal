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
import java.util.List;
import java.util.Map;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.bytecode.Bytecode;
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
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;

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
    final Graph findGraph(Object obj) {
        if (obj instanceof Graph) {
            return (Graph) obj;
        } else if (obj instanceof CachedGraph) {
            return ((CachedGraph<?>) obj).getReadonlyCopy();
        } else {
            return null;
        }
    }

    @Override
    int findNodeId(Node n) {
        return getNodeId(n);
    }

    @Override
    final void findEdges(
                    NodeClass<?> nodeClass,
                    boolean dumpInputs,
                    List<String> names, List<Boolean> direct, List<InputType> types) {
        Edges edges = nodeClass.getEdges(dumpInputs ? Inputs : Successors);
        for (int i = 0; i < edges.getCount(); i++) {
            direct.add(i < edges.getDirectCount());
            names.add(edges.getName(i));
            if (dumpInputs) {
                types.add(((InputEdges) edges).getInputType(i));
            }
        }
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
    protected Iterable<Node> findNodes(GraphInfo info) {
        return info.graph.getNodes();
    }

    @Override
    protected int findNodesCount(GraphInfo info) {
        return info.graph.getNodeCount();
    }

    @Override
    protected void findNodeProperties(Node node, Map<Object, Object> props, GraphInfo info) {
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
}
