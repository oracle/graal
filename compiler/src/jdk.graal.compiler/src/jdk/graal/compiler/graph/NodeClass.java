/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph;

import static jdk.graal.compiler.debug.GraalError.shouldNotReachHere;
import static jdk.graal.compiler.debug.GraalError.shouldNotReachHereUnexpectedValue;
import static jdk.graal.compiler.graph.Edges.NEXT_EDGE;
import static jdk.graal.compiler.graph.Edges.LIST_MASK;
import static jdk.graal.compiler.graph.Edges.OFFSET_MASK;
import static jdk.graal.compiler.graph.Graph.isNodeModificationCountsEnabled;
import static jdk.graal.compiler.graph.Node.WithAllEdges;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jdk.graal.compiler.nodes.NodeClassMap;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.FieldIntrospection;
import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.FieldsScanner;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.EdgeVisitor;
import jdk.graal.compiler.graph.Node.Input;
import jdk.graal.compiler.graph.Node.OptionalInput;
import jdk.graal.compiler.graph.Node.Successor;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.spi.BinaryCommutativeMarker;
import jdk.graal.compiler.graph.spi.CanonicalizableMarker;
import jdk.graal.compiler.graph.spi.NodeWithIdentity;
import jdk.graal.compiler.graph.spi.SimplifiableMarker;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodeinfo.Verbosity;
import jdk.internal.misc.Unsafe;

/**
 * Metadata for every {@link Node} type. The metadata includes:
 * <ul>
 * <li>The offsets of fields annotated with {@link Input} and {@link Successor} as well as methods
 * for iterating over such fields.</li>
 * <li>The identifier for an {@link IterableNodeType} class.</li>
 * </ul>
 */
public final class NodeClass<T> extends FieldIntrospection<T> {

    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final int SHORT_INPUT_LIST_THRESHOLD = 3;

    /**
     * Gets the {@link NodeClass} associated with a given {@link Class}.
     */
    @LibGraalSupport.HostedOnly
    public static <T> NodeClass<T> create(Class<T> c) {
        assert getUnchecked(c) == null;
        Class<? super T> superclass = c.getSuperclass();
        NodeClass<? super T> nodeSuperclass = null;
        if (superclass != NODE_CLASS) {
            nodeSuperclass = get(superclass);
        }
        return new NodeClass<>(c, nodeSuperclass);
    }

    @SuppressWarnings("unchecked")
    @LibGraalSupport.HostedOnly
    private static <T> NodeClass<T> getUnchecked(Class<T> clazz) {
        try {
            Field field = clazz.getDeclaredField("TYPE");
            field.setAccessible(true);
            return (NodeClass<T>) field.get(null);
        } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
            throw new RuntimeException("Could not load Graal NodeClass TYPE field for " + clazz, e);
        }
    }

    @LibGraalSupport.HostedOnly
    public static <T> NodeClass<T> get(Class<T> clazz) {
        NodeClass<T> result = getUnchecked(clazz);
        if (result == null && clazz != NODE_CLASS) {
            throw GraalError.shouldNotReachHere("TYPE field not initialized for class " + clazz.getTypeName()); // ExcludeFromJacocoGeneratedReport
        }
        return result;
    }

    private static final Class<?> NODE_CLASS = Node.class;
    private static final Class<?> INPUT_LIST_CLASS = NodeInputList.class;
    private static final Class<?> SUCCESSOR_LIST_CLASS = NodeSuccessorList.class;

    private static final AtomicInteger nextIterableId = new AtomicInteger();
    private static final AtomicInteger nextLeafId = new AtomicInteger();

    private final InputEdges inputs;
    private final SuccessorEdges successors;
    private final NodeClass<? super T> superNodeClass;

    private final boolean canGVN;
    private final int startGVNNumber;
    private final String nameTemplate;
    private final int iterableId;
    private final EnumSet<InputType> allowedUsageTypes;
    private int[] iterableIds;

    private static final CounterKey ITERABLE_NODE_TYPES = DebugContext.counter("IterableNodeTypes");

    private final boolean isCanonicalizable;
    private final boolean isCommutative;
    private final boolean isSimplifiable;
    private final boolean isLeafNode;
    private final boolean isNodeWithIdentity;
    private final boolean isMemoryKill;

    private final int leafId;

    @SuppressWarnings("try")
    @LibGraalSupport.HostedOnly
    public NodeClass(Class<T> clazz, NodeClass<? super T> superNodeClass) {
        super(clazz);
        DebugContext debug = DebugContext.forCurrentThread();
        this.superNodeClass = superNodeClass;
        assert NODE_CLASS.isAssignableFrom(clazz);

        this.isCanonicalizable = CanonicalizableMarker.class.isAssignableFrom(clazz);
        this.isCommutative = BinaryCommutativeMarker.class.isAssignableFrom(clazz);
        this.isSimplifiable = SimplifiableMarker.class.isAssignableFrom(clazz);
        this.isNodeWithIdentity = NodeWithIdentity.class.isAssignableFrom(clazz);
        this.isMemoryKill = MemoryKillMarker.class.isAssignableFrom(clazz);

        NodeFieldsScanner fs = new NodeFieldsScanner();
        fs.scan(clazz, Node.class);

        successors = fs.createSuccessors();
        inputs = fs.createInputs();
        data = fs.createData();

        isLeafNode = inputs.getCount() + successors.getCount() == 0;
        if (isLeafNode) {
            this.leafId = nextLeafId.getAndIncrement();
        } else {
            this.leafId = -1;
        }

        canGVN = Node.ValueNumberable.class.isAssignableFrom(clazz);
        startGVNNumber = clazz.getName().hashCode();

        NodeInfo info = clazz.getAnnotation(NodeInfo.class);
        assert info != null : "Missing NodeInfo annotation on " + clazz;
        shortName = computeShortName(info);
        if (!info.nameTemplate().isEmpty()) {
            this.nameTemplate = info.nameTemplate();
        } else if (!info.shortName().isEmpty()) {
            this.nameTemplate = info.shortName();
        } else {
            this.nameTemplate = "";
        }

        allowedUsageTypes = superNodeClass == null ? EnumSet.noneOf(InputType.class) : superNodeClass.allowedUsageTypes.clone();
        allowedUsageTypes.addAll(Arrays.asList(info.allowedUsageTypes()));
        GraalError.guarantee(!allowedUsageTypes.contains(InputType.Memory) || MemoryKillMarker.class.isAssignableFrom(clazz),
                        "Node of type %s with allowedUsageType of memory must inherit from MemoryKill", clazz);

        if (IterableNodeType.class.isAssignableFrom(clazz)) {
            ITERABLE_NODE_TYPES.increment(debug);
            this.iterableId = nextIterableId.getAndIncrement();

            NodeClass<?> snc = superNodeClass;
            while (snc != null && IterableNodeType.class.isAssignableFrom(snc.getClazz())) {
                snc.addIterableId(iterableId);
                snc = snc.superNodeClass;
            }

            this.iterableIds = new int[]{iterableId};
        } else {
            this.iterableId = Node.NOT_ITERABLE;
            this.iterableIds = null;
        }
        verifyIterableIds();

        try (DebugContext.Scope scope = debug.scope("NodeCosts")) {
            /*
             * Note: We do not check for the existence of the node cost annotations during
             * construction as not every node needs to have them set. However if costs are queried,
             * after the construction of the node class, they must be properly set. This is
             * important as we can not trust our cost model if there are unspecified nodes. Nodes
             * that do not need cost annotations are e.g. abstractions like FixedNode or
             * FloatingNode or ValueNode. Sub classes where costs are not specified will ask the
             * superclass for their costs during node class initialization. Therefore, getters for
             * cycles and size can omit verification during creation.
             */
            NodeCycles c = info.cycles();
            if (c == NodeCycles.CYCLES_UNSET) {
                cycles = superNodeClass != null ? superNodeClass.cycles : NodeCycles.CYCLES_UNSET;
            } else {
                cycles = c;
            }
            assert cycles != null;
            NodeSize s = info.size();
            if (s == NodeSize.SIZE_UNSET) {
                size = superNodeClass != null ? superNodeClass.size : NodeSize.SIZE_UNSET;
            } else {
                size = s;
            }
            assert size != null;
            debug.log("Node cost for node of type __| %s |_, cycles:%s,size:%s", clazz, cycles, size);
        }
        checkMemoryEdgeInvariant();
    }

    private void checkMemoryEdgeInvariant() {
        List<String> optionalMemoryInputs = new ArrayList<>();
        for (int i = 0; i < inputs.getCount(); i++) {
            if (inputs.isOptional(i) && inputs.getInputType(i) == InputType.Memory) {
                optionalMemoryInputs.add(inputs.getName(i));
            }
        }
        GraalError.guarantee(optionalMemoryInputs.size() <= 1, "Nodes participating in the memory graph should have at most 1 optional memory input: %s", optionalMemoryInputs);
    }

    private final NodeCycles cycles;
    private final NodeSize size;

    public NodeCycles cycles() {
        return cycles;
    }

    public NodeSize size() {
        return size;
    }

    private synchronized void addIterableId(int newIterableId) {
        GraalError.guarantee(!containsId(newIterableId, iterableIds), "%d should not be in %s", newIterableId, Arrays.toString(iterableIds));
        int[] copy = Arrays.copyOf(iterableIds, iterableIds.length + 1);
        copy[iterableIds.length] = newIterableId;
        iterableIds = copy;
    }

    private void verifyIterableIds() {
        NodeClass<?> snc = superNodeClass;
        while (snc != null && IterableNodeType.class.isAssignableFrom(snc.getClazz())) {
            GraalError.guarantee(containsId(iterableId, snc.iterableIds), "%d should be in %s", iterableId, Arrays.toString(snc.iterableIds));
            snc = snc.superNodeClass;
        }
    }

    private static boolean containsId(int iterableId, int[] iterableIds) {
        for (int i : iterableIds) {
            if (i == iterableId) {
                return true;
            }
        }
        return false;
    }

    private final String shortName;

    public String shortName() {
        return shortName;
    }

    private String computeShortName(NodeInfo info) {
        if (!info.shortName().isEmpty()) {
            return info.shortName();
        } else {
            String localShortName = getClazz().getSimpleName();
            if (localShortName.endsWith("Node") && !localShortName.equals("StartNode") && !localShortName.equals("EndNode")) {
                return localShortName.substring(0, localShortName.length() - 4);
            } else {
                return localShortName;
            }
        }
    }

    @Override
    public Fields[] getAllFields() {
        return new Fields[]{data, inputs, successors};
    }

    int[] iterableIds() {
        return iterableIds;
    }

    public int iterableId() {
        return iterableId;
    }

    public boolean valueNumberable() {
        return canGVN;
    }

    /**
     * Determines if this node type is abstract.
     */
    public boolean isAbstract() {
        return Modifier.isAbstract(this.getClazz().getModifiers());
    }

    /**
     * Determines if this node type is {@link CanonicalizableMarker canonicalizable}.
     */
    public boolean isCanonicalizable() {
        return isCanonicalizable;
    }

    /**
     * Determines if this node type is {@link BinaryCommutativeMarker commutative}.
     */
    public boolean isCommutative() {
        return isCommutative;
    }

    /**
     * Determines if this node type is {@link SimplifiableMarker simplifiable}.
     */
    public boolean isSimplifiable() {
        return isSimplifiable;
    }

    static int allocatedNodeIterableIds() {
        return nextIterableId.get();
    }

    public EnumSet<InputType> getAllowedUsageTypes() {
        return allowedUsageTypes;
    }

    /**
     * Describes a field representing an input or successor edge in a node.
     */
    public static class EdgeInfo extends FieldsScanner.FieldInfo {

        public EdgeInfo(long offset, String name, Class<?> type, Class<?> declaringClass) {
            super(offset, name, type, declaringClass);
        }
    }

    /**
     * Describes a field representing an {@linkplain Edges.Type#Inputs input} edge in a node.
     */
    public static class InputInfo extends EdgeInfo {
        final InputType inputType;
        final boolean optional;

        public InputInfo(long offset, String name, Class<?> type, Class<?> declaringClass, InputType inputType, boolean optional) {
            super(offset, name, type, declaringClass);
            this.inputType = inputType;
            this.optional = optional;
        }

        @Override
        public String toString() {
            return super.toString() + "{inputType=" + inputType + ", optional=" + optional + "}";
        }
    }

    protected static class NodeFieldsScanner extends FieldsScanner {

        private final List<InputInfo> directInputs = new ArrayList<>();
        private final List<InputInfo> indirectInputs = new ArrayList<>();
        private final List<EdgeInfo> directSuccessors = new ArrayList<>();
        private final List<EdgeInfo> indirectSuccessors = new ArrayList<>();

        public SuccessorEdges createSuccessors() {
            List<EdgeInfo> successors = Stream.concat(directSuccessors.stream(), indirectSuccessors.stream()).toList();
            return new SuccessorEdges(directSuccessors.size(), successors);
        }

        public InputEdges createInputs() {
            List<InputInfo> inputs = Stream.concat(directInputs.stream(), indirectInputs.stream()).toList();
            return new InputEdges(directInputs.size(), inputs);
        }

        @SuppressWarnings("try")
        @Override
        protected void scanField(Field field, long offset) {
            Input inputAnnotation = field.getAnnotation(Input.class);
            OptionalInput optionalInputAnnotation = field.getAnnotation(OptionalInput.class);
            Successor successorAnnotation = field.getAnnotation(Successor.class);
            Class<?> type = field.getType();
            int modifiers = field.getModifiers();

            if (inputAnnotation != null || optionalInputAnnotation != null) {
                assert successorAnnotation == null : "field cannot be both input and successor";
                List<InputInfo> inputs;
                if (INPUT_LIST_CLASS.isAssignableFrom(type)) {
                    // NodeInputList fields should not be final since they are
                    // written (via Unsafe) in clearInputs()
                    GraalError.guarantee(!Modifier.isFinal(modifiers), "NodeInputList input field %s should not be final", field);
                    GraalError.guarantee(!Modifier.isPublic(modifiers), "NodeInputList input field %s should not be public", field);
                    inputs = indirectInputs;
                } else {
                    GraalError.guarantee(NODE_CLASS.isAssignableFrom(type) || type.isInterface(), "invalid input type: %s", type);
                    GraalError.guarantee(!Modifier.isFinal(modifiers), "Node input field %s should not be final", field);
                    inputs = directInputs;
                }
                InputType inputType;
                if (inputAnnotation != null) {
                    GraalError.guarantee(optionalInputAnnotation == null, "inputs can either be optional or non-optional");
                    inputType = inputAnnotation.value();
                } else {
                    inputType = optionalInputAnnotation.value();
                }
                GraalError.guarantee(inputType != InputType.Memory || MemoryKillMarker.class.isAssignableFrom(type) || NodeInputList.class.isAssignableFrom(type),
                                "field type of input annotated with Memory must inherit from MemoryKill: %s", field);
                inputs.add(new InputInfo(offset, field.getName(), type, field.getDeclaringClass(), inputType, field.isAnnotationPresent(Node.OptionalInput.class)));
            } else if (successorAnnotation != null) {
                List<EdgeInfo> successors;
                if (SUCCESSOR_LIST_CLASS.isAssignableFrom(type)) {
                    // NodeSuccessorList fields should not be final since they are
                    // written (via Unsafe) in clearSuccessors()
                    GraalError.guarantee(!Modifier.isFinal(modifiers), "NodeSuccessorList successor field %s should not be final", field);
                    GraalError.guarantee(!Modifier.isPublic(modifiers), "NodeSuccessorList successor field %s should not be public", field);
                    successors = indirectSuccessors;
                } else {
                    GraalError.guarantee(NODE_CLASS.isAssignableFrom(type), "invalid successor type: %s", type);
                    GraalError.guarantee(!Modifier.isFinal(modifiers), "Node successor field %s should not be final", field);
                    successors = directSuccessors;
                }
                successors.add(new EdgeInfo(offset, field.getName(), type, field.getDeclaringClass()));
            } else {
                GraalError.guarantee(!NODE_CLASS.isAssignableFrom(type) || field.getName().equals("Null"), "suspicious node field: %s", field);
                GraalError.guarantee(!INPUT_LIST_CLASS.isAssignableFrom(type), "suspicious node input list field: %s", field);
                GraalError.guarantee(!SUCCESSOR_LIST_CLASS.isAssignableFrom(type), "suspicious node successor list field: %s", field);
                super.scanField(field, offset);
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
        if (o == null) {
            return 0;
        } else if (!o.getClass().isArray()) {
            return o.hashCode();
        } else if (o instanceof Object[]) {
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
        } else {
            throw shouldNotReachHereUnexpectedValue(o); // ExcludeFromJacocoGeneratedReport
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
                        number += Long.hashCode(longValue);
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
                        number += Long.hashCode(longValue);
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
        if (e1 == e2) {
            return true;
        } else if (e1 == null || e2 == null) {
            return false;
        } else if (!e1.getClass().isArray() || e1.getClass() != e2.getClass()) {
            return e1.equals(e2);
        } else if (e1 instanceof Object[] && e2 instanceof Object[]) {
            return deepEquals((Object[]) e1, (Object[]) e2);
        } else if (e1 instanceof int[]) {
            return Arrays.equals((int[]) e1, (int[]) e2);
        } else if (e1 instanceof long[]) {
            return Arrays.equals((long[]) e1, (long[]) e2);
        } else if (e1 instanceof byte[]) {
            return Arrays.equals((byte[]) e1, (byte[]) e2);
        } else if (e1 instanceof char[]) {
            return Arrays.equals((char[]) e1, (char[]) e2);
        } else if (e1 instanceof short[]) {
            return Arrays.equals((short[]) e1, (short[]) e2);
        } else if (e1 instanceof float[]) {
            return Arrays.equals((float[]) e1, (float[]) e2);
        } else if (e1 instanceof double[]) {
            return Arrays.equals((double[]) e1, (double[]) e2);
        } else if (e1 instanceof boolean[]) {
            return Arrays.equals((boolean[]) e1, (boolean[]) e2);
        } else {
            throw shouldNotReachHere(e1 + " " + e2); // ExcludeFromJacocoGeneratedReport
        }
    }

    private static boolean deepEquals(Object[] a1, Object[] a2) {
        int length = a1.length;
        if (a2.length != length) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (!deepEquals0(a1[i], a2[i])) {
                return false;
            }
        }
        return true;
    }

    public boolean dataEquals(Node a, Node b) {
        assert a.getClass() == b.getClass() : Assertions.errorMessageContext("a", a, "b", b);
        if (a == b) {
            return true;
        } else if (isNodeWithIdentity) {
            /*
             * The node class is manually marked by the user as having identity. Two such nodes are
             * never "value equal" regardless of their data fields.
             */
            return false;
        }

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
                assert notLambda(objectA) || notLambda(objectB) : "lambdas are not permitted in fields of " + this;
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

    private static boolean notLambda(Object obj) {
        // This needs to be consistent with InnerClassLambdaMetafactory constructor.
        return obj == null || !obj.getClass().getSimpleName().contains("$$Lambda$");
    }

    public boolean isValid(Position pos, NodeClass<?> from, Edges fromEdges) {
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
        Edges.Type curType = edges.type();
        int directCount = edges.getDirectCount();
        final long[] curOffsets = edges.getOffsets();
        while (index < directCount) {
            Node edge = Edges.getNode(node, curOffsets, index);
            if (edge != null) {
                Node newEdge = duplicationReplacement.replacement(edge, curType);
                if (curType == Edges.Type.Inputs) {
                    node.updateUsages(null, newEdge);
                } else {
                    node.updatePredecessor(null, newEdge);
                }
                edges.initializeNode(node, index, newEdge);
            }
            index++;
        }

        while (index < edges.getCount()) {
            NodeList<Node> list = Edges.getNodeList(node, curOffsets, index);
            if (list != null) {
                edges.initializeList(node, index, updateEdgeListCopy(node, list, duplicationReplacement, curType));
            }
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

    public InputEdges getInputEdges() {
        return inputs;
    }

    public SuccessorEdges getSuccessorEdges() {
        return successors;
    }

    /**
     * Returns a newly allocated node for which no subclass-specific constructor has been called.
     */
    @SuppressWarnings("unchecked")
    public Node allocateInstance() {
        try {
            Node node = (Node) UNSAFE.allocateInstance(getJavaClass());
            node.init((NodeClass<? extends Node>) this);
            return node;
        } catch (InstantiationException ex) {
            throw shouldNotReachHere(ex); // ExcludeFromJacocoGeneratedReport
        }
    }

    public Class<T> getJavaClass() {
        return getClazz();
    }

    /**
     * The template used to build the {@link Verbosity#Name} version. Variable parts are specified
     * using &#123;i#inputName&#125; or &#123;p#propertyName&#125;. If no
     * {@link NodeInfo#nameTemplate() template} is specified, it uses {@link NodeInfo#shortName()}.
     * If none of the two is specified, it returns an empty string.
     */
    public String getNameTemplate() {
        return nameTemplate;
    }

    interface InplaceUpdateClosure {

        Node replacement(Node node, Edges.Type type);
    }

    static EconomicMap<Node, Node> addGraphDuplicate(final Graph graph, final Graph oldGraph, int estimatedNodeCount, Iterable<? extends Node> nodes, final Graph.DuplicationReplacement replacements) {
        return addGraphDuplicate(graph, oldGraph, estimatedNodeCount, nodes, replacements, true);
    }

    static EconomicMap<Node, Node> addGraphDuplicate(final Graph graph, final Graph oldGraph, int estimatedNodeCount, Iterable<? extends Node> nodes, final Graph.DuplicationReplacement replacements,
                    boolean applyGVN) {
        final EconomicMap<Node, Node> newNodes;
        int denseThreshold = oldGraph.getNodeCount() + oldGraph.getNodesDeletedSinceLastCompression() >> 4;
        if (estimatedNodeCount > denseThreshold) {
            // Use dense map
            newNodes = new NodeMap<>(oldGraph);
        } else {
            // Use sparse map
            newNodes = EconomicMap.create(Equivalence.IDENTITY);
        }
        graph.beforeNodeDuplication(oldGraph);
        createNodeDuplicates(graph, nodes, replacements, newNodes, applyGVN);

        InplaceUpdateClosure replacementClosure = new InplaceUpdateClosure() {

            @Override
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
            NodeClass<?> nodeClass = node.getNodeClass();
            if (replacements == null || replacements.replacement(oldNode) == oldNode) {
                nodeClass.updateInputSuccInPlace(node, replacementClosure);
            } else {
                transferEdgesDifferentNodeClass(graph, replacements, newNodes, oldNode, node);
            }
        }

        return newNodes;
    }

    private static void createNodeDuplicates(final Graph graph, Iterable<? extends Node> nodes, final Graph.DuplicationReplacement replacements, final EconomicMap<Node, Node> newNodes,
                    boolean applyGVN) {
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
                    Node newNode = node.clone(graph, WithAllEdges, applyGVN);
                    assert newNode.getNodeClass().isLeafNode() || newNode.hasNoUsages() : Assertions.errorMessageContext("newNode", newNode);
                    assert newNode.getClass() == node.getClass() : Assertions.errorMessageContext("newNode", newNode, "node", node);
                    newNodes.put(node, newNode);
                }
            }
        }
    }

    private static void transferEdgesDifferentNodeClass(final Graph graph, final Graph.DuplicationReplacement replacements, final EconomicMap<Node, Node> newNodes, Node oldNode, Node node) {
        transferEdges(graph, replacements, newNodes, oldNode, node, Edges.Type.Inputs);
        transferEdges(graph, replacements, newNodes, oldNode, node, Edges.Type.Successors);
    }

    private static void transferEdges(final Graph graph, final Graph.DuplicationReplacement replacements, final EconomicMap<Node, Node> newNodes, Node oldNode, Node node, Edges.Type type) {
        NodeClass<?> nodeClass = node.getNodeClass();
        NodeClass<?> oldNodeClass = oldNode.getNodeClass();
        Edges oldEdges = oldNodeClass.getEdges(type);
        for (Position pos : oldEdges.getPositionsIterable(oldNode)) {
            if (!nodeClass.isValid(pos, oldNodeClass, oldEdges)) {
                continue;
            }
            Node oldEdge = pos.get(oldNode);
            if (oldEdge != null) {
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
    }

    /**
     * @return true if the node has no inputs and no successors
     */
    public boolean isLeafNode() {
        return isLeafNode;
    }

    public int getLeafId() {
        return this.leafId;
    }

    /**
     * @return {@code true} if the node implements {@link MemoryKillMarker}
     */
    public boolean isMemoryKill() {
        return isMemoryKill;
    }

    public NodeClass<? super T> getSuperNodeClass() {
        return superNodeClass;
    }

    /**
     * An iterator over edges.
     * <p>
     * An iterator of this type will not return null values, unless edges are modified concurrently.
     * Concurrent modifications are detected by an assertion on a best-effort basis.
     */
    private static class RawEdgesIterator implements Iterator<Node> {
        protected final Node node;
        protected long mask;
        protected Node nextValue;

        RawEdgesIterator(Node node, long mask) {
            this.node = node;
            this.mask = mask;
        }

        @Override
        public boolean hasNext() {
            Node next = nextValue;
            if (next != null) {
                return true;
            } else {
                nextValue = forward();
                return nextValue != null;
            }
        }

        private Node forward() {
            while (mask != 0) {
                Node next = getAndAdvanceInput();
                if (next != null) {
                    return next;
                }
            }
            return null;
        }

        @Override
        public Node next() {
            Node next = nextValue;
            if (next == null) {
                next = forward();
                if (next == null) {
                    throw new NoSuchElementException();
                } else {
                    return next;
                }
            } else {
                nextValue = null;
                return next;
            }
        }

        private Node getAndAdvanceInput() {
            long state = mask & 0x03;
            Node result;
            if (state == 0) {
                result = Edges.getNodeUnsafe(node, mask & 0xFC);
                mask = mask >>> NEXT_EDGE;
            } else if (state == 1) {
                // We are iterating a node list.
                NodeList<?> nodeList = Edges.getNodeListUnsafe(node, mask & 0xFC);
                result = nodeList.nodes[nodeList.size() - 1 - (int) ((mask >>> NEXT_EDGE) & 0xFFFF)];
                if ((mask & 0xFFFF00) != 0) {
                    // Node list count is non-zero, decrease by 1.
                    mask = mask - 0x100;
                } else {
                    // Node list is finished => go to next input.
                    mask = mask >>> 24;
                }
            } else {
                // Node list needs to expand first.
                result = null;
                NodeList<?> nodeList = Edges.getNodeListUnsafe(node, mask & 0xFC);
                int size;
                if (nodeList != null && ((size = nodeList.size()) != 0)) {
                    // Set pointer to upper most index of node list.
                    mask = ((mask >>> NEXT_EDGE) << 24) | (mask & 0xFD) | ((long) (size - 1) << NEXT_EDGE);
                    result = nodeList.nodes[size - 1 - (int) ((mask >>> NEXT_EDGE) & 0xFFFF)];
                    if ((mask & 0xFFFF00) != 0) {
                        // Node list count is non-zero, decrease by 1.
                        mask = mask - 0x100;
                    } else {
                        // Node list is finished => go to next input.
                        mask = mask >>> 24;
                    }
                } else {
                    // Node list is empty or null => skip.
                    mask = mask >>> NEXT_EDGE;
                }
            }
            return result;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public Position nextPosition() {
            return null;
        }
    }

    private static final class RawEdgesWithModCountIterator extends RawEdgesIterator {
        private final int modCount;

        private RawEdgesWithModCountIterator(Node node, long mask) {
            super(node, mask);
            assert isNodeModificationCountsEnabled();
            this.modCount = node.modCount();
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
                int modCount2 = node.modCount();
                assert modCount == modCount2 : Assertions.errorMessageContext("modCount", modCount, "node", node, "node.modCount", modCount2);
            }
        }
    }

    public NodeIterable<Node> getSuccessorIterable(final Node node) {
        long mask = this.successors.getIterationMask();
        return new NodeIterable<>() {

            @Override
            public Iterator<Node> iterator() {
                if (isNodeModificationCountsEnabled()) {
                    return new RawEdgesWithModCountIterator(node, mask);
                } else {
                    return new RawEdgesIterator(node, mask);
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                Iterator<Node> iterator = iterator();
                boolean first = true;
                sb.append("succs=");
                sb.append('[');
                while (iterator.hasNext()) {
                    Node input = iterator.next();
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(input);
                    first = false;
                }
                sb.append(']');
                return sb.toString();
            }
        };
    }

    public NodeIterable<Node> getInputIterable(final Node node) {
        long mask = this.inputs.getIterationMask();
        return new NodeIterable<>() {

            @Override
            public Iterator<Node> iterator() {
                if (isNodeModificationCountsEnabled()) {
                    return new RawEdgesWithModCountIterator(node, mask);
                } else {
                    return new RawEdgesIterator(node, mask);
                }
            }

            @Override
            public String toString() {
                StringBuilder sb = new StringBuilder();
                Iterator<Node> iterator = iterator();
                boolean first = true;
                sb.append("inputs=");
                sb.append('[');
                while (iterator.hasNext()) {
                    Node input = iterator.next();
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(input);
                    first = false;
                }
                sb.append(']');
                return sb.toString();
            }
        };
    }

    public boolean equalSuccessors(Node node, Node other) {
        return equalEdges(node, other, successors.getIterationMask());
    }

    public boolean equalInputs(Node node, Node other) {
        return equalEdges(node, other, inputs.getIterationMask());
    }

    private boolean equalEdges(Node node, Node other, long mask) {
        long myMask = mask;
        assert other.getNodeClass() == this : Assertions.errorMessageContext("other", other, "this", this);
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Object v1 = Edges.getNodeUnsafe(node, offset);
                Object v2 = Edges.getNodeUnsafe(other, offset);
                if (v1 != v2) {
                    return false;
                }
            } else {
                NodeList<Node> v1 = Edges.getNodeListUnsafe(node, offset);
                NodeList<Node> v2 = Edges.getNodeListUnsafe(other, offset);
                if (!Objects.equals(v1, v2)) {
                    return false;
                }
            }
            myMask >>>= NEXT_EDGE;
        }
        return true;
    }

    public void pushInputs(Node node, NodeStack stack) {
        long myMask = this.inputs.getIterationMask();
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    stack.push(curNode);
                }
            } else {
                pushAllHelper(stack, node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void pushAllHelper(NodeStack stack, Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (Node curNode : list) {
                if (curNode != null) {
                    stack.push(curNode);
                }
            }
        }
    }

    public void applySuccessors(Node node, EdgeVisitor consumer) {
        applyEdges(node, consumer, this.successors.getIterationMask(), successors);
    }

    public void applyInputs(Node node, EdgeVisitor consumer) {
        applyEdges(node, consumer, this.inputs.getIterationMask(), inputs);
    }

    private static void applyEdges(Node node, EdgeVisitor consumer, long mask, Edges edges) {
        int index = 0;
        long myMask = mask;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    Node newNode = consumer.apply(node, curNode);
                    if (newNode != curNode) {
                        edges.putNodeUnsafeChecked(node, offset, newNode, index);
                    }
                }
            } else {
                applyHelper(node, consumer, offset);
            }
            myMask >>>= NEXT_EDGE;
            index++;
        }
    }

    private static void applyHelper(Node node, EdgeVisitor consumer, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (int i = 0; i < list.size(); ++i) {
                Node curNode = list.get(i);
                if (curNode != null) {
                    Node newNode = consumer.apply(node, curNode);
                    if (newNode != curNode) {
                        list.initialize(i, newNode);
                    }
                }
            }
        }
    }

    public void unregisterAtSuccessorsAsPredecessor(Node node) {
        long myMask = this.successors.getIterationMask();
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    node.updatePredecessor(curNode, null);
                    Edges.putNodeUnsafe(node, offset, null);
                }
            } else {
                unregisterAtSuccessorsAsPredecessorHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void unregisterAtSuccessorsAsPredecessorHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (Node curNode : list) {
                if (curNode != null) {
                    node.updatePredecessor(curNode, null);
                }
            }
            list.clearWithoutUpdate();
        }
    }

    public void registerAtSuccessorsAsPredecessor(Node node) {
        long myMask = this.successors.getIterationMask();
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    GraalError.guarantee(curNode.isAlive(), "Adding %s to the graph but its successor %s is not alive", node, curNode);
                    node.updatePredecessor(null, curNode);
                }
            } else {
                registerAtSuccessorsAsPredecessorHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void registerAtSuccessorsAsPredecessorHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (Node curNode : list) {
                if (curNode != null) {
                    GraalError.guarantee(curNode.isAlive(), "Adding %s to the graph but its successor %s is not alive", node, curNode);
                    node.updatePredecessor(null, curNode);
                }
            }
        }
    }

    /**
     * Finds the first {@link Input} or {@link OptionalInput} in {@code node} whose value
     * {@code == key} and replaces it with {@code replacement}.
     *
     * Pre-requisite: {@code node.getNodeClass() == this}
     *
     * @return {@code true} if a replacement was made, {@code false} if {@code key} was not an input
     */
    public boolean replaceFirstInput(Node node, Node key, Node replacement) {
        assert node.getNodeClass() == this : Assertions.errorMessageContext("node", node, "this", this);
        return replaceFirstEdge(node, key, replacement, this.inputs.getIterationMask(), inputs);
    }

    /**
     * Finds the first {@link Successor} in {@code node} whose value is {@code key} and replaces it
     * with {@code replacement}.
     *
     * Pre-requisite: {@code node.getNodeClass() == this}
     *
     * @return {@code true} if a replacement was made, {@code false} if {@code key} was not an input
     */
    public boolean replaceFirstSuccessor(Node node, Node key, Node replacement) {
        assert node.getNodeClass() == this : Assertions.errorMessageContext("node", node, "this", this);
        return replaceFirstEdge(node, key, replacement, this.successors.getIterationMask(), successors);
    }

    private static boolean replaceFirstEdge(Node node, Node key, Node replacement, long mask, Edges edges) {
        int index = 0;
        long myMask = mask;
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Object curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode == key) {
                    edges.putNodeUnsafeChecked(node, offset, replacement, index);
                    return true;
                }
            } else {
                NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
                if (list != null && list.replaceFirst(key, replacement)) {
                    return true;
                }
            }
            myMask >>>= NEXT_EDGE;
            index++;
        }
        return false;
    }

    void registerAtInputsAsUsage(Node node) {
        // GraalError.guarantee(this.inputsIteration != null, this.getClazz().getName());
        long myMask = this.inputs.getIterationMask();
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    GraalError.guarantee(curNode.isAlive(), "Adding %s to the graph but its input %s is not alive", node, curNode);
                    curNode.addUsage(node);
                }
            } else {
                registerAtInputsAsUsageHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void registerAtInputsAsUsageHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            for (Node curNode : list) {
                if (curNode != null) {
                    GraalError.guarantee(curNode.isAlive(), "Adding %s to the graph but its input %s is not alive", node, curNode);
                    curNode.addUsage(node);
                }
            }
        }
    }

    public void unregisterAtInputsAsUsage(Node node) {
        long myMask = this.inputs.getIterationMask();
        while (myMask != 0) {
            long offset = (myMask & OFFSET_MASK);
            if ((myMask & LIST_MASK) == 0) {
                Node curNode = Edges.getNodeUnsafe(node, offset);
                if (curNode != null) {
                    node.removeThisFromUsages(curNode);
                    if (curNode.hasNoUsages()) {
                        node.maybeNotifyZeroUsages(curNode);
                    }
                    Edges.putNodeUnsafe(node, offset, null);
                }
            } else {
                unregisterAtInputsAsUsageHelper(node, offset);
            }
            myMask >>>= NEXT_EDGE;
        }
    }

    private static void unregisterAtInputsAsUsageHelper(Node node, long offset) {
        NodeList<Node> list = Edges.getNodeListUnsafe(node, offset);
        if (list != null) {
            if (list.size() > SHORT_INPUT_LIST_THRESHOLD) {
                // Fast path for longer input lists
                unregisterAtInputsAsUsageHelperMany(node, list);
                return;
            }
            for (Node curNode : list) {
                if (curNode != null) {
                    node.removeThisFromUsages(curNode);
                    if (curNode.hasNoUsages()) {
                        node.maybeNotifyZeroUsages(curNode);
                    }
                }
            }
            list.clearWithoutUpdate();
        }
    }

    /**
     * Optimized version of {@link #unregisterAtInputsAsUsageHelper} that is faster for nodes that
     * have many input edges leading to the same value node.
     * <p>
     * Removes batches of the same input value at once, in order to reduce repeated traversals of
     * potentially long usage lists. This allows faster clearing of nodes with many input edges to
     * the same value, i.e., the usage list of one input node may include the same usage many times,
     * and removing each occurrence of this usage one by one can be slow if the input value has many
     * usages in the graph (as is commonly the case with constant nodes, like null).
     * <p>
     * For example, considering a node with 6 inputs like this:
     *
     * <pre>
     *                         |Other|
     * C(null) C(null) C(null)    |    C(null) C(null)
     *     \      |       |       |       |      /
     *   +-----------------------------------------+
     *   |   usage node (e.g. VirtualObjectState)  |
     *   +-----------------------------------------+
     * </pre>
     *
     * We can batch-remove this node from its inputs' usages as follows, depending on
     * maxOtherEdgesToLookPast (i.e. the maximum number of other input edges to look past):
     * <ul>
     * <li>If maxOtherEdgesToLookPast = 0, we consider only consecutive occurrences of the same
     * input node (here: 3 * null + 1 * other + 2 * null).
     * <li>If maxOtherEdgesToLookPast = 1, we look past one other input to find more occurrences of
     * the same input node to be removed at once (here: 5 * null + 1 * other). Note that we need to
     * null out any input slots that would otherwise be processed again.
     * </ul>
     */
    private static void unregisterAtInputsAsUsageHelperMany(Node node, NodeList<Node> list) {
        final int maxOtherEdgesToLookPast = 1;
        int size = list.size();
        int i = 0; // Avoid checkstyle warning: Control variable 'i' is modified.
        for (; i < size; i++) {
            Node curNode = list.get(i);
            if (curNode != null) {
                // Find more occurrences of the same input node to remove at once.
                int sameInputEdges = 1;
                int otherInputEdges = 0;
                for (int j = i + 1; j < size && otherInputEdges <= maxOtherEdgesToLookPast; j++) {
                    Node nextNode = list.get(j);
                    if (nextNode != null) {
                        if (nextNode == curNode) {
                            sameInputEdges++;
                            if (otherInputEdges != 0) {
                                // Clear NodeList slot without update.
                                list.initialize(j, null);
                            }
                        } else {
                            otherInputEdges++;
                        }
                    }
                    if (otherInputEdges == 0) {
                        /*
                         * As long as we've only seen the same input node or null, there's no need
                         * to backtrack from here, so we can advance the outer loop accordingly.
                         * Otherwise, we'll need to continue from first unprocessed "other" edge
                         * (already processed edges will have been set to null, and be ignored).
                         */
                        i = j;
                    }
                }
                curNode.removeUsageNTimes(node, sameInputEdges);
                if (curNode.hasNoUsages()) {
                    node.maybeNotifyZeroUsages(curNode);
                }
            }
        }
        list.clearWithoutUpdate();
    }

    /**
     * The cached id for a {@link NodeClass} object in a specific {@link NodeClassMap}.
     *
     * @param map an object whose identity uniquely identifies a {@link NodeClassMap}
     */
    record CachedId(Object map, Integer id) {
    }

    private CachedId cachedId;

    /**
     * Sets the cache for this object's {@code id} in {@code map}.
     *
     * @param map an object whose identity uniquely identifies a {@link NodeClassMap}
     */
    public void setCachedId(Object map, Integer id) {
        cachedId = new CachedId(map, id);
    }

    /**
     * Gets the cache for this object's id in {@code map}.
     *
     * @param map an object whose identity uniquely identifies a {@link NodeClassMap}
     * @return null if no cached id for this object in {@code map} is available
     */
    public Integer getCachedId(Object map) {
        var c = cachedId;
        if (c != null && c.map == map) {
            return c.id;
        }
        return null;
    }
}
