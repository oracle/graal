/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.printer;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.io.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.alloc.*;
import com.oracle.graal.compiler.alloc.Interval.UsePosList;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.compiler.gen.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.Verbosity;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.java.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.phases.schedule.*;

/**
 * Utility for printing Graal IR at various compilation phases.
 */
class CFGPrinter extends CompilationPrinter {

    protected TargetDescription target;
    protected LIR lir;
    protected NodeLIRBuilder nodeLirGenerator;
    protected ControlFlowGraph cfg;
    protected SchedulePhase schedule;
    protected ResolvedJavaMethod method;

    /**
     * Creates a control flow graph printer.
     *
     * @param out where the output generated via this printer shown be written
     */
    public CFGPrinter(OutputStream out) {
        super(out);
    }

    /**
     * Prints the control flow graph denoted by a given block map.
     *
     * @param label A label describing the compilation phase that produced the control flow graph.
     * @param blockMap A data structure describing the blocks in a method and how they are
     *            connected.
     */
    public void printCFG(String label, BciBlockMapping blockMap) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        for (BciBlockMapping.BciBlock block : blockMap.blocks) {
            begin("block");
            printBlock(block);
            end("block");
        }
        end("cfg");
    }

    private void printBlock(BciBlockMapping.BciBlock block) {
        out.print("name \"B").print(block.startBci).println('"');
        out.print("from_bci ").println(block.startBci);
        out.print("to_bci ").println(block.endBci);

        out.println("predecessors ");

        out.print("successors ");
        for (BciBlockMapping.BciBlock succ : block.getSuccessors()) {
            if (!succ.isExceptionEntry) {
                out.print("\"B").print(succ.startBci).print("\" ");
            }
        }
        out.println();

        out.print("xhandlers");
        for (BciBlockMapping.BciBlock succ : block.getSuccessors()) {
            if (succ.isExceptionEntry) {
                out.print("\"B").print(succ.startBci).print("\" ");
            }
        }
        out.println();

        out.print("flags ");
        if (block.isExceptionEntry) {
            out.print("\"ex\" ");
        }
        if (block.isLoopHeader) {
            out.print("\"plh\" ");
        }
        out.println();

        out.print("loop_depth ").println(Long.bitCount(block.loops));
    }

    private NodeMap<Block> latestScheduling;
    private NodeBitMap printedNodes;

    private boolean inFixedSchedule(Node node) {
        return lir != null || schedule != null || node.isDeleted() || cfg.getNodeToBlock().get(node) != null;
    }

    /**
     * Prints the specified list of blocks.
     *
     * @param label A label describing the compilation phase that produced the control flow graph.
     * @param blocks The list of blocks to be printed.
     */
    public void printCFG(String label, List<? extends AbstractBlock<?>> blocks, boolean printNodes) {
        if (lir == null) {
            latestScheduling = new NodeMap<>(cfg.getNodeToBlock());
            for (AbstractBlock<?> abstractBlock : blocks) {
                Block block = (Block) abstractBlock;
                Node cur = block.getBeginNode();
                while (true) {
                    assert inFixedSchedule(cur) && latestScheduling.get(cur) == block;
                    scheduleInputs(cur, block);

                    if (cur == block.getEndNode()) {
                        break;
                    }
                    assert cur.successors().count() == 1;
                    cur = cur.successors().first();
                }
            }
        }

        begin("cfg");
        out.print("name \"").print(label).println('"');
        for (AbstractBlock<?> block : blocks) {
            printBlock(block, printNodes);
        }
        end("cfg");
        // NOTE: we do this only because the c1visualizer does not recognize the bytecode block if
        // it is proceeding the cfg blocks. Currently we have no direct influence on the emit order.
        // As a workaround we dump the bytecode after every cfg.
        if (method != null) {
            printBytecodes(new BytecodeDisassembler(false).disassemble(method));
        }

        latestScheduling = null;
    }

    private void scheduleInputs(Node node, Block nodeBlock) {
        if (node instanceof ValuePhiNode) {
            PhiNode phi = (PhiNode) node;
            assert nodeBlock.getBeginNode() == phi.merge();
            for (Block pred : nodeBlock.getPredecessors()) {
                schedule(phi.valueAt((AbstractEndNode) pred.getEndNode()), pred);
            }

        } else {
            for (Node input : node.inputs()) {
                schedule(input, nodeBlock);
            }
        }
    }

    private void schedule(Node input, Block block) {
        if (!inFixedSchedule(input)) {
            Block inputBlock = block;
            if (latestScheduling.get(input) != null) {
                inputBlock = AbstractControlFlowGraph.commonDominatorTyped(inputBlock, latestScheduling.get(input));
            }
            if (inputBlock != latestScheduling.get(input)) {
                latestScheduling.set(input, inputBlock);
                scheduleInputs(input, inputBlock);
            }
        }
    }

    private void printBlock(AbstractBlock<?> block, boolean printNodes) {
        printBlockProlog(block);
        if (printNodes) {
            assert block instanceof Block;
            printNodes((Block) block);
        }
        printBlockEpilog(block);
    }

    private void printBlockEpilog(AbstractBlock<?> block) {
        printLIR(block);
        end("block");
    }

    private void printBlockProlog(AbstractBlock<?> block) {
        begin("block");

        out.print("name \"").print(blockToString(block)).println('"');
        if (block instanceof BciBlock) {
            out.print("from_bci ").println(((BciBlock) block).startBci);
            out.print("to_bci ").println(((BciBlock) block).endBci);
        } else {
            out.println("from_bci -1");
            out.println("to_bci -1");
        }

        out.print("predecessors ");
        for (AbstractBlock<?> pred : block.getPredecessors()) {
            out.print("\"").print(blockToString(pred)).print("\" ");
        }
        out.println();

        out.print("successors ");
        for (AbstractBlock<?> succ : block.getSuccessors()) {
            if (!succ.isExceptionEntry()) {
                out.print("\"").print(blockToString(succ)).print("\" ");
            }
        }
        out.println();

        out.print("xhandlers");
        for (AbstractBlock<?> succ : block.getSuccessors()) {
            if (succ.isExceptionEntry()) {
                out.print("\"").print(blockToString(succ)).print("\" ");
            }
        }
        out.println();

        out.print("flags ");
        if (block.isLoopHeader()) {
            out.print("\"llh\" ");
        }
        if (block.isLoopEnd()) {
            out.print("\"lle\" ");
        }
        if (block.isExceptionEntry()) {
            out.print("\"ex\" ");
        }
        out.println();

        if (block.getLoop() != null) {
            out.print("loop_index ").println(block.getLoop().getIndex());
            out.print("loop_depth ").println(block.getLoop().getDepth());
        }
    }

    private void printNodes(Block block) {
        printedNodes = new NodeBitMap(cfg.graph);
        begin("IR");
        out.println("HIR");
        out.disableIndentation();

        if (block.getBeginNode() instanceof MergeNode) {
            // Currently phi functions are not in the schedule, so print them separately here.
            for (ValueNode phi : ((MergeNode) block.getBeginNode()).phis()) {
                printNode(phi, false);
            }
        }

        // Currently no node printing for lir
        if (lir == null) {
            Node cur = block.getBeginNode();
            while (true) {
                printNode(cur, false);

                if (cur == block.getEndNode()) {
                    for (Map.Entry<Node, Block> entry : latestScheduling.entries()) {
                        if (entry.getValue() == block && !inFixedSchedule(entry.getKey()) && !printedNodes.isMarked(entry.getKey())) {
                            printNode(entry.getKey(), true);
                        }
                    }
                    break;
                }
                assert cur.successors().count() == 1;
                cur = cur.successors().first();
            }

        }

        out.enableIndentation();
        end("IR");
        printedNodes = null;
    }

    private void printNode(Node node, boolean unscheduled) {
        assert !printedNodes.isMarked(node);
        printedNodes.mark(node);

        if (!(node instanceof ValuePhiNode)) {
            for (Node input : node.inputs()) {
                if (!inFixedSchedule(input) && !printedNodes.isMarked(input)) {
                    printNode(input, true);
                }
            }
        }

        if (unscheduled) {
            assert lir == null && schedule == null : "unscheduled nodes can only be present before LIR generation";
            out.print("f ").print(HOVER_START).print("u").print(HOVER_SEP).print("unscheduled").print(HOVER_END).println(COLUMN_END);
        } else if (node instanceof FixedWithNextNode) {
            out.print("f ").print(HOVER_START).print("#").print(HOVER_SEP).print("fixed with next").print(HOVER_END).println(COLUMN_END);
        } else if (node instanceof FixedNode) {
            out.print("f ").print(HOVER_START).print("*").print(HOVER_SEP).print("fixed").print(HOVER_END).println(COLUMN_END);
        } else if (node instanceof FloatingNode) {
            out.print("f ").print(HOVER_START).print("~").print(HOVER_SEP).print("floating").print(HOVER_END).println(COLUMN_END);
        }
        out.print("tid ").print(nodeToString(node)).println(COLUMN_END);

        if (nodeLirGenerator != null) {
            Value operand = nodeLirGenerator.getNodeOperands().get(node);
            if (operand != null) {
                out.print("result ").print(operand.toString()).println(COLUMN_END);
            }
        }

        if (node instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) node;
            if (stateSplit.stateAfter() != null) {
                String state = stateToString(stateSplit.stateAfter());
                out.print("st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).println(COLUMN_END);
            }
        }

        Map<Object, Object> props = new TreeMap<>(node.getDebugProperties());
        out.print("d ").print(HOVER_START).print("d").print(HOVER_SEP);
        out.println("=== Debug Properties ===");
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            out.print(entry.getKey().toString()).print(": ").print(entry.getValue() == null ? "[null]" : entry.getValue().toString()).println();
        }
        out.println("=== Inputs ===");
        printNamedNodes(node, node.inputs().iterator(), "", "\n", null);
        out.println("=== Succesors ===");
        printNamedNodes(node, node.successors().iterator(), "", "\n", null);
        out.println("=== Usages ===");
        if (node.recordsUsages() && !node.usages().isEmpty()) {
            for (Node usage : node.usages()) {
                out.print(nodeToString(usage)).print(" ");
            }
            out.println();
        }
        out.println("=== Predecessor ===");
        out.print(nodeToString(node.predecessor())).print(" ");
        out.print(HOVER_END).println(COLUMN_END);

        out.print("instruction ");
        out.print(HOVER_START).print(node.getNodeClass().shortName()).print(HOVER_SEP).print(node.getClass().getName()).print(HOVER_END).print(" ");
        printNamedNodes(node, node.inputs().iterator(), "", "", "#NDF");
        printNamedNodes(node, node.successors().iterator(), "#", "", "#NDF");
        for (Map.Entry<Object, Object> entry : props.entrySet()) {
            String key = entry.getKey().toString();
            if (key.startsWith("data.") && !key.equals("data.stamp")) {
                out.print(key.substring("data.".length())).print(": ").print(entry.getValue() == null ? "[null]" : entry.getValue().toString()).print(" ");
            }
        }
        out.print(COLUMN_END).print(' ').println(COLUMN_END);
    }

    private void printNamedNodes(Node node, NodeClassIterator iter, String prefix, String suffix, String hideSuffix) {
        int lastIndex = -1;
        while (iter.hasNext()) {
            Position pos = iter.nextPosition();
            if (hideSuffix != null && node.getNodeClass().getName(pos).endsWith(hideSuffix)) {
                continue;
            }

            if (pos.getIndex() != lastIndex) {
                if (lastIndex != -1) {
                    out.print(suffix);
                }
                out.print(prefix).print(node.getNodeClass().getName(pos)).print(": ");
                lastIndex = pos.getIndex();
            }
            out.print(nodeToString(node.getNodeClass().get(node, pos))).print(" ");
        }
        if (lastIndex != -1) {
            out.print(suffix);
        }
    }

    private String stateToString(FrameState state) {
        StringBuilder buf = new StringBuilder();
        FrameState curState = state;
        do {
            buf.append(MetaUtil.toLocation(curState.method(), curState.bci)).append('\n');

            if (curState.stackSize() > 0) {
                buf.append("stack: ");
                for (int i = 0; i < curState.stackSize(); i++) {
                    buf.append(stateValueToString(curState.stackAt(i))).append(' ');
                }
                buf.append("\n");
            }

            buf.append("locals: ");
            for (int i = 0; i < curState.localsSize(); i++) {
                buf.append(stateValueToString(curState.localAt(i))).append(' ');
            }
            buf.append("\n");

            buf.append("locks: ");
            for (int i = 0; i < curState.locksSize(); i++) {
                buf.append(stateValueToString(curState.lockAt(i))).append(' ');
            }
            buf.append("\n");

            curState = curState.outerFrameState();
        } while (curState != null);

        return buf.toString();
    }

    private String stateValueToString(ValueNode value) {
        String result = nodeToString(value);
        if (nodeLirGenerator != null && nodeLirGenerator.getNodeOperands() != null && value != null) {
            Value operand = nodeLirGenerator.getNodeOperands().get(value);
            if (operand != null) {
                result += ": " + operand;
            }
        }
        return result;
    }

    /**
     * Prints the LIR for each instruction in a given block.
     *
     * @param block the block to print
     */
    private void printLIR(AbstractBlock<?> block) {
        if (lir == null) {
            return;
        }
        List<LIRInstruction> lirInstructions = lir.getLIRforBlock(block);
        if (lirInstructions == null) {
            return;
        }

        begin("IR");
        out.println("LIR");

        for (int i = 0; i < lirInstructions.size(); i++) {
            LIRInstruction inst = lirInstructions.get(i);
            out.printf("nr %4d ", inst.id()).print(COLUMN_END);

            final StringBuilder stateString = new StringBuilder();
            inst.forEachState(new StateProcedure() {

                @Override
                protected void doState(LIRFrameState state) {
                    if (state.hasDebugInfo()) {
                        DebugInfo di = state.debugInfo();
                        stateString.append(debugInfoToString(di.getBytecodePosition(), di.getReferenceMap(), di.getCalleeSaveInfo(), target.arch));
                    } else {
                        stateString.append(debugInfoToString(state.topFrame, null, null, target.arch));
                    }
                }
            });
            if (stateString.length() > 0) {
                int level = out.indentationLevel();
                out.adjustIndentation(-level);
                out.print(" st ").print(HOVER_START).print("st").print(HOVER_SEP).print(stateString.toString()).print(HOVER_END).print(COLUMN_END);
                out.adjustIndentation(level);
            }

            out.print(" instruction ").print(inst.toString()).print(COLUMN_END);
            out.println(COLUMN_END);
        }
        end("IR");
    }

    private String nodeToString(Node node) {
        if (node == null) {
            return "-";
        }
        String prefix;
        if (node instanceof BeginNode && (lir == null && schedule == null)) {
            prefix = "B";
        } else if (node instanceof ValueNode) {
            ValueNode value = (ValueNode) node;
            if (value.getKind() == Kind.Illegal) {
                prefix = "v";
            } else {
                prefix = String.valueOf(value.getKind().getTypeChar());
            }
        } else {
            prefix = "?";
        }
        return prefix + node.toString(Verbosity.Id);
    }

    private String blockToString(AbstractBlock<?> block) {
        if (lir == null && schedule == null && block instanceof Block) {
            // During all the front-end phases, the block schedule is built only for the debug
            // output.
            // Therefore, the block numbers would be different for every CFG printed -> use the id
            // of the first instruction.
            return "B" + ((Block) block).getBeginNode().toString(Verbosity.Id);
        } else {
            // LIR instructions contain references to blocks and these blocks are printed as the
            // blockID -> use the blockID.
            return "B" + block.getId();
        }
    }

    public void printIntervals(String label, Interval[] intervals) {
        begin("intervals");
        out.println(String.format("name \"%s\"", label));

        for (Interval interval : intervals) {
            if (interval != null) {
                printInterval(interval);
            }
        }

        end("intervals");
    }

    private void printInterval(Interval interval) {
        out.printf("%s %s ", interval.operand, (isRegister(interval.operand) ? "fixed" : interval.kind().getPlatformKind()));
        if (isRegister(interval.operand)) {
            out.printf("\"[%s|%c]\"", interval.operand, interval.operand.getKind().getTypeChar());
        } else {
            if (interval.location() != null) {
                out.printf("\"[%s|%c]\"", interval.location(), interval.location().getKind().getTypeChar());
            }
        }

        Interval hint = interval.locationHint(false);
        out.printf("%s %s ", interval.splitParent().operand, hint != null ? hint.operand : -1);

        // print ranges
        Range cur = interval.first();
        while (cur != Range.EndMarker) {
            out.printf("[%d, %d[", cur.from, cur.to);
            cur = cur.next;
            assert cur != null : "range list not closed with range sentinel";
        }

        // print use positions
        int prev = 0;
        UsePosList usePosList = interval.usePosList();
        for (int i = usePosList.size() - 1; i >= 0; --i) {
            assert prev < usePosList.usePos(i) : "use positions not sorted";
            out.printf("%d %s ", usePosList.usePos(i), usePosList.registerPriority(i));
            prev = usePosList.usePos(i);
        }

        out.printf(" \"%s\"", interval.spillState());
        out.println();
    }

    public void printSchedule(String message, SchedulePhase theSchedule) {
        schedule = theSchedule;
        cfg = schedule.getCFG();
        printedNodes = new NodeBitMap(cfg.graph);

        begin("cfg");
        out.print("name \"").print(message).println('"');
        for (Block b : schedule.getCFG().getBlocks()) {
            if (schedule.nodesFor(b) != null) {
                printScheduledBlock(b, schedule.nodesFor(b));
            }
        }
        end("cfg");

        schedule = null;
        cfg = null;
        printedNodes = null;
    }

    private void printScheduledBlock(Block block, List<ScheduledNode> nodesFor) {
        printBlockProlog(block);
        begin("IR");
        out.println("HIR");
        out.disableIndentation();

        if (block.getBeginNode() instanceof MergeNode) {
            // Currently phi functions are not in the schedule, so print them separately here.
            for (ValueNode phi : ((MergeNode) block.getBeginNode()).phis()) {
                printNode(phi, false);
            }
        }

        for (Node n : nodesFor) {
            printNode(n, false);
        }

        out.enableIndentation();
        end("IR");

        printBlockEpilog(block);
    }
}
