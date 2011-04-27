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
package com.sun.c1x.debug;

import java.io.*;
import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.alloc.*;
import com.sun.c1x.alloc.Interval.*;
import com.sun.c1x.graph.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.lir.LIRInstruction.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiAddress.*;
import com.sun.cri.ri.*;

/**
 * Utility for printing the control flow graph of a method being compiled by C1X at various compilation phases.
 * The output format matches that produced by HotSpot so that it can then be fed to the
 * <a href="https://c1visualizer.dev.java.net/">C1 Visualizer</a>.
 *
 * @author Doug Simon
 */
public class CFGPrinter {
    private static final String COLUMN_END = " <|@";
    private static final String HOVER_START = "<@";
    private static final String HOVER_SEP = "|@";
    private static final String HOVER_END = ">@";

    private static OutputStream cfgFileStream;

    /**
     * Gets the output stream  on the file "output.cfg" in the current working directory.
     * This stream is first opened if necessary.
     *
     * @return the output stream to "output.cfg" or {@code null} if there was an error opening this file for writing
     */
    public static synchronized OutputStream cfgFileStream() {
        if (cfgFileStream == null) {
            File cfgFile = new File("output.cfg");
            try {
                cfgFileStream = new FileOutputStream(cfgFile);
            } catch (FileNotFoundException e) {
                TTY.println("WARNING: Could not open " + cfgFile.getAbsolutePath());
            }
        }
        return cfgFileStream;
    }

    private final LogStream out;
    private final CiTarget target;

    /**
     * Creates a control flow graph printer.
     *
     * @param os where the output generated via this printer shown be written
     * @param target the target architecture description
     */
    public CFGPrinter(OutputStream os, CiTarget target) {
        out = new LogStream(os);
        this.target = target;
    }

    /**
     * Flushes all buffered output to the stream passed to {@link #CFGPrinter(OutputStream, CiTarget)}.
     */
    public void flush() {
        out.flush();
    }

    private void begin(String string) {
        out.println("begin_" + string);
        out.adjustIndentation(2);
    }

    private void end(String string) {
        out.adjustIndentation(-2);
        out.println("end_" + string);
    }

    /**
     * Prints a compilation timestamp for a given method.
     *
     * @param method the method for which a timestamp will be printed
     */
    public void printCompilation(RiMethod method) {
        begin("compilation");
        out.print("name \" ").print(CiUtil.format("%H::%n", method, true)).println('"');
        out.print("method \"").print(CiUtil.format("%f %r %H.%n(%p)", method, true)).println('"');
        out.print("date ").println(System.currentTimeMillis());
        end("compilation");
    }

    /**
     * Print the details of a given control flow graph block.
     *
     * @param block the block to print
     * @param successors the successor blocks of {@code block}
     * @param handlers the exception handler blocks of {@code block}
     * @param printHIR if {@code true} the HIR for each instruction in the block will be printed
     * @param printLIR if {@code true} the LIR for each instruction in the block will be printed
     */
    void printBlock(BlockBegin block, List<BlockBegin> successors, Iterable<BlockBegin> handlers, boolean printHIR, boolean printLIR) {
        begin("block");

        out.print("name \"B").print(block.blockID).println('"');
        out.print("from_bci ").println(block.bci());
        out.print("to_bci ").println(block.end() == null ? -1 : block.end().bci());

        out.print("predecessors ");
        for (BlockBegin pred : block.predecessors()) {
            out.print("\"B").print(pred.blockID).print("\" ");
        }
        out.println();

        out.print("successors ");
        for (BlockBegin succ : successors) {
            out.print("\"B").print(succ.blockID).print("\" ");
        }
        out.println();

        out.print("xhandlers");
        for (BlockBegin handler : handlers) {
            out.print("\"B").print(handler.blockID).print("\" ");
        }
        out.println();

        out.print("flags ");
        if (block.isStandardEntry()) {
            out.print("\"std\" ");
        }
        if (block.isOsrEntry()) {
            out.print("\"osr\" ");
        }
        if (block.isExceptionEntry()) {
            out.print("\"ex\" ");
        }
        if (block.isSubroutineEntry()) {
            out.print("\"sr\" ");
        }
        if (block.isBackwardBranchTarget()) {
            out.print("\"bb\" ");
        }
        if (block.isParserLoopHeader()) {
            out.print("\"plh\" ");
        }
        if (block.isCriticalEdgeSplit()) {
            out.print("\"ces\" ");
        }
        if (block.isLinearScanLoopHeader()) {
            out.print("\"llh\" ");
        }
        if (block.isLinearScanLoopEnd()) {
            out.print("\"lle\" ");
        }
        out.println();

        if (block.dominator() != null) {
            out.print("dominator \"B").print(block.dominator().blockID).println('"');
        }
        if (block.loopIndex() != -1) {
            out.print("loop_index ").println(block.loopIndex());
            out.print("loop_depth ").println(block.loopDepth());
        }

        if (printHIR) {
            printState(block);
            printHIR(block);
        }

        if (printLIR) {
            printLIR(block);
        }

        end("block");
    }

    /**
     * Prints the JVM frame state upon entry to a given block.
     *
     * @param block the block for which the frame state is to be printed
     */
    private void printState(BlockBegin block) {
        begin("states");

        FrameState state = block.stateBefore();

        do {
            int stackSize = state.stackSize();
            if (stackSize > 0) {
                begin("stack");
                out.print("size ").println(stackSize);
                out.print("method \"").print(CiUtil.toLocation(state.scope().method, state.bci)).println('"');

                int i = 0;
                while (i < stackSize) {
                    Value value = state.stackAt(i);
                    out.disableIndentation();
                    out.print(block.stateString(i, value));
                    printOperand(value);
                    out.println();
                    out.enableIndentation();
                    if (value == null) {
                        i++;
                    } else {
                        i += value.kind.sizeInSlots();
                    }
                }
                end("stack");
            }

            if (state.locksSize() > 0) {
                begin("locks");
                out.print("size ").println(state.locksSize());
                out.print("method \"").print(CiUtil.toLocation(state.scope().method, state.bci)).println('"');

                for (int i = 0; i < state.locksSize(); ++i) {
                    Value value = state.lockAt(i);
                    out.disableIndentation();
                    out.print(block.stateString(i, value));
                    printOperand(value);
                    out.println();
                    out.enableIndentation();
                }
                end("locks");
            }

            begin("locals");
            out.print("size ").println(state.localsSize());
            out.print("method \"").print(CiUtil.toLocation(state.scope().method, state.bci)).println('"');
            int i = 0;
            while (i < state.localsSize()) {
                Value value = state.localAt(i);
                if (value != null) {
                    out.disableIndentation();
                    out.print(block.stateString(i, value));
                    printOperand(value);
                    out.println();
                    out.enableIndentation();
                    // also ignore illegal HiWords
                    i += value.isIllegal() ? 1 : value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }
            state = state.callerState();
            end("locals");
        } while (state != null);

        end("states");
    }

    /**
     * Formats a given {@linkplain FrameState JVM frame state} as a multi line string.
     */
    private String stateToString(FrameState state, CFGOperandFormatter operandFmt) {
        if (state == null) {
            return null;
        }

        StringBuilder buf = new StringBuilder();

        do {
            buf.append(CiUtil.toLocation(state.scope().method, state.bci));
            buf.append('\n');
            if (state.stackSize() > 0) {
                int i = 0;
                buf.append("stack: ");
                while (i < state.stackSize()) {
                    if (i == 0) {
                        buf.append(' ');
                    }
                    Value value = state.stackAt(i);
                    buf.append(stateValueToString(value, operandFmt)).append(' ');
                    i++;
                }
                buf.append("\n");
            }

            if (state.locksSize() > 0) {
                buf.append("locks: ");
                for (int i = 0; i < state.locksSize(); ++i) {
                    if (i == 0) {
                        buf.append(' ');
                    }
                    Value value = state.lockAt(i);
                    buf.append(stateValueToString(value, operandFmt)).append(' ');
                }
                buf.append("\n");
            }

            buf.append("locals: ");
            int i = 0;
            while (i < state.localsSize()) {
                if (i == 0) {
                    buf.append(' ');
                }
                Value value = state.localAt(i);
                buf.append(stateValueToString(value, operandFmt)).append(' ');
                i++;
            }
            buf.append("\n");
            state = state.callerState();
        } while (state != null);
        return buf.toString();
    }

    private String stateValueToString(Value value, OperandFormatter operandFmt) {
        if (operandFmt == null) {
            return Util.valueString(value);
        }
        if (value == null) {
            return "-";
        }
        return operandFmt.format(value.operand());
    }

    private String stateValueToString(CiValue value, OperandFormatter operandFmt) {
        if (value == null) {
            return "-";
        }
        return operandFmt.format(value);
    }

    /**
     * Formats a given {@linkplain FrameState JVM frame state} as a multi line string.
     */
    private String debugInfoToString(CiDebugInfo info, CFGOperandFormatter operandFmt) {
        if (info == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();


        if (info.hasRegisterRefMap()) {
            sb.append("reg-ref-map:");
            C1XCompilation compilation = C1XCompilation.compilation();
            CiArchitecture arch = compilation == null ? null : compilation.target.arch;
            for (int reg = info.registerRefMap.nextSetBit(0); reg >= 0; reg = info.registerRefMap.nextSetBit(reg + 1)) {
                sb.append(' ').append(arch == null ? "reg" + reg : arch.registers[reg]);
            }
            sb.append("\n");
        }
        if (info.hasStackRefMap()) {
            sb.append("frame-ref-map:");
            CiBitMap bm = info.frameRefMap;
            for (int i = bm.nextSetBit(0); i >= 0; i = bm.nextSetBit(i + 1)) {
                sb.append(' ').append(CiStackSlot.get(CiKind.Object, i));
            }
            sb.append("\n");
        }
        if (info.codePos != null) {
            sb.append(stateToString(info.codePos, operandFmt));
        }
        return sb.toString();
    }

    /**
     * Formats a given {@linkplain FrameState JVM frame state} as a multi line string.
     */
    private String stateToString(CiCodePos codePos, CFGOperandFormatter operandFmt) {
        if (codePos == null) {
            return null;
        }

        StringBuilder buf = new StringBuilder();

        do {
            buf.append(CiUtil.toLocation(codePos.method, codePos.bci));
            buf.append('\n');
            if (codePos instanceof CiFrame) {
                CiFrame frame = (CiFrame) codePos;
                if (frame.numStack > 0) {
                    int i = 0;
                    buf.append("stack: ");
                    while (i < frame.numStack) {
                        if (i == 0) {
                            buf.append(' ');
                        }
                        CiValue value = frame.getStackValue(i);
                        buf.append(stateValueToString(value, operandFmt)).append(' ');
                        i++;
                    }
                    buf.append("\n");
                }

                if (frame.numLocks > 0) {
                    buf.append("locks: ");
                    for (int i = 0; i < frame.numLocks; ++i) {
                        if (i == 0) {
                            buf.append(' ');
                        }
                        CiValue value = frame.getLockValue(i);
                        buf.append(stateValueToString(value, operandFmt)).append(' ');
                    }
                    buf.append("\n");
                }

                buf.append("locals: ");
                int i = 0;
                while (i < frame.numLocals) {
                    if (i == 0) {
                        buf.append(' ');
                    }
                    CiValue value = frame.getLocalValue(i);
                    buf.append(stateValueToString(value, operandFmt)).append(' ');
                    i++;
                }
                buf.append("\n");
            }
            codePos = codePos.caller;
        } while (codePos != null);
        return buf.toString();
    }

    /**
     * Prints the HIR for each instruction in a given block.
     *
     * @param block
     */
    private void printHIR(BlockBegin block) {
        begin("IR");
        out.println("HIR");
        out.disableIndentation();
        for (Instruction i = block.next(); i != null; i = i.next()) {
            printInstructionHIR(i);
        }
        out.enableIndentation();
        end("IR");
    }

    /**
     * Formats LIR operands as expected by the C1 Visualizer.
     */
    public static class CFGOperandFormatter extends OperandFormatter {
        /**
         * The textual delimiters used for an operand depend on the context in which it is being
         * printed. When printed as part of a frame state or as the result operand in a HIR node listing,
         * it is enclosed in double-quotes (i.e. {@code "}'s).
         */
        public final boolean asStateOrHIROperandResult;

        public CFGOperandFormatter(boolean asStateOrHIROperandResult) {
            this.asStateOrHIROperandResult = asStateOrHIROperandResult;
        }

        @Override
        public String format(CiValue operand) {
            if (operand.isLegal()) {
                String op;
                if (operand.isVariableOrRegister() || operand.isStackSlot()) {
                    op = operand.name();
                } else if (operand.isConstant()) {
                    CiConstant constant = (CiConstant) operand;
                    op = operand.kind.javaName + ":" + operand.kind.format(constant.boxedValue());
                } else if (operand.isAddress()) {
                    CiAddress address = (CiAddress) operand;
                    op = "Base:" + format(address.base);
                    if (!address.index.isIllegal()) {
                        op += " Index:" + format(address.index);
                    }
                    if (address.scale != Scale.Times1) {
                        op += " * " + address.scale.value;
                    }
                    op += " Disp:" + address.displacement;
                } else {
                    assert operand.isIllegal();
                    op = "-";
                }
                if (operand.kind != CiKind.Illegal) {
                    op += "|" + operand.kind.typeChar;
                }
                if (asStateOrHIROperandResult) {
                    op = " \"" + op.replace('"', '\'') + "\" ";
                }
                return op;
            }
            return "";
        }
    }

    /**
     * Prints the LIR for each instruction in a given block.
     *
     * @param block the block to print
     */
    private void printLIR(BlockBegin block) {
        LIRList lir = block.lir();
        if (lir != null) {
            begin("IR");
            out.println("LIR");
            for (int i = 0; i < lir.length(); i++) {
                LIRInstruction inst = lir.at(i);
                out.printf("nr %4d ", inst.id).print(COLUMN_END);

                if (inst.info != null) {
                    int level = out.indentationLevel();
                    out.adjustIndentation(-level);
                    String state;
                    if (inst.info.debugInfo != null) {
                        // Use register-allocator output if available
                        state = debugInfoToString(inst.info.debugInfo, new CFGOperandFormatter(false));
                    } else {
                        state = stateToString(inst.info.state, new CFGOperandFormatter(false));
                    }
                    if (state != null) {
                        out.print(" st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).print(COLUMN_END);
                    }
                    out.adjustIndentation(level);
                }

                out.print(" instruction ").print(inst.toString(new CFGOperandFormatter(false))).print(COLUMN_END);
                out.println(COLUMN_END);
            }
            end("IR");
        }
    }

    private void printOperand(Value i) {
        if (i != null && i.operand().isLegal()) {
            out.print(new CFGOperandFormatter(true).format(i.operand()));
        }
    }

    /**
     * Prints the HIR for a given instruction.
     *
     * @param i the instruction for which HIR will be printed
     */
    private void printInstructionHIR(Instruction i) {
        out.print("bci ").print(i.bci()).println(COLUMN_END);
        if (i.operand().isLegal()) {
            out.print("result ").print(new CFGOperandFormatter(false).format(i.operand())).println(COLUMN_END);
        }
        out.print("tid ").print(i).println(COLUMN_END);

        String state = stateToString(i.stateBefore(), null);
        if (state != null) {
            out.print("st ").print(HOVER_START).print("st").print(HOVER_SEP).print(state).print(HOVER_END).println(COLUMN_END);
        }

        out.print("instruction ");
        i.print(out);
        out.print(COLUMN_END).print(' ').println(COLUMN_END);
    }

    /**
     * Prints the control flow graph denoted by a given block map.
     *
     * @param blockMap a data structure describing the blocks in a method and how they are connected
     * @param codeSize the bytecode size of the method from which {@code blockMap} was produced
     * @param label a label describing the compilation phase that produced the control flow graph
     * @param printHIR if {@code true} the HIR for each instruction in the block will be printed
     * @param printLIR if {@code true} the LIR for each instruction in the block will be printed
     */
    public void printCFG(RiMethod method, BlockMap blockMap, int codeSize, String label, boolean printHIR, boolean printLIR) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        for (int bci = 0; bci < codeSize; ++bci) {
            BlockBegin block = blockMap.get(bci);
            if (block != null) {
                printBlock(block, Arrays.asList(blockMap.getSuccessors(block)), blockMap.getHandlers(block), printHIR, printLIR);
            }
        }
        end("cfg");
    }

    /**
     * Prints the control flow graph rooted at a given block.
     *
     * @param startBlock the entry block of the control flow graph to be printed
     * @param label a label describing the compilation phase that produced the control flow graph
     * @param printHIR if {@code true} the HIR for each instruction in the block will be printed
     * @param printLIR if {@code true} the LIR for each instruction in the block will be printed
     */
    public void printCFG(BlockBegin startBlock, String label, final boolean printHIR, final boolean printLIR) {
        begin("cfg");
        out.print("name \"").print(label).println('"');
        startBlock.iteratePreOrder(new BlockClosure() {
            public void apply(BlockBegin block) {
                List<BlockBegin> successors = block.end() != null ? block.end().successors() : new ArrayList<BlockBegin>(0);
                printBlock(block, successors, block.exceptionHandlerBlocks(), printHIR, printLIR);
            }
        });
        end("cfg");
    }

    public void printIntervals(LinearScan allocator, Interval[] intervals, String name) {
        begin("intervals");
        out.println(String.format("name \"%s\"", name));

        for (Interval interval : intervals) {
            if (interval != null) {
                printInterval(allocator, interval);
            }
        }

        end("intervals");
    }

    private void printInterval(LinearScan allocator, Interval interval) {
        out.printf("%d %s ", interval.operandNumber, (interval.operand.isRegister() ? "fixed" : interval.kind().name()));
        if (interval.operand.isRegister()) {
            out.printf("\"[%s|%c]\"", interval.operand.name(), interval.operand.kind.typeChar);
        } else {
            if (interval.location() != null) {
                out.printf("\"[%s|%c]\"", interval.location().name(), interval.location().kind.typeChar);
            }
        }

        Interval hint = interval.locationHint(false, allocator);
        out.printf("%d %d ", interval.splitParent().operandNumber, hint != null ? hint.operandNumber : -1);

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

    public void printMachineCode(String code, String label) {
        if (code.length() == 0) {
            return;
        }
        if (label != null) {
            begin("cfg");
            out.print("name \"").print(label).println('"');
            end("cfg");
        }
        begin("nmethod");
        out.print(code);
        out.println(" <|@");
        end("nmethod");
    }
}
