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

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahLoadRefBarrierNode;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

/**
 * AArch64 backend for the Shenandoah load-reference barrier.
 */
public class AArch64HotSpotShenandoahLoadRefBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotShenandoahLoadRefBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotShenandoahLoadRefBarrierOp.class);

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

    enum GCState {
        HAS_FORWARDED(1 << GCStateBitPos.HAS_FORWARDED_BITPOS.value),
        MARKING(1 << GCStateBitPos.MARKING_BITPOS.value),
        EVACUATION(1 << GCStateBitPos.EVACUATION_BITPOS.value),
        UPDATE_REFS(1 << GCStateBitPos.UPDATE_REFS_BITPOS.value),
        WEAK_ROOTS(1 << GCStateBitPos.WEAK_ROOTS_BITPOS.value),
        YOUNG_MARKING(1 << GCStateBitPos.YOUNG_MARKING_BITPOS.value),
        OLD_MARKING(1 << GCStateBitPos.OLD_MARKING_BITPOS.value);

        private final int value;

        GCState(int val) {
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
    @Alive({COMPOSITE}) protected AArch64AddressValue loadAddress;

    protected final ForeignCallLinkage callTarget;

    ShenandoahLoadRefBarrierNode.ReferenceStrength strength;
    boolean notNull;

    public AArch64HotSpotShenandoahLoadRefBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers,
                    AllocatableValue result, AllocatableValue object, AArch64AddressValue loadAddress,
                    ForeignCallLinkage callTarget,
                    ShenandoahLoadRefBarrierNode.ReferenceStrength strength,
                    boolean notNull) {
        super(TYPE);
        this.providers = providers;
        this.config = config;
        this.result = result;
        this.object = object;
        this.loadAddress = loadAddress;
        this.callTarget = callTarget;
        this.strength = strength;
        this.notNull = notNull;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister()) {
            // System.out.println("Emitting Shenandoah load reference barrier");
            Register rscratch1 = sc1.getRegister();
            Register objectRegister = asRegister(object);
            AArch64Address loadAddr = loadAddress.toAddress();
            Register resultRegister = asRegister(result);

            Register thread = providers.getRegisters().getThreadRegister();

            Label done = new Label();
            Label csetCheck = new Label();
            Label slowPath = new Label();

            // Move object to result, in case the heap is stable and no barrier needs to be called.
            masm.mov(64, resultRegister, objectRegister);

            if (!notNull) {
                // Check for object being null.
                masm.cbz(64, resultRegister, done);
            }

            // Check for heap stability
            int gcStateOffset = HotSpotReplacementsUtil.shenandoahGCStateOffset(config);
            AArch64Address gcState = masm.makeAddress(8, thread, gcStateOffset);
            masm.ldr(8, rscratch1, gcState);
            if (strength != ShenandoahLoadRefBarrierNode.ReferenceStrength.STRONG) {
                // This is needed because in a short-cut cycle we may get a trailing
                // weak-roots phase but no evacuation/update-refs phase, and during that,
                // we need to take the LRB to report null for unreachable weak-refs.
                // This is true even for non-cset objects.
                // Two tests because HAS_FORWARDED | WEAK_ROOTS currently is not representable
                // as a single immediate.
                masm.tst(64, rscratch1, GCState.HAS_FORWARDED.value);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, slowPath);
                masm.tst(64, rscratch1, GCState.WEAK_ROOTS.value);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, slowPath);
            } else {
                masm.tst(64, rscratch1, GCState.HAS_FORWARDED.value);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, csetCheck);
            }
            masm.bind(done);

            // Check for object in collection set in an out-of-line mid-path.
            if (strength == ShenandoahLoadRefBarrierNode.ReferenceStrength.STRONG) {
                crb.getLIR().addSlowPath(this, () -> {
                    try (AArch64MacroAssembler.ScratchRegister tmp1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister tmp2 = masm.getScratchRegister()) {
                        Register rtmp1 = tmp1.getRegister();
                        Register rtmp2 = tmp2.getRegister();
                        masm.bind(csetCheck);
                        masm.mov(rtmp1, HotSpotReplacementsUtil.shenandoahGCCSetFastTestAddr(config));
                        masm.lsr(64, rtmp2, objectRegister, HotSpotReplacementsUtil.shenandoahGCRegionSizeBytesShift(config));
                        masm.ldr(8, rtmp2, AArch64Address.createRegisterOffsetAddress(8, rtmp1, rtmp2, false));
                        masm.cbnz(8, rtmp2, slowPath);
                        masm.jmp(done);
                    }
                });
            }
            // Call runtime slow-path LRB in out-of-line slow-path.
            crb.getLIR().addSlowPath(this, () -> {
                try (AArch64MacroAssembler.ScratchRegister tmp1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister tmp2 = masm.getScratchRegister()) {
                    Register rtmp1 = tmp1.getRegister();
                    Register rtmp2 = tmp2.getRegister();
                    masm.bind(slowPath);
                    CallingConvention cc = callTarget.getOutgoingCallingConvention();
                    GraalError.guarantee(cc.getArgumentCount() == 2, "Expecting callTarget to have only 2 parameters. It has " + cc.getArgumentCount());

                    // Store first argument
                    AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                    masm.str(64, objectRegister, cArg0);

                    // Store second argument
                    Register addressReg;
                    if (loadAddr.isBaseRegisterOnly()) {
                        // Can directly use the base register as the address
                        addressReg = loadAddr.getBase();
                    } else {
                        addressReg = rtmp1;
                        masm.loadAddress(addressReg, loadAddr);
                    }
                    AArch64Address cArg1 = (AArch64Address) crb.asAddress(cc.getArgument(1));
                    masm.str(64, addressReg, cArg1);

                    // Make the call
                    AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : rtmp2, null);

                    // Retrieve result and move to the result register.
                    AArch64Address cRet = (AArch64Address) crb.asAddress(cc.getReturn());
                    masm.ldr(64, resultRegister, cRet);
                    masm.jmp(done);
                }
            });
        }
    }
}
