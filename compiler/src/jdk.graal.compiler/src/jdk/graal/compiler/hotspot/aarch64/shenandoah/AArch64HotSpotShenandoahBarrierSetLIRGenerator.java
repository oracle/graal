/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.aarch64.shenandoah;

import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.core.aarch64.AArch64LIRGenerator;
import jdk.graal.compiler.core.aarch64.AArch64ReadBarrierSetLIRGenerator;
import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryExtendKind;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.gen.LIRGeneratorTool;
import jdk.graal.compiler.lir.gen.ShenandoahBarrierSetLIRGeneratorTool;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahLoadRefBarrierNode;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

/**
 * Lowers Shenandoah barriers to AArch64 LIR.
 */
public class AArch64HotSpotShenandoahBarrierSetLIRGenerator implements ShenandoahBarrierSetLIRGeneratorTool, AArch64ReadBarrierSetLIRGenerator {
    public AArch64HotSpotShenandoahBarrierSetLIRGenerator(GraalHotSpotVMConfig config, HotSpotProviders providers) {
        this.config = config;
        this.providers = providers;
    }

    private final GraalHotSpotVMConfig config;
    private final HotSpotProviders providers;

    private static ForeignCallLinkage getReadBarrierStub(LIRGeneratorTool tool, ShenandoahLoadRefBarrierNode.ReferenceStrength strength, boolean narrow) {
        return switch (strength) {
            case STRONG ->
                narrow ? tool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_LOAD_BARRIER_NARROW)
                                : tool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_LOAD_BARRIER);
            case WEAK ->
                narrow ? tool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_LOAD_BARRIER_WEAK_NARROW)
                                : tool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_LOAD_BARRIER_WEAK);
            case PHANTOM ->
                narrow ? tool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_LOAD_BARRIER_PHANTOM_NARROW)
                                : tool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_LOAD_BARRIER_PHANTOM);
        };
    }

    @Override
    public Value emitLoadReferenceBarrier(LIRGeneratorTool tool, Value obj, Value address, ShenandoahLoadRefBarrierNode.ReferenceStrength strength, boolean narrow, boolean notNull) {
        PlatformKind platformKind = obj.getPlatformKind();
        LIRKind kind = LIRKind.reference(platformKind);
        Value result = tool.newVariable(tool.toRegisterKind(kind));
        ForeignCallLinkage callTarget = getReadBarrierStub(tool, strength, narrow);
        AllocatableValue object = tool.asAllocatable(obj);
        AArch64AddressValue loadAddress = ((AArch64LIRGenerator) tool).asAddressValue(address, AArch64Address.ANY_SIZE);
        tool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        tool.append(new AArch64HotSpotShenandoahLoadRefBarrierOp(config, providers, tool.asAllocatable(result), object, loadAddress, callTarget, strength, notNull));
        return result;
    }

    @Override
    public void emitPreWriteBarrier(LIRGeneratorTool lirTool, Value address, AllocatableValue expectedObject, boolean nonNull) {
        AllocatableValue temp = lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD));
        // If the assembly must load the value then it needs a temporary to store it.
        AllocatableValue temp2 = expectedObject.equals(Value.ILLEGAL) ? lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD)) : Value.ILLEGAL;

        // Load the address into a register
        AllocatableValue addressValue = lirTool.newVariable(address.getValueKind());
        lirTool.emitMove(addressValue, address);

        ForeignCallLinkage callTarget = lirTool.getForeignCalls().lookupForeignCall(HotSpotHostForeignCallsProvider.SHENANDOAH_WRITE_BARRIER_PRE);
        lirTool.getResult().getFrameMapBuilder().callsMethod(callTarget.getOutgoingCallingConvention());
        lirTool.append(new AArch64HotSpotShenandoahSATBBarrierOp(config, providers, addressValue, expectedObject, temp, temp2, callTarget, nonNull));
    }

    @Override
    public void emitCardBarrier(LIRGeneratorTool lirTool, Value address) {
        AArch64AddressValue addr = ((AArch64LIRGenerator) lirTool).asAddressValue(address, AArch64Address.ANY_SIZE);
        AllocatableValue tmp = lirTool.newVariable(LIRKind.value(AArch64Kind.QWORD));
        lirTool.append(new AArch64HotSpotShenandoahCardBarrierOp(config, providers, addr, tmp));
    }

    @Override
    public void emitCompareAndSwapOp(LIRGeneratorTool tool, boolean isLogic, Value address, MemoryOrderMode memoryOrder, AArch64Kind memKind, Variable result,
                    AllocatableValue allocatableExpectedValue, AllocatableValue allocatableNewValue, BarrierType barrierType) {
        AllocatableValue tmp1 = tool.newVariable(LIRKind.value(AArch64Kind.QWORD));
        tool.append(new AArch64HotSpotShenandoahCompareAndSwapOp(config, providers, memKind, memoryOrder, isLogic, result, allocatableExpectedValue, allocatableNewValue, tool.asAllocatable(address),
                        tmp1));
    }

    @Override
    public Value emitAtomicReadAndWrite(LIRGeneratorTool tool, LIRKind readKind, Value address, Value newValue, BarrierType barrierType) {
        // We insert the necessary barriers in the node graph, at that level it
        // is easier to handle compressed object references. No need to do anything
        // special here.
        return tool.emitAtomicReadAndWrite(readKind, address, newValue, BarrierType.NONE);
    }

    @Override
    public Variable emitBarrieredLoad(LIRGeneratorTool tool, LIRKind kind, Value address, LIRFrameState state, MemoryOrderMode memoryOrder, BarrierType barrierType) {
        // We insert the necessary barriers in the node graph, at that level it
        // is easier to handle compressed object references. No need to do anything
        // special here.
        return tool.getArithmetic().emitLoad(kind, address, state, memoryOrder, MemoryExtendKind.DEFAULT);
    }
}
