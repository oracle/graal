/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.GraalOptions.TrackNodeInsertion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.common.util.EventCounter;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Node.NodeInsertionStackTrace;
import jdk.graal.compiler.graph.Node.ValueNumberable;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class is a graph container, it contains the set of nodes that belong to this graph.
 */
public class Graph implements EventCounter {

    public static class Options {
        @Option(help = "Verify graphs often during compilation when assertions are turned on", type = OptionType.Debug)//
        public static final OptionKey<Boolean> VerifyGraalGraphs = new OptionKey<>(true);
        @Option(help = "Perform expensive verification of graph inputs, usages, successors and predecessors", type = OptionType.Debug)//
        public static final OptionKey<Boolean> VerifyGraalGraphEdges = new OptionKey<>(false);
        @Option(help = "Graal graph compression is performed when percent of live nodes falls below this value", type = OptionType.Debug)//
        public static final OptionKey<Integer> GraphCompressionThreshold = new OptionKey<>(70);
    }

    private enum FreezeState {
        Unfrozen,
        TemporaryFreeze,
        DeepFreeze
    }

    public final String name;

    /**
     * Cached actual value of the {@link Options} to avoid expensive map lookup for every time a
     * node / graph is verified.
     */
    public final boolean verifyGraphs;
    public final boolean verifyGraphEdges;

    /**
     * The set of nodes in the graph, ordered by {@linkplain #register(Node) registration} time.
     */
    Node[] nodes;

    /**
     * Source information to associate with newly created nodes.
     */
    NodeSourcePosition currentNodeSourcePosition;

    /**
     * Records if updating of node source information is required when performing inlining.
     */
    protected boolean trackNodeSourcePosition;

    /**
     * The number of valid entries in {@link #nodes}.
     */
    int nodesSize;

    /**
     * The modification count driven by the {@link NodeEventListener} machinery. Only changes to
     * edges are tracked since that captures meaningful changes to the graph. Adding or deleting
     * nodes without changes edges can't have a material effect on the graph. Changes to other
     * internal state of the nodes isn't captured by this count.
     */
    int edgeModificationCount;

    /**
     * Records the modification count for nodes. This is only used in assertions.
     */
    private int[] nodeModCounts;

    /**
     * Records the modification count for nodes' usage lists. This is only used in assertions.
     */
    private int[] nodeUsageModCounts;

    // these two arrays contain one entry for each NodeClass, indexed by NodeClass.iterableId.
    // they contain the first and last pointer to a linked list of all nodes with this type.
    private final ArrayList<Node> iterableNodesFirst;
    private final ArrayList<Node> iterableNodesLast;

    private int nodesDeletedSinceLastCompression;
    private int nodesDeletedBeforeLastCompression;

    /**
     * The number of times this graph has been compressed.
     */
    int compressions;

    private NodeEventListener nodeEventListener;

    /**
     * Used to global value number {@link ValueNumberable} {@linkplain NodeClass#isLeafNode() leaf}
     * nodes.
     */
    private EconomicMap<Node, Node>[] cachedLeafNodes;

    private static final Equivalence NODE_VALUE_COMPARE = new Equivalence() {

        @Override
        public boolean equals(Object a, Object b) {
            if (a == b) {
                return true;
            }

            assert a.getClass() == b.getClass() : Assertions.errorMessageContext("a", a, "b", b);
            return ((Node) a).valueEquals((Node) b);
        }

        @Override
        public int hashCode(Object k) {
            return ((Node) k).getNodeClass().valueNumber((Node) k);
        }
    };

    /**
     * Indicates that the graph should no longer be modified. Frozen graphs can be used by multiple
     * threads so it's only safe to read them.
     */
    private FreezeState freezeState = FreezeState.Unfrozen;

    /**
     * The option values used while compiling this graph.
     */
    private final OptionValues options;

    /**
     * The {@link DebugContext} used while compiling this graph.
     */
    private DebugContext debug;

    /**
     * Counter to associate "events" with this graph, i.e., have a counter per graph that can be
     * used to trigger certain operations.
     */
    private int eventCounter;
    private final EventCounterMarker eventCounterMarker = new EventCounterMarker();

    @Override
    public EventCounterMarker getEventCounterMarker() {
        return eventCounterMarker;
    }

    @Override
    public boolean eventCounterOverflows(int max) {
        if (eventCounter++ > max) {
            eventCounter = 0;
            return true;
        }
        return false;
    }

    @Override
    public String eventCounterToString() {
        return toString() + " eventCounter=" + eventCounter;
    }

    public int getEventCounter() {
        return eventCounter;
    }

    public int getCompressions() {
        return compressions;
    }

    private class NodeSourcePositionScope implements DebugCloseable {
        private final NodeSourcePosition previous;

        NodeSourcePositionScope(NodeSourcePosition sourcePosition) {
            previous = currentNodeSourcePosition;
            currentNodeSourcePosition = sourcePosition;
        }

        @Override
        public DebugContext getDebug() {
            return debug;
        }

        @Override
        public void close() {
            currentNodeSourcePosition = previous;
        }
    }

    public NodeSourcePosition currentNodeSourcePosition() {
        return currentNodeSourcePosition;
    }

    /**
     * Opens a scope in which the source information from {@code node} is copied into nodes created
     * within the scope. If {@code node} has no source information information, no scope is opened
     * and {@code null} is returned.
     *
     * @return a {@link DebugCloseable} for managing the opened scope or {@code null} if no scope
     *         was opened
     */
    public DebugCloseable withNodeSourcePosition(Node node) {
        return withNodeSourcePosition(node.getNodeSourcePosition());
    }

    /**
     * Opens a scope in which {@code sourcePosition} is copied into nodes created within the scope.
     * If {@code sourcePosition == null}, no scope is opened and {@code null} is returned.
     *
     * @return a {@link DebugCloseable} for managing the opened scope or {@code null} if no scope
     *         was opened
     */
    public DebugCloseable withNodeSourcePosition(NodeSourcePosition sourcePosition) {
        return trackNodeSourcePosition() && sourcePosition != null ? new NodeSourcePositionScope(sourcePosition) : null;
    }

    public boolean trackNodeSourcePosition() {
        return trackNodeSourcePosition;
    }

    public void setTrackNodeSourcePosition() {
        if (!trackNodeSourcePosition) {
            assert getNodeCount() == 1 : "can't change the value after nodes have been added";
            trackNodeSourcePosition = true;
        }
    }

    public static boolean trackNodeSourcePositionDefault(OptionValues options, DebugContext debug) {
        return (GraalOptions.TrackNodeSourcePosition.getValue(options) || debug.isDumpEnabledForMethod());
    }

    /**
     * Add any per graph properties that might be useful for debugging (e.g., to view in the ideal
     * graph visualizer).
     */
    public void getDebugProperties(Map<Object, Object> properties) {
        properties.put("graph", toString());
    }

    /**
     * This is called before nodes are transferred to {@code sourceGraph} by
     * {@link NodeClass#addGraphDuplicate} to allow the transfer of any other state which should
     * also be transferred.
     *
     * @param sourceGraph the source of the nodes that were duplicated
     */
    public void beforeNodeDuplication(Graph sourceGraph) {
    }

    /**
     * Creates an empty Graph with no name.
     */
    public Graph(OptionValues options, DebugContext debug) {
        this(null, options, debug, false);
    }

    /**
     * We only want the expensive modification count tracking when assertions are enabled for the
     * {@link Graph} class.
     */
    @SuppressWarnings("all")
    public static boolean isNodeModificationCountsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    private static final int INITIAL_NODES_SIZE = 32;

    /**
     * Creates an empty Graph with a given name.
     *
     * @param name the name of the graph, used for debugging purposes
     */
    public Graph(String name, OptionValues options, DebugContext debug, boolean trackNodeSourcePosition) {
        nodes = new Node[INITIAL_NODES_SIZE];
        iterableNodesFirst = new ArrayList<>(NodeClass.allocatedNodeIterabledIds());
        iterableNodesLast = new ArrayList<>(NodeClass.allocatedNodeIterabledIds());
        this.name = name;
        this.options = options;
        this.trackNodeSourcePosition = trackNodeSourcePosition || trackNodeSourcePositionDefault(options, debug);
        assert debug != null;
        this.debug = debug;

        if (isNodeModificationCountsEnabled()) {
            nodeModCounts = new int[INITIAL_NODES_SIZE];
            nodeUsageModCounts = new int[INITIAL_NODES_SIZE];
        }

        verifyGraphs = Options.VerifyGraalGraphs.getValue(options);
        verifyGraphEdges = Options.VerifyGraalGraphEdges.getValue(options);
    }

    int extractOriginalNodeId(Node node) {
        int id = node.id;
        if (id <= Node.DELETED_ID_START) {
            id = Node.DELETED_ID_START - id;
        }
        return id;
    }

    int getNodeModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0 && id < nodeModCounts.length) {
            return nodeModCounts[id];
        }
        return 0;
    }

    void incNodeModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0) {
            if (id >= nodeModCounts.length) {
                nodeModCounts = Arrays.copyOf(nodeModCounts, id * 2 + 30);
            }
            nodeModCounts[id]++;
        } else {
            if (Assertions.assertionsEnabled()) {
                throw new AssertionError("ID must be >= 0 but is " + id + " for node " + node);
            }
        }
    }

    int nodeUsageModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0 && id < nodeUsageModCounts.length) {
            return nodeUsageModCounts[id];
        }
        return 0;
    }

    void incNodeUsageModCount(Node node) {
        int id = extractOriginalNodeId(node);
        if (id >= 0) {
            if (id >= nodeUsageModCounts.length) {
                nodeUsageModCounts = Arrays.copyOf(nodeUsageModCounts, id * 2 + 30);
            }
            nodeUsageModCounts[id]++;
        } else {
            if (Assertions.assertionsEnabled()) {
                throw new AssertionError("ID must be >= 0 but is " + id + " for node " + node);
            }
        }
    }

    public int getEdgeModificationCount() {
        return edgeModificationCount;
    }

    /**
     * Creates a copy of this graph.
     *
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    public final Graph copy(DebugContext debugForCopy) {
        return copy(name, null, debugForCopy);
    }

    /**
     * Creates a copy of this graph.
     *
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    public final Graph copy(Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback, DebugContext debugForCopy) {
        return copy(name, duplicationMapCallback, debugForCopy);
    }

    /**
     * Creates a copy of this graph.
     *
     * @param newName the name of the copy, used for debugging purposes (can be null)
     * @param duplicationMapCallback consumer of the duplication map created during the copying
     * @param debugForCopy the debug context for the graph copy. This must not be the debug for this
     *            graph if this graph can be accessed from multiple threads (e.g., it's in a cache
     *            accessed by multiple threads).
     */
    protected Graph copy(String newName, Consumer<UnmodifiableEconomicMap<Node, Node>> duplicationMapCallback, DebugContext debugForCopy) {
        Graph copy = new Graph(newName, options, debugForCopy, trackNodeSourcePosition());
        UnmodifiableEconomicMap<Node, Node> duplicates = copy.addDuplicates(getNodes(), this, this.getNodeCount(), (EconomicMap<Node, Node>) null);
        if (duplicationMapCallback != null) {
            duplicationMapCallback.accept(duplicates);
        }
        return copy;
    }

    public final OptionValues getOptions() {
        return options;
    }

    public DebugContext getDebug() {
        return debug;
    }

    /**
     * Resets the {@link DebugContext} for this graph to a new value. This is useful when a graph is
     * "handed over" from its creating thread to another thread.
     *
     * This must only be done when the current thread is no longer using the graph. This is in
     * general impossible to test due to races and since metrics can be updated at any time. As
     * such, this method only performs a weak sanity check that at least the current debug context
     * does not have a nested scope open (the top level scope will always be open if scopes are
     * enabled).
     */
    public void resetDebug(DebugContext newDebug) {
        assert newDebug == debug || !debug.inNestedScope() : String.format("Cannot reset the debug context for %s while it has the nested scope \"%s\" open", this, debug.getCurrentScopeName());
        this.debug = newDebug;
    }

    @Override
    public String toString() {
        return name == null ? super.toString() : "Graph " + name;
    }

    /**
     * Gets the number of live nodes in this graph. That is the number of nodes which have been
     * added to the graph minus the number of deleted nodes.
     *
     * @return the number of live nodes in this graph
     */
    public int getNodeCount() {
        return nodesSize - getNodesDeletedSinceLastCompression();
    }

    /**
     * Gets the number of nodes which have been deleted from this graph since it was last
     * {@linkplain #maybeCompress() compressed}.
     */
    public int getNodesDeletedSinceLastCompression() {
        return nodesDeletedSinceLastCompression;
    }

    /**
     * Gets the total number of nodes which have been deleted from this graph.
     */
    public int getTotalNodesDeleted() {
        return nodesDeletedSinceLastCompression + nodesDeletedBeforeLastCompression;
    }

    /**
     * Adds a new node to the graph.
     *
     * @param node the node to be added
     * @return the node which was added to the graph
     */
    public <T extends Node> T add(T node) {
        if (node.getNodeClass().valueNumberable()) {
            throw new IllegalStateException("Using add for value numberable node. Consider using either unique or addWithoutUnique.");
        }
        return addHelper(node);
    }

    public <T extends Node> T addWithoutUnique(T node) {
        return addHelper(node);
    }

    /**
     * Returns {@code node} if it is alive in this graph. Otherwise, looks for an existing, GVN
     * equivalent node and returns it if found. If no such node is found, {@code node} is added to
     * this graph and returned.
     * <p>
     * The return value of this function should not be ignored. If the return value is not assigned
     * to {@code node}, {@code node} should not be used afterwards:
     *
     * <pre>
     * x = ...;
     * graph.unique(x); // wrong!
     *
     * x = ...;
     * x = graph.unique(x); // ok
     *
     * x = ...;
     * y = graph.unique(x); // ok
     * // do not use x anymore
     * </pre>
     *
     * @return {@code node} if it is already in this graph or if no GVN equivalent node exists,
     *         otherwise an existing, GVN equivalent node
     */
    public <T extends Node> T addOrUnique(T node) {
        if (node.isAlive()) {
            GraalError.guarantee(node.graph() == this, "Node is alive in another graph.");
            return node;
        }
        if (node.getNodeClass().valueNumberable()) {
            return uniqueHelper(node, null);
        }
        return add(node);
    }

    /**
     * Returns {@code node} if it is alive in this graph. Otherwise, looks for nodes in the graph
     * which are GVN equivalent to {@code node} and its inputs, recursively. Adds all nodes to the
     * graph, where no GVN equivalent nodes are found. Returns either {@code node} or an existing
     * GVN equivalent node.
     * <p>
     * The return value of this function should not be ignored. If the return value is not assigned
     * to {@code node}, {@code node} should not be used afterwards:
     *
     * <pre>
     * x = ...;
     * graph.addOrUniqueWithInputs(x); // wrong!
     *
     * x = ...;
     * x = graph.addOrUniqueWithInputs(x); // ok
     *
     * x = ...;
     * y = graph.addOrUniqueWithInputs(x); // ok
     * // do not use x anymore
     * </pre>
     *
     * @return {@code node} if it is already in this graph or if no GVN equivalent node exists,
     *         otherwise an existing, GVN equivalent node
     */
    public <T extends Node> T addOrUniqueWithInputs(T node) {
        return addOrUniqueWithInputs(node, null);
    }

    /**
     * Returns {@code node} if it is alive in this graph. Otherwise, looks for nodes in the graph
     * which are GVN equivalent to {@code node} and its inputs, recursively. Adds all nodes to the
     * graph, where no GVN equivalent nodes are found. Potentially GVN equivalent nodes are filtered
     * by the predicate. If {@code predicate == null}, it is more likely to find existing nodes in
     * the graph and not add new nodes for all inputs. Returns either {@code node} or an existing,
     * GVN equivalent node.
     * <p>
     * The return value of this function should not be ignored. If the return value is not assigned
     * to {@code node}, {@code node} should not be used afterwards:
     *
     * <pre>
     * x = ...;
     * graph.addOrUniqueWithInputs(x, p); // wrong!
     *
     * x = ...;
     * x = graph.addOrUniqueWithInputs(x, p); // ok
     *
     * x = ...;
     * y = graph.addOrUniqueWithInputs(x, p); // ok
     * // do not use x anymore
     * </pre>
     *
     * @return {@code node} if it is already in this graph or if no GVN equivalent node exists,
     *         otherwise an existing, GVN equivalent node which matches the predicate
     */
    public <T extends Node> T addOrUniqueWithInputs(T node, NodePredicate predicate) {
        if (node.isAlive()) {
            GraalError.guarantee(node.graph() == this, "Node is alive in another graph.");
            return node;
        } else {
            assert node.isUnregistered();
            addInputs(node, predicate);
            if (node.getNodeClass().valueNumberable()) {
                return uniqueHelper(node, predicate);
            }
            return add(node);
        }
    }

    public <T extends Node> T addWithoutUniqueWithInputs(T node) {
        addInputs(node, null);
        return addHelper(node);
    }

    private final class AddInputsFilter extends Node.EdgeVisitor {

        private final NodePredicate predicate;

        AddInputsFilter(NodePredicate predicate) {
            this.predicate = predicate;
        }

        AddInputsFilter() {
            this(null);
        }

        @Override
        public Node apply(Node self, Node input) {
            if (!input.isAlive()) {
                assert !input.isDeleted();
                return addOrUniqueWithInputs(input, predicate);
            } else {
                return input;
            }
        }

    }

    private AddInputsFilter addInputsFilter = new AddInputsFilter();

    private <T extends Node> void addInputs(T node, NodePredicate predicate) {
        if (predicate == null) {
            node.applyInputs(addInputsFilter);
        } else {
            node.applyInputs(new AddInputsFilter(predicate));
        }
    }

    private <T extends Node> T addHelper(T node) {
        node.initialize(this);
        return node;
    }

    /**
     * Notifies node event listeners registered with this graph that {@code node} has been created
     * as part of decoding a graph but its fields have yet to be initialized.
     */
    public void beforeDecodingFields(Node node) {
        fireNodeEvent(Graph.NodeEvent.BEFORE_DECODING_FIELDS, node);
    }

    /**
     * Notifies node event listeners registered with this graph that {@code node} has been created
     * as part of decoding a graph and its fields have now been initialized.
     */
    public void afterDecodingFields(Node node) {
        fireNodeEvent(Graph.NodeEvent.AFTER_DECODING_FIELDS, node);
    }

    /**
     * The type of events sent to a {@link NodeEventListener}.
     */
    public enum NodeEvent {
        /**
         * A node's input is changed.
         */
        INPUT_CHANGED,

        /**
         * A node's {@linkplain Node#usages() usages} count dropped to zero.
         */
        ZERO_USAGES,

        /**
         * A node was added to a graph.
         */
        NODE_ADDED,

        /**
         * A node was removed from the graph.
         */
        NODE_REMOVED,

        /**
         * Graph decoding (partial evaluation) may create nodes adding them to the graph in a "stub"
         * form before filling any node fields. We want to observe these events as well. This event
         * is triggered before a "stub" node's fields are decoded and initialized.
         */
        BEFORE_DECODING_FIELDS,

        /**
         * Graph decoding (partial evaluation) may create nodes adding them to the graph in a "stub"
         * form before filling any node fields. We want to observe these events as well. This event
         * is triggered after a "stub" node's fields are decoded and populated i.e. when the node is
         * no longer a stub.
         */
        AFTER_DECODING_FIELDS,
    }

    public final void fireNodeEvent(NodeEvent e, Node node) {
        if (nodeEventListener != null) {
            NodeEventListener l = nodeEventListener;
            do {
                l.event(e, node);
                l = l.next;
            } while (l != null);
        }
    }

    /**
     * Client interested in one or more node related events.
     */
    public abstract static class NodeEventListener {

        /**
         * Indicates whether this listener is registered at a graph.
         */
        private boolean registered;

        /**
         * Points to the next listener which is registered to the same graph as this listener.
         * Undefined, if this listener is not registered at any graph.
         */
        private NodeEventListener next;

        /**
         * A method called when a change event occurs.
         *
         * This method dispatches the event to user-defined triggers. The methods that change the
         * graph (typically in Graph and Node) must call this method to dispatch the event.
         *
         * @param e an event
         * @param node the node related to {@code e}
         */
        final void event(NodeEvent e, Node node) {
            switch (e) {
                case INPUT_CHANGED:
                    inputChanged(node);
                    break;
                case ZERO_USAGES:
                    GraalError.guarantee(node.isAlive(), "must be alive");
                    usagesDroppedToZero(node);
                    if (!node.isAlive()) {
                        throw new GraalError("%s must not kill %s", this, node);
                    }
                    break;
                case NODE_ADDED:
                    nodeAdded(node);
                    break;
                case NODE_REMOVED:
                    nodeRemoved(node);
                    break;
                case BEFORE_DECODING_FIELDS:
                    beforeDecodingFields(node);
                    break;
                case AFTER_DECODING_FIELDS:
                    afterDecodingFields(node);
                    break;
            }
            changed(e, node);
        }

        /**
         * Notifies this listener about any change event in the graph.
         *
         * @param e an event
         * @param node the node related to {@code e}
         */
        public void changed(NodeEvent e, Node node) {
        }

        /**
         * Notifies this listener about a change in a node's inputs.
         *
         * @param node a node who has had one of its inputs changed
         */
        public void inputChanged(Node node) {
        }

        /**
         * Notifies this listener that the decoding of fields for this node is about to commence.
         *
         * @param node a node whose fields are about to be decoded.
         */
        public void beforeDecodingFields(Node node) {

        }

        /**
         * Notifies this listener that the decoding of fields for this node has finished.
         *
         * @param node a node who has had fields decoded.
         */
        public void afterDecodingFields(Node node) {
        }

        /**
         * Notifies this listener of a node becoming unused.
         *
         * @param node a node whose {@link Node#usages()} just became empty
         */
        public void usagesDroppedToZero(Node node) {
        }

        /**
         * Notifies this listener of an added node.
         *
         * @param node a node that was just added to the graph
         */
        public void nodeAdded(Node node) {
        }

        /**
         * Notifies this listener of a removed node.
         *
         * @param node
         */
        public void nodeRemoved(Node node) {
        }
    }

    /**
     * Registers a given {@link NodeEventListener} with the enclosing graph until this object is
     * {@linkplain #close() closed}.
     */
    public final class NodeEventScope implements AutoCloseable {
        private final NodeEventListener listener;

        NodeEventScope(NodeEventListener listener) {
            GraalError.guarantee(!listener.registered, "Listener already registered");
            this.listener = listener;

            listener.next = nodeEventListener;
            nodeEventListener = listener;
            listener.registered = true;
        }

        @Override
        public void close() {
            GraalError.guarantee(listener.registered, "NodeEventScope has already been closed!");

            if (listener == nodeEventListener) {
                nodeEventListener = listener.next;
            } else if (listener == nodeEventListener.next) {
                nodeEventListener.next = listener.next;
            } else {
                // slow path
                NodeEventListener last = nodeEventListener.next;
                NodeEventListener cur = nodeEventListener.next.next;

                while (cur != listener) {
                    last = cur;
                    cur = cur.next;
                }

                assert cur == listener : "Listener not found";
                last.next = listener.next;
            }

            listener.registered = false;
        }
    }

    /**
     * Registers a given {@link NodeEventListener} with this graph. This should be used in
     * conjunction with try-with-resources statement as follows:
     *
     * <pre>
     * try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
     *     // make changes to the graph
     * }
     * </pre>
     */
    public NodeEventScope trackNodeEvents(NodeEventListener listener) {
        return new NodeEventScope(listener);
    }

    /**
     * Looks for an existing, GVN equivalent node and returns it if found. Otherwise {@code node} is
     * added to this graph and returned.
     * <p>
     * The return value of this function should not be ignored. If the return value is not assigned
     * to {@code node}, {@code node} should not be used afterwards:
     *
     * <pre>
     * x = ...;
     * graph.unique(x); // wrong!
     *
     * x = ...;
     * x = graph.unique(x); // ok
     *
     * x = ...;
     * y = graph.unique(x); // ok
     * // do not use x anymore
     * </pre>
     *
     * @return an existing, GVN equivalent node if one exists, otherwise {@code node}
     */
    public <T extends Node & ValueNumberable> T unique(T node) {
        return uniqueHelper(node, null);
    }

    <T extends Node> T uniqueHelper(T node, NodePredicate predicate) {
        assert node.getNodeClass().valueNumberable();
        T other = this.findDuplicate(node, predicate);
        if (other != null) {
            if (other.getNodeSourcePosition() == null) {
                other.setNodeSourcePosition(node.getNodeSourcePosition());
            }
            return other;
        } else {
            T result = addHelper(node);
            if (node.getNodeClass().isLeafNode()) {
                putNodeIntoCache(result);
            }
            return result;
        }
    }

    void removeNodeFromCache(Node node) {
        assert node.graph() == this || node.graph() == null;
        assert node.getNodeClass().valueNumberable();
        assert node.getNodeClass().isLeafNode() : node.getClass();

        int leafId = node.getNodeClass().getLeafId();
        if (cachedLeafNodes != null && cachedLeafNodes.length > leafId && cachedLeafNodes[leafId] != null) {
            cachedLeafNodes[leafId].removeKey(node);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    void putNodeIntoCache(Node node) {
        assert node.graph() == this || node.graph() == null;
        assert node.getNodeClass().valueNumberable();
        assert node.getNodeClass().isLeafNode() : node.getClass();

        int leafId = node.getNodeClass().getLeafId();
        if (cachedLeafNodes == null || cachedLeafNodes.length <= leafId) {
            EconomicMap[] newLeafNodes = new EconomicMap[leafId + 1];
            if (cachedLeafNodes != null) {
                System.arraycopy(cachedLeafNodes, 0, newLeafNodes, 0, cachedLeafNodes.length);
            }
            cachedLeafNodes = newLeafNodes;
        }

        if (cachedLeafNodes[leafId] == null) {
            cachedLeafNodes[leafId] = EconomicMap.create(NODE_VALUE_COMPARE);
        }

        cachedLeafNodes[leafId].put(node, node);
    }

    Node findNodeInCache(Node node) {
        int leafId = node.getNodeClass().getLeafId();
        if (cachedLeafNodes == null || cachedLeafNodes.length <= leafId || cachedLeafNodes[leafId] == null) {
            return null;
        }

        Node result = cachedLeafNodes[leafId].get(node);
        if (result != null && !result.isAlive()) {
            return null;
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public <T extends Node> T findDuplicate(T node) {
        return findDuplicate(node, null);
    }

    /**
     * Returns a possible duplicate for the given node in the graph or {@code null} if no such
     * duplicate exists. The predicate parameter is used to filter potential results.
     */
    @SuppressWarnings("unchecked")
    public <T extends Node> T findDuplicate(T node, NodePredicate predicate) {
        NodeClass<?> nodeClass = node.getNodeClass();
        assert nodeClass.valueNumberable();
        if (nodeClass.isLeafNode()) {
            // Leaf node: look up in cache
            Node cachedNode = findNodeInCache(node);
            if (cachedNode != null && cachedNode != node && (predicate == null || predicate.apply(cachedNode))) {
                return (T) cachedNode;
            } else {
                return null;
            }
        } else {
            /*
             * Non-leaf node: look for another usage of the node's inputs that has the same data,
             * inputs and successors as the node. To reduce the cost of this computation, only the
             * input with lowest usage count is considered. If this node is the only user of any
             * input then the search can terminate early. The usage count is only incremented once
             * the Node is in the Graph, so account for that in the test.
             */
            final int earlyExitUsageCount = node.graph() != null ? 1 : 0;
            int minCount = Integer.MAX_VALUE;
            Node minCountNode = null;
            for (Node input : node.inputs()) {
                int usageCount = input.getUsageCount();
                if (usageCount == earlyExitUsageCount) {
                    return null;
                } else if (usageCount < minCount) {
                    minCount = usageCount;
                    minCountNode = input;
                }
            }
            if (minCountNode != null) {
                for (Node usage : minCountNode.usages()) {
                    if (usage != node && nodeClass == usage.getNodeClass() && (predicate == null || predicate.apply(usage)) && node.valueEquals(usage) && nodeClass.equalInputs(node, usage) &&
                                    nodeClass.equalSuccessors(node, usage)) {
                        return (T) usage;
                    }
                }
                return null;
            }
            return null;
        }
    }

    public boolean isNew(Mark mark, Node node) {
        return node.id >= mark.getValue();
    }

    /**
     * A snapshot of the {@linkplain Graph#getNodeCount() live node count} in a graph.
     */
    public static class Mark extends NodeIdAccessor {
        private final int value;

        Mark(Graph graph) {
            super(graph);
            this.value = graph.nodeIdCount();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Mark) {
                Mark other = (Mark) obj;
                return other.getValue() == getValue() && other.getGraph() == getGraph();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return value ^ (epoch + 11);
        }

        /**
         * Gets the {@linkplain Graph#getNodeCount() live node count} of the associated graph when
         * this object was created.
         */
        int getValue() {
            return value;
        }

        /**
         * Determines if this mark still represents the {@linkplain Graph#getNodeCount() live node
         * count} of the graph.
         */
        public boolean isCurrent() {
            return value == graph.nodeIdCount();
        }
    }

    /**
     * Gets a mark that can be used with {@link #getNewNodes}.
     */
    public Mark getMark() {
        return new Mark(this);
    }

    /**
     * Returns an {@link Iterable} providing all nodes added since the last {@link Graph#getMark()
     * mark}.
     */
    public NodeIterable<Node> getNewNodes(Mark mark) {
        final int index = mark == null ? 0 : mark.getValue();
        return () -> new GraphNodeIterator(Graph.this, index);
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes.
     *
     * @return an {@link Iterable} providing all the live nodes.
     */
    public NodeIterable<Node> getNodes() {
        CompilationAlarm.checkProgress(this);
        return new NodeIterable<>() {

            @Override
            public Iterator<Node> iterator() {
                return new GraphNodeIterator(Graph.this);
            }

            @Override
            public int count() {
                return getNodeCount();
            }
        };
    }

    private static final CounterKey GraphCompressions = DebugContext.counter("GraphCompressions");

    @SuppressWarnings("unused")
    protected Object beforeNodeIdChange(Node node) {
        return null;
    }

    @SuppressWarnings("unused")
    protected void afterNodeIdChange(Node node, Object value) {
    }

    /**
     * If the {@linkplain Options#GraphCompressionThreshold compression threshold} is met, the list
     * of nodes is compressed such that all non-null entries precede all null entries while
     * preserving the ordering between the nodes within the list.
     */
    public final boolean maybeCompress() {
        return compress(false);
    }

    protected boolean compress(boolean minimizeSize) {
        if (!minimizeSize && (debug.isDumpEnabledForMethod() || debug.isLogEnabledForMethod())) {
            return false;
        }
        int liveNodeCount = getNodeCount();
        if (!minimizeSize) {
            int liveNodePercent = liveNodeCount * 100 / nodesSize;
            int compressionThreshold = Options.GraphCompressionThreshold.getValue(options);
            if (compressionThreshold == 0 || liveNodePercent >= compressionThreshold) {
                return false;
            }
        }
        GraphCompressions.increment(debug);
        int nextId = 0;
        for (int i = 0; nextId < liveNodeCount; i++) {
            Node n = nodes[i];
            if (n != null) {
                assert n.id == i : Assertions.errorMessage(n, i);
                if (i != nextId) {
                    assert n.id > nextId : n.id + " must be > " + nextId + " for " + n;
                    Object value = beforeNodeIdChange(n);
                    n.id = nextId;
                    afterNodeIdChange(n, value);
                    nodes[nextId] = n;
                    nodes[i] = null;
                }
                nextId++;
            }
        }
        if (isNodeModificationCountsEnabled()) {
            // This will cause any current iteration to fail with an assertion
            Arrays.fill(nodeModCounts, 0);
            Arrays.fill(nodeUsageModCounts, 0);
        }
        nodesSize = nextId;
        compressions++;
        nodesDeletedBeforeLastCompression += nodesDeletedSinceLastCompression;
        nodesDeletedSinceLastCompression = 0;

        if (minimizeSize) {
            /* Trim the array of all alive nodes itself. */
            nodes = trimArrayToNewSize(nodes, nextId);
            /* Remove deleted nodes from the linked list of Node.typeCacheNext. */
            recomputeIterableNodeLists();
            /* Trim node arrays used within each node. */
            for (Node node : nodes) {
                node.extraUsages = trimArrayToNewSize(node.extraUsages, node.extraUsagesCount);
                node.getNodeClass().getInputEdges().minimizeSize(node);
                node.getNodeClass().getSuccessorEdges().minimizeSize(node);
            }
        }
        return true;
    }

    static Node[] trimArrayToNewSize(Node[] input, int newSize) {
        assert input.getClass() == Node[].class;
        if (input.length == newSize) {
            return input;
        }
        for (int i = newSize; i < input.length; i++) {
            GraalError.guarantee(input[i] == null, "removing non-null element");
        }

        if (newSize == 0) {
            return Node.EMPTY_ARRAY;
        } else {
            return Arrays.copyOf(input, newSize, Node[].class);
        }
    }

    /**
     * Returns an {@link Iterable} providing all the live nodes whose type is compatible with
     * {@code type}.
     *
     * @param nodeClass the type of node to return
     * @return an {@link Iterable} providing all the matching nodes
     */
    public <T extends Node & IterableNodeType> NodeIterable<T> getNodes(final NodeClass<T> nodeClass) {
        return new NodeIterable<>() {

            @Override
            public Iterator<T> iterator() {
                return new TypedGraphNodeIterator<>(nodeClass, Graph.this);
            }
        };
    }

    /**
     * Returns whether the graph contains at least one node of the given type.
     *
     * @param type the type of node that is checked for occurrence
     * @return whether there is at least one such node
     */
    public <T extends Node & IterableNodeType> boolean hasNode(final NodeClass<T> type) {
        return getNodes(type).iterator().hasNext();
    }

    /**
     * @param iterableId
     * @return the first live Node with a matching iterableId
     */
    Node getIterableNodeStart(int iterableId) {
        if (iterableNodesFirst.size() <= iterableId) {
            return null;
        }
        Node start = iterableNodesFirst.get(iterableId);
        if (start == null || !start.isDeleted()) {
            return start;
        }
        return findFirstLiveIterable(iterableId, start);
    }

    private Node findFirstLiveIterable(int iterableId, Node node) {
        Node start = node;
        while (start != null && start.isDeleted()) {
            start = start.typeCacheNext;
        }
        /*
         * Multiple threads iterating nodes can update this cache simultaneously. This is a benign
         * race, since all threads update it to the same value.
         */
        iterableNodesFirst.set(iterableId, start);
        if (start == null) {
            iterableNodesLast.set(iterableId, start);
        }
        return start;
    }

    /**
     * @param node
     * @return return the first live Node with a matching iterableId starting from {@code node}
     */
    Node getIterableNodeNext(Node node) {
        if (node == null || !node.isDeleted()) {
            return node;
        }

        return findNextLiveiterable(node);
    }

    private Node findNextLiveiterable(Node start) {
        Node n = start;
        while (n != null && n.isDeleted()) {
            n = n.typeCacheNext;
        }
        if (n == null) {
            // Only dead nodes after this one
            start.typeCacheNext = null;
            int nodeClassId = start.getNodeClass().iterableId();
            assert nodeClassId != Node.NOT_ITERABLE : nodeClassId;
            iterableNodesLast.set(nodeClassId, start);
        } else {
            // Everything in between is dead
            start.typeCacheNext = n;
        }
        return n;
    }

    public NodeBitMap createNodeBitMap() {
        return new NodeBitMap(this);
    }

    public <T> NodeMap<T> createNodeMap() {
        return new NodeMap<>(this);
    }

    public NodeFlood createNodeFlood() {
        return new NodeFlood(this);
    }

    public NodeWorkList createNodeWorkList() {
        return new NodeWorkList.SingletonNodeWorkList(this);
    }

    public NodeWorkList createIterativeNodeWorkList(boolean fill, int iterationLimitPerNode) {
        return new NodeWorkList.IterativeNodeWorkList(this, fill, iterationLimitPerNode);
    }

    void register(Node node) {
        assert !isFrozen();
        assert node.id() == Node.INITIAL_ID : Assertions.errorMessage(node);
        if (nodes.length == nodesSize) {
            grow();
        }
        int id = nodesSize++;
        nodes[id] = node;
        node.id = id;
        if (currentNodeSourcePosition != null && trackNodeSourcePosition()) {
            node.setNodeSourcePosition(currentNodeSourcePosition);
        }
        if (TrackNodeInsertion.getValue(getOptions()) && node.getInsertionPosition() == null) {
            node.setInsertionPosition(new NodeInsertionStackTrace());
        }

        updateNodeCaches(node);

        fireNodeEvent(Graph.NodeEvent.NODE_ADDED, node);
        afterRegister(node);
    }

    private void grow() {
        Node[] newNodes = new Node[(nodesSize * 2) + 1];
        System.arraycopy(nodes, 0, newNodes, 0, nodesSize);
        nodes = newNodes;
    }

    @SuppressWarnings("unused")
    protected void afterRegister(Node node) {

    }

    /**
     * Rebuilds the lists used to support {@link #getNodes(NodeClass)}. This is useful for
     * serialization where the underlying {@linkplain NodeClass#iterableId() iterable ids} may have
     * changed.
     */
    private void recomputeIterableNodeLists() {
        iterableNodesFirst.clear();
        iterableNodesLast.clear();
        for (Node node : nodes) {
            if (node != null && node.isAlive()) {
                updateNodeCaches(node);
            }
        }
    }

    private void updateNodeCaches(Node node) {
        int nodeClassId = node.getNodeClass().iterableId();
        if (nodeClassId != Node.NOT_ITERABLE) {
            while (iterableNodesFirst.size() <= nodeClassId) {
                iterableNodesFirst.add(null);
                iterableNodesLast.add(null);
            }
            Node prev = iterableNodesLast.get(nodeClassId);
            if (prev != null) {
                prev.typeCacheNext = node;
            } else {
                iterableNodesFirst.set(nodeClassId, node);
            }
            iterableNodesLast.set(nodeClassId, node);
        }
    }

    void unregister(Node node) {
        assert !isFrozen();
        assert !node.isDeleted() : node;
        if (node.getNodeClass().isLeafNode() && node.getNodeClass().valueNumberable()) {
            removeNodeFromCache(node);
        }
        nodes[node.id] = null;
        nodesDeletedSinceLastCompression++;

        fireNodeEvent(Graph.NodeEvent.NODE_REMOVED, node);
        // nodes aren't removed from the type cache here - they will be removed during iteration
    }

    public final boolean verify() {
        verify(true);
        return true;
    }

    public boolean verify(boolean verifyInputs) {
        if (verifyGraphs) {
            for (Node node : getNodes()) {
                try {
                    try {
                        assert node.verify(verifyInputs);
                    } catch (AssertionError t) {
                        throw new GraalError(t);
                    } catch (RuntimeException t) {
                        throw new GraalError(t);
                    }
                } catch (GraalError e) {
                    throw GraalGraphError.transformAndAddContext(e, node).addContext(this);
                }
            }
        }
        return true;
    }

    public boolean verifySourcePositions(boolean performConsistencyCheck) {
        if (trackNodeSourcePosition()) {
            ResolvedJavaMethod root = null;
            for (Node node : getNodes()) {
                NodeSourcePosition pos = node.getNodeSourcePosition();
                if (pos != null) {
                    if (root == null) {
                        root = pos.getRootMethod();
                    } else {
                        assert pos.verifyRootMethod(root);
                    }
                }

                // More strict node-type-specific check
                if (performConsistencyCheck) {
                    // Disabled due to GR-10445.
                    // node.verifySourcePosition();
                }
            }
        }
        return true;
    }

    public Node getNode(int id) {
        return nodes[id];
    }

    /**
     * Returns the number of node ids generated so far.
     *
     * @return the number of node ids generated so far
     */
    public int nodeIdCount() {
        return nodesSize;
    }

    /**
     * Adds duplicates of the nodes in {@code newNodes} to this graph. This will recreate any edges
     * between the duplicate nodes. The {@code replacement} map can be used to replace a node from
     * the source graph by a given node (which must already be in this graph). Edges between
     * duplicate and replacement nodes will also be recreated so care should be taken regarding the
     * matching of node types in the replacement map.
     *
     * @param newNodes the nodes to be duplicated
     * @param replacementsMap the replacement map (can be null if no replacement is to be performed)
     * @return a map which associates the original nodes from {@code nodes} to their duplicates
     */
    public EconomicMap<Node, Node> addDuplicates(Iterable<? extends Node> newNodes, final Graph oldGraph, int estimatedNodeCount, UnmodifiableEconomicMap<Node, Node> replacementsMap) {
        DuplicationReplacement replacements;
        if (replacementsMap == null) {
            replacements = null;
        } else {
            replacements = new MapReplacement(replacementsMap);
        }
        return addDuplicates(newNodes, oldGraph, estimatedNodeCount, replacements);
    }

    public interface DuplicationReplacement {

        Node replacement(Node original);
    }

    private static final class MapReplacement implements DuplicationReplacement {

        private final UnmodifiableEconomicMap<Node, Node> map;

        MapReplacement(UnmodifiableEconomicMap<Node, Node> map) {
            this.map = map;
        }

        @Override
        public Node replacement(Node original) {
            Node replacement = map.get(original);
            return replacement != null ? replacement : original;
        }

    }

    private static final TimerKey DuplicateGraph = DebugContext.timer("DuplicateGraph");

    @SuppressWarnings({"all", "try"})
    public EconomicMap<Node, Node> addDuplicates(Iterable<? extends Node> newNodes, final Graph oldGraph, int estimatedNodeCount, DuplicationReplacement replacements) {
        try (DebugCloseable s = DuplicateGraph.start(getDebug())) {
            return NodeClass.addGraphDuplicate(this, oldGraph, estimatedNodeCount, newNodes, replacements);
        }
    }

    public boolean isFrozen() {
        return freezeState != FreezeState.Unfrozen;
    }

    public void freeze() {
        this.freezeState = FreezeState.DeepFreeze;
    }

    public void temporaryFreeze() {
        if (this.freezeState == FreezeState.DeepFreeze) {
            throw new GraalError("Graph was permanetly frozen.");
        }
        this.freezeState = FreezeState.TemporaryFreeze;
    }

    public void unfreeze() {
        if (this.freezeState == FreezeState.DeepFreeze) {
            throw new GraalError("Graph was permanetly frozen.");
        }
        this.freezeState = FreezeState.Unfrozen;
    }
}
