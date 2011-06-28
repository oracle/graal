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

import com.oracle.max.graal.compiler.ir.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;

public class GraphUtil {

    public static interface ColoringLambda<T> {
        T color(Iterable<T> incomming, Merge merge);
    }

    /**
     * colors down, applying the lambda at merge points, starting at the pre-colored points.
     */
    public static <T> void colorCFGDown(NodeMap<T> colors, ColoringLambda<T> lambda) {
        List<Node> startingPoints = new LinkedList<Node>();
        for (Entry<Node, T> entry : colors.entries()) {
            startingPoints.add(entry.getKey());
        }
        NodeWorkList work = colors.graph().createNodeWorkList();
        for (Node startingPoint : startingPoints) {
            if (startingPoint instanceof Merge) {
                work.addAll(colorCFGDownToMerge(((Merge) startingPoint).next(), colors.get(startingPoint), colors));
            } else {
                work.addAll(colorCFGDownToMerge(startingPoint, colors.get(startingPoint), colors));
            }
        }
        for (Node n : work) {
            //System.out.println("Color : work on " + n);
            Merge merge = (Merge) n;
            ArrayList<T> incomming = new ArrayList<T>(2);
            for (EndNode end : merge.cfgPredecessors()) {
                incomming.add(colors.get(end));
            }
            T color = lambda.color(incomming, merge);
            if (color != null) {
                colors.set(merge, color);
                work.addAll(colorCFGDownToMerge(merge.next(), color, colors));
            } else {
                work.addAgain(merge);
            }
        }
    }

    private static <T> Collection<Merge> colorCFGDownToMerge(Node from, T color, NodeMap<T> colors) {
        //System.out.println("colorCFGDownToMerge(" + from + ", " + color + ", colors)");
        NodeFlood work = from.graph().createNodeFlood();
        Collection<Merge> merges = new LinkedList<Merge>();
        work.add(from);
        for (Node node : work) {
            //System.out.println("colorToMerge : work on " + node);
            Node current = node;
            while (current != null) {
                //System.out.println("colorToMerge : current " + current);
                if (current instanceof Merge) {
                    merges.add((Merge) current);
                    break;
                }
                colors.set(current, color);
                if (current instanceof FixedNodeWithNext) {
                    current = ((FixedNodeWithNext) current).next();
                } else if (current instanceof EndNode) {
                    current = ((EndNode) current).merge();
                } else {
                    if (current instanceof ControlSplit) {
                        for (Node sux : current.cfgSuccessors()) {
                            work.add(sux);
                        }
                    }
                    current = null;
                }
            }
        }
        //System.out.println("return " + merges);
        return merges;
    }

    public static interface ColorSplitingLambda<T> {
        void fixSplit(Node oldNode, Node newNode, T color);
        void fixNode(Node node, T color);
        boolean explore(Node n);
    }

    // TODO (gd) handle FrameSate inputs/usages properly
    public static <T> void splitFromColoring(NodeMap<T> coloring, ColorSplitingLambda<T> lambda) {
        Map<Node, T> internalColoring = new HashMap<Node, T>();
        Queue<Node> work = new LinkedList<Node>();
        for (Entry<Node, T> entry : coloring.entries()) {
            T color = entry.getValue();
            Node node = entry.getKey();
            work.offer(node);
            internalColoring.put(node, color);
        }
        Set<T> colors = new HashSet<T>();
        while (!work.isEmpty()) {
            Node node = work.poll();
            //System.out.println("Split : work on " + node);
            boolean delay = false;
            colors.clear();
            T originalColoringColor = coloring.get(node);
            if (originalColoringColor == null && internalColoring.get(node) != null) {
                //System.out.println("Split : ori == null && intern != null -> continue");
                continue;
            }
            if (originalColoringColor == null) {
                for (Node usage : node.dataUsages()) {
                    if (usage instanceof Phi) {
                        Phi phi = (Phi) usage;
                        Merge merge = phi.merge();
                        //System.out.println("Split merge : " + merge + ".endCount = " + merge.endCount() + " phi " + phi + ".valueCount : " + phi.valueCount());
                        for (int i = 0; i < phi.valueCount(); i++) {
                            Value v = phi.valueAt(i);
                            if (v == node) {
                                T color = internalColoring.get(merge.phiPredecessorAt(i));
                                if (color == null) {
                                    //System.out.println("Split : color from " + usage + " is null");
                                    delay = true;
                                    break;
                                }
                                colors.add(color);
                            }
                        }
                    } else {
                        T color = internalColoring.get(usage);
                        if (color == null) {
                            //System.out.println("Split : color from " + usage + " is null");
                            delay = true;
                            break;
                        }
                        colors.add(color);
                    }
                }
                if (delay) {
                    //System.out.println("Split : delay");
                    work.offer(node);
                    continue;
                }
            } else {
                colors.add(originalColoringColor);
            }
            if (colors.size() == 1) {
                //System.out.println("Split : 1 color, coloring, fixing");
                T color = colors.iterator().next();
                internalColoring.put(node, color);
                lambda.fixNode(node, color);
            } else {
                //System.out.println("Split : " + colors.size() + " colors, coloring, spliting, fixing");
                for (T color : colors) {
                    Node newNode = node.copy();
                    for (int i = 0; i < node.inputs().size(); i++) {
                        Node input = node.inputs().get(i);
                        newNode.inputs().setOrExpand(i, input);
                    }
                    for (int i = 0; i < node.successors().size(); i++) {
                        Node input = node.successors().get(i);
                        newNode.successors().setOrExpand(i, input);
                    }
                    internalColoring.put(newNode, color);
                    lambda.fixSplit(node, newNode, color);
                    for (Node usage : node.dataUsages()) {
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
            }

            if (node instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) node).stateAfter();
                if (stateAfter != null && lambda.explore(stateAfter)) {
                    //System.out.println("Split : Add framestate to work");
                    work.offer(stateAfter);
                }
            }

            for (Node input : node.dataInputs()) {
                if (lambda.explore(input) && coloring.get(input) == null) {
                    //System.out.println("Split : Add input " + input + " to work");
                    work.offer(input);
                }
            }
        }
    }
}
