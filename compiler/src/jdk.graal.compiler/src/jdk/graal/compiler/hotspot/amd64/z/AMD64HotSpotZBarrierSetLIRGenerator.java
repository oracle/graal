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
package jdk.graal.compiler.hotspot.amd64.z;

import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.Equal;
import static jdk.graal.compiler.asm.amd64.AMD64Assembler.ConditionFlag.NotEqual;
import static jdk.graal.compiler.asm.amd64.AMD64BaseAssembler.OperandSize.QWORD;
import static jdk.graal.compiler.core.common.GraalOptions.AssemblyGCBarriersSlowPathOnly;
import static jdk.graal.compiler.lir.LIRValueUtil.asJavaConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isJavaConstant;
import static jdk.vm.ci.amd64.AMD64.r15;
import static jdk.vm.ci.amd64.AMD64.rax;
import static jdk.vm.ci.amd64.AMD64.rbx;
import static jdk.vm.ci.amd64.AMD64.rcx;

import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.asm.Assembler;
import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64Assembler.AMD64SIMDInstructionEncoding;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.amd64.AMD64LIRGenerator;
import jdk.graal.compiler.core.amd64.AMD64ReadBarrierSetLIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.Stride;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotMarkId;
import jdk.graal.compiler.hotspot.ZWriteBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.hotspot.amd64.AMD64HotSpotBackend;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64BinaryConsumer;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;
import jdk.vm.ci.meta.ValueKind;

/**
 * HotSpot amd64 specific code generation for Generational ZGC barriers. See
 * {@link ZWriteBarrierSetLIRGeneratorTool} for details about ZGC pointers and the SyncPort
 * definition for links to the matching code.
 */

public class AMD64HotSpotZBarrierSetLIRGenerator implements AMD64ReadBarrierSetLIRGenerator, ZWriteBarrierSetLIRGeneratorTool {

    public AMD64HotSpotZBarrierSetLIRGenerator(GraalHotSpotVMConfig config, Providers providers) {
        this.config = config;
        this.providers = providers;
    }

    private final GraalHotSpotVMConfig config;
    private final Providers providers;

    @Override
    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    /**
     * Convert a normal oop into a colored pointer.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/z_x86_64.ad#L37-L42", sha1 = "344c51c07478c916bdaabb0c697a053e7a2f64dd")
    public static void zColor(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register ref) {
        crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_SHL);
        masm.shlq(ref, UNPATCHED);
        masm.orqImm32(ref, UNPATCHED);
        crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_OR);
    }

    /**
     * Convert a colored pointer into normal oop.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/z_x86_64.ad#L44-L47", sha1 = "5024a425db7a0d1504713ad9029a68da6089967f")
    public static void zUncolor(CompilationResultBuilder crb, AMD64MacroAssembler masm, Register ref) {
        crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_LOAD_GOOD_BEFORE_SHL);
        masm.shrq(ref, UNPATCHED);
    }

    /**
     * Emit the full store barrier with a fast path, and an out of line medium path with a final
     * slow path call to the runtime.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/zBarrierSetAssembler_x86.cpp#L304-L321", sha1 = "9a628c1771df79ae8b4cee89d2863fbd4a4964bc")
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/zBarrierSetAssembler_x86.cpp#L370-L414", sha1 = "7688e7aeab5f1aa413690066355a17c18a4273fa")
    public static void emitStoreBarrier(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    LIRInstruction op,
                    GraalHotSpotVMConfig config,
                    AMD64Address address,
                    Register result,
                    Register writeValue,
                    StoreKind storeKind,
                    Register tmp,
                    Register tmp2,
                    ForeignCallLinkage callTarget,
                    LIRFrameState state,
                    boolean isInitMemory) {
        // This is the label for the out of line handling, starting at the medium path
        final Label mediumPath = new Label();
        // Label for the return to main line execution
        final Label mediumPathContinuation = new Label();

        if (isInitMemory) {
            masm.movq(result, writeValue);
            zColor(crb, masm, result);
        } else {
            // Possible jump to mediumPath and binds mediumPathContinuation
            if (writeValue != null) {
                Assembler.guaranteeDifferentRegisters(writeValue, result);
            }
            Assembler.guaranteeDifferentRegisters(result, address.getBase(), address.getIndex());
            if (storeKind == StoreKind.Atomic) {
                /*
                 * Atomic operations must ensure that the contents of memory are store-good before
                 * an atomic operation can execute. A not relocatable object could have spurious raw
                 * null pointers in its fields after getting promoted to the old generation.
                 */
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                masm.cmpwImm16(address, UNPATCHED);
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_CMP);
            } else {
                /*
                 * Stores on relocatable objects never need to deal with raw null pointers in
                 * fields. Raw null pointers may only exist in the young generation, as they get
                 * pruned when the object is relocated to old. And no pre-write barrier needs to
                 * perform any action in the young generation.
                 */
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                masm.testl(address, UNPATCHED);
                crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_BAD_AFTER_TEST);
            }
            masm.jcc(NotEqual, mediumPath);
            masm.bind(mediumPathContinuation);
            if (writeValue != null) {
                masm.movq(result, writeValue);
                zColor(crb, masm, result);
            }
            crb.getLIR().addSlowPath(op, () -> {
                masm.bind(mediumPath);

                Label slow = new Label();
                Label slowContinuation = new Label();
                storeBarrierMedium(crb, masm, address,
                                tmp, tmp2,
                                storeKind,
                                mediumPathContinuation,
                                slow,
                                slowContinuation, config);

                masm.bind(slow);

                masm.leaq(tmp, address);

                CallingConvention cc = callTarget.getOutgoingCallingConvention();
                AMD64Address cArg0 = (AMD64Address) crb.asAddress(cc.getArgument(0));

                masm.movq(cArg0, tmp);
                AMD64Call.directCall(crb, masm, callTarget, null, false, null);
                assert cc.getReturn().equals(Value.ILLEGAL) : cc + " " + callTarget;

                // Stub exit
                masm.jmp(slowContinuation);
            });
        }
    }

    /**
     * Try to perform any local store barrier fixups or dispatch to the slow path.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/zBarrierSetAssembler_x86.cpp#L450-L505", sha1 = "4b729acf92e6a297229b7f1e957601708c315f4f")
    static void storeBarrierMedium(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    AMD64Address address,
                    Register tmp,
                    Register tmp2,
                    StoreKind storeKind,
                    Label mediumPathContinuation,
                    Label slowPath,
                    Label slowPathContinuation,
                    GraalHotSpotVMConfig config) {
        // The reason to end up in the medium path is that the pre-value was not 'good'.
        if (storeKind == StoreKind.Native) {
            masm.jmp(slowPath);
            masm.bind(slowPathContinuation);
            masm.jmp(mediumPathContinuation);
        } else if (storeKind == StoreKind.Atomic) {
            // Atomic accesses can get to the medium fast path because the value was a
            // raw null value. If it was not null, then there is no doubt we need to take a slow
            // path.
            masm.cmpq(address, 0);
            masm.jcc(NotEqual, slowPath);

            // If we get this far, we know there is a young raw null value in the field.
            // Try to self-heal null values for atomic accesses
            masm.push(rax);
            masm.push(rbx);
            masm.push(rcx);

            masm.leaq(rcx, address);
            masm.xorq(rax, rax);
            masm.movptr(rbx, new AMD64Address(r15, config.ZThreadLocalData_store_good_mask_offset));

            masm.lock();
            masm.cmpxchgq(rbx, new AMD64Address(rcx, 0));

            masm.pop(rcx);
            masm.pop(rbx);
            masm.pop(rax);

            masm.jcc(NotEqual, slowPath);

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
                storeBarrierBufferAdd(masm,
                                address,
                                tmp, tmp2,
                                slowPath, config);
            }
            masm.bind(slowPathContinuation);
            masm.jmp(mediumPathContinuation);
        }
    }

    /**
     * Add a value to the store buffer.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/zBarrierSetAssembler_x86.cpp#L416-L448", sha1 = "638b10c65bb14fa4b254efa4d5bbb1751fdbb6bf")
    static void storeBarrierBufferAdd(AMD64MacroAssembler masm,
                    AMD64Address address,
                    Register tmp1,
                    Register tmp2,
                    Label slowPath,
                    GraalHotSpotVMConfig config) {
        AMD64Address buffer = new AMD64Address(r15, config.ZThreadLocalData_store_barrier_buffer_offset);

        masm.movptr(tmp1, buffer);

        // Combined pointer bump and check if the buffer is disabled or full
        masm.cmpq(new AMD64Address(tmp1, config.ZStoreBarrierBuffer_current_offset), 0);
        masm.jcc(Equal, slowPath);

        // Bump the pointer
        masm.movq(tmp2, new AMD64Address(tmp1, config.ZStoreBarrierBuffer_current_offset));
        masm.subq(tmp2, config.sizeofZStoreBarrierEntry);
        masm.movq(new AMD64Address(tmp1, config.ZStoreBarrierBuffer_current_offset), tmp2);

        // Compute the buffer entry address
        masm.leaq(tmp2, new AMD64Address(tmp1, tmp2, Stride.S1, config.ZStoreBarrierBuffer_buffer_offset));

        // Compute and log the store address
        masm.leaq(tmp1, address);
        masm.movptr(new AMD64Address(tmp2, (config.ZStoreBarrierEntry_p_offset)), tmp1);

        // Load and log the prev value
        masm.movptr(tmp1, new AMD64Address(tmp1, 0));
        masm.movptr(new AMD64Address(tmp2, (config.ZStoreBarrierEntry_prev_offset)), tmp1);
    }

    /**
     * Emits the basic Z read barrier pattern with some customization. Normally this code is used
     * from a {@link LIRInstruction} where the frame has already been set up. If an
     * {@code frameContext} is passed in then a frame will be setup and torn down around the call.
     * The call itself is done with a special stack-only calling convention that saves and restores
     * all registers around the call. This simplifies the code generation as no extra registers are
     * required.
     */
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/4acafb809c66589fbbfee9c9a4ba7820f848f0e4/src/hotspot/cpu/x86/gc/z/zBarrierSetAssembler_x86.cpp#L219-L302", sha1 = "16f5bff0a0f68ae40be8dd980b7728d7ee60cd2c")
    public static void emitLoadBarrier(CompilationResultBuilder crb,
                    AMD64MacroAssembler masm,
                    Register resultReg,
                    ForeignCallLinkage callTarget,
                    AMD64Address address,
                    LIRInstruction op,
                    AMD64HotSpotBackend.HotSpotFrameContext frameContext,
                    boolean isNotStrong) {
        assert !resultReg.equals(address.getBase()) && !resultReg.equals(address.getIndex()) : Assertions.errorMessage(resultReg, address);

        final Label entryPoint = new Label();
        final Label continuation = new Label();

        if (isNotStrong) {
            masm.testl(resultReg, UNPATCHED);
            crb.recordMark(HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_MARK_BAD_AFTER_TEST);
            masm.jcc(AMD64Assembler.ConditionFlag.NotZero, entryPoint);
            zUncolor(crb, masm, resultReg);
        } else {
            zUncolor(crb, masm, resultReg);
            masm.jcc(AMD64Assembler.ConditionFlag.Above, entryPoint);
        }
        crb.getLIR().addSlowPath(op, () -> {
            masm.bind(entryPoint);

            if (frameContext != null) {
                frameContext.rawEnter(crb);
            }

            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            AMD64Address cArg0 = (AMD64Address) crb.asAddress(cc.getArgument(0));
            AMD64Address cArg1 = (AMD64Address) crb.asAddress(cc.getArgument(1));

            // The fast-path shift destroyed the oop - need to re-read it
            masm.movq(resultReg, address);

            masm.movq(cArg0, resultReg);
            masm.leaq(resultReg, address);
            masm.movq(cArg1, resultReg);
            AMD64Call.directCall(crb, masm, callTarget, null, false, null);
            masm.movq(resultReg, cArg0);

            if (frameContext != null) {
                frameContext.rawLeave(crb);
            }

            // Return to inline code
            masm.jmp(continuation);
        });
        masm.bind(continuation);
    }

    @Override
    public Variable emitBarrieredLoad(LIRGeneratorTool tool,
                    LIRKind kind,
                    Value address,
                    LIRFrameState state,
                    MemoryOrderMode memoryOrder,
                    BarrierType barrierType) {
        if (kind.getPlatformKind().getVectorLength() == 1) {
            GraalError.guarantee(kind.getPlatformKind() == AMD64Kind.QWORD, "ZGC only uses uncompressed oops: %s", kind);

            ForeignCallLinkage callTarget = getReadBarrierStub(barrierType);
            AMD64AddressValue loadAddress = ((AMD64LIRGenerator) tool).asAddressValue(address);
            Variable result = tool.newVariable(tool.toRegisterKind(kind));
            tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            boolean isNotStrong = barrierType == BarrierType.REFERENCE_GET || barrierType == BarrierType.WEAK_REFERS_TO || barrierType == BarrierType.PHANTOM_REFERS_TO;
            tool.append(new AMD64HotSpotZReadBarrierOp(result, loadAddress, state, config, callTarget, isNotStrong));
            return result;
        }
        if (kind.getPlatformKind().getVectorLength() > 1) {
            // Emit a vector barrier
            assert barrierType == BarrierType.READ : Assertions.errorMessage(barrierType);
            ForeignCallLinkage callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_ARRAY_BARRIER);
            AMD64AddressValue loadAddress = ((AMD64LIRGenerator) tool).asAddressValue(address);
            Variable result = tool.newVariable(tool.toRegisterKind(kind));

            AMD64Assembler.VexMoveOp op = AMD64VectorMove.getVectorMemMoveOp((AMD64Kind) kind.getPlatformKind(),
                            AMD64SIMDInstructionEncoding.forFeatures(((AMD64) tool.target().arch).getFeatures()));
            Variable temp = tool.newVariable(tool.toRegisterKind(kind));
            tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            tool.append(new AMD64HotSpotZVectorReadBarrierOp(AVXKind.getRegisterSize((AMD64Kind) kind.getPlatformKind()), op, result, loadAddress, state, config, callTarget, temp));
            return result;
        }
        throw GraalError.shouldNotReachHere("unhandled barrier");
    }

    @Override
    public void emitCompareAndSwapOp(LIRGeneratorTool tool,
                    boolean isLogic,
                    LIRKind accessKind,
                    AMD64Kind memKind,
                    RegisterValue raxValue,
                    AMD64AddressValue address,
                    AllocatableValue newValue,
                    BarrierType barrierType) {
        ForeignCallLinkage callTarget = getWriteBarrierStub(barrierType, StoreKind.Atomic);
        assert memKind == accessKind.getPlatformKind() : Assertions.errorMessage(memKind, accessKind, raxValue, address, newValue);
        AllocatableValue tmp = tool.newVariable(tool.toRegisterKind(accessKind));
        AllocatableValue tmp2 = tool.newVariable(tool.toRegisterKind(accessKind));
        tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        tool.append(new AMD64HotSpotZCompareAndSwapOp(isLogic, memKind, raxValue, address, raxValue, tool.asAllocatable(newValue), tmp, tmp2, config, callTarget));
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRGeneratorTool tool,
                    LIRKind accessKind,
                    Value address,
                    Value newValue,
                    BarrierType barrierType) {
        AMD64Kind kind = (AMD64Kind) accessKind.getPlatformKind();
        GraalError.guarantee(barrierType == BarrierType.FIELD || barrierType == BarrierType.ARRAY, "unexpected type for barrier: %s", barrierType);
        Variable result = tool.newVariable(accessKind);
        AMD64AddressValue addressValue = ((AMD64LIRGenerator) tool).asAddressValue(address);
        AllocatableValue tmp = tool.newVariable(tool.toRegisterKind(accessKind));
        AllocatableValue tmp2 = tool.newVariable(tool.toRegisterKind(accessKind));
        GraalError.guarantee(kind == AMD64Kind.QWORD, "unexpected kind for ZGC");
        ForeignCallLinkage callTarget = getWriteBarrierStub(barrierType, StoreKind.Atomic);
        tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        tool.append(new AMD64HotSpotZAtomicReadAndWriteOp(result, addressValue, tool.asAllocatable(newValue), tmp, tmp2, config, callTarget));
        return result;
    }

    /**
     * Efficiently store a color null value.
     */
    static class ZStoreNullOp extends AMD64BinaryConsumer.MemoryConstOp {
        public static final LIRInstructionClass<ZStoreNullOp> TYPE = LIRInstructionClass.create(ZStoreNullOp.class);

        ZStoreNullOp(AMD64BaseAssembler.OperandSize size, AMD64AddressValue x, LIRFrameState state) {
            super(TYPE, AMD64Assembler.AMD64MIOp.MOV, size, x, 0, state);
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            super.emitCode(crb, masm);
            /*
             * The relocation can't be fully after the mov, as that is the beginning of a random
             * subsequent instruction, which violates assumptions made by unrelated code. Hence the
             * masm.position() - 1
             */
            crb.recordMark(masm.position() - 1, HotSpotMarkId.Z_BARRIER_RELOCATION_FORMAT_STORE_GOOD_AFTER_MOV);
        }
    }

    @Override
    public void emitStore(LIRGeneratorTool lirTool,
                    ValueKind<?> lirKind,
                    BarrierType barrierType,
                    Value address,
                    Value value,
                    LIRFrameState state,
                    MemoryOrderMode memoryOrder,
                    LocationIdentity location) {
        AMD64LIRGenerator tool = (AMD64LIRGenerator) lirTool;
        AMD64AddressValue storeAddress = tool.asAddressValue(address);
        AMD64Kind kind = (AMD64Kind) lirKind.getPlatformKind();
        GraalError.guarantee(kind == AMD64Kind.QWORD, "unexpected kind for ZGC");

        boolean isConstantNull = isJavaConstant(value) && asJavaConstant(value).isDefaultForKind();
        Value writeValue = value;
        if (!location.isInit() || !isConstantNull) {
            StoreKind storeKind = location instanceof HotSpotReplacementsUtil.OopHandleLocationIdentity ? StoreKind.Native : StoreKind.Normal;
            LIRKind accessKind = (LIRKind) value.getValueKind();
            Variable result = tool.newVariable(accessKind);
            AMD64AddressValue addressValue = tool.asAddressValue(address);
            AllocatableValue tmp = tool.newVariable(tool.toRegisterKind(accessKind));
            AllocatableValue tmp2 = tool.newVariable(tool.toRegisterKind(accessKind));
            ForeignCallLinkage callTarget = getWriteBarrierStub(barrierType, storeKind);
            tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            tool.append(new AMD64HotSpotZPreWriteBarrierOp(tool.asAllocatable(value), addressValue, tmp, tmp2, config, callTarget, result, storeKind,
                            location.isInit() && barrierType != BarrierType.POST_INIT_WRITE, state));
            writeValue = result;
        }
        if (isConstantNull) {
            tool.append(new ZStoreNullOp(QWORD, storeAddress, state));
        } else {
            tool.getArithmetic().emitStore(lirKind, address, writeValue, state, memoryOrder);
        }
    }
}
