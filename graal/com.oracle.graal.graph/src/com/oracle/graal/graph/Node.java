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
package com.oracle.graal.graph;

import java.lang.annotation.*;
import java.util.*;

import com.oracle.graal.graph.Graph.NodeChangedListener;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.graph.iterators.*;

/**
 * This class is the base class for all nodes, it represent a node which can be inserted in a
 * {@link Graph}.
 * <p>
 * Once a node has been added to a graph, it has a graph-unique {@link #id()}. Edges in the
 * subclasses are represented with annotated fields. There are two kind of edges : {@link Input} and
 * {@link Successor}. If a field, of a type compatible with {@link Node}, annotated with either
 * {@link Input} and {@link Successor} is not null, then there is an edge from this node to the node
 * this field points to.
 * <p>
 * Nodes which are be value numberable should implement the {@link ValueNumberable} interface.
 * 
 * <h1>Assertions and Verification</h1>
 * 
 * The Node class supplies the {@link #assertTrue(boolean, String, Object...)} and
 * {@link #assertFalse(boolean, String, Object...)} methods, which will check the supplied boolean
 * and throw a VerificationError if it has the wrong value. Both methods will always either throw an
 * exception or return true. They can thus be used within an assert statement, so that the check is
 * only performed if assertions are enabled.
 */
public abstract class Node implements Cloneable, Formattable {

    static final int DELETED_ID_START = -1000000000;
    static final int INITIAL_ID = -1;
    static final int ALIVE_ID_START = 0;

    /**
     * Denotes a node input. This should be applied to exactly the fields of a node that are of type
     * {@link Node}. Nodes that update their inputs outside of their constructor should call
     * {@link Node#updateUsages(Node, Node)} just prior to doing the update of the input.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Input {

        boolean notDataflow() default false;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public static @interface Successor {
    }

    /**
     * Denotes that a parameter of an {@linkplain NodeIntrinsic intrinsic} method must be a compile
     * time constant at all call sites to the intrinsic method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public static @interface ConstantNodeParameter {
    }

    /**
     * Annotates a method that can be replaced by a compiler intrinsic. A (resolved) call to the
     * annotated method can be replaced with an instance of the node class denoted by
     * {@link #value()}. For this reason, the signature of the annotated method must match the
     * signature of a constructor in the node class.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public static @interface NodeIntrinsic {

        /**
         * Gets the {@link Node} subclass instantiated when intrinsifying a call to the annotated
         * method. If not specified, then the class in which the annotated method is declared is
         * used (and is assumed to be a {@link Node} subclass).
         */
        Class value() default NodeIntrinsic.class;

        /**
         * Determines if the stamp of the instantiated intrinsic node has its stamp set from the
         * return type of the annotated method.
         */
        boolean setStampFromReturnType() default false;
    }

    public interface ValueNumberable {
    }

    public interface IterableNodeType {
    }

    private Graph graph;
    int id;

    // this next pointer is used in Graph to implement fast iteration over NodeClass types, it
    // therefore points to the next Node of the same type.
    Node typeCacheNext;

    private NodeUsagesList usages;
    private Node predecessor;
    private int modCount;

    public Node() {
        this.graph = null;
        this.id = INITIAL_ID;
    }

    protected int id() {
        return id;
    }

    public Graph graph() {
        return graph;
    }

    /**
     * Returns an {@link NodeClassIterable iterable} which can be used to traverse all non-null
     * input edges of this node.
     * 
     * @return an {@link NodeClassIterable iterable} for all non-null input edges.
     */
    public NodeClassIterable inputs() {
        return getNodeClass().getInputIterable(this);
    }

    /**
     * Returns an {@link NodeClassIterable iterable} which can be used to traverse all non-null
     * successor edges of this node.
     * 
     * @return an {@link NodeClassIterable iterable} for all non-null successor edges.
     */
    public NodeClassIterable successors() {
        return getNodeClass().getSuccessorIterable(this);
    }

    public final NodeIterable<Node> usages() {
        return usages;
    }

    public final Node predecessor() {
        return predecessor;
    }

    final int modCount() {
        return modCount;
    }

    final void incModCount() {
        modCount++;
    }

    public boolean isDeleted() {
        return id <= DELETED_ID_START;
    }

    public boolean isAlive() {
        return id >= ALIVE_ID_START;
    }

    /**
     * Updates the usages sets of the given nodes after an input slot is changed from oldInput to
     * newInput: removes this node from oldInput's usages and adds this node to newInput's usages.
     */
    protected void updateUsages(Node oldInput, Node newInput) {
        assert assertTrue(usages != null, "usages == null while adding %s to %s", newInput, this);
        if (oldInput != newInput) {
            if (oldInput != null) {
                boolean result = removeThisFromUsages(oldInput);
                assert assertTrue(result, "not found in usages, old input: %s", oldInput);
            }
            if (newInput != null) {
                NodeChangedListener inputChanged = graph.inputChanged;
                if (inputChanged != null) {
                    inputChanged.nodeChanged(this);
                }
                assert newInput.usages != null : "not yet added? " + newInput;
                newInput.usages.add(this);
            } else if (oldInput != null && oldInput.usages().isEmpty()) {
                NodeChangedListener nodeChangedListener = graph.usagesDroppedZero;
                if (nodeChangedListener != null) {
                    nodeChangedListener.nodeChanged(oldInput);
                }
            }
        }
    }

    /**
     * Updates the predecessor of the given nodes after a successor slot is changed from
     * oldSuccessor to newSuccessor: removes this node from oldSuccessor's predecessors and adds
     * this node to newSuccessor's predecessors.
     */
    protected void updatePredecessor(Node oldSuccessor, Node newSuccessor) {
        assert assertTrue(usages != null, "usages == null while adding %s to %s", newSuccessor, this);
        if (oldSuccessor != newSuccessor) {
            if (oldSuccessor != null) {
                assert assertTrue(oldSuccessor.predecessor == this, "wrong predecessor in old successor (%s): %s", oldSuccessor, oldSuccessor.predecessor);
                oldSuccessor.predecessor = null;
            }
            if (newSuccessor != null) {
                assert assertTrue(newSuccessor.predecessor == null, "unexpected non-null predecessor in new successor (%s): %s", newSuccessor, newSuccessor.predecessor);
                newSuccessor.predecessor = this;
            }
        }
    }

    void initialize(Graph newGraph) {
        assert assertTrue(id == INITIAL_ID, "unexpected id: %d", id);
        this.graph = newGraph;
        newGraph.register(this);
        usages = new NodeUsagesList();
        for (Node input : inputs()) {
            updateUsages(null, input);
        }
        for (Node successor : successors()) {
            updatePredecessor(null, successor);
        }
    }

    public final NodeClass getNodeClass() {
        return NodeClass.get(getClass());
    }

    private boolean checkReplaceWith(Node other) {
        assert assertFalse(other == this, "cannot replace a node with itself");
        assert assertFalse(isDeleted(), "cannot replace deleted node");
        assert assertTrue(other == null || !other.isDeleted(), "cannot replace with deleted node %s", other);
        assert assertTrue(other == null || other.graph() == graph, "cannot replace with node in different graph: %s", other == null ? null : other.graph());
        return true;
    }

    public void replaceAtUsages(Node other) {
        assert checkReplaceWith(other);
        for (Node usage : usages) {
            boolean result = usage.getNodeClass().replaceFirstInput(usage, this, other);
            assert assertTrue(result, "not found in inputs, usage: %s", usage);
            if (other != null) {
                NodeChangedListener inputChanged = graph.inputChanged;
                if (inputChanged != null) {
                    inputChanged.nodeChanged(usage);
                }
                other.usages.add(usage);
            }
        }
        usages.clear();
    }

    public void replaceAtPredecessor(Node other) {
        assert checkReplaceWith(other);
        if (predecessor != null) {
            boolean result = predecessor.getNodeClass().replaceFirstSuccessor(predecessor, this, other);
            assert assertTrue(result, "not found in successors, predecessor: %s", predecessor);
            predecessor.updatePredecessor(this, other);
        }
    }

    public void replaceAndDelete(Node other) {
        assert checkReplaceWith(other);
        if (other != null) {
            clearSuccessors();
            replaceAtUsages(other);
            replaceAtPredecessor(other);
        }
        safeDelete();
    }

    public void replaceFirstSuccessor(Node oldSuccessor, Node newSuccessor) {
        if (getNodeClass().replaceFirstSuccessor(this, oldSuccessor, newSuccessor)) {
            updatePredecessor(oldSuccessor, newSuccessor);
        }
    }

    public void replaceFirstInput(Node oldInput, Node newInput) {
        if (getNodeClass().replaceFirstInput(this, oldInput, newInput)) {
            updateUsages(oldInput, newInput);
        }
    }

    public void clearInputs() {
        assert assertFalse(isDeleted(), "cannot clear inputs of deleted node");

        for (Node input : inputs()) {
            removeThisFromUsages(input);
            if (input.usages().isEmpty()) {
                NodeChangedListener nodeChangedListener = graph.usagesDroppedZero;
                if (nodeChangedListener != null) {
                    nodeChangedListener.nodeChanged(input);
                }
            }
        }
        getNodeClass().clearInputs(this);
    }

    private boolean removeThisFromUsages(Node n) {
        if (n.usages.remove(this)) {
            return true;
        } else {
            return false;
        }
    }

    public void clearSuccessors() {
        assert assertFalse(isDeleted(), "cannot clear successors of deleted node");

        for (Node successor : successors()) {
            assert assertTrue(successor.predecessor == this, "wrong predecessor in old successor (%s): %s", successor, successor.predecessor);
            successor.predecessor = null;
        }
        getNodeClass().clearSuccessors(this);
    }

    private boolean checkDeletion() {
        assertTrue(usages.isEmpty(), "cannot delete node %s because of usages: %s", this, usages);
        assertTrue(predecessor == null, "cannot delete node %s because of predecessor: %s", this, predecessor);
        return true;
    }

    /**
     * Removes this node from its graph. This node must have no {@linkplain Node#usages() usages}
     * and no {@linkplain #predecessor() predecessor}.
     */
    public void safeDelete() {
        assert checkDeletion();
        clearInputs();
        clearSuccessors();
        graph.unregister(this);
        id = DELETED_ID_START - id;
        assert isDeleted();
    }

    public final Node copyWithInputs() {
        Node newNode = clone(graph);
        NodeClass clazz = getNodeClass();
        clazz.copyInputs(this, newNode);
        for (Node input : inputs()) {
            input.usages.add(newNode);
        }
        return newNode;
    }

    public Node clone(Graph into) {
        Node newNode = null;
        try {
            newNode = (Node) this.clone();
        } catch (CloneNotSupportedException e) {
            throw new GraalInternalError(e).addContext(this);
        }
        getNodeClass().clearInputs(newNode);
        getNodeClass().clearSuccessors(newNode);
        newNode.graph = into;
        newNode.typeCacheNext = null;
        newNode.id = INITIAL_ID;
        into.register(newNode);
        newNode.usages = new NodeUsagesList();
        newNode.predecessor = null;
        newNode.modCount = 0;
        return newNode;
    }

    public boolean verify() {
        assertTrue(isAlive(), "cannot verify inactive nodes (id=%d)", id);
        assertTrue(graph() != null, "null graph");
        for (Node input : inputs()) {
            assertTrue(input.usages().contains(this), "missing usage in input %s", input);
            assertTrue(input.graph() == graph(), "mismatching graph in input %s", input);
        }
        for (Node successor : successors()) {
            assertTrue(successor.predecessor() == this, "missing predecessor in %s (actual: %s)", successor, successor.predecessor());
            assertTrue(successor.graph() == graph(), "mismatching graph in successor %s", successor);
        }
        for (Node usage : usages()) {
            assertFalse(usage.isDeleted(), "usage must never be deleted");
            assertTrue(usage.inputs().contains(this), "missing input in usage %s", usage);
        }
        if (predecessor != null) {
            assertFalse(predecessor.isDeleted(), "predecessor must never be deleted");
            assertTrue(predecessor.successors().contains(this), "missing successor in predecessor %s", predecessor);
        }
        return true;
    }

    public boolean assertTrue(boolean condition, String message, Object... args) {
        if (condition) {
            return true;
        } else {
            throw new VerificationError(message, args).addContext(this);
        }
    }

    public boolean assertFalse(boolean condition, String message, Object... args) {
        if (condition) {
            throw new VerificationError(message, args).addContext(this);
        } else {
            return true;
        }
    }

    public Iterable<? extends Node> cfgPredecessors() {
        if (predecessor == null) {
            return Collections.emptySet();
        } else {
            return Collections.singleton(predecessor);
        }
    }

    /**
     * Returns an iterator that will provide all control-flow successors of this node. Normally this
     * will be the contents of all fields marked as NodeSuccessor, but some node classes (like
     * EndNode) may return different nodes. Note that the iterator may generate null values if the
     * fields contain them.
     */
    public Iterable<? extends Node> cfgSuccessors() {
        return successors();
    }

    /**
     * hashCode and equals should always rely on object identity alone, thus hashCode and equals are
     * final.
     */
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    /**
     * hashCode and equals should always rely on object identity alone, thus hashCode and equals are
     * final.
     */
    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * Provides a {@link Map} of properties of this node for use in debugging (e.g., to view in the
     * ideal graph visualizer).
     */
    public final Map<Object, Object> getDebugProperties() {
        return getDebugProperties(new HashMap<>());
    }

    /**
     * Fills a {@link Map} with properties of this node for use in debugging (e.g., to view in the
     * ideal graph visualizer). Subclasses overriding this method should also fill the map using
     * their superclass.
     * 
     * @param map
     */
    public Map<Object, Object> getDebugProperties(Map<Object, Object> map) {
        getNodeClass().getDebugProperties(this, map);
        return map;
    }

    /**
     * This method is a shortcut for {@link #toString(Verbosity)} with {@link Verbosity#Short}.
     */
    @Override
    public final String toString() {
        return toString(Verbosity.Short);
    }

    public enum Verbosity {
        /**
         * Only the id of the node.
         */
        Id,
        /**
         * Only the name of the node, which may contain some more information for certain node types
         * (constants, ...).
         */
        Name,
        /**
         * {@link #Id} + {@link #Name}.
         */
        Short,
        /**
         * Defaults to {@link #Short} and may be enhanced by subclasses.
         */
        Long,
        /**
         * For use by a custom formatting facility in an IDE.
         */
        Debugger,
        /**
         * All the other information plus all debug properties of the node.
         */
        All
    }

    /**
     * Creates a String representation for this node with a given {@link Verbosity}.
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
            formatter.format("%s", toString(Verbosity.Long));
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

            NodeClassIterator inputIter = inputs().iterator();
            while (inputIter.hasNext()) {
                Position position = inputIter.nextPosition();
                Node input = getNodeClass().get(this, position);
                if (input != null) {
                    formatter.format(" ");
                    formatter.format(getNodeClass().getName(position));
                    formatter.format("={");
                    input.formatTo(formatter, neighborsFlags, width - 1, 0);
                    formatter.format("}");
                }
            }
        }

        if (precision > 0) {
            if (this.usages.count() > 0) {
                formatter.format(" usages={");
                int z = 0;
                for (Node usage : this.usages) {
                    if (z != 0) {
                        formatter.format(", ");
                    }
                    usage.formatTo(formatter, neighborsFlags, 0, precision - 1);
                    ++z;
                }
                formatter.format("}");
            }

            NodeClassIterator succIter = successors().iterator();
            while (succIter.hasNext()) {
                Position position = succIter.nextPosition();
                Node successor = getNodeClass().get(this, position);
                if (successor != null) {
                    formatter.format(" ");
                    formatter.format(getNodeClass().getName(position));
                    formatter.format("={");
                    successor.formatTo(formatter, neighborsFlags, 0, precision - 1);
                    formatter.format("}");
                }
            }
        }
    }
}
