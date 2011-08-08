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
package com.oracle.max.graal.compiler.util;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.ir.Phi.PhiType;
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.NodeClass.*;
import com.oracle.max.graal.graph.collections.*;
import com.oracle.max.graal.graph.collections.NodeWorkList.InfiniteWorkException;

public class GraphUtil {

    public static interface ColoringLambda<T> {
        T color(Iterable<T> incomming, Merge merge);
        T danglingColor(Iterable<T> incomming, Merge merge);
    }

    /**
     * colors down, applying the lambda at merge points, starting at the pre-colored points.
     */
    public static <T> void colorCFGDown(NodeMap<T> colors, ColoringLambda<T> lambda) {
        Set<Merge> delayed = new HashSet<Merge>();
        Set<Node> currentPoints = new HashSet<Node>();
        Set<Node> otherPoints = new HashSet<Node>();
        Set<Merge> otherMerges = new HashSet<Merge>();
        for (Entry<Node, T> entry : colors.entries()) {
            currentPoints.add(entry.getKey());
        }
        ArrayList<T> incomming = new ArrayList<T>(2);
        while (!currentPoints.isEmpty()) {
            for (Node node : currentPoints) {
                otherMerges.addAll(colorCFGDownToMerge(node, colors.get(node), colors));
            }
            for (Merge merge : otherMerges) {
                incomming.clear();
                for (EndNode end : merge.cfgPredecessors()) {
                    incomming.add(colors.get(end));
                }
                T color = lambda.color(incomming, merge);
                if (color != null) {
                    colors.set(merge, color);
                    colors.set(merge.next(), color);
                    otherPoints.add(merge.next());
                    delayed.remove(merge);
                } else {
                    delayed.add(merge);
                }
            }
            Set<Node> tmp = currentPoints;
            currentPoints = otherPoints;
            otherPoints = tmp;
            otherPoints.clear();
            otherMerges.clear();
        }
        for (Merge merge : delayed) {
            T color = lambda.danglingColor(incomming, merge);
            if (color != null) {
                colors.set(merge, color);
            }
        }
    }

    private static <T> Collection<Merge> colorCFGDownToMerge(Node from, T color, NodeMap<T> colors) {
        NodeFlood work = from.graph().createNodeFlood();
        Collection<Merge> merges = new LinkedList<Merge>();
        work.add(from);
        for (Node node : work) {
            Node current = node;
            while (current != null) {
                if (current instanceof Merge) {
                    merges.add((Merge) current);
                    break;
                }
                colors.set(current, color);
                if (current instanceof FixedNodeWithNext && !(current instanceof AbstractVectorNode) && !(current instanceof Invoke && ((Invoke) current).exceptionEdge() != null)) {
                    current = ((FixedNodeWithNext) current).next();
                } else if (current instanceof EndNode) {
                    current = ((EndNode) current).merge();
                } else {
                    if (current instanceof ControlSplit) {
                        for (Node sux : current.cfgSuccessors()) {
                            work.add(sux);
                        }
                    } else if (current instanceof Invoke && ((Invoke) current).exceptionEdge() != null) {
                        Invoke invoke = (Invoke) current;
                        work.add(invoke.next());
                        work.add(invoke.exceptionEdge());
                    } else if (current instanceof AbstractVectorNode) {
                        for (Node usage : current.usages()) {
                            work.add(usage);
                        }
                        work.add(((AbstractVectorNode) current).next());
                    }
                    current = null;
                }
            }
        }
        return merges;
    }

    public static interface ColorSplitingLambda<T> {
        void fixSplit(Node oldNode, Node newNode, T color);
        void fixNode(Node node, T color);
        Value fixPhiInput(Value input, T color);
        boolean explore(Node n);
        List<T> parentColors(T color);
        Merge merge(T color);
    }

    // TODO (gd) rework that code around Phi handling : too complicated
    public static <T> void splitFromColoring(NodeMap<T> coloring, ColorSplitingLambda<T> lambda) {
        Map<Node, T> internalColoring = new HashMap<Node, T>();
        NodeWorkList work = coloring.graph().createNodeWorkList();
        for (Entry<Node, T> entry : coloring.entries()) {
            T color = entry.getValue();
            Node node = entry.getKey();
            work.add(node);
            internalColoring.put(node, color);
        }
        Set<T> colors = new HashSet<T>();
        try {
            for (Node node : work) {
                if (node instanceof Phi) {
                    Phi phi = (Phi) node;
                    Merge merge = phi.merge();
                    for (int i = 0; i < phi.valueCount(); i++) {
                        Value v = phi.valueAt(i);
                        if (v != null) {
                            T color = internalColoring.get(merge.phiPredecessorAt(i));
                            if (color != null) {
                                Value replace = lambda.fixPhiInput(v, color);
                                if (replace != v) {
                                    phi.setValueAt(i, replace);
                                } else {
                                    if (lambda.explore(v) && coloring.get(v) == null && !work.isNew(v)) {
                                        work.add(v);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    boolean delay = false;
                    colors.clear();
                    T originalColoringColor = coloring.get(node);
                    if (originalColoringColor == null && internalColoring.get(node) != null) {
                        continue;
                    }
                    if (originalColoringColor == null) {
                        for (Node usage : node.dataUsages()) {
                            if (usage instanceof Phi) {
                                Phi phi = (Phi) usage;
                                Merge merge = phi.merge();
                                for (int i = 0; i < phi.valueCount(); i++) {
                                    Value v = phi.valueAt(i);
                                    if (v == node) {
                                        T color = internalColoring.get(merge.phiPredecessorAt(i));
                                        if (color != null) {
                                            colors.add(color);
                                        }
                                    }
                                }
                            } else {
                                T color = internalColoring.get(usage);
                                if (color == null) {
                                    if (lambda.explore(usage)) {
                                        delay = true;
                                        break;
                                    }
                                } else {
                                    colors.add(color);
                                }
                            }
                        }
                        if (delay) {
                            work.addAgain(node);
                            continue;
                        }
                    } else {
                        colors.add(originalColoringColor);
                    }
                    if (colors.size() == 1) {
                        T color = colors.iterator().next();
                        internalColoring.put(node, color);
                        lambda.fixNode(node, color);
                    } else {
                        Map<T, Node> newNodes = new HashMap<T, Node>();
                        Queue<T> colorQueue = new LinkedList<T>(colors);
                        while (!colorQueue.isEmpty()) {
                            T color = colorQueue.poll();
                            List<T> parentColors = lambda.parentColors(color);
                            Node newNode;
                            if (parentColors.size() > 1 && !(node instanceof FrameState) && colors.containsAll(parentColors)) {
                                boolean ready = true;
                                for (T parentColor : parentColors) {
                                    if (newNodes.get(parentColor) == null) {
                                        ready = false;
                                        break;
                                    }
                                }
                                if (!ready) {
                                    colorQueue.offer(color);
                                    continue;
                                }
                                Phi phi = new Phi(((Value) node).kind, lambda.merge(color), PhiType.Value, node.graph());
                                for (T parentColor : parentColors) {
                                    Node input = newNodes.get(parentColor);
                                    phi.addInput((Value) input);
                                }
                                newNode = phi;
                            } else {
                                newNode = node.clone(node.graph());
                                for (NodeClassIterator iter = node.inputs().iterator(); iter.hasNext();) {
                                    Position pos = iter.nextPosition();
                                    newNode.set(pos, node.get(pos));
                                }
                                for (NodeClassIterator iter = node.successors().iterator(); iter.hasNext();) {
                                    Position pos = iter.nextPosition();
                                    newNode.set(pos, node.get(pos));
                                }
                                internalColoring.put(newNode, color);
                                lambda.fixSplit(node, newNode, color);
                            }
                            newNodes.put(color, newNode);
                            LinkedList<Node> dataUsages = new LinkedList<Node>();
                            for (Node usage : node.dataUsages()) {
                                dataUsages.add(usage);
                            }
                            for (Node usage : dataUsages) {
                                if (usage instanceof Phi) {
                                    Phi phi = (Phi) usage;
                                    Merge merge = phi.merge();
                                    for (int i = 0; i < phi.valueCount(); i++) {
                                        Value v = phi.valueAt(i);
                                        if (v == node) {
                                            T uColor = internalColoring.get(merge.endAt(i));
                                            if (uColor == color) {
                                                phi.setValueAt(i, (Value) newNode);
                                            }
                                        }
                                    }
                                } else {
                                    T uColor = internalColoring.get(usage);
                                    if (uColor == color) {
                                        usage.inputs().replace(node, newNode);
                                    }
                                }
                            }
                        }
                        lambda.fixNode(node, null /*white*/);
                    }
                    if (node instanceof StateSplit) {
                        FrameState stateAfter = ((StateSplit) node).stateAfter();
                        if (stateAfter != null && lambda.explore(stateAfter) && !work.isNew(stateAfter)) {
                            if (!(node instanceof Merge && coloring.get(((Merge) node).next()) == null)) { // not dangling colored merge
                                work.add(stateAfter);
                            }
                        }
                    }

                    if (node instanceof Merge) {
                        for (Node usage : node.usages()) {
                            if (!work.isNew(usage)) {
                                work.add(usage);
                            }
                        }
                    }

                    if (node instanceof LoopEnd) {
                        work.add(((LoopEnd) node).loopBegin());
                    }

                    for (Node input : node.dataInputs()) {
                        if (lambda.explore(input) && coloring.get(input) == null && !work.isNew(input)) {
                            work.add(input);
                        }
                    }
                }
            }
        } catch (InfiniteWorkException re) {
            System.out.println("Infinite work, current queue :");
            for (Node n : work) {
                System.out.println(" - " + n);
            }
            GraalCompilation compilation = GraalCompilation.compilation();
            if (compilation.compiler.isObserved()) {
                NodeMap<T> debugColoring = coloring.graph().createNodeMap();
                for (Entry<Node, T> entry : internalColoring.entrySet()) {
                    debugColoring.set(entry.getKey(), entry.getValue());
                }
                Map<String, Object> debug = new HashMap<String, Object>();
                debug.put("split", debugColoring);
                compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "RuntimeException in split", coloring.graph(), true, false, true, debug));
            }
            throw re;
        }
        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            NodeMap<T> debugColoring = coloring.graph().createNodeMap();
            for (Entry<Node, T> entry : internalColoring.entrySet()) {
                debugColoring.set(entry.getKey(), entry.getValue());
            }
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("split", debugColoring);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Split end!!", coloring.graph(), true, false, debug));
        }
    }
}
