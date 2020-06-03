/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import static org.graalvm.compiler.truffle.runtime.TruffleDebugOptions.PrintGraph;
import static org.graalvm.compiler.truffle.runtime.TruffleDebugOptions.getValue;
import static org.graalvm.compiler.truffle.runtime.TruffleDebugOptions.PrintGraphTarget.Disable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.graphio.GraphBlocks;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeClass;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;
import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;

public final class TruffleTreeDumper {

    private TruffleTreeDumper() {
    }

    private static final ASTDumpStructure AST_DUMP_STRUCTURE = new ASTDumpStructure();
    private static final CallTreeDumpStructure CALL_GRAPH_DUMP_STRUCTURE = new CallTreeDumpStructure();
    private static final String AFTER_PROFILING = "After Profiling";
    private static final String AFTER_INLINING = "After Inlining";

    public static void dump(TruffleDebugContext debug, OptimizedCallTarget callTarget, TruffleInlining inliningDecision) {
        if (getValue(PrintGraph) != Disable) {
            try {
                dumpASTAndCallTrees(debug, callTarget, inliningDecision);
            } catch (IOException ex) {
                throw rethrowSilently(RuntimeException.class, ex);
            }
        }
    }

    private static void dumpASTAndCallTrees(TruffleDebugContext debug, OptimizedCallTarget callTarget, TruffleInlining inlining) throws IOException {
        if (callTarget.getRootNode() != null) {
            AST ast = new AST(callTarget);
            final GraphOutput<AST, ?> astOutput = debug.buildOutput(GraphOutput.newBuilder(AST_DUMP_STRUCTURE).blocks(AST_DUMP_STRUCTURE).protocolVersion(7, 0));

            astOutput.beginGroup(ast, "AST", "AST", null, 0, debug.getVersionProperties());

            astOutput.print(ast, Collections.emptyMap(), 0, AFTER_PROFILING);
            if (inlining.countInlinedCalls() > 0) {
                dumpInlinedTrees(debug, astOutput, callTarget, inlining, new ArrayList<>());
                ast.inline(inlining);
                astOutput.print(ast, null, 1, AFTER_INLINING);
            }
            astOutput.endGroup(); // AST
            astOutput.close();

            if (callTarget.getOptionValue(PolyglotCompilerOptions.LanguageAgnosticInlining)) {
                return;
            }
            CallTree callTree = new CallTree(callTarget, null);
            final GraphOutput<CallTree, ?> callTreeOutput = debug.buildOutput(GraphOutput.newBuilder(CALL_GRAPH_DUMP_STRUCTURE).blocks(CALL_GRAPH_DUMP_STRUCTURE).protocolVersion(7, 0));
            callTreeOutput.beginGroup(null, "Call Tree", "Call Tree", null, 0, debug.getVersionProperties());
            callTreeOutput.print(callTree, null, 0, AFTER_PROFILING);
            if (inlining.countInlinedCalls() > 0) {
                callTree = new CallTree(callTarget, inlining);
                callTreeOutput.print(callTree, null, 0, AFTER_INLINING);
            }
            callTreeOutput.endGroup(); // Call Tree
            callTreeOutput.close();
        }
    }

    private static void dumpInlinedTrees(TruffleDebugContext debug, GraphOutput<AST, ?> output, final RootCallTarget callTarget, TruffleInlining inlining, List<RootCallTarget> dumped)
                    throws IOException {
        for (DirectCallNode callNode : NodeUtil.findAllNodeInstances(callTarget.getRootNode(), DirectCallNode.class)) {
            CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
            if (inlinedCallTarget instanceof RootCallTarget && callNode instanceof OptimizedDirectCallNode) {
                TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                if (decision != null && decision.shouldInline()) {
                    final RootCallTarget rootCallTarget = (RootCallTarget) inlinedCallTarget;
                    if (!dumped.contains(rootCallTarget)) {
                        AST ast = new AST(rootCallTarget);
                        output.beginGroup(ast, inlinedCallTarget.toString(), rootCallTarget.getRootNode().getName(), null, 0, debug.getVersionProperties());
                        output.print(ast, Collections.emptyMap(), 0, AFTER_PROFILING);
                        output.endGroup();
                        dumped.add(rootCallTarget);
                        dumpInlinedTrees(debug, output, (OptimizedCallTarget) inlinedCallTarget, decision, dumped);
                    }
                }
            }
        }
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
        final List<ASTNode> nodes = new ArrayList<>();
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
            nodes.add(astNode);
            return astNode;
        }

        ASTNode findASTNode(Node source) {
            for (ASTNode node : nodes) {
                if (node.source == source) {
                    return node;
                }
            }
            return null;
        }

        ASTBlock makeASTBlock() {
            final ASTBlock astBlock = new ASTBlock(blocks.size());
            blocks.add(astBlock);
            return astBlock;
        }

        void inline(TruffleInlining inliningDecisions) {
            traverseSeenNodes(root.source, root, this, inliningDecisions, blocks.get(0));
        }

        private static void traverseSeenNodes(Node parent, ASTNode astParent, AST ast, TruffleInlining inliningDecisions, ASTBlock currentBlock) {
            for (Map.Entry<String, Node> entry : findNamedNodeChildren(parent).entrySet()) {
                final String label = entry.getKey();
                final Node node = entry.getValue();
                final ASTNode seenAstNode = ast.findASTNode(node);
                if (seenAstNode == null) {
                    final ASTNode astNode = ast.makeASTNode(node);
                    currentBlock.nodes.add(astNode);
                    astParent.edges.add(new ASTEdge(astNode, label));
                    handleCallNodes(ast, inliningDecisions, node, astNode, currentBlock);
                    traverseSeenNodes(node, astNode, ast, inliningDecisions, currentBlock);
                } else {
                    handleCallNodes(ast, inliningDecisions, node, seenAstNode, currentBlock);
                    traverseSeenNodes(node, seenAstNode, ast, inliningDecisions, currentBlock);
                }
            }
        }

        private static void traverseNodes(Node parent, ASTNode astParent, AST ast, TruffleInlining inliningDecisions, ASTBlock currentBlock) {
            for (Map.Entry<String, Node> entry : findNamedNodeChildren(parent).entrySet()) {
                final String label = entry.getKey();
                final Node node = entry.getValue();
                final ASTNode astNode = ast.makeASTNode(node);
                currentBlock.nodes.add(astNode);
                astParent.edges.add(new ASTEdge(astNode, label));
                handleCallNodes(ast, inliningDecisions, node, astNode, currentBlock);
                traverseNodes(node, astNode, ast, inliningDecisions, currentBlock);
            }
        }

        private static void handleCallNodes(AST ast, TruffleInlining inliningDecisions, Node node, ASTNode astNode, ASTBlock currentBlock) {
            // Has this call node been handled already?
            if (astNode.edges.size() > 0) {
                return;
            }
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
                            astNode.setNewClass();
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
        Map<String, ? super Object> properties = new LinkedHashMap<>();
        ASTNodeClass nodeClass;

        ASTNode(Node source, int id) {
            this.source = source;
            this.id = id;
            setNewClass();

            setBasicProperties(properties, source);
            readNodeProperties(this, source);
            copyDebugProperties(this, source);

        }

        private static void setBasicProperties(Map<String, ? super Object> properties, Node source) {
            String className = className(source.getClass());
            properties.put("label", dropNodeSuffix(className));
            properties.put("cost", source.getCost());
            NodeInfo nodeInfo = source.getClass().getAnnotation(NodeInfo.class);
            if (nodeInfo != null) {
                if (!nodeInfo.shortName().isEmpty()) {
                    properties.put("shortName", nodeInfo.shortName());
                }
            }
            if (Introspection.isIntrospectable(source)) {
                final List<Introspection.SpecializationInfo> specializations = Introspection.getSpecializations(source);
                for (Introspection.SpecializationInfo specialization : specializations) {
                    final String methodName = "specialization." + specialization.getMethodName();
                    String state;
                    if (specialization.isActive()) {
                        state = "active";
                    } else if (specialization.isExcluded()) {
                        state = "excluded";
                    } else {
                        state = "inactive";
                    }
                    properties.put(methodName, state);
                    if (specialization.getInstances() > 1 || (specialization.getInstances() == 1 && specialization.getCachedData(0).size() > 0)) {
                        properties.put(methodName + ".instances", specialization.getInstances());
                        for (int instance = 0; instance < specialization.getInstances(); instance++) {
                            final List<Object> cachedData = specialization.getCachedData(instance);
                            int cachedIndex = 0;
                            for (Object o : cachedData) {
                                properties.put(methodName + ".instance[" + instance + "].cached[" + cachedIndex + "]", o);
                                cachedIndex++;
                            }
                        }
                    }
                }
            }
        }

        static String className(Class<?> clazz) {
            String name = clazz.getName();
            return name.substring(name.lastIndexOf('.') + 1);
        }

        private static String dropNodeSuffix(String className) {
            return className.replaceFirst("Node$", "");
        }

        void setNewClass() {
            nodeClass = new ASTNodeClass(this);
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

    static class ASTNodeClass {
        final ASTNode node;

        ASTNodeClass(ASTNode node) {
            this.node = node;
        }
    }

    static class ASTBlock {
        final int id;
        final List<ASTBlock> successors = new ArrayList<>();
        final List<ASTNode> nodes = new ArrayList<>();

        ASTBlock(int id) {
            this.id = id;
        }
    }

    static class ASTDumpStructure implements GraphStructure<AST, ASTNode, ASTNodeClass, List<ASTEdge>>, GraphBlocks<AST, ASTBlock, ASTNode> {

        @Override
        public AST graph(AST currentGraph, Object obj) {
            return obj instanceof AST ? (AST) obj : null;
        }

        @Override
        public Iterable<? extends ASTNode> nodes(AST graph) {
            return graph.nodes;
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
        public ASTNodeClass nodeClass(Object obj) {
            return obj instanceof ASTNodeClass ? ((ASTNodeClass) obj) : null;
        }

        @Override
        public ASTNodeClass classForNode(ASTNode node) {
            return node.nodeClass;
        }

        @Override
        public String nameTemplate(ASTNodeClass nodeClass) {
            return "{p#label}";
        }

        @Override
        public Object nodeClassType(ASTNodeClass nodeClass) {
            return nodeClass.node.source.getClass();
        }

        @Override
        public List<ASTEdge> portInputs(ASTNodeClass nodeClass) {
            return Collections.emptyList();
        }

        @Override
        public List<ASTEdge> portOutputs(ASTNodeClass nodeClass) {
            return nodeClass.node.edges;
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

    static class CallTree {
        final CallTreeNode root;
        final List<CallTreeNode> nodes = new ArrayList<>();
        final CallTreeBlock inlined = new CallTreeBlock(0);
        final CallTreeBlock notInlined = new CallTreeBlock(1);

        CallTree(RootCallTarget target, TruffleInlining inlining) {
            root = makeCallTreeNode(target);
            inlined.nodes.add(root);
            root.properties.put("label", target.toString());
            root.properties.putAll(((OptimizedCallTarget) target).getDebugProperties(null));
            build(target, root, inlining, this);
        }

        private static void build(RootCallTarget target, CallTreeNode parent, TruffleInlining inlining, CallTree graph) {
            if (inlining == null) {
                for (DirectCallNode callNode : NodeUtil.findAllNodeInstances((target).getRootNode(), DirectCallNode.class)) {
                    CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    final CallTreeNode callTreeNode = graph.makeCallTreeNode(inlinedCallTarget);
                    parent.edges.add(new CallTreeEdge(callTreeNode, ""));
                    graph.notInlined.nodes.add(callTreeNode);
                    callTreeNode.properties.put("label", inlinedCallTarget.toString());
                    callTreeNode.properties.put("inlined", "false");
                }
            } else {
                List<RootCallTarget> furtherTargets = new ArrayList<>();
                List<CallTreeNode> furtherParent = new ArrayList<>();
                List<TruffleInlining> furtherDecisions = new ArrayList<>();
                for (DirectCallNode callNode : NodeUtil.findAllNodeInstances((target).getRootNode(), DirectCallNode.class)) {
                    CallTarget inlinedCallTarget = callNode.getCurrentCallTarget();
                    if (inlinedCallTarget instanceof OptimizedCallTarget && callNode instanceof OptimizedDirectCallNode) {
                        TruffleInliningDecision decision = inlining.findByCall((OptimizedDirectCallNode) callNode);
                        final CallTreeNode callTreeNode = graph.makeCallTreeNode(inlinedCallTarget);
                        callTreeNode.properties.put("label", inlinedCallTarget.toString());
                        parent.edges.add(new CallTreeEdge(callTreeNode, ""));
                        if (decision != null && decision.shouldInline()) {
                            graph.inlined.nodes.add(callTreeNode);
                            callTreeNode.properties.put("inlined", "true");
                            callTreeNode.properties.putAll(decision.getProfile().getDebugProperties());
                            furtherTargets.add((RootCallTarget) inlinedCallTarget);
                            furtherParent.add(callTreeNode);
                            furtherDecisions.add(decision);
                        } else {
                            callTreeNode.properties.put("inlined", "false");
                            if (decision != null) {
                                callTreeNode.properties.putAll(decision.getTarget().getDebugProperties(decision));
                            }
                            graph.notInlined.nodes.add(callTreeNode);
                        }
                    }
                }
                for (int i = 0; i < furtherTargets.size(); i++) {
                    build(furtherTargets.get(i), furtherParent.get(i), furtherDecisions.get(i), graph);
                }
            }
        }

        CallTreeNode makeCallTreeNode(CallTarget source) {
            final CallTreeNode callTreeNode = new CallTreeNode(source, nodes.size());
            nodes.add(callTreeNode);
            return callTreeNode;
        }
    }

    static class CallTreeNode {
        final CallTarget source;
        List<CallTreeEdge> edges = new ArrayList<>();
        final int id;
        final Map<String, ? super Object> properties = new HashMap<>();
        final CallTreeClass c = new CallTreeClass();

        CallTreeNode(CallTarget source, int id) {
            this.source = source;
            this.id = id;
        }

        class CallTreeClass {
            CallTreeNode getNode() {
                return CallTreeNode.this;
            }

            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof CallTreeClass)) {
                    return false;
                }
                CallTreeClass other = (CallTreeClass) obj;
                return other.getNode() == CallTreeNode.this;
            }

            @Override
            public int hashCode() {
                return CallTreeNode.this.hashCode();
            }

        }
    }

    static class CallTreeEdge {
        final CallTreeNode target;
        final String label;

        CallTreeEdge(CallTreeNode target, String label) {
            this.target = target;
            this.label = label;
        }
    }

    static class CallTreeBlock {
        final int id;
        final List<CallTreeNode> nodes = new ArrayList<>();

        CallTreeBlock(int id) {
            this.id = id;
        }
    }

    static class CallTreeDumpStructure implements
                    GraphStructure<CallTree, CallTreeNode, CallTreeNode.CallTreeClass, List<CallTreeEdge>>,
                    GraphBlocks<CallTree, CallTreeBlock, CallTreeNode> {

        @Override
        public CallTree graph(CallTree currentGraph, Object obj) {
            return obj instanceof CallTree ? (CallTree) obj : null;
        }

        @Override
        public Iterable<? extends CallTreeNode> nodes(CallTree graph) {
            return graph.nodes;
        }

        @Override
        public int nodesCount(CallTree graph) {
            return graph.nodes.size();
        }

        @Override
        public int nodeId(CallTreeNode node) {
            return node.id;
        }

        @Override
        public boolean nodeHasPredecessor(CallTreeNode node) {
            return false;
        }

        @Override
        public void nodeProperties(CallTree graph, CallTreeNode node, Map<String, ? super Object> properties) {
            properties.putAll(node.properties);
        }

        @Override
        public CallTreeNode node(Object obj) {
            return obj instanceof CallTreeNode ? (CallTreeNode) obj : null;
        }

        @Override
        public CallTreeNode.CallTreeClass nodeClass(Object obj) {
            return obj instanceof CallTreeNode.CallTreeClass ? (CallTreeNode.CallTreeClass) obj : null;
        }

        @Override
        public CallTreeNode.CallTreeClass classForNode(CallTreeNode node) {
            return node.c;
        }

        @Override
        public String nameTemplate(CallTreeNode.CallTreeClass nodeClass) {
            return "{p#label}";
        }

        @Override
        public Object nodeClassType(CallTreeNode.CallTreeClass nodeClass) {
            return nodeClass.getNode().source.getClass();
        }

        @Override
        public List<CallTreeEdge> portInputs(CallTreeNode.CallTreeClass nodeClass) {
            return Collections.emptyList();
        }

        @Override
        public List<CallTreeEdge> portOutputs(CallTreeNode.CallTreeClass nodeClass) {
            return nodeClass.getNode().edges;
        }

        @Override
        public int portSize(List<CallTreeEdge> port) {
            return port.size();
        }

        @Override
        public boolean edgeDirect(List<CallTreeEdge> port, int index) {
            return true;
        }

        @Override
        public String edgeName(List<CallTreeEdge> port, int index) {
            return "";
        }

        @Override
        public Object edgeType(List<CallTreeEdge> port, int index) {
            return port.get(index).label;
        }

        @Override
        public Collection<? extends CallTreeNode> edgeNodes(CallTree graph, CallTreeNode node, List<CallTreeEdge> port, int index) {
            List<CallTreeNode> singleton = new ArrayList<>(1);
            singleton.add(port.get(index).target);
            return singleton;
        }

        @Override
        public Collection<? extends CallTreeBlock> blocks(CallTree graph) {
            return Arrays.asList(graph.inlined, graph.notInlined);
        }

        @Override
        public int blockId(CallTreeBlock block) {
            return block.id;
        }

        @Override
        public Collection<? extends CallTreeNode> blockNodes(CallTree info, CallTreeBlock block) {
            return block.nodes;
        }

        @Override
        public Collection<? extends CallTreeBlock> blockSuccessors(CallTreeBlock block) {
            return Collections.emptyList();
        }
    }

}
