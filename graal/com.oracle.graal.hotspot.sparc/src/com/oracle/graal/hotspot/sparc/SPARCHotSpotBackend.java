/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.sparc;

import static com.oracle.graal.api.code.CallingConvention.Type.*;
import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.sparc.SPARC.*;
import static com.oracle.graal.compiler.common.UnsafeAccess.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpne;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.Ldx;
import com.oracle.graal.asm.sparc.SPARCAssembler.Save;
import com.oracle.graal.asm.sparc.SPARCAssembler.Stx;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Cmp;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Nop;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.RestoreWindow;
import com.oracle.graal.asm.sparc.SPARCMacroAssembler.Setx;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.HotSpotCodeCacheProvider.MarkId;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.stubs.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.sparc.*;
import com.oracle.graal.lir.sparc.SPARCCall.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.sparc.*;

/**
 * HotSpot SPARC specific backend.
 */
public class SPARCHotSpotBackend extends HotSpotHostBackend {

    public SPARCHotSpotBackend(HotSpotGraalRuntime runtime, HotSpotProviders providers) {
        super(runtime, providers);
    }

    @Override
    public boolean shouldAllocateRegisters() {
        return true;
    }

    @Override
    public FrameMap newFrameMap(RegisterConfig registerConfig) {
        return new SPARCFrameMap(getProviders().getCodeCache(), registerConfig);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(CallingConvention cc, LIRGenerationResult lirGenRes) {
        return new SPARCHotSpotLIRGenerator(getProviders(), getRuntime().getConfig(), cc, lirGenRes);
    }

    @Override
    public LIRGenerationResult newLIRGenerationResult(LIR lir, FrameMap frameMap, ResolvedJavaMethod method, Object stub) {
        return new SPARCHotSpotLIRGenerationResult(lir, frameMap, stub);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new SPARCHotSpotNodeLIRBuilder(graph, lirGen);
    }

    /**
     * Emits code to do stack overflow checking.
     *
     * @param afterFrameInit specifies if the stack pointer has already been adjusted to allocate
     *            the current frame
     */
    protected static void emitStackOverflowCheck(CompilationResultBuilder crb, int pagesToBang, boolean afterFrameInit) {
        if (pagesToBang > 0) {
            SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
            final int frameSize = crb.frameMap.totalFrameSize();
            if (frameSize > 0) {
                int lastFramePage = frameSize / unsafe.pageSize();
                // emit multiple stack bangs for methods with frames larger than a page
                for (int i = 0; i <= lastFramePage; i++) {
                    int disp = (i + pagesToBang) * unsafe.pageSize();
                    if (afterFrameInit) {
                        disp -= frameSize;
                    }
                    crb.blockComment("[stack overflow check]");
                    // Use SPARCAddress to get the final displacement including the stack bias.
                    SPARCAddress address = new SPARCAddress(sp, -disp);
                    if (SPARCAssembler.isSimm13(address.getDisplacement())) {
                        new Stx(g0, address).emit(masm);
                    } else {
                        try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                            Register scratch = sc.getRegister();
                            new Setx(address.getDisplacement(), scratch).emit(masm);
                            new Stx(g0, new SPARCAddress(sp, scratch)).emit(masm);
                        }
                    }
                }
            }
        }
    }

    public class HotSpotFrameContext implements FrameContext {

        final boolean isStub;

        HotSpotFrameContext(boolean isStub) {
            this.isStub = isStub;
        }

        public boolean hasFrame() {
            return true;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            final int frameSize = crb.frameMap.totalFrameSize();
            final int stackpoinerChange = -frameSize;
            SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
            if (!isStub && pagesToBang > 0) {
                emitStackOverflowCheck(crb, pagesToBang, false);
            }

            if (SPARCAssembler.isSimm13(stackpoinerChange)) {
                new Save(sp, stackpoinerChange, sp).emit(masm);
            } else {
                try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                    Register scratch = sc.getRegister();
                    new Setx(stackpoinerChange, scratch).emit(masm);
                    new Save(sp, scratch, sp).emit(masm);
                }
            }

            if (ZapStackOnMethodEntry.getValue()) {
                final int slotSize = 8;
                for (int i = 0; i < frameSize / slotSize; ++i) {
                    // 0xC1C1C1C1
                    new Stx(g0, new SPARCAddress(sp, i * slotSize)).emit(masm);
                }
            }
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
            new RestoreWindow().emit(masm);
        }
    }

    @Override
    protected Assembler createAssembler(FrameMap frameMap) {
        return new SPARCMacroAssembler(getTarget(), frameMap.registerConfig);
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRes, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        SPARCHotSpotLIRGenerationResult gen = (SPARCHotSpotLIRGenerationResult) lirGenRes;
        FrameMap frameMap = gen.getFrameMap();
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";

        Stub stub = gen.getStub();
        Assembler masm = createAssembler(frameMap);
        // On SPARC we always use stack frames.
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null);
        CompilationResultBuilder crb = factory.createBuilder(getProviders().getCodeCache(), getProviders().getForeignCalls(), frameMap, masm, frameContext, compilationResult);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        StackSlot deoptimizationRescueSlot = gen.getDeoptimizationRescueSlot();
        if (deoptimizationRescueSlot != null && stub == null) {
            crb.compilationResult.setCustomStackAreaOffset(frameMap.offsetForStackSlot(deoptimizationRescueSlot));
        }

        if (stub != null) {
            // Even on sparc we need to save floating point registers
            Set<Register> definedRegisters = gatherDefinedRegisters(lir);
            Map<LIRFrameState, SaveRegistersOp> calleeSaveInfo = gen.getCalleeSaveInfo();
            updateStub(stub, definedRegisters, calleeSaveInfo, frameMap);
        }

        return crb;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        fixupDelayedInstructions(lir);
        SPARCMacroAssembler masm = (SPARCMacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.registerConfig;
        HotSpotVMConfig config = getRuntime().getConfig();
        Label unverifiedStub = installedCodeOwner == null || installedCodeOwner.isStatic() ? null : new Label();

        // Emit the prefix

        if (unverifiedStub != null) {
            MarkId.recordMark(crb, MarkId.UNVERIFIED_ENTRY);
            // We need to use JavaCall here because we haven't entered the frame yet.
            CallingConvention cc = regConfig.getCallingConvention(JavaCall, null, new JavaType[]{getProviders().getMetaAccess().lookupJavaType(Object.class)}, getTarget(), false);
            Register inlineCacheKlass = g5; // see MacroAssembler::ic_call

            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch = sc.getRegister();
                Register receiver = asRegister(cc.getArgument(0));
                SPARCAddress src = new SPARCAddress(receiver, config.hubOffset);

                new Ldx(src, scratch).emit(masm);
                new Cmp(scratch, inlineCacheKlass).emit(masm);
            }
            new Bpne(CC.Xcc, unverifiedStub).emit(masm);
            new Nop().emit(masm);  // delay slot
        }

        masm.align(config.codeEntryAlignment);
        MarkId.recordMark(crb, MarkId.OSR_ENTRY);
        MarkId.recordMark(crb, MarkId.VERIFIED_ENTRY);

        // Emit code for the LIR
        crb.emit(lir);

        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        HotSpotForeignCallsProvider foreignCalls = getProviders().getForeignCalls();
        if (!frameContext.isStub) {
            MarkId.recordMark(crb, MarkId.EXCEPTION_HANDLER_ENTRY);
            SPARCCall.directCall(crb, masm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), null, false, null);
            MarkId.recordMark(crb, MarkId.DEOPT_HANDLER_ENTRY);
            SPARCCall.directCall(crb, masm, foreignCalls.lookupForeignCall(DEOPTIMIZATION_HANDLER), null, false, null);
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
        }

        if (unverifiedStub != null) {
            masm.bind(unverifiedStub);
            try (SPARCScratchRegister sc = SPARCScratchRegister.get()) {
                Register scratch = sc.getRegister();
                SPARCCall.indirectJmp(crb, masm, scratch, foreignCalls.lookupForeignCall(IC_MISS_HANDLER));
            }
        }
    }

    private static void fixupDelayedInstructions(LIR l) {
        for (AbstractBlock<?> b : l.codeEmittingOrder()) {
            fixupDelayedInstructions(l, b);
        }
    }

    private static void fixupDelayedInstructions(LIR l, AbstractBlock<?> block) {
        TailDelayedLIRInstruction lastDelayable = null;
        for (LIRInstruction inst : l.getLIRforBlock(block)) {
            if (lastDelayable != null && inst instanceof DelaySlotHolder) {
                if (isDelayable(inst, (LIRInstruction) lastDelayable)) {
                    lastDelayable.setDelaySlotHolder((DelaySlotHolder) inst);
                }
                lastDelayable = null; // We must not pull over other delay slot holder.
            } else if (inst instanceof TailDelayedLIRInstruction) {
                lastDelayable = (TailDelayedLIRInstruction) inst;
            } else {
                lastDelayable = null;
            }
        }
    }

    public static boolean isDelayable(final LIRInstruction delaySlotHolder, final LIRInstruction other) {
        final Set<Value> delaySlotHolderInputs = new HashSet<>(2);
        final Set<LIRFrameState> otherFrameStates = new HashSet<>(2);
        other.forEachState(new InstructionStateProcedure() {
            @Override
            protected void doState(LIRInstruction instruction, LIRFrameState state) {
                otherFrameStates.add(state);
            }
        });
        int frameStatesBefore = otherFrameStates.size();
        delaySlotHolder.forEachState(new InstructionStateProcedure() {
            @Override
            protected void doState(LIRInstruction instruction, LIRFrameState state) {
                otherFrameStates.add(state);
            }
        });
        if (frameStatesBefore != otherFrameStates.size() && otherFrameStates.size() >= 2) {
            // both have framestates, the instruction is not delayable
            return false;
        }
        // Direct calls do not have dependencies to data before
        if (delaySlotHolder instanceof DirectCallOp) {
            return true;
        }
        delaySlotHolder.visitEachInput(new InstructionValueConsumer() {
            @Override
            protected void visitValue(LIRInstruction instruction, Value value) {
                delaySlotHolderInputs.add(value);
            }
        });
        delaySlotHolder.visitEachTemp(new InstructionValueConsumer() {
            @Override
            protected void visitValue(LIRInstruction instruction, Value value) {
                delaySlotHolderInputs.add(value);
            }
        });
        if (delaySlotHolderInputs.size() == 0) {
            return true;
        }
        final Set<Value> otherOutputs = new HashSet<>();
        other.visitEachOutput(new InstructionValueConsumer() {
            @Override
            protected void visitValue(LIRInstruction instruction, Value value) {
                otherOutputs.add(value);
            }
        });
        other.visitEachTemp(new InstructionValueConsumer() {
            @Override
            protected void visitValue(LIRInstruction instruction, Value value) {
                otherOutputs.add(value);
            }
        });
        int sizeBefore = otherOutputs.size();
        otherOutputs.removeAll(delaySlotHolderInputs);
        return otherOutputs.size() == sizeBefore;
    }
}
