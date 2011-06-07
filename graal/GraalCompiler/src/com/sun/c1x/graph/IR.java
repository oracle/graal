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

        Graph graph = compilation.graph;

        // Split critical edges.
        List<Node> nodes = graph.getNodes();
        for (int i = 0; i < nodes.size(); ++i) {
            Node n = nodes.get(i);
            if (Schedule.trueSuccessorCount(n) > 1) {
                for (int j = 0; j < n.successors().size(); ++j) {
                    Node succ = n.successors().get(j);
                    if (Schedule.truePredecessorCount(succ) > 1) {
                        Anchor a = new Anchor(graph);
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
        new GraphBuilder(compilation, compilation.method, compilation.graph).build(false);

        verifyAndPrint("After graph building");

        DeadCodeElimination dce = new DeadCodeElimination();
        dce.apply(compilation.graph);
        if (dce.deletedNodeCount > 0) {
            verifyAndPrint("After dead code elimination");
        }

        if (C1XOptions.Inline) {
            inlineMethods();
        }

        if (C1XOptions.PrintCompilation) {
            TTY.print(String.format("%3d blocks | ", compilation.stats.blockCount));
        }
    }

    private void inlineMethods() {
        int inliningSize = compilation.method.code().length;
        boolean inlined;
        int iterations = C1XOptions.MaximumRecursiveInlineLevel;
        do {
            inlined = false;

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
                            if (concrete != null && concrete.isResolved() && !Modifier.isNative(concrete.accessFlags())) {
                                deoptInline.add(invoke);
                                deoptMethods.add(concrete);
                            }
                        }
                    }
                }
            }

            for (Invoke invoke : trivialInline) {
                if (inlineMethod(invoke, invoke.target)) {
                    inlined = true;
                    inliningSize += invoke.target.code().length;
                    if (inliningSize > C1XOptions.MaximumInstructionCount) {
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
                    inlined = true;
                    inliningSize += method.code().length;
                    if (inliningSize > C1XOptions.MaximumInstructionCount) {
                        break;
                    }
                }
            }

            if (inlined) {
                DeadCodeElimination dce = new DeadCodeElimination();
                dce.apply(compilation.graph);
                if (dce.deletedNodeCount > 0) {
                    verifyAndPrint("After dead code elimination");
                }
                verifyAndPrint("After inlining iteration");
            }

            if (inliningSize > C1XOptions.MaximumInstructionCount) {
                break;
            }
        } while(inlined && (--iterations > 0));
    }

    private boolean inlineMethod(Invoke invoke, RiMethod method) {
        String name = invoke.id() + ": " + CiUtil.format("%H.%n(%p):%r", method, false) + " (" + method.code().length + " bytes)";
        FrameState stateAfter = invoke.stateAfter();

        if (method.code().length > C1XOptions.MaximumInlineSize) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of code size");
            }
            return false;
        }

        if (invoke.predecessors().size() == 0) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because the invoke is dead code");
            }
            return false;
        }

        Instruction exceptionEdge = invoke.exceptionEdge();
//        if (exceptionEdge != null) {
//            if (C1XOptions.TraceInlining) {
//                System.out.println("not inlining " + name + " because of exceptionEdge");
//            }
//            return false;
//        }
        if (!method.holder().isInitialized()) {
            if (C1XOptions.TraceInlining) {
                System.out.println("not inlining " + name + " because of non-initialized class");
            }
            return false;
        }

        if (C1XOptions.TraceInlining) {
            System.out.printf("Building graph for %s, locals: %d, stack: %d\n", name, method.maxLocals(), method.maxStackSize());
        }

        CompilerGraph graph = new CompilerGraph();
        new GraphBuilder(compilation, method, graph).build(true);

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
        StartNode startNode = graph.start();
        for (Node node : graph.getNodes()) {
            if (node != null) {
                if (node instanceof StartNode) {
                    assert startNode == node;
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

        if (C1XOptions.TraceInlining) {
            printGraph("Subgraph " + CiUtil.format("%H.%n(%p):%r", method, false), graph);
            System.out.println("inlining " + name + ": " + frameStates.size() + " frame states, " + nodes.size() + " nodes");
        }

        assert invoke.predecessors().size() == 1 : "size: " + invoke.predecessors().size();
        Instruction pred;
        if (withReceiver) {
            pred = new NullCheck(parameters[0], compilation.graph);
        } else {
            pred = new Merge(compilation.graph);
        }
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

//        if (invoke.next() instanceof Merge) {
//            ((Merge) invoke.next()).removePhiPredecessor(invoke);
//        }
//        invoke.successors().clearAll();
        invoke.inputs().clearAll();
        invoke.setExceptionEdge(null);
//        invoke.delete();


        if (exceptionEdge != null) {
            if (unwindNode != null) {
                assert unwindNode.predecessors().size() == 1;
                assert exceptionEdge.successors().size() == 1;
                ExceptionObject obj = (ExceptionObject) exceptionEdge;

                List<Node> usages = new ArrayList<Node>(obj.usages());
                for (Node usage : usages) {
                    if (replacements.containsKey(unwindNode.exception())) {
                        usage.inputs().replace(obj, replacements.get(unwindNode.exception()));
                    } else {
                        usage.inputs().replace(obj, duplicates.get(unwindNode.exception()));
                    }
                }
                Node unwindDuplicate = duplicates.get(unwindNode);
                unwindDuplicate.inputs().clearAll();

                assert unwindDuplicate.predecessors().size() == 1;
                Node unwindPred = unwindDuplicate.predecessors().get(0);
                int index = unwindDuplicate.predecessorsIndex().get(0);
                unwindPred.successors().setAndClear(index, obj, 0);

                obj.inputs().clearAll();
                obj.delete();
                unwindDuplicate.delete();

            }
        }

        // adjust all frame states that were copied
        if (frameStates.size() > 0) {
            FrameState outerFrameState = stateAfter.duplicateModified(invoke.bci, invoke.kind);
            for (Node frameState : frameStates) {
                ((FrameState) duplicates.get(frameState)).setOuterFrameState(outerFrameState);
            }
        }

        if (C1XOptions.TraceInlining) {
            verifyAndPrint("After inlining " + CiUtil.format("%H.%n(%p):%r", method, false));
        }
        return true;
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

    public void printGraph(String phase, Graph graph) {
        if (C1XOptions.PrintHIR && !TTY.isSuppressed()) {
            TTY.println(phase);
            print(false);
        }

        if (compilation.compiler.isObserved()) {
            compilation.compiler.fireCompilationEvent(new CompilationEvent(compilation, phase, graph, true, false));
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
