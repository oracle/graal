/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.hotspot.HotSpotGraalRuntime;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.MemOp;
import com.oracle.graal.lir.asm.*;

public class AMD64HotSpotCompare {

    @Opcode("CMP")
    public static final class HotSpotCompareConstantOp extends AMD64LIRInstruction {
        public static final LIRInstructionClass<HotSpotCompareConstantOp> TYPE = LIRInstructionClass.create(HotSpotCompareConstantOp.class);

        @Use({REG}) protected AllocatableValue x;
        protected JavaConstant y;

        public HotSpotCompareConstantOp(AllocatableValue x, JavaConstant y) {
            super(TYPE);
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            assert isRegister(x);
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(y)) {
                // compressed null
                masm.testl(asRegister(x), asRegister(x));
            } else if (y instanceof HotSpotObjectConstant) {
                HotSpotObjectConstant yConst = (HotSpotObjectConstant) y;
                if (yConst.isCompressed()) {
                    // compressed oop
                    crb.recordInlineDataInCode(y);
                    masm.cmpl(asRegister(x), 0xDEADDEAD);
                } else {
                    // uncompressed oop
                    AMD64Address patch = (AMD64Address) crb.recordDataReferenceInCode(y, 8);
                    masm.cmpq(asRegister(x), patch);
                }
            } else if (y instanceof HotSpotMetaspaceConstant) {
                boolean isImmutable = GraalOptions.ImmutableCode.getValue();
                boolean generatePIC = GraalOptions.GeneratePIC.getValue();
                if (y.getKind() == Kind.Int) {
                    // compressed metaspace pointer
                    crb.recordInlineDataInCode(y);
                    if (isImmutable && generatePIC) {
                        Kind hostWordKind = HotSpotGraalRuntime.getHostWordKind();
                        int alignment = hostWordKind.getBitCount() / Byte.SIZE;
                        // recordDataReferenceInCode forces the mov to be rip-relative
                        masm.cmpl(asRegister(x), (AMD64Address) crb.recordDataReferenceInCode(JavaConstant.INT_0, alignment));
                    } else {
                        masm.cmpl(asRegister(x), y.asInt());
                    }
                } else {
                    // uncompressed metaspace pointer
                    if (isImmutable && generatePIC) {
                        crb.recordInlineDataInCode(y);
                        Kind hostWordKind = HotSpotGraalRuntime.getHostWordKind();
                        int alignment = hostWordKind.getBitCount() / Byte.SIZE;
                        // recordDataReferenceInCode forces the mov to be rip-relative
                        masm.cmpq(asRegister(x), (AMD64Address) crb.recordDataReferenceInCode(JavaConstant.INT_0, alignment));
                    } else {
                        AMD64Address patch = (AMD64Address) crb.recordDataReferenceInCode(y, 8);
                        masm.cmpq(asRegister(x), patch);
                    }
                }
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    @Opcode("CMP")
    public static final class HotSpotCompareMemoryConstantOp extends MemOp {
        public static final LIRInstructionClass<HotSpotCompareMemoryConstantOp> TYPE = LIRInstructionClass.create(HotSpotCompareMemoryConstantOp.class);

        protected JavaConstant y;

        public HotSpotCompareMemoryConstantOp(Kind kind, AMD64AddressValue x, JavaConstant y, LIRFrameState state) {
            super(TYPE, kind, x, state);
            this.y = y;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(y)) {
                // compressed null
                masm.cmpl(address.toAddress(), 0);
            } else if (y instanceof HotSpotObjectConstant) {
                HotSpotObjectConstant yConst = (HotSpotObjectConstant) y;
                if (yConst.isCompressed() && crb.target.inlineObjects) {
                    // compressed oop
                    crb.recordInlineDataInCode(y);
                    masm.cmpl(address.toAddress(), 0xDEADDEAD);
                } else {
                    // uncompressed oop
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else if (y instanceof HotSpotMetaspaceConstant) {
                if (y.getKind() == Kind.Int) {
                    // compressed metaspace pointer
                    crb.recordInlineDataInCode(y);
                    masm.cmpl(address.toAddress(), y.asInt());
                } else if (y.getKind() == Kind.Long && NumUtil.is32bit(y.asLong())) {
                    // uncompressed metaspace pointer
                    crb.recordInlineDataInCode(y);
                    masm.cmpq(address.toAddress(), (int) y.asLong());
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

}
