/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleDebugContext;
import org.graalvm.compiler.truffle.common.TruffleSourceLanguagePosition;
import org.graalvm.graphio.GraphBlocks;
import org.graalvm.graphio.GraphOutput;
import org.graalvm.graphio.GraphStructure;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Introspection;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.RootNode;

public final class TruffleTreeDumper {

    private TruffleTreeDumper() {
    }

    private static final ASTDumpStructure AST_DUMP_STRUCTURE = new ASTDumpStructure();

    public static void dump(TruffleDebugContext debug, OptimizedCallTarget callTarget) {
        if (GraalTruffleRuntime.getRuntime().isPrintGraphEnabled()) {
            try {
                dumpAST(debug, callTarget, new TruffleNodeSources());
            } catch (IOException ex) {
                throw new RuntimeException("Failed to dump AST: " + callTarget, ex);
            }
        }
    }

    public static void dump(TruffleDebugContext debug, OptimizedCallTarget root, TruffleInlining inlining) {
        if (GraalTruffleRuntime.getRuntime().isPrintGraphEnabled()) {
            try {
                CompilableTruffleAST[] inlinedTargets = inlining.inlinedTargets();
                Set<CompilableTruffleAST> uniqueTargets = new HashSet<>(Arrays.asList(inlinedTargets));
                uniqueTargets.remove(root);
                dumpInlinedASTs(debug, uniqueTargets, new TruffleNodeSources());
            } catch (IOException ex) {
                throw new RuntimeException("Failed to dump Inlined ASTs: ", ex);
            }
        }
    }

    private static void dumpInlinedASTs(TruffleDebugContext debug, Set<CompilableTruffleAST> inlinedTargets, TruffleNodeSources nodeSources) throws IOException {
        final GraphOutput<AST, ?> astOutput = debug.buildOutput(GraphOutput.newBuilder(AST_DUMP_STRUCTURE).blocks(AST_DUMP_STRUCTURE));
        astOutput.beginGroup(null, "Inlined ASTs", "Inlined", null, 0, debug.getVersionProperties());
        for (CompilableTruffleAST target : inlinedTargets) {
            AST ast = new AST((RootCallTarget) target, nodeSources);
            astOutput.print(ast, Collections.emptyMap(), 0, target.getName());
        }
        astOutput.endGroup(); // Inlined
        astOutput.close();
    }

    private static void dumpAST(TruffleDebugContext debug, OptimizedCallTarget callTarget, TruffleNodeSources nodeSources) throws IOException {
        if (callTarget.getRootNode() != null) {
            AST ast = new AST(callTarget, nodeSources);
            final GraphOutput<AST, ?> astOutput = debug.buildOutput(GraphOutput.newBuilder(AST_DUMP_STRUCTURE).blocks(AST_DUMP_STRUCTURE));
            astOutput.print(ast, Collections.emptyMap(), 0, callTarget.getName());
            astOutput.close();
        }
    }

    private static void readNodeProperties(ASTNode astNode, Node node) {
        astNode.properties.putAll(NodeUtil.collectNodeProperties(node));

    }

    private static void copyDebugProperties(ASTNode astNode, Node node) {
        Map<String, Object> debugProperties = node.getDebugProperties();
        for (Map.Entry<String, Object> property : debugProperties.entrySet()) {
            astNode.properties.put(property.getKey(), property.getValue());
        }
    }

    static class AST {
        final ASTNode root;
        final EconomicMap<Node, ASTNode> nodes = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        final List<ASTBlock> blocks = new ArrayList<>();

        AST(RootCallTarget target, TruffleNodeSources nodeSources) {
            final ASTBlock astBlock = makeASTBlock();
            final RootNode rootNode = target.getRootNode();
            root = makeASTNode(rootNode, nodeSources);
            astBlock.nodes.add(root);
            traverseNodes(rootNode, root, this, null, nodeSources, astBlock);
        }

        ASTNode makeASTNode(Node source, TruffleNodeSources nodeSources) {
            ASTNode seen = nodes.get(source);
            if (seen != null) {
                return seen;
            }
            final ASTNode astNode = new ASTNode(source, nodeSources.getSourceLocation(source));
            nodes.put(source, astNode);
            return astNode;
        }

        ASTNode findASTNode(Node source) {
            return nodes.get(source);
        }

        ASTBlock makeASTBlock() {
            final ASTBlock astBlock = new ASTBlock(blocks.size());
            blocks.add(astBlock);
            return astBlock;
        }

        private static void traverseNodes(Node parent, ASTNode astParent, AST ast, TruffleInlining inliningDecisions, TruffleNodeSources nodeSources, ASTBlock currentBlock) {
            for (Map.Entry<String, Node> entry : NodeUtil.collectNodeChildren(parent).entrySet()) {
                final String label = entry.getKey();
                final Node node = entry.getValue();
                final ASTNode astNode = ast.makeASTNode(node, nodeSources);
                currentBlock.nodes.add(astNode);
                astParent.edges.add(new ASTEdge(astNode, label));
                traverseNodes(node, astNode, ast, inliningDecisions, nodeSources, currentBlock);
            }
        }

    }

    static class ASTNode {
        Node source;
        List<ASTEdge> edges = new ArrayList<>();
        final int id;
        Map<String, ? super Object> properties = new LinkedHashMap<>();
        ASTNodeClass nodeClass;

        ASTNode(Node source, TruffleSourceLanguagePosition sourcePosition) {
            this.source = source;
            this.id = sourcePosition.getNodeId();
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
            return graph.nodes.getValues();
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
}
