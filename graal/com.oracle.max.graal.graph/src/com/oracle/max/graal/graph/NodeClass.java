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
package com.oracle.max.graal.graph;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.*;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.max.graal.graph.Node.Data;

import sun.misc.Unsafe;

public class NodeClass {

    public static final int NOT_ITERABLE = -1;

    /**
     * Interface used by {@link NodeClass#rescanAllFieldOffsets(CalcOffset)} to determine the offset (in bytes) of a field.
     */
    public interface CalcOffset {
        long getOffset(Field field);
    }

    private static final Class< ? > NODE_CLASS = Node.class;
    private static final Class< ? > INPUT_LIST_CLASS = NodeInputList.class;
    private static final Class< ? > SUCCESSOR_LIST_CLASS = NodeSuccessorList.class;

    private static final Unsafe unsafe = getUnsafe();

    private static Unsafe getUnsafe() {
        try {
            // this will only fail if graal is not part of the boot class path
            return Unsafe.getUnsafe();
        } catch (SecurityException e) {
            // nothing to do
        }
        try {
            Field theUnsafeInstance = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafeInstance.setAccessible(true);
            return (Unsafe) theUnsafeInstance.get(Unsafe.class);
        } catch (Exception e) {
            // currently we rely on being able to use Unsafe...
            throw new RuntimeException("exception while trying to get Unsafe.theUnsafe via reflection:", e);
        }
    }

    private static final Map<Class< ? >, NodeClass> nodeClasses = new ConcurrentHashMap<Class< ? >, NodeClass>();
    private static int nextIterableId = 0;

    private final Class< ? > clazz;
    private final int directInputCount;
    private final long[] inputOffsets;
    private final Class<?>[] inputTypes;
    private final String[] inputNames;
    private final int directSuccessorCount;
    private final long[] successorOffsets;
    private final Class<?>[] successorTypes;
    private final String[] successorNames;
    private final long[] dataOffsets;
    private final Class<?>[] dataTypes;
    private final String[] dataNames;
    private final boolean canGVN;
    private final int startGVNNumber;
    private final String shortName;
    private final int iterableId;
    private final boolean hasOutgoingEdges;

    static class DefaultCalcOffset implements CalcOffset {
        @Override
        public long getOffset(Field field) {
            return unsafe.objectFieldOffset(field);
        }
    }

    public NodeClass(Class< ? > clazz) {
        assert NODE_CLASS.isAssignableFrom(clazz);
        this.clazz = clazz;

        FieldScanner scanner = new FieldScanner(new DefaultCalcOffset());
        scanner.scan(clazz);

        directInputCount = scanner.inputOffsets.size();
        inputOffsets = sortedLongCopy(scanner.inputOffsets, scanner.inputListOffsets);
        directSuccessorCount = scanner.successorOffsets.size();
        successorOffsets = sortedLongCopy(scanner.successorOffsets, scanner.successorListOffsets);
        dataOffsets = new long[scanner.dataOffsets.size()];
        for (int i = 0; i < scanner.dataOffsets.size(); ++i) {
            dataOffsets[i] = scanner.dataOffsets.get(i);
        }
        dataTypes = scanner.dataTypes.toArray(new Class[0]);
        dataNames = scanner.dataNames.toArray(new String[0]);
        inputTypes = arrayUsingSortedOffsets(scanner.inputTypesMap, inputOffsets, new Class<?>[inputOffsets.length]);
        inputNames = arrayUsingSortedOffsets(scanner.inputNamesMap, inputOffsets, new String[inputOffsets.length]);
        successorTypes = arrayUsingSortedOffsets(scanner.successorTypesMap, successorOffsets, new Class<?>[successorOffsets.length]);
        successorNames = arrayUsingSortedOffsets(scanner.successorNamesMap, successorOffsets, new String[successorOffsets.length]);

        canGVN = Node.ValueNumberable.class.isAssignableFrom(clazz);
        startGVNNumber = clazz.hashCode();

        String shortName = clazz.getSimpleName();
        if (shortName.endsWith("Node") && !shortName.equals("StartNode") && !shortName.equals("EndNode")) {
            shortName = shortName.substring(0, shortName.length() - 4);
        }
        NodeInfo info = clazz.getAnnotation(NodeInfo.class);
        if (info != null) {
            if (!info.shortName().isEmpty()) {
                shortName = info.shortName();
            }
        }
        this.shortName = shortName;
        if (Node.IterableNodeType.class.isAssignableFrom(clazz)) {
            this.iterableId = nextIterableId++;
            // TODO(ls) add type hierarchy - based node iteration
//            for (NodeClass nodeClass : nodeClasses.values()) {
//                if (clazz.isAssignableFrom(nodeClass.clazz)) {
//                    throw new UnsupportedOperationException("iterable non-final Node classes not supported: " + clazz);
//                }
//            }
        } else {
            this.iterableId = NOT_ITERABLE;
        }
        this.hasOutgoingEdges = this.inputOffsets.length > 0 || this.successorOffsets.length > 0;
    }

    public static void rescanAllFieldOffsets(CalcOffset calc) {
        for (NodeClass nodeClass : nodeClasses.values()) {
            nodeClass.rescanFieldOffsets(calc);
        }
    }

    private void rescanFieldOffsets(CalcOffset calc) {
        FieldScanner scanner = new FieldScanner(calc);
        scanner.scan(clazz);
        assert directInputCount == scanner.inputOffsets.size();
        copyInto(inputOffsets, sortedLongCopy(scanner.inputOffsets, scanner.inputListOffsets));
        assert directSuccessorCount == scanner.successorOffsets.size();
        copyInto(successorOffsets, sortedLongCopy(scanner.successorOffsets, scanner.successorListOffsets));
        assert dataOffsets.length == scanner.dataOffsets.size();
        for (int i = 0; i < scanner.dataOffsets.size(); ++i) {
            dataOffsets[i] = scanner.dataOffsets.get(i);
        }
        copyInto(dataTypes, scanner.dataTypes);
        copyInto(dataNames, scanner.dataNames);

        copyInto(inputTypes, arrayUsingSortedOffsets(scanner.inputTypesMap, this.inputOffsets, new Class<?>[this.inputOffsets.length]));
        copyInto(inputNames, arrayUsingSortedOffsets(scanner.inputNamesMap, this.inputOffsets, new String[this.inputOffsets.length]));
        copyInto(successorTypes, arrayUsingSortedOffsets(scanner.successorTypesMap, this.successorOffsets, new Class<?>[this.successorOffsets.length]));
        copyInto(successorNames, arrayUsingSortedOffsets(scanner.successorNamesMap, this.successorOffsets, new String[this.successorNames.length]));
    }

    private static void copyInto(long[] dest, long[] src) {
        assert dest.length == src.length;
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src[i];
        }
    }

    private static <T> void copyInto(T[] dest, T[] src) {
        assert dest.length == src.length;
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src[i];
        }
    }

    private static <T> void copyInto(T[] dest, List<T> src) {
        assert dest.length == src.size();
        for (int i = 0; i < dest.length; i++) {
            dest[i] = src.get(i);
        }
    }

    public boolean hasOutgoingEdges() {
        return hasOutgoingEdges;
    }

    public String shortName() {
        return shortName;
    }

    public int iterableId() {
        return iterableId;
    }

    public boolean valueNumberable() {
        return canGVN;
    }

    private static synchronized NodeClass getSynchronized(Class< ? > c) {
        NodeClass clazz = nodeClasses.get(c);
        if (clazz == null) {
            clazz = new NodeClass(c);
            nodeClasses.put(c, clazz);
        }
        return clazz;
    }

    public static final NodeClass get(Class< ? > c) {
        NodeClass clazz = nodeClasses.get(c);
        return clazz == null ? getSynchronized(c) : clazz;
    }

    public static int cacheSize() {
        return nextIterableId;
    }

    private static class FieldScanner {
        public final ArrayList<Long> inputOffsets = new ArrayList<Long>();
        public final ArrayList<Long> inputListOffsets = new ArrayList<Long>();
        public final Map<Long, Class< ? >> inputTypesMap = new HashMap<Long, Class<?>>();
        public final Map<Long, String> inputNamesMap = new HashMap<Long, String>();
        public final ArrayList<Long> successorOffsets = new ArrayList<Long>();
        public final ArrayList<Long> successorListOffsets = new ArrayList<Long>();
        public final Map<Long, Class< ? >> successorTypesMap = new HashMap<Long, Class<?>>();
        public final Map<Long, String> successorNamesMap = new HashMap<Long, String>();
        public final ArrayList<Long> dataOffsets = new ArrayList<Long>();
        public final ArrayList<Class< ? >> dataTypes = new ArrayList<Class<?>>();
        public final ArrayList<String> dataNames = new ArrayList<String>();
        public final CalcOffset calc;

        public FieldScanner(CalcOffset calc) {
            this.calc = calc;
        }

        public void scan(Class< ? > clazz) {
            do {
                for (Field field : clazz.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers())) {
                        Class< ? > type = field.getType();
                        long offset = calc.getOffset(field);
                        String name = field.getName();
                        if (field.isAnnotationPresent(Node.Input.class)) {
                            assert !field.isAnnotationPresent(Node.Successor.class) : "field cannot be both input and successor";
                            if (INPUT_LIST_CLASS.isAssignableFrom(type)) {
                                inputListOffsets.add(offset);
                            } else {
                                assert NODE_CLASS.isAssignableFrom(type) : "invalid input type: " + type;
                                inputOffsets.add(offset);
                                inputTypesMap.put(offset, type);
                            }
                            if (field.getAnnotation(Node.Input.class).notDataflow()) {
                                inputNamesMap.put(offset, name + "#NDF");
                            } else {
                                inputNamesMap.put(offset, name);
                            }
                        } else if (field.isAnnotationPresent(Node.Successor.class)) {
                            if (SUCCESSOR_LIST_CLASS.isAssignableFrom(type)) {
                                successorListOffsets.add(offset);
                            } else {
                                assert NODE_CLASS.isAssignableFrom(type) : "invalid successor type: " + type;
                                successorOffsets.add(offset);
                                successorTypesMap.put(offset, type);
                            }
                            successorNamesMap.put(offset, name);
                        } else if (field.isAnnotationPresent(Node.Data.class)) {
                            dataOffsets.add(offset);
                            dataTypes.add(type);
                            dataNames.add(name);
                        } else {
                            assert !NODE_CLASS.isAssignableFrom(type) || name.equals("Null") : "suspicious node field: " + field;
                            assert !INPUT_LIST_CLASS.isAssignableFrom(type) : "suspicious node input list field: " + field;
                            assert !SUCCESSOR_LIST_CLASS.isAssignableFrom(type) : "suspicious node successor list field: " + field;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            } while (clazz != Node.class);
        }
    }

    private static <T> T[] arrayUsingSortedOffsets(Map<Long, T> map, long[] sortedOffsets, T[] result) {
        for (int i = 0; i < sortedOffsets.length; i++) {
            result[i] = map.get(sortedOffsets[i]);
        }
        return result;
    }

    private static long[] sortedLongCopy(ArrayList<Long> list1, ArrayList<Long> list2) {
        Collections.sort(list1);
        Collections.sort(list2);
        long[] result = new long[list1.size() + list2.size()];
        for (int i = 0; i < list1.size(); i++) {
            result[i] = list1.get(i);
        }
        for (int i = 0; i < list2.size(); i++) {
            result[list1.size() + i] = list2.get(i);
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("NodeClass ").append(clazz.getSimpleName()).append(" [");
        for (int i = 0; i < inputOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(inputOffsets[i]);
        }
        str.append("] [");
        for (int i = 0; i < successorOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(successorOffsets[i]);
        }
        str.append("] [");
        for (int i = 0; i < dataOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(dataOffsets[i]);
        }
        str.append("]");
        return str.toString();
    }

    public static final class Position {
        public final boolean input;
        public final int index;
        public final int subIndex;

        public Position(boolean input, int index, int subIndex) {
            this.input = input;
            this.index = index;
            this.subIndex = subIndex;
        }

        @Override
        public String toString() {
            return (input ? "input " : "successor ") + index + "/" + subIndex;
        }

        public Node get(Node node) {
            return node.getNodeClass().get(node, this);
        }

        public void set(Node node, Node value) {
            node.getNodeClass().set(node, this, value);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + index;
            result = prime * result + (input ? 1231 : 1237);
            result = prime * result + subIndex;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            Position other = (Position) obj;
            if (index != other.index) {
                return false;
            }
            if (input != other.input) {
                return false;
            }
            if (subIndex != other.subIndex) {
                return false;
            }
            return true;
        }
    }

    private static Node getNode(Node node, long offset) {
        return (Node) unsafe.getObject(node, offset);
    }

    @SuppressWarnings("unchecked")
    private static NodeList<Node> getNodeList(Node node, long offset) {
        return (NodeList<Node>) unsafe.getObject(node, offset);
    }

    private static void putNode(Node node, long offset, Node value) {
        unsafe.putObject(node, offset, value);
    }

    private static void putNodeList(Node node, long offset, NodeList value) {
        unsafe.putObject(node, offset, value);
    }

    /**
     * An iterator that will iterate over the fields given in {@link offsets}.
     * The first {@ directCount} offsets are treated as fields of type {@link Node}, while the rest of the fields are treated as {@link NodeList}s.
     * All elements of these NodeLists will be visited by the iterator as well.
     * This iterator can be used to iterate over the inputs or successors of a node.
     *
     * An iterator of this type will not return null values, unless the field values are modified concurrently.
     * Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    public static final class NodeClassIterator implements Iterator<Node> {

        private final Node node;
        private final int modCount;
        private final int directCount;
        private final long[] offsets;
        private int index;
        private int subIndex;

        /**
         * Creates an iterator that will iterate over fields in the given node.
         * @param node the node which contains the fields.
         * @param offsets the offsets of the fields.
         * @param directCount the number of fields that should be treated as fields of type {@link Node}, the rest are treated as {@link NodeList}s.
         */
        private NodeClassIterator(Node node, long[] offsets, int directCount) {
            this.node = node;
            this.modCount = node.modCount();
            this.offsets = offsets;
            this.directCount = directCount;
            index = NOT_ITERABLE;
            subIndex = 0;
            forward();
        }

        private void forward() {
            if (index < directCount) {
                index++;
                while (index < directCount) {
                    Node element = getNode(node, offsets[index]);
                    if (element != null) {
                        return;
                    }
                    index++;
                }
            } else {
                subIndex++;
            }
            while (index < offsets.length) {
                NodeList<Node> list = getNodeList(node, offsets[index]);
                while (subIndex < list.size()) {
                    if (list.get(subIndex) != null) {
                        return;
                    }
                    subIndex++;
                }
                subIndex = 0;
                index++;
            }
        }

        private Node nextElement() {
            if (index < directCount) {
                return getNode(node, offsets[index]);
            } else  if (index < offsets.length) {
                NodeList<Node> list = getNodeList(node, offsets[index]);
                return list.get(subIndex);
            }
            return null;
        }

        @Override
        public boolean hasNext() {
            try {
                return index < offsets.length;
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Node next() {
            try {
                return nextElement();
            } finally {
                forward();
                assert modCount == node.modCount();
            }
        }

        public Position nextPosition() {
            try {
                if (index < directCount) {
                    return new Position(offsets == node.getNodeClass().inputOffsets, index, NOT_ITERABLE);
                } else {
                    return new Position(offsets == node.getNodeClass().inputOffsets, index, subIndex);
                }
            } finally {
                forward();
                assert modCount == node.modCount();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public int valueNumber(Node n) {
        int number = 0;
        if (canGVN) {
            number = startGVNNumber;
            for (int i = 0; i < dataOffsets.length; ++i) {
                Class<?> type = dataTypes[i];
                if (type.isPrimitive()) {
                    if (type == Integer.TYPE) {
                        int intValue = unsafe.getInt(n, dataOffsets[i]);
                        number += intValue;
                    } else if (type == Boolean.TYPE) {
                        boolean booleanValue = unsafe.getBoolean(n, dataOffsets[i]);
                        if (booleanValue) {
                            number += 7;
                        }
                    } else {
                        assert false;
                    }
                } else {
                    Object o = unsafe.getObject(n, dataOffsets[i]);
                    if (o != null) {
                        number += o.hashCode();
                    }
                }
                number *= 13;
            }
        }
        return number;
    }

    /**
     * Populates a given map with the names and values of all fields marked with @{@link Data}.
     * @param node the node from which to take the values.
     * @param properties a map that will be populated.
     */
    public void getDebugProperties(Node node, Map<Object, Object> properties) {
        for (int i = 0; i < dataOffsets.length; ++i) {
            Class<?> type = dataTypes[i];
            Object value = null;
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    value = unsafe.getInt(node, dataOffsets[i]);
                } else if (type == Boolean.TYPE) {
                    value = unsafe.getBoolean(node, dataOffsets[i]);
                } else {
                    assert false;
                }
            } else {
                value = unsafe.getObject(node, dataOffsets[i]);
            }
            properties.put("data." + dataNames[i], value);
        }
    }

    public boolean valueEqual(Node a, Node b) {
        if (!canGVN || a.getNodeClass() != b.getNodeClass()) {
            return a == b;
        }
        for (int i = 0; i < dataOffsets.length; ++i) {
            Class<?> type = dataTypes[i];
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    int aInt = unsafe.getInt(a, dataOffsets[i]);
                    int bInt = unsafe.getInt(b, dataOffsets[i]);
                    if (aInt != bInt) {
                        return false;
                    }
                } else if (type == Boolean.TYPE) {
                    boolean aBoolean = unsafe.getBoolean(a, dataOffsets[i]);
                    boolean bBoolean = unsafe.getBoolean(b, dataOffsets[i]);
                    if (aBoolean != bBoolean) {
                        return false;
                    }
                } else {
                    assert false;
                }
            } else {
                Object objectA = unsafe.getObject(a, dataOffsets[i]);
                Object objectB = unsafe.getObject(b, dataOffsets[i]);
                if (objectA != objectB) {
                    if (objectA != null && objectB != null) {
                        if (!(objectA.equals(objectB))) {
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public Node get(Node node, Position pos) {
        long offset = pos.input ? inputOffsets[pos.index] : successorOffsets[pos.index];
        if (pos.subIndex == NOT_ITERABLE) {
            return getNode(node, offset);
        } else {
            return getNodeList(node, offset).get(pos.subIndex);
        }
    }

    public String getName(Position pos) {
        return pos.input ? inputNames[pos.index] : successorNames[pos.index];
    }

    private void set(Node node, Position pos, Node x) {
        long offset = pos.input ? inputOffsets[pos.index] : successorOffsets[pos.index];
        if (pos.subIndex == NOT_ITERABLE) {
            Node old = getNode(node,  offset);
            assert x == null || (pos.input ? inputTypes : successorTypes)[pos.index].isAssignableFrom(x.getClass()) : this + ".set(node, pos, " + x + ") while type is " + (pos.input ? inputTypes : successorTypes)[pos.index];
            putNode(node, offset, x);
            if (pos.input) {
                node.updateUsages(old, x);
            } else {
                node.updatePredecessors(old, x);
            }
        } else {
            NodeList<Node> list = getNodeList(node, offset);
            if (pos.subIndex < list.size()) {
                list.set(pos.subIndex, x);
            } else {
                while (pos.subIndex < list.size() - 1) {
                    list.add(null);
                }
                list.add(x);
            }
        }
    }

    public NodeInputsIterable getInputIterable(final Node node) {
        assert clazz.isInstance(node);
        return new NodeInputsIterable() {

            @Override
            public NodeClassIterator iterator() {
                return new NodeClassIterator(node, inputOffsets, directInputCount);
            }

            @Override
            public boolean contains(Node other) {
                return inputContains(node, other);
            }
        };
    }

    public NodeSuccessorsIterable getSuccessorIterable(final Node node) {
        assert clazz.isInstance(node);
        return new NodeSuccessorsIterable() {
            @Override
            public NodeClassIterator iterator() {
                return new NodeClassIterator(node, successorOffsets, directSuccessorCount);
            }

            @Override
            public boolean contains(Node other) {
                return successorContains(node, other);
            }
        };
    }

    public boolean replaceFirstInput(Node node, Node old, Node other) {
        int index = 0;
        while (index < directInputCount) {
            Node input = getNode(node, inputOffsets[index]);
            if (input == old) {
                assert other == null || inputTypes[index].isAssignableFrom(other.getClass());
                putNode(node, inputOffsets[index], other);
                return true;
            }
            index++;
        }
        while (index < inputOffsets.length) {
            NodeList<Node> list = getNodeList(node, inputOffsets[index]);
            assert list != null : clazz;
            if (list.replaceFirst(old, other)) {
                return true;
            }
            index++;
        }
        return false;
    }

    public boolean replaceFirstSuccessor(Node node, Node old, Node other) {
        int index = 0;
        while (index < directSuccessorCount) {
            Node successor = getNode(node, successorOffsets[index]);
            if (successor == old) {
                assert other == null || successorTypes[index].isAssignableFrom(other.getClass()) : successorTypes[index] + " is not compatible with " + other.getClass();
                putNode(node, successorOffsets[index], other);
                return true;
            }
            index++;
        }
        while (index < successorOffsets.length) {
            NodeList<Node> list = getNodeList(node, successorOffsets[index]);
            assert list != null : clazz + " " + successorOffsets[index] + " " + node;
            if (list.replaceFirst(old, other)) {
                return true;
            }
            index++;
        }
        return false;
    }

    /**
     * Clear all inputs in the given node. This is accomplished by setting input fields to null and replacing input lists with new lists.
     * (which is important so that this method can be used to clear the inputs of cloned nodes.)
     * @param node the node to be cleared
     */
    public void clearInputs(Node node) {
        int index = 0;
        while (index < directInputCount) {
            putNode(node, inputOffsets[index++], null);
        }
        while (index < inputOffsets.length) {
            long curOffset = inputOffsets[index++];
            int size = (getNodeList(node, curOffset)).initialSize;
            // replacing with a new list object is the expected behavior!
            putNodeList(node, curOffset, new NodeInputList<Node>(node, size));
        }
    }

    /**
     * Clear all successors in the given node. This is accomplished by setting successor fields to null and replacing successor lists with new lists.
     * (which is important so that this method can be used to clear the successors of cloned nodes.)
     * @param node the node to be cleared
     */
    public void clearSuccessors(Node node) {
        int index = 0;
        while (index < directSuccessorCount) {
            putNode(node, successorOffsets[index++], null);
        }
        while (index < successorOffsets.length) {
            long curOffset = successorOffsets[index++];
            int size = getNodeList(node, curOffset).initialSize;
            // replacing with a new list object is the expected behavior!
            putNodeList(node, curOffset, new NodeSuccessorList<Node>(node, size));
        }
    }

    /**
     * Copies the inputs from node to newNode. The nodes are expected to be of the exact same NodeClass type.
     * @param node the node from which the inputs should be copied.
     * @param newNode the node to which the inputs should be copied.
     */
    public void copyInputs(Node node, Node newNode) {
        assert node.getClass() == clazz && newNode.getClass() == clazz;

        int index = 0;
        while (index < directInputCount) {
            putNode(newNode, inputOffsets[index], getNode(node, inputOffsets[index]));
            index++;
        }
        while (index < inputOffsets.length) {
            NodeList<Node> list = getNodeList(newNode, inputOffsets[index]);
            list.copy(getNodeList(node, inputOffsets[index]));
            index++;
        }
    }

    /**
     * Copies the successors from node to newNode. The nodes are expected to be of the exact same NodeClass type.
     * @param node the node from which the successors should be copied.
     * @param newNode the node to which the successors should be copied.
     */
    public void copySuccessors(Node node, Node newNode) {
        assert node.getClass() == clazz && newNode.getClass() == clazz;

        int index = 0;
        while (index < directSuccessorCount) {
            putNode(newNode, successorOffsets[index], getNode(node, successorOffsets[index]));
            index++;
        }
        while (index < successorOffsets.length) {
            NodeList<Node> list = getNodeList(newNode, successorOffsets[index]);
            list.copy(getNodeList(node, successorOffsets[index]));
            index++;
        }
    }

    public boolean edgesEqual(Node node, Node other) {
        assert node.getClass() == clazz && other.getClass() == clazz;

        int index = 0;
        while (index < directInputCount) {
            if (getNode(other, inputOffsets[index]) != getNode(node, inputOffsets[index])) {
                return false;
            }
            index++;
        }
        while (index < inputOffsets.length) {
            NodeList<Node> list = getNodeList(other, inputOffsets[index]);
            if (!list.equals(getNodeList(node, inputOffsets[index]))) {
                return false;
            }
            index++;
        }

        index = 0;
        while (index < directSuccessorCount) {
            if (getNode(other, successorOffsets[index]) != getNode(node, successorOffsets[index])) {
                return false;
            }
            index++;
        }
        while (index < successorOffsets.length) {
            NodeList<Node> list = getNodeList(other, successorOffsets[index]);
            if (!list.equals(getNodeList(node, successorOffsets[index]))) {
                return false;
            }
            index++;
        }
        return true;
    }

    public boolean inputContains(Node node, Node other) {
        assert node.getClass() == clazz;

        int index = 0;
        while (index < directInputCount) {
            if (getNode(node, inputOffsets[index]) == other) {
                return true;
            }
            index++;
        }
        while (index < inputOffsets.length) {
            NodeList<Node> list = getNodeList(node, inputOffsets[index]);
            if (list.contains(other)) {
                return true;
            }
            index++;
        }
        return false;
    }

    public boolean successorContains(Node node, Node other) {
        assert node.getClass() == clazz;

        int index = 0;
        while (index < directSuccessorCount) {
            if (getNode(node, successorOffsets[index]) == other) {
                return true;
            }
            index++;
        }
        while (index < successorOffsets.length) {
            NodeList<Node> list = getNodeList(node, successorOffsets[index]);
            if (list.contains(other)) {
                return true;
            }
            index++;
        }
        return false;
    }

    public int directInputCount() {
        return directInputCount;
    }

    public int directSuccessorCount() {
        return directSuccessorCount;
    }

    static Map<Node, Node> addGraphDuplicate(Graph graph, Iterable<Node> nodes, Map<Node, Node> replacements) {
        if (replacements == null) {
            replacements = Collections.emptyMap();
        }
        Map<Node, Node> newNodes = new IdentityHashMap<Node, Node>();
        // create node duplicates
        for (Node node : nodes) {
            if (node != null && !replacements.containsKey(node)) {
                assert !node.isDeleted() : "trying to duplicate deleted node";
                Node newNode = node.clone(graph);
                assert newNode.getClass() == node.getClass();
                newNodes.put(node, newNode);
            }
        }
        // re-wire inputs
        for (Entry<Node, Node> entry : newNodes.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            for (NodeClassIterator iter = oldNode.inputs().iterator(); iter.hasNext();) {
                Position pos = iter.nextPosition();
                Node input = oldNode.getNodeClass().get(oldNode, pos);
                Node target = replacements.get(input);
                if (target == null) {
                    target = newNodes.get(input);
                }
                node.getNodeClass().set(node, pos, target);
            }
        }
        for (Entry<Node, Node> entry : replacements.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            if (oldNode == node) {
                continue;
            }
            for (NodeClassIterator iter = oldNode.inputs().iterator(); iter.hasNext();) {
                Position pos = iter.nextPosition();
                Node input = oldNode.getNodeClass().get(oldNode, pos);
                if (newNodes.containsKey(input)) {
                    node.getNodeClass().set(node, pos, newNodes.get(input));
                }
            }
        }

        // re-wire successors
        for (Entry<Node, Node> entry : newNodes.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            for (NodeClassIterator iter = oldNode.successors().iterator(); iter.hasNext();) {
                Position pos = iter.nextPosition();
                Node succ = oldNode.getNodeClass().get(oldNode, pos);
                Node target = replacements.get(succ);
                if (target == null) {
                    target = newNodes.get(succ);
                }
                node.getNodeClass().set(node, pos, target);
            }
        }
        for (Entry<Node, Node> entry : replacements.entrySet()) {
            Node oldNode = entry.getKey();
            Node node = entry.getValue();
            if (oldNode == node) {
                continue;
            }
            for (NodeClassIterator iter = oldNode.successors().iterator(); iter.hasNext();) {
                Position pos = iter.nextPosition();
                Node succ = oldNode.getNodeClass().get(oldNode, pos);
                if (newNodes.containsKey(succ)) {
                    node.getNodeClass().set(node, pos, newNodes.get(succ));
                }
            }
        }
        return newNodes;
    }
}
