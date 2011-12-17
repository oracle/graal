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
package com.oracle.max.graal.compiler.lir;

import java.util.*;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;

/**
 * This class implements the overall container for the LIR graph
 * and directs its construction, optimization, and finalization.
 */
public class LIR {

    /**
     * The start block of this LIR.
     */
    private final LIRBlock startBlock;

    /**
     * The linear-scan ordered list of blocks.
     */
    private final List<LIRBlock> linearScanOrder;

    /**
     * The order in which the code is emitted.
     */
    private final List<LIRBlock> codeEmittingOrder;

    private final NodeMap<LIRBlock> valueToBlock;


    public final List<SlowPath> slowPaths;

    public final List<SlowPath> deoptimizationStubs;

    /**
     * The last slow path emitted, which can be used emit marker bytes.
     */
    public SlowPath methodEndMarker;


    public interface SlowPath {
        void emitCode(TargetMethodAssembler tasm);
    }

    /**
     * Creates a new LIR instance for the specified compilation.
     * @param compilation the compilation
     */
    public LIR(LIRBlock startBlock, List<LIRBlock> linearScanOrder, List<LIRBlock> codeEmittingOrder, NodeMap<LIRBlock> valueToBlock) {
        this.codeEmittingOrder = codeEmittingOrder;
        this.linearScanOrder = linearScanOrder;
        this.startBlock = startBlock;
        this.valueToBlock = valueToBlock;

        slowPaths = new ArrayList<SlowPath>();
        deoptimizationStubs = new ArrayList<SlowPath>();
    }

    /**
     * Gets the linear scan ordering of blocks as a list.
     * @return the blocks in linear scan order
     */
    public List<LIRBlock> linearScanOrder() {
        return linearScanOrder;
    }

    public List<LIRBlock> codeEmittingOrder() {
        return codeEmittingOrder;
    }

    public LIRBlock startBlock() {
        return startBlock;
    }

    public NodeMap<LIRBlock> valueToBlock() {
        return valueToBlock;
    }


    public void emitCode(TargetMethodAssembler tasm) {
        if (GraalOptions.PrintLIR && !TTY.isSuppressed()) {
            printLIR(codeEmittingOrder());
        }

        for (LIRBlock b : codeEmittingOrder()) {
            emitBlock(tasm, b);
        }

        // generate code for slow cases
        for (SlowPath sp : slowPaths) {
            sp.emitCode(tasm);
        }
        // generate deoptimization stubs
        for (SlowPath sp : deoptimizationStubs) {
            sp.emitCode(tasm);
        }
        // generate traps at the end of the method
        methodEndMarker.emitCode(tasm);
    }

    private void emitBlock(TargetMethodAssembler tasm, LIRBlock block) {
        if (GraalOptions.PrintLIRWithAssembly) {
            block.printWithoutPhis(TTY.out());
        }

        if (GraalOptions.CommentedAssembly) {
            String st = String.format(" block B%d", block.blockID());
            tasm.blockComment(st);
        }

        for (LIRInstruction op : block.lir()) {
            if (GraalOptions.CommentedAssembly) {
                // Only print out branches
                if (op.code instanceof LIRBranch) {
                    tasm.blockComment(op.toStringWithIdPrefix());
                }
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

    private void emitOp(TargetMethodAssembler tasm, LIRInstruction op) {
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

    private int lastDecodeStart;

    private void printAssembly(TargetMethodAssembler tasm) {
        byte[] currentBytes = tasm.asm.codeBuffer.copyData(lastDecodeStart, tasm.asm.codeBuffer.position());
        if (currentBytes.length > 0) {
            String disasm = tasm.compilation.compiler.runtime.disassemble(currentBytes, lastDecodeStart);
            if (disasm.length() != 0) {
                TTY.println(disasm);
            } else {
                TTY.println("Code [+%d]: %d bytes", lastDecodeStart, currentBytes.length);
                Util.printBytes(lastDecodeStart, currentBytes, GraalOptions.PrintAssemblyBytesPerLine);
            }
        }
        lastDecodeStart = tasm.asm.codeBuffer.position();
    }


    public static void printBlock(LIRBlock x) {
        // print block id
        TTY.print("B%d ", x.blockID());

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
                TTY.print("B%d ", x.predAt(i).blockID());
            }
        }

        if (x.numberOfSux() > 0) {
            TTY.print("sux: ");
            for (int i = 0; i < x.numberOfSux(); i++) {
                TTY.print("B%d ", x.suxAt(i).blockID());
            }
        }

        TTY.println();
    }

    public static void printLIR(List<LIRBlock> blocks) {
        if (TTY.isSuppressed()) {
            return;
        }
        TTY.println("LIR:");
        int i;
        for (i = 0; i < blocks.size(); i++) {
            LIRBlock bb = blocks.get(i);
            printBlock(bb);
            TTY.println("__id_Instruction___________________________________________");
            for (LIRInstruction op : bb.lir()) {
                TTY.println(op.toStringWithIdPrefix());
                TTY.println();
            }
            TTY.println();
        }
    }
}
