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
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.GraphUtil.ColorSplitingLambda;
import com.oracle.max.graal.compiler.util.GraphUtil.ColoringLambda;
import com.oracle.max.graal.compiler.value.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public class LoopUtil {

    public static class Loop {
        private final LoopBegin loopBegin;
        private final NodeBitMap nodes;
        private Loop parent;
        private final NodeBitMap exits;
        public Loop(LoopBegin loopBegin, NodeBitMap nodes, NodeBitMap exits) {
            this.loopBegin = loopBegin;
            this.nodes = nodes;
            this.exits = exits;
        }

        public LoopBegin loopBegin() {
            return loopBegin;
        }

        public NodeBitMap nodes() {
            return nodes;
        }

        public Loop parent() {
            return parent;
        }

        public NodeBitMap exits() {
            return exits;
        }

        public void setParent(Loop parent) {
            this.parent = parent;
        }
    }

    private static class PeelingResult {
        public final FixedNode begin;
        public final FixedNode end;
        public final NodeMap<Placeholder> exits;
        public final NodeMap<Placeholder> phis;
        public final NodeMap<Node> phiInits;
        public final NodeMap<Node> dataOut;
        public PeelingResult(FixedNode begin, FixedNode end, NodeMap<Placeholder> exits, NodeMap<Placeholder> phis, NodeMap<Node> phiInits, NodeMap<Node> dataOut) {
            this.begin = begin;
            this.end = end;
            this.exits = exits;
            this.phis = phis;
            this.phiInits = phiInits;
            this.dataOut = dataOut;
        }
    }

    public static List<Loop> computeLoops(Graph graph) {
        List<Loop> loops = new LinkedList<LoopUtil.Loop>();
        for (LoopBegin loopBegin : graph.getNodes(LoopBegin.class)) {
            NodeBitMap nodes = computeLoopNodes(loopBegin);
            NodeBitMap exits = graph.createNodeBitMap();
            Loop loop = new Loop(loopBegin, nodes, exits);
            NodeFlood workCFG = graph.createNodeFlood();
            workCFG.add(loopBegin.loopEnd());
            for (Node n : workCFG) {
                if (n == loopBegin) {
                    continue;
                }
                if (IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                    for (Node sux : n.cfgSuccessors()) {
                        if (!nodes.isMarked(sux) && sux instanceof FixedNode) {
                            exits.mark(sux);
                        }
                    }
                }
                for (Node pred : n.cfgPredecessors()) {
                    workCFG.add(pred);
                }
            }
            loops.add(loop);
        }
        for (Loop loop : loops) {
            for (Loop other : loops) {
                if (other != loop && other.nodes().isMarked(loop.loopBegin())) {
                    if (loop.parent() == null || loop.parent().nodes().isMarked(other.loopBegin())) {
                        loop.setParent(other);
                    }
                }
            }
        }
        return loops;
    }

    public static NodeBitMap computeLoopNodes(LoopBegin loopBegin) {
        return computeLoopNodesFrom(loopBegin, loopBegin.loopEnd());
    }
    private static boolean recurse = false;
    public static NodeBitMap computeLoopNodesFrom(LoopBegin loopBegin, FixedNode from) {
        NodeFlood workData1 = loopBegin.graph().createNodeFlood();
        NodeFlood workData2 = loopBegin.graph().createNodeFlood();
        NodeBitMap loopNodes = markUpCFG(loopBegin, from);
        loopNodes.mark(loopBegin);
        for (Node n : loopNodes) {
            workData1.add(n);
            workData2.add(n);
        }
        NodeBitMap inOrAfter = loopBegin.graph().createNodeBitMap();
        for (Node n : workData1) {
            inOrAfter.mark(n);
            for (Node usage : n.dataUsages()) {
                if (usage instanceof Phi) { // filter out data graph cycles
                    Phi phi = (Phi) usage;
                    if (!phi.isDead()) {
                        Merge merge = phi.merge();
                        if (merge instanceof LoopBegin) {
                            LoopBegin phiLoop = (LoopBegin) merge;
                            int backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                            if (phi.valueAt(backIndex) == n) {
                                continue;
                            }
                        }
                    }
                }
                workData1.add(usage);
            }
        }
        NodeBitMap inOrBefore = loopBegin.graph().createNodeBitMap();
        for (Node n : workData2) {
            inOrBefore.mark(n);
            if (n instanceof Phi) {
                Phi phi = (Phi) n;
                if (!phi.isDead()) {
                    int backIndex = -1;
                    Merge merge = phi.merge();
                    if (merge instanceof LoopBegin) {
                        LoopBegin phiLoop = (LoopBegin) merge;
                        backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                    }
                    for (int i = 0; i < phi.valueCount(); i++) {
                        if (i != backIndex) {
                            workData2.add(phi.valueAt(i));
                        }
                    }
                }
            } else {
                for (Node input : n.dataInputs()) {
                    workData2.add(input);
                }
            }
            if (n instanceof Merge) { //add phis & counters
                for (Node usage : n.dataUsages()) {
                    workData2.add(usage);
                }
            }
        }
        if (!recurse) {
            recurse = true;
            GraalCompilation compilation = GraalCompilation.compilation();
            if (compilation.compiler.isObserved()) {
                Map<String, Object> debug = new HashMap<String, Object>();
                debug.put("loopNodes", loopNodes);
                debug.put("inOrAfter", inOrAfter);
                debug.put("inOrBefore", inOrBefore);
                compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Compute loop nodes", loopBegin.graph(), true, false, debug));
            }
            recurse = false;
        }
        inOrAfter.setIntersect(inOrBefore);
        loopNodes.setUnion(inOrAfter);
        return loopNodes;
    }

    private static NodeBitMap markUpCFG(LoopBegin loopBegin, FixedNode from) {
        NodeFlood workCFG = loopBegin.graph().createNodeFlood();
        workCFG.add(from);
        NodeBitMap loopNodes = loopBegin.graph().createNodeBitMap();
        for (Node n : workCFG) {
            if (n == loopBegin) {
                continue;
            }
            loopNodes.mark(n);
            if (n instanceof LoopBegin) {
                workCFG.add(((LoopBegin) n).loopEnd());
            }
            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }
        return loopNodes;
    }

    public static void ifDoWhileTransform(Loop loop, If split) {
        assert loop.nodes().isMarked(split);
        FixedNode noExit = split.trueSuccessor();
        FixedNode exit = split.falseSuccessor();
        if (loop.nodes().isMarked(exit) && !loop.nodes().isMarked(noExit)) {
            FixedNode tmp = noExit;
            noExit = exit;
            exit = tmp;
        }
        assert !loop.nodes().isMarked(exit);
        assert loop.nodes().isMarked(noExit);

        PeelingResult peeling = preparePeeling(loop, split);
        rewirePeeling(peeling, loop, split);
        // TODO (gd) move peeled part to the end, rewire dataOut
    }

    public static void peelLoop(Loop loop) {
        LoopEnd loopEnd = loop.loopBegin().loopEnd();
        PeelingResult peeling = preparePeeling(loop, loopEnd);
        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After peeling preparation", loopEnd.graph(), true, false));
        }
        /*System.out.println("Peeling : ");
        System.out.println(" begin = " + peeling.begin);
        System.out.println(" end = " + peeling.end);
        System.out.println(" Phis :");
        for (Entry<Node, Placeholder> entry : peeling.phis.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println(" Exits :");
        for (Entry<Node, Placeholder> entry : peeling.exits.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println(" PhiInits :");
        for (Entry<Node, Node> entry : peeling.phiInits.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println(" DataOut :");
        for (Entry<Node, Node> entry : peeling.dataOut.entries()) {
            System.out.println("  - " + entry.getKey() + " -> " + entry.getValue());
        }*/
        rewirePeeling(peeling, loop, loopEnd);
        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After rewirePeeling", loopEnd.graph(), true, false));
        }
    }

    private static void rewirePeeling(PeelingResult peeling, Loop loop, FixedNode from) {
        LoopBegin loopBegin = loop.loopBegin();
        Graph graph = loopBegin.graph();
        Node loopPred = loopBegin.singlePredecessor();
        loopPred.successors().replace(loopBegin.forwardEdge(), peeling.begin);
        NodeBitMap loopNodes = loop.nodes();
        Node originalLast = from;
        if (originalLast == loopBegin.loopEnd()) {
            originalLast = loopBegin.loopEnd().singlePredecessor();
        }
        int size = originalLast.successors().size();
        boolean found = false;
        for (int i = 0; i < size; i++) {
            Node sux = originalLast.successors().get(i);
            if (sux == null) {
                continue;
            }
            if (loopNodes.isMarked(sux)) {
                assert !found;
                peeling.end.successors().set(i, loopBegin.forwardEdge());
                found = true;
            }
        }
        assert found;
        int phiInitIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (Entry<Node, Placeholder> entry : peeling.phis.entries()) {
            Phi phi = (Phi) entry.getKey();
            Placeholder p = entry.getValue();
            p.replaceAndDelete(phi.valueAt(phiInitIndex));
        }
        for (Entry<Node, Node> entry : peeling.phiInits.entries()) {
            Phi phi = (Phi) entry.getKey();
            Node newInit = entry.getValue();
            phi.setValueAt(phiInitIndex, (Value) newInit);
        }

        if (from == loopBegin.loopEnd()) {
            for (LoopCounter counter : loopBegin.counters()) {
                counter.setInit(new IntegerAdd(counter.kind, counter.init(), counter.stride(), graph));
            }
        }
        NodeMap<NodeMap<Value>> newExitValues = graph.createNodeMap();
        List<Node> exitPoints = new LinkedList<Node>();
        for (Node exit : loop.exits()) {
            exitPoints.add(exit);
        }
        for (Entry<Node, Placeholder> entry : peeling.exits.entries()) {
            Placeholder original = (Placeholder) entry.getKey();
            Placeholder newExit = entry.getValue();
            FixedNode next = original.next();
            EndNode oEnd = new EndNode(graph);
            EndNode nEnd = new EndNode(graph);
            Merge merge = new Merge(graph);
            merge.setNext(next);
            FrameState newState = newExit.stateAfter();
            merge.addEnd(nEnd);
            merge.setStateAfter(newState);
            //newState.merge(merge, original.stateAfter());
            merge.addEnd(oEnd);
            original.setNext(oEnd);
            newExit.setStateAfter(null);
            newExit.replaceAndDelete(nEnd);

            exitPoints.add(nEnd);
        }

        for (Entry<Node, Placeholder> entry : peeling.exits.entries()) {
            Placeholder original = (Placeholder) entry.getKey();
            EndNode oEnd = (EndNode) original.next();
            Merge merge = oEnd.merge();
            EndNode nEnd = merge.endAt(1 - merge.phiPredecessorIndex(oEnd));
            FrameState newState = merge.stateAfter();
            NodeArray oInputs = original.stateAfter().inputs();
            NodeArray nInputs = newState.inputs();
            int oSize = oInputs.size();
            for (int i = 0; i < oSize; i++) {
                Node newValue = nInputs.get(i);
                Node originalValue = oInputs.get(i);
                if (newValue != originalValue) {
                    NodeMap<Value> phiMap = newExitValues.get(originalValue);
                    if (phiMap == null) {
                        phiMap = graph.createNodeMap();
                        newExitValues.set(originalValue, phiMap);
                    }
                    phiMap.set(original, (Value) originalValue);
                    phiMap.set(nEnd, (Value) newValue);

                    phiMap = newExitValues.get(newValue);
                    if (phiMap == null) {
                        phiMap = graph.createNodeMap();
                        newExitValues.set(newValue, phiMap);
                    }
                    phiMap.set(original, (Value) originalValue);
                    phiMap.set(nEnd, (Value) newValue);
                }
            }
            /*Placeholder original = (Placeholder) entry.getKey();
            Merge merge = ((EndNode) original.next()).merge();
            FrameState newState = merge.stateAfter();
            NodeArray oInputs = original.stateAfter().inputs();
            NodeArray nInputs = newState.inputs();
            int oSize = oInputs.size();
            for (int i = 0; i < oSize; i++) {
                Node newValue = nInputs.get(i);
                Node originalValue = oInputs.get(i);
                if (newValue != originalValue && newValue instanceof Phi) {
                    Phi newPhi = (Phi) newValue;
                    assert newPhi.valueAt(1) == originalValue;
                    NodeMap<Value> phiMap = newExitValues.get(originalValue);
                    if (phiMap == null) {
                        phiMap = graph.createNodeMap();
                        newExitValues.set(originalValue, phiMap);
                    }
                    phiMap.set(merge, newPhi);
                }
            }*/
        }
        for (Entry<Node, NodeMap<Value>> entry : newExitValues.entries()) {
            Value original = (Value) entry.getKey();
            NodeMap<Value> pointToValue = entry.getValue();
            for (Node exit : exitPoints) {
                Node valueAtExit = pointToValue.get(exit);
                if (valueAtExit == null) {
                    pointToValue.set(exit, original);
                }
            }
        }

        replaceValuesAtLoopExits(newExitValues, loop, exitPoints);
    }

    private static void replaceValuesAtLoopExits(final NodeMap<NodeMap<Value>> newExitValues, Loop loop, List<Node> exitPoints) {
        Graph graph = loop.loopBegin().graph();
        final NodeMap<Node> colors = graph.createNodeMap();

        // prepare inital colors
        for (Node exitPoint : exitPoints) {
            colors.set(exitPoint, exitPoint);
        }

        /*System.out.println("newExitValues");
        for (Entry<Node, NodeMap<Value>> entry : newExitValues.entries()) {
            System.out.println(" - " + entry.getKey() + " :");
            for (Entry<Node, Value> entry2 : entry.getValue().entries()) {
                System.out.println("    + " + entry2.getKey() + " -> " + entry2.getValue());
            }
        }*/

        // color
        GraphUtil.colorCFGDown(colors, new ColoringLambda<Node>() {
            @Override
            public Node color(Iterable<Node> incomming, Merge merge) {
                Node color = null;
                for (Node c : incomming) {
                    if (c == null) {
                        return null;
                    }
                    if (color == null) {
                        color = c;
                    } else if (color != c) {
                        return merge;
                    }
                }
                return color;
            }
            @Override
            public Node danglingColor(Iterable<Node> incomming, Merge merge) {
                Node color = null;
                for (Node c : incomming) {
                    if (color == null) {
                        color = c;
                    } else if (color != c) {
                        return merge;
                    }
                }
                assert color != null;
                return color;
            }
        });

        final NodeBitMap inOrBefore = inOrBefore(loop);

        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("loopExits", colors);
            debug.put("inOrBefore", inOrBefore);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After coloring", graph, true, false, debug));
        }

        GraphUtil.splitFromColoring(colors, new ColorSplitingLambda<Node>(){
            @Override
            public void fixSplit(Node oldNode, Node newNode, Node color) {
                this.fixNode(newNode, color);
            }
            private Value getValueAt(Node point, NodeMap<Value> valueMap, CiKind kind) {
                Value value = valueMap.get(point);
                if (value != null) {
                    //System.out.println("getValueAt(" + point + ", valueMap, kind) = " + value);
                    return value;
                }
                Merge merge = (Merge) point;
                ArrayList<Value> values = new ArrayList<Value>(merge.phiPredecessorCount());
                Value v = null;
                boolean createPhi = false;
                for (EndNode end : merge.cfgPredecessors()) {
                    Value valueAt = getValueAt(colors.get(end), valueMap, kind);
                    if (v == null) {
                        v = valueAt;
                    } else if (v != valueAt) {
                        createPhi = true;
                    }
                    values.add(valueAt);
                }
                if (createPhi) {
                    Phi phi = new Phi(kind, merge, merge.graph());
                    valueMap.set(point, phi);
                    for (EndNode end : merge.cfgPredecessors()) {
                        phi.addInput(getValueAt(colors.get(end), valueMap, kind));
                    }
                    //System.out.println("getValueAt(" + point + ", valueMap, kind) = " + phi);
                    return phi;
                } else {
                    assert v != null;
                    valueMap.set(point, v);
                    //System.out.println("getValueAt(" + point + ", valueMap, kind) = " + v);
                    return v;
                }
            }
            @Override
            public boolean explore(Node n) {
                return !inOrBefore.isNew(n) && !inOrBefore.isMarked(n) && !(n instanceof Local); //TODO (gd) hum
            }
            @Override
            public void fixNode(Node node, Node color) {
                //System.out.println("fixNode(" + node + ", " + color + ")");
                for (int i = 0; i < node.inputs().size(); i++) {
                    Node input = node.inputs().get(i);
                    if (input == null || newExitValues.isNew(input)) {
                        continue;
                    }
                    NodeMap<Value> valueMap = newExitValues.get(input);
                    if (valueMap != null) {
                        Value replacement = getValueAt(color, valueMap, ((Value) input).kind);
                        node.inputs().set(i, replacement);
                    }
                }
            }
            @Override
            public Value fixPhiInput(Value input, Node color) {
                if (newExitValues.isNew(input)) {
                    return input;
                }
                NodeMap<Value> valueMap = newExitValues.get(input);
                if (valueMap != null) {
                    return getValueAt(color, valueMap, input.kind);
                }
                return input;
            }});

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After split from colors", graph, true, false));
        }
    }

    private static PeelingResult preparePeeling(Loop loop, FixedNode from) {
        LoopBegin loopBegin = loop.loopBegin();
        Graph graph = loopBegin.graph();
        NodeBitMap marked = computeLoopNodesFrom(loopBegin, from);
        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("marked", marked);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After computeLoopNodesFrom", loopBegin.graph(), true, false, debug));
        }
        if (from == loopBegin.loopEnd()) {
            marked.clear(from);
        }
        marked.clear(loopBegin);
        Map<Node, Node> replacements = new HashMap<Node, Node>();
        NodeMap<Placeholder> phis = graph.createNodeMap();
        NodeMap<Placeholder> exits = graph.createNodeMap();

        for (Node exit : loop.exits()) {
            if (marked.isMarked(exit.singlePredecessor())) {
                marked.mark(((Placeholder) exit).stateAfter());
                Placeholder p = new Placeholder(graph);
                replacements.put(exit, p);
                exits.set(exit, p);
            }
        }

        for (Node n : marked) {
            if (n instanceof StateSplit) {
                FrameState stateAfter = ((StateSplit) n).stateAfter();
                if (stateAfter != null) {
                    marked.mark(stateAfter);
                }
            }
            if (n instanceof Phi && ((Phi) n).merge() == loopBegin) {
                Placeholder p = new Placeholder(graph);
                replacements.put(n, p);
                phis.set(n, p);
                marked.clear(n);
            }
            for (Node input : n.dataInputs()) {
                if (!marked.isMarked(input) && (!(input instanceof Phi) || ((Phi) input).merge() != loopBegin)) {
                    replacements.put(input, input);
                }
            }
        }

        //GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("marked", marked);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Before addDuplicate", loopBegin.graph(), true, false, debug));
        }

        Map<Node, Node> duplicates = graph.addDuplicate(marked, replacements);

        NodeMap<Node> dataOut = graph.createNodeMap();
        for (Node n : marked) {
            for (Node usage : n.dataUsages()) {
                if (!marked.isMarked(usage)
                                && !loop.nodes().isNew(usage) && loop.nodes().isMarked(usage)
                                && !((usage instanceof Phi) || ((Phi) usage).merge() != loopBegin)) {
                    dataOut.set(n, duplicates.get(n));
                    break;
                }
            }
        }
        NodeMap<Node> phiInits = graph.createNodeMap();
        int backIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
        int fowardIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (Phi phi : loopBegin.phis()) {
            Value backValue = phi.valueAt(backIndex);
            if (marked.isMarked(backValue)) {
                phiInits.set(phi, duplicates.get(backValue));
            } else if (backValue instanceof Phi && ((Phi) backValue).merge() == loopBegin) {
                Phi backPhi = (Phi) backValue;
                phiInits.set(phi, backPhi.valueAt(fowardIndex));
            }
        }

        FixedNode newBegin = (FixedNode) duplicates.get(loopBegin.next());
        FixedNode newFrom = (FixedNode) duplicates.get(from == loopBegin.loopEnd() ? from.singlePredecessor() : from);
        return new PeelingResult(newBegin, newFrom, exits, phis, phiInits, dataOut);
    }

    private static NodeBitMap inOrBefore(Loop loop) {
        Graph graph = loop.loopBegin().graph();
        NodeBitMap inOrBefore = graph.createNodeBitMap();
        NodeFlood work = graph.createNodeFlood();
        work.addAll(loop.nodes());
        for (Node n : work) {
            inOrBefore.mark(n);
            for (Node pred : n.predecessors()) {
                work.add(pred);
            }
            for (Node in : n.inputs()) {
                if (in != null) {
                    work.add(in);
                }
            }
        }
        return inOrBefore;
    }
}
