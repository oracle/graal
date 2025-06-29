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
package jdk.graal.compiler.hotspot.amd64.shenandoah;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64Assembler;
import jdk.graal.compiler.asm.amd64.AMD64BaseAssembler;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.spi.ForeignCallLinkage;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Call;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.graal.compiler.nodes.gc.shenandoah.ShenandoahLoadRefBarrierNode;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import static jdk.graal.compiler.asm.Assembler.guaranteeDifferentRegisters;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.vm.ci.code.ValueUtil.asRegister;

/**
 * X86 backend for the Shenandoah load-reference barrier.
 */
public class AMD64HotSpotShenandoahLoadRefBarrierOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotShenandoahLoadRefBarrierOp> TYPE = LIRInstructionClass.create(AMD64HotSpotShenandoahLoadRefBarrierOp.class);

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

    @Temp({REG}) private AllocatableValue tmp;

    @Temp({REG}) private AllocatableValue tmp2;

    /**
     * The output of the LRB. Passes the canonicalized reference to the consumer.
     */
    @Def({REG}) private AllocatableValue result;

    /**
     * The input of the LRB. This is typically a reference that has just been loaded.
     */
    @Alive({REG}) private AllocatableValue object;

    /**
     * The address from where the reference has been loaded, if any.
     */
    @Alive({COMPOSITE}) private AMD64AddressValue loadAddress;

    public AMD64HotSpotShenandoahLoadRefBarrierOp(GraalHotSpotVMConfig config, HotSpotProviders providers,
                    AllocatableValue result, AllocatableValue object, AMD64AddressValue loadAddress,
                    ForeignCallLinkage callTarget, ShenandoahLoadRefBarrierNode.ReferenceStrength strength,
                    AllocatableValue tmp, AllocatableValue tmp2, boolean notNull) {
        super(TYPE);
        this.providers = providers;
        this.config = config;
        this.result = result;
        this.object = object;
        this.loadAddress = loadAddress;
        this.callTarget = callTarget;
        this.strength = strength;
        this.notNull = notNull;
        this.tmp = tmp;
        this.tmp2 = tmp2;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        Register thread = providers.getRegisters().getThreadRegister();
        Register rtmp1 = asRegister(tmp);
        Register rtmp2 = asRegister(tmp2);
        Register objectRegister = asRegister(object);
        Register resultRegister = asRegister(result);
        AMD64Address loadAddr = loadAddress.toAddress(masm);
        emitCode(config, crb, masm, this, thread, resultRegister, objectRegister, rtmp1, rtmp2, loadAddr, callTarget, strength, notNull);
    }

    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/x86/gc/shenandoah/shenandoahBarrierSetAssembler_x86.cpp#L296-L430",
              sha1 = "a039ddb87ee03446a7d015f2d955eb3014c9413e")
    // @formatter:on
    public static void emitCode(GraalHotSpotVMConfig config, CompilationResultBuilder crb, AMD64MacroAssembler masm, LIRInstruction op, Register thread, Register resultRegister,
                    Register objectRegister, Register rtmp1, Register rtmp2,
                    AMD64Address loadAddr, ForeignCallLinkage callTarget, ShenandoahLoadRefBarrierNode.ReferenceStrength strength, boolean notNull) {
        guaranteeDifferentRegisters(thread, rtmp1, rtmp2, objectRegister, resultRegister);

        Label done = new Label();
        Label csetCheck = new Label();
        Label slowPath = new Label();

        // Move object to result, in case the heap is stable and no barrier needs to be called.
        masm.movq(resultRegister, objectRegister);

        if (!notNull) {
            // Check for object being null.
            masm.testAndJcc(AMD64BaseAssembler.OperandSize.QWORD, resultRegister, resultRegister, AMD64Assembler.ConditionFlag.Zero, done, true);
        }

        // Check for heap stability
        masm.movb(rtmp1, new AMD64Address(thread, HotSpotReplacementsUtil.shenandoahGCStateOffset(config)));
        if (strength != ShenandoahLoadRefBarrierNode.ReferenceStrength.STRONG) {
            // This is needed because in a short-cut cycle we may get a trailing
            // weak-roots phase but no evacuation/update-refs phase, and during that,
            // we need to take the LRB to report null for unreachable weak-refs.
            // This is true even for non-cset objects.
            masm.testlAndJcc(rtmp1, config.shenandoahGCStateHasForwarded | config.shenandoahGCStateWeakRoots, AMD64Assembler.ConditionFlag.NotZero, slowPath, false);
        } else {
            masm.testlAndJcc(rtmp1, config.shenandoahGCStateHasForwarded, AMD64Assembler.ConditionFlag.NotZero, csetCheck, false);
        }
        masm.bind(done);

        // Check for object in collection set in an out-of-line mid-path.
        if (strength == ShenandoahLoadRefBarrierNode.ReferenceStrength.STRONG) {
            crb.getLIR().addSlowPath(op, () -> {
                masm.bind(csetCheck);

                masm.movq(rtmp1, HotSpotReplacementsUtil.shenandoahGCCSetFastTestAddr(config));
                masm.movq(rtmp2, objectRegister);
                masm.shrq(rtmp2, HotSpotReplacementsUtil.shenandoahGCRegionSizeBytesShift(config));

                masm.addq(rtmp2, rtmp1);
                masm.cmpb(new AMD64Address(rtmp2), 0);
                masm.jcc(AMD64Assembler.ConditionFlag.NotZero, slowPath);

                masm.jmp(done);
            });
        }

        // Call runtime slow-path LRB in out-of-line slow-path.
        crb.getLIR().addSlowPath(op, () -> {
            masm.bind(slowPath);
            CallingConvention cc = callTarget.getOutgoingCallingConvention();
            GraalError.guarantee(cc.getArgumentCount() == 2, "Expecting callTarget to have only 2 parameters. It has %d", cc.getArgumentCount());

            AMD64Address cArg0 = (AMD64Address) crb.asAddress(cc.getArgument(0));
            AMD64Address cArg1 = (AMD64Address) crb.asAddress(cc.getArgument(1));

            // Store first argument
            masm.movq(cArg0, objectRegister);

            // Store second argument
            masm.leaq(rtmp1, loadAddr);
            masm.movq(cArg1, rtmp1);

            // Make the call
            AMD64Call.directCall(crb, masm, callTarget, null, false, null);

            // Retrieve result and move to the result register.
            AMD64Address cRet = (AMD64Address) crb.asAddress(cc.getReturn());
            masm.movq(resultRegister, cRet);
            masm.jmp(done);
        });
    }
}
