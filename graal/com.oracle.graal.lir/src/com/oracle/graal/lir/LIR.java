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
package com.oracle.graal.lir;

import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;

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
    private final BlockMap<List<ScheduledNode>> blockToNodesMap;

    /**
     * The linear-scan ordered list of blocks.
     */
    private final List<Block> linearScanOrder;

    /**
     * The order in which the code is emitted.
     */
    private final List<Block> codeEmittingOrder;

    /**
     * Various out-of-line stubs to be emitted near the end of the method
     * after all other LIR code has been emitted.
     */
    public final List<Code> stubs;

    private int numVariables;

    public SpillMoveFactory spillMoveFactory;

    public interface SpillMoveFactory {
        LIRInstruction createMove(Value result, Value input);
        LIRInstruction createExchange(Value input1, Value input2);
    }

    private boolean hasArgInCallerFrame;

    /**
     * An opaque chunk of machine code.
     */
    public interface Code {
        void emitCode(TargetMethodAssembler tasm);
        /**
         * A description of this code stub useful for commenting the code in a disassembly.
         */
        String description();
    }

    /**
     * Creates a new LIR instance for the specified compilation.
     * @param numLoops number of loops
     * @param compilation the compilation
     */
    public LIR(ControlFlowGraph cfg, BlockMap<List<ScheduledNode>> blockToNodesMap, List<Block> linearScanOrder, List<Block> codeEmittingOrder) {
        this.cfg = cfg;
        this.blockToNodesMap = blockToNodesMap;
        this.codeEmittingOrder = codeEmittingOrder;
        this.linearScanOrder = linearScanOrder;

        stubs = new ArrayList<>();
    }

    /**
     * Gets the nodes in a given block.
     */
    public List<ScheduledNode> nodesFor(Block block) {
        return blockToNodesMap.get(block);
    }

    /**
     * Determines if any instruction in the LIR has any debug info associated with it.
     */
    public boolean hasDebugInfo() {
        for (Block b : linearScanOrder()) {
            for (LIRInstruction op : b.lir) {
                if (op.info != null) {
                    return true;
                }
            }
        }
        return false;
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
        if (tasm.frameContext != null) {
            tasm.frameContext.enter(tasm);
        }

        for (Block b : codeEmittingOrder()) {
            emitBlock(tasm, b);
        }

        // generate code stubs
        for (Code c : stubs) {
            emitCodeStub(tasm, c);
        }
    }

    private static void emitBlock(TargetMethodAssembler tasm, Block block) {
        if (Debug.isDumpEnabled()) {
            tasm.blockComment(String.format("block B%d %s", block.getId(), block.getLoop()));
        }

        for (LIRInstruction op : block.lir) {
            if (Debug.isDumpEnabled()) {
                tasm.blockComment(String.format("%d %s", op.id(), op));
            }

            emitOp(tasm, op);
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

    private static void emitCodeStub(TargetMethodAssembler tasm, Code code) {
        if (Debug.isDumpEnabled()) {
            tasm.blockComment(String.format("code stub: %s", code.description()));
        }
        code.emitCode(tasm);
    }

    public void setHasArgInCallerFrame() {
        hasArgInCallerFrame = true;
    }

    /**
     * Determines if any of the parameters to the method are passed via the stack
     * where the parameters are located in the caller's frame.
     */
    public boolean hasArgInCallerFrame() {
        return hasArgInCallerFrame;
    }

/*
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
*/
}
