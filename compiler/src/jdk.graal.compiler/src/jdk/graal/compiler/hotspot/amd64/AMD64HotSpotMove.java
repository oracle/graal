/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.HINT;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.REG;
import static jdk.graal.compiler.lir.LIRInstruction.OperandFlag.STACK;
import static jdk.vm.ci.code.ValueUtil.asRegister;
import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import jdk.graal.compiler.asm.amd64.AMD64Address;
import jdk.graal.compiler.asm.amd64.AMD64MacroAssembler;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotMarkId;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.StandardOp.LoadConstantOp;
import jdk.graal.compiler.lir.amd64.AMD64LIRInstruction;
import jdk.graal.compiler.lir.asm.CompilationResultBuilder;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Constant;

public class AMD64HotSpotMove {

    public static final class HotSpotLoadObjectConstantOp extends AMD64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<HotSpotLoadObjectConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadObjectConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final HotSpotObjectConstant input;

        public HotSpotLoadObjectConstantOp(AllocatableValue result, HotSpotObjectConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            boolean compressed = input.isCompressed();
            if (crb.target.inlineObjects) {
                crb.recordInlineDataInCode(input);
                if (isRegister(result)) {
                    if (compressed) {
                        masm.movl(asRegister(result), 0xDEADDEAD);
                    } else {
                        masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                    }
                } else {
                    assert isStackSlot(result);
                    if (compressed) {
                        masm.movl((AMD64Address) crb.asAddress(result), 0xDEADDEAD);
                    } else {
                        throw GraalError.shouldNotReachHere("Cannot store 64-bit constants to memory"); // ExcludeFromJacocoGeneratedReport
                    }
                }
            } else {
                if (isRegister(result)) {
                    AMD64Address address = (AMD64Address) crb.recordDataReferenceInCode(input, compressed ? 4 : 8);
                    if (compressed) {
                        masm.movl(asRegister(result), address);
                    } else {
                        masm.movq(asRegister(result), address);
                    }
                } else {
                    throw GraalError.shouldNotReachHere("Cannot directly store data patch to memory"); // ExcludeFromJacocoGeneratedReport
                }
            }
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public boolean canRematerializeToStack() {
            /*
             * This is slightly too lenient, formally we would also need to check if the target
             * allows inlining objects. In practice that is always true, and we do not have access
             * to the relevant information here.
             */
            return input.isCompressed();
        }
    }

    public static final class BaseMove extends AMD64LIRInstruction {
        public static final LIRInstructionClass<BaseMove> TYPE = LIRInstructionClass.create(BaseMove.class);

        @Def({REG, HINT}) protected AllocatableValue result;

        public BaseMove(AllocatableValue result) {
            super(TYPE);
            this.result = result;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            masm.movq(asRegister(result), masm.getPlaceholder(-1));
            crb.recordMark(HotSpotMarkId.NARROW_KLASS_BASE_ADDRESS);
        }

    }

    public static final class HotSpotLoadMetaspaceConstantOp extends AMD64LIRInstruction implements LoadConstantOp {
        public static final LIRInstructionClass<HotSpotLoadMetaspaceConstantOp> TYPE = LIRInstructionClass.create(HotSpotLoadMetaspaceConstantOp.class);

        @Def({REG, STACK}) private AllocatableValue result;
        private final HotSpotMetaspaceConstant input;

        public HotSpotLoadMetaspaceConstantOp(AllocatableValue result, HotSpotMetaspaceConstant input) {
            super(TYPE);
            this.result = result;
            this.input = input;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            boolean compressed = input.isCompressed();
            if (isRegister(result)) {
                if (compressed) {
                    crb.recordInlineDataInCode(input);
                    masm.movl(asRegister(result), 0xDEADDEAD);
                } else {
                    crb.recordInlineDataInCode(input);
                    masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                }
            } else {
                assert isStackSlot(result);
                if (compressed) {
                    crb.recordInlineDataInCode(input);
                    masm.movl((AMD64Address) crb.asAddress(result), 0xDEADDEAD);
                } else {
                    throw GraalError.shouldNotReachHere("Cannot store 64-bit constants to memory"); // ExcludeFromJacocoGeneratedReport
                }
            }
        }

        @Override
        public Constant getConstant() {
            return input;
        }

        @Override
        public AllocatableValue getResult() {
            return result;
        }

        @Override
        public boolean canRematerializeToStack() {
            return input.isCompressed();
        }
    }

    /**
     * Decode compressed class pointer stored in {@code register}. The content in {@code scratch}
     * might be destroyed.
     */
    public static void decodeKlassPointer(AMD64MacroAssembler masm, Register register, Register scratch, GraalHotSpotVMConfig config) {
        CompressEncoding encoding = config.getKlassEncoding();
        if (encoding.getShift() != 0) {
            masm.shlq(register, encoding.getShift());
        }
        if (encoding.hasBase()) {
            assert encoding.getBase() != 0;
            masm.movq(scratch, encoding.getBase());
            masm.addq(register, scratch);
        }
    }
}
