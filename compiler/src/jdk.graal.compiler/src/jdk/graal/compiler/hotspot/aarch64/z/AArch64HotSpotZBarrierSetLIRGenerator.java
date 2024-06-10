/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64.z;

import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.EQ;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ConditionFlag.NE;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.PrefetchMode.PSTL1STRM;
import static jdk.graal.compiler.asm.aarch64.AArch64Assembler.ShiftType.LSL;
import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriersSlowPathOnly;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.aarch64.AArch64LIRGenerator;
import jdk.graal.compiler.core.aarch64.AArch64ReadBarrierSetLIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotMarkId;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotBackend;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64FrameMap;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * HotSpot amd64 specific code generation for Generational ZGC barriers. See
 * {@link ZWriteBarrierSetLIRGeneratorTool} for details about ZGC pointers and the SyncPort
 * definition for links to the matching code.
 */
public class AArch64HotSpotZBarrierSetLIRGenerator implements AArch64ReadBarrierSetLIRGenerator, ZWriteBarrierSetLIRGeneratorTool {

    public AArch64HotSpotZBarrierSetLIRGenerator(GraalHotSpotVMConfig config, HotSpotProviders providers) {
        this.config = config;
        this.providers = providers;
    }

    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    /**
     * Convert a normal oop into a colored pointer.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/z_aarch64.ad#L36-L41", sha1 = "ee0780117d2ff7f782c4f3e2ae79b55a3c8dd523")
    static void zColor(CompilationResultBuilder crb, AArch64MacroAssembler masm, GraalHotSpotVMConfig config, Register dst, Register src) {
        Assembler.guaranteeDifferentRegisters(src, dst);
        crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_BEFORE_MOV);
        masm.movzPatchable(32, dst, UNPATCHED);
        masm.orr(64, dst, dst, src, LSL, config.zPointerLoadShift);
    }

    /**
     * Convert a colored pointer into normal oop.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/z_aarch64.ad#L43-L45", sha1 = "3c53528425bc5609e9c5fc3588bbed0c01cd63a6")
    static void zUncolor(AArch64MacroAssembler masm, GraalHotSpotVMConfig config, Register ref) {
        masm.lsr(64, ref, ref, config.zPointerLoadShift);
    }

    /**
     * Emit the full store barrier with a fast path, and an out of line medium path with a final
     * slow path call to the runtime. This varies slightly from the HotSpot version in that the
     * zColor of the value to be written must be performed by the caller. The value to be written
     * isn't needed by this code otherwise and in some cases the destination register for the zColor
     * must be customized.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/zBarrierSetAssembler_aarch64.cpp#L167-L225", sha1 = "101b4c83516738a04bf6fb3f17bfc78f58ac5784")
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/zBarrierSetAssembler_aarch64.cpp#L310-L371", sha1 = "755eb5d52e1ad8c30c9aa9c5f009d35f8c52bb78")
    static void emitStoreBarrier(CompilationResultBuilder crb,
                    AArch64MacroAssembler masm,
                    LIRInstruction op,
                    GraalHotSpotVMConfig config,
                    AArch64Address address,
                    Register result,
                    StoreKind storeKind,
                    ForeignCallLinkage callTarget,
                    LIRFrameState state) {

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister();
                        AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();
            Register rscratch2 = sc2.getRegister();

            Assembler.guaranteeDifferentRegisters(address.getBase(), result, rscratch1);
            Assembler.guaranteeDifferentRegisters(address.getOffset(), result, rscratch1);
            Assembler.guaranteeDifferentRegisters(result, rscratch1);

            if (storeKind == StoreKind.Atomic) {
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                masm.ldr(16, rscratch1, address);
                /*
                 * Atomic operations must ensure that the contents of memory are store-good before
                 * an atomic operation can execute. A not relocatable object could have spurious raw
                 * null pointers in its fields after getting promoted to the old generation.
                 */
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_BEFORE_MOV);
                masm.movzPatchable(32, rscratch2, UNPATCHED);
                masm.cmp(32, rscratch1, rscratch2);
            } else {
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                masm.ldr(64, rscratch1, address);
                /*
                 * Stores on relocatable objects never need to deal with raw null pointers in
                 * fields. Raw null pointers may only exist in the young generation, as they get
                 * pruned when the object is relocated to old. And no pre-write barrier needs to
                 * perform any action in the young generation.
                 */
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_BAD_BEFORE_MOV);
                masm.movzPatchable(32, rscratch2, UNPATCHED);
                masm.tst(64, rscratch1, rscratch2);
            }

            Label entry = new Label();
            Label continuation = new Label();
            if (crb.usesConservativeLabelRanges()) {
                Label good = new Label();
                masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, good);
                masm.jmp(entry);
                masm.bind(good);
            } else {
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, entry);
            }
            masm.bind(continuation);
            crb.getLIR().addSlowPath(op, () -> {
                // Stub entry
                masm.bind(entry);
                Label slow = new Label();
                Label slowContinuation = new Label();
                storeBarrierMedium(crb, masm, config,
                                address,
                                rscratch2,
                                result,
                                rscratch1,
                                storeKind,
                                continuation,
                                slow,
                                slowContinuation);

                masm.bind(slow);

                CallingConvention cc = callTarget.getOutgoingCallingConvention();
                assert cc.getArgumentCount() == 1 : "only one argument expected: " + cc;

                AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                masm.loadAddress(rscratch1, address);
                masm.str(64, rscratch1, cArg0);

                AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : rscratch1, null);
                assert cc.getReturn().equals(Value.ILLEGAL) : cc + " " + callTarget;

                masm.jmp(slowContinuation);
            });
        }
    }

    /**
     * Try to perform any local store barrier fixups or dispatch to the slow path.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/zBarrierSetAssembler_aarch64.cpp#L259-L308", sha1 = "061eaf13b97f69aee4f687ce51e500ac3b37071a")
    static void storeBarrierMedium(CompilationResultBuilder crb,
                    AArch64MacroAssembler masm,
                    GraalHotSpotVMConfig config,
                    AArch64Address address,
                    Register rtmp1,
                    Register rtmp2,
                    Register rtmp3,
                    StoreKind storeKind,
                    Label mediumPathContinuation,
                    Label slowPath,
                    Label slowPathContinuation) {
        Assembler.guaranteeDifferentRegisters(address.getBase(), address.getOffset(), rtmp1, rtmp2, rtmp3);

        // The reason to end up in the medium path is that the pre-value was not 'good'.
        if (storeKind == StoreKind.Native) {
            masm.jmp(slowPath);
            masm.bind(slowPathContinuation);
            masm.jmp(mediumPathContinuation);
        } else if (storeKind == StoreKind.Atomic) {
            // Atomic accesses can get to the medium fast path because the value was a
            // raw null value. If it was not null, then there is no doubt we need to take a slow
            // path.
            masm.loadAddress(rtmp2, address);
            masm.ldr(64, rtmp1, AArch64Address.createBaseRegisterOnlyAddress(64, rtmp2));
            masm.cbnz(64, rtmp1, slowPath);

            // If we get this far, we know there is a young raw null value in the field.
            crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_BEFORE_MOV);
            masm.movzPatchable(32, rtmp1, UNPATCHED);
            try (AArch64MacroAssembler.ScratchRegister sc = masm.getScratchRegister()) {
                Register rscratch1 = sc.getRegister();
                Label done = new Label();
                masm.prfm(AArch64Address.createBaseRegisterOnlyAddress(64, rtmp2), PSTL1STRM);
                masm.loadExclusive(64, rtmp3, rtmp2, false);
                masm.cmp(64, rtmp3, AArch64.zr);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, done);
                masm.storeExclusive(64, rscratch1, rtmp1, rtmp2, false);
                masm.compare(64, rscratch1, 0);  // If the store fails, return NE to our caller.
                masm.bind(done);
            }
            masm.branchConditionally(NE, slowPath);

            masm.bind(slowPathContinuation);
            masm.jmp(mediumPathContinuation);
        } else {
            // A non-atomic relocatable object won't get to the medium fast path due to a
            // raw null in the young generation. We only get here because the field is bad.
            // In this path we don't need any self healing, so we can avoid a runtime call
            // most of the time by buffering the store barrier to be applied lazily.
            if (AssemblyGCBarriersSlowPathOnly.getValue(crb.getOptions())) {
                masm.jmp(slowPath);
            } else {
                storeBarrierBufferAdd(masm, config,
                                address,
                                rtmp1,
                                rtmp2,
                                slowPath);
            }
            masm.bind(slowPathContinuation);
            masm.jmp(mediumPathContinuation);
        }
    }

    /**
     * Add a value to the store buffer.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/zBarrierSetAssembler_aarch64.cpp#L227-L257", sha1 = "b52bb540cf136f455dfac53fece3cc029a240bf2")
    static void storeBarrierBufferAdd(AArch64MacroAssembler masm,
                    GraalHotSpotVMConfig config,
                    AArch64Address refAddr,
                    Register tmp1,
                    Register tmp2,
                    Label slowPath) {
        Register rthread = AArch64HotSpotRegisterConfig.threadRegister;
        int offset4 = config.ZThreadLocalData_store_barrier_buffer_offset;
        AArch64Address buffer = masm.makeAddress(64, rthread, offset4);
        Assembler.guaranteeDifferentRegisters(refAddr.getBase(), refAddr.getOffset(), tmp1, tmp2);

        masm.ldr(64, tmp1, buffer);

        // Combined pointer bump and check if the buffer is disabled or full
        int offset3 = config.ZStoreBarrierBuffer_current_offset;
        masm.ldr(64, tmp2, masm.makeAddress(64, tmp1, offset3));
        masm.compare(64, tmp2, 0);
        masm.branchConditionally(EQ, slowPath);

        // Bump the pointer
        masm.sub(64, tmp2, tmp2, config.sizeofZStoreBarrierEntry);
        int offset2 = config.ZStoreBarrierBuffer_current_offset;
        masm.str(64, tmp2, masm.makeAddress(64, tmp1, offset2));

        // Compute the buffer entry address
        masm.add(64, tmp2, tmp2, config.ZStoreBarrierBuffer_buffer_offset);
        masm.add(64, tmp2, tmp2, tmp1);

        // Compute and log the store address
        masm.loadAddress(tmp1, refAddr);
        int offset1 = config.ZStoreBarrierEntry_p_offset;
        masm.str(64, tmp1, masm.makeAddress(64, tmp2, offset1));

        // Load and log the prev value
        masm.ldr(64, tmp1, AArch64Address.createBaseRegisterOnlyAddress(64, tmp1));
        int offset = config.ZStoreBarrierEntry_prev_offset;
        masm.str(64, tmp1, masm.makeAddress(64, tmp2, offset));
    }

    /**
     * Emits the basic Z read barrier pattern with some customization. Normally this code is used
     * from a {@link LIRInstruction} where the frame has already been set up. If an
     * {@link AArch64FrameMap} is passed then a frame will be setup and torn down around the call.
     * The call itself is done with a special stack-only calling convention that saves and restores
     * all registers around the call. This simplifies the code generation as no extra registers are
     * required.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/aarch64/gc/z/zBarrierSetAssembler_aarch64.cpp#L105-L165", sha1 = "2b500d0e7769c719aca0eb4d1707ac0cbf476727")
    public static void emitLoadBarrier(CompilationResultBuilder crb,
                    AArch64MacroAssembler masm,
                    GraalHotSpotVMConfig config,
                    Register ref,
                    ForeignCallLinkage callTarget,
                    AArch64Address address,
                    LIRInstruction op,
                    AArch64FrameMap frameMap,
                    boolean elided,
                    boolean isNotStrong) {
        if (elided) {
            zUncolor(masm, config, ref);
            return;
        }
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            Register scratch1 = sc1.getRegister();

            Label entry = new Label();
            Label continuation = new Label();
            if (isNotStrong) {
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_MARK_BAD_BEFORE_MOV);
                masm.movzPatchable(32, scratch1, UNPATCHED);
                masm.tst(64, ref, scratch1);
                if (crb.usesConservativeLabelRanges()) {
                    Label good = new Label();
                    masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, good);
                    masm.jmp(entry);
                    masm.bind(good);
                } else {
                    masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, entry);
                }
            } else if (crb.usesConservativeLabelRanges()) {
                Label good = new Label();
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_TB_X);
                masm.tbz(ref, UNPATCHED, good);
                masm.jmp(entry);
                masm.bind(good);
            } else {
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_TB_X);
                masm.tbnz(ref, UNPATCHED, entry);
            }
            zUncolor(masm, config, ref);
            masm.bind(continuation);

            crb.getLIR().addSlowPath(op, () -> {
                masm.bind(entry);

                if (frameMap != null) {
                    AArch64HotSpotBackend.rawEnter(crb, frameMap, masm, config, false);
                }

                CallingConvention cc = callTarget.getOutgoingCallingConvention();
                AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                AArch64Address cArg1 = (AArch64Address) crb.asAddress(cc.getArgument(1));

                masm.str(64, ref, cArg0);
                Register addressReg;
                if (address.isBaseRegisterOnly()) {
                    // Can directly use the base register as the address
                    addressReg = address.getBase();
                } else {
                    addressReg = ref;
                    masm.loadAddress(ref, address);
                }
                masm.str(64, addressReg, cArg1);
                AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : scratch1, null);
                masm.ldr(64, ref, cArg0);

                if (frameMap != null) {
                    AArch64HotSpotBackend.rawLeave(crb, config);
                }

                masm.jmp(continuation);
            });
        }
    }

    @Override
    public Variable emitBarrieredLoad(LIRGeneratorTool tool,
                    LIRKind kind,
                    Value address,
                    LIRFrameState state,
                    MemoryOrderMode memoryOrder,
                    BarrierType barrierType) {
        if (kind.getPlatformKind().getVectorLength() == 1) {
            GraalError.guarantee(kind.getPlatformKind() == AArch64Kind.QWORD, "ZGC only uses uncompressed oops: %s", kind);

            ForeignCallLinkage callTarget = getReadBarrierStub(barrierType);
            AArch64AddressValue loadAddress = ((AArch64LIRGenerator) tool).asAddressValue(address, 64);
            Variable result = tool.newVariable(tool.toRegisterKind(kind));
            tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            boolean isNotStrong = barrierType == BarrierType.REFERENCE_GET || barrierType == BarrierType.WEAK_REFERS_TO || barrierType == BarrierType.PHANTOM_REFERS_TO;
            tool.append(new AArch64HotSpotZReadBarrierOp(result, loadAddress, memoryOrder, state, config, callTarget, isNotStrong));
            return result;
        }
        throw GraalError.shouldNotReachHere("unhandled");
    }

    @Override
    public void emitCompareAndSwapOp(LIRGeneratorTool tool,
                    boolean isLogicVariant,
                    Value address,
                    MemoryOrderMode memoryOrder,
                    AArch64Kind memKind,
                    Variable result,
                    AllocatableValue allocatableExpectedValue,
                    AllocatableValue allocatableNewValue,
                    BarrierType barrierType) {
        ForeignCallLinkage callTarget = getWriteBarrierStub(barrierType, StoreKind.Atomic);
        AllocatableValue temp = tool.newVariable(tool.toRegisterKind(LIRKind.value(memKind)));
        tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        tool.append(new AArch64HotSpotZCompareAndSwapOp(isLogicVariant, memKind, memoryOrder, isLogicVariant, result,
                        allocatableExpectedValue, allocatableNewValue, tool.asAllocatable(address), config, callTarget, temp));
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRGeneratorTool tool,
                    LIRKind accessKind,
                    Value address,
                    Value newValue,
                    BarrierType barrierType) {
        Variable result = tool.newVariable(tool.toRegisterKind(accessKind));
        GraalError.guarantee(accessKind.getPlatformKind() == AArch64Kind.QWORD, "unexpected kind for ZGC");
        ForeignCallLinkage callTarget = getWriteBarrierStub(barrierType, StoreKind.Atomic);
        tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        tool.append(new AArch64HotSpotZAtomicReadAndWriteOp((AArch64Kind) accessKind.getPlatformKind(), result, tool.asAllocatable(address),
                        tool.asAllocatable(newValue), config, callTarget));
        return result;
    }

    @Override
    public void emitStore(LIRGeneratorTool tool,
                    ValueKind<?> lirKind,
                    BarrierType barrierType,
                    Value address,
                    Value value,
                    LIRFrameState state,
                    MemoryOrderMode memoryOrder,
                    LocationIdentity locationIdentity) {
        StoreKind storeKind = locationIdentity instanceof HotSpotReplacementsUtil.OopHandleLocationIdentity ? StoreKind.Native : StoreKind.Normal;
        LIRKind accessKind = (LIRKind) value.getValueKind();
        AArch64Kind kind = (AArch64Kind) value.getPlatformKind();
        Variable result = tool.newVariable(accessKind);
        AArch64AddressValue addressValue = ((AArch64LIRGenerator) tool).asAddressValue(address, 64);
        AllocatableValue tmp = tool.newVariable(tool.toRegisterKind(accessKind));
        AllocatableValue tmp2 = tool.newVariable(tool.toRegisterKind(accessKind));
        GraalError.guarantee(kind == AArch64Kind.QWORD, "unexpected kind for ZGC");
        ForeignCallLinkage callTarget = getWriteBarrierStub(barrierType, storeKind);
        tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        tool.append(new AArch64HotSpotZPreWriteBarrierOp(tool.asAllocatable(value), addressValue, tmp, tmp2, config, callTarget, result, storeKind,
                        locationIdentity.isInit() && barrierType != BarrierType.POST_INIT_WRITE, state));
        tool.getArithmetic().emitStore(lirKind, address, result, state, memoryOrder);
    }
}
