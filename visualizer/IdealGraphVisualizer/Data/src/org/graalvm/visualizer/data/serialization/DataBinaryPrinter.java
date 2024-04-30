/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.data.serialization;

import org.graalvm.visualizer.data.impl.DataSrcApiAccessor;
import org.graalvm.visualizer.data.src.LocationStratum;
import org.graalvm.visualizer.data.src.LocationStackFrame;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.serialization.BinaryReader.EnumKlass;
import org.graalvm.visualizer.data.serialization.BinaryReader.EnumValue;
import org.graalvm.visualizer.data.serialization.BinaryReader.Field;
import org.graalvm.visualizer.data.serialization.BinaryReader.Method;
import org.graalvm.visualizer.data.serialization.BinaryReader.Signature;
import org.graalvm.visualizer.data.serialization.Builder.Port;
import java.util.stream.Collectors;
import jdk.graal.compiler.graphio.GraphBlocks;
import jdk.graal.compiler.graphio.GraphElements;
import jdk.graal.compiler.graphio.GraphLocations;
import jdk.graal.compiler.graphio.GraphOutput;
import jdk.graal.compiler.graphio.GraphStructure;
import jdk.graal.compiler.graphio.GraphTypes;
import java.net.URI;
import java.net.URISyntaxException;

final class DataBinaryPrinter implements
                GraphStructure<InputGraph, InputNode, Builder.NodeClass, List<? extends Port>>,
                GraphBlocks<InputGraph, InputBlock, InputNode>,
                GraphElements<Method, Field, BinaryReader.Signature, LocationStackFrame>,
                GraphLocations<Method, LocationStackFrame, LocationStratum>,
                GraphTypes {
    private DataBinaryPrinter() {
    }

    static GraphOutput<InputGraph, Method> createOutput(FileChannel open, Map<String, Object> props) throws IOException {
        DataBinaryPrinter p = new DataBinaryPrinter();
        GraphOutput.Builder bld = GraphOutput.newBuilder(p).blocks(p).elementsAndLocations(p, p).types(p).protocolVersion(8, 0);
        if (props != null) {
            for (String k : props.keySet()) {
                bld.attr(k, props.get(k));
            }
        }
        return bld.build(open);
    }

    @Override
    public InputGraph graph(InputGraph current, Object obj) {
        return obj instanceof InputGraph ? (InputGraph) obj : null;
    }

    @Override
    public Method method(Object obj) {
        return obj instanceof Method ? (Method) obj : null;
    }

    @Override
    public InputNode node(Object obj) {
        return obj instanceof InputNode ? (InputNode) obj : null;
    }

    @Override
    public Builder.NodeClass classForNode(InputNode node) {
        return node.getNodeClass();
    }

    @Override
    public Builder.NodeClass nodeClass(Object obj) {
        return obj instanceof Builder.NodeClass ? (Builder.NodeClass) obj : null;
    }

    @Override
    public Object nodeClassType(Builder.NodeClass clazz) {
        return new BinaryReader.Klass(clazz.className);
    }

    @Override
    public Object enumClass(Object e) {
        if (e instanceof EnumValue) {
            return ((EnumValue) e).enumKlass;
        } else {
            return null;
        }
    }

    @Override
    public int enumOrdinal(Object obj) {
        if (obj instanceof EnumValue) {
            return ((EnumValue) obj).ordinal;
        } else {
            return -1;
        }
    }

    @Override
    public String[] enumTypeValues(Object clazz) {
        if (clazz instanceof BinaryReader.EnumKlass) {
            return ((EnumKlass) clazz).values;
        }
        return null;
    }

    @Override
    public String nameTemplate(Builder.NodeClass clazz) {
        return clazz.nameTemplate;
    }

    @Override
    public List<? extends Port> portInputs(Builder.NodeClass nodeClass) {
        return nodeClass.inputs;
    }

    @Override
    public List<? extends Port> portOutputs(Builder.NodeClass nodeClass) {
        return nodeClass.sux;
    }

    @Override
    public int nodeId(InputNode n) {
        return n == null ? -1 : n.getId();
    }

    @Override
    public boolean nodeHasPredecessor(InputNode node) {
        return node.hasPredecessors();
    }

    @Override
    public int nodesCount(InputGraph info) {
        return info.getNodeCount();
    }

    @Override
    public Iterable<InputNode> nodes(InputGraph info) {
        return info.getNodes();
    }

    @Override
    public void nodeProperties(InputGraph graph, InputNode node, Map<String, ? super Object> props) {
        node.getProperties().toMap(props, DataBinaryWriter.NODE_PROPERTY_EXCLUDE);
        node.getSubgraphs(props);
    }

    @Override
    public List<InputNode> blockNodes(InputGraph info, InputBlock block) {
        return block.getNodes();
    }

    @Override
    public int blockId(InputBlock sux) {
        try {
            return Integer.parseInt(sux.getName());
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public Collection<InputBlock> blocks(InputGraph graph) {
        return graph.getBlocks().stream().filter(x -> !x.getName().contains("no block")).collect(Collectors.toList());
    }

    @Override
    public Collection<InputBlock> blockSuccessors(InputBlock block) {
        return block.getSuccessors();
    }

    @Override
    public int portSize(List<? extends Port> edges) {
        return edges.size();
    }

    @Override
    public boolean edgeDirect(List<? extends Port> edges, int i) {
        return !edges.get(i).isList;
    }

    @Override
    public String edgeName(List<? extends Port> edges, int i) {
        return edges.get(i).name;
    }

    @Override
    public BinaryReader.EnumValue edgeType(List<? extends Port> edges, int i) {
        return ((Builder.TypedPort) edges.get(i)).type;
    }

    @Override
    public Collection<? extends InputNode> edgeNodes(InputGraph graph, InputNode node, List<? extends Port> port, int index) {
        List<InputNode> ret = new ArrayList<>();
        List<Integer> ids = node.getIdsMap().get(port.get(index));
        if (ids != null) {
            for (Integer id : ids) {
                ret.add(graph.getNode(id));
            }
        }
        return ret.isEmpty() ? null : ret;
    }

    @Override
    public String typeName(Object obj) {
        if (obj instanceof BinaryReader.Klass) {
            return ((BinaryReader.Klass) obj).name;
        }
        return null;
    }

    @Override
    public byte[] methodCode(Method method) {
        return method.code;
    }

    @Override
    public int methodModifiers(Method method) {
        return method.accessFlags;
    }

    @Override
    public Signature methodSignature(Method method) {
        return method.signature;
    }

    @Override
    public String methodName(Method method) {
        return method.name;
    }

    @Override
    public Object methodDeclaringClass(Method method) {
        return method.holder;
    }

    @Override
    public int fieldModifiers(Field field) {
        return field.accessFlags;
    }

    @Override
    public String fieldTypeName(Field field) {
        return field.type;
    }

    @Override
    public String fieldName(Field field) {
        return field.name;
    }

    @Override
    public Object fieldDeclaringClass(Field field) {
        return field.holder;
    }

    @Override
    public Field field(Object object) {
        return object instanceof Field ? (Field) object : null;
    }

    @Override
    public BinaryReader.Signature signature(Object object) {
        if (object instanceof BinaryReader.Signature) {
            return (Signature) object;
        }
        return null;
    }

    @Override
    public int signatureParameterCount(BinaryReader.Signature signature) {
        return signature.argTypes.length;
    }

    @Override
    public String signatureParameterTypeName(BinaryReader.Signature signature, int index) {
        return signature.argTypes[index];
    }

    @Override
    public String signatureReturnTypeName(BinaryReader.Signature signature) {
        return signature.returnType;
    }

    @Override
    public LocationStackFrame nodeSourcePosition(Object object) {
        if (object instanceof LocationStackFrame) {
            return (LocationStackFrame) object;
        }
        return null;
    }

    @Override
    public Method nodeSourcePositionMethod(LocationStackFrame pos) {
        return DataSrcApiAccessor.getInstance().getMethod(pos);
    }

    @Override
    public LocationStackFrame nodeSourcePositionCaller(LocationStackFrame pos) {
        return pos.getParent();
    }

    @Override
    public int nodeSourcePositionBCI(LocationStackFrame pos) {
        return pos.getBci();
    }

    @Override
    public StackTraceElement methodStackTraceElement(Method method, int bci, LocationStackFrame pos) {
        return new StackTraceElement("", "", pos.getFileName(), pos.getLine());
    }

    @Override
    public Iterable<LocationStratum> methodLocation(Method method, int bci, LocationStackFrame pos) {
        return pos.getStrata();
    }

    @Override
    public String locationLanguage(LocationStratum location) {
        return location.language;
    }

    @Override
    public URI locationURI(LocationStratum location) throws URISyntaxException {
        if (location.uri != null) {
            return new URI(location.uri);
        }
        String path = location.file;
        try {
            return path == null ? null : new URI(null, null, path, null);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    @Override
    public int locationLineNumber(LocationStratum location) {
        return location.line;
    }

    @Override
    public int locationOffsetStart(LocationStratum location) {
        return location.startOffset;
    }

    @Override
    public int locationOffsetEnd(LocationStratum location) {
        return location.endOffset;
    }

}
