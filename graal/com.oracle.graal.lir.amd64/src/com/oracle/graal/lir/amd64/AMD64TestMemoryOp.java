/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;

public class AMD64TestMemoryOp extends AMD64LIRInstruction {

    @Use({COMPOSITE}) protected AMD64AddressValue x;
    @Use({REG, CONST}) protected Value y;
    @State protected LIRFrameState state;

    public AMD64TestMemoryOp(AMD64AddressValue x, Value y, LIRFrameState state) {
        this.x = x;
        this.y = y;
        this.state = state;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
        if (state != null) {
            crb.recordImplicitException(masm.position(), state);
        }
        emit(crb, masm, x, y);
    }

    @Override
    protected void verify() {
        super.verify();
        // Can't check the kind of an address so just check the other input
        assert (x.getKind() == Kind.Int || x.getKind() == Kind.Long) : x + " " + y;
    }

    public static void emit(CompilationResultBuilder crb, AMD64MacroAssembler masm, Value x, Value y) {
        if (isRegister(y)) {
            switch (y.getKind()) {
                case Int:
                    masm.testl(asIntReg(y), ((AMD64AddressValue) x).toAddress());
                    break;
                case Long:
                    masm.testq(asLongReg(y), ((AMD64AddressValue) x).toAddress());
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else if (isConstant(y)) {
            switch (y.getKind()) {
                case Int:
                    masm.testl(((AMD64AddressValue) x).toAddress(), crb.asIntConst(y));
                    break;
                case Long:
                    masm.testq(((AMD64AddressValue) x).toAddress(), crb.asIntConst(y));
                    break;
                default:
                    throw GraalInternalError.shouldNotReachHere();
            }
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
    }
}
