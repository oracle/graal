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
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.aarch64.AArch64HotSpotMove;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.Variable;
import jdk.graal.compiler.lir.aarch64.AArch64AtomicMove;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

import static jdk.vm.ci.aarch64.AArch64.zr;
import static jdk.vm.ci.code.ValueUtil.asRegister;

/**
 * Special Shenandoah CAS implementation that handles false negatives due to concurrent evacuation.
 * The service is more complex than a traditional CAS operation because the CAS operation is
 * intended to succeed if the reference at addr exactly matches expected or if the reference at addr
 * holds a pointer to a from-space object that has been relocated to the location named by expected.
 * There are two races that must be addressed: a) A parallel thread may mutate the contents of addr
 * so that it points to a different object. In this case, the CAS operation should fail. b) A
 * parallel thread may heal the contents of addr, replacing a from-space pointer held in addr with
 * the to-space pointer representing the new location of the object. Upon entry to cmpxchg_oop, it
 * is assured that new_val equals null or it refers to an object that is not being evacuated out of
 * from-space, or it refers to the to-space version of an object that is being evacuated out of
 * from-space.
 */
public class AArch64HotSpotShenandoahCompareAndSwapOp extends AArch64AtomicMove.CompareAndSwapOp {
    public static final LIRInstructionClass<AArch64HotSpotShenandoahCompareAndSwapOp> TYPE = LIRInstructionClass.create(AArch64HotSpotShenandoahCompareAndSwapOp.class);

    private final HotSpotProviders providers;
    private final GraalHotSpotVMConfig config;

    @Temp private AllocatableValue tmp1Value;

    public AArch64HotSpotShenandoahCompareAndSwapOp(GraalHotSpotVMConfig config, HotSpotProviders providers, AArch64Kind accessKind, MemoryOrderMode memoryOrder, boolean isLogicVariant,
                    Variable result, AllocatableValue expectedValue, AllocatableValue newValue, AllocatableValue address, AllocatableValue tmp1) {
        super(TYPE, accessKind, memoryOrder, isLogicVariant, result, expectedValue, newValue, address);
        this.providers = providers;
        this.config = config;

        this.tmp1Value = tmp1;
    }

    @Override
    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/aarch64/gc/shenandoah/shenandoahBarrierSetAssembler_aarch64.cpp#L471-L606",
              sha1 = "553a2fb0d37f39016eda85331e8cd2421153cbfe")
    // @formatter:on
    public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
        Register address = asRegister(addressValue);
        Register result = asRegister(resultValue);
        Register expected = asRegister(expectedValue);
        Register newVal = asRegister(newValue);

        Register tmp1 = asRegister(tmp1Value);
        Label step2 = new Label();
        Label done = new Label();
        GraalError.guarantee(accessKind == AArch64Kind.QWORD || accessKind == AArch64Kind.DWORD, "must be 64 or 32 bit access");
        int size = (accessKind == AArch64Kind.QWORD) ? 64 : 32;

        // Step 1. Fast-path.
        //
        // Try to CAS with given arguments. If successful, then we are done.

        emitCompareAndSwap(masm, accessKind, address, result, expected, newVal, memoryOrder, true);
        // EQ flag set iff success. result holds value fetched.

        // If expected equals null but result does not equal null, the
        // step2 branches to done to report failure of CAS. If both
        // expected and tmp2 equal null, the following branches to done to
        // report success of CAS. There's no need for a special test of
        // expected equal to null.

        masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, step2);

        masm.bind(done);

        crb.getLIR().addSlowPath(this, () -> {
            // Step 2. CAS has failed because the value held at addr does not
            // match expected. This may be a false negative because the value fetched
            // from addr (now held in result) may be a from-space pointer to the
            // original copy of same object referenced by to-space pointer expected.
            //
            // To resolve this, it suffices to find the forward pointer associated
            // with fetched value. If this matches expected, retry CAS with new
            // parameters. If this mismatches, then we have a legitimate
            // failure, and we're done.
            masm.bind(step2);

            // Check for null. If we get null, then we have a legitimate failure.
            masm.tst(size, result, result);
            Label resultNullFailure = setConditionFlags ? new Label() : done;
            masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, resultNullFailure);

            // overwrite tmp1 with from-space pointer fetched from memory
            masm.mov(size, tmp1, result);

            // Decode tmp1 in order to resolve its forward pointer
            uncompress(masm, tmp1);

            // Load mark-word (i.e. potential forwarding pointer).
            masm.ldr(64, tmp1, AArch64Address.createImmediateAddress(64, AArch64Address.AddressingMode.IMMEDIATE_SIGNED_UNSCALED, tmp1, config.markOffset));
            // Invert the mark-word, so that we can test the two lowest bits for 11, while
            // preserving the upper bits.
            masm.eon(64, tmp1, tmp1, zr);
            // Check lowest bits for 00, which would have been originally 11.
            // Original 11 indicates a forwarded object.
            masm.tst(64, tmp1, config.markWordLockMaskInPlace);
            // If not forwarded, then we're done. It must be a legitimate failure.
            masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, done);
            // Set lowest two bits, which will result in actual clearing the bits
            // after the following inversion.
            masm.orr(64, tmp1, tmp1, config.markWordLockMaskInPlace);
            // ... and invert all bits back to get the forwarding pointer into tmp1.
            masm.eon(64, tmp1, tmp1, zr);

            // Encode tmp1 to compare against expected.
            compress(masm, tmp1);

            // Does forwarded value of fetched from-space pointer match original
            // value of expected?
            masm.cmp(size, tmp1, expected);

            // If not, then the failure was legitimate and we're done.
            // Branching to done with NE condition denotes failure.
            masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, done);

            // Fall through to step 3. No need for step3 label.

            // Step 3. We've confirmed that the value originally held in memory
            // (now held in result) pointed to from-space version of original
            // expected value. Try the CAS again with the from-space expected
            // value. If it now succeeds, we're good.
            //
            // Note: result holds encoded from-space pointer that matches to-space
            // object residing at expected. result is the new "expected".
            masm.mov(size, tmp1, result);
            emitCompareAndSwap(masm, accessKind, address, result, tmp1, newVal, memoryOrder, true);
            // EQ flag set iff success. result holds value fetched.

            // If fetched value did not equal the new expected, this could
            // still be a false negative because some other thread may have
            // newly overwritten the memory value with its to-space equivalent.
            masm.branchConditionally(AArch64Assembler.ConditionFlag.EQ, done);

            // In the rare case that four steps are required to perform the
            // requested operation, the fourth step is the same as the first.

            // Step 4. CAS has failed because the value most recently fetched
            // from addr is no longer the from-space pointer held in result. If a
            // different thread replaced the in-memory value with its equivalent
            // to-space pointer, then CAS may still be able to succeed. The
            // value held in the expected register has not changed.
            //
            // It is extremely rare we reach this point.

            emitCompareAndSwap(masm, accessKind, address, result, expected, newVal, memoryOrder, setConditionFlags);
            // EQ flag set iff success. result holds value fetched.

            masm.jmp(done);

            if (setConditionFlags) {
                masm.bind(resultNullFailure);
                // Clear zero flag to indicate failure. We come here knowing that result is null,
                // so comparing it to 1 results in the zero (EQ) flag getting cleared.
                masm.subs(32, zr, result,1);
                masm.jmp(done);
            }
        });
    }

    void uncompress(AArch64MacroAssembler masm, Register obj) {
        if (accessKind == AArch64Kind.DWORD) {
            Register heapBase = providers.getRegisters().getHeapBaseRegister();
            AArch64HotSpotMove.UncompressPointer.emitUncompressCode(masm, obj, obj, heapBase, config.getOopEncoding(), false);
        }
    }

    void compress(AArch64MacroAssembler masm, Register obj) {
        if (accessKind == AArch64Kind.DWORD) {
            Register heapBase = providers.getRegisters().getHeapBaseRegister();
            AArch64HotSpotMove.CompressPointer.emitCompressCode(masm, obj, obj, heapBase, config.getOopEncoding(), false);
        }
    }
}
