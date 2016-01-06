/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.aarch64;

import com.oracle.graal.asm.aarch64.AArch64Address;
import com.oracle.graal.compiler.aarch64.AArch64ArithmeticLIRGenerator;
import com.oracle.graal.compiler.aarch64.AArch64LIRGenerator;
import com.oracle.graal.compiler.common.spi.ForeignCallLinkage;
import com.oracle.graal.compiler.common.spi.LIRKindTool;
import com.oracle.graal.hotspot.HotSpotBackend;
import com.oracle.graal.hotspot.HotSpotLIRGenerator;
import com.oracle.graal.hotspot.HotSpotLockStack;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.stubs.Stub;
import com.oracle.graal.lir.LIRFrameState;
import com.oracle.graal.lir.StandardOp.SaveRegistersOp;
import com.oracle.graal.lir.Variable;
import com.oracle.graal.lir.VirtualStackSlot;
import com.oracle.graal.lir.aarch64.AArch64AddressValue;
import com.oracle.graal.lir.aarch64.AArch64Move;
import com.oracle.graal.lir.gen.LIRGenerationResult;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterValue;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.hotspot.HotSpotVMConfig;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.LIRKind;
import jdk.vm.ci.meta.Value;

/**
 * LIR generator specialized for AArch64 HotSpot.
 */
public class AArch64HotSpotLIRGenerator extends AArch64LIRGenerator implements HotSpotLIRGenerator {

    final HotSpotVMConfig config;
    private HotSpotLockStack lockStack;

    protected AArch64HotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes) {
        this(providers, config, cc, lirGenRes, new ConstantTableBaseProvider());
    }

    private AArch64HotSpotLIRGenerator(HotSpotProviders providers, HotSpotVMConfig config, CallingConvention cc, LIRGenerationResult lirGenRes, ConstantTableBaseProvider constantTableBaseProvider) {
        this(new AArch64HotSpotLIRKindTool(), new AArch64ArithmeticLIRGenerator(), new AArch64HotSpotMoveFactory(providers.getCodeCache(), constantTableBaseProvider), providers, config, cc,
                        lirGenRes, constantTableBaseProvider);
    }

    protected AArch64HotSpotLIRGenerator(LIRKindTool lirKindTool, AArch64ArithmeticLIRGenerator arithmeticLIRGen, MoveFactory moveFactory, HotSpotProviders providers, HotSpotVMConfig config,
                    CallingConvention cc, LIRGenerationResult lirGenRes, ConstantTableBaseProvider constantTableBaseProvider) {
        super(lirKindTool, arithmeticLIRGen, moveFactory, providers, cc, lirGenRes, constantTableBaseProvider);
        this.config = config;
    }

    @Override
    public boolean needOnlyOopMaps() {
        // Stubs only need oop maps
        return ((AArch64HotSpotLIRGenerationResult) getResult()).getStub() != null;
    }

    @Override
    public HotSpotProviders getProviders() {
        return (HotSpotProviders) super.getProviders();
    }

    @Override
    public void emitTailcall(Value[] args, Value address) {
        throw JVMCIError.unimplemented();
    }

    @Override
    public SaveRegistersOp emitSaveAllRegisters() {
        throw JVMCIError.unimplemented();
    }

    @Override
    public VirtualStackSlot getLockSlot(int lockDepth) {
        return getLockStack().makeLockSlot(lockDepth);
    }

    private HotSpotLockStack getLockStack() {
        assert lockStack != null;
        return lockStack;
    }

    protected void setLockStack(HotSpotLockStack lockStack) {
        assert this.lockStack == null;
        this.lockStack = lockStack;
    }

    @SuppressWarnings("unused")
    @Override
    public Value emitCompress(Value pointer, HotSpotVMConfig.CompressEncoding encoding, boolean nonNull) {
        LIRKind inputKind = pointer.getLIRKind();
        assert inputKind.getPlatformKind() == AArch64Kind.QWORD;
        Variable result = newVariable(LIRKind.reference(AArch64Kind.DWORD));
        AllocatableValue base = getCompressionBase(encoding, inputKind);
        // TODO (das) continue here.
        throw JVMCIError.unimplemented("finish implementation");
    }

    private AllocatableValue getCompressionBase(HotSpotVMConfig.CompressEncoding encoding, LIRKind inputKind) {
        if (inputKind.isReference(0)) {
            // oop
            return getProviders().getRegisters().getHeapBaseRegister().asValue();
        } else {
            // metaspace pointer
            if (encoding.base == 0) {
                return AArch64.zr.asValue(LIRKind.value(AArch64Kind.QWORD));
            } else {
                return emitLoadConstant(LIRKind.value(AArch64Kind.QWORD), JavaConstant.forLong(encoding.base));
            }
        }
    }

    @Override
    public Value emitUncompress(Value pointer, HotSpotVMConfig.CompressEncoding encoding, boolean nonNull) {
        return null;
    }

    @Override
    public void emitPrefetchAllocate(Value address) {
        // TODO (das) Optimization for later.
    }

    @Override
    public void emitDeoptimizeCaller(DeoptimizationAction action, DeoptimizationReason reason) {
        Value actionAndReason = emitJavaConstant(getMetaAccess().encodeDeoptActionAndReason(action, reason, 0));
        Value nullValue = emitConstant(LIRKind.reference(AArch64Kind.QWORD), JavaConstant.NULL_POINTER);
        moveDeoptValuesToThread(actionAndReason, nullValue);
        append(new AArch64HotSpotDeoptimizeCallerOp(config));
    }

    @Override
    public void emitDeoptimize(Value actionAndReason, Value failedSpeculation, LIRFrameState state) {
        moveDeoptValuesToThread(actionAndReason, failedSpeculation);
        append(new AArch64HotSpotDeoptimizeOp(state));
    }

    private void moveDeoptValuesToThread(Value actionAndReason, Value speculation) {
        moveValueToThread(actionAndReason, config.pendingDeoptimizationOffset);
        moveValueToThread(speculation, config.pendingFailedSpeculationOffset);
    }

    private void moveValueToThread(Value value, int offset) {
        LIRKind wordKind = LIRKind.value(target().arch.getWordKind());
        RegisterValue thread = getProviders().getRegisters().getThreadRegister().asValue(wordKind);
        AArch64AddressValue pendingDeoptAddress = new AArch64AddressValue(value.getLIRKind(), thread, Value.ILLEGAL, offset, false, AArch64Address.AddressingMode.IMMEDIATE_UNSCALED);
        append(new AArch64Move.StoreOp((AArch64Kind) value.getPlatformKind(), pendingDeoptAddress, loadReg(value), null));
    }

    @Override
    public void emitUnwind(Value exception) {
        ForeignCallLinkage linkage = getForeignCalls().lookupForeignCall(HotSpotBackend.UNWIND_EXCEPTION_TO_CALLER);
        CallingConvention outgoingCc = linkage.getOutgoingCallingConvention();
        assert outgoingCc.getArgumentCount() == 2;
        RegisterValue exceptionParameter = (RegisterValue) outgoingCc.getArgument(0);
        emitMove(exceptionParameter, exception);
        append(new AArch64HotSpotUnwindOp(config, exceptionParameter));
    }

    @Override
    public void emitReturn(JavaKind kind, Value input) {
        AllocatableValue operand = Value.ILLEGAL;
        if (input != null) {
            operand = resultOperandFor(kind, input.getLIRKind());
            emitMove(operand, input);
        }
        append(new AArch64HotSpotReturnOp(operand, getStub() != null, config));
    }

    /**
     * Gets the {@link Stub} this generator is generating code for or {@code null} if a stub is not
     * being generated.
     */
    public Stub getStub() {
        return ((AArch64HotSpotLIRGenerationResult) getResult()).getStub();
    }

}
