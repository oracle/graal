/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.cfg.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;

/**
 * This class implements the overall container for the LIR graph
 * and directs its construction, optimization, and finalization.
 */
public class LIR {

    public final ControlFlowGraph cfg;

    /**
     * The nodes for the blocks.
     * TODO: This should go away, we want all nodes connected with a next-pointer.
     */
    private final BlockMap<List<Node>> nodesFor;

    /**
     * The linear-scan ordered list of blocks.
     */
    private final List<Block> linearScanOrder;

    /**
     * The order in which the code is emitted.
     */
    private final List<Block> codeEmittingOrder;


    public final List<SlowPath> slowPaths;

    public final List<SlowPath> deoptimizationStubs;

    /**
     * The last slow path emitted, which can be used emit marker bytes.
     */
    public SlowPath methodEndMarker;

    private int numVariables;

    public SpillMoveFactory spillMoveFactory;

    public interface SpillMoveFactory {
        LIRInstruction createMove(CiValue result, CiValue input);
        LIRInstruction createExchange(CiValue input1, CiValue input2);
    }

    public interface SlowPath {
        void emitCode(TargetMethodAssembler tasm);
    }

    /**
     * Creates a new LIR instance for the specified compilation.
     * @param numLoops number of loops
     * @param compilation the compilation
     */
    public LIR(ControlFlowGraph cfg, BlockMap<List<Node>> nodesFor, List<Block> linearScanOrder, List<Block> codeEmittingOrder) {
        this.cfg = cfg;
        this.nodesFor = nodesFor;
        this.codeEmittingOrder = codeEmittingOrder;
        this.linearScanOrder = linearScanOrder;

        slowPaths = new ArrayList<>();
        deoptimizationStubs = new ArrayList<>();
    }

    public List<Node> nodesFor(Block block) {
        return nodesFor.get(block);
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<Block> linearScanOrder() {
        return linearScanOrder;
    }

    public List<Block> codeEmittingOrder() {
        return codeEmittingOrder;
    }

    public int numVariables() {
        return numVariables;
    }

    public int nextVariable() {
        return numVariables++;
    }

    public void emitCode(TargetMethodAssembler tasm) {
        if (GraalOptions.PrintLIR && !TTY.isSuppressed()) {
            printLIR(codeEmittingOrder());
        }

        for (Block b : codeEmittingOrder()) {
            emitBlock(tasm, b);
        }

        // generate code for slow cases
        for (SlowPath sp : slowPaths) {
            emitSlowPath(tasm, sp);
        }
        // generate deoptimization stubs
        for (SlowPath sp : deoptimizationStubs) {
            emitSlowPath(tasm, sp);
        }
        // generate traps at the end of the method
        emitSlowPath(tasm, methodEndMarker);
    }

    private void emitBlock(TargetMethodAssembler tasm, Block block) {
        if (GraalOptions.PrintLIRWithAssembly) {
            TTY.println(block.toString());
        }

        if (GraalOptions.CommentedAssembly) {
            tasm.blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        for (LIRInstruction op : block.lir) {
            if (GraalOptions.CommentedAssembly) {
                tasm.blockComment(String.format("%d %s", op.id(), op));
            }
            if (GraalOptions.PrintLIRWithAssembly && !TTY.isSuppressed()) {
                // print out the LIR operation followed by the resulting assembly
                TTY.println(op.toStringWithIdPrefix());
                TTY.println();
            }

            emitOp(tasm, op);

            if (GraalOptions.PrintLIRWithAssembly) {
                printAssembly(tasm);
            }
        }
    }

    private static void emitOp(TargetMethodAssembler tasm, LIRInstruction op) {
        try {
            try {
                op.emitCode(tasm);
            } catch (AssertionError t) {
                throw new GraalInternalError(t);
            } catch (RuntimeException t) {
                throw new GraalInternalError(t);
            }
        } catch (GraalInternalError e) {
            throw e.addContext("lir instruction", op);
        }
    }

    private static void emitSlowPath(TargetMethodAssembler tasm, SlowPath sp) {
        if (GraalOptions.CommentedAssembly) {
            tasm.blockComment(String.format("slow case %s", sp.getClass().getName()));
        }
        sp.emitCode(tasm);
    }

    private int lastDecodeStart;

    private void printAssembly(TargetMethodAssembler tasm) {
        byte[] currentBytes = tasm.asm.codeBuffer.copyData(lastDecodeStart, tasm.asm.codeBuffer.position());
        if (currentBytes.length > 0) {
            String disasm = tasm.runtime.disassemble(currentBytes, lastDecodeStart);
            if (disasm.length() != 0) {
                TTY.println(disasm);
            } else {
                TTY.println("Code [+%d]: %d bytes", lastDecodeStart, currentBytes.length);
                Util.printBytes(lastDecodeStart, currentBytes, GraalOptions.PrintAssemblyBytesPerLine);
            }
        }
        lastDecodeStart = tasm.asm.codeBuffer.position();
    }


    public static void printBlock(Block x) {
        // print block id
        TTY.print("B%d ", x.getId());

        // print flags
        if (x.isLoopHeader()) {
            TTY.print("lh ");
        }
        if (x.isLoopEnd()) {
            TTY.print("le ");
        }

        // print block bci range
        TTY.print("[%d, %d] ", -1, -1);

        // print predecessors and successors
        if (x.numberOfPreds() > 0) {
            TTY.print("preds: ");
            for (int i = 0; i < x.numberOfPreds(); i++) {
                TTY.print("B%d ", x.predAt(i).getId());
            }
        }

        if (x.numberOfSux() > 0) {
            TTY.print("sux: ");
            for (int i = 0; i < x.numberOfSux(); i++) {
                TTY.print("B%d ", x.suxAt(i).getId());
            }
        }

        TTY.println();
    }

    public static void printLIR(List<Block> blocks) {
        if (TTY.isSuppressed()) {
            return;
        }
        TTY.println("LIR:");
        int i;
        for (i = 0; i < blocks.size(); i++) {
            Block bb = blocks.get(i);
            printBlock(bb);
            TTY.println("__id_Instruction___________________________________________");
            for (LIRInstruction op : bb.lir) {
                TTY.println(op.toStringWithIdPrefix());
                TTY.println();
            }
            TTY.println();
        }
    }
}
