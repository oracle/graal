/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.debug.JavaMethodContext;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.truffle.phases.inlining.CallNode;
import jdk.graal.compiler.truffle.phases.inlining.CallTree;
import jdk.graal.compiler.graphio.GraphBlocks;
import jdk.graal.compiler.graphio.GraphStructure;

import com.oracle.truffle.compiler.ConstantFieldInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilationTask;
import com.oracle.truffle.compiler.TruffleSourceLanguagePosition;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Represents a TruffleAST constructed from Truffle nodes using constant reflection. Note this AST
 * is currently only constructed for AST dumping to IGV.
 *
 * @see TruffleDebugHandlersFactory for details on dumping
 */
final class TruffleAST implements JavaMethodContext {

    static final ASTDumpStructure AST_DUMP_STRUCTURE = new ASTDumpStructure();

    private final ASTNode root;
    private final List<TruffleAST.ASTBlock> blocks = new ArrayList<>();
    private final List<ASTNode> nodes = new ArrayList<>();
    private final TruffleCompilationTask task;
    private final TruffleCompilable compilable;
    private final PartialEvaluator partialEvaluator;
    private final CallTree callTree;
    private int currentId = 0;

    private TruffleAST(PartialEvaluator partialEvaluator, TruffleCompilationTask task, TruffleCompilable compilable, CallTree callTree) {
        this.partialEvaluator = partialEvaluator;
        this.compilable = compilable;
        this.callTree = callTree;
        this.task = task;
        JavaConstant rootNode = readRootNode(compilable);
        this.root = makeASTNode(null, null, rootNode);
        injectRootName(root, compilable);
        buildTree(rootNode, root, root.block);
    }

    static TruffleAST create(PartialEvaluator config, TruffleCompilationTask task, TruffleCompilable compilable, CallTree callTree) {
        return new TruffleAST(config, task, compilable, callTree);
    }

    private JavaConstant readRootNode(TruffleCompilable c) {
        return constantReflection().readFieldValue(types().OptimizedCallTarget_rootNode, c.asJavaConstant());
    }

    private KnownTruffleTypes types() {
        return partialEvaluator.getTypes();
    }

    @Override
    public JavaMethod asJavaMethod() {
        return new TruffleDebugJavaMethod(task, compilable);
    }

    private ASTNode makeASTNode(ASTNode parent, TruffleAST.ASTBlock blockParent, JavaConstant node) {
        TruffleAST.ASTBlock block = null;
        if (blockParent == null) {
            block = makeASTBlock(null, callTree != null ? callTree.getRoot() : null);
        } else {
            block = blockParent;
        }
        final ASTNode astNode = new ASTNode(block, node);
        nodes.add(astNode);
        makeInlinedAST(parent, node, astNode);
        return astNode;
    }

    private void makeInlinedAST(ASTNode parent, JavaConstant node, final ASTNode astNode) {
        if (callTree == null) {
            return;
        }
        if (types().OptimizedDirectCallNode.equals(metaAccess().lookupJavaType(node))) {
            CallNode found = null;
            for (CallNode callNode : parent.block.callNode.getChildren()) {
                if (callNode.getCallNode().equals(node)) {
                    found = callNode;
                    break;
                }
            }
            if (found != null) {
                for (var entry : found.getDebugProperties().entrySet()) {
                    astNode.properties.put("call." + entry.getKey().toString(), entry.getValue());
                }
            }
            if (found != null && found.getState() == CallNode.State.Inlined) {
                TruffleAST.ASTBlock block = makeASTBlock(parent.block, found);
                TruffleCompilable ast = block.callNode.getDirectCallTarget();
                buildTree(readRootNode(ast), astNode, block);

                if (astNode.children.size() >= 1) {
                    ASTNode rootNode = astNode.children.get(0);
                    injectRootName(rootNode, ast);
                }
            }
        }
    }

    private static void injectRootName(ASTNode rootNode, TruffleCompilable ast) {
        String rootName = ast.getName();
        rootNode.properties.put("label", rootNode.properties.get("label") + " (" + rootName + ")");
        rootNode.properties.put("rootName", rootName);
    }

    private TruffleAST.ASTBlock makeASTBlock(TruffleAST.ASTBlock parentBlock, CallNode callNode) {
        final TruffleAST.ASTBlock astBlock = new ASTBlock(blocks.size(), callNode);
        blocks.add(astBlock);
        if (parentBlock != null) {
            parentBlock.successors.add(astBlock);
        }
        return astBlock;
    }

    private void buildTree(JavaConstant parent, ASTNode astParent, TruffleAST.ASTBlock blockParent) {
        if (astParent == null) {
            return;
        }
        ResolvedJavaType type = metaAccess().lookupJavaType(parent);
        if (type != null) {
            ConstantReflectionProvider constantReflection = constantReflection();
            for (ResolvedJavaField field : type.getInstanceFields(true)) {
                String label = field.getName();
                ConstantFieldInfo info = partialEvaluator.getConstantFieldInfo(field);
                if (info == null) {
                    continue;
                } else if (info.isChild()) {
                    JavaConstant node = constantReflection.readFieldValue(field, parent);
                    ASTNode astNode = addNode(astParent, blockParent, node, label);
                    buildTree(node, astNode, blockParent);
                } else if (info.isChildren()) {
                    JavaConstant array = constantReflection.readFieldValue(field, parent);
                    if (array.isNonNull()) {
                        for (int i = 0; i < constantReflection.readArrayLength(array); i++) {
                            String arrayLabel = label + "[" + i + "]";
                            JavaConstant node = constantReflection.readArrayElement(array, i);
                            ASTNode astNode = addNode(astParent, blockParent, node, arrayLabel);
                            buildTree(node, astNode, blockParent);
                        }
                    }
                }
            }
        }
    }

    private MetaAccessProvider metaAccess() {
        return partialEvaluator.config.lastTier().providers().getMetaAccess();
    }

    private ConstantReflectionProvider constantReflection() {
        return partialEvaluator.config.lastTier().providers().getConstantReflection();
    }

    private ASTNode addNode(ASTNode parent, TruffleAST.ASTBlock blockParent, JavaConstant node, String edgeLabel) {
        if (node.isNull()) {
            return null;
        }
        final ASTNode astNode = makeASTNode(parent, blockParent, node);
        parent.edges.add(new ASTEdge(astNode, edgeLabel));
        parent.children.add(astNode);
        return astNode;
    }

    private enum EdgeType {
        EDGE_TYPE;
    }

    private static final class ASTNodeClass {
        final ASTNode node;

        ASTNodeClass(ASTNode node) {
            this.node = node;
        }
    }

    private static class ASTBlock {
        private final int id;
        private final List<TruffleAST.ASTBlock> successors = new ArrayList<>();
        private final List<ASTNode> nodes = new ArrayList<>();
        private final CallNode callNode;

        ASTBlock(int id, CallNode callNode) {
            this.id = id;
            this.callNode = callNode;
        }
    }

    private static class ASTEdge {
        final ASTNode node;
        final String label;

        ASTEdge(ASTNode node, String label) {
            this.node = node;
            this.label = label;
        }
    }

    private final class ASTNode {

        final List<ASTEdge> edges = new ArrayList<>();

        private final int id;
        private final ResolvedJavaType nodeType;
        private final Map<String, Object> properties = new LinkedHashMap<>();
        private final ASTNodeClass nodeClass;

        private final ASTBlock block;
        private final List<ASTNode> children = new ArrayList<>();

        ASTNode(ASTBlock block, JavaConstant node) {
            this.block = block;
            this.id = currentId++;
            this.nodeClass = new ASTNodeClass(this);
            this.nodeType = metaAccess().lookupJavaType(node);
            this.properties.put("label", dropNodeSuffix(nodeType.getUnqualifiedName()));
            this.properties.put("cost", "NodeCost.MONOMORPHIC");
            this.properties.put("nodeClassName", nodeType.toJavaName(true));
            if (callTree != null) {
                StructuredGraph graph = callTree.getRoot().getIR();
                ConstantNode constant = null;
                for (Node irNode : graph.getNodes()) {
                    if (irNode instanceof ConstantNode c) {
                        if (node.equals(c.asJavaConstant())) {
                            constant = c;
                            break;
                        }
                    }
                }
                this.properties.put("graalIRNode", constant);
            }

            TruffleSourceLanguagePosition position = task.getPosition(node);
            if (position != null) {
                properties.put("sourceLanguage", position.getLanguage());
                properties.put("sourceDescription", position.getDescription());
                properties.put("nodeSourcePosition",
                                new NodeSourcePosition(new PartialEvaluator.SourceLanguagePositionImpl(position), null, types().OptimizedCallTarget_profiledPERoot, -1));
            }
            this.properties.putAll(task.getDebugProperties(node));
            block.nodes.add(this);
        }

        private static String dropNodeSuffix(String className) {
            return className.replaceFirst("Node$", "").replaceFirst("NodeGen$", "");
        }

    }

    static final class ASTDumpStructure implements GraphStructure<TruffleAST, ASTNode, ASTNodeClass, List<ASTEdge>>, GraphBlocks<TruffleAST, ASTBlock, ASTNode> {

        @Override
        public TruffleAST graph(TruffleAST currentGraph, Object obj) {
            return obj instanceof TruffleAST ? (TruffleAST) obj : null;
        }

        @Override
        public Iterable<? extends ASTNode> nodes(TruffleAST graph) {
            return graph.nodes;
        }

        @Override
        public int nodesCount(TruffleAST graph) {
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
        public void nodeProperties(TruffleAST graph, ASTNode node, Map<String, ? super Object> properties) {
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
            return nodeClass.getClass();
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
        public Collection<? extends ASTNode> edgeNodes(TruffleAST graph, ASTNode node, List<ASTEdge> port, int index) {
            return List.of(port.get(index).node);
        }

        @Override
        public Collection<? extends ASTBlock> blocks(TruffleAST graph) {
            return graph.blocks;
        }

        @Override
        public int blockId(ASTBlock block) {
            return block.id;
        }

        @Override
        public Collection<? extends ASTNode> blockNodes(TruffleAST info, ASTBlock block) {
            return block.nodes;
        }

        @Override
        public Collection<? extends ASTBlock> blockSuccessors(ASTBlock block) {
            return block.successors;
        }

    }

}
