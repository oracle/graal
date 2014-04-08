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
package com.oracle.graal.lir.hsail;

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.calc.*;

/**
 * Implementation of compare operations.
 */
public enum HSAILCompare {
    ICMP(Kind.Int),
    LCMP(Kind.Long),
    ACMP(Kind.Object),
    FCMP(Kind.Float),
    DCMP(Kind.Double);

    public final Kind kind;

    private HSAILCompare(Kind kind) {
        this.kind = kind;
    }

    public static class CompareOp extends HSAILLIRInstruction {

        @Opcode private final HSAILCompare opcode;
        @Use({REG, STACK, CONST}) protected Value x;
        @Use({REG, STACK, CONST}) protected Value y;
        @Def({REG}) protected Value z;
        private final Condition condition;
        public boolean unordered = false;

        public CompareOp(HSAILCompare opcode, Condition condition, Value x, Value y, Value z) {
            this.opcode = opcode;
            this.condition = condition;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, HSAILAssembler masm) {
            emit(crb, masm, opcode, condition, x, y, z, unordered);
        }

        @Override
        protected void verify() {
            super.verify();
            assert (x.getKind() == y.getKind() && ((name().startsWith("I") && x.getKind() == Kind.Int) || (name().startsWith("L") && x.getKind() == Kind.Long) ||
                            (name().startsWith("A") && x.getKind() == Kind.Object) || (name().startsWith("F") && x.getKind() == Kind.Float) || (name().startsWith("D") && x.getKind() == Kind.Double)));
        }
    }

    @SuppressWarnings("unused")
    public static void emit(CompilationResultBuilder crb, HSAILAssembler masm, HSAILCompare opcode, Condition condition, Value x, Value y, Value z, boolean unorderedIsTrue) {
        masm.emitCompare(opcode.kind, x, y, conditionToString(condition), unorderedIsTrue, isUnsignedCompare(condition));
    }

    public static String conditionToString(Condition condition) {
        switch (condition) {
            case EQ:
                return "eq";
            case NE:
                return "ne";
            case LT:
            case BT:
                return "lt";
            case LE:
            case BE:
                return "le";
            case GT:
            case AT:
                return "gt";
            case GE:
            case AE:
                return "ge";
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    private static boolean isUnsignedCompare(Condition condition) {
        switch (condition) {
            case BT:
            case BE:
            case AT:
            case AE:
                return true;
            default:
                return false;
        }
    }
}
