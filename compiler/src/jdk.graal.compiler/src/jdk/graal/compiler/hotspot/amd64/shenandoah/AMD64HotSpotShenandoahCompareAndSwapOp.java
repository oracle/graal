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
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.Opcode;
import jdk.graal.compiler.lir.SyncPort;
import jdk.graal.compiler.lir.amd64.AMD64AddressValue;
import jdk.graal.compiler.lir.amd64.AMD64Move;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.amd64.AMD64Kind;

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.COMPOSITE;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;

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
@Opcode("CAS_Shenandoah")
public class AMD64HotSpotShenandoahCompareAndSwapOp extends AMD64LIRInstruction {
    public static final LIRInstructionClass<AMD64HotSpotShenandoahCompareAndSwapOp> TYPE = LIRInstructionClass.create(AMD64HotSpotShenandoahCompareAndSwapOp.class);

    private final HotSpotProviders providers;
    private final GraalHotSpotVMConfig config;

    private final AMD64Kind accessKind;
    private final boolean isLogic;

    @Def private AllocatableValue result;
    @Alive({COMPOSITE}) private AMD64AddressValue address;
    @Alive private AllocatableValue cmpValue;
    @Alive private AllocatableValue newValue;

    @Temp private AllocatableValue tmp1Value;
    @Temp private AllocatableValue tmp2Value;

    public AMD64HotSpotShenandoahCompareAndSwapOp(GraalHotSpotVMConfig config, HotSpotProviders providers, AMD64Kind accessKind, AllocatableValue result, AMD64AddressValue address,
                    AllocatableValue cmpValue, AllocatableValue newValue, AllocatableValue tmp1, AllocatableValue tmp2, boolean isLogic) {
        super(TYPE);
        this.providers = providers;
        this.config = config;
        this.accessKind = accessKind;
        this.isLogic = isLogic;
        this.result = result;
        this.address = address;
        this.cmpValue = cmpValue;
        this.newValue = newValue;
        this.tmp1Value = tmp1;
        this.tmp2Value = tmp2;
    }

    @Override
    // @formatter:off
    @SyncPort(from = "https://github.com/openjdk/jdk/blob/a2743bab4fd203b0791cf47e617c1a95b05ab3cc/src/hotspot/cpu/x86/gc/shenandoah/shenandoahBarrierSetAssembler_x86.cpp#L596-L737",
              sha1 = "c236c9ca8ccf7e45757d6ac04d16a230df9475a0")
    // @formatter:on
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        AMD64Address addr = address.toAddress(masm);
        Register resultReg = asRegister(result);
        Register expectedReg = asRegister(cmpValue);
        Register newVal = asRegister(newValue);

        Register tmp1 = asRegister(tmp1Value);
        Register tmp2 = asRegister(tmp2Value);

        GraalError.guarantee(resultReg.equals(expectedReg), "same registers");
        GraalError.guarantee(!tmp1.equals(tmp2), "different registers");
        GraalError.guarantee(!tmp1.equals(newVal), "different registers");
        GraalError.guarantee(!tmp1.equals(expectedReg), "different registers");
        GraalError.guarantee(!tmp2.equals(newVal), "different registers");
        GraalError.guarantee(!tmp2.equals(expectedReg), "different registers");
        GraalError.guarantee(!newVal.equals(expectedReg), "different registers");

        Label done = new Label();
        Label step2 = new Label();

        AMD64BaseAssembler.OperandSize opSize = AMD64BaseAssembler.OperandSize.get(accessKind);

        // Remember expectedReg for retry logic below
        mov(masm, opSize, tmp1, expectedReg);

        // Step 1. Fast-path.
        //
        // Try to CAS with given arguments. If successful, then we are done.

        // There are two ways to reach this label. Initial entry into the
        // cmpxchg_oop code expansion starts at step1 (which is equivalent
        // to label step4). Additionally, in the rare case that four steps
        // are required to perform the requested operation, the fourth step
        // is the same as the first. On a second pass through step 1,
        // control may flow through step 2 on its way to failure. It will
        // not flow from step 2 to step 3 since we are assured that the
        // memory at addr no longer holds a from-space pointer.
        emitCompareAndSwap(crb, masm, accessKind, addr, expectedReg, newVal, resultReg);
        masm.jcc(AMD64Assembler.ConditionFlag.NotEqual, step2);
        masm.bind(done);

        crb.getLIR().addSlowPath(this, () -> {
            // Step 2. CAS had failed. This may be a false negative.
            //
            // The trouble comes when we compare the to-space pointer with the from-space
            // pointer to the same object. To resolve this, it will suffice to resolve
            // the value from memory -- this will give both to-space pointers.
            // If they mismatch, then it was a legitimate failure.
            masm.bind(step2);

            // First we check the value that we just fetched for null.
            // If the value is null, then we have a legitimate failure.
            // We need the correct ZF at the done label. That is, upon seeing null,
            // we need to have ZF=0 to indicate CAS failure. Unfortunately, the
            // null-test comes out with ZF=1. Therefore we branch to a small block
            // at the end, which clears ZF, and then jumps to done.
            Label resultNullFailure = isLogic ? new Label() : done;
            masm.testAndJcc(opSize, resultReg, resultReg, AMD64Assembler.ConditionFlag.Zero, resultNullFailure, isLogic);

            // We need to preserve resultReg for the failure paths.
            mov(masm, opSize, tmp2, resultReg);
            // Uncompress the offending value that we just fetched,
            // so that we can read its mark-word.
            uncompress(masm, opSize, tmp2);

            // Resolve forwarding ptr into tmp2.
            masm.movq(tmp2, new AMD64Address(tmp2, config.markOffset));
            // Invert the whole mark-word, to preserve the upper bits.
            // If the object has been forwarded, i.e. the lowest
            // two bits have been 11, then these become 00, which is
            // easily testable.
            masm.notq(tmp2);
            // Test lowest two bits for 00.
            GraalError.guarantee(config.markWordLockMaskInPlace == 3, "mask must be 3 (11)");
            // If not forwarded, then we have a legitimate failure of the CAS.
            masm.testAndJcc(AMD64BaseAssembler.OperandSize.QWORD, tmp2, (int) config.markWordLockMaskInPlace, AMD64Assembler.ConditionFlag.NotZero, done, false);
            // Now set the two lowest bits. Upon re-inversion, these become 00.
            masm.orq(tmp2, (int) config.markWordLockMaskInPlace);
            // Invert again to get the resolved forwardee in tmp2.
            masm.notq(tmp2);
            // We need to compress it for comparing with original
            // expectedReg value.
            compress(masm, opSize, tmp2);

            // Now we have the forwarded offender in tmp2.
            // Compare with original expectedReg value, and if they don't match, we have legitimate
            // failure
            masm.cmpAndJcc(opSize, tmp1, tmp2, AMD64Assembler.ConditionFlag.NotEqual, done, false);

            // Step 3. We've confirmed that the value originally held in memory
            // (now held in resultReg/expectedReg) pointed to from-space version of original
            // expectedReg value. Try the CAS again with the from-space expectedReg
            // value. If it now succeeds, we're good.
            //
            // Note: resultReg holds encoded from-space pointer that matches to-space
            // object residing at expectedReg. resultReg is the new "expectedReg".
            emitCompareAndSwap(crb, masm, accessKind, addr, expectedReg, newVal, resultReg);

            // If fetched value did not equal the new expectedReg, this could
            // still be a false negative because some other thread may have
            // newly overwritten the memory value with its to-space equivalent.
            masm.jcc(AMD64Assembler.ConditionFlag.Equal, done);

            // Step 4. CAS has failed because the value most recently fetched
            // from addr is no longer the from-space pointer held in tmp2. If a
            // different thread replaced the in-memory value with its equivalent
            // to-space pointer, then CAS may still be able to succeed. The
            // value held in the expectedReg register must be updated to expect
            // the original expectedReg value (cmpxchg uses the same register for
            // expectedReg and resultReg, and it might hold a wrong value now).
            mov(masm, opSize, expectedReg, tmp1);
            emitCompareAndSwap(crb, masm, accessKind, addr, expectedReg, newVal, resultReg);
            masm.jmp(done);

            if (isLogic) {
                masm.bind(resultNullFailure);
                // Clear 0 register to indicate failure. We arrive here with
                // resultReg == 0, but need the ZR flag to be cleared to
                // indicate failure on the done label.
                masm.cmpl(resultReg, 1);
                masm.jmp(done);
            }
        });
    }

    private static void mov(AMD64MacroAssembler masm, AMD64BaseAssembler.OperandSize opSize, Register dst, Register src) {
        if (opSize == AMD64BaseAssembler.OperandSize.QWORD) {
            masm.movq(dst, src);
        } else if (opSize == AMD64BaseAssembler.OperandSize.DWORD) {
            masm.movl(dst, src);
        } else {
            GraalError.shouldNotReachHereUnexpectedValue(opSize);
        }
    }

    private void compress(AMD64MacroAssembler masm, AMD64BaseAssembler.OperandSize opSize, Register dst) {
        if (opSize == AMD64BaseAssembler.OperandSize.DWORD) {
            CompressEncoding encoding = config.getOopEncoding();
            Register heapBase = providers.getRegisters().getHeapBaseRegister();
            AMD64Move.CompressPointerOp.emitCompressCode(masm, dst, encoding.getShift(), heapBase, true);
        } else {
            GraalError.guarantee(opSize == AMD64BaseAssembler.OperandSize.QWORD, "unexpected opSize");
        }
    }

    private void uncompress(AMD64MacroAssembler masm, AMD64BaseAssembler.OperandSize opSize, Register dst) {
        if (opSize == AMD64BaseAssembler.OperandSize.DWORD) {
            CompressEncoding encoding = config.getOopEncoding();
            Register heapBase = providers.getRegisters().getHeapBaseRegister();
            AMD64Move.UncompressPointerOp.emitUncompressCode(masm, dst, encoding.getShift(), heapBase, true);
        } else {
            GraalError.guarantee(opSize == AMD64BaseAssembler.OperandSize.QWORD, "unexpected opSize");
        }
    }

    private static void emitCompareAndSwap(CompilationResultBuilder crb, AMD64MacroAssembler masm, AMD64Kind accessKind, AMD64Address address, Register expected, Register newValue, Register result) {
        GraalError.guarantee(expected.equals(AMD64.rax), "expected must be in rax");
        GraalError.guarantee(result.equals(AMD64.rax), "result must be in rax");

        if (crb.target.isMP) {
            masm.lock();
        }
        switch (accessKind) {
            case DWORD:
                masm.cmpxchgl(address, newValue);
                break;
            case QWORD:
                masm.cmpxchgq(newValue, address);
                break;
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(accessKind); // ExcludeFromJacocoGeneratedReport
        }
    }
}
