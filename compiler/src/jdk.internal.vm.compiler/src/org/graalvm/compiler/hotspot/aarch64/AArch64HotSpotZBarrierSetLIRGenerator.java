/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_FIELD_BARRIER;
import static org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_PHANTOM_REFERS_TO_BARRIER;
import static org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_REFERENCE_GET_BARRIER;
import static org.graalvm.compiler.hotspot.meta.HotSpotHostForeignCallsProvider.Z_WEAK_REFERS_TO_BARRIER;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Address;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.core.aarch64.AArch64BarrierSetLIRGenerator;
import org.graalvm.compiler.core.aarch64.AArch64LIRGenerator;
import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.memory.BarrierType;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.core.common.spi.ForeignCallLinkage;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotProviders;
import org.graalvm.compiler.lir.LIRFrameState;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.aarch64.AArch64AddressValue;
import org.graalvm.compiler.lir.aarch64.AArch64Call;
import org.graalvm.compiler.lir.aarch64.AArch64FrameMap;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;

import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.aarch64.AArch64HotSpotRegisterConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * HotSpot specific code generation for ZGC read barriers.
 */
public class AArch64HotSpotZBarrierSetLIRGenerator extends AArch64BarrierSetLIRGenerator {

    public AArch64HotSpotZBarrierSetLIRGenerator(GraalHotSpotVMConfig config, HotSpotProviders providers) {
        this.config = config;
        this.providers = providers;
    }

    public AArch64LIRGenerator getAArch64LIRGen() {
        return (AArch64LIRGenerator) lirGen;
    }

    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    /**
     * Emits the basic Z read barrier pattern with some customization. Normally this code is used
     * from a {@link LIRInstruction} where the frame has already been set up. If an
     * {@link AArch64FrameMap} is passed then a frame will be setup and torn down around the call.
     * The call itself is done with a special stack-only calling convention that saves and restores
     * all registers around the call. This simplifies the code generation as no extra registers are
     * required.
     */
    public static void emitBarrier(CompilationResultBuilder crb, AArch64MacroAssembler masm, Label success, Register resultReg, GraalHotSpotVMConfig config, ForeignCallLinkage callTarget,
                    AArch64Address address, LIRInstruction op, AArch64FrameMap frameMap) {
        assert !resultReg.equals(address.getBase()) && !resultReg.equals(address.getOffset());

        final Label entryPoint = new Label();
        final Label continuation = new Label();

        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            Register scratch1 = sc1.getRegister();

            Register thread = AArch64HotSpotRegisterConfig.threadRegister;
            AArch64Address badMask = masm.makeAddress(64, thread, config.threadAddressBadMaskOffset, scratch1);
            masm.ldr(64, scratch1, badMask);
            if (success == null) {
                masm.tst(64, scratch1, resultReg);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, entryPoint);
            } else {
                // In this case the pattern is embedded inside another sequence so use a pattern
                // which doesn't screw with the condition codes. It also assumes that the label is
                // close enough that the reach of cbz is sufficient.
                masm.and(64, scratch1, scratch1, resultReg);
                masm.cbz(64, scratch1, success);
                masm.jmp(entryPoint);
            }
            crb.getLIR().addSlowPath(op, () -> {
                masm.bind(entryPoint);

                if (frameMap != null) {
                    AArch64HotSpotBackend.rawEnter(crb, frameMap, masm, config);
                }

                CallingConvention cc = callTarget.getOutgoingCallingConvention();
                AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                AArch64Address cArg1 = (AArch64Address) crb.asAddress(cc.getArgument(1));

                masm.str(64, resultReg, cArg0);
                Register addressReg;
                if (address.isBaseRegisterOnly()) {
                    // Can directly use the base register as the address
                    addressReg = address.getBase();
                } else {
                    addressReg = resultReg;
                    masm.loadAddress(resultReg, address);
                }
                masm.str(64, addressReg, cArg1);
                AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : scratch1, null);
                masm.ldr(64, resultReg, cArg0);

                if (frameMap != null) {
                    AArch64HotSpotBackend.rawLeave(crb, config);
                }

                // Return to inline code
                masm.jmp(continuation);
            });
            masm.bind(continuation);
        }
    }

    @Override
    public Variable emitBarrieredLoad(LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        if (kind.getPlatformKind().getVectorLength() == 1) {
            GraalError.guarantee(kind.getPlatformKind() == AArch64Kind.QWORD, "ZGC only uses uncompressed oops: %s", kind);

            ForeignCallLinkage callTarget = getBarrierStub(barrierType);
            AArch64AddressValue loadAddress = getAArch64LIRGen().asAddressValue(address, 64);
            Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));
            getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            getLIRGen().append(new AArch64HotSpotZReadBarrierOp(result, loadAddress, memoryOrder, state, config, callTarget));
            return result;
        }
        throw GraalError.shouldNotReachHere("unhandled");
    }

    public ForeignCallLinkage getBarrierStub(BarrierType barrierType) {
        ForeignCallLinkage callTarget;
        switch (barrierType) {
            case READ:
                callTarget = getForeignCalls().lookupForeignCall(Z_FIELD_BARRIER);
                break;
            case REFERENCE_GET:
                callTarget = getForeignCalls().lookupForeignCall(Z_REFERENCE_GET_BARRIER);
                break;
            case WEAK_REFERS_TO:
                callTarget = getForeignCalls().lookupForeignCall(Z_WEAK_REFERS_TO_BARRIER);
                break;
            case PHANTOM_REFERS_TO:
                callTarget = getForeignCalls().lookupForeignCall(Z_PHANTOM_REFERS_TO_BARRIER);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected barrier type: " + barrierType);
        }
        return callTarget;
    }

    @Override
    public void emitCompareAndSwapOp(boolean isLogicVariant, Value address, MemoryOrderMode memoryOrder, AArch64Kind memKind, Variable result, AllocatableValue allocatableExpectedValue,
                    AllocatableValue allocatableNewValue, BarrierType barrierType) {
        ForeignCallLinkage callTarget = getBarrierStub(barrierType);
        AllocatableValue temp = getLIRGen().newVariable(getLIRGen().toRegisterKind(LIRKind.value(memKind)));
        getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        getLIRGen().append(new AArch64HotSpotZCompareAndSwapOp(memKind, memoryOrder, isLogicVariant, result,
                        allocatableExpectedValue, allocatableNewValue, getLIRGen().asAllocatable(address), config, callTarget, temp));
    }

    AllocatableValue asAllocatable(Value value) {
        return getLIRGen().asAllocatable(value);
    }

    public <I extends LIRInstruction> I append(I op) {
        return getLIRGen().append(op);
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue, BarrierType barrierType) {
        GraalError.guarantee(barrierType == BarrierType.READ, "unexpected type for barrier: %s", barrierType);
        Variable result = lirGen.newVariable(lirGen.toRegisterKind(accessKind));
        GraalError.guarantee(accessKind.getPlatformKind() == AArch64Kind.QWORD, "unexpected kind for ZGC");
        ForeignCallLinkage callTarget = getBarrierStub(barrierType);
        getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        append(new AArch64HotSpotZAtomicReadAndWriteOp((AArch64Kind) accessKind.getPlatformKind(), result, asAllocatable(address), asAllocatable(newValue), config, callTarget));
        return result;
    }
}
