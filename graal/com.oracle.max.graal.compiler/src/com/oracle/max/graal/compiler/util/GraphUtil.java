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
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;

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


        /*List<Node> startingPoints = new LinkedList<Node>();
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
                System.out.println("Can not color " + merge);
                work.addAgain(merge);
            }
        }*/
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
        Value fixPhiInput(Value input, T color);
        boolean explore(Node n);
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
                //System.out.println("Split : work on " + node);
                if (node instanceof Phi) {
                    Phi phi = (Phi) node;
                    Merge merge = phi.merge();
                    for (int i = 0; i < phi.valueCount(); i++) {
                        Value v = phi.valueAt(i);
                        if (v != null) {
                            T color = internalColoring.get(merge.phiPredecessorAt(i));
                            Value replace = lambda.fixPhiInput(v, color);
                            if (replace != v) {
                                phi.setValueAt(i, replace);
                            }
                        }
                    }
                } else {
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
                                        if (color != null) {
                                            colors.add(color);
                                        }
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
                            work.addAgain(node);
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
                    }
                }

                if (node instanceof StateSplit) {
                    FrameState stateAfter = ((StateSplit) node).stateAfter();
                    if (stateAfter != null && lambda.explore(stateAfter) && !work.isNew(stateAfter)) {
                        //System.out.println("Split : Add framestate to work");
                        work.add(stateAfter);
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
                        //System.out.println("Split : Add input " + input + " to work from " + node);
                        work.add(input);
                    }
                }
            }
        } catch (RuntimeException re) {
            re.printStackTrace();
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
        }
    }
}
