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

import static jdk.graal.compiler.graph.Edges.Type.Inputs;
import static jdk.graal.compiler.graph.Edges.Type.Successors;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.InputEdges;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.SourceLanguagePosition;
import jdk.graal.compiler.graphio.GraphBlocks;
import jdk.graal.compiler.graphio.GraphElements;
import jdk.graal.compiler.graphio.GraphLocations;
import jdk.graal.compiler.graphio.GraphOutput;
import jdk.graal.compiler.graphio.GraphStructure;
import jdk.graal.compiler.graphio.GraphTypes;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.util.JavaConstantFormattable;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.Signature;

public class BinaryGraphPrinter implements
                GraphStructure<BinaryGraphPrinter.GraphInfo, Node, NodeClass<?>, Edges>,
                GraphBlocks<BinaryGraphPrinter.GraphInfo, HIRBlock, Node>,
                GraphElements<ResolvedJavaMethod, ResolvedJavaField, Signature, NodeSourcePosition>,
                GraphLocations<ResolvedJavaMethod, NodeSourcePosition, SourceLanguagePosition>,
                GraphTypes, GraphPrinter {
    private final SnippetReflectionProvider snippetReflection;
    private final GraphOutput<BinaryGraphPrinter.GraphInfo, ResolvedJavaMethod> output;

    @SuppressWarnings("this-escape")
    public BinaryGraphPrinter(DebugContext ctx, SnippetReflectionProvider snippetReflection) throws IOException {
        // @formatter:off
        this.output = ctx.buildOutput(GraphOutput.newBuilder(this).
                        blocks(this).
                        elementsAndLocations(this, this).
                        types(this)
        );
        // @formatter:on
        this.snippetReflection = snippetReflection;
    }

    @SuppressWarnings("this-escape")
    public BinaryGraphPrinter(WritableByteChannel channel, SnippetReflectionProvider snippetReflection) throws IOException {
        this.output = GraphOutput.newBuilder(this).blocks(this).elementsAndLocations(this, this).types(this).build(channel);
        this.snippetReflection = snippetReflection;
    }

    @Override
    public SnippetReflectionProvider getSnippetReflectionProvider() {
        return snippetReflection;
    }

    @Override
    public void beginGroup(DebugContext debug, String name, String shortName, ResolvedJavaMethod method, int bci, Map<Object, Object> properties) throws IOException {
        output.beginGroup(new GraphInfo(debug, null), name, shortName, method, bci, DebugContext.addVersionProperties(properties));
    }

    @Override
    public void endGroup() throws IOException {
        output.endGroup();
    }

    @Override
    public void close() {
        output.close();
    }

    @Override
    public ResolvedJavaMethod method(Object object) {
        if (object instanceof Bytecode) {
            return ((Bytecode) object).getMethod();
        } else if (object instanceof ResolvedJavaMethod) {
            return ((ResolvedJavaMethod) object);
        } else {
            return null;
        }
    }

    @Override
    public Node node(Object obj) {
        return obj instanceof Node ? (Node) obj : null;
    }

    @Override
    public NodeClass<?> nodeClass(Object obj) {
        if (obj instanceof NodeClass<?>) {
            return (NodeClass<?>) obj;
        }
        return null;
    }

    @Override
    public NodeClass<?> classForNode(Node node) {
        return node.getNodeClass();
    }

    @Override
    public Object nodeClassType(NodeClass<?> node) {
        return node.getJavaClass();
    }

    @Override
    public String nameTemplate(NodeClass<?> nodeClass) {
        return nodeClass.getNameTemplate();
    }

    @Override
    public final GraphInfo graph(GraphInfo currrent, Object obj) {
        if (obj instanceof Graph) {
            return new GraphInfo(currrent.debug, (Graph) obj);
        } else {
            return null;
        }
    }

    @Override
    public int nodeId(Node n) {
        return getNodeId(n);
    }

    @Override
    public Edges portInputs(NodeClass<?> nodeClass) {
        return nodeClass.getEdges(Inputs);
    }

    @Override
    public Edges portOutputs(NodeClass<?> nodeClass) {
        return nodeClass.getEdges(Successors);
    }

    @SuppressWarnings("deprecation")
    private static int getNodeId(Node node) {
        return node == null ? -1 : node.getId();
    }

    @Override
    public List<Node> blockNodes(GraphInfo info, HIRBlock block) {
        List<Node> nodes = info.blockToNodes.get(block);
        if (nodes == null) {
            return null;
        }
        List<Node> extraNodes = new LinkedList<>();
        for (Node node : nodes) {
            findExtraNodes(node, extraNodes);
        }
        extraNodes.removeAll(nodes);
        extraNodes.addAll(0, nodes);
        return extraNodes;
    }

    @Override
    public int blockId(HIRBlock sux) {
        return sux.getId();
    }

    @Override
    public List<HIRBlock> blockSuccessors(HIRBlock block) {
        ArrayList<HIRBlock> succ = new ArrayList<>();
        for (int i = 0; i < block.getSuccessorCount(); i++) {
            succ.add(block.getSuccessorAt(i));
        }
        return succ;
    }

    @Override
    public Iterable<Node> nodes(GraphInfo info) {
        return info.graph.getNodes();
    }

    @Override
    public int nodesCount(GraphInfo info) {
        return info.graph.getNodeCount();
    }

    private static boolean checkNoChars(Node node, Map<String, ? super Object> props) {
        for (Map.Entry<String, Object> e : props.entrySet()) {
            Object value = e.getValue();
            if (value instanceof Character) {
                throw new AssertionError("value of " + node.getClass().getName() + " debug property \"" + e.getKey() +
                                "\" should be an Integer or a String as a Character value may not be printable/viewable");
            }
        }
        return true;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void nodeProperties(GraphInfo info, Node node, Map<String, ? super Object> props) {
        node.getDebugProperties((Map) props);
        assert checkNoChars(node, props);
        NodeMap<HIRBlock> nodeToBlocks = info.nodeToBlocks;

        if (nodeToBlocks != null) {
            HIRBlock block = getBlockForNode(node, nodeToBlocks);
            if (block != null) {
                props.put("relativeFrequency", block.getRelativeFrequency());
                props.put("nodeToBlock", block);
            }
        }

        try {
            props.put("nodeCostSize", node.estimatedNodeSize());
        } catch (GraalError | Exception e) {
            handleNodePropertyException("nodeCostSize", node, e, props);
        }
        try {
            props.put("nodeCostCycles", node.estimatedNodeCycles());
        } catch (GraalError | Exception e) {
            handleNodePropertyException("nodeCostCycles", node, e, props);
        }

        if (nodeToBlocks != null) {
            Object block = getBlockForNode(node, nodeToBlocks);
            if (block != null) {
                props.put("nodeToBlock", block);
            }
        }

        if (info.cfg != null) {
            if (node instanceof LoopBeginNode) {
                // check if cfg is up to date
                if (info.cfg.getLocalLoopFrequencyData().containsKey((LoopBeginNode) node)) {
                    props.put("localLoopFrequency", info.cfg.localLoopFrequency((LoopBeginNode) node));
                    props.put("localLoopFrequencySource", info.cfg.localLoopFrequencySource((LoopBeginNode) node));
                }
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
                updateStringPropertiesForConstant((Map) props, cn);
            }
            props.put("category", "floating");
        }

        if (MemoryKill.isSingleMemoryKill(node)) {
            try {
                props.put("killedLocationIdentity", ((SingleMemoryKill) node).getKilledLocationIdentity());
            } catch (GraalError | Exception e) {
                handleNodePropertyException("killedLocationIdentity", node, e, props);
            }
        }
        if (MemoryKill.isMultiMemoryKill(node)) {
            try {
                props.put("killedLocationIdentities", ((MultiMemoryKill) node).getKilledLocationIdentities());
            } catch (GraalError | Exception e) {
                handleNodePropertyException("killedLocationIdentities", node, e, props);
            }
        }

        if (node instanceof MemoryAccess) {
            try {
                props.put("locationIdentity", ((MemoryAccess) node).getLocationIdentity());
            } catch (GraalError | Exception e) {
                handleNodePropertyException("locationIdentity", node, e, props);
            }
        }

        if (getSnippetReflectionProvider() != null) {
            for (Map.Entry<String, Object> prop : props.entrySet()) {
                if (prop.getValue() instanceof JavaConstantFormattable) {
                    props.put(prop.getKey(), ((JavaConstantFormattable) prop.getValue()).format(this));
                }
            }
        }
    }

    private static void handleNodePropertyException(String property, Node node, Throwable e, Map<String, Object> properties) {
        TTY.printf("Exception when calculating node property \"%s\" for node %s: %s. This indicates a node is not checking if optional/non-optional properties are properly initialized or set. " +
                        "Dumping can happen with non-canonical non-verifiable graphs. Node implementations should also be able to dump contents if they are not properly initialized.%n",
                        property, node, e);
        properties.put(property, "Exception when calculating node property.");
    }

    private HIRBlock getBlockForNode(Node node, NodeMap<HIRBlock> nodeToBlocks) {
        if (nodeToBlocks.isNew(node)) {
            return null;
        } else {
            HIRBlock block = nodeToBlocks.get(node);
            if (block != null) {
                return block;
            } else if (node instanceof PhiNode) {
                return getBlockForNode(((PhiNode) node).merge(), nodeToBlocks);
            }
        }
        return null;
    }

    private static void findExtraNodes(Node node, Collection<? super Node> extraNodes) {
        if (node instanceof AbstractMergeNode) {
            AbstractMergeNode merge = (AbstractMergeNode) node;
            for (PhiNode phi : merge.phis()) {
                extraNodes.add(phi);
            }
        }
    }

    @Override
    public boolean nodeHasPredecessor(Node node) {
        return node.predecessor() != null;
    }

    @Override
    public List<HIRBlock> blocks(GraphInfo graph) {
        return graph.blocks;
    }

    @Override
    public void print(DebugContext debug, Graph graph, Map<Object, Object> properties, int id, String format, Object... args) throws IOException {
        output.print(new GraphInfo(debug, graph), properties, id, format, args);
    }

    @Override
    public int portSize(Edges port) {
        return port.getCount();
    }

    @Override
    public boolean edgeDirect(Edges port, int index) {
        return index < port.getDirectCount();
    }

    @Override
    public String edgeName(Edges port, int index) {
        return port.getName(index);
    }

    @Override
    public Object edgeType(Edges port, int index) {
        return ((InputEdges) port).getInputType(index);
    }

    @Override
    public Collection<? extends Node> edgeNodes(GraphInfo graph, Node node, Edges port, int i) {
        if (i < port.getDirectCount()) {
            Node single = Edges.getNode(node, port.getOffsets(), i);
            return Collections.singletonList(single);
        } else {
            return Edges.getNodeList(node, port.getOffsets(), i);
        }
    }

    @Override
    public Object enumClass(Object enumValue) {
        if (enumValue instanceof Enum) {
            return enumValue.getClass();
        }
        return null;
    }

    @Override
    public int enumOrdinal(Object obj) {
        if (obj instanceof Enum<?>) {
            return ((Enum<?>) obj).ordinal();
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    @Override
    public String[] enumTypeValues(Object clazz) {
        if (clazz instanceof Class<?>) {
            Class<? extends Enum<?>> enumClass = (Class<? extends Enum<?>>) clazz;
            Enum<?>[] constants = enumClass.getEnumConstants();
            if (constants != null) {
                String[] names = new String[constants.length];
                for (int i = 0; i < constants.length; i++) {
                    names[i] = constants[i].name();
                }
                return names;
            }
        }
        return null;
    }

    @Override
    public String typeName(Object obj) {
        if (obj instanceof Class<?>) {
            return ((Class<?>) obj).getName();
        }
        if (obj instanceof JavaType) {
            return ((JavaType) obj).toJavaName();
        }
        return null;
    }

    @Override
    public byte[] methodCode(ResolvedJavaMethod method) {
        return method.getCode();
    }

    @Override
    public int methodModifiers(ResolvedJavaMethod method) {
        return method.getModifiers();
    }

    @Override
    public Signature methodSignature(ResolvedJavaMethod method) {
        return method.getSignature();
    }

    @Override
    public String methodName(ResolvedJavaMethod method) {
        return method.getName();
    }

    @Override
    public Object methodDeclaringClass(ResolvedJavaMethod method) {
        return method.getDeclaringClass();
    }

    @Override
    public int fieldModifiers(ResolvedJavaField field) {
        return field.getModifiers();
    }

    @Override
    public String fieldTypeName(ResolvedJavaField field) {
        return field.getType().toJavaName();
    }

    @Override
    public String fieldName(ResolvedJavaField field) {
        return field.getName();
    }

    @Override
    public Object fieldDeclaringClass(ResolvedJavaField field) {
        return field.getDeclaringClass();
    }

    @Override
    public ResolvedJavaField field(Object object) {
        if (object instanceof ResolvedJavaField) {
            return (ResolvedJavaField) object;
        }
        return null;
    }

    @Override
    public Signature signature(Object object) {
        if (object instanceof Signature) {
            return (Signature) object;
        }
        return null;
    }

    @Override
    public int signatureParameterCount(Signature signature) {
        return signature.getParameterCount(false);
    }

    @Override
    public String signatureParameterTypeName(Signature signature, int index) {
        return signature.getParameterType(index, null).getName();
    }

    @Override
    public String signatureReturnTypeName(Signature signature) {
        return signature.getReturnType(null).getName();
    }

    @Override
    public NodeSourcePosition nodeSourcePosition(Object object) {
        if (object instanceof NodeSourcePosition) {
            return (NodeSourcePosition) object;
        }
        return null;
    }

    @Override
    public ResolvedJavaMethod nodeSourcePositionMethod(NodeSourcePosition pos) {
        return pos.getMethod();
    }

    @Override
    public NodeSourcePosition nodeSourcePositionCaller(NodeSourcePosition pos) {
        return pos.getCaller();
    }

    @Override
    public int nodeSourcePositionBCI(NodeSourcePosition pos) {
        return pos.getBCI();
    }

    @Override
    public StackTraceElement methodStackTraceElement(ResolvedJavaMethod method, int bci, NodeSourcePosition pos) {
        return method.asStackTraceElement(bci);
    }

    @Override
    public Iterable<SourceLanguagePosition> methodLocation(ResolvedJavaMethod method, int bci, NodeSourcePosition pos) {
        StackTraceElement e = methodStackTraceElement(method, bci, pos);
        class JavaSourcePosition implements SourceLanguagePosition {

            @Override
            public String toShortString() {
                return e.toString();
            }

            @Override
            public int getOffsetEnd() {
                return -1;
            }

            @Override
            public int getOffsetStart() {
                return -1;
            }

            @Override
            public int getLineNumber() {
                return e.getLineNumber();
            }

            @Override
            public URI getURI() {
                String path = e.getFileName();
                try {
                    return new URI(null, null, path == null ? "(Unknown Source)" : path, null);
                } catch (URISyntaxException ex) {
                    throw new IllegalArgumentException(ex);
                }
            }

            @Override
            public String getLanguage() {
                return "Java";
            }

            @Override
            public int getNodeId() {
                return -1;
            }

            @Override
            public String getNodeClassName() {
                return null;
            }

        }

        List<SourceLanguagePosition> arr = new ArrayList<>();
        arr.add(new JavaSourcePosition());
        NodeSourcePosition at = pos;
        while (at != null) {
            SourceLanguagePosition cur = at.getSourceLanguage();
            if (cur != null) {
                arr.add(cur);
            }
            at = at.getCaller();
        }
        return arr;
    }

    @Override
    public String locationLanguage(SourceLanguagePosition location) {
        return location.getLanguage();
    }

    @Override
    public URI locationURI(SourceLanguagePosition location) {
        return location.getURI();
    }

    @Override
    public int locationLineNumber(SourceLanguagePosition location) {
        return location.getLineNumber();
    }

    @Override
    public int locationOffsetStart(SourceLanguagePosition location) {
        return location.getOffsetStart();
    }

    @Override
    public int locationOffsetEnd(SourceLanguagePosition location) {
        return location.getOffsetEnd();
    }

    static final class GraphInfo {
        final DebugContext debug;
        final Graph graph;
        final ControlFlowGraph cfg;
        final BlockMap<List<Node>> blockToNodes;
        final NodeMap<HIRBlock> nodeToBlocks;
        final List<HIRBlock> blocks;

        private GraphInfo(DebugContext debug, Graph graph) {
            this.debug = debug;
            this.graph = graph;
            StructuredGraph.ScheduleResult scheduleResult = null;
            if (graph instanceof StructuredGraph) {
                StructuredGraph structuredGraph = (StructuredGraph) graph;
                scheduleResult = GraalDebugHandlersFactory.tryGetSchedule(debug, structuredGraph);
            }
            cfg = scheduleResult == null ? debug.contextLookup(ControlFlowGraph.class) : scheduleResult.getCFG();
            blockToNodes = scheduleResult == null ? null : scheduleResult.getBlockToNodesMap();
            nodeToBlocks = scheduleResult == null ? null : scheduleResult.getNodeToBlockMap();
            blocks = cfg == null ? null : Arrays.asList(cfg.getBlocks());
        }
    }

}
