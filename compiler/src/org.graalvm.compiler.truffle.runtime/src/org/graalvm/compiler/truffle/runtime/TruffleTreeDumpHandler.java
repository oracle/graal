/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;


import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugDumpHandler;
import org.graalvm.compiler.debug.DebugOptions;
import org.graalvm.compiler.options.OptionValues;

import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;
import org.graalvm.graphio.GraphBlocks;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TruffleTreeDumpHandler implements DebugDumpHandler {

    private final OptionValues options;

    /**
     * The {@link OptimizedCallTarget} is dumped multiple times during Graal compilation, because it
     * is also a subclass of InstalledCode. To disambiguate dumping, we wrap the call target into
     * this class when we want to dump the Truffle tree.
     */
    static class TruffleTreeDump {
        final RootCallTarget callTarget;
        final TruffleInlining inlining;

        TruffleTreeDump(OptimizedCallTarget callTarget, TruffleInlining inliningDecision) {
            this.callTarget = callTarget;
            this.inlining = inliningDecision;
        }
    }

    public TruffleTreeDumpHandler(OptionValues options) {
        this.options = options;
    }

    @Override
    public void dump(DebugContext debug, Object object, final String format, Object... arguments) {
        if (object instanceof TruffleTreeDump && DebugOptions.PrintGraph.getValue(options) && TruffleCompilerOptions.getValue(DebugOptions.PrintTruffleTrees)) {
            try {
                dumpASTAndCallTrees(debug, (TruffleTreeDump) object);
            } catch (IOException ex) {
                throw rethrowSilently(RuntimeException.class, ex);
            }
        }
    }

    private static final ASTDumpStructure AST_DUMP_STRUCTURE = new ASTDumpStructure();
    private static final CallGraphDumpStructure CALL_GRAPH_DUMP_STRUCTURE = new CallGraphDumpStructure();
    private static final String AFTER_PROFILING = "After Profiling";
    private static final String AFTER_INLINING = "After Inlining";

    private static void dumpASTAndCallTrees(DebugContext debug, TruffleTreeDump truffleTreeDump) throws IOException {
        final RootCallTarget callTarget = truffleTreeDump.callTarget;
        if (callTarget.getRootNode() != null && callTarget instanceof OptimizedCallTarget) {
            AST ast = new AST(callTarget);
            final GraphOutput<AST, ?> astOutput = debug.buildOutput(GraphOutput.newBuilder(AST_DUMP_STRUCTURE).blocks(AST_DUMP_STRUCTURE));

            astOutput.beginGroup(ast, "TruffleAST", "TruffleAST", null, 0, DebugContext.addVersionProperties(null));

            astOutput.print(ast, Collections.emptyMap(), 0, AFTER_PROFILING);
            final TruffleInlining inlining = truffleTreeDump.inlining;
            if (inlining.countInlinedCalls() > 0) {
                dumpInlinedTrees(astOutput, (OptimizedCallTarget) callTarget, inlining, new ArrayList<>());
                // TODO why is this needed? It would be preferred to just call inline on the old graph.
                ast = new AST(callTarget);
                ast.inline(truffleTreeDump.inlining);
                astOutput.print(ast, null, 1, AFTER_INLINING);
            }
            astOutput.endGroup(); // TruffleAST
            astOutput.close();

            CallGraph callGraph = new CallGraph(truffleTreeDump.callTarget, null);
            final GraphOutput<CallGraph, ?> callGraphOutput = debug.buildOutput(GraphOutput.newBuilder(CALL_GRAPH_DUMP_STRUCTURE).blocks(CALL_GRAPH_DUMP_STRUCTURE));
            callGraphOutput.beginGroup(null, "TruffleCallGraph", "TruffleCallGraph", null, 0, DebugContext.addVersionProperties(null));
            callGraphOutput.print(callGraph, null, 0, AFTER_PROFILING);
            callGraph = new CallGraph(truffleTreeDump.callTarget, truffleTreeDump.inlining);
            callGraphOutput.print(callGraph, null, 0, AFTER_INLINING);
            callGraphOutput.endGroup(); // TruffleCallGraph
            callGraphOutput.close();


        }
    }

    private static void dumpInlinedTrees(GraphOutput<AST, ?> output, final OptimizedCallTarget callTarget, TruffleInlining inlining, List<RootCallTarget> dumped) throws IOException {
        if (dumped.contains(callTarget)) {
            return;
        }
        for (DirectCallNode callNode : NodeUtil.findAllNodeInstances(callTarget.getRootNode(), DirectCallNode.class)) {
            CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
            if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                if (decision != null && decision.shouldInline()) {
                    final RootCallTarget rootCallTarget = (RootCallTarget) inlinedCallTarget;
                    AST ast = new AST(rootCallTarget);
                    output.beginGroup(ast, inlinedCallTarget.toString(), rootCallTarget.getRootNode().getName(), null, 0, DebugContext.addVersionProperties(null));
                    output.print(ast, Collections.emptyMap(), 0, AFTER_PROFILING);
                    output.endGroup();
                    dumped.add(rootCallTarget);
                    dumpInlinedTrees(output, (OptimizedCallTarget) inlinedCallTarget, decision, dumped);
                }
            }
        }
    }

    @Override
    public void close() {
        // nothing to do
    }

    @SuppressWarnings({"unused", "unchecked"})
    private static <E extends Exception> E rethrowSilently(Class<E> type, Throwable ex) throws E {
        throw (E) ex;
    }

    @SuppressWarnings("deprecation")
    private static void readNodeProperties(ASTNode astNode, Node node) {
        NodeClass nodeClass = NodeClass.get(node);
        for (com.oracle.truffle.api.nodes.NodeFieldAccessor field : findNodeFields(nodeClass)) {
            if (isDataField(nodeClass, field)) {
                String key = findFieldName(nodeClass, field);
                Object value = findFieldValue(nodeClass, field, node);
                astNode.properties.put(key, value);
            }
        }
    }

    private static void copyDebugProperties(ASTNode astNode, Node node) {
        Map<String, Object> debugProperties = node.getDebugProperties();
        for (Map.Entry<String, Object> property : debugProperties.entrySet()) {
            astNode.properties.put(property.getKey(), property.getValue());
        }
    }

    @SuppressWarnings("deprecation")
    private static LinkedHashMap<String, Node> findNamedNodeChildren(Node node) {
        LinkedHashMap<String, Node> nodes = new LinkedHashMap<>();
        NodeClass nodeClass = NodeClass.get(node);

        for (com.oracle.truffle.api.nodes.NodeFieldAccessor field : findNodeFields(nodeClass)) {
            if (isChildField(nodeClass, field)) {
                Object value = findFieldObject(nodeClass, field, node);
                if (value != null) {
                    nodes.put(findFieldName(nodeClass, field), (Node) value);
                }
            } else if (isChildrenField(nodeClass, field)) {
                Object value = findFieldObject(nodeClass, field, node);
                if (value != null) {
                    Object[] children = (Object[]) value;
                    for (int i = 0; i < children.length; i++) {
                        if (children[i] != null) {
                            nodes.put(findFieldName(nodeClass, field) + "[" + i + "]", (Node) children[i]);
                        }
                    }
                }
            }
        }

        return nodes;
    }

    @SuppressWarnings({"deprecation", "unused"})
    private static Object findFieldValue(NodeClass nodeClass, com.oracle.truffle.api.nodes.NodeFieldAccessor field, Node node) {
        return field.loadValue(node);
    }

    @SuppressWarnings("deprecation")
    private static Iterable<com.oracle.truffle.api.nodes.NodeFieldAccessor> findNodeFields(NodeClass nodeClass) {
        return Arrays.asList(nodeClass.getFields());
    }

    @SuppressWarnings({"deprecation", "unused"})
    private static boolean isChildField(NodeClass nodeClass, com.oracle.truffle.api.nodes.NodeFieldAccessor field) {
        return field.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILD;
    }

    @SuppressWarnings({"deprecation", "unused"})
    private static boolean isChildrenField(NodeClass nodeClass, com.oracle.truffle.api.nodes.NodeFieldAccessor field) {
        return field.getKind() == com.oracle.truffle.api.nodes.NodeFieldAccessor.NodeFieldKind.CHILDREN;
    }

    @SuppressWarnings({"deprecation", "unused"})
    private static Object findFieldObject(NodeClass nodeClass, com.oracle.truffle.api.nodes.NodeFieldAccessor field, Node node) {
        return field.getObject(node);
    }

    @SuppressWarnings({"deprecation", "unused"})
    private static String findFieldName(NodeClass nodeClass, com.oracle.truffle.api.nodes.NodeFieldAccessor field) {
        return field.getName();
    }

    @SuppressWarnings("deprecation")
    private static boolean isDataField(NodeClass nodeClass, com.oracle.truffle.api.nodes.NodeFieldAccessor field) {
        return !isChildField(nodeClass, field) && !isChildrenField(nodeClass, field);
    }

    static class AST {
        final ASTNode root;
        final Map<Node, ASTNode> nodes = new HashMap<>();
        final List<ASTBlock> blocks = new ArrayList<>();

        AST(RootCallTarget target) {
            final ASTBlock astBlock = makeASTBlock();
            final RootNode rootNode = target.getRootNode();
            root = makeASTNode(rootNode);
            astBlock.nodes.add(root);
            traverseNodes(rootNode, root, this, null, astBlock);
        }

        ASTNode makeASTNode(Node source) {
            final ASTNode astNode = new ASTNode(source, nodes.size());
            nodes.put(source, astNode);
            return astNode;
        }

        ASTBlock makeASTBlock() {
            final ASTBlock astBlock = new ASTBlock(blocks.size());
            blocks.add(astBlock);
            return astBlock;
        }

        void inline(TruffleInlining inliningDecisions) {
            traverseNodes(root.source, root, this, inliningDecisions, blocks.get(0));
        }

        private static void traverseNodes(Node parent, ASTNode astParent, AST ast, TruffleInlining inliningDecisions, ASTBlock currentBlock) {
            for (Map.Entry<String, Node> entry : findNamedNodeChildren(parent).entrySet()) {
                final String label = entry.getKey();
                final Node node = entry.getValue();
                final ASTNode seenAstNode = ast.nodes.get(node);
                if (seenAstNode == null) {
                    final ASTNode astNode = ast.makeASTNode(node);
                    currentBlock.nodes.add(astNode);
                    astParent.edges.add(new ASTEdge(astNode, label));
                    handleCallNodes(ast, inliningDecisions, node, astNode, currentBlock);
                    traverseNodes(node, astNode, ast, inliningDecisions, currentBlock);
                } else {
                    handleCallNodes(ast, inliningDecisions, node, seenAstNode, currentBlock);
                    traverseNodes(node, seenAstNode, ast, inliningDecisions, currentBlock);
                }
            }
        }

        private static void handleCallNodes(AST ast, TruffleInlining inliningDecisions, Node node, ASTNode astNode, ASTBlock currentBlock) {
            if (inliningDecisions != null) {
                if (node instanceof DirectCallNode) {
                    final DirectCallNode callNode = (DirectCallNode) node;
                    final CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                        TruffleInliningDecision decision = inliningDecisions.findByCall((OptimizedDirectCallNode) callNode);
                        if (decision != null && decision.shouldInline()) {
                            final RootNode targetRootNode = ((OptimizedCallTarget) inlinedCallTarget).getRootNode();
                            final ASTNode astTargetRootNode = ast.makeASTNode(targetRootNode);
                            astNode.edges.add(new ASTEdge(astTargetRootNode, inlinedCallTarget.toString()));
                            final ASTBlock newBlock = ast.makeASTBlock();
                            if (currentBlock != null) {
                                currentBlock.successors.add(newBlock);
                            }
                            newBlock.nodes.add(astTargetRootNode);
                            traverseNodes(targetRootNode, astTargetRootNode, ast, decision, newBlock);
                        }
                    }
                }
            }
        }
    }

    static class ASTNode {
        Node source;
        List<ASTEdge> edges = new ArrayList<>();
        final int id;
        Map<String, ? super Object> properties = new HashMap<>();

        ASTNode(Node source, int id) {
            this.source = source;
            this.id = id;
            String className = className(source.getClass());
            properties.put("label", dropNodeSuffix(className));
            NodeInfo nodeInfo = source.getClass().getAnnotation(NodeInfo.class);
            if (nodeInfo != null) {
                properties.put("cost", nodeInfo.cost());
                if (!nodeInfo.shortName().isEmpty()) {
                    properties.put("shortName", nodeInfo.shortName());
                }
            }

            readNodeProperties(this, source);
            copyDebugProperties(this, source);

        }

        static String className(Class<?> clazz) {
            String name = clazz.getName();
            return name.substring(name.lastIndexOf('.') + 1);
        }

        private static String dropNodeSuffix(String className) {
            return className.replaceFirst("Node$", "");
        }
    }

    static class ASTEdge {
        final ASTNode node;
        final String label;

        ASTEdge(ASTNode node, String label) {
            this.node = node;
            this.label = label;
        }
    }

    enum EdgeType {
        EDGE_TYPE;
    }

    static class ASTBlock {
        final int id;
        final List<ASTBlock> successors = new ArrayList<>();
        final List<ASTNode> nodes = new ArrayList<>();

        ASTBlock(int id) {
            this.id = id;
        }
    }

    static class ASTDumpStructure implements GraphStructure<AST, ASTNode, ASTNode, List<ASTEdge>>, GraphBlocks<AST, ASTBlock, ASTNode> {

        @Override
        public AST graph(AST currentGraph, Object obj) {
            return obj instanceof AST ? (AST) obj : null;
        }

        @Override
        public Iterable<? extends ASTNode> nodes(AST graph) {
            return graph.nodes.values();
        }

        @Override
        public int nodesCount(AST graph) {
            return graph.nodes.size();
        }

        @Override
        public int nodeId(ASTNode node) {
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(ASTNode node) {
            return false;
        }

        @Override
        public void nodeProperties(AST graph, ASTNode node, Map<String, ? super Object> properties) {
            properties.putAll(node.properties);
        }

        @Override
        public ASTNode node(Object obj) {
            return obj instanceof ASTNode ? (ASTNode) obj : null;
        }

        @Override
        public ASTNode nodeClass(Object obj) {
            return obj instanceof ASTNode ? (ASTNode) obj : null;
        }

        @Override
        public ASTNode classForNode(ASTNode node) {
            return node;
        }

        @Override
        public String nameTemplate(ASTNode nodeClass) {
            return "{p#label}";
        }

        @Override
        public Object nodeClassType(ASTNode nodeClass) {
            return nodeClass.source.getClass();
        }

        @Override
        public List<ASTEdge> portInputs(ASTNode nodeClass) {
            return Collections.emptyList();
        }

        @Override
        public List<ASTEdge> portOutputs(ASTNode nodeClass) {
            return nodeClass.edges;
        }

        @Override
        public int portSize(List<ASTEdge> port) {
            return port.size();
        }

        @Override
        public boolean edgeDirect(List<ASTEdge> port, int index) {
            return true;
        }

        @Override
        public String edgeName(List<ASTEdge> port, int index) {
            return port.get(index).label;
        }

        @Override
        public Object edgeType(List<ASTEdge> port, int index) {
            return EdgeType.EDGE_TYPE;
        }

        @Override
        public Collection<? extends ASTNode> edgeNodes(AST graph, ASTNode node, List<ASTEdge> port, int index) {
            List<ASTNode> singleton = new ArrayList<>(1);
            singleton.add(port.get(index).node);
            return singleton;
        }

        @Override
        public Collection<? extends ASTBlock> blocks(AST graph) {
            return graph.blocks;
        }

        @Override
        public int blockId(ASTBlock block) {
            return block.id;
        }

        @Override
        public Collection<? extends ASTNode> blockNodes(AST info, ASTBlock block) {
            return block.nodes;
        }

        @Override
        public Collection<? extends ASTBlock> blockSuccessors(ASTBlock block) {
            return block.successors;
        }
    }

    static class CallGraph {
        final CallGraphNode root;
        final List<CallGraphNode> nodes = new ArrayList<>();
        final CallGraphBlock inlined = new CallGraphBlock(0);
        final CallGraphBlock notInlined = new CallGraphBlock(1);

        CallGraph(RootCallTarget target, TruffleInlining inlining) {
            root = makeCallGraphNode(target);
            inlined.nodes.add(root);
            root.properties.put("label", target.toString());
            build(target, root, inlining, this);
        }

        private static void build(RootCallTarget target, CallGraphNode parent, TruffleInlining inlining, CallGraph graph) {
            if (inlining == null) {
                for (DirectCallNode callNode : NodeUtil.findAllNodeInstances((target).getRootNode(), DirectCallNode.class)) {
                    CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    final CallGraphNode callGraphNode = graph.makeCallGraphNode(inlinedCallTarget);
                    callGraphNode.properties.put("label", inlinedCallTarget.toString());
                    parent.edges.add(new CallGraphEdge(callGraphNode, ""));
                    callGraphNode.properties.put("inlined", "no");
                    graph.notInlined.nodes.add(callGraphNode);
                }
            } else {
                List<RootCallTarget> furtherTargets = new ArrayList<>();
                List<CallGraphNode> furtherParent = new ArrayList<>();
                List<TruffleInlining> furtherDecisions = new ArrayList<>();
                for (DirectCallNode callNode : NodeUtil.findAllNodeInstances((target).getRootNode(), DirectCallNode.class)) {
                    CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                        TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                        final CallGraphNode callGraphNode = graph.makeCallGraphNode(inlinedCallTarget);
                        callGraphNode.properties.put("label", inlinedCallTarget.toString());
                        parent.edges.add(new CallGraphEdge(callGraphNode, ""));
                        if (decision != null && decision.shouldInline()) {
                            graph.inlined.nodes.add(callGraphNode);
                            callGraphNode.properties.put("inlined", "yes");
                            furtherTargets.add((RootCallTarget) inlinedCallTarget);
                            furtherParent.add(callGraphNode);
                            furtherDecisions.add(decision);
                        } else {
                            callGraphNode.properties.put("inlined", "no");
                            graph.notInlined.nodes.add(callGraphNode);
                        }
                    }
                }
                for (int i = 0; i < furtherTargets.size(); i++) {
                    build(furtherTargets.get(i), furtherParent.get(i), furtherDecisions.get(i), graph);
                }
            }
        }

        CallGraphNode makeCallGraphNode(CallTarget source) {
            final CallGraphNode callGraphNode = new CallGraphNode(source, nodes.size());
            nodes.add(callGraphNode);
            return callGraphNode;
        }
    }

    static class CallGraphNode {
        final CallTarget source;
        List<CallGraphEdge> edges = new ArrayList<>();
        final int id;
        final Map<String, ? super Object> properties = new HashMap<>();

        CallGraphNode(CallTarget source, int id) {
            this.source = source;
            this.id = id;
        }
    }

    static class CallGraphEdge {
        final CallGraphNode target;
        final String label;

        CallGraphEdge(CallGraphNode target, String label) {
            this.target = target;
            this.label = label;
        }
    }

    static class CallGraphBlock {
        final int id;
        final List<CallGraphNode> nodes = new ArrayList<>();

        CallGraphBlock(int id) {
            this.id = id;
        }
    }

    static class CallGraphDumpStructure implements
            GraphStructure<CallGraph, CallGraphNode, CallGraphNode, List<CallGraphEdge>>,
            GraphBlocks<CallGraph, CallGraphBlock, CallGraphNode> {

        @Override
        public CallGraph graph(CallGraph currentGraph, Object obj) {
            return obj instanceof CallGraph ? (CallGraph) obj : null;
        }

        @Override
        public Iterable<? extends CallGraphNode> nodes(CallGraph graph) {
            return graph.nodes;
        }

        @Override
        public int nodesCount(CallGraph graph) {
            return graph.nodes.size();
        }

        @Override
        public int nodeId(CallGraphNode node) {
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(CallGraphNode node) {
            return false;
        }

        @Override
        public void nodeProperties(CallGraph graph, CallGraphNode node, Map<String, ? super Object> properties) {
            properties.putAll(node.properties);
        }

        @Override
        public CallGraphNode node(Object obj) {
            return obj instanceof CallGraphNode ? (CallGraphNode) obj : null;
        }

        @Override
        public CallGraphNode nodeClass(Object obj) {
            return obj instanceof CallGraphNode ? (CallGraphNode) obj : null;
        }

        @Override
        public CallGraphNode classForNode(CallGraphNode node) {
            return node;
        }

        @Override
        public String nameTemplate(CallGraphNode nodeClass) {
            return "{p#label}";
        }

        @Override
        public Object nodeClassType(CallGraphNode nodeClass) {
            return nodeClass.source.getClass();
        }

        @Override
        public List<CallGraphEdge> portInputs(CallGraphNode nodeClass) {
            return Collections.emptyList();
        }

        @Override
        public List<CallGraphEdge> portOutputs(CallGraphNode nodeClass) {
            return nodeClass.edges;
        }

        @Override
        public int portSize(List<CallGraphEdge> port) {
            return port.size();
        }

        @Override
        public boolean edgeDirect(List<CallGraphEdge> port, int index) {
            return true;
        }

        @Override
        public String edgeName(List<CallGraphEdge> port, int index) {
            return "";
        }

        @Override
        public Object edgeType(List<CallGraphEdge> port, int index) {
            return port.get(index).label;
        }

        @Override
        public Collection<? extends CallGraphNode> edgeNodes(CallGraph graph, CallGraphNode node, List<CallGraphEdge> port, int index) {
            List<CallGraphNode> singleton = new ArrayList<>(1);
            singleton.add(port.get(index).target);
            return singleton;
        }

        @Override
        public Collection<? extends CallGraphBlock> blocks(CallGraph graph) {
            return Arrays.asList(graph.inlined, graph.notInlined);
        }

        @Override
        public int blockId(CallGraphBlock block) {
            return block.id;
        }

        @Override
        public Collection<? extends CallGraphNode> blockNodes(CallGraph info, CallGraphBlock block) {
            return block.nodes;
        }

        @Override
        public Collection<? extends CallGraphBlock> blockSuccessors(CallGraphBlock block) {
            return Collections.emptyList();
        }
    }

}
