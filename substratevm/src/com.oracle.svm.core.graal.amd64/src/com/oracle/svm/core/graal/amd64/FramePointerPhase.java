/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.amd64;

import java.util.ArrayList;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.graal.code.SubstrateBackend.SubstrateMarkId;

import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInsertionBuffer;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.framemap.FrameMapBuilderTool;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.PreAllocationOptimizationPhase;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;

/**
 * This phase ensures that methods that modify the stack pointer in a non-standard way have a frame
 * pointer, which is necessary to properly support stack unwinding.
 *
 * @see LIRInstruction#modifiesStackPointer
 */
public class FramePointerPhase extends PreAllocationOptimizationPhase {
    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PreAllocationOptimizationContext context) {
        if (!isSupported(lirGenRes)) {
            return;
        }

        LIR lir = lirGenRes.getLIR();
        if (!modifiesStackPointer(lir)) {
            return;
        }

        /*
         * The stack pointer is being modified in a way that requires the use of a frame pointer, so
         * for this method we configure the register allocator to preserve rbp, and the method's
         * prologue and epilogue to use rbp as the frame pointer.
         */
        ((SubstrateAMD64Backend.SubstrateAMD64RegisterAllocationConfig) lirGenRes.getRegisterAllocationConfig()).setPreserveFramePointer();
        ((SubstrateAMD64Backend.SubstrateAMD64FrameMap) ((FrameMapBuilderTool) lirGenRes.getFrameMapBuilder()).getFrameMap()).setNeedsFramePointer();

        /*
         * Additionally, if rbp is not globally preserved, we must also preserve it around calls
         * that may destroy it.
         */
        if (!SubstrateOptions.PreserveFramePointer.getValue()) {
            LIRInsertionBuffer buffer = new LIRInsertionBuffer();
            for (int blockId : lir.getBlocks()) {
                if (LIR.isBlockDeleted(blockId)) {
                    continue;
                }
                BasicBlock<?> block = lir.getBlockById(blockId);
                ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);

                /*
                 * Note that we need to restore rbp after both calls and exceptions, so for
                 * simplicity we spill it before each call (not just the ones that might destroy it)
                 * because then we can simply reload it in each exception handler.
                 */
                buffer.init(instructions);
                if (block.isExceptionEntry()) {
                    buffer.append(lirGenRes.getFirstInsertPosition(), new SpillFramePointerOp(true));
                    buffer.append(lirGenRes.getFirstInsertPosition(), new ReloadFramePointerOp());
                }
                for (int i = 0; i < instructions.size(); i++) {
                    if (instructions.get(i) instanceof AMD64Call.CallOp callOp) {
                        buffer.append(i, new SpillFramePointerOp());
                        buffer.append(i + 1, new ReloadFramePointerOp(!callOp.destroysCallerSavedRegisters()));
                    }
                }
                buffer.finish();
            }
        }
    }

    static boolean isSupported(LIRGenerationResult lirGenRes) {
        /*
         * JIT compilation and deopt targets are not supported, see GR-64771. For these unsupported
         * methods, a base pointer is not used, even if there are LIR instructions that modify the
         * stack pointer directly.
         */
        SubstrateAMD64Backend.SubstrateLIRGenerationResult result = (SubstrateAMD64Backend.SubstrateLIRGenerationResult) lirGenRes;
        return SubstrateUtil.HOSTED && !result.getMethod().isDeoptTarget();
    }

    /** Returns true if any LIR instruction modifies the stack pointer, false otherwise. */
    private static boolean modifiesStackPointer(LIR lir) {
        for (int blockId : lir.getBlocks()) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (LIRInstruction op : lir.getLIRforBlock(block)) {
                if (op.modifiesStackPointer()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Opcode("SPILL_FRAME_POINTER")
    public static class SpillFramePointerOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<SpillFramePointerOp> TYPE = LIRInstructionClass.create(SpillFramePointerOp.class);

        private final boolean recordMarkOnly;

        SpillFramePointerOp() {
            this(false);
        }

        SpillFramePointerOp(boolean recordMarkOnly) {
            super(TYPE);
            this.recordMarkOnly = recordMarkOnly;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (!recordMarkOnly) {
                var frameMap = (SubstrateAMD64Backend.SubstrateAMD64FrameMap) crb.frameMap;
                masm.movq(masm.makeAddress(AMD64.rsp, frameMap.getFramePointerSaveAreaOffset()), AMD64.rbp);
            }
            crb.recordMark(SubstrateMarkId.FRAME_POINTER_SPILLED);
        }
    }

    @Opcode("RELOAD_FRAME_POINTER")
    public static class ReloadFramePointerOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<ReloadFramePointerOp> TYPE = LIRInstructionClass.create(ReloadFramePointerOp.class);

        private final boolean recordMarkOnly;

        ReloadFramePointerOp() {
            this(false);
        }

        ReloadFramePointerOp(boolean recordMarkOnly) {
            super(TYPE);
            this.recordMarkOnly = recordMarkOnly;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (!recordMarkOnly) {
                var frameMap = (SubstrateAMD64Backend.SubstrateAMD64FrameMap) crb.frameMap;
                masm.movq(AMD64.rbp, masm.makeAddress(AMD64.rsp, frameMap.getFramePointerSaveAreaOffset()));
            }
            crb.recordMark(SubstrateMarkId.FRAME_POINTER_RELOADED);
        }
    }
}
