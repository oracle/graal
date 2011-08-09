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
import com.oracle.max.graal.compiler.observer.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.compiler.util.GraphUtil.ColorSplitingLambda;
import com.oracle.max.graal.compiler.util.GraphUtil.ColoringLambda;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.max.graal.graph.NodeClass.Position;
import com.oracle.max.graal.graph.collections.*;
import com.oracle.max.graal.nodes.base.*;
import com.oracle.max.graal.nodes.base.PhiNode.*;
import com.oracle.max.graal.nodes.extended.*;
import com.oracle.max.graal.nodes.loop.*;
import com.sun.cri.ci.*;

public class LoopUtil {

    public static class Loop {
        private final LoopBeginNode loopBegin;
        private NodeBitMap cfgNodes;
        private Loop parent;
        private NodeBitMap exits;
        private NodeBitMap inOrBefore;
        private NodeBitMap inOrAfter;
        private NodeBitMap nodes;
        public Loop(LoopBeginNode loopBegin, NodeBitMap nodes, NodeBitMap exits) {
            this.loopBegin = loopBegin;
            this.cfgNodes = nodes;
            this.exits = exits;
        }

        public LoopBeginNode loopBegin() {
            return loopBegin;
        }

        public NodeBitMap cfgNodes() {
            return cfgNodes;
        }

        public NodeBitMap nodes() {
            if (nodes == null) {
                nodes = loopBegin().graph().createNodeBitMap();
                nodes.setUnion(inOrAfter());
                nodes.setIntersect(inOrBefore());
            }
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

        public boolean isChild(Loop loop) {
            return loop.parent != null && (loop.parent == this || loop.parent.isChild(this));
        }

        public NodeBitMap inOrAfter() {
            if (inOrAfter == null) {
                inOrAfter = LoopUtil.inOrAfter(this);
            }
            return inOrAfter;
        }

        public NodeBitMap inOrBefore() {
            if (inOrBefore == null) {
                inOrBefore = LoopUtil.inOrBefore(this, inOrAfter());
            }
            return inOrBefore;
        }

        public void invalidateCached() {
            inOrAfter = null;
            inOrBefore = null;
            nodes = null;
        }

        @Override
        public String toString() {
            return "Loop #" + loopBegin().id();
        }
    }

    private static class PeelingResult {
        public final FixedNode begin;
        public final FixedNode end;
        public final NodeMap<StateSplit> exits;
        public final NodeBitMap unaffectedExits;
        public final NodeMap<PlaceholderNode> phis;
        public final NodeMap<Node> phiInits;
        public final NodeMap<Node> dataOut;
        public final NodeBitMap exitFrameStates;
        public final NodeBitMap peeledNodes;
        public PeelingResult(FixedNode begin, FixedNode end, NodeMap<StateSplit> exits, NodeMap<PlaceholderNode> phis, NodeMap<Node> phiInits, NodeMap<Node> dataOut, NodeBitMap unaffectedExits, NodeBitMap exitFrameStates, NodeBitMap peeledNodes) {
            this.begin = begin;
            this.end = end;
            this.exits = exits;
            this.phis = phis;
            this.phiInits = phiInits;
            this.dataOut = dataOut;
            this.unaffectedExits = unaffectedExits;
            this.exitFrameStates = exitFrameStates;
            this.peeledNodes = peeledNodes;
        }
    }

    public static List<Loop> computeLoops(Graph graph) {
        List<Loop> loops = new LinkedList<LoopUtil.Loop>();
        for (LoopBeginNode loopBegin : graph.getNodes(LoopBeginNode.class)) {
            NodeBitMap cfgNodes = markUpCFG(loopBegin, loopBegin.loopEnd()); // computeLoopNodes(loopBegin);
            cfgNodes.mark(loopBegin);
            NodeBitMap exits = computeLoopExits(loopBegin, cfgNodes);
            loops.add(new Loop(loopBegin, cfgNodes, exits));
        }
        for (Loop loop : loops) {
            for (Loop other : loops) {
                if (other != loop && other.cfgNodes().isMarked(loop.loopBegin())) {
                    if (loop.parent() == null || loop.parent().cfgNodes().isMarked(other.loopBegin())) {
                        loop.setParent(other);
                    }
                }
            }
        }
        return loops;
    }

    public static NodeBitMap computeLoopExits(LoopBeginNode loopBegin, NodeBitMap cfgNodes) {
        Graph graph = loopBegin.graph();
        NodeBitMap exits = graph.createNodeBitMap();
        for (Node n : cfgNodes) {
            if (IdentifyBlocksPhase.trueSuccessorCount(n) > 1) {
                for (Node sux : n.cfgSuccessors()) {
                    if (!cfgNodes.isMarked(sux) && sux instanceof FixedNode) {
                        exits.mark(sux);
                    }
                }
            }
        }
        return exits;
    }

    private static boolean recurse = false;
    public static NodeBitMap computeLoopNodesFrom(Loop loop, FixedNode from) {
        LoopBeginNode loopBegin = loop.loopBegin();
        NodeBitMap loopNodes = markUpCFG(loopBegin, from);
        loopNodes.mark(loopBegin);
        NodeBitMap inOrAfter = inOrAfter(loop, loopNodes, false);
        NodeBitMap inOrBefore = inOrBefore(loop, inOrAfter, loopNodes, false);
        if (!recurse) {
            recurse = true;
            GraalCompilation compilation = GraalCompilation.compilation();
            if (compilation.compiler.isObserved()) {
                Map<String, Object> debug = new HashMap<String, Object>();
                debug.put("loopNodes", loopNodes);
                debug.put("inOrAfter", inOrAfter);
                debug.put("inOrBefore", inOrBefore);
                compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "Compute loop nodes loop#" + loopBegin.id(), loopBegin.graph(), true, false, debug));
            }
            recurse = false;
        }
        inOrAfter.setIntersect(inOrBefore);
        loopNodes.setUnion(inOrAfter);
        if (from == loopBegin.loopEnd()) { // fill the Loop cache value for loop nodes this is correct even if after/before were partial
            loop.nodes = loopNodes;
        }
        return loopNodes;
    }

    public static NodeBitMap markUpCFG(LoopBeginNode loopBegin) {
        return markUpCFG(loopBegin, loopBegin.loopEnd());
    }

    public static NodeBitMap markUpCFG(LoopBeginNode loopBegin, FixedNode from) {
        NodeFlood workCFG = loopBegin.graph().createNodeFlood();
        workCFG.add(from);
        NodeBitMap loopNodes = loopBegin.graph().createNodeBitMap();
        for (Node n : workCFG) {
            if (n == loopBegin) {
                continue;
            }
            loopNodes.mark(n);
            if (n instanceof LoopBeginNode) {
                workCFG.add(((LoopBeginNode) n).loopEnd());
            }
            for (Node pred : n.cfgPredecessors()) {
                workCFG.add(pred);
            }
        }
        return loopNodes;
    }

    public static void inverseLoop(Loop loop, IfNode split) {
        assert loop.cfgNodes().isMarked(split);
        FixedNode noExit = split.trueSuccessor();
        FixedNode exit = split.falseSuccessor();
        if (loop.cfgNodes().isMarked(exit) && !loop.cfgNodes().isMarked(noExit)) {
            FixedNode tmp = noExit;
            noExit = exit;
            exit = tmp;
        }
        assert !loop.cfgNodes().isMarked(exit);
        assert loop.cfgNodes().isMarked(noExit);

        PeelingResult peeling = preparePeeling(loop, split);
        rewireInversion(peeling, loop, split);

        // move peeled part to the end
        LoopBeginNode loopBegin = loop.loopBegin();
        LoopEndNode loopEnd = loopBegin.loopEnd();
        FixedNode lastNode = (FixedNode) loopEnd.predecessor();
        if (loopBegin.next() != lastNode) {
            lastNode.successors().replace(loopEnd, loopBegin.next());
            loopBegin.setNext(noExit);
            split.successors().replace(noExit, loopEnd);
        }

        //rewire phi usage in peeled part
        int backIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
        for (PhiNode phi : loopBegin.phis()) {
            ValueNode backValue = phi.valueAt(backIndex);
            if (loop.nodes().isMarked(backValue) && peeling.peeledNodes.isNotNewNotMarked(backValue)) {
                for (Node usage : phi.usages().snapshot()) {
                    if (peeling.peeledNodes.isNotNewMarked(usage)) {
                        usage.inputs().replace(phi, backValue);
                    }
                }
            }
        }

        loop.invalidateCached();

        // update parents
        Loop parent = loop.parent();
        while (parent != null) {
            parent.cfgNodes = markUpCFG(parent.loopBegin, parent.loopBegin.loopEnd());
            parent.invalidateCached();
            parent.exits = computeLoopExits(parent.loopBegin, parent.cfgNodes);
            parent = parent.parent;
        }

        GraalMetrics.LoopsInverted++;
    }

    public static void peelLoop(Loop loop) {
        LoopEndNode loopEnd = loop.loopBegin().loopEnd();
        PeelingResult peeling = preparePeeling(loop, loopEnd);
        GraalCompilation compilation = GraalCompilation.compilation();

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After peeling preparation", loopEnd.graph(), true, false));
        }

        rewirePeeling(peeling, loop);

        loop.invalidateCached();
        // update parents
        Loop parent = loop.parent();
        while (parent != null) {
            parent.cfgNodes = markUpCFG(parent.loopBegin, parent.loopBegin.loopEnd());
            parent.invalidateCached();
            parent.exits = computeLoopExits(parent.loopBegin, parent.cfgNodes);
            parent = parent.parent;
        }
        GraalMetrics.LoopsPeeled++;
    }

    private static void rewireInversion(PeelingResult peeling, Loop loop, FixedNode from) {
        rewirePeeling(peeling, loop, from, true);
    }

    private static void rewirePeeling(PeelingResult peeling, Loop loop) {
        rewirePeeling(peeling, loop, loop.loopBegin().loopEnd(), false);
    }

    private static void rewirePeeling(PeelingResult peeling, Loop loop, FixedNode from, boolean inversion) {
        LoopBeginNode loopBegin = loop.loopBegin();
        Graph graph = loopBegin.graph();
        Node loopPred = loopBegin.predecessor();
        loopPred.successors().replace(loopBegin.forwardEdge(), peeling.begin);
        NodeBitMap loopNodes = loop.cfgNodes();
        Node originalLast = from;
        if (originalLast == loopBegin.loopEnd()) {
            originalLast = loopBegin.loopEnd().predecessor();
        }
        boolean found = false;
        for (NodeClassIterator iter = originalLast.successors().iterator(); iter.hasNext();) {
            Position pos = iter.nextPosition();
            Node sux = originalLast.getNodeClass().get(originalLast, pos);
            if (sux == null) {
                continue;
            }
            if (loopNodes.isMarked(sux)) {
                assert !found;
                peeling.end.getNodeClass().set(peeling.end, pos, loopBegin.forwardEdge());
                found = true;
            }
        }
        assert found;
        int phiInitIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (Entry<Node, PlaceholderNode> entry : peeling.phis.entries()) {
            PhiNode phi = (PhiNode) entry.getKey();
            PlaceholderNode p = entry.getValue();
            ValueNode init = phi.valueAt(phiInitIndex);
            p.replaceAndDelete(init);
            for (Entry<Node, Node> dataEntry : peeling.dataOut.entries()) {
                if (dataEntry.getValue() == p) {
                    dataEntry.setValue(init);
                }
            }
        }
        for (Entry<Node, Node> entry : peeling.phiInits.entries()) {
            PhiNode phi = (PhiNode) entry.getKey();
            Node newInit = entry.getValue();
            phi.setValueAt(phiInitIndex, (ValueNode) newInit);
        }

        if (from == loopBegin.loopEnd()) {
            for (InductionVariableNode iv : loopBegin.inductionVariables()) {
                iv.peelOneIteration();
            }
        }
        NodeMap<NodeMap<ValueNode>> newExitValues = graph.createNodeMap();
        List<Node> exitPoints = new LinkedList<Node>();
        for (Node exit : peeling.unaffectedExits) {
            exitPoints.add(exit);
        }

        for (Entry<Node, StateSplit> entry : peeling.exits.entries()) {
            StateSplit original = (StateSplit) entry.getKey();
            StateSplit newExit = entry.getValue();
            EndNode oEnd = new EndNode(graph);
            EndNode nEnd = new EndNode(graph);
            MergeNode merge = new MergeNode(graph);
            FrameState originalState = original.stateAfter();
            merge.addEnd(nEnd);
            merge.addEnd(oEnd);
            merge.setStateAfter(originalState.duplicate(originalState.bci, true));
            merge.setNext(original.next());
            original.setNext(oEnd);
            newExit.setNext(nEnd);
            exitPoints.add(original);
            exitPoints.add(newExit);
        }

        int phiBackIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
        for (Entry<Node, StateSplit> entry : peeling.exits.entries()) {
            StateSplit original = (StateSplit) entry.getKey();
            EndNode oEnd = (EndNode) original.next();
            MergeNode merge = oEnd.merge();
            EndNode nEnd = merge.endAt(1 - merge.phiPredecessorIndex(oEnd));
            Node newExit = nEnd.predecessor();
            for (Entry<Node, Node> dataEntry : peeling.dataOut.entries()) {
                Node originalValue = dataEntry.getKey();
                Node newValue = dataEntry.getValue();
                NodeMap<ValueNode> phiMap = newExitValues.get(originalValue);
                if (phiMap == null) {
                    phiMap = graph.createNodeMap();
                    newExitValues.set(originalValue, phiMap);
                }
                ValueNode backValue = null;
                if (inversion && originalValue instanceof PhiNode && ((PhiNode) originalValue).merge() == loopBegin) {
                    backValue = ((PhiNode) originalValue).valueAt(phiBackIndex);
                    if (peeling.peeledNodes.isNotNewMarked(backValue)) {
                        backValue = null;
                    }
                }
                if (backValue != null) {
                    phiMap.set(original, backValue);
                } else {
                    phiMap.set(original, (ValueNode) originalValue);
                }
                phiMap.set(newExit, (ValueNode) newValue);
            }
        }

        if (inversion) {
            // rewire dataOut in non-peeled body
            NodeBitMap exitMergesPhis = graph.createNodeBitMap();
            for (Entry<Node, StateSplit> entry : peeling.exits.entries()) {
                StateSplit newExit = entry.getValue();
                MergeNode merge = ((EndNode) newExit.next()).merge();
                exitMergesPhis.markAll(merge.phis());
            }
            for (Entry<Node, Node> entry : peeling.dataOut.entries()) {
                ValueNode originalValue = (ValueNode) entry.getKey();
                if (originalValue instanceof PhiNode && ((PhiNode) originalValue).merge() == loopBegin) {
                    continue;
                }
                ValueNode newValue = (ValueNode) entry.getValue();
                PhiNode phi = null;
                for (Node usage : originalValue.usages().snapshot()) {
                    if (exitMergesPhis.isMarked(usage) || (
                                    loop.nodes().isNotNewMarked(usage)
                                    && peeling.peeledNodes.isNotNewNotMarked(usage)
                                    && !(usage instanceof PhiNode && ((PhiNode) usage).merge() == loopBegin))
                                    && !(usage instanceof FrameState && ((FrameState) usage).block() == loopBegin)) {
                        if (phi == null) {
                            phi = new PhiNode(originalValue.kind, loopBegin, PhiType.Value, graph);
                            phi.addInput(newValue);
                            phi.addInput(originalValue);
                            NodeMap<ValueNode> exitMap = newExitValues.get(originalValue);
                            for (Node exit : peeling.unaffectedExits) {
                                exitMap.set(exit, phi);
                            }
                        }
                        usage.inputs().replace(originalValue, phi);
                    }
                }
            }
        }

        for (Entry<Node, NodeMap<ValueNode>> entry : newExitValues.entries()) {
            ValueNode original = (ValueNode) entry.getKey();
            NodeMap<ValueNode> pointToValue = entry.getValue();
            for (Node exit : exitPoints) {
                Node valueAtExit = pointToValue.get(exit);
                if (valueAtExit == null) {
                    pointToValue.set(exit, original);
                }
            }
        }

        replaceValuesAtLoopExits(newExitValues, loop, exitPoints, peeling.exitFrameStates);
    }

    private static void replaceValuesAtLoopExits(final NodeMap<NodeMap<ValueNode>> newExitValues, Loop loop, List<Node> exitPoints, final NodeBitMap exitFrameStates) {
        Graph graph = loop.loopBegin().graph();
        final NodeMap<Node> colors = graph.createNodeMap();

        // prepare inital colors
        for (Node exitPoint : exitPoints) {
            colors.set(exitPoint, exitPoint);
        }

        // color
        GraphUtil.colorCFGDown(colors, new ColoringLambda<Node>() {
            @Override
            public Node color(Iterable<Node> incomming, MergeNode merge) {
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
            public Node danglingColor(Iterable<Node> incomming, MergeNode merge) {
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


        loop.invalidateCached();
        final NodeBitMap inOrBefore = loop.inOrBefore();

        GraalCompilation compilation = GraalCompilation.compilation();
        if (compilation.compiler.isObserved()) {
            Map<String, Object> debug = new HashMap<String, Object>();
            debug.put("loopExits", colors);
            debug.put("inOrBefore", inOrBefore);
            debug.put("inOrAfter", loop.inOrAfter());
            debug.put("nodes", loop.nodes());
            debug.put("exitFrameStates", exitFrameStates);
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, "After coloring", graph, true, false, debug));
        }

        GraphUtil.splitFromColoring(colors, new ColorSplitingLambda<Node>(){
            @Override
            public void fixSplit(Node oldNode, Node newNode, Node color) {
                assert color != null;
                this.fixNode(newNode, color);
            }
            private ValueNode getValueAt(Node point, NodeMap<ValueNode> valueMap, CiKind kind) {
                ValueNode value = valueMap.get(point);
                if (value != null) {
                    return value;
                }
                MergeNode merge = (MergeNode) point;
                ArrayList<ValueNode> values = new ArrayList<ValueNode>(merge.phiPredecessorCount());
                ValueNode v = null;
                boolean createPhi = false;
                for (EndNode end : merge.cfgPredecessors()) {
                    ValueNode valueAt = getValueAt(colors.get(end), valueMap, kind);
                    if (v == null) {
                        v = valueAt;
                    } else if (v != valueAt) {
                        createPhi = true;
                    }
                    values.add(valueAt);
                }
                if (createPhi) {
                    PhiNode phi = new PhiNode(kind, merge, PhiType.Value, merge.graph());
                    valueMap.set(point, phi);
                    for (EndNode end : merge.cfgPredecessors()) {
                        phi.addInput(getValueAt(colors.get(end), valueMap, kind));
                    }
                    return phi;
                } else {
                    assert v != null;
                    valueMap.set(point, v);
                    return v;
                }
            }
            @Override
            public boolean explore(Node n) {
                return exitFrameStates.isNotNewMarked(n) || (inOrBefore.isNotNewNotMarked(n) && n.getNodeClass().directInputCount() > 0 && !afterColoringFramestate(n)); //TODO (gd) hum
            }
            public boolean afterColoringFramestate(Node n) {
                if (!(n instanceof FrameState)) {
                    return false;
                }
                final FrameState frameState = (FrameState) n;
                MergeNode block = frameState.block();
                if (block != null) {
                    return colors.get(block.next()) == null;
                }
                StateSplit stateSplit = frameState.stateSplit();
                if (stateSplit != null) {
                    return colors.get(stateSplit) == null;
                }
                for (FrameState inner : frameState.innerFrameStates()) {
                    if (!afterColoringFramestate(inner)) {
                        return false;
                    }
                }
                return true;
            }
            @Override
            public void fixNode(Node node, Node color) {
                if (color == null) {
                    // 'white' it out : make non-explorable
                    if (!exitFrameStates.isNew(node)) {
                        exitFrameStates.clear(node);
                    }
                    inOrBefore.mark(node);
                } else {
                    for (NodeClassIterator iter = node.inputs().iterator(); iter.hasNext();) {
                        Position pos = iter.nextPosition();
                        Node input = node.getNodeClass().get(node, pos);
                        if (input == null || newExitValues.isNew(input)) {
                            continue;
                        }
                        NodeMap<ValueNode> valueMap = newExitValues.get(input);
                        if (valueMap != null) {
                            ValueNode replacement = getValueAt(color, valueMap, ((ValueNode) input).kind);
                            node.getNodeClass().set(node, pos, replacement);
                        }
                    }
                }
            }
            @Override
            public ValueNode fixPhiInput(ValueNode input, Node color) {
                if (newExitValues.isNew(input)) {
                    return input;
                }
                NodeMap<ValueNode> valueMap = newExitValues.get(input);
                if (valueMap != null) {
                    return getValueAt(color, valueMap, input.kind);
                }
                return input;
            }
            @Override
            public List<Node> parentColors(Node color) {
                if (!(color instanceof MergeNode)) {
                    return Collections.emptyList();
                }
                MergeNode merge = (MergeNode) color;
                List<Node> parentColors = new ArrayList<Node>(merge.phiPredecessorCount());
                for (EndNode pred : merge.cfgPredecessors()) {
                    parentColors.add(colors.get(pred));
                }
                return parentColors;
            }
            @Override
            public MergeNode merge(Node color) {
                return (MergeNode) color;
            }
        });
    }

    private static PeelingResult preparePeeling(Loop loop, FixedNode from) {
        LoopBeginNode loopBegin = loop.loopBegin();
        Graph graph = loopBegin.graph();
        NodeBitMap marked = computeLoopNodesFrom(loop, from);
        if (from == loopBegin.loopEnd()) {
            clearWithState(from, marked);
        }
        clearWithState(loopBegin, marked);
        Map<Node, Node> replacements = new HashMap<Node, Node>();
        NodeMap<PlaceholderNode> phis = graph.createNodeMap();
        NodeMap<StateSplit> exits = graph.createNodeMap();
        NodeBitMap unaffectedExits = graph.createNodeBitMap();
        NodeBitMap clonedExits = graph.createNodeBitMap();
        NodeBitMap exitFrameStates = graph.createNodeBitMap();
        for (Node exit : loop.exits()) {
            if (marked.isMarked(exit.predecessor())) {
                StateSplit pExit = findNearestMergableExitPoint((FixedNode) exit, marked);
                markWithState(pExit, marked);
                clonedExits.mark(pExit);
                FrameState stateAfter = pExit.stateAfter();
                while (stateAfter != null) {
                    exitFrameStates.mark(stateAfter);
                    stateAfter = stateAfter.outerFrameState();
                }
            } else {
                unaffectedExits.mark(exit);
            }
        }

        NodeBitMap dataOut = graph.createNodeBitMap();
        for (Node n : marked) {
            if (!(n instanceof FrameState)) {
                for (Node usage : n.dataUsages()) {
                    if ((!marked.isMarked(usage) && !((usage instanceof PhiNode) && ((PhiNode) usage).merge() != loopBegin))
                                    || (marked.isMarked(usage) && exitFrameStates.isMarked(usage))) {
                        dataOut.mark(n);
                        break;
                    }
                }
            }
        }

        for (Node n : marked) {
            if (n instanceof PhiNode && ((PhiNode) n).merge() == loopBegin) {
                PlaceholderNode p = new PlaceholderNode(graph);
                replacements.put(n, p);
                phis.set(n, p);
                marked.clear(n);
            }
            for (Node input : n.dataInputs()) {
                if (!marked.isMarked(input) && (!(input instanceof PhiNode) || ((PhiNode) input).merge() != loopBegin) && replacements.get(input) == null) {
                    replacements.put(input, input);
                }
            }
        }

        Map<Node, Node> duplicates = graph.addDuplicate(marked, replacements);

        NodeMap<Node> dataOutMapping = graph.createNodeMap();
        for (Node n : dataOut) {
            Node newOut = duplicates.get(n);
            if (newOut == null) {
                newOut = replacements.get(n);
            }
            assert newOut != null;
            dataOutMapping.set(n, newOut);
        }

        for (Node n : clonedExits) {
            exits.set(n, (StateSplit) duplicates.get(n));
        }

        NodeMap<Node> phiInits = graph.createNodeMap();
        int backIndex = loopBegin.phiPredecessorIndex(loopBegin.loopEnd());
        int fowardIndex = loopBegin.phiPredecessorIndex(loopBegin.forwardEdge());
        for (PhiNode phi : loopBegin.phis()) {
            ValueNode backValue = phi.valueAt(backIndex);
            if (marked.isMarked(backValue)) {
                phiInits.set(phi, duplicates.get(backValue));
            } else if (from == loopBegin.loopEnd()) {
                if (backValue instanceof PhiNode && ((PhiNode) backValue).merge() == loopBegin) {
                    PhiNode backPhi = (PhiNode) backValue;
                    phiInits.set(phi, backPhi.valueAt(fowardIndex));
                } else {
                    phiInits.set(phi, backValue);
                }
            }
        }

        FixedNode newBegin = (FixedNode) duplicates.get(loopBegin.next());
        FixedNode newFrom = (FixedNode) duplicates.get(from == loopBegin.loopEnd() ? from.predecessor() : from);
        return new PeelingResult(newBegin, newFrom, exits, phis, phiInits, dataOutMapping, unaffectedExits, exitFrameStates, marked);
    }

    private static StateSplit findNearestMergableExitPoint(FixedNode exit, NodeBitMap marked) {

        LinkedList<FixedNode> branches = new LinkedList<FixedNode>();
        branches.add(exit);
        while (true) {
            if (branches.size() == 1) {
                final FixedNode fixedNode = branches.get(0);
                if (fixedNode instanceof StateSplit && ((StateSplit) fixedNode).stateAfter() != null) {
                    return (StateSplit) fixedNode;
                }
            } else {
                // FixedNode current = branches.poll();
                // TODO (gd) find appropriate point : will be useful if a loop exit goes "up" as a result of making a branch dead in the loop body
            }
        }
    }

    private static NodeBitMap inOrAfter(Loop loop) {
        return inOrAfter(loop, loop.cfgNodes());
    }

    private static NodeBitMap inOrAfter(Loop loop, NodeBitMap cfgNodes) {
        return inOrAfter(loop, cfgNodes, true);
    }

    private static NodeBitMap inOrAfter(Loop loop, NodeBitMap cfgNodes, boolean full) {
        Graph graph = loop.loopBegin().graph();
        NodeBitMap inOrAfter = graph.createNodeBitMap();
        NodeFlood work = graph.createNodeFlood();
        work.addAll(cfgNodes);
        for (Node n : work) {
            markWithState(n, inOrAfter);
            if (full) {
                for (Node sux : n.successors()) {
                    if (sux != null) {
                        work.add(sux);
                    }
                }
            }
            for (Node usage : n.usages()) {
                if (usage instanceof PhiNode) { // filter out data graph cycles
                    PhiNode phi = (PhiNode) usage;
                    MergeNode merge = phi.merge();
                    if (merge instanceof LoopBeginNode) {
                        LoopBeginNode phiLoop = (LoopBeginNode) merge;
                        int backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                        if (phi.valueAt(backIndex) == n) {
                            continue;
                        }
                    }
                }
                work.add(usage);
            }
        }
        return inOrAfter;
    }

    private static NodeBitMap inOrBefore(Loop loop) {
        return inOrBefore(loop, inOrAfter(loop));
    }

    private static NodeBitMap inOrBefore(Loop loop, NodeBitMap inOrAfter) {
        return inOrBefore(loop, inOrAfter, loop.cfgNodes());
    }

    private static NodeBitMap inOrBefore(Loop loop, NodeBitMap inOrAfter, NodeBitMap cfgNodes) {
        return inOrBefore(loop, inOrAfter, cfgNodes, true);
    }

    private static NodeBitMap inOrBefore(Loop loop, NodeBitMap inOrAfter, NodeBitMap cfgNodes, boolean full) {
        Graph graph = loop.loopBegin().graph();
        NodeBitMap inOrBefore = graph.createNodeBitMap();
        NodeFlood work = graph.createNodeFlood();
        work.addAll(cfgNodes);
        for (Node n : work) {
            inOrBefore.mark(n);
            if (full) {
                if (n.predecessor() != null) {
                    work.add(n.predecessor());
                }
            }
            if (n instanceof PhiNode) { // filter out data graph cycles
                PhiNode phi = (PhiNode) n;
                if (phi.type() == PhiType.Value) {
                    int backIndex = -1;
                    MergeNode merge = phi.merge();
                    if (merge instanceof LoopBeginNode && cfgNodes.isNotNewNotMarked(((LoopBeginNode) merge).loopEnd())) {
                        LoopBeginNode phiLoop = (LoopBeginNode) merge;
                        backIndex = phiLoop.phiPredecessorIndex(phiLoop.loopEnd());
                    }
                    for (int i = 0; i < phi.valueCount(); i++) {
                        if (i != backIndex) {
                            work.add(phi.valueAt(i));
                        }
                    }
                }
            } else {
                for (Node in : n.inputs()) {
                    if (in != null) {
                        work.add(in);
                    }
                }
                if (full) {
                    for (Node sux : n.cfgSuccessors()) { // go down into branches that are not 'inOfAfter'
                        if (sux != null && !inOrAfter.isMarked(sux)) {
                            work.add(sux);
                        }
                    }
                    if (n instanceof LoopBeginNode && n != loop.loopBegin()) {
                        Loop p = loop.parent;
                        boolean isParent = false;
                        while (p != null) {
                            if (p.loopBegin() == n) {
                                isParent = true;
                                break;
                            }
                            p = p.parent;
                        }
                        if (!isParent) {
                            work.add(((LoopBeginNode) n).loopEnd());
                        }
                    }
                }
                if (cfgNodes.isNotNewMarked(n)) { //add all values from the exits framestates
                    for (Node sux : n.cfgSuccessors()) {
                        if (loop.exits().isNotNewMarked(sux) && sux instanceof StateSplit) {
                            FrameState stateAfter = ((StateSplit) sux).stateAfter();
                            while (stateAfter != null) {
                                for (Node in : stateAfter.inputs()) {
                                    if (!(in instanceof FrameState)) {
                                        work.add(in);
                                    }
                                }
                                stateAfter = stateAfter.outerFrameState();
                            }
                        }
                    }
                }
                if (n instanceof MergeNode) { //add phis & counters
                    for (Node usage : n.dataUsages()) {
                        if (!(usage instanceof LoopEndNode)) {
                            work.add(usage);
                        }
                    }
                }
                if (n instanceof AbstractVectorNode) {
                    for (Node usage : n.usages()) {
                        work.add(usage);
                    }
                }
            }
        }
        return inOrBefore;
    }

    private static void markWithState(Node n, NodeBitMap map) {
        map.mark(n);
        if (n instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) n).stateAfter();
            while (stateAfter != null) {
                map.mark(stateAfter);
                stateAfter = stateAfter.outerFrameState();
            }
        }
    }

    private static void clearWithState(Node n, NodeBitMap map) {
        map.clear(n);
        if (n instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) n).stateAfter();
            while (stateAfter != null) {
                map.clear(stateAfter);
                stateAfter = stateAfter.outerFrameState();
            }
        }
    }
}
