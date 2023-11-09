/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.vm.ci.amd64.AMD64.r15;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.asm.amd64.AVXKind;
import jdk.graal.compiler.core.amd64.AMD64BarrierSetLIRGenerator;
import jdk.graal.compiler.core.amd64.AMD64LIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64FrameMap;
import jdk.graal.compiler.lir.amd64.vector.AMD64VectorMove;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.amd64.AMD64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

/**
 * HotSpot specific code generation for ZGC read barriers.
 */
public class AMD64HotSpotZBarrierSetLIRGenerator extends AMD64BarrierSetLIRGenerator {

    public AMD64HotSpotZBarrierSetLIRGenerator(GraalHotSpotVMConfig config, Providers providers) {
        this.config = config;
        this.providers = providers;
    }

    public AMD64LIRGenerator getAMD64LIRGen() {
        return (AMD64LIRGenerator) lirGen;
    }

    private final GraalHotSpotVMConfig config;
    private final Providers providers;

    public ForeignCallsProvider getForeignCalls() {
        return providers.getForeignCalls();
    }

    /**
     * Emits the basic Z read barrier pattern with some customization. Normally this code is used
     * from a {@link LIRInstruction} where the frame has already been set up. If an
     * {@link AMD64FrameMap} is passed then a frame will be setup and torn down around the call. The
     * call itself is done with a special stack-only calling convention that saves and restores all
     * registers around the call. This simplifies the code generation as no extra registers are
     * required.
     */
    public static void emitBarrier(CompilationResultBuilder crb, AMD64MacroAssembler masm, Label success, Register resultReg, GraalHotSpotVMConfig config, ForeignCallLinkage callTarget,
                    AMD64Address address, LIRInstruction op, AMD64HotSpotBackend.HotSpotFrameContext frameContext) {
        assert !resultReg.equals(address.getBase()) && !resultReg.equals(address.getIndex()) : Assertions.errorMessage(resultReg, address);

        final Label entryPoint = new Label();
        final Label continuation = new Label();

        masm.testq(resultReg, new AMD64Address(r15, config.threadAddressBadMaskOffset));
        if (success != null) {
            masm.jcc(AMD64Assembler.ConditionFlag.Zero, success);
            masm.jmp(entryPoint);
        } else {
            masm.jcc(AMD64Assembler.ConditionFlag.NotZero, entryPoint);
        }
        crb.getLIR().addSlowPath(op, () -> {
            masm.bind(entryPoint);

            if (frameContext != null) {
                frameContext.rawEnter(crb);
            }

            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            AMD64Address cArg0 = (AMD64Address) crb.asAddress(cc.getArgument(0));
            AMD64Address cArg1 = (AMD64Address) crb.asAddress(cc.getArgument(1));

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
    public Variable emitBarrieredLoad(LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        if (kind.getPlatformKind().getVectorLength() == 1) {
            GraalError.guarantee(kind.getPlatformKind() == AMD64Kind.QWORD, "ZGC only uses uncompressed oops: %s", kind);

            ForeignCallLinkage callTarget = getBarrierStub(barrierType);
            AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
            Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));
            getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            getLIRGen().append(new AMD64HotSpotZReadBarrierOp(result, loadAddress, state, config, callTarget));
            return result;
        }
        if (kind.getPlatformKind().getVectorLength() > 1) {
            // Emit a vector barrier
            assert barrierType == BarrierType.READ : Assertions.errorMessage(barrierType);
            ForeignCallLinkage callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_ARRAY_BARRIER);
            AMD64AddressValue loadAddress = getAMD64LIRGen().asAddressValue(address);
            Variable result = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));

            AMD64Assembler.VexMoveOp op = AMD64VectorMove.getVectorMemMoveOp((AMD64Kind) kind.getPlatformKind());
            Variable temp = getLIRGen().newVariable(getLIRGen().toRegisterKind(kind));
            getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
            getLIRGen().append(new AMD64HotSpotZVectorReadBarrierOp(AVXKind.getRegisterSize((AMD64Kind) kind.getPlatformKind()), op, result, loadAddress, state, config, callTarget, temp));
            return result;
        }
        throw GraalError.shouldNotReachHere("unhandled barrier");
    }

    public ForeignCallLinkage getBarrierStub(BarrierType barrierType) {
        ForeignCallLinkage callTarget;
        switch (barrierType) {
            case READ:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_FIELD_BARRIER);
                break;
            case REFERENCE_GET:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_REFERENCE_GET_BARRIER);
                break;
            case WEAK_REFERS_TO:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_WEAK_REFERS_TO_BARRIER);
                break;
            case PHANTOM_REFERS_TO:
                callTarget = getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.Z_PHANTOM_REFERS_TO_BARRIER);
                break;
            default:
                throw GraalError.shouldNotReachHere("Unexpected barrier type: " + barrierType);
        }
        return callTarget;
    }

    @Override
    public void emitCompareAndSwapOp(LIRKind accessKind, AMD64Kind memKind, RegisterValue raxValue, AMD64AddressValue address, AllocatableValue newValue, BarrierType barrierType) {
        ForeignCallLinkage callTarget = getBarrierStub(barrierType);
        assert memKind == accessKind.getPlatformKind() : Assertions.errorMessage(memKind, accessKind, raxValue, address, newValue);
        AllocatableValue temp = getLIRGen().newVariable(getLIRGen().toRegisterKind(accessKind));
        getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        getLIRGen().append(new AMD64HotSpotZCompareAndSwapOp(memKind, raxValue, address, raxValue, getLIRGen().asAllocatable(newValue), temp, config, callTarget));
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRKind accessKind, Value address, Value newValue, BarrierType barrierType) {
        AMD64Kind kind = (AMD64Kind) accessKind.getPlatformKind();
        GraalError.guarantee(barrierType == BarrierType.READ, "unexpected type for barrier: %s", barrierType);
        Variable result = getLIRGen().newVariable(accessKind);
        AMD64AddressValue addressValue = getAMD64LIRGen().asAddressValue(address);
        GraalError.guarantee(kind == AMD64Kind.QWORD, "unexpected kind for ZGC");
        ForeignCallLinkage callTarget = getBarrierStub(barrierType);
        getLIRGen().getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        getLIRGen().append(new AMD64HotSpotZAtomicReadAndWriteOp(result, addressValue, getLIRGen().asAllocatable(newValue), config, callTarget));
        return result;
    }
}
