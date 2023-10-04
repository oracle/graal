/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.amd64;

import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.compiler.graal.core.common.GraalOptions.ZapStackOnMethodEntry;

import jdk.compiler.graal.asm.Label;
import jdk.compiler.graal.asm.amd64.AMD64Address;
import jdk.compiler.graal.asm.amd64.AMD64Assembler;
import jdk.compiler.graal.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.compiler.graal.asm.amd64.AMD64BaseAssembler;
import jdk.compiler.graal.asm.amd64.AMD64MacroAssembler;
import jdk.compiler.graal.code.CompilationResult;
import jdk.compiler.graal.core.amd64.AMD64NodeMatchRules;
import jdk.compiler.graal.core.common.NumUtil;
import jdk.compiler.graal.core.common.alloc.RegisterAllocationConfig;
import jdk.compiler.graal.core.common.spi.ForeignCallLinkage;
import jdk.compiler.graal.core.gen.LIRGenerationProvider;
import jdk.compiler.graal.debug.DebugContext;
import jdk.compiler.graal.debug.GraalError;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotDataBuilder;
import jdk.compiler.graal.hotspot.HotSpotGraalRuntimeProvider;
import jdk.compiler.graal.hotspot.HotSpotHostBackend;
import jdk.compiler.graal.hotspot.HotSpotLIRGenerationResult;
import jdk.compiler.graal.hotspot.HotSpotMarkId;
import jdk.compiler.graal.hotspot.meta.HotSpotForeignCallsProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.stubs.Stub;
import jdk.compiler.graal.lir.LIR;
import jdk.compiler.graal.lir.amd64.AMD64Call;
import jdk.compiler.graal.lir.amd64.AMD64FrameMap;
import jdk.compiler.graal.lir.asm.CompilationResultBuilder;
import jdk.compiler.graal.lir.asm.CompilationResultBuilderFactory;
import jdk.compiler.graal.lir.asm.DataBuilder;
import jdk.compiler.graal.lir.asm.EntryPointDecorator;
import jdk.compiler.graal.lir.asm.FrameContext;
import jdk.compiler.graal.lir.framemap.FrameMap;
import jdk.compiler.graal.lir.framemap.FrameMapBuilder;
import jdk.compiler.graal.lir.gen.LIRGenerationResult;
import jdk.compiler.graal.lir.gen.LIRGeneratorTool;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.spi.NodeLIRBuilderTool;
import jdk.compiler.graal.options.OptionValues;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * HotSpot AMD64 specific backend.
 */
public class AMD64HotSpotBackend extends HotSpotHostBackend implements LIRGenerationProvider {

    public AMD64HotSpotBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(config, runtime, providers);
    }

    @Override
    protected FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        AMD64FrameMap frameMap = new AMD64HotSpotFrameMap(getCodeCache(), registerConfigNonNull, this, config);
        return new AMD64HotSpotFrameMapBuilder(frameMap, getCodeCache(), registerConfigNonNull);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new AMD64HotSpotLIRGenerator(getProviders(), config, lirGenRes);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new AMD64HotSpotNodeLIRBuilder(graph, lirGen, new AMD64NodeMatchRules(lirGen));
    }

    @Override
    protected void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        int pos = asm.position();
        asm.movl(new AMD64Address(rsp, -bangOffset), AMD64.rax);
        assert asm.position() - pos >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
    }

    /**
     * The size of the instruction used to patch the verified entry point of an nmethod when the
     * nmethod is made non-entrant or a zombie (e.g. during deopt or class unloading). The first
     * instruction emitted at an nmethod's verified entry point must be at least this length to
     * ensure mt-safe patching.
     */
    public static final int PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE = 5;

    /**
     * Emits code at the verified entry point and return point(s) of a method.
     */
    public class HotSpotFrameContext implements FrameContext {

        final boolean isStub;

        HotSpotFrameContext(boolean isStub) {
            this.isStub = isStub;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            AMD64FrameMap frameMap = (AMD64FrameMap) crb.frameMap;
            int frameSize = frameMap.frameSize();
            AMD64HotSpotMacroAssembler asm = (AMD64HotSpotMacroAssembler) crb.asm;

            int verifiedEntryPointOffset = asm.position();
            if (!isStub) {
                emitStackOverflowCheck(crb);
                // assert asm.position() - verifiedEntryPointOffset >=
                // PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
            }
            if (frameMap.preserveFramePointer()) {
                // Stack-walking friendly instructions
                asm.push(rbp);
                asm.movq(rbp, rsp);
            }
            if (!isStub && asm.position() == verifiedEntryPointOffset) {
                asm.subqWide(rsp, frameSize);
                assert asm.position() - verifiedEntryPointOffset >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE;
            } else {
                asm.decrementq(rsp, frameSize);
            }

            assert frameMap.getRegisterConfig().getCalleeSaveRegisters() == null;

            if (!isStub && config.nmethodEntryBarrier != 0) {
                emitNmethodEntryBarrier(crb, asm);
            } else {
                crb.recordMark(HotSpotMarkId.FRAME_COMPLETE);
            }

            if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                final int intSize = 4;
                for (int i = 0; i < frameSize / intSize; ++i) {
                    asm.movl(new AMD64Address(rsp, i * intSize), 0xC1C1C1C1);
                }
            }
        }

        private void emitNmethodEntryBarrier(CompilationResultBuilder crb, AMD64HotSpotMacroAssembler asm) {
            GraalError.guarantee(HotSpotMarkId.ENTRY_BARRIER_PATCH.isAvailable(), "must be available");
            ForeignCallLinkage callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.NMETHOD_ENTRY_BARRIER);

            // The assembly sequence is from
            // src/hotspot/cpu/x86/gc/shared/barrierSetAssembler_x86.cpp. It was improved in
            // JDK 20 to be more efficient.
            final Label continuation = new Label();
            final Label entryPoint = new Label();

            // The following code sequence must be emitted in exactly this fashion as HotSpot
            // will check that the barrier is the expected code sequence.
            asm.align(4);
            crb.recordMark(HotSpotMarkId.FRAME_COMPLETE);
            crb.recordMark(HotSpotMarkId.ENTRY_BARRIER_PATCH);
            asm.nmethodEntryCompare(config.threadDisarmedOffset);
            asm.jcc(ConditionFlag.NotEqual, entryPoint);
            crb.getLIR().addSlowPath(null, () -> {
                asm.bind(entryPoint);
                /*
                 * The nmethod entry barrier can deoptimize by manually removing this frame. It
                 * makes some assumptions about the frame layout that aren't always true for Graal.
                 * In particular it assumes the caller`s rbp is always saved in the standard
                 * location. With -XX:+PreserveFramePointer this has been done by the frame setup.
                 * Otherwise it is only saved lazily (i.e. if rbp is actually used by the register
                 * allocator). Since nmethod entry barriers are enabled, the space for rbp has been
                 * reserved in the frame and here we ensure it is properly saved before calling the
                 * nmethod entry barrier.
                 */
                AMD64HotSpotFrameMap frameMap = (AMD64HotSpotFrameMap) crb.frameMap;
                if (!frameMap.preserveFramePointer()) {
                    asm.movq(new AMD64Address(rsp, frameMap.offsetForStackSlot(frameMap.getRBPSpillSlot())), rbp);
                }
                // This is always a near call
                int beforeCall = asm.position();
                asm.call();
                crb.recordDirectCall(beforeCall, asm.position(), callTarget, null);

                // Return to inline code
                asm.jmp(continuation);
            });
            asm.bind(continuation);
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AMD64HotSpotFrameMap frameMap = (AMD64HotSpotFrameMap) crb.frameMap;
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            assert frameMap.getRegisterConfig().getCalleeSaveRegisters() == null;

            if (frameMap.preserveFramePointer()) {
                asm.movq(rsp, rbp);
                asm.pop(rbp);
            } else {
                asm.incrementq(rsp, frameMap.frameSize());
            }
        }

        @Override
        public void returned(CompilationResultBuilder crb) {
            // nothing to do
        }

        public void rawEnter(CompilationResultBuilder crb) {
            AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
            AMD64FrameMap frameMap = (AMD64FrameMap) crb.frameMap;

            if (frameMap.preserveFramePointer()) {
                // Stack-walking friendly instructions
                asm.push(rbp);
                asm.movq(rbp, rsp);
            }
            asm.decrementq(rsp, frameMap.frameSize());
        }

        public void rawLeave(CompilationResultBuilder crb) {
            leave(crb);
        }
    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        // Omit the frame if the method:
        // - has no spill slots or other slots allocated during register allocation
        // - has no callee-saved registers
        // - has no incoming arguments passed on the stack
        // - has no deoptimization points
        // - makes no foreign calls (which require an aligned stack)
        HotSpotLIRGenerationResult gen = (HotSpotLIRGenerationResult) lirGenRen;
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";
        OptionValues options = lir.getOptions();
        DebugContext debug = lir.getDebug();

        Stub stub = gen.getStub();
        AMD64MacroAssembler masm = new AMD64HotSpotMacroAssembler(config, getTarget(), options, config.CPU_HAS_INTEL_JCC_ERRATUM);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null);
        DataBuilder dataBuilder = new HotSpotDataBuilder(getCodeCache().getTarget());
        CompilationResultBuilder crb = factory.createBuilder(getProviders(), frameMap, masm, dataBuilder, frameContext, options, debug, compilationResult, Register.None, lir);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        crb.setMaxInterpreterFrameSize(gen.getMaxInterpreterFrameSize());
        crb.setMinDataSectionItemAlignment(getMinDataSectionItemAlignment());
        StackSlot deoptimizationRescueSlot = gen.getDeoptimizationRescueSlot();
        if (deoptimizationRescueSlot != null && stub == null) {
            crb.compilationResult.setCustomStackAreaOffset(deoptimizationRescueSlot);
        }

        if (stub != null) {
            updateStub(stub, gen, frameMap);
        }

        return crb;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, EntryPointDecorator entryPointDecorator) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();

        // Emit the prefix
        emitCodePrefix(installedCodeOwner, crb, asm, regConfig);

        if (entryPointDecorator != null) {
            entryPointDecorator.emitEntryPoint(crb);
        }

        // Emit code for the LIR
        crb.emitLIR();

        // Emit the suffix
        emitCodeSuffix(crb, asm);
    }

    /**
     * Emits the code prior to the verified entry point.
     *
     * @param installedCodeOwner see {@link LIRGenerationProvider#emitCode}
     */
    public void emitCodePrefix(ResolvedJavaMethod installedCodeOwner, CompilationResultBuilder crb, AMD64MacroAssembler asm, RegisterConfig regConfig) {
        HotSpotProviders providers = getProviders();
        if (installedCodeOwner != null && !installedCodeOwner.isStatic()) {
            crb.recordMark(HotSpotMarkId.UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(HotSpotCallingConventionType.JavaCallee, null, new JavaType[]{providers.getMetaAccess().lookupJavaType(Object.class)}, this);
            Register inlineCacheKlass = rax; // see definition of IC_Klass in
            // c1_LIRAssembler_x86.cpp
            Register receiver = asRegister(cc.getArgument(0));
            AMD64Address src = new AMD64Address(receiver, config.hubOffset);
            int before;

            if (config.useCompressedClassPointers) {
                Register register = r10;
                Register heapBase = providers.getRegisters().getHeapBaseRegister();
                AMD64HotSpotMove.decodeKlassPointer(asm, register, heapBase, src, config);
                if (config.narrowKlassBase != 0) {
                    // The heap base register was destroyed above, so restore it
                    if (config.narrowOopBase == 0L) {
                        asm.xorq(heapBase, heapBase);
                    } else {
                        asm.movq(heapBase, config.narrowOopBase);
                    }
                }
                before = asm.cmpqAndJcc(inlineCacheKlass, register, ConditionFlag.NotEqual, null, false);
            } else {
                before = asm.cmpqAndJcc(inlineCacheKlass, src, ConditionFlag.NotEqual, null, false);
            }
            crb.recordDirectCall(before, asm.position(), getForeignCalls().lookupForeignCall(IC_MISS_HANDLER), null);
        }

        asm.align(config.codeEntryAlignment);
        crb.recordMark(crb.compilationResult.getEntryBCI() != -1 ? HotSpotMarkId.OSR_ENTRY : HotSpotMarkId.VERIFIED_ENTRY);
    }

    public void emitCodeSuffix(CompilationResultBuilder crb, AMD64MacroAssembler asm) {
        HotSpotProviders providers = getProviders();
        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        if (!frameContext.isStub) {
            HotSpotForeignCallsProvider foreignCalls = providers.getForeignCalls();
            if (crb.getPendingImplicitExceptionList() != null) {
                for (CompilationResultBuilder.PendingImplicitException pendingImplicitException : crb.getPendingImplicitExceptionList()) {
                    // Insert stub code that stores the corresponding deoptimization action &
                    // reason, as well as the failed speculation, and calls into
                    // DEOPT_BLOB_UNCOMMON_TRAP. Note that we use the debugging info at the
                    // exceptional PC that triggers this implicit exception, we cannot touch
                    // any register/stack slot in this stub, so as to preserve a valid mapping for
                    // constructing the interpreter frame.
                    int pos = asm.position();
                    Register thread = getProviders().getRegisters().getThreadRegister();
                    // Store deoptimization reason and action into thread local storage.
                    asm.movl(new AMD64Address(thread, config.pendingDeoptimizationOffset), pendingImplicitException.state.deoptReasonAndAction.asInt());

                    JavaConstant deoptSpeculation = pendingImplicitException.state.deoptSpeculation;
                    if (deoptSpeculation.getJavaKind() == JavaKind.Long) {
                        // Store speculation into thread local storage. As AMD64 does not support
                        // 64-bit long integer memory store, we break it into two 32-bit integer
                        // store.
                        long speculationAsLong = pendingImplicitException.state.deoptSpeculation.asLong();
                        if (NumUtil.isInt(speculationAsLong)) {
                            AMD64Assembler.AMD64MIOp.MOV.emit(asm, AMD64BaseAssembler.OperandSize.QWORD,
                                            new AMD64Address(thread, config.pendingFailedSpeculationOffset), (int) speculationAsLong);
                        } else {
                            asm.movl(new AMD64Address(thread, config.pendingFailedSpeculationOffset), (int) speculationAsLong);
                            asm.movl(new AMD64Address(thread, config.pendingFailedSpeculationOffset + 4), (int) (speculationAsLong >> 32));
                        }
                    } else {
                        assert deoptSpeculation.getJavaKind() == JavaKind.Int;
                        int speculationAsInt = pendingImplicitException.state.deoptSpeculation.asInt();
                        asm.movl(new AMD64Address(thread, config.pendingFailedSpeculationOffset), speculationAsInt);
                    }

                    AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNCOMMON_TRAP), null, false, pendingImplicitException.state);
                    crb.recordImplicitException(pendingImplicitException.codeOffset, pos, pendingImplicitException.state);
                }
            }
            crb.recordMark(AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), null, false, null), HotSpotMarkId.EXCEPTION_HANDLER_ENTRY);
            crb.recordMark(AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK), null, false, null), HotSpotMarkId.DEOPT_HANDLER_ENTRY);
            if (config.supportsMethodHandleDeoptimizationEntry() && crb.needsMHDeoptHandler()) {
                crb.recordMark(AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK), null, false, null), HotSpotMarkId.DEOPT_MH_HANDLER_ENTRY);
            }
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AMD64HotSpotRegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo, config.preserveFramePointer);
    }
}
