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

import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.Folder;
import org.graalvm.visualizer.data.InputMethod;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.InputEdge;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputNode;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.services.GroupCallback;
import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.graalvm.visualizer.data.FolderElement;
import static org.graalvm.visualizer.data.serialization.StreamUtils.maybeIntern;

/**
 * This parser is deprecated. Use {@link BinaryReader} and {@link ModelBuilder} instead.
 *
 * @see BinaryReader
 * @see BinarySource
 * @see ModelBuilder
 * @deprecated
 */
@Deprecated
public class BinaryParser implements GraphParser {
    private static final int BEGIN_GROUP = 0x00;
    private static final int BEGIN_GRAPH = 0x01;
    private static final int CLOSE_GROUP = 0x02;

    private static final int POOL_NEW = 0x00;
    private static final int POOL_STRING = 0x01;
    private static final int POOL_ENUM = 0x02;
    private static final int POOL_CLASS = 0x03;
    private static final int POOL_METHOD = 0x04;
    private static final int POOL_NULL = 0x05;
    private static final int POOL_NODE_CLASS = 0x06;
    private static final int POOL_FIELD = 0x07;
    private static final int POOL_SIGNATURE = 0x08;

    private static final int KLASS = 0x00;
    private static final int ENUM_KLASS = 0x01;

    private static final int PROPERTY_POOL = 0x00;
    private static final int PROPERTY_INT = 0x01;
    private static final int PROPERTY_LONG = 0x02;
    private static final int PROPERTY_DOUBLE = 0x03;
    private static final int PROPERTY_FLOAT = 0x04;
    private static final int PROPERTY_TRUE = 0x05;
    private static final int PROPERTY_FALSE = 0x06;
    private static final int PROPERTY_ARRAY = 0x07;
    private static final int PROPERTY_SUBGRAPH = 0x08;

    private static final String NO_BLOCK = "noBlock";

    private final GroupCallback callback;
    private final GraphDocument rootDocument;
    private final Deque<Folder> folderStack;
    private final Deque<byte[]> hashStack;
    private final ParseMonitor monitor;

    private MessageDigest digest;

    private BinarySource dataSource;
    protected ConstantPool constantPool;

    static class VersionMismatchException extends IOException {
        VersionMismatchException(String message) {
            super(message);
        }
    }

    private enum Length {
        S,
        M,
        L
    }

    private interface LengthToString {
        String toString(Length l);
    }

    private static abstract class Member implements LengthToString {
        public final Klass holder;
        public final int accessFlags;
        public final String name;

        public Member(Klass holder, String name, int accessFlags) {
            this.holder = holder;
            this.accessFlags = accessFlags;
            this.name = name;
        }
    }

    private static class Method extends Member {
        public final Signature signature;
        public final byte[] code;

        public Method(String name, Signature signature, byte[] code, Klass holder, int accessFlags) {
            super(holder, name, accessFlags);
            this.signature = signature;
            this.code = code;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(holder).append('.').append(name).append('(');
            for (int i = 0; i < signature.argTypes.length; i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(signature.argTypes[i]);
            }
            sb.append(')');
            return sb.toString();
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case M:
                    return holder.toString(Length.L) + "." + name;
                case S:
                    return holder.toString(Length.S) + "." + name;
                default:
                case L:
                    return toString();
            }
        }
    }

    private static class Signature {
        public final String returnType;
        public final String[] argTypes;

        public Signature(String returnType, String[] argTypes) {
            this.returnType = returnType;
            this.argTypes = argTypes;
        }

        @Override
        public String toString() {
            return "Signature(" + returnType + ":" + String.join(":", argTypes) + ")";
        }
    }

    private static class Field extends Member {
        public final String type;

        public Field(String type, Klass holder, String name, int accessFlags) {
            super(holder, name, accessFlags);
            this.type = type;
        }

        @Override
        public String toString() {
            return holder + "." + name;
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case M:
                    return holder.toString(Length.L) + "." + name;
                case S:
                    return holder.toString(Length.S) + "." + name;
                default:
                case L:
                    return toString();
            }
        }
    }

    private static class Klass implements LengthToString {
        public final String name;
        public final String simpleName;

        public Klass(String name) {
            this.name = name;
            String simple;
            try {
                simple = name.substring(name.lastIndexOf('.') + 1);
            } catch (IndexOutOfBoundsException ioobe) {
                simple = name;
            }
            this.simpleName = simple;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case S:
                    return simpleName;
                default:
                case L:
                case M:
                    return toString();
            }
        }
    }

    private static class EnumKlass extends Klass {
        public final String[] values;

        public EnumKlass(String name, String[] values) {
            super(name);
            this.values = values;
        }
    }

    private static class Port {
        public final boolean isList;
        public final String name;

        private Port(boolean isList, String name) {
            this.isList = isList;
            this.name = name;
        }
    }

    private static class TypedPort extends Port {
        public final EnumValue type;

        private TypedPort(boolean isList, String name, EnumValue type) {
            super(isList, name);
            this.type = type;
        }
    }

    private static class NodeClass {
        public final String className;
        public final String nameTemplate;
        public final List<TypedPort> inputs;
        public final List<Port> sux;

        private NodeClass(String className, String nameTemplate, List<TypedPort> inputs, List<Port> sux) {
            this.className = className;
            this.nameTemplate = nameTemplate;
            this.inputs = inputs;
            this.sux = sux;
        }

        @Override
        public String toString() {
            return className;
        }
    }

    private static class EnumValue implements LengthToString {
        public EnumKlass enumKlass;
        public int ordinal;

        public EnumValue(EnumKlass enumKlass, int ordinal) {
            this.enumKlass = enumKlass;
            this.ordinal = ordinal;
        }

        @Override
        public String toString() {
            return enumKlass.simpleName + "." + enumKlass.values[ordinal];
        }

        @Override
        public String toString(Length l) {
            switch (l) {
                case S:
                    return enumKlass.values[ordinal];
                default:
                case M:
                case L:
                    return toString();
            }
        }
    }

    public BinaryParser(ReadableByteChannel channel, ParseMonitor monitor, GraphDocument rootDocument, GroupCallback callback) {
        this(new BinarySource(null, channel), monitor, rootDocument, callback);
    }

    public BinaryParser(BinarySource dataSource, ParseMonitor monitor, GraphDocument rootDocument, GroupCallback callback) {
        this.callback = callback;
        constantPool = new ConstantPool();
        this.dataSource = dataSource;
        this.rootDocument = rootDocument;
        folderStack = new LinkedList<>();
        hashStack = new LinkedList<>();
        this.monitor = monitor;
        try {
            this.digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
        }
    }

    public BinaryParser(BinarySource dataSource, ConstantPool pool, GraphDocument rootDocument, GroupCallback callback) {
        this.callback = callback;
        this.constantPool = pool;
        this.dataSource = dataSource;
        this.rootDocument = rootDocument;
        this.monitor = null;
        folderStack = new LinkedList<>();
        hashStack = new LinkedList<>();
        try {
            this.digest = MessageDigest.getInstance("SHA-1");
            dataSource.useDigest(digest);
        } catch (NoSuchAlgorithmException e) {
        }
    }

    private Object[] readPoolObjects() throws IOException {
        int len = dataSource.readInt();
        if (len < 0) {
            return null;
        }
        Object[] props = new Object[len];
        for (int i = 0; i < len; i++) {
            props[i] = readPoolObject(Object.class);
        }
        return props;
    }

    @SuppressWarnings("unchecked")
    private <T> T readPoolObject(Class<T> klass) throws IOException {
        int type = dataSource.readByte();
        if (type == POOL_NULL) {
            return null;
        }
        if (type == POOL_NEW) {
            return (T) addPoolEntry(klass);
        }
        assert assertObjectType(klass, type);
        char index = dataSource.readShort();
        if (index >= constantPool.size()) {
            throw new IOException("Invalid constant pool index : " + index);
        }
        Object obj = getPoolData(index);
        return (T) obj;
    }

    private Object getPoolData(int index) {
        return constantPool.get(index, dataSource.getMark() - 1);
    }

    private boolean assertObjectType(Class<?> klass, int type) {
        switch (type) {
            case POOL_CLASS:
                return klass.isAssignableFrom(EnumKlass.class);
            case POOL_ENUM:
                return klass.isAssignableFrom(EnumValue.class);
            case POOL_METHOD:
                return klass.isAssignableFrom(Method.class);
            case POOL_STRING:
                return klass.isAssignableFrom(String.class);
            case POOL_NODE_CLASS:
                return klass.isAssignableFrom(NodeClass.class);
            case POOL_FIELD:
                return klass.isAssignableFrom(Field.class);
            case POOL_SIGNATURE:
                return klass.isAssignableFrom(Signature.class);
            case POOL_NULL:
                return true;
            default:
                return false;
        }
    }

    private Object addPoolEntry(Class<?> klass) throws IOException {
        long where = dataSource.getMark();
        char index = dataSource.readShort();
        int type = dataSource.readByte();
        int size = 0;
        assert assertObjectType(klass, type) : "Wrong object type : " + klass + " != " + type;
        Object obj;
        switch (type) {
            case POOL_CLASS: {
                String name = dataSource.readString();
                int klasstype = dataSource.readByte();
                if (klasstype == ENUM_KLASS) {
                    int len = dataSource.readInt();
                    String[] values = new String[len];
                    for (int i = 0; i < len; i++) {
                        values[i] = readPoolObject(String.class);
                    }
                    obj = new EnumKlass(name, values);
                } else if (klasstype == KLASS) {
                    obj = new Klass(name);
                } else {
                    throw new IOException("unknown klass type : " + klasstype);
                }
                break;
            }
            case POOL_ENUM: {
                EnumKlass enumClass = readPoolObject(EnumKlass.class);
                int ordinal = dataSource.readInt();
                obj = new EnumValue(enumClass, ordinal);
                break;
            }
            case POOL_NODE_CLASS: {
                String className = dataSource.readString();
                String nameTemplate = dataSource.readString();
                int inputCount = dataSource.readShort();
                List<TypedPort> inputs = new ArrayList<>(inputCount);
                for (int i = 0; i < inputCount; i++) {
                    boolean isList = dataSource.readByte() != 0;
                    String name = readPoolObject(String.class);
                    EnumValue inputType = readPoolObject(EnumValue.class);
                    inputs.add(new TypedPort(isList, name, inputType));
                }
                int suxCount = dataSource.readShort();
                List<Port> sux = new ArrayList<>(suxCount);
                for (int i = 0; i < suxCount; i++) {
                    boolean isList = dataSource.readByte() != 0;
                    String name = readPoolObject(String.class);
                    sux.add(new Port(isList, name));
                }
                obj = new NodeClass(className, nameTemplate, inputs, sux);
                break;
            }
            case POOL_METHOD: {
                Klass holder = readPoolObject(Klass.class);
                String name = readPoolObject(String.class);
                Signature sign = readPoolObject(Signature.class);
                int flags = dataSource.readInt();
                byte[] code = dataSource.readBytes();
                obj = new Method(name, sign, code, holder, flags);
                break;
            }
            case POOL_FIELD: {
                Klass holder = readPoolObject(Klass.class);
                String name = readPoolObject(String.class);
                String fType = readPoolObject(String.class);
                int flags = dataSource.readInt();
                obj = new Field(fType, holder, name, flags);
                break;
            }
            case POOL_SIGNATURE: {
                int argc = dataSource.readShort();
                String[] args = new String[argc];
                for (int i = 0; i < argc; i++) {
                    args[i] = readPoolObject(String.class);
                }
                String returnType = readPoolObject(String.class);
                obj = new Signature(returnType, args);
                break;
            }
            case POOL_STRING: {
                obj = dataSource.readString();
                size = obj.toString().length();
                break;
            }
            default:
                throw new IOException("unknown pool type");
        }
        return constantPool.addPoolEntry(index, obj, where);
    }

    private Object readPropertyObject() throws IOException {
        int type = dataSource.readByte();
        switch (type) {
            case PROPERTY_INT:
                return dataSource.readInt();
            case PROPERTY_LONG:
                return dataSource.readLong();
            case PROPERTY_FLOAT:
                return dataSource.readFloat();
            case PROPERTY_DOUBLE:
                return dataSource.readDouble();
            case PROPERTY_TRUE:
                return Boolean.TRUE;
            case PROPERTY_FALSE:
                return Boolean.FALSE;
            case PROPERTY_POOL:
                return readPoolObject(Object.class);
            case PROPERTY_ARRAY:
                int subType = dataSource.readByte();
                switch (subType) {
                    case PROPERTY_INT:
                        return dataSource.readInts();
                    case PROPERTY_DOUBLE:
                        return dataSource.readDoubles();
                    case PROPERTY_POOL:
                        return readPoolObjects();
                    default:
                        throw new IOException("Unknown type");
                }
            case PROPERTY_SUBGRAPH:
                long id = dataSource.getMark();
                InputGraph graph = parseGraph("");
                new Group(null, null).addElement(graph);
                return graph;
            default:
                throw new IOException("Unknown type");
        }
    }

    @Override
    public GraphDocument parse() throws IOException {
        folderStack.push(rootDocument);
        hashStack.push(null);
        if (monitor != null) {
            monitor.setState("Starting parsing");
        }
        try {
            // Check for a version specification
            dataSource.readHeader();
            while (true) {
                parseRoot();
            }
        } catch (EOFException e) {

        }
        if (monitor != null) {
            monitor.setState("Finished parsing");
        }
        return rootDocument;
    }

    protected void registerGraph(Folder parent, FolderElement graph) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                parent.addElement(graph);
            }
        });
    }

    protected void beginGroup(Folder parent) throws IOException {
        final Group group = parseGroup(parent);
        if (callback == null || parent instanceof Group) {
            registerGraph(parent, group);
        }
        folderStack.push(group);
        hashStack.push(null);
        if (callback != null && parent instanceof GraphDocument) {
            callback.started(group);
        }
    }

    protected void closeGroup(Group g) throws IOException {
    }

    protected void parseRoot() throws IOException {
        int type = dataSource.readByte();
        switch (type) {
            case BEGIN_GRAPH: {
                final Folder parent = folderStack.peek();
                final InputGraph graph = parseGraph();
                registerGraph(parent, graph);
                break;
            }
            case BEGIN_GROUP: {
                final Folder parent = folderStack.peek();
                beginGroup(parent);
                break;
            }
            case CLOSE_GROUP: {
                if (folderStack.isEmpty()) {
                    throw new IOException("Unbalanced groups");
                }
                Group g = (Group) folderStack.pop();
                hashStack.pop();
                closeGroup(g);
                break;
            }
            default:
                throw new IOException("unknown root : " + type);
        }
    }

    protected Group createGroup(Folder parent) {
        return new Group(parent, null);
    }

    protected Group parseGroup(Folder parent) throws IOException {
        String name = readPoolObject(String.class);
        String shortName = readPoolObject(String.class);
        if (monitor != null) {
            monitor.setState(shortName);
        }
        Method method = readPoolObject(Method.class);
        int bci = dataSource.readInt();
        Group group = createGroup(parent);
        group.getProperties().setProperty("name", name);
        parseProperties(group.getProperties());
        if (method != null) {
            InputMethod inMethod = new InputMethod(group, method.name, shortName, bci, toReaderMethod(method));
            group.setMethod(inMethod);
        }
        return group;
    }

    private BinaryReader.Method toReaderMethod(Method method) {
        return new BinaryReader.Method(method.name, toReaderSignature(method.signature), method.code, toReaderKlass(method.holder), method.accessFlags);
    }

    private BinaryReader.Signature toReaderSignature(Signature signature) {
        return new BinaryReader.Signature(signature.returnType, signature.argTypes);
    }
    private BinaryReader.Klass toReaderKlass(Klass klass) {
        return new BinaryReader.Klass(klass.name);
    }

    int lastPosition = 0;

    private InputGraph parseGraph() throws IOException {
        if (monitor != null) {
            monitor.updateProgress();
        }
        String title = readPoolObject(String.class);
        InputGraph graph = parseGraph(title);

        byte[] d = dataSource.finishDigest();
        byte[] hash = hashStack.peek();
        if (hash != null && Arrays.equals(hash, d)) {
            graph.getProperties().setProperty("_isDuplicate", "true");
        } else {
            hashStack.pop();
            hashStack.push(d);
        }
        return graph;
    }

    private void parseProperties(Properties properties) throws IOException {
        int propCount = dataSource.readShort();
        for (int j = 0; j < propCount; j++) {
            String key = readPoolObject(String.class);
            Object value = readPropertyObject();
            properties.setProperty(key, value);
        }
    }

    private InputGraph parseGraph(String title) throws IOException {
        InputGraph graph = new InputGraph(title);
        parseProperties(graph.getProperties());
        dataSource.startDigest();
        parseNodes(graph);
        parseBlocks(graph);
        graph.ensureNodesInBlocks();
        for (InputNode node : graph.getNodes()) {
            node.internProperties();
        }
        return graph;
    }

    private void parseBlocks(InputGraph graph) throws IOException {
        int blockCount = dataSource.readInt();
        List<Edge> edges = new LinkedList<>();
        for (int i = 0; i < blockCount; i++) {
            int id = dataSource.readInt();
            String name = id >= 0 ? Integer.toString(id) : NO_BLOCK;
            InputBlock block = graph.addBlock(name);
            int nodeCount = dataSource.readInt();
            for (int j = 0; j < nodeCount; j++) {
                int nodeId = dataSource.readInt();
                if (nodeId < 0) {
                    continue;
                }
                final Properties properties = graph.getNode(nodeId).getProperties();
                final String oldBlock = properties.get("block", String.class);
                if (oldBlock != null) {
                    properties.setProperty("block", oldBlock + ", " + name);
                } else {
                    block.addNode(nodeId);
                    properties.setProperty("block", name);
                }
            }
            int edgeCount = dataSource.readInt();
            for (int j = 0; j < edgeCount; j++) {
                int to = dataSource.readInt();
                edges.add(new Edge(id, to));
            }
        }
        for (Edge e : edges) {
            String fromName = e.from >= 0 ? Integer.toString(e.from) : NO_BLOCK;
            String toName = e.to >= 0 ? Integer.toString(e.to) : NO_BLOCK;
            graph.addBlockEdge(graph.getBlock(fromName), graph.getBlock(toName));
        }
    }

    private void parseNodes(InputGraph graph) throws IOException {
        int count = dataSource.readInt();
        Map<String, Object> props = new HashMap<>();
        List<Edge> inputEdges = new ArrayList<>(count);
        List<Edge> succEdges = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int id = dataSource.readInt();
            InputNode node = new InputNode(id);
            final Properties properties = node.getProperties();
            NodeClass nodeClass = readPoolObject(NodeClass.class);
            int preds = dataSource.readByte();
            if (preds > 0) {
                properties.setProperty("hasPredecessor", "true");
            }
            properties.setProperty("idx", Integer.toString(id));
            int propCount = dataSource.readShort();
            for (int j = 0; j < propCount; j++) {
                String key = readPoolObject(String.class);
                if (key.equals("hasPredecessor") || key.equals("name") || key.equals("class") || key.equals("id") || key.equals("idx")) {
                    key = "!data." + key;
                }
                Object value = readPropertyObject();
                if (value instanceof InputGraph) {
                    InputGraph subgraph = (InputGraph) value;
                    subgraph.getProperties().setProperty("name", node.getId() + ":" + key);
                    node.addSubgraph((InputGraph) value);
                } else {
                    properties.setProperty(key, value);
                    props.put(key, value);
                }
            }
            ArrayList<Edge> currentEdges = new ArrayList<>();
            int portNum = 0;
            for (TypedPort p : nodeClass.inputs) {
                if (p.isList) {
                    int size = dataSource.readShort();
                    for (int j = 0; j < size; j++) {
                        int in = dataSource.readInt();
                        if (in >= 0) {
                            Edge e = new Edge(in, id, (char) (preds + portNum), p.name + "[" + j + "]", p.type.toString(Length.S), true);
                            currentEdges.add(e);
                            inputEdges.add(e);
                            portNum++;
                        }
                    }
                } else {
                    int in = dataSource.readInt();
                    if (in >= 0) {
                        Edge e = new Edge(in, id, (char) (preds + portNum), p.name, p.type.toString(Length.S), true);
                        currentEdges.add(e);
                        inputEdges.add(e);
                        portNum++;
                    }
                }

            }
            portNum = 0;
            for (Port p : nodeClass.sux) {
                if (p.isList) {
                    int size = dataSource.readShort();
                    for (int j = 0; j < size; j++) {
                        int sux = dataSource.readInt();
                        if (sux >= 0) {
                            Edge e = new Edge(id, sux, (char) portNum, p.name + "[" + j + "]", "Successor", false);
                            currentEdges.add(e);
                            succEdges.add(e);
                            portNum++;
                        }
                    }
                } else {
                    int sux = dataSource.readInt();
                    if (sux >= 0) {
                        Edge e = new Edge(id, sux, (char) portNum, p.name, "Successor", false);
                        currentEdges.add(e);
                        succEdges.add(e);
                        portNum++;
                    }
                }
            }
            properties.setProperty("name", createName(currentEdges, props, nodeClass.nameTemplate));
            properties.setProperty("class", nodeClass.className);
            switch (nodeClass.className) {
                case "BeginNode":
                    properties.setProperty("shortName", "B");
                    break;
                case "EndNode":
                    properties.setProperty("shortName", "E");
                    break;
            }
            graph.addNode(node);
            props.clear();
        }

        Set<InputNode> nodesWithSuccessor = new HashSet<>();

        for (Edge e : succEdges) {
            assert !e.input;
            char fromIndex = e.num;
            nodesWithSuccessor.add(graph.getNode(e.from));
            char toIndex = 0;
            graph.addEdge(InputEdge.createImmutable(fromIndex, toIndex, e.from, e.to, e.label, e.type));
        }
        for (Edge e : inputEdges) {
            assert e.input;
            char fromIndex = (char) (nodesWithSuccessor.contains(graph.getNode(e.from)) ? 1 : 0);
            char toIndex = e.num;
            graph.addEdge(InputEdge.createImmutable(fromIndex, toIndex, e.from, e.to, e.label, e.type));
        }
    }

    static final Pattern TEMPLATE_PATTERN = Pattern.compile("\\{(p|i)#([a-zA-Z0-9$_]+)(/(l|m|s))?\\}");

    private String createName(List<Edge> edges, Map<String, Object> properties, String template) {
        Matcher m = TEMPLATE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String name = m.group(2);
            String type = m.group(1);
            String result;
            switch (type) {
                case "i":
                    StringBuilder inputString = new StringBuilder();
                    for (Edge edge : edges) {
                        if (edge.label.startsWith(name) && (name.length() == edge.label.length() || edge.label.charAt(name.length()) == '[')) {
                            if (inputString.length() > 0) {
                                inputString.append(", ");
                            }
                            inputString.append(edge.from);
                        }
                    }
                    result = inputString.toString();
                    break;
                case "p":
                    Object prop = properties.get(name);
                    String length = m.group(4);
                    if (prop == null) {
                        result = "?";
                    } else if (length != null && prop instanceof LengthToString) {
                        LengthToString lengthProp = (LengthToString) prop;
                        switch (length) {
                            default:
                            case "l":
                                result = lengthProp.toString(Length.L);
                                break;
                            case "m":
                                result = lengthProp.toString(Length.M);
                                break;
                            case "s":
                                result = lengthProp.toString(Length.S);
                                break;
                        }
                    } else {
                        result = prop.toString();
                    }
                    break;
                default:
                    result = "#?#";
                    break;
            }

            // Escape '\' and '$' to not interfere with the regular expression.
            StringBuilder newResult = new StringBuilder();
            for (int i = 0; i < result.length(); ++i) {
                char c = result.charAt(i);
                if (c == '\\') {
                    newResult.append("\\\\");
                } else if (c == '$') {
                    newResult.append("\\$");
                } else {
                    newResult.append(c);
                }
            }
            result = newResult.toString();
            m.appendReplacement(sb, result);
        }
        m.appendTail(sb);
        return maybeIntern(sb.toString());
    }

    private static class Edge {
        final int from;
        final int to;
        final char num;
        final String label;
        final String type;
        final boolean input;

        public Edge(int from, int to) {
            this(from, to, (char) 0, null, null, false);
        }

        public Edge(int from, int to, char num, String label, String type, boolean input) {
            this.from = from;
            this.to = to;
            this.label = maybeIntern(label);
            this.type = maybeIntern(type);
            this.num = num;
            this.input = input;
        }
    }

    public final ConstantPool getConstantPool() {
        return constantPool;
    }
}
