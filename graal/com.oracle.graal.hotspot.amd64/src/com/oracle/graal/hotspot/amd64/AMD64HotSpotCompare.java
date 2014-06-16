/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.hotspot.data.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.type.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64Move.MemOp;
import com.oracle.graal.lir.asm.*;

public class AMD64HotSpotCompare {

    @Opcode("NCMP")
    public static class HotSpotCompareNarrowOp extends AMD64LIRInstruction {

        @Use({REG}) protected AllocatableValue x;
        @Use({REG, STACK}) protected AllocatableValue y;

        public HotSpotCompareNarrowOp(AllocatableValue x, AllocatableValue y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (isRegister(y)) {
                masm.cmpl(asRegister(x), asRegister(y));
            } else {
                assert isStackSlot(y);
                masm.cmpl(asRegister(x), (AMD64Address) crb.asAddress(y));
            }
        }

        @Override
        protected void verify() {
            assert x.getPlatformKind() == NarrowOopStamp.NarrowOop && y.getPlatformKind() == NarrowOopStamp.NarrowOop;
        }
    }

    @Opcode("CMP")
    public static class HotSpotCompareConstantOp extends AMD64LIRInstruction {

        @Use({REG}) protected AllocatableValue x;
        protected Constant y;

        public HotSpotCompareConstantOp(AllocatableValue x, Constant y) {
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
                if (HotSpotObjectConstant.isCompressed(y)) {
                    // compressed oop
                    crb.recordInlineDataInCode(new OopData(0, HotSpotObjectConstant.asObject(y), true));
                    masm.cmpl(asRegister(x), 0xDEADDEAD);
                } else {
                    // uncompressed oop
                    AMD64Address patch = (AMD64Address) crb.recordDataReferenceInCode(new OopData(8, HotSpotObjectConstant.asObject(y), false));
                    masm.cmpq(asRegister(x), patch);
                }
            } else if (y instanceof HotSpotMetaspaceConstant) {
                if (y.getKind() == Kind.Int) {
                    // compressed metaspace pointer
                    crb.recordInlineDataInCode(new MetaspaceData(0, y.asInt(), HotSpotMetaspaceConstant.getMetaspaceObject(y), true));
                    masm.cmpl(asRegister(x), y.asInt());
                } else {
                    // uncompressed metaspace pointer
                    AMD64Address patch = (AMD64Address) crb.recordDataReferenceInCode(new MetaspaceData(8, y.asLong(), HotSpotObjectConstant.asObject(y), false));
                    masm.cmpq(asRegister(x), patch);
                }
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

    @Opcode("CMP")
    public static class HotSpotCompareMemoryConstantOp extends MemOp {

        protected Constant y;

        public HotSpotCompareMemoryConstantOp(Kind kind, AMD64AddressValue x, Constant y, LIRFrameState state) {
            super(kind, x, state);
            this.y = y;
        }

        @Override
        protected void emitMemAccess(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            if (HotSpotCompressedNullConstant.COMPRESSED_NULL.equals(y)) {
                // compressed null
                masm.cmpl(address.toAddress(), 0);
            } else if (y instanceof HotSpotObjectConstant) {
                if (HotSpotObjectConstant.isCompressed(y) && crb.target.inlineObjects) {
                    // compressed oop
                    crb.recordInlineDataInCode(new OopData(0, HotSpotObjectConstant.asObject(y), true));
                    masm.cmpl(address.toAddress(), 0xDEADDEAD);
                } else {
                    // uncompressed oop
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else if (y instanceof HotSpotMetaspaceConstant) {
                if (y.getKind() == Kind.Int) {
                    // compressed metaspace pointer
                    crb.recordInlineDataInCode(new MetaspaceData(0, y.asInt(), HotSpotMetaspaceConstant.getMetaspaceObject(y), true));
                    masm.cmpl(address.toAddress(), y.asInt());
                } else {
                    // uncompressed metaspace pointer
                    throw GraalInternalError.shouldNotReachHere();
                }
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }
        }
    }

}
