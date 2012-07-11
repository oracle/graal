/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.printer;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.schedule.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;

public class BinaryGraphPrinter implements GraphPrinter{
    private static final int CONSTANT_POOL_MAX_SIZE = 2000;

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

    private static final int PROPERTY_POOL = 0x00;
    private static final int PROPERTY_INT = 0x01;
    private static final int PROPERTY_LONG = 0x02;
    private static final int PROPERTY_DOUBLE = 0x03;
    private static final int PROPERTY_FLOAT = 0x04;
    private static final int PROPERTY_TRUE = 0x05;
    private static final int PROPERTY_FALSE = 0x06;
    private static final int PROPERTY_ARRAY = 0x07;

    private static final int KLASS = 0x00;
    private static final int ENUM_KLASS = 0x01;

    private static final class ConstantPool extends LinkedHashMap<Object, Integer> {
        private final LinkedList<Integer> availableIds;
        private int nextId;
        private static final long serialVersionUID = -2676889957907285681L;
        public ConstantPool() {
            super(50, 0.65f);
            availableIds = new LinkedList<>();
        }
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Object, Integer> eldest) {
            if (size() > CONSTANT_POOL_MAX_SIZE) {
                availableIds.addFirst(eldest.getValue());
                return true;
            }
            return false;
        }

        private Integer nextAvailableId() {
            if (!availableIds.isEmpty()) {
                return availableIds.removeFirst();
            }
            return nextId++;
        }

        public int add(Object obj) {
            Integer id = nextAvailableId();
            put(obj, id);
            return id;
        }
    }

    private final ConstantPool constantPool;
    private final ByteBuffer buffer;
    private final WritableByteChannel channel;
    private long bytes;
    private long newPoolEntry;

    public BinaryGraphPrinter(WritableByteChannel channel) {
        constantPool = new ConstantPool();
        buffer = ByteBuffer.allocateDirect(256 * 1024);
        this.channel = channel;
    }

    public void print(Graph graph, String title, SchedulePhase predefinedSchedule) throws IOException {
        long startBytes = bytes;
        long startTime = System.currentTimeMillis();
        long startPool = newPoolEntry;
        SchedulePhase schedule = predefinedSchedule;
        if (schedule == null) {
            try {
                schedule = new SchedulePhase();
                schedule.apply((StructuredGraph) graph);
            } catch (Throwable t) {
            }
        }
        ControlFlowGraph cfg =  schedule == null ? null : schedule.getCFG();
        BlockMap<List<ScheduledNode>> blockToNodes = schedule == null ? null : schedule.getBlockToNodesMap();
        Block[] blocks = cfg == null ? null : cfg.getBlocks();
        writeByte(BEGIN_GRAPH);
        writePoolObject(title);
        writeNodes(graph);
        writeBlocks(blocks, blockToNodes);
        flush();
        long t = System.currentTimeMillis() - startTime;
        long b = bytes - startBytes;
        long pool = newPoolEntry - startPool;
        System.out.println("Graph printed in " + t + " ms and " + b + " bytes, " + pool + " pool entries created (" + graph.getNodeCount() + " nodes)");
    }

    private void flush() throws IOException {
        buffer.flip();
        bytes += channel.write(buffer);
        buffer.compact();
    }

    private void ensureAvailable(int i) throws IOException {
        while (buffer.remaining() < i) {
            flush();
        }
    }

    private void writeByte(int b) throws IOException {
        ensureAvailable(1);
        buffer.put((byte) b);
    }

    private void writeInt(int b) throws IOException {
        ensureAvailable(4);
        buffer.putInt(b);
    }

    private void writeLong(long b) throws IOException {
        ensureAvailable(8);
        buffer.putLong(b);
    }

    private void writeDouble(double b) throws IOException {
        ensureAvailable(8);
        buffer.putDouble(b);
    }

    private void writeFloat(float b) throws IOException {
        ensureAvailable(4);
        buffer.putFloat(b);
    }

    private void writeShort(char b) throws IOException {
        ensureAvailable(2);
        buffer.putChar(b);
    }

    private void writeString(String str) throws IOException {
        writeInt(str.length());
        ensureAvailable(str.length() * 2);
        for (int i = 0; i < str.length(); i++) {
            buffer.putChar(str.charAt(i));
        }
    }

    private void writeBytes(byte[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            ensureAvailable(b.length);
            buffer.put(b);
        }
    }

    private void writeInts(int[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            ensureAvailable(b.length * 4);
            for (int i = 0; i < b.length; i++) {
                buffer.putInt(b[i]);
            }
        }
    }

    private void writeDoubles(double[] b) throws IOException {
        if (b == null) {
            writeInt(-1);
        } else {
            writeInt(b.length);
            ensureAvailable(b.length * 8);
            for (int i = 0; i < b.length; i++) {
                buffer.putDouble(b[i]);
            }
        }
    }

    private void writePoolObject(Object object) throws IOException {
        if (object == null) {
            writeByte(POOL_NULL);
            return;
        }
        if (object instanceof ResolvedJavaType) {
            writePoolObject(((ResolvedJavaType) object).toJava());
            return;
        }
        Integer id = constantPool.get(object);
        if (id == null) {
            addPoolEntry(object);
        } else {
            if (object instanceof Enum<?>) {
                writeByte(POOL_ENUM);
            } else if (object instanceof Class<?>) {
                writeByte(POOL_CLASS);
            } else if (object instanceof NodeClass) {
                writeByte(POOL_NODE_CLASS);
            } else if (object instanceof ResolvedJavaMethod) {
                writeByte(POOL_METHOD);
            } else if (object instanceof ResolvedJavaField) {
                writeByte(POOL_FIELD);
            } else if (object instanceof Signature) {
                writeByte(POOL_SIGNATURE);
            } else {
                writeByte(POOL_STRING);
            }
            writeInt(id.intValue());
        }
    }

    private void addPoolEntry(Object object) throws IOException {
        newPoolEntry++;
        int index = constantPool.add(object);
        writeByte(POOL_NEW);
        writeInt(index);
        if (object instanceof Class<?>) {
            Class<?> klass = (Class< ? >) object;
            writeByte(POOL_CLASS);
            writeString(klass.getName());
            if (klass.isEnum()) {
                writeByte(ENUM_KLASS);
                Object[] enumConstants = klass.getEnumConstants();
                writeInt(enumConstants.length);
                for (Object o : enumConstants) {
                    writePoolObject(((Enum<?>) o).name());
                }
            } else {
                writeByte(KLASS);
            }
        } else if (object instanceof Enum<?>) {
            writeByte(POOL_ENUM);
            writePoolObject(object.getClass());
            writeInt(((Enum) object).ordinal());
        } else if (object instanceof NodeClass) {
            NodeClass nodeClass = (NodeClass) object;
            writeByte(POOL_NODE_CLASS);
            writeString(nodeClass.getJavaClass().getSimpleName());
            writeString(nodeClass.getNameTemplate());
            List<Position> directInputPositions = nodeClass.getFirstLevelInputPositions();
            writeShort((char) directInputPositions.size());
            for (Position pos : directInputPositions) {
                writePoolObject(nodeClass.getName(pos));
            }
            List<Position> directSuccessorPositions = nodeClass.getFirstLevelSuccessorPositions();
            writeShort((char) directSuccessorPositions.size());
            for (Position pos : directSuccessorPositions) {
                writePoolObject(nodeClass.getName(pos));
            }
        } else if (object instanceof ResolvedJavaMethod) {
            writeByte(POOL_METHOD);
            ResolvedJavaMethod method = ((ResolvedJavaMethod) object);
            writePoolObject(method.holder());
            writePoolObject(method.name());
            writePoolObject(method.signature());
            writeInt(method.accessFlags());
            writeBytes(method.code());
        } else if (object instanceof ResolvedJavaField) {
            writeByte(POOL_FIELD);
            ResolvedJavaField field = ((ResolvedJavaField) object);
            writePoolObject(field.holder());
            writePoolObject(field.name());
            writePoolObject(field.type().name());
            writeInt(field.accessFlags());
        } else if (object instanceof Signature) {
            writeByte(POOL_SIGNATURE);
            Signature signature = ((Signature) object);
            int args = signature.argumentCount(false);
            writeShort((char) args);
            for (int i = 0; i < args; i++) {
                writePoolObject(signature.argumentTypeAt(i, null).name());
            }
            writePoolObject(signature.returnType(null).name());
        } else {
            writeByte(POOL_STRING);
            writeString(object.toString());
        }
    }

    private void writePropertyObject(Object obj) throws IOException {
        if (obj instanceof Integer) {
            writeByte(PROPERTY_INT);
            writeInt(((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            writeByte(PROPERTY_LONG);
            writeLong(((Long) obj).longValue());
        } else if (obj instanceof Double) {
            writeByte(PROPERTY_DOUBLE);
            writeDouble(((Double) obj).doubleValue());
        } else if (obj instanceof Float) {
            writeByte(PROPERTY_FLOAT);
            writeFloat(((Float) obj).floatValue());
        } else if (obj instanceof Boolean) {
            if (((Boolean) obj).booleanValue()) {
                writeByte(PROPERTY_TRUE);
            } else {
                writeByte(PROPERTY_FALSE);
            }
        } else if (obj != null && obj.getClass().isArray()) {
            Class< ? > componentType = obj.getClass().getComponentType();
            if (componentType.isPrimitive()) {
                if (componentType == Double.TYPE) {
                    writeByte(PROPERTY_ARRAY);
                    writeByte(PROPERTY_DOUBLE);
                    writeDoubles((double[]) obj);
                } else if (componentType == Integer.TYPE) {
                    writeByte(PROPERTY_ARRAY);
                    writeByte(PROPERTY_INT);
                    writeInts((int[]) obj);
                } else {
                    writeByte(PROPERTY_POOL);
                    writePoolObject(obj);
                }
            } else {
                writeByte(PROPERTY_ARRAY);
                writeByte(PROPERTY_POOL);
                Object[] array = (Object[]) obj;
                writeInt(array.length);
                for (Object o : array) {
                    writePoolObject(o);
                }
            }
        } else {
            writeByte(PROPERTY_POOL);
            writePoolObject(obj);
        }
    }

    @SuppressWarnings("deprecation")
    private void writeNodes(Graph graph) throws IOException {
        Map<Object, Object> props = new HashMap<>();
        writeInt(graph.getNodeCount());
        for (Node node : graph.getNodes()) {
            NodeClass nodeClass = node.getNodeClass();
            node.getDebugProperties(props);
            writeInt(node.getId());
            writePoolObject(nodeClass);
            writeByte(node.predecessor() == null ? 0 : 1);
            writeShort((char) props.size());
            for (Entry<Object, Object> entry : props.entrySet()) {
                String key = entry.getKey().toString();
                writePoolObject(key);
                writePropertyObject(entry.getValue());
            }
            // successors
            NodeSuccessorsIterable successors = node.successors();
            writeShort((char) successors.count());
            NodeClassIterator suxIt = successors.iterator();
            while (suxIt.hasNext()) {
                Position pos = suxIt.nextPosition();
                Node sux = nodeClass.get(node, pos);
                writeInt(sux.getId());
                writeShort((char) pos.index);
            }
            //inputs
            NodeInputsIterable inputs = node.inputs();
            writeShort((char) inputs.count());
            NodeClassIterator inIt = inputs.iterator();
            while (inIt.hasNext()) {
                Position pos = inIt.nextPosition();
                Node in = nodeClass.get(node, pos);
                writeInt(in.getId());
                writeShort((char) pos.index);
            }
            props.clear();
        }
    }

    @SuppressWarnings("deprecation")
    private void writeBlocks(Block[] blocks, BlockMap<List<ScheduledNode>> blockToNodes) throws IOException {
        if (blocks != null) {
            writeInt(blocks.length);
            for (Block block : blocks) {
                List<ScheduledNode> nodes = blockToNodes.get(block);
                writeInt(block.getId());
                writeInt(nodes.size());
                for (Node node : nodes) {
                    writeInt(node.getId());
                }
                writeInt(block.getSuccessors().size());
                for (Block sux : block.getSuccessors()) {
                    writeInt(sux.getId());
                }
            }
        } else {
            writeInt(0);
        }
    }

    public void beginGroup(String name, String shortName, ResolvedJavaMethod method, int bci) throws IOException {
        writeByte(BEGIN_GROUP);
        writePoolObject(name);
        writePoolObject(shortName);
        writePoolObject(method);
        writeInt(bci);
    }

    public void endGroup() throws IOException {
        writeByte(CLOSE_GROUP);
    }
}
