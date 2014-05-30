/*
 * Copyright (c) 2011, 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph;

import static com.oracle.graal.graph.Graph.*;
import static com.oracle.graal.graph.util.CollectionsAccess.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.Node.Input;
import com.oracle.graal.graph.Node.Successor;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.graph.spi.*;

/**
 * Metadata for every {@link Node} type. The metadata includes:
 * <ul>
 * <li>The offsets of fields annotated with {@link Input} and {@link Successor} as well as methods
 * for iterating over such fields.</li>
 * <li>The identifier for an {@link IterableNodeType} class.</li>
 * </ul>
 */
public final class NodeClass extends FieldIntrospection {

    private static final Object GetNodeClassLock = new Object();

    /**
     * Gets the {@link NodeClass} associated with a given {@link Class}.
     */
    @SuppressWarnings("unchecked")
    public static NodeClass get(Class<?> c) {
        Class<? extends Node> key = (Class<? extends Node>) c;
        NodeClass value = (NodeClass) allClasses.get(key);
        // The fact that {@link ConcurrentHashMap#put} and {@link ConcurrentHashMap#get}
        // are used makes the double-checked locking idiom work.
        if (value == null) {
            // The creation of a NodeClass must be serialized as the NodeClass constructor accesses
            // both FieldIntrospection.allClasses and NodeClass.nextIterableId.
            synchronized (GetNodeClassLock) {
                value = (NodeClass) allClasses.get(key);
                if (value == null) {
                    value = new NodeClass(key);
                    Object old = allClasses.putIfAbsent(key, value);
                    assert old == null : old + "   " + key;
                }
            }
        }
        return value;
    }

    public static final int NOT_ITERABLE = -1;
    public static final int NODE_LIST = -2;

    private static final Class<?> NODE_CLASS = Node.class;
    private static final Class<?> INPUT_LIST_CLASS = NodeInputList.class;
    private static final Class<?> SUCCESSOR_LIST_CLASS = NodeSuccessorList.class;

    private static int nextIterableId = 0;

    private final int directInputCount;
    private final long[] inputOffsets;
    private final InputType[] inputTypes;
    private final int directSuccessorCount;
    private final long[] successorOffsets;
    private final Class<?>[] dataTypes;
    private final boolean canGVN;
    private final boolean isLeafNode;
    private final int startGVNNumber;
    private final String shortName;
    private final String nameTemplate;
    private final int iterableId;
    private final EnumSet<InputType> allowedUsageTypes;
    private int[] iterableIds;

    private static final DebugMetric ITERABLE_NODE_TYPES = Debug.metric("IterableNodeTypes");
    private final DebugMetric nodeIterableCount;

    /**
     * Determines if this node type implements {@link Canonicalizable}.
     */
    private final boolean isCanonicalizable;

    /**
     * Determines if this node type implements {@link Simplifiable}.
     */
    private final boolean isSimplifiable;

    public NodeClass(Class<?> clazz) {
        this(clazz, new DefaultCalcOffset(), null, 0);
    }

    public NodeClass(Class<?> clazz, CalcOffset calcOffset, int[] presetIterableIds, int presetIterableId) {
        super(clazz);
        assert NODE_CLASS.isAssignableFrom(clazz);
        this.isCanonicalizable = Canonicalizable.class.isAssignableFrom(clazz);
        this.isSimplifiable = Simplifiable.class.isAssignableFrom(clazz);

        FieldScanner scanner = new FieldScanner(calcOffset);
        scanner.scan(clazz);

        directInputCount = scanner.inputOffsets.size();
        inputOffsets = sortedLongCopy(scanner.inputOffsets, scanner.inputListOffsets);
        inputTypes = new InputType[inputOffsets.length];
        for (int i = 0; i < inputOffsets.length; i++) {
            inputTypes[i] = scanner.types.get(inputOffsets[i]);
            assert inputTypes[i] != null;
        }
        directSuccessorCount = scanner.successorOffsets.size();
        successorOffsets = sortedLongCopy(scanner.successorOffsets, scanner.successorListOffsets);

        dataOffsets = sortedLongCopy(scanner.dataOffsets);
        dataTypes = new Class[dataOffsets.length];
        for (int i = 0; i < dataOffsets.length; i++) {
            dataTypes[i] = scanner.fieldTypes.get(dataOffsets[i]);
        }

        fieldNames = scanner.fieldNames;
        fieldTypes = scanner.fieldTypes;

        canGVN = Node.ValueNumberable.class.isAssignableFrom(clazz);
        startGVNNumber = clazz.hashCode();

        String newShortName = clazz.getSimpleName();
        if (newShortName.endsWith("Node") && !newShortName.equals("StartNode") && !newShortName.equals("EndNode")) {
            newShortName = newShortName.substring(0, newShortName.length() - 4);
        }
        String newNameTemplate = null;
        NodeInfo info = clazz.getAnnotation(NodeInfo.class);
        if (info != null) {
            if (!info.shortName().isEmpty()) {
                newShortName = info.shortName();
            }
            if (!info.nameTemplate().isEmpty()) {
                newNameTemplate = info.nameTemplate();
            }
        }
        EnumSet<InputType> newAllowedUsageTypes = EnumSet.noneOf(InputType.class);
        Class<?> current = clazz;
        do {
            NodeInfo currentInfo = current.getAnnotation(NodeInfo.class);
            if (currentInfo != null) {
                if (currentInfo.allowedUsageTypes().length > 0) {
                    newAllowedUsageTypes.addAll(Arrays.asList(currentInfo.allowedUsageTypes()));
                }
            }
            current = current.getSuperclass();
        } while (current != Node.class);
        this.nameTemplate = newNameTemplate == null ? newShortName : newNameTemplate;
        this.allowedUsageTypes = newAllowedUsageTypes;
        this.shortName = newShortName;
        if (presetIterableIds != null) {
            this.iterableIds = presetIterableIds;
            this.iterableId = presetIterableId;
        } else if (IterableNodeType.class.isAssignableFrom(clazz)) {
            ITERABLE_NODE_TYPES.increment();
            this.iterableId = nextIterableId++;
            List<NodeClass> existingClasses = new LinkedList<>();
            for (FieldIntrospection nodeClass : allClasses.values()) {
                if (clazz.isAssignableFrom(nodeClass.getClazz())) {
                    existingClasses.add((NodeClass) nodeClass);
                }
                if (nodeClass.getClazz().isAssignableFrom(clazz) && IterableNodeType.class.isAssignableFrom(nodeClass.getClazz())) {
                    NodeClass superNodeClass = (NodeClass) nodeClass;
                    superNodeClass.iterableIds = Arrays.copyOf(superNodeClass.iterableIds, superNodeClass.iterableIds.length + 1);
                    superNodeClass.iterableIds[superNodeClass.iterableIds.length - 1] = this.iterableId;
                }
            }
            int[] ids = new int[existingClasses.size() + 1];
            ids[0] = iterableId;
            int i = 1;
            for (NodeClass other : existingClasses) {
                ids[i++] = other.iterableId;
            }
            this.iterableIds = ids;
        } else {
            this.iterableId = NOT_ITERABLE;
            this.iterableIds = null;
        }

        isLeafNode = (this.inputOffsets.length == 0 && this.successorOffsets.length == 0);
        nodeIterableCount = Debug.metric("NodeIterable_%s", shortName);
    }

    @Override
    protected void rescanFieldOffsets(CalcOffset calc) {
        FieldScanner scanner = new FieldScanner(calc);
        scanner.scan(getClazz());
        assert directInputCount == scanner.inputOffsets.size();
        copyInto(inputOffsets, sortedLongCopy(scanner.inputOffsets, scanner.inputListOffsets));
        assert directSuccessorCount == scanner.successorOffsets.size();
        copyInto(successorOffsets, sortedLongCopy(scanner.successorOffsets, scanner.successorListOffsets));
        copyInto(dataOffsets, sortedLongCopy(scanner.dataOffsets));

        for (int i = 0; i < dataOffsets.length; i++) {
            dataTypes[i] = scanner.fieldTypes.get(dataOffsets[i]);
        }

        fieldNames.clear();
        fieldNames.putAll(scanner.fieldNames);
        fieldTypes.clear();
        fieldTypes.putAll(scanner.fieldTypes);
    }

    public String shortName() {
        return shortName;
    }

    public int[] iterableIds() {
        nodeIterableCount.increment();
        return iterableIds;
    }

    public int iterableId() {
        return iterableId;
    }

    public boolean valueNumberable() {
        return canGVN;
    }

    public boolean isLeafNode() {
        return isLeafNode;
    }

    /**
     * Determines if this node type implements {@link Canonicalizable}.
     */
    public boolean isCanonicalizable() {
        return isCanonicalizable;
    }

    /**
     * Determines if this node type implements {@link Simplifiable}.
     */
    public boolean isSimplifiable() {
        return isSimplifiable;
    }

    public static int cacheSize() {
        return nextIterableId;
    }

    public EnumSet<InputType> getAllowedUsageTypes() {
        return allowedUsageTypes;
    }

    protected static class FieldScanner extends BaseFieldScanner {

        public final ArrayList<Long> inputOffsets = new ArrayList<>();
        public final ArrayList<Long> inputListOffsets = new ArrayList<>();
        public final ArrayList<Long> successorOffsets = new ArrayList<>();
        public final ArrayList<Long> successorListOffsets = new ArrayList<>();
        public final HashMap<Long, InputType> types = new HashMap<>();
        public final HashMap<Long, String> names = new HashMap<>();

        protected FieldScanner(CalcOffset calc) {
            super(calc);
        }

        @Override
        protected void scanField(Field field, Class<?> type, long offset) {
            if (field.isAnnotationPresent(Node.Input.class)) {
                assert !field.isAnnotationPresent(Node.Successor.class) : "field cannot be both input and successor";
                Input inputAnnotation = field.getAnnotation(Node.Input.class);
                if (INPUT_LIST_CLASS.isAssignableFrom(type)) {
                    GraalInternalError.guarantee(Modifier.isFinal(field.getModifiers()), "NodeInputList input field %s should be final", field);
                    GraalInternalError.guarantee(!Modifier.isPublic(field.getModifiers()), "NodeInputList input field %s should not be public", field);
                    inputListOffsets.add(offset);
                } else {
                    GraalInternalError.guarantee(NODE_CLASS.isAssignableFrom(type) || type.isInterface(), "invalid input type: %s", type);
                    GraalInternalError.guarantee(!Modifier.isFinal(field.getModifiers()), "Node input field %s should not be final", field);
                    GraalInternalError.guarantee(Modifier.isPrivate(field.getModifiers()), "Node input field %s should be private", field);
                    inputOffsets.add(offset);
                }
                types.put(offset, inputAnnotation.value());
                names.put(offset, field.getName());
                if (inputAnnotation.value() != InputType.Value) {
                    fieldNames.put(offset, field.getName() + "#NDF");
                }
            } else if (field.isAnnotationPresent(Node.Successor.class)) {
                if (SUCCESSOR_LIST_CLASS.isAssignableFrom(type)) {
                    GraalInternalError.guarantee(Modifier.isFinal(field.getModifiers()), "NodeSuccessorList successor field % should be final", field);
                    GraalInternalError.guarantee(!Modifier.isPublic(field.getModifiers()), "NodeSuccessorList successor field %s should not be public", field);
                    successorListOffsets.add(offset);
                } else {
                    GraalInternalError.guarantee(NODE_CLASS.isAssignableFrom(type), "invalid successor type: %s", type);
                    GraalInternalError.guarantee(!Modifier.isFinal(field.getModifiers()), "Node successor field %s should not be final", field);
                    GraalInternalError.guarantee(Modifier.isPrivate(field.getModifiers()), "Node successor field %s should be private", field);
                    successorOffsets.add(offset);
                }
                names.put(offset, field.getName());
            } else {
                GraalInternalError.guarantee(!NODE_CLASS.isAssignableFrom(type) || field.getName().equals("Null"), "suspicious node field: %s", field);
                GraalInternalError.guarantee(!INPUT_LIST_CLASS.isAssignableFrom(type), "suspicious node input list field: %s", field);
                GraalInternalError.guarantee(!SUCCESSOR_LIST_CLASS.isAssignableFrom(type), "suspicious node successor list field: %s", field);
                dataOffsets.add(offset);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("NodeClass ").append(getClazz().getSimpleName()).append(" [");
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

    /**
     * Describes an edge slot for a {@link NodeClass}.
     *
     * @see NodeClass#get(Node, Position)
     * @see NodeClass#getName(Position)
     */
    public static final class Position {

        private final boolean input;
        private final int index;
        private final int subIndex;

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

        public InputType getInputType(Node node) {
            return node.getNodeClass().getInputType(this);
        }

        public String getInputName(Node node) {
            return node.getNodeClass().getName(this);
        }

        public void set(Node node, Node value) {
            node.getNodeClass().set(node, this, value);
        }

        public boolean isValidFor(Node node, Node from) {
            return node.getNodeClass().isValid(this, from.getNodeClass());
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

        public int getSubIndex() {
            return subIndex;
        }

        public int getIndex() {
            return index;
        }

        public boolean isInput() {
            return input;
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

    private static void putNodeList(Node node, long offset, NodeList<?> value) {
        unsafe.putObject(node, offset, value);
    }

    /**
     * An iterator that will iterate over the fields given in {@link #getOffsets()}. The first
     * {@link #getDirectCount()} offsets are treated as fields of type {@link Node}, while the rest
     * of the fields are treated as {@link NodeList}s. All elements of these NodeLists will be
     * visited by the iterator as well. This iterator can be used to iterate over the inputs or
     * successors of a node.
     *
     * An iterator of this type will not return null values, unless the field values are modified
     * concurrently. Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    public abstract static class NodeClassIterator implements Iterator<Node> {
        protected final Node node;
        private int index;
        private int subIndex;

        /**
         * Creates an iterator that will iterate over fields in the given node.
         *
         * @param node the node which contains the fields.
         */
        NodeClassIterator(Node node) {
            this.node = node;
            index = NOT_ITERABLE;
            subIndex = 0;
        }

        void forward() {
            if (index < getDirectCount()) {
                index++;
                while (index < getDirectCount()) {
                    Node element = getNode(node, getOffsets()[index]);
                    if (element != null) {
                        return;
                    }
                    index++;
                }
            } else {
                subIndex++;
            }
            while (index < getOffsets().length) {
                NodeList<Node> list = getNodeList(node, getOffsets()[index]);
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
            if (index < getDirectCount()) {
                return getNode(node, getOffsets()[index]);
            } else if (index < getOffsets().length) {
                NodeList<Node> list = getNodeList(node, getOffsets()[index]);
                return list.get(subIndex);
            }
            throw new NoSuchElementException();
        }

        @Override
        public boolean hasNext() {
            return index < getOffsets().length;
        }

        @Override
        public Node next() {
            try {
                return nextElement();
            } finally {
                forward();
            }
        }

        public Position nextPosition() {
            try {
                if (index < getDirectCount()) {
                    return new Position(getOffsets() == getNodeClass().inputOffsets, index, NOT_ITERABLE);
                } else {
                    return new Position(getOffsets() == getNodeClass().inputOffsets, index, subIndex);
                }
            } finally {
                forward();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        protected abstract int getDirectCount();

        protected abstract long[] getOffsets();

        protected abstract NodeClass getNodeClass();
    }

    private class NodeClassInputsIterator extends NodeClassIterator {
        NodeClassInputsIterator(Node node) {
            this(node, true);
        }

        NodeClassInputsIterator(Node node, boolean forward) {
            super(node);
            assert NodeClass.this == node.getNodeClass();
            if (forward) {
                forward();
            }
        }

        @Override
        protected int getDirectCount() {
            return directInputCount;
        }

        @Override
        protected long[] getOffsets() {
            return inputOffsets;
        }

        @Override
        protected NodeClass getNodeClass() {
            return NodeClass.this;
        }
    }

    private final class NodeClassInputsWithModCountIterator extends NodeClassInputsIterator {
        private final int modCount;

        private NodeClassInputsWithModCountIterator(Node node) {
            super(node, false);
            assert MODIFICATION_COUNTS_ENABLED;
            this.modCount = node.modCount();
            forward();
        }

        @Override
        public boolean hasNext() {
            try {
                return super.hasNext();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Node next() {
            try {
                return super.next();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Position nextPosition() {
            try {
                return super.nextPosition();
            } finally {
                assert modCount == node.modCount();
            }
        }
    }

    private class NodeClassSuccessorsIterator extends NodeClassIterator {
        NodeClassSuccessorsIterator(Node node) {
            this(node, true);
        }

        NodeClassSuccessorsIterator(Node node, boolean forward) {
            super(node);
            assert NodeClass.this == node.getNodeClass();
            if (forward) {
                forward();
            }
        }

        @Override
        protected int getDirectCount() {
            return directSuccessorCount;
        }

        @Override
        protected long[] getOffsets() {
            return successorOffsets;
        }

        @Override
        protected NodeClass getNodeClass() {
            return NodeClass.this;
        }
    }

    private final class NodeClassSuccessorsWithModCountIterator extends NodeClassSuccessorsIterator {
        private final int modCount;

        private NodeClassSuccessorsWithModCountIterator(Node node) {
            super(node, false);
            assert MODIFICATION_COUNTS_ENABLED;
            this.modCount = node.modCount();
            forward();
        }

        @Override
        public boolean hasNext() {
            try {
                return super.hasNext();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Node next() {
            try {
                return super.next();
            } finally {
                assert modCount == node.modCount() : "must not be modified";
            }
        }

        @Override
        public Position nextPosition() {
            try {
                return super.nextPosition();
            } finally {
                assert modCount == node.modCount();
            }
        }
    }

    private static int deepHashCode0(Object o) {
        if (o instanceof Object[]) {
            return Arrays.deepHashCode((Object[]) o);
        } else if (o instanceof byte[]) {
            return Arrays.hashCode((byte[]) o);
        } else if (o instanceof short[]) {
            return Arrays.hashCode((short[]) o);
        } else if (o instanceof int[]) {
            return Arrays.hashCode((int[]) o);
        } else if (o instanceof long[]) {
            return Arrays.hashCode((long[]) o);
        } else if (o instanceof char[]) {
            return Arrays.hashCode((char[]) o);
        } else if (o instanceof float[]) {
            return Arrays.hashCode((float[]) o);
        } else if (o instanceof double[]) {
            return Arrays.hashCode((double[]) o);
        } else if (o instanceof boolean[]) {
            return Arrays.hashCode((boolean[]) o);
        } else if (o != null) {
            return o.hashCode();
        } else {
            return 0;
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
                    } else if (type == Long.TYPE) {
                        long longValue = unsafe.getLong(n, dataOffsets[i]);
                        number += longValue ^ (longValue >>> 32);
                    } else if (type == Boolean.TYPE) {
                        boolean booleanValue = unsafe.getBoolean(n, dataOffsets[i]);
                        if (booleanValue) {
                            number += 7;
                        }
                    } else if (type == Float.TYPE) {
                        float floatValue = unsafe.getFloat(n, dataOffsets[i]);
                        number += Float.floatToRawIntBits(floatValue);
                    } else if (type == Double.TYPE) {
                        double doubleValue = unsafe.getDouble(n, dataOffsets[i]);
                        long longValue = Double.doubleToRawLongBits(doubleValue);
                        number += longValue ^ (longValue >>> 32);
                    } else if (type == Short.TYPE) {
                        short shortValue = unsafe.getShort(n, dataOffsets[i]);
                        number += shortValue;
                    } else if (type == Character.TYPE) {
                        char charValue = unsafe.getChar(n, dataOffsets[i]);
                        number += charValue;
                    } else if (type == Byte.TYPE) {
                        byte byteValue = unsafe.getByte(n, dataOffsets[i]);
                        number += byteValue;
                    } else {
                        assert false : "unhandled property type: " + type;
                    }
                } else {
                    Object o = unsafe.getObject(n, dataOffsets[i]);
                    number += deepHashCode0(o);
                }
                number *= 13;
            }
        }
        return number;
    }

    private static boolean deepEquals0(Object e1, Object e2) {
        assert e1 != null;
        boolean eq;
        if (e1 instanceof Object[] && e2 instanceof Object[]) {
            eq = Arrays.deepEquals((Object[]) e1, (Object[]) e2);
        } else if (e1 instanceof byte[] && e2 instanceof byte[]) {
            eq = Arrays.equals((byte[]) e1, (byte[]) e2);
        } else if (e1 instanceof short[] && e2 instanceof short[]) {
            eq = Arrays.equals((short[]) e1, (short[]) e2);
        } else if (e1 instanceof int[] && e2 instanceof int[]) {
            eq = Arrays.equals((int[]) e1, (int[]) e2);
        } else if (e1 instanceof long[] && e2 instanceof long[]) {
            eq = Arrays.equals((long[]) e1, (long[]) e2);
        } else if (e1 instanceof char[] && e2 instanceof char[]) {
            eq = Arrays.equals((char[]) e1, (char[]) e2);
        } else if (e1 instanceof float[] && e2 instanceof float[]) {
            eq = Arrays.equals((float[]) e1, (float[]) e2);
        } else if (e1 instanceof double[] && e2 instanceof double[]) {
            eq = Arrays.equals((double[]) e1, (double[]) e2);
        } else if (e1 instanceof boolean[] && e2 instanceof boolean[]) {
            eq = Arrays.equals((boolean[]) e1, (boolean[]) e2);
        } else {
            eq = e1.equals(e2);
        }
        return eq;
    }

    public boolean valueEqual(Node a, Node b) {
        if (a.getClass() != b.getClass()) {
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
                } else if (type == Long.TYPE) {
                    long aLong = unsafe.getLong(a, dataOffsets[i]);
                    long bLong = unsafe.getLong(b, dataOffsets[i]);
                    if (aLong != bLong) {
                        return false;
                    }
                } else if (type == Float.TYPE) {
                    float aFloat = unsafe.getFloat(a, dataOffsets[i]);
                    float bFloat = unsafe.getFloat(b, dataOffsets[i]);
                    if (aFloat != bFloat) {
                        return false;
                    }
                } else if (type == Double.TYPE) {
                    double aDouble = unsafe.getDouble(a, dataOffsets[i]);
                    double bDouble = unsafe.getDouble(b, dataOffsets[i]);
                    if (aDouble != bDouble) {
                        return false;
                    }
                } else if (type == Short.TYPE) {
                    short aShort = unsafe.getShort(a, dataOffsets[i]);
                    short bShort = unsafe.getShort(b, dataOffsets[i]);
                    if (aShort != bShort) {
                        return false;
                    }
                } else if (type == Character.TYPE) {
                    char aChar = unsafe.getChar(a, dataOffsets[i]);
                    char bChar = unsafe.getChar(b, dataOffsets[i]);
                    if (aChar != bChar) {
                        return false;
                    }
                } else if (type == Byte.TYPE) {
                    byte aByte = unsafe.getByte(a, dataOffsets[i]);
                    byte bByte = unsafe.getByte(b, dataOffsets[i]);
                    if (aByte != bByte) {
                        return false;
                    }
                } else {
                    assert false : "unhandled type: " + type;
                }
            } else {
                Object objectA = unsafe.getObject(a, dataOffsets[i]);
                Object objectB = unsafe.getObject(b, dataOffsets[i]);
                if (objectA != objectB) {
                    if (objectA != null && objectB != null) {
                        if (!deepEquals0(objectA, objectB)) {
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

    public boolean isValid(Position pos, NodeClass from) {
        if (this == from) {
            return true;
        }
        long[] offsets = pos.isInput() ? inputOffsets : successorOffsets;
        if (pos.getIndex() >= offsets.length) {
            return false;
        }
        long[] fromOffsets = pos.isInput() ? from.inputOffsets : from.successorOffsets;
        if (pos.getIndex() >= fromOffsets.length) {
            return false;
        }
        return offsets[pos.getIndex()] == fromOffsets[pos.getIndex()];
    }

    public Node get(Node node, Position pos) {
        long offset = pos.isInput() ? inputOffsets[pos.getIndex()] : successorOffsets[pos.getIndex()];
        if (pos.getSubIndex() == NOT_ITERABLE) {
            return getNode(node, offset);
        } else {
            return getNodeList(node, offset).get(pos.getSubIndex());
        }
    }

    public InputType getInputType(Position pos) {
        assert pos.isInput();
        return inputTypes[pos.getIndex()];
    }

    public NodeList<?> getNodeList(Node node, Position pos) {
        long offset = pos.isInput() ? inputOffsets[pos.getIndex()] : successorOffsets[pos.getIndex()];
        assert pos.getSubIndex() == NODE_LIST;
        return getNodeList(node, offset);
    }

    public String getName(Position pos) {
        return fieldNames.get(pos.isInput() ? inputOffsets[pos.getIndex()] : successorOffsets[pos.getIndex()]);
    }

    public String getPropertyName(int pos) {
        return fieldNames.get(dataOffsets[pos]);
    }

    public Class<?> getPropertyType(int pos) {
        return fieldTypes.get(dataOffsets[pos]);
    }

    public Object getProperty(Node node, int pos) {
        long dataOffset = dataOffsets[pos];
        Class<?> type = fieldTypes.get(dataOffset);
        Object value = null;
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                value = unsafe.getInt(node, dataOffset);
            } else if (type == Long.TYPE) {
                value = unsafe.getLong(node, dataOffset);
            } else if (type == Boolean.TYPE) {
                value = unsafe.getBoolean(node, dataOffset);
            } else if (type == Float.TYPE) {
                value = unsafe.getFloat(node, dataOffset);
            } else if (type == Double.TYPE) {
                value = unsafe.getDouble(node, dataOffset);
            } else if (type == Short.TYPE) {
                value = unsafe.getShort(node, dataOffset);
            } else if (type == Character.TYPE) {
                value = unsafe.getChar(node, dataOffset);
            } else if (type == Byte.TYPE) {
                value = unsafe.getByte(node, dataOffset);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            value = unsafe.getObject(node, dataOffset);
        }
        return value;
    }

    public void setProperty(Node node, int pos, Object value) {
        long dataOffset = dataOffsets[pos];
        Class<?> type = fieldTypes.get(dataOffset);
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                unsafe.putInt(node, dataOffset, (Integer) value);
            } else if (type == Long.TYPE) {
                unsafe.putLong(node, dataOffset, (Long) value);
            } else if (type == Boolean.TYPE) {
                unsafe.putBoolean(node, dataOffset, (Boolean) value);
            } else if (type == Float.TYPE) {
                unsafe.putFloat(node, dataOffset, (Float) value);
            } else if (type == Double.TYPE) {
                unsafe.putDouble(node, dataOffset, (Double) value);
            } else if (type == Short.TYPE) {
                unsafe.putShort(node, dataOffset, (Short) value);
            } else if (type == Character.TYPE) {
                unsafe.putChar(node, dataOffset, (Character) value);
            } else if (type == Byte.TYPE) {
                unsafe.putByte(node, dataOffset, (Byte) value);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            assert value == null || !value.getClass().isPrimitive();
            unsafe.putObject(node, dataOffset, value);
        }
    }

    void updateInputSuccInPlace(Node node, InplaceUpdateClosure duplicationReplacement) {
        int index = 0;
        while (index < directInputCount) {
            Node input = getNode(node, inputOffsets[index]);
            if (input != null) {
                Node newInput = duplicationReplacement.replacement(input, true);
                node.updateUsages(null, newInput);
                assert newInput == null || fieldTypes.get(inputOffsets[index]).isAssignableFrom(newInput.getClass()) : "Can not assign " + newInput.getClass() + " to " +
                                fieldTypes.get(inputOffsets[index]) + " in " + node;
                putNode(node, inputOffsets[index], newInput);
            }
            index++;
        }

        if (index < inputOffsets.length) {
            updateInputLists(node, duplicationReplacement, index);
        }

        index = 0;
        while (index < directSuccessorCount) {
            Node successor = getNode(node, successorOffsets[index]);
            if (successor != null) {
                Node newSucc = duplicationReplacement.replacement(successor, false);
                node.updatePredecessor(null, newSucc);
                assert newSucc == null || fieldTypes.get(successorOffsets[index]).isAssignableFrom(newSucc.getClass()) : fieldTypes.get(successorOffsets[index]) + " is not compatible with " +
                                newSucc.getClass();
                putNode(node, successorOffsets[index], newSucc);
            }
            index++;
        }

        if (index < successorOffsets.length) {
            updateSuccLists(node, duplicationReplacement, index);
        }
    }

    private void updateInputLists(Node node, InplaceUpdateClosure duplicationReplacement, int startIndex) {
        int index = startIndex;
        while (index < inputOffsets.length) {
            NodeList<Node> list = getNodeList(node, inputOffsets[index]);
            assert list != null : getClazz();
            putNodeList(node, inputOffsets[index], updateInputListCopy(list, node, duplicationReplacement));
            index++;
        }
    }

    private void updateSuccLists(Node node, InplaceUpdateClosure duplicationReplacement, int startIndex) {
        int index = startIndex;
        while (index < successorOffsets.length) {
            NodeList<Node> list = getNodeList(node, successorOffsets[index]);
            assert list != null : getClazz();
            putNodeList(node, successorOffsets[index], updateSuccListCopy(list, node, duplicationReplacement));
            index++;
        }
    }

    private static NodeInputList<Node> updateInputListCopy(NodeList<Node> list, Node node, InplaceUpdateClosure duplicationReplacement) {
        int size = list.size();
        NodeInputList<Node> result = new NodeInputList<>(node, size);
        for (int i = 0; i < list.count(); ++i) {
            Node oldNode = list.get(i);
            if (oldNode != null) {
                Node newNode = duplicationReplacement.replacement(oldNode, true);
                result.set(i, newNode);
            }
        }
        return result;
    }

    private static NodeSuccessorList<Node> updateSuccListCopy(NodeList<Node> list, Node node, InplaceUpdateClosure duplicationReplacement) {
        int size = list.size();
        NodeSuccessorList<Node> result = new NodeSuccessorList<>(node, size);
        for (int i = 0; i < list.count(); ++i) {
            Node oldNode = list.get(i);
            if (oldNode != null) {
                Node newNode = duplicationReplacement.replacement(oldNode, false);
                result.set(i, newNode);
            }
        }
        return result;
    }

    public void set(Node node, Position pos, Node x) {
        long offset = pos.isInput() ? inputOffsets[pos.getIndex()] : successorOffsets[pos.getIndex()];
        if (pos.getSubIndex() == NOT_ITERABLE) {
            Node old = getNode(node, offset);
            assert x == null || fieldTypes.get((pos.isInput() ? inputOffsets : successorOffsets)[pos.getIndex()]).isAssignableFrom(x.getClass()) : this + ".set(node, pos, " + x + ")";
            putNode(node, offset, x);
            if (pos.isInput()) {
                node.updateUsages(old, x);
            } else {
                node.updatePredecessor(old, x);
            }
        } else {
            NodeList<Node> list = getNodeList(node, offset);
            if (pos.getSubIndex() < list.size()) {
                list.set(pos.getSubIndex(), x);
            } else {
                while (list.size() < pos.getSubIndex()) {
                    list.add(null);
                }
                list.add(x);
            }
        }
    }

    public NodeClassIterable getInputIterable(final Node node) {
        assert getClazz().isInstance(node);
        return new NodeClassIterable() {

            @Override
            public NodeClassIterator iterator() {
                if (MODIFICATION_COUNTS_ENABLED) {
                    return new NodeClassInputsWithModCountIterator(node);
                } else {
                    return new NodeClassInputsIterator(node);
                }
            }

            @Override
            public boolean contains(Node other) {
                return inputContains(node, other);
            }
        };
    }

    public NodeClassIterable getSuccessorIterable(final Node node) {
        assert getClazz().isInstance(node);
        return new NodeClassIterable() {

            @Override
            public NodeClassIterator iterator() {
                if (MODIFICATION_COUNTS_ENABLED) {
                    return new NodeClassSuccessorsWithModCountIterator(node);
                } else {
                    return new NodeClassSuccessorsIterator(node);
                }
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
                assert other == null || fieldTypes.get(inputOffsets[index]).isAssignableFrom(other.getClass()) : "Can not assign " + other.getClass() + " to " + fieldTypes.get(inputOffsets[index]) +
                                " in " + node;
                putNode(node, inputOffsets[index], other);
                return true;
            }
            index++;
        }
        while (index < inputOffsets.length) {
            NodeList<Node> list = getNodeList(node, inputOffsets[index]);
            assert list != null : getClazz();
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
                assert other == null || fieldTypes.get(successorOffsets[index]).isAssignableFrom(other.getClass()) : fieldTypes.get(successorOffsets[index]) + " is not compatible with " +
                                other.getClass();
                putNode(node, successorOffsets[index], other);
                return true;
            }
            index++;
        }
        while (index < successorOffsets.length) {
            NodeList<Node> list = getNodeList(node, successorOffsets[index]);
            assert list != null : getClazz() + " " + successorOffsets[index] + " " + node;
            if (list.replaceFirst(old, other)) {
                return true;
            }
            index++;
        }
        return false;
    }

    /**
     * Clear all inputs in the given node. This is accomplished by setting input fields to null and
     * replacing input lists with new lists. (which is important so that this method can be used to
     * clear the inputs of cloned nodes.)
     *
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
            putNodeList(node, curOffset, new NodeInputList<>(node, size));
        }
    }

    /**
     * Clear all successors in the given node. This is accomplished by setting successor fields to
     * null and replacing successor lists with new lists. (which is important so that this method
     * can be used to clear the successors of cloned nodes.)
     *
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
            putNodeList(node, curOffset, new NodeSuccessorList<>(node, size));
        }
    }

    /**
     * Copies the inputs from node to newNode. The nodes are expected to be of the exact same
     * NodeClass type.
     *
     * @param node the node from which the inputs should be copied.
     * @param newNode the node to which the inputs should be copied.
     */
    public void copyInputs(Node node, Node newNode) {
        assert node.getClass() == getClazz() && newNode.getClass() == getClazz();

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
     * Copies the successors from node to newNode. The nodes are expected to be of the exact same
     * NodeClass type.
     *
     * @param node the node from which the successors should be copied.
     * @param newNode the node to which the successors should be copied.
     */
    public void copySuccessors(Node node, Node newNode) {
        assert node.getClass() == getClazz() && newNode.getClass() == getClazz();

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
        return inputsEqual(node, other) && successorsEqual(node, other);
    }

    public boolean inputsEqual(Node node, Node other) {
        assert node.getClass() == getClazz() && other.getClass() == getClazz();
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
        return true;
    }

    public boolean successorsEqual(Node node, Node other) {
        assert node.getClass() == getClazz() && other.getClass() == getClazz();
        int index = 0;
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
        assert node.getClass() == getClazz();

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
        assert node.getClass() == getClazz();

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

    public Collection<Position> getFirstLevelInputPositions() {
        return new AbstractCollection<Position>() {
            @Override
            public Iterator<Position> iterator() {
                return new Iterator<NodeClass.Position>() {
                    int i = 0;

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    public Position next() {
                        Position pos = new Position(true, i, i >= directInputCount ? NODE_LIST : NOT_ITERABLE);
                        i++;
                        return pos;
                    }

                    public boolean hasNext() {
                        return i < inputOffsets.length;
                    }
                };
            }

            @Override
            public int size() {
                return inputOffsets.length;
            }
        };
    }

    public Collection<Position> getFirstLevelSuccessorPositions() {
        return new AbstractCollection<Position>() {
            @Override
            public Iterator<Position> iterator() {
                return new Iterator<NodeClass.Position>() {
                    int i = 0;

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    public Position next() {
                        Position pos = new Position(false, i, i >= directSuccessorCount ? NODE_LIST : NOT_ITERABLE);
                        i++;
                        return pos;
                    }

                    public boolean hasNext() {
                        return i < successorOffsets.length;
                    }
                };
            }

            @Override
            public int size() {
                return successorOffsets.length;
            }
        };
    }

    public Collection<Integer> getPropertyPositions() {
        return new AbstractCollection<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    int i = 0;

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    public Integer next() {
                        Integer pos = i++;
                        return pos;
                    }

                    public boolean hasNext() {
                        return i < dataOffsets.length;
                    }
                };
            }

            @Override
            public int size() {
                return dataOffsets.length;
            }
        };
    }

    /**
     * Initializes a fresh allocated node for which no constructor is called yet. Needed to
     * implement node factories in svm.
     */
    public void initRawNode(Node node) {
        node.init();
        for (int inputPos = directInputCount; inputPos < inputOffsets.length; inputPos++) {
            if (getNodeList(node, inputOffsets[inputPos]) == null) {
                putNodeList(node, inputOffsets[inputPos], new NodeInputList<>(node));
            }
        }
        for (int successorPos = directSuccessorCount; successorPos < successorOffsets.length; successorPos++) {
            if (getNodeList(node, successorOffsets[successorPos]) == null) {
                putNodeList(node, successorOffsets[successorPos], new NodeSuccessorList<>(node));
            }
        }
    }

    public Class<?> getJavaClass() {
        return getClazz();
    }

    /**
     * The template used to build the {@link Verbosity#Name} version. Variable part are specified
     * using &#123;i#inputName&#125; or &#123;p#propertyName&#125;.
     */
    public String getNameTemplate() {
        return nameTemplate;
    }

    interface InplaceUpdateClosure {

        Node replacement(Node node, boolean isInput);
    }

    static Map<Node, Node> addGraphDuplicate(final Graph graph, final Graph oldGraph, int estimatedNodeCount, Iterable<Node> nodes, final DuplicationReplacement replacements) {
        final Map<Node, Node> newNodes;
        int denseThreshold = oldGraph.getNodeCount() + oldGraph.getNodesDeletedSinceLastCompression() >> 4;
        if (estimatedNodeCount > denseThreshold) {
            // Use dense map
            newNodes = new NodeNodeMap(oldGraph);
        } else {
            // Use sparse map
            newNodes = newIdentityMap();
        }
        createNodeDuplicates(graph, nodes, replacements, newNodes);

        InplaceUpdateClosure replacementClosure = new InplaceUpdateClosure() {

            public Node replacement(Node node, boolean isInput) {
                Node target = newNodes.get(node);
                if (target == null) {
                    Node replacement = node;
                    if (replacements != null) {
                        replacement = replacements.replacement(node);
                    }
                    if (replacement != node) {
                        target = replacement;
                    } else if (node.graph() == graph && isInput) {
                        // patch to the outer world
                        target = node;
                    }

                }
                return target;
            }

        };

        // re-wire inputs
        for (Node oldNode : nodes) {
            Node node = newNodes.get(oldNode);
            NodeClass oldNodeClass = oldNode.getNodeClass();
            NodeClass nodeClass = node.getNodeClass();
            if (replacements == null || replacements.replacement(oldNode) == oldNode) {
                nodeClass.updateInputSuccInPlace(node, replacementClosure);
            } else {
                transferValuesDifferentNodeClass(graph, replacements, newNodes, oldNode, node, oldNodeClass, nodeClass);
            }
        }

        return newNodes;
    }

    private static void createNodeDuplicates(final Graph graph, Iterable<Node> nodes, final DuplicationReplacement replacements, final Map<Node, Node> newNodes) {
        for (Node node : nodes) {
            if (node != null) {
                assert !node.isDeleted() : "trying to duplicate deleted node: " + node;
                Node replacement = node;
                if (replacements != null) {
                    replacement = replacements.replacement(node);
                }
                if (replacement != node) {
                    assert replacement != null;
                    newNodes.put(node, replacement);
                } else {
                    Node newNode = node.clone(graph, false);
                    assert newNode.inputs().isEmpty() || newNode.usages().isEmpty();
                    assert newNode.getClass() == node.getClass();
                    newNodes.put(node, newNode);
                }
            }
        }
    }

    private static void transferValuesDifferentNodeClass(final Graph graph, final DuplicationReplacement replacements, final Map<Node, Node> newNodes, Node oldNode, Node node, NodeClass oldNodeClass,
                    NodeClass nodeClass) {
        for (NodeClassIterator iter = oldNode.inputs().iterator(); iter.hasNext();) {
            Position pos = iter.nextPosition();
            if (!nodeClass.isValid(pos, oldNodeClass)) {
                continue;
            }
            Node input = oldNodeClass.get(oldNode, pos);
            Node target = newNodes.get(input);
            if (target == null) {
                Node replacement = input;
                if (replacements != null) {
                    replacement = replacements.replacement(input);
                }
                if (replacement != input) {
                    assert isAssignable(nodeClass.fieldTypes.get(nodeClass.inputOffsets[pos.getIndex()]), replacement);
                    target = replacement;
                } else if (input.graph() == graph) { // patch to the outer world
                    target = input;
                }
            }
            nodeClass.set(node, pos, target);
        }

        for (NodeClassIterator iter = oldNode.successors().iterator(); iter.hasNext();) {
            Position pos = iter.nextPosition();
            if (!nodeClass.isValid(pos, oldNodeClass)) {
                continue;
            }
            Node succ = oldNodeClass.get(oldNode, pos);
            Node target = newNodes.get(succ);
            if (target == null) {
                Node replacement = replacements.replacement(succ);
                if (replacement != succ) {
                    assert isAssignable(nodeClass.fieldTypes.get(node.getNodeClass().successorOffsets[pos.getIndex()]), replacement);
                    target = replacement;
                }
            }
            nodeClass.set(node, pos, target);
        }
    }

    private static boolean isAssignable(Class<?> fieldType, Node replacement) {
        return replacement == null || !NODE_CLASS.isAssignableFrom(fieldType) || fieldType.isAssignableFrom(replacement.getClass());
    }
}
