/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.aarch64.AArch64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahLoadBarrierNode;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import static jdk.graal.compiler.asm.aarch64.AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

public class AArch64HotSpotShenandoahReadBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotShenandoahReadBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotShenandoahReadBarrierOp.class);

    enum GCStateBitPos {
        // Heap has forwarded objects: needs LRB barriers.
        HAS_FORWARDED_BITPOS(0),

        // Heap is under marking: needs SATB barriers.
        // For generational mode, it means either young or old marking, or both.
        MARKING_BITPOS(1),

        // Heap is under evacuation: needs LRB barriers. (Set together with HAS_FORWARDED)
        EVACUATION_BITPOS(2),

        // Heap is under updating: needs no additional barriers.
        UPDATE_REFS_BITPOS(3),

        // Heap is under weak-reference/roots processing: needs weak-LRB barriers.
        WEAK_ROOTS_BITPOS(4),

        // Young regions are under marking, need SATB barriers.
        YOUNG_MARKING_BITPOS(5),

        // Old regions are under marking, need SATB barriers.
        OLD_MARKING_BITPOS(6);

        private final int value;

        GCStateBitPos(int val) {
            this.value = val;
        }

        public int getValue() {
            return this.value;
        }
    }

    private final HotSpotProviders providers;
    private final GraalHotSpotVMConfig config;

    @Def({REG}) protected AllocatableValue result;
    @Use({REG}) protected AllocatableValue object;
    @Use({COMPOSITE}) protected AArch64AddressValue loadAddress;

    protected final ForeignCallLinkage callTarget;

    ShenandoahLoadBarrierNode.ReferenceStrength strength;

    public AArch64HotSpotShenandoahReadBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers,
                                                 AllocatableValue result, AllocatableValue object, AArch64AddressValue loadAddress,
                                                 ForeignCallLinkage callTarget,
                                                 ShenandoahLoadBarrierNode.ReferenceStrength strength) {
        super(TYPE);
        this.providers = providers;
        this.config = config;
        this.result = result;
        this.object = object;
        this.loadAddress = loadAddress;
        this.callTarget = callTarget;
        this.strength = strength;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister sc2 = masm.getScratchRegister()) {
            System.out.println("Emitting Shenandoah load reference barrier");
            Register rscratch1 = sc1.getRegister();
            Register rscratch2 = sc2.getRegister();
            Register objectRegister = asRegister(object);
            Register loadRegister = asRegister(loadAddress);
            Register resultRegister = asRegister(result);

            Register thread = providers.getRegisters().getThreadRegister();

            // Move object to result, in case the heap is stable an no barrier needs to be called.
            masm.mov(64, resultRegister, objectRegister);

            // Check for heap stability
            Label heapIsStable = new Label();
            int gcStateOffset = HotSpotReplacementsUtil.shenandoahGCStateOffset(config);
            AArch64Address gcState = masm.makeAddress(8, thread, gcStateOffset);
            masm.ldr(8, rscratch2, gcState);
            masm.tbz(rscratch2, GCStateBitPos.HAS_FORWARDED_BITPOS.getValue(), heapIsStable);

            // Make the call to LRB barrier
            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            assert cc.getArgumentCount() == 2 : "Expecting callTarget to have only 2 parameters. It has " + cc.getArgumentCount();

            // Store first argument
            AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
            masm.str(64, objectRegister, cArg0);

            // Store second argument
            AArch64Address cArg1 = (AArch64Address) crb.asAddress(cc.getArgument(1));
            masm.str(64, loadRegister, cArg1);

            // Make the call
            AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : rscratch1, null);

            // Retrieve result and move to same register that our input was in
            AArch64Address cRet = (AArch64Address) crb.asAddress(cc.getReturn());
            masm.ldr(64, resultRegister, cRet);

            masm.bind(heapIsStable);
        }
    }
}
