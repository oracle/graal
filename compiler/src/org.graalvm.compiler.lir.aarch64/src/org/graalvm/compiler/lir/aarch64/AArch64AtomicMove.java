/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.aarch64;

import static jdk.vm.ci.code.ValueUtil.asRegister;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.CONST;
import static org.graalvm.compiler.lir.LIRInstruction.OperandFlag.REG;

import org.graalvm.compiler.asm.Label;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler;
import org.graalvm.compiler.asm.aarch64.AArch64Assembler.ExtendType;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler;
import org.graalvm.compiler.asm.aarch64.AArch64MacroAssembler.ScratchRegister;
import org.graalvm.compiler.core.common.memory.MemoryOrderMode;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.LIRValueUtil;
import org.graalvm.compiler.lir.Opcode;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.gen.LIRGenerator;

import jdk.vm.ci.aarch64.AArch64;
import jdk.vm.ci.aarch64.AArch64Kind;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class AArch64AtomicMove {
    /**
     * Compare and swap instruction. Does the following atomically:
     *
     * <pre>
     *  CAS(newVal, expected, address):
     *    oldVal = *address
     *    if oldVal == expected:
     *        *address = newVal
     *    return oldVal
     * </pre>
     */
    @Opcode("CAS")
    public static class CompareAndSwapOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<CompareAndSwapOp> TYPE = LIRInstructionClass.create(CompareAndSwapOp.class);

        private final AArch64Kind accessKind;
        private final MemoryOrderMode memoryOrder;
        private final boolean setConditionFlags;

        @Def({REG}) protected AllocatableValue resultValue;
        @Alive({REG}) protected Value expectedValue;
        @Alive({REG}) protected AllocatableValue newValue;
        @Alive({REG}) protected AllocatableValue addressValue;

        public CompareAndSwapOp(AArch64Kind accessKind, MemoryOrderMode memoryOrder, boolean setConditionFlags, AllocatableValue result, Value expectedValue, AllocatableValue newValue,
                        AllocatableValue addressValue) {
            super(TYPE);
            this.accessKind = accessKind;
            this.memoryOrder = memoryOrder;
            this.setConditionFlags = setConditionFlags;
            this.resultValue = result;
            this.expectedValue = expectedValue;
            this.newValue = newValue;
            this.addressValue = addressValue;
        }

        /**
         * Both cas and ld(a)xr produce a zero-extended value. Since comparisons must be at minimum
         * 32-bits, the expected value must also be zero-extended to produce an accurate comparison.
         */
        private static void emitCompare(AArch64MacroAssembler masm, int memAccessSize, Register result, Register expected) {
            switch (memAccessSize) {
                case 8:
                    masm.cmp(32, result, expected, ExtendType.UXTB, 0);
                    break;
                case 16:
                    masm.cmp(32, result, expected, ExtendType.UXTH, 0);
                    break;
                case 32:
                case 64:
                    masm.cmp(memAccessSize, result, expected);
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert accessKind.isInteger();
            final int memAccessSize = accessKind.getSizeInBytes() * Byte.SIZE;

            Register address = asRegister(addressValue);
            Register result = asRegister(resultValue);
            Register newVal = asRegister(newValue);
            Register expected = asRegister(expectedValue);

            /*
             * Determining whether acquire and/or release semantics are needed.
             */
            boolean acquire;
            boolean release;
            switch (memoryOrder) {
                case PLAIN:
                case OPAQUE:
                    acquire = false;
                    release = false;
                    break;
                case ACQUIRE:
                    acquire = true;
                    release = false;
                    break;
                case RELEASE:
                    acquire = false;
                    release = true;
                    break;
                case RELEASE_ACQUIRE:
                case VOLATILE:
                    acquire = true;
                    release = true;
                    break;
                default:
                    throw GraalError.shouldNotReachHere();
            }

            if (AArch64LIRFlags.useLSE(masm)) {
                masm.mov(Math.max(memAccessSize, 32), result, expected);
                masm.cas(memAccessSize, result, newVal, address, acquire, release);
                if (setConditionFlags) {
                    emitCompare(masm, memAccessSize, result, expected);
                }
            } else {
                /*
                 * Because the store is only conditionally emitted, a dmb is needed for performing a
                 * release.
                 *
                 * Furthermore, even if the stlxr is emitted, if both acquire and release semantics
                 * are required, then a dmb is anyways needed to ensure that the instruction
                 * sequence:
                 *
                 * A -> ldaxr -> stlxr -> B
                 *
                 * cannot be executed as:
                 *
                 * ldaxr -> B -> A -> stlxr
                 */
                if (release) {
                    masm.dmb(AArch64Assembler.BarrierKind.ANY_ANY);
                }

                try (ScratchRegister scratchRegister = masm.getScratchRegister()) {
                    Register scratch = scratchRegister.getRegister();
                    Label retry = new Label();
                    Label fail = new Label();
                    masm.bind(retry);
                    masm.loadExclusive(memAccessSize, result, address, acquire);
                    emitCompare(masm, memAccessSize, result, expected);
                    masm.branchConditionally(AArch64Assembler.ConditionFlag.NE, fail);
                    /*
                     * Even with the prior dmb, for releases it is still necessary to use stlxr
                     * instead of stxr to guarantee subsequent lda(x)r/stl(x)r cannot be hoisted
                     * above this instruction and thereby violate volatile semantics.
                     */
                    masm.storeExclusive(memAccessSize, scratch, newVal, address, release);
                    // if scratch == 0 then write successful, else retry.
                    masm.cbnz(32, scratch, retry);
                    masm.bind(fail);
                }
            }
        }
    }

    /**
     * Determines whether to use the atomic or load-store conditional implementation of atomic
     * read&add based on the available hardware features.
     *
     * These two variants are split into two separate classes, as deltaValue is allowed to be a
     * constant within the load-store conditional implementation.
     */
    public static AArch64LIRInstruction createAtomicReadAndAdd(LIRGenerator gen, AArch64Kind kind, AllocatableValue result, AllocatableValue address, Value delta) {
        if (AArch64LIRFlags.useLSE((AArch64) gen.target().arch)) {
            return new AtomicReadAndAddLSEOp(kind, result, address, gen.asAllocatable(delta));
        } else {
            return new AtomicReadAndAddOp(kind, result, address, delta);
        }
    }

    /**
     * Load (Read) and Add instruction. Does the following atomically:
     *
     * <pre>
     *  ATOMIC_READ_AND_ADD(addend, result, address):
     *    result = *address
     *    *address = result + addend
     *    return result
     * </pre>
     */
    @Opcode("ATOMIC_READ_AND_ADD")
    public static final class AtomicReadAndAddOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndAddOp> TYPE = LIRInstructionClass.create(AtomicReadAndAddOp.class);

        private final AArch64Kind accessKind;

        @Def({REG}) protected AllocatableValue resultValue;
        @Alive({REG}) protected AllocatableValue addressValue;
        @Alive({REG, CONST}) protected Value deltaValue;

        AtomicReadAndAddOp(AArch64Kind kind, AllocatableValue result, AllocatableValue address, Value delta) {
            super(TYPE);
            this.accessKind = kind;
            this.resultValue = result;
            this.addressValue = address;
            this.deltaValue = delta;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert accessKind.isInteger();
            final int memAccessSize = accessKind.getSizeInBytes() * Byte.SIZE;
            final int addSize = Math.max(memAccessSize, 32);

            Register address = asRegister(addressValue);
            Register result = asRegister(resultValue);

            Label retry = new Label();
            masm.bind(retry);
            masm.loadExclusive(memAccessSize, result, address, false);
            try (ScratchRegister scratchRegister1 = masm.getScratchRegister()) {
                Register scratch1 = scratchRegister1.getRegister();
                if (LIRValueUtil.isConstantValue(deltaValue)) {
                    long delta = LIRValueUtil.asConstantValue(deltaValue).getJavaConstant().asLong();
                    masm.add(addSize, scratch1, result, delta);
                } else { // must be a register then
                    masm.add(addSize, scratch1, result, asRegister(deltaValue));
                }
                try (ScratchRegister scratchRegister2 = masm.getScratchRegister()) {
                    Register scratch2 = scratchRegister2.getRegister();
                    masm.storeExclusive(memAccessSize, scratch2, scratch1, address, true);
                    // if scratch2 == 0 then write successful, else retry
                    masm.cbnz(32, scratch2, retry);
                }
            }
            /*
             * Use a full barrier for the acquire semantics instead of ldaxr to guarantee that the
             * instruction sequence:
             *
             * A -> ldaxr -> stlxr -> B
             *
             * cannot be executed as:
             *
             * ldaxr -> B -> A -> stlxr
             */
            masm.dmb(AArch64Assembler.BarrierKind.ANY_ANY);
        }
    }

    /**
     * Load (Read) and Add instruction. Does the following atomically:
     *
     * <pre>
     *  ATOMIC_READ_AND_ADD(addend, result, address):
     *    result = *address
     *    *address = result + addend
     *    return result
     * </pre>
     *
     * The LSE version has different properties with regards to the register allocator. To define
     * these differences, we have to create a separate LIR instruction class.
     *
     * The difference to {@linkplain AtomicReadAndAddOp} is:
     * <li>{@linkplain #deltaValue} must be a register (@Use({REG}) instead @Alive({REG,CONST}))
     * <li>{@linkplain #resultValue} may be an alias for the input registers (@Use instead
     * of @Alive)
     */
    @Opcode("ATOMIC_READ_AND_ADD")
    public static final class AtomicReadAndAddLSEOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndAddLSEOp> TYPE = LIRInstructionClass.create(AtomicReadAndAddLSEOp.class);

        private final AArch64Kind accessKind;

        @Def({REG}) protected AllocatableValue resultValue;
        @Use({REG}) protected AllocatableValue addressValue;
        @Use({REG}) protected AllocatableValue deltaValue;

        AtomicReadAndAddLSEOp(AArch64Kind kind, AllocatableValue result, AllocatableValue address, AllocatableValue delta) {
            super(TYPE);
            this.accessKind = kind;
            this.resultValue = result;
            this.addressValue = address;
            this.deltaValue = delta;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            assert accessKind.isInteger();
            final int memAccessSize = accessKind.getSizeInBytes() * Byte.SIZE;

            Register address = asRegister(addressValue);
            Register delta = asRegister(deltaValue);
            Register result = asRegister(resultValue);
            masm.ldadd(memAccessSize, delta, result, address, true, true);
        }
    }

    /**
     * Load (Read) and Write instruction. Does the following atomically:
     *
     * <pre>
     *  ATOMIC_READ_AND_WRITE(newValue, result, address):
     *    result = *address
     *    *address = newValue
     *    return result
     * </pre>
     */
    @Opcode("ATOMIC_READ_AND_WRITE")
    public static final class AtomicReadAndWriteOp extends AArch64LIRInstruction {
        public static final LIRInstructionClass<AtomicReadAndWriteOp> TYPE = LIRInstructionClass.create(AtomicReadAndWriteOp.class);

        private final AArch64Kind accessKind;

        @Def({REG}) protected AllocatableValue resultValue;
        @Alive({REG}) protected AllocatableValue addressValue;
        @Alive({REG}) protected AllocatableValue newValue;

        public AtomicReadAndWriteOp(AArch64Kind kind, AllocatableValue result, AllocatableValue address, AllocatableValue newValue) {
            super(TYPE);
            assert kind.isInteger();
            this.accessKind = kind;
            this.resultValue = result;
            this.addressValue = address;
            this.newValue = newValue;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AArch64MacroAssembler masm) {
            final int memAccessSize = accessKind.getSizeInBytes() * Byte.SIZE;

            Register address = asRegister(addressValue);
            Register value = asRegister(newValue);
            Register result = asRegister(resultValue);

            if (AArch64LIRFlags.useLSE(masm)) {
                masm.swp(memAccessSize, value, result, address, true, true);
            } else {
                try (ScratchRegister scratchRegister = masm.getScratchRegister()) {
                    Register scratch = scratchRegister.getRegister();
                    Label retry = new Label();
                    masm.bind(retry);
                    masm.loadExclusive(memAccessSize, result, address, false);
                    masm.storeExclusive(memAccessSize, scratch, value, address, true);
                    // if scratch == 0 then write successful, else retry
                    masm.cbnz(32, scratch, retry);
                    /*
                     * Use a full barrier for the acquire semantics instead of ldaxr to guarantee
                     * that the instruction sequence:
                     *
                     * A -> ldaxr -> stlxr -> B
                     *
                     * cannot be executed as:
                     *
                     * ldaxr -> B -> A -> stlxr
                     */
                    masm.dmb(AArch64Assembler.BarrierKind.ANY_ANY);
                }
            }
        }
    }
}
