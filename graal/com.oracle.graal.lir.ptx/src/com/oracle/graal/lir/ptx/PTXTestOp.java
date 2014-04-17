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
package com.oracle.graal.lir.ptx;

import static com.oracle.graal.asm.ptx.PTXAssembler.BooleanOperator.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.ptx.*;
import com.oracle.graal.asm.ptx.PTXAssembler.Setp;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.lir.asm.*;

public class PTXTestOp extends PTXLIRInstruction {

    @Use({REG}) protected Value x;
    @Use({REG, STACK, CONST}) protected Value y;
    int predicate;

    public PTXTestOp(Value x, Value y, int predicate) {
        this.x = x;
        this.y = y;
        this.predicate = predicate;
    }

    @Override
    public void emitCode(CompilationResultBuilder crb, PTXMacroAssembler masm) {
        emit(masm, x, y, predicate);
    }

    @Override
    protected void verify() {
        super.verify();
        assert (x.getKind() == Kind.Int && y.getKind().getStackKind() == Kind.Int) || (x.getKind() == Kind.Long && y.getKind() == Kind.Long) : x + " " + y;
    }

    public static void emit(PTXAssembler masm, Value x, Value y, int predicate) {
        /*
         * This is not yet quite right - as the result for the equivalent in
         * ControlPTXText.testIntegerTestBranch2I is wrong.
         */
        new Setp(Condition.EQ, AND, x, y, predicate).emit(masm);
    }
}
