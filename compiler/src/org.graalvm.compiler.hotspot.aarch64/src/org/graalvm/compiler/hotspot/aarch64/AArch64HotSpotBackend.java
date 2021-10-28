/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.aarch64;

import static java.lang.reflect.Modifier.isStatic;
import static jdk.vm.ci.aarch64.AArch64.lr;
import static jdk.vm.ci.aarch64.AArch64.r10;
import static jdk.vm.ci.aarch64.AArch64.sp;
import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig.fp;
import static org.graalvm.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_UNSIGNED_SCALED;
import static org.graalvm.compiler.core.common.GraalOptions.ZapStackOnMethodEntry;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.asm.BranchTargetOutOfBoundsException;
import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.aarch64.AArch64NodeMatchRules;
import org.graalvm.compiler.core.common.alloc.RegisterAllocationConfig;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.gen.LIRGenerationProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.HotSpotDataBuilder;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntimeProvider;
import org.graalvm.compiler.hotspot.HotSpotHostBackend;
import org.graalvm.compiler.hotspot.HotSpotLIRGenerationResult;
import org.graalvm.compiler.hotspot.HotSpotMarkId;
import org.graalvm.compiler.hotspot.meta.HotSpotForeignCallsProvider;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.hotspot.stubs.Stub;
import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMap;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMapBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.CompilationResultBuilderFactory;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.lir.framemap.FrameMapBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CompilationRequest;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.hotspot.HotSpotCallingConventionType;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

/**
 * HotSpot AArch64 specific backend.
 */
public class AArch64HotSpotBackend extends HotSpotHostBackend implements LIRGenerationProvider {

    public AArch64HotSpotBackend(GraalHotSpotVMConfig config, HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        super(config, runtime, providers);
    }

    @Override
    protected FrameMapBuilder newFrameMapBuilder(RegisterConfig registerConfig) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        FrameMap frameMap = new AArch64FrameMap(getCodeCache(), registerConfigNonNull, this);
        return new AArch64FrameMapBuilder(frameMap, getCodeCache(), registerConfigNonNull);
    }

    @Override
    public LIRGeneratorTool newLIRGenerator(LIRGenerationResult lirGenRes) {
        return new AArch64HotSpotLIRGenerator(getProviders(), config, lirGenRes);
    }

    @Override
    public NodeLIRBuilderTool newNodeLIRBuilder(StructuredGraph graph, LIRGeneratorTool lirGen) {
        return new AArch64HotSpotNodeLIRBuilder(graph, lirGen, new AArch64NodeMatchRules(lirGen));
    }

    @Override
    protected void bangStackWithOffset(CompilationResultBuilder crb, int bangOffset) {
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        try (ScratchRegister sc = masm.getScratchRegister()) {
            Register scratch = sc.getRegister();
            AArch64Address address = masm.makeAddress(64, sp, -bangOffset, scratch);
            masm.str(64, zr, address);
        }
    }

    @Override
    public InstalledCode createInstalledCode(DebugContext debug,
                    ResolvedJavaMethod method,
                    CompilationRequest compilationRequest,
                    CompilationResult compilationResult,
                    InstalledCode predefinedInstalledCode,
                    boolean isDefault,
                    Object[] context) {
        boolean isStub = (method == null);
        if (!isStub) {
            // Non-stub compilation results are installed into HotSpot as nmethods. As AArch64 has
            // a constraint that the instruction at nmethod verified entry point should be a nop or
            // jump, AArch64HotSpotBackend always generate a nop placeholder before the code body
            // for non-AOT compilations. See AArch64HotSpotBackend.emitInvalidatePlaceholder(). This
            // assert checks if the nop placeholder is generated at all required places, including
            // in manually assembled code in CodeGenTest cases.
            assert hasInvalidatePlaceholder(compilationResult);
        }
        return super.createInstalledCode(debug, method, compilationRequest, compilationResult, predefinedInstalledCode, isDefault, context);
    }

    private boolean hasInvalidatePlaceholder(CompilationResult compilationResult) {
        byte[] targetCode = compilationResult.getTargetCode();
        int verifiedEntryOffset = 0;
        for (CompilationResult.CodeMark mark : compilationResult.getMarks()) {
            if (mark.id == HotSpotMarkId.VERIFIED_ENTRY || mark.id == HotSpotMarkId.OSR_ENTRY) {
                // The nmethod verified entry is located at some pc offset.
                verifiedEntryOffset = mark.pcOffset;
                break;
            }
        }
        Unsafe unsafe = GraalUnsafeAccess.getUnsafe();
        int instruction = unsafe.getIntVolatile(targetCode, unsafe.arrayBaseOffset(byte[].class) + verifiedEntryOffset);
        AArch64MacroAssembler masm = new AArch64MacroAssembler(getTarget());
        masm.nop();
        return instruction == masm.getInt(0);
    }

    private class HotSpotFrameContext implements FrameContext {
        final boolean isStub;
        final boolean preserveFramePointer;

        HotSpotFrameContext(boolean isStub, boolean preserveFramePointer) {
            this.isStub = isStub;
            this.preserveFramePointer = preserveFramePointer;
        }

        @Override
        public void enter(CompilationResultBuilder crb) {
            FrameMap frameMap = crb.frameMap;
            final int frameSize = frameMap.frameSize();
            final int totalFrameSize = frameMap.totalFrameSize();
            int wordSize = crb.target.arch.getWordSize();
            assert frameSize + 2 * wordSize == totalFrameSize : "total framesize should be framesize + 2 words";
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            if (!isStub) {
                emitStackOverflowCheck(crb);
            }
            crb.blockComment("[method prologue]");

            // based on HotSpot's macroAssembler_aarch64.cpp MacroAssembler::build_frame
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                assert totalFrameSize > 0;
                AArch64Address.AddressingMode addressingMode = AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
                if (AArch64Address.isValidImmediateAddress(64, addressingMode, frameSize)) {
                    masm.sub(64, sp, sp, totalFrameSize);
                    masm.stp(64, fp, lr, AArch64Address.createImmediateAddress(64, addressingMode, sp, frameSize));
                    if (preserveFramePointer) {
                        masm.add(64, fp, sp, frameSize);
                    }
                } else {
                    int frameRecordSize = 2 * wordSize;
                    masm.stp(64, fp, lr, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_PAIR_PRE_INDEXED, sp, -frameRecordSize));
                    if (preserveFramePointer) {
                        masm.mov(64, fp, sp);
                    }
                    masm.sub(64, sp, sp, totalFrameSize - frameRecordSize, scratch);
                }
            }
            if (HotSpotMarkId.FRAME_COMPLETE.isAvailable()) {
                crb.recordMark(HotSpotMarkId.FRAME_COMPLETE);
            }
            if (ZapStackOnMethodEntry.getValue(crb.getOptions())) {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    int longSize = 8;
                    masm.mov(64, scratch, sp);
                    AArch64Address address = AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_POST_INDEXED, scratch, longSize);
                    try (ScratchRegister sc2 = masm.getScratchRegister()) {
                        Register value = sc2.getRegister();
                        masm.mov(value, 0xBADDECAFFC0FFEEL);
                        for (int i = 0; i < frameSize; i += longSize) {
                            masm.str(64, value, address);
                        }
                    }

                }
            }
            crb.blockComment("[code body]");
        }

        @Override
        public void leave(CompilationResultBuilder crb) {
            AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
            FrameMap frameMap = crb.frameMap;
            final int totalFrameSize = frameMap.totalFrameSize();

            crb.blockComment("[method epilogue]");
            // based on HotSpot's macroAssembler_aarch64.cpp MacroAssembler::leave_frame
            try (ScratchRegister sc = masm.getScratchRegister()) {
                int wordSize = 8;
                Register scratch = sc.getRegister();
                final int frameSize = frameMap.frameSize();
                assert totalFrameSize > 0;
                AArch64Address.AddressingMode addressingMode = AArch64Address.AddressingMode.IMMEDIATE_PAIR_SIGNED_SCALED;
                if (AArch64Address.isValidImmediateAddress(64, addressingMode, frameSize)) {
                    masm.ldp(64, fp, lr, AArch64Address.createImmediateAddress(64, addressingMode, sp, frameSize));
                    masm.add(64, sp, sp, totalFrameSize);
                } else {
                    int frameRecordSize = 2 * wordSize;
                    masm.add(64, sp, sp, totalFrameSize - frameRecordSize, scratch);
                    masm.ldp(64, fp, lr, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_PAIR_POST_INDEXED, sp, frameRecordSize));
                }
            }

        }

        @Override
        public void returned(CompilationResultBuilder crb) {
            // nothing to do
        }

        @Override
        public boolean hasFrame() {
            return true;
        }

    }

    @Override
    public CompilationResultBuilder newCompilationResultBuilder(LIRGenerationResult lirGenRen, FrameMap frameMap, CompilationResult compilationResult, CompilationResultBuilderFactory factory) {
        HotSpotLIRGenerationResult gen = (HotSpotLIRGenerationResult) lirGenRen;
        LIR lir = gen.getLIR();
        assert gen.getDeoptimizationRescueSlot() == null || frameMap.frameNeedsAllocating() : "method that can deoptimize must have a frame";

        Stub stub = gen.getStub();
        Assembler masm = new AArch64MacroAssembler(getTarget());
        HotSpotFrameContext frameContext = new HotSpotFrameContext(stub != null, config.preserveFramePointer);

        DataBuilder dataBuilder = new HotSpotDataBuilder(getCodeCache().getTarget());
        CompilationResultBuilder crb = factory.createBuilder(getProviders(), frameMap, masm, dataBuilder, frameContext, lir.getOptions(), lir.getDebug(), compilationResult,
                        Register.None);
        crb.setTotalFrameSize(frameMap.totalFrameSize());
        crb.setMaxInterpreterFrameSize(gen.getMaxInterpreterFrameSize());
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
    public void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner) {
        Label verifiedStub = new Label();
        crb.buildLabelOffsets(lir);
        try {
            emitCode(crb, lir, installedCodeOwner, verifiedStub);
        } catch (BranchTargetOutOfBoundsException e) {
            // A branch estimation was wrong, now retry with conservative label ranges, this
            // should always work
            crb.setConservativeLabelRanges();
            crb.resetForEmittingCode();
            lir.resetLabels();
            verifiedStub.reset();
            emitCode(crb, lir, installedCodeOwner, verifiedStub);
        }
    }

    private void emitCode(CompilationResultBuilder crb, LIR lir, ResolvedJavaMethod installedCodeOwner, Label verifiedStub) {
        AArch64MacroAssembler masm = (AArch64MacroAssembler) crb.asm;
        FrameMap frameMap = crb.frameMap;
        RegisterConfig regConfig = frameMap.getRegisterConfig();
        emitCodePrefix(crb, installedCodeOwner, masm, regConfig, verifiedStub);
        emitCodeBody(crb, lir, masm);
        emitCodeSuffix(crb, masm, frameMap);
    }

    private void emitCodePrefix(CompilationResultBuilder crb, ResolvedJavaMethod installedCodeOwner, AArch64MacroAssembler masm, RegisterConfig regConfig, Label verifiedStub) {
        HotSpotProviders providers = getProviders();
        if (installedCodeOwner != null && !isStatic(installedCodeOwner.getModifiers())) {
            crb.recordMark(HotSpotMarkId.UNVERIFIED_ENTRY);
            CallingConvention cc = regConfig.getCallingConvention(HotSpotCallingConventionType.JavaCallee, null, new JavaType[]{providers.getMetaAccess().lookupJavaType(Object.class)}, this);
            // See definition of IC_Klass in c1_LIRAssembler_aarch64.cpp
            // equal to scratch(1) careful!
            Register inlineCacheKlass = AArch64HotSpotRegisterConfig.inlineCacheRegister;
            Register receiver = asRegister(cc.getArgument(0));
            int size = config.useCompressedClassPointers ? 32 : 64;
            AArch64Address klassAddress = masm.makeAddress(size, receiver, config.hubOffset);

            // Are r10 and r11 available scratch registers here? One would hope so.
            Register klass = r10;
            if (config.useCompressedClassPointers) {
                masm.ldr(32, klass, klassAddress);
                AArch64HotSpotMove.decodeKlassPointer(masm, klass, klass, config.getKlassEncoding());
            } else {
                masm.ldr(64, klass, klassAddress);
            }
            masm.cmp(64, inlineCacheKlass, klass);
            /*
             * Conditional jumps have a much lower range than unconditional ones, which can be a
             * problem because the miss handler could be out of range.
             */
            masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, verifiedStub);
            AArch64Call.directJmp(crb, masm, getForeignCalls().lookupForeignCall(IC_MISS_HANDLER));
        }
        masm.align(config.codeEntryAlignment);
        masm.bind(verifiedStub);
        crb.recordMark(crb.compilationResult.getEntryBCI() != -1 ? HotSpotMarkId.OSR_ENTRY : HotSpotMarkId.VERIFIED_ENTRY);
    }

    private static void emitCodeBody(CompilationResultBuilder crb, LIR lir, AArch64MacroAssembler masm) {
        emitInvalidatePlaceholder(crb, masm);
        crb.emit(lir);
    }

    /**
     * Insert a nop at the start of the prolog so we can patch in a branch if we need to invalidate
     * the method later.
     *
     * @see "http://mail.openjdk.java.net/pipermail/aarch64-port-dev/2013-September/000273.html"
     */
    public static void emitInvalidatePlaceholder(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        crb.blockComment("[nop for method invalidation]");
        masm.nop();
    }

    private void emitCodeSuffix(CompilationResultBuilder crb, AArch64MacroAssembler masm, FrameMap frameMap) {
        HotSpotProviders providers = getProviders();
        HotSpotFrameContext frameContext = (HotSpotFrameContext) crb.frameContext;
        if (!frameContext.isStub) {
            HotSpotForeignCallsProvider foreignCalls = providers.getForeignCalls();
            if (crb.getPendingImplicitExceptionList() != null) {
                try (ScratchRegister sc = masm.getScratchRegister()) {
                    Register scratch = sc.getRegister();
                    for (CompilationResultBuilder.PendingImplicitException pendingImplicitException : crb.getPendingImplicitExceptionList()) {
                        // Insert stub code that stores the corresponding deoptimization action &
                        // reason, as well as the failed speculation, and calls into
                        // DEOPT_BLOB_UNCOMMON_TRAP. Note that we use the debugging info at the
                        // exceptional PC that triggers this implicit exception, we cannot touch
                        // any register/stack slot in this stub, so as to preserve a valid mapping
                        // for constructing the interpreter frame.
                        int pos = masm.position();
                        Register thread = getProviders().getRegisters().getThreadRegister();
                        // Store deoptimization reason and action into thread local storage.
                        int dwordSizeInBits = AArch64Kind.DWORD.getSizeInBytes() * Byte.SIZE;
                        AArch64Address pendingDeoptimization = AArch64Address.createImmediateAddress(dwordSizeInBits, IMMEDIATE_UNSIGNED_SCALED, thread, config.pendingDeoptimizationOffset);
                        masm.mov(scratch, pendingImplicitException.state.deoptReasonAndAction.asInt());
                        masm.str(dwordSizeInBits, scratch, pendingDeoptimization);

                        // Store speculation into thread local storage
                        JavaConstant deoptSpeculation = pendingImplicitException.state.deoptSpeculation;
                        if (deoptSpeculation.getJavaKind() == JavaKind.Long) {
                            int qwordSizeInBits = AArch64Kind.QWORD.getSizeInBytes() * Byte.SIZE;
                            AArch64Address pendingSpeculation = AArch64Address.createImmediateAddress(qwordSizeInBits, IMMEDIATE_UNSIGNED_SCALED, thread, config.pendingFailedSpeculationOffset);
                            masm.mov(scratch, pendingImplicitException.state.deoptSpeculation.asLong());
                            masm.str(qwordSizeInBits, scratch, pendingSpeculation);
                        } else {
                            assert deoptSpeculation.getJavaKind() == JavaKind.Int;
                            AArch64Address pendingSpeculation = AArch64Address.createImmediateAddress(dwordSizeInBits, IMMEDIATE_UNSIGNED_SCALED, thread, config.pendingFailedSpeculationOffset);
                            masm.mov(scratch, pendingImplicitException.state.deoptSpeculation.asInt());
                            masm.str(dwordSizeInBits, scratch, pendingSpeculation);
                        }

                        ForeignCallLinkage uncommonTrapBlob = foreignCalls.lookupForeignCall(DEOPT_BLOB_UNCOMMON_TRAP);
                        Register helper = AArch64Call.isNearCall(uncommonTrapBlob) ? null : scratch;
                        AArch64Call.directCall(crb, masm, uncommonTrapBlob, helper, pendingImplicitException.state);
                        crb.recordImplicitException(pendingImplicitException.codeOffset, pos, pendingImplicitException.state);
                    }
                }
            }
            try (ScratchRegister sc = masm.getScratchRegister()) {
                Register scratch = sc.getRegister();
                crb.recordMark(HotSpotMarkId.EXCEPTION_HANDLER_ENTRY);
                ForeignCallLinkage linkage = foreignCalls.lookupForeignCall(EXCEPTION_HANDLER);
                Register helper = AArch64Call.isNearCall(linkage) ? null : scratch;
                AArch64Call.directCall(crb, masm, linkage, helper, null);
            }
            crb.recordMark(HotSpotMarkId.DEOPT_HANDLER_ENTRY);
            ForeignCallLinkage linkage = foreignCalls.lookupForeignCall(DEOPT_BLOB_UNPACK);
            masm.adr(lr, 0); // Warning: the argument is an offset from the instruction!
            AArch64Call.directJmp(crb, masm, linkage);
            if (config.supportsMethodHandleDeoptimizationEntry() && crb.needsMHDeoptHandler()) {
                crb.recordMark(HotSpotMarkId.DEOPT_MH_HANDLER_ENTRY);
                masm.adr(lr, 0);
                AArch64Call.directJmp(crb, masm, linkage);
            }
        } else {
            // No need to emit the stubs for entries back into the method since
            // it has no calls that can cause such "return" entries
            assert !frameMap.accessesCallerFrame();
        }
    }

    @Override
    public RegisterAllocationConfig newRegisterAllocationConfig(RegisterConfig registerConfig, String[] allocationRestrictedTo) {
        RegisterConfig registerConfigNonNull = registerConfig == null ? getCodeCache().getRegisterConfig() : registerConfig;
        return new AArch64HotSpotRegisterAllocationConfig(registerConfigNonNull, allocationRestrictedTo, config.preserveFramePointer);
    }
}
