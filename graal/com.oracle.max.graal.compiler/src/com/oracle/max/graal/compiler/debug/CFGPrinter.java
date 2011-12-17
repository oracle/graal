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
package com.oracle.max.graal.compiler.debug;

import java.io.*;
import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.*;
import com.oracle.max.graal.compiler.alloc.Interval.UsePosList;
import com.oracle.max.graal.compiler.graphbuilder.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.schedule.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.graph.Node.*;
import com.oracle.max.graal.graph.NodeClass.*;
import com.oracle.max.graal.nodes.*;
import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Utility for printing Graal IR at various compilation phases.
 */
public class CFGPrinter extends CompilationPrinter {

    public final ByteArrayOutputStream buffer;
    public final GraalCompilation compilation;
    public final CiTarget target;
    public final RiRuntime runtime;

    /**
     * Creates a control flow graph printer.
     *
     * @param buffer where the output generated via this printer shown be written
     */
    public CFGPrinter(ByteArrayOutputStream buffer, GraalCompilation compilation) {
        super(buffer);
        this.buffer = buffer;
        this.compilation = compilation;
        this.target = compilation.compiler.target;
        this.runtime = compilation.compiler.runtime;
    }

    public CFGPrinter(ByteArrayOutputStream buffer, GraalCompilation compilation, CiTarget target, RiRuntime runtime) {
        super(buffer);
        this.buffer = buffer;
        this.compilation = compilation;
        this.target = target;
        this.runtime = runtime;
    }

    /**
     * Prints the control flow graph denoted by a given block map.
     *
     * @param label A label describing the compilation phase that produced the control flow graph.
     * @param blockMap A data structure describing the blocks in a method and how they are connected.
     */
    public void printCFG(String label, BlockMap blockMap) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        for (BlockMap.Block block : blockMap.blocks) {
            begin("block");
            printBlock(block);
            end("block");
        }
        end("cfg");
    }

    private void printBlock(BlockMap.Block block) {
        out.print("name \"B").print(block.startBci).println('"');
        out.print("from_bci ").println(block.startBci);
        out.print("to_bci ").println(block.endBci);

        out.println("predecessors ");

        out.print("successors ");
        for (BlockMap.Block succ : block.successors) {
            if (!succ.isExceptionEntry) {
                out.print("\"B").print(succ.startBci).print("\" ");
            }
        }
        out.println();

        out.print("xhandlers");
        for (BlockMap.Block succ : block.successors) {
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


    /**
     * Prints the specified list of blocks.
     *
     * @param label A label describing the compilation phase that produced the control flow graph.
     * @param blocks The list of blocks to be printed.
     * @param printNodes If {@code true} the nodes in the block will be printed.
     */
    public void printCFG(String label, List<? extends Block> blocks, boolean printNodes) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        for (Block block : blocks) {
            printBlock(block, printNodes);
        }
        end("cfg");
    }

    private void printBlock(Block block, boolean printNodes) {
        begin("block");

        out.print("name \"").print(blockToString(block)).println('"');
        out.println("from_bci -1");
        out.println("to_bci -1");

        out.print("predecessors ");
        for (Block pred : block.getPredecessors()) {
            out.print("\"").print(blockToString(pred)).print("\" ");
        }
        out.println();

        out.print("successors ");
        for (Block succ : block.getSuccessors()) {
            if (!succ.isExceptionBlock()) {
                out.print("\"").print(blockToString(succ)).print("\" ");
            }
        }
        out.println();

        out.print("xhandlers");
        for (Block succ : block.getSuccessors()) {
            if (succ.isExceptionBlock()) {
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
        if (block.isExceptionBlock()) {
            out.print("\"ex\" ");
        }
        out.println();

        out.print("loop_index ").println(block.loopIndex());
        out.print("loop_depth ").println(block.loopDepth());

        if (printNodes) {
            printNodes(block);
        }

        if (block instanceof LIRBlock) {
            printLIR((LIRBlock) block);
        }

        end("block");
    }

    private void printNodes(Block block) {
        begin("IR");
        out.println("HIR");
        out.disableIndentation();

        if (block.getPredecessors().size() == 0) {
            // Currently method parameters are not in the schedule, so print them separately here.
            for (ValueNode param : block.firstNode().graph().getNodes(LocalNode.class)) {
                printNode(param);
            }
        }
        if (block.firstNode() instanceof MergeNode) {
            // Currently phi functions are not in the schedule, so print them separately here.
            for (ValueNode phi : ((MergeNode) block.firstNode()).phis()) {
                printNode(phi);
            }
        }

        for (Node node : block.getInstructions()) {
            printNode(node);
        }
        out.enableIndentation();
        end("IR");
    }

    private void printNode(Node node) {
        if (node instanceof FixedWithNextNode) {
            out.print("f ").print(HOVER_START).print("#").print(HOVER_SEP).print("fixed with next").print(HOVER_END).println(COLUMN_END);
        } else if (node instanceof FixedNode) {
            out.print("f ").print(HOVER_START).print("*").print(HOVER_SEP).print("fixed").print(HOVER_END).println(COLUMN_END);
        } else if (node instanceof FloatingNode) {
            out.print("f ").print(HOVER_START).print("~").print(HOVER_SEP).print("floating").print(HOVER_END).println(COLUMN_END);
        }
        if (compilation.nodeOperands != null && node instanceof ValueNode) {
            CiValue operand = compilation.operand((ValueNode) node);
            if (operand != null) {
                out.print("result ").print(new OperandFormatter(false).format(operand)).println(COLUMN_END);
            }
        }
        out.print("tid ").print(nodeToString(node)).println(COLUMN_END);

        if (node instanceof StateSplit) {
            StateSplit stateSplit = (StateSplit) node;
            if (stateSplit.stateAfter() != null) {
                String state = stateToString(stateSplit.stateAfter(), null);
                out.print("st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).println(COLUMN_END);
            }
        }

        Map<Object, Object> props = new TreeMap<Object, Object>(node.getDebugProperties());
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
        if (!node.usages().isEmpty()) {
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

            if (pos.index != lastIndex) {
                if (lastIndex != -1) {
                    out.print(suffix);
                }
                out.print(prefix).print(node.getNodeClass().getName(pos)).print(": ");
                lastIndex = pos.index;
            }
            out.print(nodeToString(node.getNodeClass().get(node, pos))).print(" ");
        }
        if (lastIndex != -1) {
            out.print(suffix);
        }
    }

    private String stateToString(FrameState state, OperandFormatter operandFmt) {
        StringBuilder buf = new StringBuilder();
        do {
            buf.append(CiUtil.toLocation(state.method(), state.bci)).append('\n');

            if (state.stackSize() > 0) {
                buf.append("stack: ");
                for (int i = 0; i < state.stackSize(); i++) {
                    buf.append(stateValueToString(state.stackAt(i), operandFmt)).append(' ');
                }
                buf.append("\n");
            }

            if (state.locksSize() > 0) {
                buf.append("locks: ");
                for (int i = 0; i < state.locksSize(); ++i) {
                    buf.append(stateValueToString(state.lockAt(i), operandFmt)).append(' ');
                }
                buf.append("\n");
            }

            buf.append("locals: ");
            for (int i = 0; i < state.localsSize(); i++) {
                buf.append(stateValueToString(state.localAt(i), operandFmt)).append(' ');
            }
            buf.append("\n");

            state = state.outerFrameState();
        } while (state != null);

        return buf.toString();
    }

    private String stateValueToString(ValueNode value, OperandFormatter operandFmt) {
        if (operandFmt == null) {
            return nodeToString(value);
        }
        if (value == null) {
            return "-";
        }
        return operandFmt.format(compilation.operand(value));
    }

    /**
     * Prints the LIR for each instruction in a given block.
     *
     * @param block the block to print
     */
    private void printLIR(LIRBlock block) {
        List<LIRInstruction> lir = block.lir();
        if (lir == null) {
            return;
        }

        begin("IR");
        out.println("LIR");
        for (int i = 0; i < lir.size(); i++) {
            LIRInstruction inst = lir.get(i);
            out.printf("nr %4d ", inst.id()).print(COLUMN_END);

            if (inst.info != null) {
                int level = out.indentationLevel();
                out.adjustIndentation(-level);
                String state;
                if (inst.info.hasDebugInfo()) {
                    state = debugInfoToString(inst.info.debugInfo().codePos, inst.info.debugInfo().registerRefMap, inst.info.debugInfo().frameRefMap, new OperandFormatter(false), target.arch);
                } else {
                    state = debugInfoToString(inst.info.topFrame, null, null, new OperandFormatter(false), target.arch);
                }
                if (state != null) {
                    out.print(" st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).print(COLUMN_END);
                }
                out.adjustIndentation(level);
            }

            out.print(" instruction ").print(inst.toString(new OperandFormatter(false))).print(COLUMN_END);
            out.println(COLUMN_END);
        }
        end("IR");
    }

    private String nodeToString(Node node) {
        if (node == null) {
            return "-";
        }
        String prefix;
        if (node instanceof BeginNode && compilation != null && compilation.lir() == null) {
            prefix = "B";
        } else if (node instanceof ValueNode) {
            ValueNode value = (ValueNode) node;
            if (value.kind() == CiKind.Illegal) {
                prefix = "v";
            } else {
                prefix = String.valueOf(value.kind().typeChar);
            }
        } else {
            prefix = "?";
        }
        return prefix + node.toString(Verbosity.Id);
    }

    private String blockToString(Block block) {
        if (compilation != null && compilation.lir() == null) {
            // During all the front-end phases, the block schedule is built only for the debug output.
            // Therefore, the block numbers would be different for every CFG printed -> use the id of the first instruction.
            return "B" + block.firstNode().toString(Verbosity.Id);
        } else {
            // LIR instructions contain references to blocks and these blocks are printed as the blockID -> use the blockID.
            return "B" + block.blockID();
        }
    }


    public void printIntervals(String label, LinearScan allocator, Interval[] intervals) {
        begin("intervals");
        out.println(String.format("name \"%s\"", label));

        for (Interval interval : intervals) {
            if (interval != null) {
                printInterval(allocator, interval);
            }
        }

        end("intervals");
    }

    private void printInterval(LinearScan allocator, Interval interval) {
        out.printf("%s %s ", interval.operand.name(), (interval.operand.isRegister() ? "fixed" : interval.kind().name()));
        if (interval.operand.isRegister()) {
            out.printf("\"[%s|%c]\"", interval.operand.name(), interval.operand.kind.typeChar);
        } else {
            if (interval.location() != null) {
                out.printf("\"[%s|%c]\"", interval.location().name(), interval.location().kind.typeChar);
            }
        }

        Interval hint = interval.locationHint(false, allocator);
        out.printf("%s %s ", interval.splitParent().operand.name(), hint != null ? hint.operand.name() : -1);

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
}
