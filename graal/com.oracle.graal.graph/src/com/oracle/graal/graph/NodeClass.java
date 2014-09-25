/*
 * Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.graph.Node.*;
import static com.oracle.graal.graph.util.CollectionsAccess.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.Node.Input;
import com.oracle.graal.graph.Node.Successor;
import com.oracle.graal.graph.spi.*;
import com.oracle.graal.nodeinfo.*;

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

    // Timers for creation of a NodeClass instance
    private static final DebugTimer Init = Debug.timer("NodeClass.Init");
    private static final DebugTimer Init_FieldScanning = Debug.timer("NodeClass.Init.FieldScanning");
    private static final DebugTimer Init_Edges = Debug.timer("NodeClass.Init.Edges");
    private static final DebugTimer Init_Data = Debug.timer("NodeClass.Init.Data");
    private static final DebugTimer Init_Naming = Debug.timer("NodeClass.Init.Naming");
    private static final DebugTimer Init_AllowedUsages = Debug.timer("NodeClass.Init.AllowedUsages");
    private static final DebugTimer Init_IterableIds = Debug.timer("NodeClass.Init.IterableIds");

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
                try (TimerCloseable t = Init.start()) {
                    value = (NodeClass) allClasses.get(key);
                    if (value == null) {
                        GeneratedNode gen = c.getAnnotation(GeneratedNode.class);
                        if (gen != null) {
                            Class<? extends Node> originalNodeClass = (Class<? extends Node>) gen.value();
                            value = (NodeClass) allClasses.get(originalNodeClass);
                            assert value != null;
                            if (value.genClass == null) {
                                value.genClass = (Class<? extends Node>) c;
                            } else {
                                assert value.genClass == c;
                            }
                        } else {
                            Class<?> superclass = c.getSuperclass();
                            if (superclass != NODE_CLASS) {
                                // Ensure NodeClass for superclass exists
                                get(superclass);
                            }
                            value = new NodeClass(key);
                        }
                        Object old = allClasses.putIfAbsent(key, value);
                        assert old == null : old + "   " + key;
                    }
                }
            }
        }
        return value;
    }

    private static final Class<?> NODE_CLASS = Node.class;
    private static final Class<?> INPUT_LIST_CLASS = NodeInputList.class;
    private static final Class<?> SUCCESSOR_LIST_CLASS = NodeSuccessorList.class;

    private static int nextIterableId = 0;

    private final Edges inputs;
    private final Edges successors;

    private final Class<?>[] dataTypes;
    private final boolean canGVN;
    private final int startGVNNumber;
    private final String shortName;
    private final String nameTemplate;
    private final int iterableId;
    private final EnumSet<InputType> allowedUsageTypes;
    private int[] iterableIds;

    /**
     * The {@linkplain GeneratedNode generated} node class denoted by this object. This value is
     * lazily initialized to avoid class initialization circularity issues. A sentinel value of
     * {@code Node.class} is used to denote absence of a generated class.
     */
    private Class<? extends Node> genClass;

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
    private final boolean isLeafNode;

    public NodeClass(Class<?> clazz) {
        this(clazz, new DefaultCalcOffset(), null, 0);
    }

    private static long[] sortedOffsets(ArrayList<Long> list1, ArrayList<Long> list2) {
        if (list1.isEmpty() && list2.isEmpty()) {
            return new long[0];
        }
        return sortedLongCopy(list1, list2);
    }

    public NodeClass(Class<?> clazz, CalcOffset calcOffset, int[] presetIterableIds, int presetIterableId) {
        super(clazz);
        assert NODE_CLASS.isAssignableFrom(clazz);

        this.isCanonicalizable = Canonicalizable.class.isAssignableFrom(clazz);
        if (Canonicalizable.Unary.class.isAssignableFrom(clazz) || Canonicalizable.Binary.class.isAssignableFrom(clazz)) {
            assert Canonicalizable.Unary.class.isAssignableFrom(clazz) ^ Canonicalizable.Binary.class.isAssignableFrom(clazz) : clazz + " should implement either Unary or Binary, not both";
        }

        this.isSimplifiable = Simplifiable.class.isAssignableFrom(clazz);

        FieldScanner fs = new FieldScanner(calcOffset);
        try (TimerCloseable t = Init_FieldScanning.start()) {
            fs.scan(clazz);
        }

        try (TimerCloseable t1 = Init_Edges.start()) {
            successors = new SuccessorEdges(clazz, fs.successorOffsets.size(), sortedOffsets(fs.successorOffsets, fs.successorListOffsets), fs.fieldNames, fs.fieldTypes);
            inputs = new InputEdges(clazz, fs.inputOffsets.size(), sortedOffsets(fs.inputOffsets, fs.inputListOffsets), fs.fieldNames, fs.fieldTypes, fs.types, fs.optionalInputs);
        }
        try (TimerCloseable t1 = Init_Data.start()) {
            dataOffsets = sortedLongCopy(fs.dataOffsets);
            dataTypes = new Class[dataOffsets.length];
            for (int i = 0; i < dataOffsets.length; i++) {
                dataTypes[i] = fs.fieldTypes.get(dataOffsets[i]);
            }
        }

        isLeafNode = inputs.getCount() + successors.getCount() == 0;
        fieldNames = fs.fieldNames;
        fieldTypes = fs.fieldTypes;

        canGVN = Node.ValueNumberable.class.isAssignableFrom(clazz);
        startGVNNumber = clazz.hashCode();

        String newNameTemplate = null;
        String newShortName;
        try (TimerCloseable t1 = Init_Naming.start()) {
            newShortName = clazz.getSimpleName();
            if (newShortName.endsWith("Node") && !newShortName.equals("StartNode") && !newShortName.equals("EndNode")) {
                newShortName = newShortName.substring(0, newShortName.length() - 4);
            }
            NodeInfo info = clazz.getAnnotation(NodeInfo.class);
            assert info != null : "missing " + NodeInfo.class.getSimpleName() + " annotation on " + clazz;
            if (!info.shortName().isEmpty()) {
                newShortName = info.shortName();
            }
            if (!info.nameTemplate().isEmpty()) {
                newNameTemplate = info.nameTemplate();
            }
        }
        EnumSet<InputType> newAllowedUsageTypes = EnumSet.noneOf(InputType.class);
        try (TimerCloseable t1 = Init_AllowedUsages.start()) {
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
        }
        this.nameTemplate = newNameTemplate == null ? newShortName : newNameTemplate;
        this.allowedUsageTypes = newAllowedUsageTypes;
        this.shortName = newShortName;
        if (presetIterableIds != null) {
            this.iterableIds = presetIterableIds;
            this.iterableId = presetIterableId;
        } else if (IterableNodeType.class.isAssignableFrom(clazz)) {
            ITERABLE_NODE_TYPES.increment();
            try (TimerCloseable t1 = Init_IterableIds.start()) {
                this.iterableId = nextIterableId++;

                Class<?> superclass = clazz.getSuperclass();
                while (superclass != NODE_CLASS) {
                    if (IterableNodeType.class.isAssignableFrom(superclass)) {
                        NodeClass superNodeClass = NodeClass.get(superclass);
                        assert !containsId(this.iterableId, superNodeClass.iterableIds);
                        superNodeClass.iterableIds = Arrays.copyOf(superNodeClass.iterableIds, superNodeClass.iterableIds.length + 1);
                        superNodeClass.iterableIds[superNodeClass.iterableIds.length - 1] = this.iterableId;
                    }
                    superclass = superclass.getSuperclass();
                }

                this.iterableIds = new int[]{iterableId};
            }
        } else {
            this.iterableId = Node.NOT_ITERABLE;
            this.iterableIds = null;
        }
        nodeIterableCount = Debug.metric("NodeIterable_%s", shortName);
    }

    /**
     * Gets the {@linkplain GeneratedNode generated} node class (if any) described by the object.
     */
    @SuppressWarnings("unchecked")
    public Class<? extends Node> getGenClass() {
        if (USE_GENERATED_NODES) {
            if (genClass == null) {
                if (!isAbstract(getClazz().getModifiers())) {
                    String genClassName = getClazz().getName().replace('$', '_') + "Gen";
                    try {
                        genClass = (Class<? extends Node>) Class.forName(genClassName);
                    } catch (ClassNotFoundException e) {
                        throw new GraalInternalError("Could not find generated class " + genClassName + " for " + getClazz());
                    }
                } else {
                    // Sentinel value denoting no generated class
                    genClass = Node.class;
                }
            }
            return genClass.equals(Node.class) ? null : genClass;
        }
        return null;
    }

    private static boolean containsId(int iterableId, int[] iterableIds) {
        for (int i : iterableIds) {
            if (i == iterableId) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void rescanFieldOffsets(CalcOffset calc) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines if a given {@link Node} class is described by this {@link NodeClass} object. This
     * is useful for doing an exact type test (as opposed to an instanceof test) on a node. For
     * example:
     *
     * <pre>
     *     if (node.getNodeClass().is(BeginNode.class)) { ... }
     *
     *     // Due to generated Node classes, the test below
     *     // is *not* the same as the test above:
     *     if (node.getClass() == BeginNode.class) { ... }
     * </pre>
     *
     * @param nodeClass a {@linkplain GeneratedNode non-generated} {@link Node} class
     */
    public boolean is(Class<? extends Node> nodeClass) {
        assert nodeClass.getAnnotation(GeneratedNode.class) == null : "cannot test NodeClas against generated " + nodeClass;
        return nodeClass == getClazz();
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
        public final HashSet<Long> optionalInputs = new HashSet<>();

        protected FieldScanner(CalcOffset calc) {
            super(calc);
        }

        @Override
        protected void scanField(Field field, Class<?> type, long offset) {
            if (field.isAnnotationPresent(Node.Input.class) || field.isAnnotationPresent(Node.OptionalInput.class)) {
                assert !field.isAnnotationPresent(Node.Successor.class) : "field cannot be both input and successor";
                assert field.isAnnotationPresent(Node.Input.class) ^ field.isAnnotationPresent(Node.OptionalInput.class) : "inputs can either be optional or non-optional";
                if (INPUT_LIST_CLASS.isAssignableFrom(type)) {
                    // NodeInputList fields should not be final since they are
                    // written (via Unsafe) in clearInputs()
                    GraalInternalError.guarantee(!Modifier.isFinal(field.getModifiers()), "NodeInputList input field %s should not be final", field);
                    GraalInternalError.guarantee(!Modifier.isPublic(field.getModifiers()), "NodeInputList input field %s should not be public", field);
                    inputListOffsets.add(offset);
                } else {
                    GraalInternalError.guarantee(NODE_CLASS.isAssignableFrom(type) || type.isInterface(), "invalid input type: %s", type);
                    GraalInternalError.guarantee(!Modifier.isFinal(field.getModifiers()), "Node input field %s should not be final", field);
                    inputOffsets.add(offset);
                }
                if (field.isAnnotationPresent(Node.Input.class)) {
                    types.put(offset, field.getAnnotation(Node.Input.class).value());
                } else {
                    types.put(offset, field.getAnnotation(Node.OptionalInput.class).value());
                }
                if (field.isAnnotationPresent(Node.OptionalInput.class)) {
                    optionalInputs.add(offset);
                }
            } else if (field.isAnnotationPresent(Node.Successor.class)) {
                if (SUCCESSOR_LIST_CLASS.isAssignableFrom(type)) {
                    // NodeSuccessorList fields should not be final since they are
                    // written (via Unsafe) in clearSuccessors()
                    GraalInternalError.guarantee(!Modifier.isFinal(field.getModifiers()), "NodeSuccessorList successor field % should not be final", field);
                    GraalInternalError.guarantee(!Modifier.isPublic(field.getModifiers()), "NodeSuccessorList successor field %s should not be public", field);
                    successorListOffsets.add(offset);
                } else {
                    GraalInternalError.guarantee(NODE_CLASS.isAssignableFrom(type), "invalid successor type: %s", type);
                    GraalInternalError.guarantee(!Modifier.isFinal(field.getModifiers()), "Node successor field %s should not be final", field);
                    successorOffsets.add(offset);
                }
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
        inputs.appendOffsets(str);
        str.append("] [");
        successors.appendOffsets(str);
        str.append("] [");
        for (int i = 0; i < dataOffsets.length; i++) {
            str.append(i == 0 ? "" : ", ").append(dataOffsets[i]);
        }
        str.append("]");
        return str.toString();
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

    public boolean isValid(Position pos, NodeClass from, Edges fromEdges) {
        if (this == from) {
            return true;
        }
        Edges toEdges = getEdges(fromEdges.type());
        if (pos.getIndex() >= toEdges.getCount()) {
            return false;
        }
        if (pos.getIndex() >= fromEdges.getCount()) {
            return false;
        }
        return toEdges.isSameEdge(fromEdges, pos.getIndex());
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

    static void updateEdgesInPlace(Node node, InplaceUpdateClosure duplicationReplacement, Edges edges) {
        int index = 0;
        while (index < edges.getDirectCount()) {
            Node edge = edges.getNode(node, index);
            if (edge != null) {
                Node newEdge = duplicationReplacement.replacement(edge, edges.type());
                if (edges.type() == Edges.Type.Inputs) {
                    node.updateUsages(null, newEdge);
                } else {
                    node.updatePredecessor(null, newEdge);
                }
                assert newEdge == null || edges.getType(index).isAssignableFrom(newEdge.getClass()) : "Can not assign " + newEdge.getClass() + " to " + edges.getType(index) + " in " + node;
                edges.initializeNode(node, index, newEdge);
            }
            index++;
        }

        while (index < edges.getCount()) {
            NodeList<Node> list = edges.getNodeList(node, index);
            assert list != null : edges;
            edges.initializeList(node, index, updateEdgeListCopy(node, list, duplicationReplacement, edges.type()));
            index++;
        }
    }

    void updateInputSuccInPlace(Node node, InplaceUpdateClosure duplicationReplacement) {
        updateEdgesInPlace(node, duplicationReplacement, inputs);
        updateEdgesInPlace(node, duplicationReplacement, successors);
    }

    private static NodeList<Node> updateEdgeListCopy(Node node, NodeList<Node> list, InplaceUpdateClosure duplicationReplacement, Edges.Type type) {
        NodeList<Node> result = type == Edges.Type.Inputs ? new NodeInputList<>(node, list.size()) : new NodeSuccessorList<>(node, list.size());

        for (int i = 0; i < list.count(); ++i) {
            Node oldNode = list.get(i);
            if (oldNode != null) {
                Node newNode = duplicationReplacement.replacement(oldNode, type);
                result.set(i, newNode);
            }
        }
        return result;
    }

    /**
     * Gets the input or successor edges defined by this node class.
     */
    public Edges getEdges(Edges.Type type) {
        return type == Edges.Type.Inputs ? inputs : successors;
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
        initNullEdgeLists(node, Edges.Type.Inputs);
        initNullEdgeLists(node, Edges.Type.Successors);
    }

    private void initNullEdgeLists(Node node, Edges.Type type) {
        Edges edges = getEdges(type);
        for (int inputPos = edges.getDirectCount(); inputPos < edges.getCount(); inputPos++) {
            if (edges.getNodeList(node, inputPos) == null) {
                edges.initializeList(node, inputPos, type == Edges.Type.Inputs ? new NodeInputList<>(node) : new NodeSuccessorList<>(node));
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

        Node replacement(Node node, Edges.Type type);
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

            public Node replacement(Node node, Edges.Type type) {
                Node target = newNodes.get(node);
                if (target == null) {
                    Node replacement = node;
                    if (replacements != null) {
                        replacement = replacements.replacement(node);
                    }
                    if (replacement != node) {
                        target = replacement;
                    } else if (node.graph() == graph && type == Edges.Type.Inputs) {
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
            NodeClass nodeClass = node.getNodeClass();
            if (replacements == null || replacements.replacement(oldNode) == oldNode) {
                nodeClass.updateInputSuccInPlace(node, replacementClosure);
            } else {
                transferEdgesDifferentNodeClass(graph, replacements, newNodes, oldNode, node);
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

    private static void transferEdgesDifferentNodeClass(final Graph graph, final DuplicationReplacement replacements, final Map<Node, Node> newNodes, Node oldNode, Node node) {
        transferEdges(graph, replacements, newNodes, oldNode, node, Edges.Type.Inputs);
        transferEdges(graph, replacements, newNodes, oldNode, node, Edges.Type.Successors);
    }

    private static void transferEdges(final Graph graph, final DuplicationReplacement replacements, final Map<Node, Node> newNodes, Node oldNode, Node node, Edges.Type type) {
        NodeClass nodeClass = node.getNodeClass();
        NodeClass oldNodeClass = oldNode.getNodeClass();
        Edges oldEdges = oldNodeClass.getEdges(type);
        for (NodePosIterator oldIter = oldEdges.getIterable(oldNode).iterator(); oldIter.hasNext();) {
            Position pos = oldIter.nextPosition();
            if (!nodeClass.isValid(pos, oldNodeClass, oldEdges)) {
                continue;
            }
            Node oldEdge = pos.get(oldNode);
            Node target = newNodes.get(oldEdge);
            if (target == null) {
                Node replacement = oldEdge;
                if (replacements != null) {
                    replacement = replacements.replacement(oldEdge);
                }
                if (replacement != oldEdge) {
                    target = replacement;
                } else if (type == Edges.Type.Inputs && oldEdge.graph() == graph) {
                    // patch to the outer world
                    target = oldEdge;
                }
            }
            pos.set(node, target);
        }
    }

    public boolean isLeafNode() {
        return isLeafNode;
    }
}
