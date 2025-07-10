/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.webimage.codegen.lowerer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;

/**
 * Schedules a set of moves that are supposed to happen simultaneously as consecutive moves while
 * preserving the desired assignments.
 * <p>
 * The algorithm is generic in what kind of values are moved around to facilitate testing, but the
 * purpose of this class is to schedule assignments of {@link ValuePhiNode}s at
 * {@link AbstractEndNode}s.
 * <p>
 * At an {@link AbstractEndNode}, the values of all phi nodes used in the associated
 * {@link AbstractMergeNode} need to be written somewhere so that they can be used after the merge.
 * This can be seen as directed graph of moves where the nodes are the phi nodes and their values
 * and each edge {@code (u, v)} is a move from a phi node's value {@code u} into the phi node's
 * location {@code v} (this can be a variable, register, memory location, etc.). All these moves are
 * expected to happen simultaneously and result in the location {@code v} containing {@code u} for
 * all edges {@code (u, v)}.
 * <p>
 * The problem arises when the sources of those moves are phis that are also a target of a move. For
 * example:
 *
 * <pre>
 * value1 -> phi1
 * phi1   -> phi2
 * </pre>
 *
 * Here the move {@code phi1 -> phi2} has to be scheduled first, otherwise it contains
 * {@code value1} instead of its original value.
 * <p>
 * An example where simple reordering is not enough:
 *
 * <pre>
 * phi1 -> phi2
 * phi2 -> phi1
 * </pre>
 *
 * This requests a swap of the values in {@code phi1} and {@code phi2}. In the graph, this would be
 * a cycle and has to be scheduled like this:
 *
 * <pre>
 * phi2 -> temp
 * phi1 -> phi2
 * temp -> phi1
 * </pre>
 *
 * A temporary variable is required to hold the value of {@code phi2} to access it after
 * {@code phi2} is overwritten.
 * <p>
 * Due to the nature of the problem, the resulting graph has some beneficial properties which make
 * solving this problem easier:
 * <ul>
 * <li>A node has at most one incoming edge because only a merge's phi nodes have incoming edges and
 * there every phi is unique.</li>
 * <li>Each component in the graph has at least one node from which all nodes in the component are
 * reachable (a root node).</li>
 * <li>A cycle has no incoming edges from nodes not in the cycle. This would require multiple
 * incoming edges.</li>
 * <li>Each component has at most one cycle and that cycle is the root of the component (all nodes
 * are reachable from any node in the cycle).</li>
 * </ul>
 * A cycle can be broken up by finding any back edge {@code (u, v)} and replacing it with
 * {@code (temp(u), v)} where {@code temp(u)} is a new temporary node for {@code u} indicating that
 * this move must use the temporary variable.
 * <p>
 * Due to there being only a single cycle in a component, at most one temporary variable is required
 * to schedule a cycle in a component. And since the moves in each component are independent of the
 * moves of other components, components can be scheduled separately and the temporary variable
 * reused.
 * <p>
 * The complete algorithm is described in {@link #scheduleMoves()}.
 *
 * @param <S> Type of source values
 * @param <T> Type of targets (must extend {@code S} as they are possibly used as sources).
 */
public class MoveResolver<S, T extends S> {

    protected final Graph graph;
    protected final Set<T> targets;
    protected final Map<Integer, T> idToTarget;
    protected final Map<Integer, S> idToSource;
    protected final Map<S, Integer> valueIds;

    public MoveResolver(Collection<T> moveTargets) {
        int numTargets = moveTargets.size();
        // There are at most 2 * numTargets node, but it may be less
        int numNodes = 2 * numTargets;
        this.graph = new Graph(numTargets, numNodes);
        this.targets = new EconomicHashSet<>(moveTargets.size());
        this.idToTarget = new EconomicHashMap<>(numTargets);
        this.idToSource = new EconomicHashMap<>(numNodes);
        this.valueIds = new EconomicHashMap<>(numNodes);

        for (T target : moveTargets) {
            assert !targets.contains(target) : "Target can only be added once";
            targets.add(target);
        }
    }

    protected boolean isTarget(S source) {
        return targets.contains(source);
    }

    /**
     * Request a move from source to target.
     */
    public void addMove(S source, T target) {
        assert isTarget(target) : "Target was not provided in constructor";

        int sourceId = getId(source);
        int targetId = getId(target);

        graph.addMove(sourceId, targetId);
    }

    protected int getId(S value) {
        if (valueIds.containsKey(value)) {
            return valueIds.get(value);
        }

        int id;
        if (isTarget(value)) {
            id = graph.newTargetNode();
            /*
             * An unchecked cast is necessary here because the target value may not be passed using
             * the T parameter type.
             */
            @SuppressWarnings("unchecked")
            T targetValue = (T) value;
            idToTarget.put(id, targetValue);
        } else {
            id = graph.newSourceNode();
        }

        assert !idToSource.containsKey(id);
        valueIds.put(value, id);
        idToSource.put(id, value);

        return id;
    }

    /**
     * Create a schedule of concrete moves required to perform the requested simultaneous moves.
     * <p>
     * This happens in three steps:
     * <ol>
     * <li>The graph is cleaned up so that no nodes that move to themselves remain.</li>
     * <li>All cycles in the graph are identified (see {@link #findCycles()}) and then broken up by
     * introducing temporary nodes (see {@link Graph#breakMove(int, int)})</li>
     * <li>The moves for each component in the graph are scheduled according to reverse topological
     * order (see {@link #scheduleComponent(Schedule, int)})</li>
     * </ol>
     */
    public Schedule scheduleMoves() {
        /*
         * At this point, all target nodes should have at least one adjacent edge.
         *
         * It may still contain nodes that have itself as an incoming move, those "moves" will be
         * removed since they don't require a move in the final schedule.
         */
        for (int node : graph.getNodes()) {
            if (graph.isTarget(node)) {
                assert !graph.isLeaf(node) || graph.hasIncoming(node);

                // Remove a move to itself
                if (graph.getIncoming(node) == node) {
                    graph.removeMove(node, node);

                    // If this makes the node isolated (no edges), remove the node
                    if (graph.isLeaf(node)) {
                        graph.removeTarget(node);
                    }
                }
            }
        }

        /*
         * Break all cycles in the graph by replacing the source of each back edge with a temporary
         * node.
         */
        for (var backEdge : findCycles()) {
            graph.breakMove(backEdge.getLeft(), backEdge.getRight());
        }

        /*
         * Now that the graph is acyclic, each component in the graph can be scheduled separately.
         *
         * Each component is a tree with a single root node with no incoming moves and one or more
         * outgoing moves.
         */
        List<Integer> roots = new ArrayList<>();
        for (var node : graph.getNodes()) {
            if (!graph.isLeaf(node) && !graph.hasIncoming(node)) {
                roots.add(node);
            }
        }

        Schedule schedule = new Schedule();
        roots.forEach(root -> scheduleComponent(schedule, root));

        // At this point, all targets should have been removed
        assert IntStream.of(graph.getNodes()).noneMatch(graph::hasIncoming) : "The graph still has moves";
        assert IntStream.of(graph.getNodes()).allMatch(graph::isLeaf) : "The graph still has moves";
        return schedule;
    }

    /**
     * Colors for three-coloring nodes in a graph.
     */
    enum Color {
        /**
         * The node has never been visited.
         */
        White,
        /**
         * The node's successors are currently being visited.
         */
        Gray,
        /**
         * The node has been completely visited.
         */
        Black
    }

    /**
     * Finds all cycles in the graph.
     * <p>
     * Since cycles cannot overlap in this graph (one cycle per component), any edge in the cycle
     * (called a back edge here) can identify the cycle. This method finds all such back edges.
     * <p>
     * This is done using a depth-first-search (DFS) with three colors (see {@link Color}).
     * Initially, all nodes are white, once a node is visited, it is colored gray and finally
     * colored black once all nodes reachable from it have been visited.
     * <p>
     * This means that the node currently being visited is reachable from all gray nodes. From this
     * follows that any outgoing edge to a gray node is a back edge.
     *
     * @return A list of back edges, one per cycle.
     */
    private List<Pair<Integer, Integer>> findCycles() {
        List<Pair<Integer, Integer>> cycles = new ArrayList<>();

        /*
         * Maps each node in the graph to a color.
         */
        Map<Integer, Color> colors = new EconomicHashMap<>();
        int[] nodes = graph.getNodes();

        for (int node : nodes) {
            colors.put(node, Color.White);
        }

        for (var node : nodes) {
            if (colors.get(node) == Color.White) {
                Pair<Integer, Integer> backEdge = findSingleCycle(node, colors);
                if (backEdge != null) {
                    cycles.add(backEdge);
                }
            }
        }

        return cycles;
    }

    /**
     * Tries to find a cycle that is reachable from the given start node.
     * <p>
     * See also {@link #findCycles()}.
     *
     * @param startNode The node from which to start searching.
     * @param colors Current colors of all nodes.
     * @return A back edge of the cycle or {@code null} if none was found.
     */
    private Pair<Integer, Integer> findSingleCycle(int startNode, Map<Integer, Color> colors) {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(startNode);

        // Stores a back edge found in the component (if any)
        Pair<Integer, Integer> backEdge = null;

        while (!stack.isEmpty()) {
            int current = stack.pop();

            assert colors.get(current) != Color.Black : "Must not be black " + current;

            /*
             * The node appears on the stack for the second time, this means all nodes reachable
             * from it have been visited.
             */
            if (colors.get(current) == Color.Gray) {
                colors.put(current, Color.Black);
                continue;
            }

            /*
             * The value is pushed onto the stack once more, but now it is colored gray. The next
             * time the value is popped from the stack, it is colored black and the visit of that
             * node and all nodes reachable from it has concluded.
             */
            colors.put(current, Color.Gray);
            stack.push(current);

            for (int target : graph.getTargets(current)) {
                Color color = colors.get(target);

                if (color == Color.White) {
                    stack.push(target);
                } else if (color == Color.Gray) {
                    /*
                     * Found a gray node, this outgoing edge is a back edge.
                     *
                     * If no other back edge has been found, set this as the back edge.
                     */
                    if (backEdge == null) {
                        backEdge = Pair.create(current, target);
                    }
                }
            }
        }

        return backEdge;
    }

    /**
     * Schedules all moves in the component rooted in the given root node in the given schedule.
     * <p>
     * For this, it schedules the moves to targets in reverse topological order of the nodes in the
     * component and removes the moves from the graph.
     * <p>
     * Due to the reverse topological order a node's outgoing moves are only scheduled once the
     * outgoing moves of all targets have been scheduled.
     * <p>
     * If there is a {@link Graph.TempNode} in the component, it is the root node (because it can't
     * have an incoming edge). Right before the original value of the temporary node is overwritten,
     * a move into the temporary variable is scheduled to preserve it.
     */
    private void scheduleComponent(Schedule schedule, int rootNode) {
        Iterable<Integer> reverseTopologicalOrder = reverseTopologicalOrder(rootNode);

        // If there is a TempNode, it is the root node.
        Integer tempNode = graph.isTemp(rootNode) ? rootNode : null;

        // Whether we already moved a value into the temporary variable for this component.
        boolean wasTempMoveScheduled = false;

        for (int leafNode : reverseTopologicalOrder) {
            assert graph.isLeaf(leafNode);

            if (leafNode == rootNode) {
                // The root node is the last node in the reverse topological sort.
                break;
            }

            // Schedule move to this target
            T target = idToTarget.get(leafNode);

            int source = graph.getIncoming(leafNode);

            if (graph.isTemp(source)) {
                assert tempNode != null : "Move from temporary variable, but no temporary node exists";
                assert tempNode == source : "Found a second TempNode";
                assert wasTempMoveScheduled : "Move from temporary variable, but the move to the temporary variable was not yet scheduled";
                int originalSource = graph.getOriginalTarget(source);
                schedule.scheduleMoveFromTemporary(idToSource.get(originalSource), target);
            } else {
                /*
                 * Right before overwriting the value referenced by the tempNode for the first time,
                 * store the original value into the temporary variable.
                 */
                if (!wasTempMoveScheduled && tempNode != null && graph.getOriginalTarget(tempNode) == leafNode) {
                    schedule.scheduleMoveToTemporary(target);
                    wasTempMoveScheduled = true;
                }
                schedule.scheduleMove(idToSource.get(source), target);
            }

            graph.removeMove(source, leafNode);

            /*
             * Now the target has neither incoming nor outgoing moves and can be removed.
             */
            graph.removeTarget(leafNode);
        }
    }

    /**
     * @return The reverse topological order of nodes in the component reachable from the root node.
     */
    private Iterable<Integer> reverseTopologicalOrder(int rootNode) {
        assert !graph.hasIncoming(rootNode) : "The node is not a root";
        assert !graph.isLeaf(rootNode) : "The root node is part of a component without edges";

        Deque<Integer> workList = new ArrayDeque<>();
        workList.push(rootNode);

        Deque<Integer> reverseTopologicalOrder = new ArrayDeque<>();

        // Run DFS to find all nodes in the component.
        while (!workList.isEmpty()) {
            int current = workList.pop();
            reverseTopologicalOrder.addFirst(current);

            assert !graph.isTemp(current) || current == rootNode : "The TempNode is not the root.";

            for (int target : graph.getTargets(current)) {
                assert !reverseTopologicalOrder.contains(target) : "The graph still has cycles";
                workList.push(target);
            }
        }

        return reverseTopologicalOrder;
    }

    /**
     * Directed graph representation for a set of moves as described in {@link MoveResolver}.
     * <p>
     * Each edge {@code (u, v)} in the graph represents a move from {@code u} to {@code v}.
     * <p>
     * Nodes are represented by integers and clients can create new nodes by calling
     * {@link #newTargetNode()} for nodes that are a target of a move and {@link #newSourceNode()}
     * for nodes that are only inputs for moves.
     */
    protected static class Graph {
        /**
         * Nodes in the graph.
         * <p>
         * {@link Node#id} is the index into this list, node removal is signaled by setting its
         * entry to {@code null}.
         */
        private final List<Node> nodes;
        /**
         * The value at {@link Node#id} contains the node's outgoing edges.
         */
        private final List<SortedSet<Integer>> moves;
        /**
         * Inverse of {@link #moves}. The value at {@link Node#id} contains the node's incoming edge
         * (any target can have at most one incoming edge).
         */
        private final List<Integer> incomingMoves;
        private final EconomicMap<Integer, Integer> targetToTemp;

        /**
         * Create a graph with an estimated number of nodes to set initial capacities of data
         * structures.
         *
         * @param numTargets The expected number of targets in the graph.
         * @param numNodes The expected number of nodes in the graph
         */
        public Graph(int numTargets, int numNodes) {
            this.nodes = new ArrayList<>(numNodes);
            this.moves = new ArrayList<>(numNodes);
            this.incomingMoves = new ArrayList<>(numNodes);

            this.targetToTemp = EconomicMap.create(numTargets);
        }

        /**
         * Create a new move target node and return its id.
         */
        public int newTargetNode() {
            return addNode(id -> new Node(Type.Target, id));
        }

        /**
         * Create a new move source node and return its id.
         * <p>
         * Only use this if the node is not also a target.
         */
        public int newSourceNode() {
            return addNode(id -> new Node(Type.PureSource, id));
        }

        /**
         * Adds a move from the given source to target.
         */
        public void addMove(int source, int target) {
            assert !isTemp(target);
            assert isTarget(target);
            doGetTargets(source).add(target);
            assert !hasIncoming(target);
            incomingMoves.set(target, source);
        }

        /**
         * Removes the given target node without edges from the graph.
         */
        public void removeTarget(int target) {
            /*
             * A target can only be removed if it has no edges.
             */
            assert isLeaf(target) : "Removed target must have no outgoing moves";
            assert isTarget(target);
            assert !hasIncoming(target) : "Removed target must have no incoming move";
            moves.set(target, null);
            nodes.set(target, null);
        }

        /**
         * Removes the edge from source to target.
         */
        public void removeMove(int source, int target) {
            assert isTarget(target);
            doGetTargets(source).remove(target);
            assert hasIncoming(target);
            incomingMoves.set(target, null);
        }

        /**
         * Adds a temporary node for the source node and replaces the edge from source to target
         * with an edge from the new temporary node to the target.
         */
        public void breakMove(int source, int target) {
            int tempNode = getTempForTarget(source);
            removeMove(source, target);
            addMove(tempNode, target);
        }

        /**
         * Returns a copy of all node ids in this graph.
         */
        public int[] getNodes() {
            return IntStream.range(0, nodes.size()).filter(node -> nodes.get(node) != null).toArray();
        }

        /**
         * Allocates a new node in the graph.
         *
         * @param constructor function to create a new node given its id.
         * @return The node id.
         */
        private int addNode(Function<Integer, Node> constructor) {
            // The node id is the current number of nodes (the next index in the nodes list).
            int numNodes = nodes.size();
            assert numNodes == moves.size() : Assertions.errorMessage(numNodes, moves.size(), moves);
            assert numNodes == incomingMoves.size() : Assertions.errorMessage(numNodes, incomingMoves.size(), incomingMoves);

            Node node = constructor.apply(numNodes);
            nodes.add(node);
            moves.add(new TreeSet<>());
            incomingMoves.add(null);
            return numNodes;
        }

        private Node getNode(int id) {
            Node node = nodes.get(id);
            assert node != null;
            return node;
        }

        public boolean isTarget(int node) {
            return getNode(node).isTarget();
        }

        public boolean isTemp(int node) {
            return getNode(node).isTemp();
        }

        /**
         * Creates or reuses an existing {@link TempNode} for the given target node.
         *
         * @return The id of the temporary node.
         */
        private int getTempForTarget(int target) {
            assert isTarget(target);
            if (targetToTemp.containsKey(target)) {
                return targetToTemp.get(target);
            }

            int id = addNode(newId -> new TempNode(newId, target));
            targetToTemp.put(target, id);
            return id;
        }

        /**
         * Gets the original target node referenced by the given temporary node.
         *
         * @param tempNode Node id of the temporary node.
         */
        public int getOriginalTarget(int tempNode) {
            assert isTemp(tempNode);
            return ((TempNode) getNode(tempNode)).originalNode;
        }

        private SortedSet<Integer> doGetTargets(int node) {
            SortedSet<Integer> targets = moves.get(node);
            assert targets != null;
            return targets;
        }

        /**
         * Returns an unmodifiable view onto the set of move targets for the given node.
         */
        public SortedSet<Integer> getTargets(int node) {
            return Collections.unmodifiableSortedSet(doGetTargets(node));
        }

        /**
         * @return true iff the node has no outgoing edges.
         */
        public boolean isLeaf(int node) {
            return doGetTargets(node).isEmpty();
        }

        /**
         * @return The node id of the source of the incoming edge of the target node or null if
         *         there is no incoming edge.
         */
        public Integer getIncoming(int target) {
            assert isTarget(target);
            return incomingMoves.get(target);
        }

        /**
         * Whether the given node has an incoming edge.
         */
        public boolean hasIncoming(int node) {
            return incomingMoves.get(node) != null;
        }

        enum Type {
            /**
             * A node that exclusively a source value and not also a target.
             */
            PureSource,
            /**
             * A node that can be the target of a move, but may also be the source of one.
             */
            Target,
            /**
             * A node that is the source of a move, wrapping a node of type {@link Type#Target}.
             */
            Temp
        }

        static class Node {
            private final Type type;
            protected final int id;

            Node(Type type, int id) {
                this.type = type;
                this.id = id;
            }

            public boolean isTarget() {
                return type == Type.Target;
            }

            public boolean isTemp() {
                return type == Type.Temp;
            }

            @Override
            public String toString() {
                return "Node{" + id + ", " + type.name() + '}';
            }
        }

        static class TempNode extends Node {

            public final int originalNode;

            TempNode(int id, int originalNode) {
                super(Type.Temp, id);
                this.originalNode = originalNode;
            }
        }
    }

    /**
     * A schedule of consecutive moves with the possibility of requiring a single temporary
     * variable.
     */
    public class Schedule {
        /**
         * The moves to be executed. See also {@link Move}.
         */
        public final List<Move> moves = new ArrayList<>();

        private boolean needsTemporary = false;

        /**
         * Move the current value of {@code source} into {@code target}.
         */
        public void scheduleMove(S source, T target) {
            moves.add(new Move(source, target, false));
        }

        /**
         * Move the current value of the temporary variable into {@code target}.
         * <p>
         * The temporary variable is expected to hold the original value of {@code source}.
         */
        public void scheduleMoveFromTemporary(S source, T target) {
            assert needsTemporary;
            moves.add(new Move(source, target, true));
        }

        /**
         * Move the current value of {@code source} into the temporary variable.
         * <p>
         * The current value of {@code source} is expected to be its original value.
         */
        public void scheduleMoveToTemporary(T source) {
            needsTemporary = true;
            moves.add(new Move(source, null, true));
        }

        /**
         * Whether this schedule requires the use of a temporary variable.
         */
        public boolean needsTemporary() {
            return needsTemporary;
        }

        @Override
        public String toString() {
            return "Schedule{" + (needsTemporary ? "with temp, " : "") + moves + "}";
        }

        /**
         * Encodes a move operation from {@link #source} to {@link #target}.
         * <p>
         * If {@link #useTemporary} is false, the current value of {@link #source} is to be written
         * to {@link #target}. Otherwise, the temporary variable (which is supposed to hold the
         * value of {@link #source} before the schedule was executed) instead of {@link #source} is
         * used.
         * <p>
         * If {@link #target} is {@code null}, this is a move from the original value of
         * {@link #source} to the temporary.
         */
        public class Move {
            public final S source;
            public final T target;

            public final boolean useTemporary;

            public Move(S source, T target, boolean useTemporary) {
                this.source = source;
                this.target = target;
                this.useTemporary = useTemporary;
            }

            @Override
            public String toString() {
                return "Move{" + source + " -> " + target + ", " + (useTemporary ? "temporary" : "current") + "}";
            }
        }
    }
}
