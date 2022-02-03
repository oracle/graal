/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.graph;

import static org.graalvm.compiler.graph.Edges.Type.Inputs;
import static org.graalvm.compiler.graph.Edges.Type.Successors;
import static org.graalvm.compiler.graph.Graph.isModificationCountsEnabled;
import static org.graalvm.compiler.serviceprovider.GraalUnsafeAccess.getUnsafe;

import java.lang.annotation.ElementType;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Formattable;
import java.util.FormattableFlags;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.graalvm.compiler.core.common.Fields;
import org.graalvm.compiler.core.common.type.AbstractPointerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Graph.NodeEventListener;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.graph.iterators.NodePredicate;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeCycles;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodeinfo.NodeSize;
import org.graalvm.compiler.nodeinfo.Verbosity;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.services.Services;
import sun.misc.Unsafe;

/**
 * This class is the base class for all nodes. It represents a node that can be inserted in a
 * {@link Graph}.
 * <p>
 * Once a node has been added to a graph, it has a graph-unique {@link #id()}. Edges in the
 * subclasses are represented with annotated fields. There are two kind of edges: {@link Input} and
 * {@link Successor}. If a field of type {@link Node} is annotated with {@link Input} or
 * {@link Successor}, it must not be {@code null}. There is an edge from this node to the node
 * denoted by the field's value. A field annotated with {@link OptionalInput} is also such an edge
 * but it may be {@code null}.
 * <p>
 * Exactly one of {@link Input}, {@link OptionalInput}, or {@link Successor} must be applied to all
 * fields of a node that are of type {@link Node}. A field of type {@link NodeInputList} must be
 * annotated with {@link Input} or {@link OptionalInput}. A field of type {@link NodeSuccessorList}
 * must be annotated with {@link Successor}.
 * <p>
 * Nodes which are value numberable should implement the {@link ValueNumberable} interface.
 *
 * <h1>Assertions and Verification</h1>
 *
 * The Node class supplies the {@link #assertTrue(boolean, String, Object...)} and
 * {@link #assertFalse(boolean, String, Object...)} methods, which will check the supplied boolean
 * and throw a VerificationError if it has the wrong value. Both methods will always either throw an
 * exception or return true. They can thus be used within an assert statement, so that the check is
 * only performed if assertions are enabled.
 */
@NodeInfo
public abstract class Node implements Cloneable, Formattable {

    private static final Unsafe UNSAFE = getUnsafe();

    public static final NodeClass<?> TYPE = null;

    public static final boolean TRACK_CREATION_POSITION = Boolean.parseBoolean(Services.getSavedProperties().get("debug.graal.TrackNodeCreationPosition"));

    static final int DELETED_ID_START = -1000000000;
    static final int INITIAL_ID = -1;
    static final int ALIVE_ID_START = 0;

    // The use of fully qualified class names here and in the rest
    // of this file works around a problem javac has resolving symbols

    /**
     * Denotes a non-optional (non-null) node input. This should only be applied to fields of type
     * {@link Node} or {@link NodeInputList}.
     *
     * Nodes that update fields of type {@link Node} outside of their constructor should call
     * {@link Node#updateUsages(Node, Node)} just prior to the update.
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.FIELD)
    public static @interface Input {
        InputType value() default InputType.Value;
    }

    /**
     * Denotes an optional (nullable) node input. This should only be applied to fields of type
     * {@link Node} or {@link NodeInputList}.
     *
     * Nodes that update fields of type {@link Node} outside of their constructor should call
     * {@link Node#updateUsages(Node, Node)} just prior to the update.
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.FIELD)
    public static @interface OptionalInput {
        InputType value() default InputType.Value;
    }

    /**
     * Denotes a non-optional (non-null) node successor. This should only be applied to fields of
     * type {@link Node} or {@link NodeSuccessorList}.
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.FIELD)
    public static @interface Successor {
    }

    /**
     * Denotes that a parameter of an {@linkplain NodeIntrinsic intrinsic} method must be a compile
     * time constant at all call sites to the intrinsic method.
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.PARAMETER)
    public static @interface ConstantNodeParameter {
    }

    /**
     * Denotes an injected parameter in a {@linkplain NodeIntrinsic node intrinsic} constructor. If
     * the constructor is called as part of node intrinsification, the node intrinsifier will inject
     * an argument for the annotated parameter. Injected parameters must precede all non-injected
     * parameters in a constructor. If the type of the annotated parameter is {@link Stamp}, the
     * {@linkplain Stamp#javaType type} of the injected stamp is the return type of the annotated
     * method (which cannot be {@code void}).
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.PARAMETER)
    public static @interface InjectedNodeParameter {
    }

    /**
     * Annotates a method that can be replaced by a compiler intrinsic. A (resolved) call to the
     * annotated method will be processed by a generated {@code InvocationPlugin} that calls either
     * a factory method or a constructor corresponding with the annotated method. By default the
     * intrinsics are implemented by invoking the constructor but a factory method may be used
     * instead. To use a factory method the class implementing the intrinsic must be annotated with
     * {@link NodeIntrinsicFactory}. To ease error checking of NodeIntrinsics all intrinsics are
     * expected to be implemented in the same way, so it's not possible to mix constructor and
     * factory intrinsification in the same class.
     * <p>
     * A factory method corresponding to an annotated method is a static method named
     * {@code intrinsify} defined in the class denoted by {@link #value()}. In order, its signature
     * is as follows:
     * <ol>
     * <li>A {@code GraphBuilderContext} parameter.</li>
     * <li>A sequence of zero or more {@linkplain InjectedNodeParameter injected} parameters.</li>
     * <li>Remaining parameters that match the declared parameters of the annotated method.</li>
     * </ol>
     * A constructor corresponding to an annotated method is defined in the class denoted by
     * {@link #value()}. In order, its signature is as follows:
     * <ol>
     * <li>A sequence of zero or more {@linkplain InjectedNodeParameter injected} parameters.</li>
     * <li>Remaining parameters that match the declared parameters of the annotated method.</li>
     * </ol>
     * There must be exactly one such factory method or constructor corresponding to a
     * {@link NodeIntrinsic} annotated method.
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.METHOD)
    public static @interface NodeIntrinsic {

        /**
         * The class declaring the factory method or {@link Node} subclass declaring the constructor
         * used to intrinsify a call to the annotated method. The default value is the class in
         * which the annotated method is declared.
         */
        Class<?> value() default NodeIntrinsic.class;

        /**
         * If {@code true}, the factory method or constructor selected by the annotation must have
         * an {@linkplain InjectedNodeParameter injected} {@link Stamp} parameter. Calling
         * {@link AbstractPointerStamp#nonNull()} on the injected stamp is guaranteed to return
         * {@code true}.
         */
        boolean injectedStampIsNonNull() default false;

        /**
         * If {@code true} then this is lowered into a node that has side effects.
         */
        boolean hasSideEffect() default false;
    }

    /**
     * Marker annotation indicating that the class uses factory methods instead of constructors for
     * intrinsification.
     */
    @java.lang.annotation.Retention(RetentionPolicy.RUNTIME)
    @java.lang.annotation.Target(ElementType.TYPE)
    public @interface NodeIntrinsicFactory {
    }

    /**
     * Marker for a node that can be replaced by another node via global value numbering. A
     * {@linkplain NodeClass#isLeafNode() leaf} node can be replaced by another node of the same
     * type that has exactly the same {@linkplain NodeClass#getData() data} values. A non-leaf node
     * can be replaced by another node of the same type that has exactly the same data values as
     * well as the same {@linkplain Node#inputs() inputs} and {@linkplain Node#successors()
     * successors}.
     */
    public interface ValueNumberable {
    }

    /**
     * Marker interface for nodes that contain other nodes. When the inputs to {@code this} change,
     * users of {@code this} should also be placed on the work list for canonicalization.
     */
    public interface IndirectCanonicalization {
    }

    /**
     * The graph owning {@code this}.
     */
    private Graph graph;

    /**
     * @see #id()
     */
    int id;

    // this next pointer is used in Graph to implement fast iteration over NodeClass types, it
    // therefore points to the next Node of the same type.
    Node typeCacheNext;

    static final int INLINE_USAGE_COUNT = 2;
    private static final Node[] NO_NODES = {};

    /**
     * Head of usage list (i.e. list of nodes that have {@code this} as an input). Note that each
     * element denotes a specific usage so there can be duplicates in the list. For example, a
     * {@code ConstNode} modeling a compile constant that is added to itself will show up twice in
     * the usage list of the {@code AddNode}.
     *
     * The elements of the usage list in order are {@link #usage0}, {@link #usage1} and
     * {@link #extraUsages}. The first null entry terminates the list.
     */
    Node usage0;
    Node usage1;
    Node[] extraUsages;
    int extraUsagesCount;

    private Node predecessor;
    private NodeClass<? extends Node> nodeClass;

    public static final int NODE_LIST = -2;
    public static final int NOT_ITERABLE = -1;

    static class NodeStackTrace {
        final StackTraceElement[] stackTrace = new Throwable().getStackTrace();

        private String getString(String label) {
            StringBuilder sb = new StringBuilder();
            if (label != null) {
                sb.append(label).append(": ");
            }
            for (StackTraceElement ste : stackTrace) {
                sb.append("at ").append(ste.toString()).append('\n');
            }
            return sb.toString();
        }

        String getStrackTraceString() {
            return getString(null);
        }

        @Override
        public String toString() {
            return getString(getClass().getSimpleName());
        }
    }

    static class NodeCreationStackTrace extends NodeStackTrace {
    }

    public static class NodeInsertionStackTrace extends NodeStackTrace {
    }

    public Node(NodeClass<? extends Node> c) {
        init(c);
    }

    final void init(NodeClass<? extends Node> c) {
        assert c.getJavaClass() == this.getClass();
        this.nodeClass = c;
        id = INITIAL_ID;
        extraUsages = NO_NODES;
        if (TRACK_CREATION_POSITION) {
            setCreationPosition(new NodeCreationStackTrace());
        }
    }

    /**
     * Gets an identifier for {@code this} that is unique in the context of {@link #graph} iff
     * {@code this.graph() != NULL && this.isAlive()}. The value returned by this method can change
     * after the graph is {@linkplain Graph#maybeCompress() compressed}.
     */
    final int id() {
        return id;
    }

    /**
     * Gets the graph context of {@code this}.
     */
    public Graph graph() {
        return graph;
    }

    /**
     * Gets the option values associated with {@code this.graph()}.
     */
    public final OptionValues getOptions() {
        return graph == null ? null : graph.getOptions();
    }

    /**
     * Gets the debug context associated with {@code this.graph()}.
     */
    public final DebugContext getDebug() {
        return graph.getDebug();
    }

    /**
     * Returns an {@link NodeIterable iterable} which can be used to traverse all non-null input
     * edges of {@code this}.
     *
     * @return an {@link NodeIterable iterable} for all non-null input edges.
     */
    public NodeIterable<Node> inputs() {
        return nodeClass.getInputIterable(this);
    }

    /**
     * Returns an {@link Iterable iterable} which can be used to traverse all non-null input edges
     * of {@code this}.
     *
     * @return an {@link Iterable iterable} for all non-null input edges.
     */
    public Iterable<Position> inputPositions() {
        return nodeClass.getInputEdges().getPositionsIterable(this);
    }

    public abstract static class EdgeVisitor {

        public abstract Node apply(Node source, Node target);

    }

    /**
     * Applies the given visitor to all inputs of {@code this}.
     *
     * @param visitor the visitor to be applied to the inputs
     */
    public void applyInputs(EdgeVisitor visitor) {
        nodeClass.applyInputs(this, visitor);
    }

    /**
     * Applies the given visitor to all successors of {@code this}.
     *
     * @param visitor the visitor to be applied to the successors
     */
    public void applySuccessors(EdgeVisitor visitor) {
        nodeClass.applySuccessors(this, visitor);
    }

    /**
     * Returns an {@link NodeIterable iterable} which can be used to traverse all non-null successor
     * edges of {@code this}.
     *
     * @return an {@link NodeIterable iterable} for all non-null successor edges.
     */
    public NodeIterable<Node> successors() {
        assert !this.isDeleted() : this;
        return nodeClass.getSuccessorIterable(this);
    }

    /**
     * Returns an {@link Iterable iterable} which can be used to traverse all successor edge
     * positions of {@code this}.
     *
     * @return an {@link Iterable iterable} for all successor edge positoins.
     */
    public Iterable<Position> successorPositions() {
        return nodeClass.getSuccessorEdges().getPositionsIterable(this);
    }

    /**
     * Gets the maximum number of usages {@code this} has had at any point in time.
     */
    public int getUsageCount() {
        if (usage0 == null) {
            return 0;
        }
        if (usage1 == null) {
            return 1;
        }
        return INLINE_USAGE_COUNT + extraUsagesCount;
    }

    /**
     * Gets the list of nodes that use {@code this} (i.e., as an input).
     */
    public final NodeIterable<Node> usages() {
        return new NodeUsageIterable(this);
    }

    /**
     * Checks whether {@code this} has no usages.
     */
    public final boolean hasNoUsages() {
        return this.usage0 == null;
    }

    /**
     * Checks whether {@code this} has usages.
     */
    public final boolean hasUsages() {
        return this.usage0 != null;
    }

    /**
     * Checks whether {@code this} has more than one usage.
     */
    public final boolean hasMoreThanOneUsage() {
        return this.usage1 != null;
    }

    /**
     * Checks whether {@code this} has exactly one usage.
     */
    public final boolean hasExactlyOneUsage() {
        return hasUsages() && !hasMoreThanOneUsage();
    }

    /**
     * Checks whether {@code this} has only usages of type {@code inputType}.
     *
     * @param inputType the type of usages to look for
     */
    public final boolean hasOnlyUsagesOfType(InputType inputType) {
        for (Node usage : usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.get(usage) == this) {
                    if (pos.getInputType() != inputType) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Checks whether this node has usages of a given {@link InputType}.
     *
     * @param type the type of usages to look for
     */
    public final boolean hasUsagesOfType(InputType type) {
        for (Node usage : usages()) {
            for (Position pos : usage.inputPositions()) {
                if (pos.get(usage) == this) {
                    if (pos.getInputType() == type) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Adds a given node to this node's {@linkplain #usages() usages}.
     *
     * @param node the node to add
     */
    void addUsage(Node node) {
        incUsageModCount();
        if (usage0 == null) {
            usage0 = node;
        } else if (usage1 == null) {
            usage1 = node;
        } else {
            int length = extraUsages.length;
            if (length == 0) {
                extraUsages = new Node[4];
            } else if (extraUsagesCount == length) {
                Node[] newExtraUsages = new Node[length * 2 + 1];
                System.arraycopy(extraUsages, 0, newExtraUsages, 0, length);
                extraUsages = newExtraUsages;
            }
            extraUsages[extraUsagesCount++] = node;
        }
    }

    private void movUsageFromEndTo(int destIndex) {
        if (destIndex >= INLINE_USAGE_COUNT) {
            movUsageFromEndToExtraUsages(destIndex - INLINE_USAGE_COUNT);
        } else if (destIndex == 1) {
            movUsageFromEndToIndexOne();
        } else {
            assert destIndex == 0;
            movUsageFromEndToIndexZero();
        }
    }

    private void movUsageFromEndToExtraUsages(int destExtraIndex) {
        this.extraUsagesCount--;
        Node n = extraUsages[extraUsagesCount];
        extraUsages[destExtraIndex] = n;
        extraUsages[extraUsagesCount] = null;
    }

    private void movUsageFromEndToIndexZero() {
        if (extraUsagesCount > 0) {
            this.extraUsagesCount--;
            usage0 = extraUsages[extraUsagesCount];
            extraUsages[extraUsagesCount] = null;
        } else if (usage1 != null) {
            usage0 = usage1;
            usage1 = null;
        } else {
            usage0 = null;
        }
    }

    private void movUsageFromEndToIndexOne() {
        if (extraUsagesCount > 0) {
            this.extraUsagesCount--;
            usage1 = extraUsages[extraUsagesCount];
            extraUsages[extraUsagesCount] = null;
        } else {
            assert usage1 != null;
            usage1 = null;
        }
    }

    /**
     * Removes one occurrence of a given node from this node's {@linkplain #usages() usages}.
     *
     * @param node the node to remove
     * @return whether or not {@code usage} was in the usage list
     */
    public boolean removeUsage(Node node) {
        assert node != null;
        // For large graphs, usage removal is performance critical.
        // Furthermore, it is critical that this method maintains the invariant that the usage list
        // has no null element preceding a non-null element.
        incUsageModCount();
        if (usage0 == node) {
            movUsageFromEndToIndexZero();
            return true;
        }
        if (usage1 == node) {
            movUsageFromEndToIndexOne();
            return true;
        }
        for (int i = this.extraUsagesCount - 1; i >= 0; i--) {
            if (extraUsages[i] == node) {
                movUsageFromEndToExtraUsages(i);
                return true;
            }
        }
        return false;
    }

    public final Node predecessor() {
        return predecessor;
    }

    public final int modCount() {
        if (isModificationCountsEnabled() && graph != null) {
            return graph.getNodeModCount(this);
        }
        return 0;
    }

    final void incModCount() {
        if (isModificationCountsEnabled() && graph != null) {
            graph.incNodeModCount(this);
        }
    }

    final int usageModCount() {
        if (isModificationCountsEnabled() && graph != null) {
            return graph.nodeUsageModCount(this);
        }
        return 0;
    }

    final void incUsageModCount() {
        if (isModificationCountsEnabled() && graph != null) {
            graph.incNodeUsageModCount(this);
        }
    }

    public final boolean isDeleted() {
        return id <= DELETED_ID_START;
    }

    public final boolean isAlive() {
        return id >= ALIVE_ID_START;
    }

    public final boolean isUnregistered() {
        return id == INITIAL_ID;
    }

    /**
     * Removes one occurrence of {@code this} from {@code oldInput}'s usages and adds it to
     * {@code newInput}'s usages.
     */
    protected void updateUsages(Node oldInput, Node newInput) {
        assert isAlive() && (newInput == null || newInput.isAlive()) : "adding " + newInput + " to " + this + " instead of " + oldInput;
        if (oldInput != newInput) {
            if (oldInput != null) {
                boolean result = removeThisFromUsages(oldInput);
                assertTrue(result, "not found in usages, old input: %s", oldInput);
            }
            maybeNotifyInputChanged(this);
            if (newInput != null) {
                newInput.addUsage(this);
            }
            if (oldInput != null && oldInput.hasNoUsages()) {
                maybeNotifyZeroUsages(oldInput);
            }
        }
    }

    /**
     * Updates the predecessor of the given nodes after a successor slot is changed from
     * oldSuccessor to newSuccessor: removes {@code this} from oldSuccessor's predecessors and adds
     * {@code this} to newSuccessor's predecessors.
     */
    protected void updatePredecessor(Node oldSuccessor, Node newSuccessor) {
        assertTrue(isAlive() && (newSuccessor == null || newSuccessor.isAlive()) || newSuccessor == null && !isAlive(), "adding %s to %s instead of %s", newSuccessor, this, oldSuccessor);
        assert graph == null || !graph.isFrozen();
        if (oldSuccessor != newSuccessor) {
            if (oldSuccessor != null) {
                assertTrue(newSuccessor == null || oldSuccessor.predecessor == this, "wrong predecessor in old successor (%s): %s, should be %s", oldSuccessor, oldSuccessor.predecessor, this);
                oldSuccessor.predecessor = null;
            }
            if (newSuccessor != null) {
                assertTrue(newSuccessor.predecessor == null, "unexpected non-null predecessor in new successor (%s): %s, this=%s", newSuccessor, newSuccessor.predecessor, this);
                newSuccessor.predecessor = this;
                maybeNotifyInputChanged(newSuccessor);
            }
            maybeNotifyInputChanged(this);
        }
    }

    void initialize(Graph newGraph) {
        assertTrue(id == INITIAL_ID, "unexpected id: %d", id);
        this.graph = newGraph;
        newGraph.register(this);
        NodeClass<? extends Node> nc = nodeClass;
        nc.registerAtInputsAsUsage(this);
        nc.registerAtSuccessorsAsPredecessor(this);
    }

    /**
     * Information associated with {@code this}. A single value is stored directly in the field.
     * Multiple values are stored by creating an Object[].
     */
    private Object annotation;

    private <T> T getNodeInfo(Class<T> clazz) {
        assert clazz != Object[].class;
        if (annotation == null) {
            return null;
        }
        if (clazz.isInstance(annotation)) {
            return clazz.cast(annotation);
        }
        if (annotation.getClass() == Object[].class) {
            Object[] annotations = (Object[]) annotation;
            for (Object ann : annotations) {
                if (clazz.isInstance(ann)) {
                    return clazz.cast(ann);
                }
            }
        }
        return null;
    }

    private <T> void setNodeInfo(Class<T> clazz, T value) {
        assert clazz != Object[].class;
        if (annotation == null || clazz.isInstance(annotation)) {
            // Replace the current value
            this.annotation = value;
        } else if (annotation.getClass() == Object[].class) {
            Object[] annotations = (Object[]) annotation;
            for (int i = 0; i < annotations.length; i++) {
                if (clazz.isInstance(annotations[i])) {
                    annotations[i] = value;
                    return;
                }
            }
            Object[] newAnnotations = Arrays.copyOf(annotations, annotations.length + 1);
            newAnnotations[annotations.length] = value;
            this.annotation = newAnnotations;
        } else {
            this.annotation = new Object[]{this.annotation, value};
        }
    }

    /**
     * Gets the source position information for {@code this} or null if it doesn't exist.
     */
    public NodeSourcePosition getNodeSourcePosition() {
        return getNodeInfo(NodeSourcePosition.class);
    }

    /**
     * Set the source position to {@code sourcePosition}. Setting it to null is ignored so that it's
     * not accidentally cleared. Use {@link #clearNodeSourcePosition()} instead.
     */
    public void setNodeSourcePosition(NodeSourcePosition sourcePosition) {
        if (sourcePosition == null) {
            return;
        }
        setNodeInfo(NodeSourcePosition.class, sourcePosition);
    }

    public void clearNodeSourcePosition() {
        setNodeInfo(NodeSourcePosition.class, null);
    }

    public NodeCreationStackTrace getCreationPosition() {
        return getNodeInfo(NodeCreationStackTrace.class);
    }

    public void setCreationPosition(NodeCreationStackTrace trace) {
        setNodeInfo(NodeCreationStackTrace.class, trace);
    }

    public NodeInsertionStackTrace getInsertionPosition() {
        return getNodeInfo(NodeInsertionStackTrace.class);
    }

    public void setInsertionPosition(NodeInsertionStackTrace trace) {
        setNodeInfo(NodeInsertionStackTrace.class, trace);
    }

    /**
     * Update the source position only if it is null.
     */
    public void updateNodeSourcePosition(Supplier<NodeSourcePosition> sourcePositionSupp) {
        if (this.getNodeSourcePosition() == null) {
            setNodeSourcePosition(sourcePositionSupp.get());
        }
    }

    public DebugCloseable withNodeSourcePosition() {
        return graph.withNodeSourcePosition(this);
    }

    public final NodeClass<? extends Node> getNodeClass() {
        return nodeClass;
    }

    public boolean isAllowedUsageType(InputType type) {
        if (type == InputType.Value) {
            return false;
        }
        return getNodeClass().getAllowedUsageTypes().contains(type);
    }

    private boolean checkReplaceWith(Node replacement) {
        if (graph != null && graph.isFrozen()) {
            fail("cannot modify frozen graph");
        }
        if (replacement == this) {
            fail("cannot replace a node with itself");
        }
        if (isDeleted()) {
            fail("cannot replace deleted node");
        }
        if (replacement != null && replacement.isDeleted()) {
            fail("cannot replace with deleted node %s", replacement);
        }
        return true;
    }

    /**
     * For each use of {@code this} in another node, replace it with {@code replacement}.
     *
     * This is shown by the graph transformation below where edges are from usages to inputs (e.g.
     * {@code this} is an input of {@code n0}).
     *
     * Before:
     *
     * <pre>
     *       this
     *         ^
     *         |
     *        /|\
     *       / | \
     *      /  |  \
     *    n0  n1 ..nN
     *
     * </pre>
     *
     * After:
     *
     * <pre>
     *     replacement
     *         ^
     *         |
     *        /|\
     *       / | \
     *      /  |  \
     *    n0  n1 ..nN
     * </pre>
     *
     * If {@code replacement == null}, then the edges are simply removed.
     */
    public final void replaceAtUsages(Node replacement) {
        replaceAtAllUsages(replacement, false);
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if {@code filter == null} or {@code filter.test(n) == true}.
     *
     * @see #replaceAtUsages(Node)
     */
    public final void replaceAtUsages(Node replacement, Predicate<Node> filter) {
        replaceAtUsages(replacement, filter, false);
    }

    /**
     * For each use of {@code this} in another node, replace it with {@code replacement} and then
     * {@linkplain #safeDelete() remove} {@code this} from the graph.
     *
     * @see #replaceAtUsages(Node)
     */
    public final void replaceAtUsagesAndDelete(Node replacement) {
        replaceAtUsages(replacement, null, true);
        safeDelete();
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if {@code filter == null} or {@code filter.test(n) == true} and then
     * {@linkplain #safeDelete() remove} {@code this} from the graph.
     *
     * @see #replaceAtUsages(Node)
     */
    public final void replaceAtUsagesAndDelete(Node replacement, Predicate<Node> filter) {
        replaceAtUsages(replacement, filter, true);
        safeDelete();
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if {@code filter == null} or {@code filter.test(n) == true}.
     *
     * @param forDeletion specifies if the caller will {@linkplain #safeDelete() remove}
     *            {@code this} from the graph after this method returns
     * @see #replaceAtUsages(Node)
     */
    private void replaceAtUsages(Node replacement, Predicate<Node> filter, boolean forDeletion) {
        if (filter == null) {
            replaceAtAllUsages(replacement, forDeletion);
        } else {
            replaceAtMatchingUsages(replacement, filter, forDeletion);
        }
        assert checkReplaceAtUsagesInvariants(replacement);
    }

    /**
     * Subclasses can override this to check invariants related to replacing uses of {@code this}.
     *
     * @param replacement
     * @return {@code true} if all invariants hold
     */
    protected boolean checkReplaceAtUsagesInvariants(Node replacement) {
        return true;
    }

    /**
     * For each use of {@code this} in another node, replace it with {@code replacement}.
     *
     * @param forDeletion specifies if the caller will {@linkplain #safeDelete() remove}
     *            {@code this} from the graph after this method returns
     */
    public final void replaceAtAllUsages(Node replacement, boolean forDeletion) {
        checkReplaceWith(replacement);
        if (usage0 == null) {
            return;
        }
        replaceAtUsage(replacement, forDeletion, usage0);
        usage0 = null;

        if (usage1 == null) {
            return;
        }
        replaceAtUsage(replacement, forDeletion, usage1);
        usage1 = null;

        if (extraUsagesCount <= 0) {
            return;
        }
        for (int i = 0; i < extraUsagesCount; i++) {
            Node usage = extraUsages[i];
            replaceAtUsage(replacement, forDeletion, usage);
        }
        this.extraUsages = NO_NODES;
        this.extraUsagesCount = 0;
    }

    /**
     * For the use of {@code this} in another node represented by {@code usage}, replace it with
     * {@code replacement}.
     *
     * @param forDeletion specifies if the caller will {@linkplain #safeDelete() remove}
     *            {@code this} from the graph after this method returns
     *
     * @see #replaceAtUsages(Node)
     */
    private void replaceAtUsage(Node replacement, boolean forDeletion, Node usage) {
        boolean result = usage.getNodeClass().replaceFirstInput(usage, this, replacement);
        assertTrue(result, "not found in inputs, usage: %s", usage);
        /*
         * Don't notify for nodes which are about to be deleted.
         */
        if (!forDeletion || usage != this) {
            maybeNotifyInputChanged(usage);
        }
        if (replacement != null) {
            replacement.addUsage(usage);
        }
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if {@code filter.test(n) == true}.
     *
     * @param forDeletion specifies if the caller will {@linkplain #safeDelete() remove}
     *            {@code this} from the graph after this method returns
     *
     * @see #replaceAtUsages(Node)
     */
    private void replaceAtMatchingUsages(Node replacement, Predicate<Node> filter, boolean forDeletion) {
        Objects.requireNonNull(filter);
        checkReplaceWith(replacement);
        int i = 0;
        int usageCount = this.getUsageCount();
        while (i < usageCount) {
            Node usage = this.getUsageAt(i);
            if (filter.test(usage)) {
                replaceAtUsage(replacement, forDeletion, usage);
                this.movUsageFromEndTo(i);
                usageCount--;
            } else {
                ++i;
            }
        }
    }

    private Node getUsageAt(int index) {
        if (index == 0) {
            return this.usage0;
        } else if (index == 1) {
            return this.usage1;
        } else {
            return this.extraUsages[index - INLINE_USAGE_COUNT];
        }
    }

    public Node singleUsage() {
        assert hasExactlyOneUsage();
        return this.usage0;
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if {@code filter.test(n) == true}.
     *
     * @see #replaceAtUsages(Node)
     */
    public void replaceAtMatchingUsages(Node replacement, NodePredicate usagePredicate) {
        checkReplaceWith(replacement);
        replaceAtMatchingUsages(replacement, usagePredicate, false);
    }

    private void replaceAtUsagePos(Node replacement, Node usage, Position pos) {
        pos.initialize(usage, replacement);
        maybeNotifyInputChanged(usage);
        if (replacement != null) {
            replacement.addUsage(usage);
        }
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if the type of the use is {@code inputType}.
     *
     * @see #replaceAtUsages(Node)
     */
    public void replaceAtUsages(Node replacement, InputType inputType) {
        checkReplaceWith(replacement);
        int i = 0;
        int usageCount = this.getUsageCount();
        if (usageCount == 0) {
            return;
        }
        usages: while (i < usageCount) {
            Node usage = this.getUsageAt(i);
            for (Position pos : usage.inputPositions()) {
                if (pos.getInputType() == inputType && pos.get(usage) == this) {
                    replaceAtUsagePos(replacement, usage, pos);
                    this.movUsageFromEndTo(i);
                    usageCount--;
                    continue usages;
                }
            }
            i++;
        }
        if (hasNoUsages()) {
            maybeNotifyZeroUsages(this);
        }
    }

    /**
     * For each use of {@code this} in another node, {@code n}, replace it with {@code replacement}
     * if the type of the use is in {@code inputTypes}.
     *
     * @see #replaceAtUsages(Node)
     */
    public void replaceAtUsages(Node replacement, InputType... inputTypes) {
        checkReplaceWith(replacement);
        int i = 0;
        int usageCount = this.getUsageCount();
        if (usageCount == 0) {
            return;
        }
        usages: while (i < usageCount) {
            Node usage = this.getUsageAt(i);
            for (Position pos : usage.inputPositions()) {
                for (InputType type : inputTypes) {
                    if (pos.getInputType() == type && pos.get(usage) == this) {
                        replaceAtUsagePos(replacement, usage, pos);
                        this.movUsageFromEndTo(i);
                        usageCount--;
                        continue usages;
                    }
                }
            }
            i++;
        }
        if (hasNoUsages()) {
            maybeNotifyZeroUsages(this);
        }
    }

    private void maybeNotifyInputChanged(Node node) {
        if (graph != null) {
            assert !graph.isFrozen();
            NodeEventListener listener = graph.nodeEventListener;
            if (listener != null) {
                listener.event(Graph.NodeEvent.INPUT_CHANGED, node);
            }
            graph.modificationCount++;
        }
    }

    /**
     * Iterates over each {@link NodeEventListener} attached to {@code this.graph()} if
     * {@code node.isAlive()} and notifies the listener that {@code node} has had its last usage
     * removed.
     */
    public void maybeNotifyZeroUsages(Node node) {
        if (graph != null && node.isAlive()) {
            assert !graph.isFrozen();
            NodeEventListener listener = graph.nodeEventListener;
            if (listener != null) {
                listener.event(Graph.NodeEvent.ZERO_USAGES, node);
            }
            graph.modificationCount++;
        }
    }

    /**
     * Updates the control flow edge, if it exists, from {@link #predecessor()} to {@code this} to
     * have a target of {@code replacement}.
     */
    public void replaceAtPredecessor(Node replacement) {
        checkReplaceWith(replacement);
        if (predecessor != null) {
            if (!predecessor.getNodeClass().replaceFirstSuccessor(predecessor, this, replacement)) {
                fail("not found in successors, predecessor: %s", predecessor);
            }
            predecessor.updatePredecessor(this, replacement);
        }
    }

    /**
     * Replaces {@code this} at its predecessor (if any) and its usages with {@code replacement} and
     * removes it from its graph.
     */
    public void replaceAndDelete(Node replacement) {
        checkReplaceWith(replacement);
        if (replacement == null) {
            fail("cannot replace with null");
        }
        if (this.hasUsages()) {
            replaceAtUsages(replacement);
        }
        replaceAtPredecessor(replacement);
        this.safeDelete();
    }

    /**
     * Finds the first {@link Successor} in {@code this} whose value is {@code oldSuccessor} and
     * replaces it with {@code newSuccessor}. The predecessor fields in {@code oldSuccessor} and
     * {@code newSuccessor} are updated to reflect any change made.
     */
    public void replaceFirstSuccessor(Node oldSuccessor, Node newSuccessor) {
        if (nodeClass.replaceFirstSuccessor(this, oldSuccessor, newSuccessor)) {
            updatePredecessor(oldSuccessor, newSuccessor);
        }
    }

    /**
     * Finds the first {@link Input} or {@link OptionalInput} in {@code this} whose value is
     * {@code oldInput} and replaces it with {@code newInput}. If the input is changed, the usage
     * info for {@code oldInput} and {@code newInput} is updated as well.
     *
     * Before {@code this.replaceFirstInput(n0, n2)}:
     *
     * <pre>
     *       n0  n1  n0
     *        \  |  /
     *         \ | /
     *          \|/
     *           |
     *           V
     *         this
     * </pre>
     *
     * After {@code this.replaceFirstInput(n0, n2)}:
     *
     * <pre>
     *       n2  n1  n0
     *        \  |  /
     *         \ | /
     *          \|/
     *           |
     *           V
     *         this
     * </pre>
     */
    public void replaceFirstInput(Node oldInput, Node newInput) {
        if (nodeClass.replaceFirstInput(this, oldInput, newInput)) {
            updateUsages(oldInput, newInput);
        }
    }

    /**
     * Finds all {@link Input}s and {@link OptionalInput}s in {@code this} whose value is
     * {@code oldInput} and replaces them with {@code newInput}. If any input is changed, the usage
     * info for {@code oldInput} and {@code newInput} is updated as well.
     *
     * Before {@code this.replaceAllInputs(n0, n2)}:
     *
     * <pre>
     *       n0  n1  n0
     *        \  |  /
     *         \ | /
     *          \|/
     *           |
     *           V
     *         this
     * </pre>
     *
     * After {@code this.replaceAllInputs(n0, n2)}:
     *
     * <pre>
     *       n2  n1  n2
     *        \  |  /
     *         \ | /
     *          \|/
     *           |
     *           V
     *         this
     * </pre>
     */
    public void replaceAllInputs(Node oldInput, Node newInput) {
        while (nodeClass.replaceFirstInput(this, oldInput, newInput)) {
            updateUsages(oldInput, newInput);
        }
    }

    public void clearInputs() {
        assertFalse(isDeleted(), "cannot clear inputs of deleted node");
        getNodeClass().unregisterAtInputsAsUsage(this);
    }

    boolean removeThisFromUsages(Node n) {
        return n.removeUsage(this);
    }

    public void clearSuccessors() {
        assertFalse(isDeleted(), "cannot clear successors of deleted node");
        getNodeClass().unregisterAtSuccessorsAsPredecessor(this);
    }

    private boolean checkDeletion() {
        assertTrue(isAlive(), "must be alive");
        assertTrue(hasNoUsages(), "cannot delete node %s because of usages: %s", this, usages());
        assertTrue(predecessor == null, "cannot delete node %s because of predecessor: %s", this, predecessor);
        return true;
    }

    /**
     * Removes {@code this} from {@code this.graph()}. This node must have no
     * {@linkplain Node#usages() usages} and no {@linkplain #predecessor() predecessor}.
     */
    public void safeDelete() {
        assert checkDeletion();
        this.clearInputs();
        this.clearSuccessors();
        markDeleted();
    }

    public void markDeleted() {
        graph.unregister(this);
        id = DELETED_ID_START - id;
        assert isDeleted();
    }

    public final Node copyWithInputs() {
        return copyWithInputs(true);
    }

    public final Node copyWithInputs(boolean insertIntoGraph) {
        Node newNode = clone(insertIntoGraph ? graph : null, WithOnlyInputEdges);
        if (insertIntoGraph) {
            for (Node input : inputs()) {
                input.addUsage(newNode);
            }
        }
        return newNode;
    }

    /**
     * @param newNode the result of cloning {@code this} or {@link Unsafe#allocateInstance(Class)
     *            raw allocating} a copy of {@code this}
     * @param type the type of edges to process
     * @param edgesToCopy if {@code type} is in this set, the edges are copied otherwise they are
     *            cleared
     */
    private void copyOrClearEdgesForClone(Node newNode, Edges.Type type, EnumSet<Edges.Type> edgesToCopy) {
        if (edgesToCopy.contains(type)) {
            getNodeClass().getEdges(type).copy(this, newNode);
        } else {
            // The direct edges are already null
            getNodeClass().getEdges(type).initializeLists(newNode, this);
        }
    }

    public static final EnumSet<Edges.Type> WithNoEdges = EnumSet.noneOf(Edges.Type.class);
    public static final EnumSet<Edges.Type> WithAllEdges = EnumSet.allOf(Edges.Type.class);
    public static final EnumSet<Edges.Type> WithOnlyInputEdges = EnumSet.of(Inputs);
    public static final EnumSet<Edges.Type> WithOnlySucessorEdges = EnumSet.of(Successors);

    /**
     * Makes a copy of {@code this} in(to) a given graph.
     *
     * @param into the graph in which the copy will be registered (which may be
     *            {@code this.graph()}) or null if the copy should not be registered in a graph
     * @param edgesToCopy specifies the edges to be copied. The edges not specified in this set are
     *            initialized to their default value (i.e., {@code null} for a direct edge, an empty
     *            list for an edge list)
     * @return the copy of {@code this}
     */
    final Node clone(Graph into, EnumSet<Edges.Type> edgesToCopy) {
        final NodeClass<? extends Node> nodeClassTmp = getNodeClass();
        boolean useIntoLeafNodeCache = false;
        if (into != null) {
            if (nodeClassTmp.valueNumberable() && nodeClassTmp.isLeafNode()) {
                useIntoLeafNodeCache = true;
                Node otherNode = into.findNodeInCache(this);
                if (otherNode != null) {
                    return otherNode;
                }
            }
        }

        Node newNode = null;
        try {
            newNode = (Node) UNSAFE.allocateInstance(getClass());
            newNode.nodeClass = nodeClassTmp;
            nodeClassTmp.getData().copy(this, newNode);
            copyOrClearEdgesForClone(newNode, Inputs, edgesToCopy);
            copyOrClearEdgesForClone(newNode, Successors, edgesToCopy);
        } catch (Exception e) {
            throw new GraalGraphError(e).addContext(this);
        }
        newNode.graph = into;
        newNode.id = INITIAL_ID;
        if (getNodeSourcePosition() != null && (into == null || into.trackNodeSourcePosition())) {
            newNode.setNodeSourcePosition(getNodeSourcePosition());
        }
        if (into != null) {
            into.register(newNode);
        }
        newNode.extraUsages = NO_NODES;

        if (into != null && useIntoLeafNodeCache) {
            into.putNodeIntoCache(newNode);
        }
        newNode.afterClone(this);
        return newNode;
    }

    protected void afterClone(@SuppressWarnings("unused") Node other) {
    }

    protected boolean verifyInputs() {
        for (Position pos : inputPositions()) {
            Node input = pos.get(this);
            if (input == null) {
                assertTrue(pos.isInputOptional(), "non-optional input %s cannot be null in %s (fix nullness or use @OptionalInput)", pos, this);
            } else {
                assertFalse(input.isDeleted(), "input was deleted %s", input);
                assertTrue(input.isAlive(), "input is not alive yet, i.e., it was not yet added to the graph");
                assertTrue(pos.getInputType() == InputType.Unchecked || input.isAllowedUsageType(pos.getInputType()), "invalid usage type input:%s inputType:%s inputField:%s", input,
                                pos.getInputType(), pos.getName());
                Class<?> expectedType = pos.getType();
                assertTrue(expectedType.isAssignableFrom(input.getClass()), "Invalid input type for %s: expected a %s but was a %s", pos, expectedType, input.getClass());
            }
        }
        return true;
    }

    public boolean verify() {
        assertTrue(isAlive(), "cannot verify inactive node %s", this);
        assertTrue(graph() != null, "null graph");
        verifyInputs();
        if (graph.verifyGraphEdges) {
            verifyEdges();
        }
        return true;
    }

    public boolean verifySourcePosition() {
        return true;
    }

    /**
     * Perform expensive verification of inputs, usages, predecessors and successors.
     *
     * @return true
     */
    public boolean verifyEdges() {
        for (Node input : inputs()) {
            assertTrue(input == null || input.usages().contains(this), "missing usage of %s in input %s", this, input);
        }

        for (Node successor : successors()) {
            assertTrue(successor.predecessor() == this, "missing predecessor in %s (actual: %s)", successor, successor.predecessor());
            assertTrue(successor.graph() == graph(), "mismatching graph in successor %s", successor);
        }
        for (Node usage : usages()) {
            assertFalse(usage.isDeleted(), "usage %s must never be deleted", usage);
            assertTrue(usage.inputs().contains(this), "missing input in usage %s", usage);
            boolean foundThis = false;
            for (Position pos : usage.inputPositions()) {
                if (pos.get(usage) == this) {
                    foundThis = true;
                    if (pos.getInputType() != InputType.Unchecked) {
                        assertTrue(isAllowedUsageType(pos.getInputType()), "invalid input of type %s from %s to %s (%s)", pos.getInputType(), usage, this, pos.getName());
                    }
                }
            }
            assertTrue(foundThis, "missing input in usage %s", usage);
        }

        if (predecessor != null) {
            assertFalse(predecessor.isDeleted(), "predecessor %s must never be deleted", predecessor);
            assertTrue(predecessor.successors().contains(this), "missing successor in predecessor %s", predecessor);
        }
        return true;
    }

    public boolean assertTrue(boolean condition, String message, Object... args) {
        if (condition) {
            return true;
        } else {
            throw fail(message, args);
        }
    }

    public boolean assertFalse(boolean condition, String message, Object... args) {
        if (condition) {
            throw fail(message, args);
        } else {
            return true;
        }
    }

    protected GraalGraphError fail(String message, Object... args) throws GraalGraphError {
        throw new GraalGraphError(message, args).addContext(this);
    }

    public Iterable<? extends Node> cfgPredecessors() {
        if (predecessor == null) {
            return Collections.emptySet();
        } else {
            return Collections.singleton(predecessor);
        }
    }

    /**
     * Returns an iterator that will provide all control-flow successors of {@code this}. Normally
     * this will be the contents of all fields annotated with {@link Successor}, but some node
     * classes (like EndNode) may return different nodes.
     */
    public Iterable<? extends Node> cfgSuccessors() {
        return successors();
    }

    /**
     * Nodes using their {@link #id} as the hash code. This works very well when nodes of the same
     * graph are stored in sets. It can give bad behavior when storing nodes of different graphs in
     * the same set.
     */
    @Override
    public final int hashCode() {
        assert !this.isUnregistered() : "node not yet constructed";
        if (this.isDeleted()) {
            return -id + DELETED_ID_START;
        }
        return id;
    }

    /**
     * Do not overwrite the equality test of a node in subclasses. Equality tests must rely solely
     * on identity.
     */

    /**
     * Provides a {@link Map} of properties of {@code this} for use in debugging (e.g., to view in
     * the ideal graph visualizer).
     */
    public final Map<Object, Object> getDebugProperties() {
        return getDebugProperties(new HashMap<>());
    }

    /**
     * Fills a {@link Map} with properties of {@code this} for use in debugging (e.g., to view in
     * the ideal graph visualizer). Subclasses overriding this method should also fill the map using
     * their superclass.
     *
     * @param map
     */
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        Fields properties = getNodeClass().getData();
        for (int i = 0; i < properties.getCount(); i++) {
            map.put(properties.getName(i), properties.get(this, i));
        }
        NodeSourcePosition pos = getNodeSourcePosition();
        if (pos != null) {
            map.put("nodeSourcePosition", pos);
        }
        NodeCreationStackTrace creation = getCreationPosition();
        if (creation != null) {
            map.put("nodeCreationPosition", creation.getStrackTraceString());
        }
        NodeInsertionStackTrace insertion = getInsertionPosition();
        if (insertion != null) {
            map.put("nodeInsertionPosition", insertion.getStrackTraceString());
        }
        return map;
    }

    /**
     * This method is a shortcut for {@link #toString(Verbosity)} with {@link Verbosity#Short}.
     */
    @Override
    public final String toString() {
        return toString(Verbosity.Short);
    }

    /**
     * Creates a String representation for {@code this} with a given {@link Verbosity}.
     */
    public String toString(Verbosity verbosity) {
        switch (verbosity) {
            case Id:
                return Integer.toString(id);
            case Name:
                return getNodeClass().shortName();
            case Short:
                return toString(Verbosity.Id) + "|" + toString(Verbosity.Name);
            case Long:
                return toString(Verbosity.Short);
            case Debugger:
            case All: {
                StringBuilder str = new StringBuilder();
                str.append(toString(Verbosity.Short)).append(" { ");
                for (Map.Entry<Object, Object> entry : getDebugProperties().entrySet()) {
                    str.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
                }
                str.append(" }");
                return str.toString();
            }
            default:
                throw new RuntimeException("unknown verbosity: " + verbosity);
        }
    }

    @Deprecated
    public int getId() {
        return id;
    }

    @Override
    public void formatTo(Formatter formatter, int flags, int width, int precision) {
        if ((flags & FormattableFlags.ALTERNATE) == FormattableFlags.ALTERNATE) {
            formatter.format("%s", toString(Verbosity.Id));
        } else if ((flags & FormattableFlags.UPPERCASE) == FormattableFlags.UPPERCASE) {
            // Use All here since Long is only slightly longer than Short.
            formatter.format("%s", toString(Verbosity.All));
        } else {
            formatter.format("%s", toString(Verbosity.Short));
        }

        boolean neighborsAlternate = ((flags & FormattableFlags.LEFT_JUSTIFY) == FormattableFlags.LEFT_JUSTIFY);
        int neighborsFlags = (neighborsAlternate ? FormattableFlags.ALTERNATE | FormattableFlags.LEFT_JUSTIFY : 0);
        if (width > 0) {
            if (this.predecessor != null) {
                formatter.format(" pred={");
                this.predecessor.formatTo(formatter, neighborsFlags, width - 1, 0);
                formatter.format("}");
            }

            for (Position position : this.inputPositions()) {
                Node input = position.get(this);
                if (input != null) {
                    formatter.format(" ");
                    formatter.format(position.getName());
                    formatter.format("={");
                    input.formatTo(formatter, neighborsFlags, width - 1, 0);
                    formatter.format("}");
                }
            }
        }

        if (precision > 0) {
            if (!hasNoUsages()) {
                formatter.format(" usages={");
                int z = 0;
                for (Node usage : usages()) {
                    if (z != 0) {
                        formatter.format(", ");
                    }
                    usage.formatTo(formatter, neighborsFlags, 0, precision - 1);
                    ++z;
                }
                formatter.format("}");
            }

            for (Position position : this.successorPositions()) {
                Node successor = position.get(this);
                if (successor != null) {
                    formatter.format(" ");
                    formatter.format(position.getName());
                    formatter.format("={");
                    successor.formatTo(formatter, neighborsFlags, 0, precision - 1);
                    formatter.format("}");
                }
            }
        }
    }

    /**
     * Determines if this node's {@link NodeClass#getData() data} fields are equal to the data
     * fields of another node of the same type. Primitive fields are compared by value and
     * non-primitive fields are compared by {@link Objects#equals(Object, Object)}.
     *
     * The result of this method undefined if {@code other.getClass() != this.getClass()}.
     *
     * @param other a node of exactly the same type as {@code this}
     * @return true if the data fields of this object and {@code other} are equal
     */
    public final boolean valueEquals(Node other) {
        return getNodeClass().dataEquals(this, other);
    }

    /**
     * Determines if {@code this} is equal to the other node while ignoring differences in
     * {@linkplain Successor control-flow} edges.
     *
     */
    public final boolean dataFlowEquals(Node other) {
        return this == other || nodeClass == other.getNodeClass() && this.valueEquals(other) && nodeClass.equalInputs(this, other);
    }

    public final void pushInputs(NodeStack stack) {
        getNodeClass().pushInputs(this, stack);
    }

    public NodeSize estimatedNodeSize() {
        return nodeClass.size();
    }

    public NodeCycles estimatedNodeCycles() {
        return nodeClass.cycles();
    }

}
