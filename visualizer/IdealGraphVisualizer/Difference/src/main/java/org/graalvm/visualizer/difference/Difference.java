/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.difference;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.*;

import java.util.*;

import org.graalvm.visualizer.data.Pair;
import org.graalvm.visualizer.difference.impl.DiffGraph;

import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.Properties;

public class Difference {
    public static final String VALUE_NEW = "new";
    public static final String VALUE_CHANGED = "changed";
    public static final String VALUE_SAME = "same";
    public static final String VALUE_DELETED = "deleted";
    public static final String NEW_PREFIX = "NEW_";
    public static final double LIMIT = 100.0;
    public static final String[] IGNORE_PROPERTIES = new String[]{PROPNAME_IDX, "debug_idx"};

    public static InputGraph createDiffGraph(InputGraph a, InputGraph b) {
        /*
         * Concatenate the two graphs' format strings and arguments,
         * including their dump IDs if they are valid.
         */
        int argsSize = a.getArgs().length + b.getArgs().length;
        int argsAStartIndex = 0;
        int argsBStartIndex = a.getArgs().length;
        StringBuilder format = new StringBuilder();
        if (a.getDumpId() < 0) {
            format.append(a.getFormat());
        } else {
            format.append("%s: ");
            format.append(a.getFormat());
            argsAStartIndex++;
            argsBStartIndex++;
            argsSize++;
        }
        if (b.getDumpId() < 0) {
            format.append(", ");
            format.append(b.getFormat());
        } else {
            format.append(", %s: ");
            format.append(b.getFormat());
            argsBStartIndex++;
            argsSize++;
        }
        Object[] args = new Object[argsSize];
        System.arraycopy(a.getArgs(), 0, args, argsAStartIndex, a.getArgs().length);
        System.arraycopy(b.getArgs(), 0, args, argsBStartIndex, b.getArgs().length);
        if (a.getDumpId() >= 0) {
            args[argsAStartIndex - 1] = a.getDumpId();
        }
        if (b.getDumpId() >= 0) {
            args[argsBStartIndex - 1] = b.getDumpId();
        }
        String id = a.getID().toString() + "/" + b.getID().toString();
        DiffGraph diffGraph = new DiffGraph(id, format.toString(), args, a, b, Difference::completeDiffGraph);
        return associateGroup(a, b, diffGraph);
    }

    private static DiffGraph completeDiffGraph(InputGraph a, InputGraph b, DiffGraph c) {
        if (a.getGroup() == b.getGroup()) {
            return createDiffSameGroup(a, b, c);
        } else {
            return createDiff(a, b, c);
        }
    }

    private static DiffGraph createDiffSameGroup(InputGraph a, InputGraph b, DiffGraph c) {
        Map<Integer, InputNode> keyMapB = new HashMap<>(b.getNodes().size());
        for (InputNode n : b.getNodes()) {
            Integer key = n.getId();
            assert !keyMapB.containsKey(key);
            keyMapB.put(key, n);
        }

        Set<NodePair> pairs = new HashSet<>();

        for (InputNode n : a.getNodes()) {
            Integer key = n.getId();

            if (keyMapB.containsKey(key)) {
                InputNode nB = keyMapB.get(key);
                pairs.add(new NodePair(n, nB));
            }
        }

        return createDiff(a, b, c, pairs);
    }

    private static DiffGraph associateGroup(InputGraph a, InputGraph b, DiffGraph graph) {
        Group g = new Group(null, null);
        g.setMethod(a.getGroup().getMethod());
        if (a.getGroup() == b.getGroup()) {
            g.getProperties().add(a.getGroup().getProperties());
        } else {
            // copy properties that have the same value in both groups
            Properties bps = b.getGroup().getProperties();
            for (Property p : a.getGroup().getProperties()) {
                Object value = p.getValue();
                if (value != null && value.equals(bps.get(p.getName()))) {
                    g.getProperties().setProperty(p.getName(), value);
                }
            }
        }
        g.getProperties().setProperty(PROPNAME_NAME, "Difference");
        g.addElement(graph);
        return graph;
    }

    private static DiffGraph createDiff(InputGraph a, InputGraph b, DiffGraph graph, Set<NodePair> pairs) {
        Map<InputBlock, InputBlock> blocksMap = new HashMap<>();
        for (InputBlock blk : a.getBlocks()) {
            InputBlock diffblk = graph.addBlock(blk.getName());
            blocksMap.put(blk, diffblk);
        }
        for (InputBlock blk : b.getBlocks()) {
            InputBlock diffblk = graph.getBlock(blk.getName());
            if (diffblk == null) {
                diffblk = graph.addBlock(blk.getName());
            }
            blocksMap.put(blk, diffblk);
        }

        // Difference between block edges
        Set<Pair<String, String>> aEdges = new HashSet<>();
        for (InputBlockEdge edge : a.getBlockEdges()) {
            aEdges.add(new Pair<>(edge.getFrom().getName(), edge.getTo().getName()));
        }
        for (InputBlockEdge bEdge : b.getBlockEdges()) {
            InputBlock from = bEdge.getFrom();
            InputBlock to = bEdge.getTo();
            Pair<String, String> pair = new Pair<>(from.getName(), to.getName());
            if (aEdges.contains(pair)) {
                // same
                graph.addBlockEdge(blocksMap.get(from), blocksMap.get(to));
                aEdges.remove(pair);
            } else {
                // added
                InputBlockEdge edge = graph.addBlockEdge(blocksMap.get(from), blocksMap.get(to));
                edge.setState(InputBlockEdge.State.NEW);
            }
        }
        for (Pair<String, String> deleted : aEdges) {
            // removed
            InputBlock from = graph.getBlock(deleted.getLeft());
            InputBlock to = graph.getBlock(deleted.getRight());
            InputBlockEdge edge = graph.addBlockEdge(from, to);
            edge.setState(InputBlockEdge.State.DELETED);
        }

        Set<InputNode> nodesA = new HashSet<>(a.getNodes());
        Set<InputNode> nodesB = new HashSet<>(b.getNodes());

        Map<InputNode, InputNode> inputNodeMap = new HashMap<>(pairs.size());
        for (NodePair p : pairs) {
            InputNode n = p.getLeft();
            assert nodesA.contains(n);
            InputNode nB = p.getRight();
            assert nodesB.contains(nB);

            nodesA.remove(n);
            nodesB.remove(nB);
            InputNode n2 = new InputNode(n);
            inputNodeMap.put(n, n2);
            inputNodeMap.put(nB, n2);
            graph.addNode(n2);
            InputBlock block = blocksMap.get(a.getBlock(n));
            block.addNode(n2.getId());
            markAsChanged(n2, n, nB);
        }

        for (InputNode n : nodesA) {
            InputNode n2 = new InputNode(n);
            graph.addNode(n2);
            InputBlock block = blocksMap.get(a.getBlock(n));
            block.addNode(n2.getId());
            markAsDeleted(n2);
            inputNodeMap.put(n, n2);
        }

        int curIndex = 0;
        for (InputNode n : nodesB) {
            InputNode n2 = new InputNode(n);

            // Find new ID for node of b, does not change the id property
            while (graph.getNode(curIndex) != null) {
                curIndex++;
            }

            n2.setId(curIndex);
            graph.addNode(n2);
            InputBlock block = blocksMap.get(b.getBlock(n));
            block.addNode(n2.getId());
            markAsNew(n2);
            inputNodeMap.put(n, n2);
        }

        Collection<InputEdge> edgesA = a.getEdges();
        Collection<InputEdge> edgesB = b.getEdges();

        Set<InputEdge> newEdges = new HashSet<>();

        for (InputEdge e : edgesA) {
            int from = e.getFrom();
            int to = e.getTo();
            InputNode nodeFrom = inputNodeMap.get(a.getNode(from));
            InputNode nodeTo = inputNodeMap.get(a.getNode(to));
            char fromIndex = e.getFromIndex();
            char toIndex = e.getToIndex();

            if (nodeFrom == null || nodeTo == null) {
                System.out.println("Unexpected edge : " + from + " -> " + to);
            } else {
                InputEdge newEdge = new InputEdge(fromIndex, toIndex, nodeFrom.getId(), nodeTo.getId(), e.getListIndex(), e.getLabel(), e.getType());
                if (!newEdges.contains(newEdge)) {
                    markAsDeleted(newEdge);
                    newEdges.add(newEdge);
                    graph.addEdge(newEdge);
                }
            }
        }

        for (InputEdge e : edgesB) {
            int from = e.getFrom();
            int to = e.getTo();
            InputNode nodeFrom = inputNodeMap.get(b.getNode(from));
            InputNode nodeTo = inputNodeMap.get(b.getNode(to));
            char fromIndex = e.getFromIndex();
            char toIndex = e.getToIndex();

            if (nodeFrom == null || nodeTo == null) {
                System.out.println("Unexpected edge : " + from + " -> " + to);
            } else {
                InputEdge newEdge = new InputEdge(fromIndex, toIndex, nodeFrom.getId(), nodeTo.getId(), e.getListIndex(), e.getLabel(), e.getType());
                if (!newEdges.contains(newEdge)) {
                    markAsNew(newEdge);
                    newEdges.add(newEdge);
                    graph.addEdge(newEdge);
                } else {
                    newEdges.remove(newEdge);
                    graph.removeEdge(newEdge);
                    markAsSame(newEdge);
                    newEdges.add(newEdge);
                    graph.addEdge(newEdge);
                }
            }
        }

        return graph;
    }

    private static class NodePair extends Pair<InputNode, InputNode> {

        public NodePair(InputNode n1, InputNode n2) {
            super(n1, n2);
        }

        public double getValue() {

            double result = 0.0;
            for (Property p : getLeft().getProperties()) {
                double faktor = 1.0;
                for (String forbidden : IGNORE_PROPERTIES) {
                    if (p.getName().equals(forbidden)) {
                        faktor = 0.1;
                        break;
                    }
                }
                result += evaluate(p.getValue(), getRight().getProperties().get(p.getName())) * faktor;
            }

            return result;
        }

        private double evaluate(Object p, Object p2) {
            if ((p2 == null || p == null) && p2 != p) {
                return 1.0;
            }
            String s1 = Objects.toString(p);
            String s2 = Objects.toString(p2);
            if (s1.equals(s2)) {
                return 0.0;
            } else {
                return (double) (Math.abs(s1.length() - s2.length())) / s1.length() + 0.5;
            }
        }
    }

    private static DiffGraph createDiff(InputGraph a, InputGraph b, DiffGraph c) {
        Set<NodePair> pairs = new HashSet<>();
        for (InputNode n : a.getNodes()) {
            String s = n.getProperties().getString(PROPNAME_NAME, "");
            for (InputNode n2 : b.getNodes()) {
                String s2 = n2.getProperties().getString(PROPNAME_NAME, "");

                if (s.equals(s2)) {
                    NodePair p = new NodePair(n, n2);
                    pairs.add(p);
                }
            }
        }

        Set<NodePair> selectedPairs = new HashSet<>();
        while (pairs.size() > 0) {

            double min = Double.MAX_VALUE;
            NodePair minPair = null;
            for (NodePair p : pairs) {
                double cur = p.getValue();
                if (cur < min) {
                    minPair = p;
                    min = cur;
                }
            }

            if (min > LIMIT) {
                break;
            } else {
                selectedPairs.add(minPair);

                Set<NodePair> toRemove = new HashSet<>();
                for (NodePair p : pairs) {
                    if (p.getLeft() == minPair.getLeft() || p.getRight() == minPair.getRight()) {
                        toRemove.add(p);
                    }
                }
                pairs.removeAll(toRemove);
            }
        }

        return createDiff(a, b, c, selectedPairs);
    }

    private static void markAsNew(InputEdge e) {
        e.setState(InputEdge.State.NEW);
    }

    private static void markAsDeleted(InputEdge e) {
        e.setState(InputEdge.State.DELETED);

    }

    private static void markAsSame(InputEdge e) {
        e.setState(InputEdge.State.SAME);
    }

    private static void markAsChanged(InputNode n, InputNode firstNode, InputNode otherNode) {

        boolean difference = false;
        for (Property p : otherNode.getProperties()) {
            Object po = p.getValue();
            if (!Objects.deepEquals(po, firstNode.getProperties().get(p.getName()))) {
                difference = true;
                n.getProperties().setProperty(NEW_PREFIX + p.getName(), po);
            }
        }

        for (Property p : firstNode.getProperties()) {
            Object po = p.getValue();
            if (otherNode.getProperties().get(p.getName()) == null && (po == null ? "" : Objects.toString(po)).length() > 0) {
                difference = true;
                n.getProperties().setProperty(NEW_PREFIX + p.getName(), "");
            }
        }

        if (difference) {
            n.getProperties().setProperty(PROPNAME_STATE, VALUE_CHANGED);
        } else {
            n.getProperties().setProperty(PROPNAME_STATE, VALUE_SAME);
        }
    }

    private static void markAsDeleted(InputNode n) {
        n.getProperties().setProperty(PROPNAME_STATE, VALUE_DELETED);
    }

    private static void markAsNew(InputNode n) {
        n.getProperties().setProperty(PROPNAME_STATE, VALUE_NEW);
    }
}
