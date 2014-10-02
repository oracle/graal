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

import static com.oracle.graal.compiler.common.Fields.*;
import static com.oracle.graal.graph.Edges.*;
import static com.oracle.graal.graph.InputEdges.*;
import static com.oracle.graal.graph.Node.*;
import static com.oracle.graal.graph.util.CollectionsAccess.*;
import static java.lang.reflect.Modifier.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.Edges.Type;
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
    private static final DebugTimer Init_FieldScanningInner = Debug.timer("NodeClass.Init.FieldScanning.Inner");
    private static final DebugTimer Init_AnnotationParsing = Debug.timer("NodeClass.Init.AnnotationParsing");
    private static final DebugTimer Init_Edges = Debug.timer("NodeClass.Init.Edges");
    private static final DebugTimer Init_Data = Debug.timer("NodeClass.Init.Data");
    private static final DebugTimer Init_AllowedUsages = Debug.timer("NodeClass.Init.AllowedUsages");
    private static final DebugTimer Init_IterableIds = Debug.timer("NodeClass.Init.IterableIds");

    private static <T extends Annotation> T getAnnotationTimed(AnnotatedElement e, Class<T> annotationClass) {
        try (TimerCloseable s = Init_AnnotationParsing.start()) {
            return e.getAnnotation(annotationClass);
        }
    }

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
                        Class<?> superclass = c.getSuperclass();
                        if (GeneratedNode.class.isAssignableFrom(c)) {
                            Class<? extends Node> originalNodeClass = (Class<? extends Node>) superclass;
                            value = (NodeClass) allClasses.get(originalNodeClass);
                            assert value != null;
                            if (value.genClass == null) {
                                value.genClass = (Class<? extends Node>) c;
                            } else {
                                assert value.genClass == c;
                            }
                        } else {
                            NodeClass superNodeClass = null;
                            if (superclass != NODE_CLASS) {
                                // Ensure NodeClass for superclass exists
                                superNodeClass = get(superclass);
                            }
                            value = new NodeClass(key, superNodeClass);
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

    private final InputEdges inputs;
    private final SuccessorEdges successors;
    private final NodeClass superNodeClass;

    private final boolean canGVN;
    private final int startGVNNumber;
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

    public NodeClass(Class<?> clazz, NodeClass superNodeClass) {
        this(clazz, superNodeClass, new DefaultCalcOffset(), null, 0);
    }

    public NodeClass(Class<?> clazz, NodeClass superNodeClass, CalcOffset calcOffset, int[] presetIterableIds, int presetIterableId) {
        super(clazz);
        this.superNodeClass = superNodeClass;
        assert NODE_CLASS.isAssignableFrom(clazz);

        this.isCanonicalizable = Canonicalizable.class.isAssignableFrom(clazz);
        if (Canonicalizable.Unary.class.isAssignableFrom(clazz) || Canonicalizable.Binary.class.isAssignableFrom(clazz)) {
            assert Canonicalizable.Unary.class.isAssignableFrom(clazz) ^ Canonicalizable.Binary.class.isAssignableFrom(clazz) : clazz + " should implement either Unary or Binary, not both";
        }

        this.isSimplifiable = Simplifiable.class.isAssignableFrom(clazz);

        FieldScanner fs = new FieldScanner(calcOffset, superNodeClass);
        try (TimerCloseable t = Init_FieldScanning.start()) {
            fs.scan(clazz, false);
        }

        try (TimerCloseable t1 = Init_Edges.start()) {
            successors = new SuccessorEdges(fs.directSuccessors, fs.successors);
            inputs = new InputEdges(fs.directInputs, fs.inputs);
        }
        try (TimerCloseable t1 = Init_Data.start()) {
            data = new Fields(fs.data);
        }

        isLeafNode = inputs.getCount() + successors.getCount() == 0;

        canGVN = Node.ValueNumberable.class.isAssignableFrom(clazz);
        startGVNNumber = clazz.hashCode();

        NodeInfo info = getAnnotationTimed(clazz, NodeInfo.class);
        this.nameTemplate = info.nameTemplate();

        try (TimerCloseable t1 = Init_AllowedUsages.start()) {
            allowedUsageTypes = superNodeClass == null ? EnumSet.noneOf(InputType.class) : superNodeClass.allowedUsageTypes.clone();
            allowedUsageTypes.addAll(Arrays.asList(info.allowedUsageTypes()));
        }

        if (presetIterableIds != null) {
            this.iterableIds = presetIterableIds;
            this.iterableId = presetIterableId;
        } else if (IterableNodeType.class.isAssignableFrom(clazz)) {
            ITERABLE_NODE_TYPES.increment();
            try (TimerCloseable t1 = Init_IterableIds.start()) {
                this.iterableId = nextIterableId++;

                NodeClass snc = superNodeClass;
                while (snc != null && IterableNodeType.class.isAssignableFrom(snc.getClazz())) {
                    assert !containsId(this.iterableId, snc.iterableIds);
                    snc.iterableIds = Arrays.copyOf(snc.iterableIds, snc.iterableIds.length + 1);
                    snc.iterableIds[snc.iterableIds.length - 1] = this.iterableId;
                    snc = snc.superNodeClass;
                }

                this.iterableIds = new int[]{iterableId};
            }
        } else {
            this.iterableId = Node.NOT_ITERABLE;
            this.iterableIds = null;
        }
        nodeIterableCount = Debug.metric("NodeIterable_%s", clazz);
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
        assert !GeneratedNode.class.isAssignableFrom(nodeClass) : "cannot test NodeClass against generated " + nodeClass;
        return nodeClass == getClazz();
    }

    private String shortName;

    public String shortName() {
        if (shortName == null) {
            NodeInfo info = getClazz().getAnnotation(NodeInfo.class);
            if (!info.shortName().isEmpty()) {
                shortName = info.shortName();
            } else {
                shortName = getClazz().getSimpleName();
                if (shortName.endsWith("Node") && !shortName.equals("StartNode") && !shortName.equals("EndNode")) {
                    shortName = shortName.substring(0, shortName.length() - 4);
                }
            }
        }
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

    /**
     * Describes a field representing an input or successor edge in a node.
     */
    protected static class EdgeInfo extends FieldInfo {

        public EdgeInfo(long offset, String name, Class<?> type) {
            super(offset, name, type);
        }

        /**
         * Sorts non-list edges before list edges.
         */
        @Override
        public int compareTo(FieldInfo o) {
            if (NodeList.class.isAssignableFrom(o.type)) {
                if (!NodeList.class.isAssignableFrom(type)) {
                    return -1;
                }
            } else {
                if (NodeList.class.isAssignableFrom(type)) {
                    return 1;
                }
            }
            return super.compareTo(o);
        }
    }

    /**
     * Describes a field representing an {@linkplain Type#Inputs input} edge in a node.
     */
    protected static class InputInfo extends EdgeInfo {
        final InputType inputType;
        final boolean optional;

        public InputInfo(long offset, String name, Class<?> type, InputType inputType, boolean optional) {
            super(offset, name, type);
            this.inputType = inputType;
            this.optional = optional;
        }

        @Override
        public String toString() {
            return super.toString() + "{inputType=" + inputType + ", optional=" + optional + "}";
        }
    }

    protected static class FieldScanner extends BaseFieldScanner {

        public final ArrayList<InputInfo> inputs = new ArrayList<>();
        public final ArrayList<EdgeInfo> successors = new ArrayList<>();
        int directInputs;
        int directSuccessors;

        protected FieldScanner(CalcOffset calc, NodeClass superNodeClass) {
            super(calc);
            if (superNodeClass != null) {
                translateInto(superNodeClass.inputs, inputs);
                translateInto(superNodeClass.successors, successors);
                translateInto(superNodeClass.data, data);
                directInputs = superNodeClass.inputs.getDirectCount();
                directSuccessors = superNodeClass.successors.getDirectCount();
            }
        }

        @Override
        protected void scanField(Field field, long offset) {
            Input inputAnnotation = getAnnotationTimed(field, Node.Input.class);
            OptionalInput optionalInputAnnotation = getAnnotationTimed(field, Node.OptionalInput.class);
            Successor successorAnnotation = getAnnotationTimed(field, Successor.class);
            try (TimerCloseable s = Init_FieldScanningInner.start()) {
                Class<?> type = field.getType();
                int modifiers = field.getModifiers();

                if (inputAnnotation != null || optionalInputAnnotation != null) {
                    assert successorAnnotation == null : "field cannot be both input and successor";
                    if (INPUT_LIST_CLASS.isAssignableFrom(type)) {
                        // NodeInputList fields should not be final since they are
                        // written (via Unsafe) in clearInputs()
                        GraalInternalError.guarantee(!Modifier.isFinal(modifiers), "NodeInputList input field %s should not be final", field);
                        GraalInternalError.guarantee(!Modifier.isPublic(modifiers), "NodeInputList input field %s should not be public", field);
                    } else {
                        GraalInternalError.guarantee(NODE_CLASS.isAssignableFrom(type) || type.isInterface(), "invalid input type: %s", type);
                        GraalInternalError.guarantee(!Modifier.isFinal(modifiers), "Node input field %s should not be final", field);
                        directInputs++;
                    }
                    InputType inputType;
                    if (inputAnnotation != null) {
                        assert optionalInputAnnotation == null : "inputs can either be optional or non-optional";
                        inputType = inputAnnotation.value();
                    } else {
                        inputType = optionalInputAnnotation.value();
                    }
                    inputs.add(new InputInfo(offset, field.getName(), type, inputType, field.isAnnotationPresent(Node.OptionalInput.class)));
                } else if (successorAnnotation != null) {
                    if (SUCCESSOR_LIST_CLASS.isAssignableFrom(type)) {
                        // NodeSuccessorList fields should not be final since they are
                        // written (via Unsafe) in clearSuccessors()
                        GraalInternalError.guarantee(!Modifier.isFinal(modifiers), "NodeSuccessorList successor field % should not be final", field);
                        GraalInternalError.guarantee(!Modifier.isPublic(modifiers), "NodeSuccessorList successor field %s should not be public", field);
                    } else {
                        GraalInternalError.guarantee(NODE_CLASS.isAssignableFrom(type), "invalid successor type: %s", type);
                        GraalInternalError.guarantee(!Modifier.isFinal(modifiers), "Node successor field %s should not be final", field);
                        directSuccessors++;
                    }
                    successors.add(new EdgeInfo(offset, field.getName(), type));
                } else {
                    GraalInternalError.guarantee(!NODE_CLASS.isAssignableFrom(type) || field.getName().equals("Null"), "suspicious node field: %s", field);
                    GraalInternalError.guarantee(!INPUT_LIST_CLASS.isAssignableFrom(type), "suspicious node input list field: %s", field);
                    GraalInternalError.guarantee(!SUCCESSOR_LIST_CLASS.isAssignableFrom(type), "suspicious node successor list field: %s", field);
                    data.add(new FieldInfo(offset, field.getName(), type));
                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("NodeClass ").append(getClazz().getSimpleName()).append(" [");
        inputs.appendFields(str);
        str.append("] [");
        successors.appendFields(str);
        str.append("] [");
        data.appendFields(str);
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
            for (int i = 0; i < data.getCount(); ++i) {
                Class<?> type = data.getType(i);
                if (type.isPrimitive()) {
                    if (type == Integer.TYPE) {
                        int intValue = data.getInt(n, i);
                        number += intValue;
                    } else if (type == Long.TYPE) {
                        long longValue = data.getLong(n, i);
                        number += longValue ^ (longValue >>> 32);
                    } else if (type == Boolean.TYPE) {
                        boolean booleanValue = data.getBoolean(n, i);
                        if (booleanValue) {
                            number += 7;
                        }
                    } else if (type == Float.TYPE) {
                        float floatValue = data.getFloat(n, i);
                        number += Float.floatToRawIntBits(floatValue);
                    } else if (type == Double.TYPE) {
                        double doubleValue = data.getDouble(n, i);
                        long longValue = Double.doubleToRawLongBits(doubleValue);
                        number += longValue ^ (longValue >>> 32);
                    } else if (type == Short.TYPE) {
                        short shortValue = data.getShort(n, i);
                        number += shortValue;
                    } else if (type == Character.TYPE) {
                        char charValue = data.getChar(n, i);
                        number += charValue;
                    } else if (type == Byte.TYPE) {
                        byte byteValue = data.getByte(n, i);
                        number += byteValue;
                    } else {
                        assert false : "unhandled property type: " + type;
                    }
                } else {
                    Object o = data.getObject(n, i);
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

    public boolean dataEquals(Node a, Node b) {
        assert a.getClass() == b.getClass();
        for (int i = 0; i < data.getCount(); ++i) {
            Class<?> type = data.getType(i);
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    int aInt = data.getInt(a, i);
                    int bInt = data.getInt(b, i);
                    if (aInt != bInt) {
                        return false;
                    }
                } else if (type == Boolean.TYPE) {
                    boolean aBoolean = data.getBoolean(a, i);
                    boolean bBoolean = data.getBoolean(b, i);
                    if (aBoolean != bBoolean) {
                        return false;
                    }
                } else if (type == Long.TYPE) {
                    long aLong = data.getLong(a, i);
                    long bLong = data.getLong(b, i);
                    if (aLong != bLong) {
                        return false;
                    }
                } else if (type == Float.TYPE) {
                    float aFloat = data.getFloat(a, i);
                    float bFloat = data.getFloat(b, i);
                    if (aFloat != bFloat) {
                        return false;
                    }
                } else if (type == Double.TYPE) {
                    double aDouble = data.getDouble(a, i);
                    double bDouble = data.getDouble(b, i);
                    if (aDouble != bDouble) {
                        return false;
                    }
                } else if (type == Short.TYPE) {
                    short aShort = data.getShort(a, i);
                    short bShort = data.getShort(b, i);
                    if (aShort != bShort) {
                        return false;
                    }
                } else if (type == Character.TYPE) {
                    char aChar = data.getChar(a, i);
                    char bChar = data.getChar(b, i);
                    if (aChar != bChar) {
                        return false;
                    }
                } else if (type == Byte.TYPE) {
                    byte aByte = data.getByte(a, i);
                    byte bByte = data.getByte(b, i);
                    if (aByte != bByte) {
                        return false;
                    }
                } else {
                    assert false : "unhandled type: " + type;
                }
            } else {
                Object objectA = data.getObject(a, i);
                Object objectB = data.getObject(b, i);
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
        return toEdges.isSame(fromEdges, pos.getIndex());
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
     * The template used to build the {@link Verbosity#Name} version. Variable parts are specified
     * using &#123;i#inputName&#125; or &#123;p#propertyName&#125;.
     */
    public String getNameTemplate() {
        return nameTemplate.isEmpty() ? shortName() : nameTemplate;
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

    /**
     * @returns true if the node has no inputs and no successors
     */
    public boolean isLeafNode() {
        return isLeafNode;
    }
}
