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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.aarch64.AArch64Address;
import jdk.graal.compiler.asm.aarch64.AArch64Assembler;
import jdk.graal.compiler.asm.aarch64.AArch64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.aarch64.AArch64AddressValue;
import jdk.graal.compiler.lir.aarch64.AArch64Call;
import jdk.graal.compiler.lir.aarch64.AArch64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahLoadRefBarrierNode;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

/**
 * AArch64 backend for the Shenandoah load-reference barrier.
 */
public class AArch64HotSpotShenandoahLoadRefBarrierOp extends AArch64LIRInstruction {
    public static final LIRInstructionClass<AArch64HotSpotShenandoahLoadRefBarrierOp> TYPE = LIRInstructionClass.create(AArch64HotSpotShenandoahLoadRefBarrierOp.class);

    private final HotSpotProviders providers;
    private final GraalHotSpotVMConfig config;

    /**
     * The slow-path entry for the load-reference-barrier.
     */
    private final ForeignCallLinkage callTarget;

    /**
     * Strength (strong, weak, phantom) of incoming object reference. This affects whether or not
     * the barrier needs to be active in the weak-roots phase, and whether or not we need to check
     * for the object to be in the collection set.
     */
    private final ShenandoahLoadRefBarrierNode.ReferenceStrength strength;

    /**
     * If we know that the incoming object is not null, then we don't need to emit a null-check.
     */
    private final boolean notNull;

    /**
     * The output of the LRB. Passes the canonicalized reference to the consumer.
     */
    @Def({REG}) private AllocatableValue result;

    /**
     * The input of the LRB. This is typically a reference that has just been loaded.
     */
    @Use({REG}) private AllocatableValue object;

    @Alive({COMPOSITE}) private AArch64AddressValue loadAddress;

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
        Register thread = providers.getRegisters().getThreadRegister();
        emitCode(config, crb, masm, this, thread, asRegister(result), asRegister(object), loadAddress.toAddress(), callTarget, strength, notNull);
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/aarch64/gc/shenandoah/shenandoahBarrierSetAssembler_aarch64.cpp#L232-L309",
              sha1 = "4ed44f985dfdca39bf93c6d306a378be4bf88fe7")
    // @formatter:on
    @SuppressWarnings("try")
    public static void emitCode(GraalHotSpotVMConfig config, CompilationResultBuilder crb, AArch64MacroAssembler masm, LIRInstruction op, Register thread, Register result, Register object,
                    AArch64Address loadAddress, ForeignCallLinkage callTarget, ShenandoahLoadRefBarrierNode.ReferenceStrength strength, boolean notNull) {
        /*
         * The slow path uses both scratch registers so allocate them both here to catch any cases
         * where the caller might thing the scratch registers are free.
         */
        try (AArch64MacroAssembler.ScratchRegister sc1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister unused = masm.getScratchRegister()) {
            Register rscratch1 = sc1.getRegister();

            Label done = new Label();
            Label csetCheck = new Label();
            Label slowPath = new Label();

            // Move object to result, in case the heap is stable and no barrier needs to be called.
            masm.mov(64, result, object);

            if (!notNull) {
                // Check for object being null.
                masm.cbz(64, result, done);
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
                masm.tst(64, rscratch1, config.shenandoahGCStateHasForwarded);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, slowPath);
                masm.tst(64, rscratch1, config.shenandoahGCStateWeakRoots);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, slowPath);
            } else {
                masm.tst(64, rscratch1, config.shenandoahGCStateHasForwarded);
                masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, csetCheck);
            }
            masm.bind(done);

            // Check for object in collection set in an out-of-line mid-path.
            if (strength == ShenandoahLoadRefBarrierNode.ReferenceStrength.STRONG) {
                crb.getLIR().addSlowPath(op, () -> {
                    try (AArch64MacroAssembler.ScratchRegister tmp1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister tmp2 = masm.getScratchRegister()) {
                        Register rtmp1 = tmp1.getRegister();
                        Register rtmp2 = tmp2.getRegister();
                        LIRValueUtil.differentRegisters(object, rtmp1, rtmp2);
                        masm.bind(csetCheck);
                        masm.mov(rtmp1, HotSpotReplacementsUtil.shenandoahGCCSetFastTestAddr(config));
                        masm.lsr(64, rtmp2, object, HotSpotReplacementsUtil.shenandoahGCRegionSizeBytesShift(config));
                        masm.ldr(8, rtmp2, AArch64Address.createRegisterOffsetAddress(8, rtmp1, rtmp2, false));
                        masm.cbnz(32, rtmp2, slowPath);
                        masm.jmp(done);
                    }
                });
            }
            // Call runtime slow-path LRB in out-of-line slow-path.
            crb.getLIR().addSlowPath(op, () -> {
                try (AArch64MacroAssembler.ScratchRegister tmp1 = masm.getScratchRegister(); AArch64MacroAssembler.ScratchRegister tmp2 = masm.getScratchRegister()) {
                    Register rtmp1 = tmp1.getRegister();
                    Register rtmp2 = tmp2.getRegister();
                    masm.bind(slowPath);
                    CallingConvention cc = callTarget.getOutgoingCallingConvention();
                    GraalError.guarantee(cc.getArgumentCount() == 2, "Expecting callTarget to have only 2 parameters. It has %d", cc.getArgumentCount());

                    // Store first argument
                    AArch64Address cArg0 = (AArch64Address) crb.asAddress(cc.getArgument(0));
                    masm.str(64, object, cArg0);

                    // Store second argument
                    Register addressReg;
                    if (loadAddress.isBaseRegisterOnly()) {
                        // Can directly use the base register as the address
                        addressReg = loadAddress.getBase();
                    } else {
                        addressReg = rtmp1;
                        masm.loadAddress(addressReg, loadAddress);
                    }
                    AArch64Address cArg1 = (AArch64Address) crb.asAddress(cc.getArgument(1));
                    masm.str(64, addressReg, cArg1);

                    // Make the call
                    AArch64Call.directCall(crb, masm, callTarget, AArch64Call.isNearCall(callTarget) ? null : rtmp2, null);

                    // Retrieve result and move to the result register.
                    AArch64Address cRet = (AArch64Address) crb.asAddress(cc.getReturn());
                    masm.ldr(64, result, cRet);
                    masm.jmp(done);
                }
            });
        }
    }
}
