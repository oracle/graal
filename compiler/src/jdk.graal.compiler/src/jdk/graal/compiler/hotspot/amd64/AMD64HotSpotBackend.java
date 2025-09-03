/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import static jdk.graal.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;
import static jdk.vm.ci.amd64.AMD64.r10;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbp;
import static jdk.vm.ci.amd64.AMD64.rsp;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.BranchTargetOutOfBoundsException;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.amd64.AMD64NodeMatchRules;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.gen.LIRGenerationProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotDataBuilder;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.HotSpotHostBackend;
import jdk.graal.compiler.hotspot.HotSpotLIRGenerationResult;
import jdk.graal.compiler.hotspot.HotSpotMarkId;
import jdk.graal.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.stubs.Stub;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64FrameMap;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.asm.CompilationResultBuilderFactory;
import jdk.graal.compiler.lir.asm.DataBuilder;
import jdk.graal.compiler.lir.asm.EntryPointDecorator;
import jdk.graal.compiler.lir.asm.FrameContext;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.vector.lir.VectorLIRGeneratorTool;
import jdk.graal.compiler.vector.lir.amd64.AMD64VectorNodeMatchRules;
import jdk.graal.compiler.vector.lir.hotspot.amd64.AMD64HotSpotVectorLIRGenerator;
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
    protected FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig, Stub stub) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        AMD64FrameMap frameMap = new AMD64HotSpotFrameMap(getCodeCache(), registerConfigNonNull, this, config.preserveFramePointer(stub != null));
        return new AMD64HotSpotFrameMapBuilder(frameMap, getCodeCache(), registerConfigNonNull);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        if (((AMD64) getTarget().arch).getFeatures().contains(AMD64.CPUFeature.AVX)) {
            return new AMD64HotSpotVectorLIRGenerator(getProviders(), config, lirGenRes);
        } else {
            return new AMD64HotSpotLIRGenerator(getProviders(), config, lirGenRes);
        }
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        if (lirGen.getArithmetic() instanceof VectorLIRGeneratorTool) {
            return new AMD64HotSpotNodeLIRBuilder(graph, lirGen, new AMD64VectorNodeMatchRules(lirGen));
        } else {
            return new AMD64HotSpotNodeLIRBuilder(graph, lirGen, new AMD64NodeMatchRules(lirGen));
        }
    }

    @Override
    protected void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        int pos = asm.position();
        asm.movl(new AMD64Address(rsp, -bangOffset), AMD64.rax);
        assert asm.position() - pos >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE : asm.position() + "-" + pos;
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
        private final EntryPointDecorator entryPointDecorator;

        HotSpotFrameContext(boolean isStub, EntryPointDecorator entryPointDecorator) {
            this.isStub = isStub;
            this.entryPointDecorator = entryPointDecorator;
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
                assert asm.position() - verifiedEntryPointOffset >= PATCHED_VERIFIED_ENTRY_POINT_INSTRUCTION_SIZE : asm.position() + "-" + verifiedEntryPointOffset;
            } else {
                asm.decrementq(rsp, frameSize);
            }

            assert frameMap.getRegisterConfig().getCalleeSaveRegisters() == null;

            if (!isStub) {
                emitNmethodEntryBarrier(crb, asm);
            } else {
                crb.recordMark(HotSpotMarkId.FRAME_COMPLETE);
            }

            if (entryPointDecorator != null) {
                entryPointDecorator.emitEntryPoint(crb, false);
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

            /*
             * The following code sequence must be emitted in exactly this fashion as HotSpot will
             * check that the barrier is the expected code sequence.
             */
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
                asm.call((before, after) -> {
                    crb.recordDirectCall(before, after, callTarget, null);
                }, callTarget);

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
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory,
                    EntryPointDecorator entryPointDecorator) {
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
        AMD64MacroAssembler masm = new AMD64HotSpotMacroAssembler(config, getTarget(), options, getProviders(), config.CPU_HAS_INTEL_JCC_ERRATUM);
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null, entryPointDecorator);
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
        emitCodeHelper(crb, installedCodeOwner, entryPointDecorator);
        if (GraalOptions.OptimizeLongJumps.getValue(crb.getOptions())) {
            optimizeLongJumps(crb, installedCodeOwner, entryPointDecorator);
        }
    }

    private void emitCodeHelper(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, EntryPointDecorator entryPointDecorator) {
        AMD64MacroAssembler asm = (AMD64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();

        // Emit the prefix
        emitCodePrefix(installedCodeOwner, crb, asm, regConfig);

        if (entryPointDecorator != null) {
            entryPointDecorator.emitEntryPoint(crb, true);
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
            JavaType[] parameterTypes = {providers.getMetaAccess().lookupJavaType(Object.class)};
            CallingConvention cc = regConfig.getCallingConvention(HotSpotCallingConventionType.JavaCallee, null, parameterTypes, this);
            Register receiver = asRegister(cc.getArgument(0));
            int before;
            if (config.icSpeculatedKlassOffset == Integer.MAX_VALUE) {
                crb.recordMark(HotSpotMarkId.UNVERIFIED_ENTRY);
                // c1_LIRAssembler_x86.cpp: const Register IC_Klass = rax;
                Register inlineCacheKlass = rax;

                if (config.useCompressedClassPointers) {
                    Register register = r10;
                    Register heapBase = providers.getRegisters().getHeapBaseRegister();
                    if (config.useCompactObjectHeaders) {
                        ((AMD64HotSpotMacroAssembler) asm).loadCompactClassPointer(register, receiver);
                    } else {
                        asm.movl(register, new AMD64Address(receiver, config.hubOffset));
                    }
                    AMD64HotSpotMove.decodeKlassPointer(asm, register, heapBase, config);
                    if (config.narrowKlassBase != 0) {
                        // The heap base register was destroyed above, so restore it
                        if (config.narrowOopBase == 0L) {
                            asm.xorl(heapBase, heapBase);
                        } else {
                            asm.movq(heapBase, config.narrowOopBase);
                        }
                    }
                    before = asm.cmpqAndJcc(inlineCacheKlass, register, ConditionFlag.NotEqual, null, false);
                } else {
                    before = asm.cmpqAndJcc(inlineCacheKlass, new AMD64Address(receiver, config.hubOffset), ConditionFlag.NotEqual, null, false);
                }
                crb.recordDirectCall(before, asm.position(), getForeignCalls().lookupForeignCall(IC_MISS_HANDLER), null);
            } else {
                // JDK-8322630 (removed ICStubs)
                Register data = rax;
                Register temp = r10;
                ForeignCallLinkage icMissHandler = getForeignCalls().lookupForeignCall(IC_MISS_HANDLER);

                // Size of IC check sequence checked with a guarantee below.
                int inlineCacheCheckSize = 14;
                if (asm.force4ByteNonZeroDisplacements()) {
                    /*
                     * The mov and cmp below each contain a 1-byte displacement that is emitted as 4
                     * bytes instead, thus we have 3 extra bytes for each of these instructions.
                     */
                    inlineCacheCheckSize += 3 + 3;
                }
                if (config.useCompactObjectHeaders) {
                    // 4 bytes for extra shift instruction, 1 byte less for 0-displacement address
                    inlineCacheCheckSize += 3;
                }
                asm.align(config.codeEntryAlignment, asm.position() + inlineCacheCheckSize);

                int startICCheck = asm.position();
                crb.recordMark(HotSpotMarkId.UNVERIFIED_ENTRY);
                AMD64Address icSpeculatedKlass = new AMD64Address(data, config.icSpeculatedKlassOffset);

                AMD64BaseAssembler.OperandSize size;
                if (config.useCompressedClassPointers) {
                    if (config.useCompactObjectHeaders) {
                        ((AMD64HotSpotMacroAssembler) asm).loadCompactClassPointer(temp, receiver);
                    } else {
                        asm.movl(temp, new AMD64Address(receiver, config.hubOffset));
                    }
                    size = AMD64BaseAssembler.OperandSize.DWORD;
                } else {
                    asm.movptr(temp, new AMD64Address(receiver, config.hubOffset));
                    size = AMD64BaseAssembler.OperandSize.QWORD;
                }
                before = asm.cmpAndJcc(size, temp, icSpeculatedKlass, ConditionFlag.NotEqual, null, false);
                crb.recordDirectCall(before, asm.position(), icMissHandler, null);

                int actualInlineCacheCheckSize = asm.position() - startICCheck;
                if (actualInlineCacheCheckSize != inlineCacheCheckSize) {
                    // Code emission pattern has changed: adjust `inlineCacheCheckSize`
                    // initialization above accordingly.
                    throw new GraalError("%s != %s", actualInlineCacheCheckSize, inlineCacheCheckSize);
                }
            }
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
                        assert deoptSpeculation.getJavaKind() == JavaKind.Int : deoptSpeculation;
                        int speculationAsInt = pendingImplicitException.state.deoptSpeculation.asInt();
                        asm.movl(new AMD64Address(thread, config.pendingFailedSpeculationOffset), speculationAsInt);
                    }

                    AMD64Call.directCall(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNCOMMON_TRAP), null, false, pendingImplicitException.state);
                    crb.recordImplicitException(pendingImplicitException.codeOffset, pos, pendingImplicitException.state);
                }
            }
            emitExceptionHandler(crb, asm, foreignCalls.lookupForeignCall(EXCEPTION_HANDLER), HotSpotMarkId.EXCEPTION_HANDLER_ENTRY);
            emitDeoptHandler(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK), HotSpotMarkId.DEOPT_HANDLER_ENTRY);
            if (config.supportsMethodHandleDeoptimizationEntry() && crb.needsMHDeoptHandler()) {
                emitDeoptHandler(crb, asm, foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK), HotSpotMarkId.DEOPT_MH_HANDLER_ENTRY);
            }
        }
    }

    private static void emitExceptionHandler(CompilationResultBuilder crb, AMD64MacroAssembler asm, ForeignCallLinkage callTarget, HotSpotMarkId exceptionHandlerEntry) {
        crb.recordMark(AMD64Call.directCall(crb, asm, callTarget, null, false, null), exceptionHandlerEntry);
        // Ensure the return location is a unique pc and that control flow doesn't return here
        asm.halt();
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/74a2c831a2af55c66317ca8aead53fde2a2a6900/src/hotspot/cpu/x86/x86.ad#L1261-L1288",
              sha1 = "1326c5aa33296807cd6fb271150c3fcc0bfb9388")
    // @formatter:on
    private static void emitDeoptHandler(CompilationResultBuilder crb, AMD64MacroAssembler asm, ForeignCallLinkage callTarget, HotSpotMarkId deoptHandlerEntry) {
        /* Line comments preserved from JDK code. */
        crb.recordMark(asm.position(), deoptHandlerEntry);
        int position = asm.position();
        Label next = new Label();
        // push a "the_pc" on the stack without destroying any registers
        // as they all may be live.

        // push address of "next"
        asm.call(next);
        asm.bind(next);
        // adjust it so it matches "the_pc"
        asm.subq(new AMD64Address(rsp, 0), asm.position() - position);

        int jmpSize = 1 + 4;  // 1 byte opcode + 4 bytes displacement
        crb.recordDirectCall(asm.position(), asm.position() + jmpSize, callTarget, null);
        asm.rawJmpNoJCCErratumMitigation();

        /*
         * Ensure that control flow doesn't return here. The synthetic return location PC is the
         * address of the call instruction above.
         */
        asm.halt();
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo, Object stub) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AMD64HotSpotRegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo, config.preserveFramePointer(stub != null));
    }

    /**
     * Performs a code emit from LIR and replaces jumps with 4byte displacement by equivalent
     * instructions with single byte displacement, where possible. If any of these optimizations
     * unexpectedly results in a {@link BranchTargetOutOfBoundsException}, code without any
     * optimized jumps will be emitted.
     */
    private void optimizeLongJumps(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, EntryPointDecorator entryPointDecorator) {
        // triggers a reset of the assembler during which replaceable jumps are identified
        crb.resetForEmittingCode();
        try {
            emitCodeHelper(crb, installedCodeOwner, entryPointDecorator);
        } catch (BranchTargetOutOfBoundsException e) {
            /*
             * Alignments have invalidated the assumptions regarding short jumps. Trigger fail-safe
             * mode and emit unoptimized code.
             */
            AMD64MacroAssembler masm = (AMD64MacroAssembler) crb.asm;
            masm.disableOptimizeLongJumpsAfterException();
            crb.resetForEmittingCode();
            emitCodeHelper(crb, installedCodeOwner, entryPointDecorator);
        }
    }
}
