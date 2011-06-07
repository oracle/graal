/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.graph;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.max.graal.schedule.*;
import com.sun.c1x.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.gen.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.observer.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * This class implements the overall container for the HIR (high-level IR) graph
 * and directs its construction, optimization, and finalization.
 */
public class IR {

    /**
     * The compilation associated with this IR.
     */
    public final C1XCompilation compilation;

    /**
     * The start block of this IR.
     */
    public LIRBlock startBlock;

    /**
     * The linear-scan ordered list of blocks.
     */
    private List<LIRBlock> orderedBlocks;

    /**
     * Creates a new IR instance for the specified compilation.
     * @param compilation the compilation
     */
    public IR(C1XCompilation compilation) {
        this.compilation = compilation;
    }

    public Map<Node, LIRBlock> valueToBlock;

    /**
     * Builds the graph, optimizes it, and computes the linear scan block order.
     */
    public void build() {
        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_CREATE.start();
        }

        buildGraph();

        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_CREATE.stop();
            C1XTimers.HIR_OPTIMIZE.start();
        }

        new PhiSimplifier(this);

//        Graph newGraph = new Graph();
//        HashMap<Node, Node> replacement = new HashMap<Node, Node>();
//        replacement.put(compilation.graph.start(), newGraph.start());
//        replacement.put(compilation.graph.end(), newGraph.end());
//        newGraph.addDuplicate(compilation.graph.getNodes(), replacement);
//        compilation.graph = newGraph;

        Graph graph = compilation.graph;

        // Split critical edges.
        List<Node> nodes = graph.getNodes();
        for (int i = 0; i < nodes.size(); ++i) {
            Node n = nodes.get(i);
            if (Schedule.trueSuccessorCount(n) > 1) {
                for (int j = 0; j < n.successors().size(); ++j) {
                    Node succ = n.successors().get(j);
                    if (Schedule.truePredecessorCount(succ) > 1) {
                        Anchor a = new Anchor(null, graph);
                        a.successors().setAndClear(1, n, j);
                        n.successors().set(j, a);
                    }
                }
            }
        }

        Schedule schedule = new Schedule(graph);
        List<Block> blocks = schedule.getBlocks();
        List<LIRBlock> lirBlocks = new ArrayList<LIRBlock>();
        Map<Block, LIRBlock> map = new HashMap<Block, LIRBlock>();
        for (Block b : blocks) {
            LIRBlock block = new LIRBlock(b.blockID());
            map.put(b, block);
            block.setInstructions(b.getInstructions());
            block.setLinearScanNumber(b.blockID());

            block.setFirstInstruction(b.firstNode());
            block.setLastInstruction(b.lastNode());
            lirBlocks.add(block);
        }

        for (Block b : blocks) {
            for (Block succ : b.getSuccessors()) {
                map.get(b).blockSuccessors().add(map.get(succ));
            }

            for (Block pred : b.getPredecessors()) {
                map.get(b).blockPredecessors().add(map.get(pred));
            }
        }

        orderedBlocks = lirBlocks;
        valueToBlock = new HashMap<Node, LIRBlock>();
        for (LIRBlock b : orderedBlocks) {
            for (Node i : b.getInstructions()) {
                valueToBlock.put(i, b);
            }
        }
        startBlock = lirBlocks.get(0);
        assert startBlock != null;
        assert startBlock.blockPredecessors().size() == 0;

        ComputeLinearScanOrder clso = new ComputeLinearScanOrder(lirBlocks.size(), startBlock);
        orderedBlocks = clso.linearScanOrder();
        this.compilation.stats.loopCount = clso.numLoops();

        int z = 0;
        for (LIRBlock b : orderedBlocks) {
            b.setLinearScanNumber(z++);
        }

        verifyAndPrint("After linear scan order");

        if (C1XOptions.PrintTimers) {
            C1XTimers.HIR_OPTIMIZE.stop();
        }
    }

    private void buildGraph() {
        // Graph builder must set the startBlock and the osrEntryBlock
        new GraphBuilder(compilation, compilation.method, compilation.graph).build();

        verifyAndPrint("After graph building");

            if (C1XOptions.Inline) {
            List<Invoke> trivialInline = new ArrayList<Invoke>();
            List<Invoke> deoptInline = new ArrayList<Invoke>();
            List<RiMethod> deoptMethods = new ArrayList<RiMethod>();
            for (Node node : compilation.graph.getNodes()) {
                if (node instanceof Invoke) {
                    Invoke invoke = (Invoke) node;
                    RiMethod target = invoke.target;
                    if (target.isResolved() && !Modifier.isNative(target.accessFlags())) {
                        if (target.canBeStaticallyBound()) {
                            trivialInline.add(invoke);
                        } else {
                            RiMethod concrete = invoke.target.holder().uniqueConcreteMethod(invoke.target);
                            if (concrete != null) {
                                deoptInline.add(invoke);
                                deoptMethods.add(concrete);
                            }
                        }
                    }
                }
            }

            int allowedInlinings = 50;
            for (Invoke invoke : trivialInline) {
                if (inlineMethod(invoke, invoke.target)) {
                    if (--allowedInlinings <= 0) {
                        break;
                    }
                }
            }

            for (int i = 0; i < deoptInline.size(); i++) {
                Invoke invoke = deoptInline.get(i);
                RiMethod method = deoptMethods.get(i);
                if (inlineMethod(invoke, method)) {
                    if (C1XOptions.TraceInlining) {
                        System.out.println("registering concrete method assumption...");
                    }
                    compilation.assumptions.recordConcreteMethod(invoke.target, method);
                    if (--allowedInlinings <= 0) {
                        break;
                    }
                }
            }
        }

        if (C1XOptions.PrintCompilation) {
            TTY.print(String.format("%3d blocks | ", compilation.stats.blockCount));
        }
    }

    private boolean inlineMethod(Invoke invoke, RiMethod method) {
        String name = invoke.id() + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.code().length + " bytes)";

        if (method.code().length > 50) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of code size");
            }
            return false;
        }

        Instruction exceptionEdge = invoke.exceptionEdge();
        if (exceptionEdge != null) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of exceptionEdge");
            }
            return false;
        }
        if (!method.holder().isInitialized()) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of non-initialized class");
            }
            return false;
        }

        if (C1XOptions.TraceInlining) {
            System.out.println("building graph: " + name);
        }
        CompilerGraph graph = new CompilerGraph();
        new GraphBuilder(compilation, method, graph).build();

        boolean withReceiver = !Modifier.isStatic(method.accessFlags());

        int argumentCount = method.signature().argumentCount(false);
        Value[] parameters = new Value[argumentCount + (withReceiver ? 1 : 0)];
        int slot = withReceiver ? 1 : 0;
        int param = withReceiver ? 1 : 0;
        for (int i = 0; i < argumentCount; i++) {
            parameters[param++] = invoke.argument(slot);
            slot += method.signature().argumentKindAt(i).sizeInSlots();
        }
        if (withReceiver) {
            parameters[0] = invoke.argument(0);
        }

        HashMap<Node, Node> replacements = new HashMap<Node, Node>();
        ArrayList<Node> nodes = new ArrayList<Node>();
        ArrayList<Node> frameStates = new ArrayList<Node>();
        Return returnNode = null;
        Unwind unwindNode = null;
        StartNode startNode = null;
        boolean invokes = false;
        for (Node node : graph.getNodes()) {
            if (node != null) {
                if (node instanceof StartNode) {
                    startNode = (StartNode) node;
                } else if (node instanceof Local) {
                    replacements.put(node, parameters[((Local) node).index()]);
                } else {
                    nodes.add(node);
                    if (node instanceof Return) {
                        returnNode = (Return) node;
                    } else if (node instanceof Unwind) {
                        unwindNode = (Unwind) node;
                    } else if (node instanceof FrameState) {
                        frameStates.add(node);
                    }
                }
            }
        }
        if (unwindNode != null) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of unwind node");
            }
            return false;
        }
        if (invokes) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of invokes");
            }
            return false;
        }

        if (C1XOptions.TraceInlining) {
            System.out.println("inlining " + name + ": " + frameStates.size() + " frame states, " + nodes.size() + " nodes");
        }

        Instruction pred;
        if (withReceiver) {
            pred = new NullCheck(parameters[0], compilation.graph);
        } else {
            pred = new Merge(compilation.graph);
        }
        assert invoke.predecessors().size() == 1;
        invoke.predecessors().get(0).successors().replace(invoke, pred);
        replacements.put(startNode, pred);

        Map<Node, Node> duplicates = compilation.graph.addDuplicate(nodes, replacements);

        if (returnNode != null) {
            List<Node> usages = new ArrayList<Node>(invoke.usages());
            for (Node usage : usages) {
                if (returnNode.result() instanceof Local) {
                    usage.inputs().replace(invoke, replacements.get(returnNode.result()));
                } else {
                    usage.inputs().replace(invoke, duplicates.get(returnNode.result()));
                }
            }
            Node returnDuplicate = duplicates.get(returnNode);
            returnDuplicate.inputs().clearAll();

            assert returnDuplicate.predecessors().size() == 1;
            Node returnPred = returnDuplicate.predecessors().get(0);
            int index = returnDuplicate.predecessorsIndex().get(0);
            returnPred.successors().setAndClear(index, invoke, 0);

            returnDuplicate.delete();
        }
        FrameState stateAfter = invoke.stateAfter();
        if (frameStates.size() > 0) {
            FrameState outerFrameState = stateAfter.duplicateModified(invoke.bci, invoke.kind);
            for (Node frameState : frameStates) {
                ((FrameState) duplicates.get(frameState)).setOuterFrameState(outerFrameState);
            }
        }

        invoke.successors().clearAll();
        invoke.inputs().clearAll();
        invoke.delete();

        stateAfter.delete();

        deleteUnused(exceptionEdge);

        verifyAndPrint("After inlining " + CiUtil.format("%H.%n(%p):%r", method, false));
        return true;
    }

    private void deleteUnused(Node node) {
        if (node != null && node.predecessors().size() == 0) {
            if (node instanceof ExceptionObject) {
                Node successor = node.successors().get(0);
                node.successors().clearAll();
                if (successor instanceof ExceptionDispatch) {
                    ExceptionDispatch dispatch = (ExceptionDispatch) successor;
                    Node succ1 = dispatch.catchSuccessor();
                    Node succ2 = dispatch.otherSuccessor();
                    if (succ1 instanceof Merge) {
                        ((Merge) succ1).removePhiPredecessor(dispatch);
                    }
                    if (succ2 instanceof Merge) {
                        ((Merge) succ2).removePhiPredecessor(dispatch);
                    }
                    dispatch.successors().clearAll();
                    deleteUnused(succ1);
                    deleteUnused(succ2);
                    dispatch.delete();
                } else {
                    assert successor instanceof Merge;
                    System.out.println("succ: " + successor.successors().get(0));
                    Node next = successor.successors().get(0);
                    successor.successors().clearAll();
                    deleteUnused(next);
                    successor.delete();
                }
                node.delete();
            } else if (node instanceof Unwind) {
                node.delete();
            }
        }
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<LIRBlock> linearScanOrder() {
        return orderedBlocks;
    }

    private void print(boolean cfgOnly) {
        if (!TTY.isSuppressed()) {
            TTY.println("IR for " + compilation.method);
            final InstructionPrinter ip = new InstructionPrinter(TTY.out());
            final BlockPrinter bp = new BlockPrinter(this, ip, cfgOnly);
            //getHIRStartBlock().iteratePreOrder(bp);
        }
    }

    /**
     * Verifies the IR and prints it out if the relevant options are set.
     * @param phase the name of the phase for printing
     */
    public void verifyAndPrint(String phase) {
        if (C1XOptions.PrintHIR && !TTY.isSuppressed()) {
            TTY.println(phase);
            print(false);
        }

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, phase, compilation.graph, true, false));
        }
    }

    public int numLoops() {
        return compilation.stats.loopCount;
    }

    /**
     * Gets the maximum number of locks in the graph's frame states.
     */
    public final int maxLocks() {
        int maxLocks = 0;
        for (Node node : compilation.graph.getNodes()) {
            if (node instanceof FrameState) {
                int lockCount = ((FrameState) node).locksSize();
                if (lockCount > maxLocks) {
                    maxLocks = lockCount;
                }
            }
        }
        return maxLocks;
    }

    public Instruction getHIRStartBlock() {
        return (Instruction) compilation.graph.start().successors().get(0);
    }
}
