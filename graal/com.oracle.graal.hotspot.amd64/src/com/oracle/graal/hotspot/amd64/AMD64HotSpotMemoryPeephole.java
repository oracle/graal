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

import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.amd64.*;
import com.oracle.graal.compiler.amd64.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.data.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.amd64.*;
import com.oracle.graal.lir.amd64.AMD64ControlFlow.BranchOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;

/**
 * Specialized code gen for comparison with compressed memory.
 */

public class AMD64HotSpotMemoryPeephole extends AMD64MemoryPeephole {
    public static class CompareMemoryCompressedOp extends AMD64LIRInstruction {
        @Alive({COMPOSITE}) protected AMD64AddressValue x;
        @Use({CONST}) protected Value y;
        @State protected LIRFrameState state;

        public CompareMemoryCompressedOp(AMD64AddressValue x, Constant y, LIRFrameState state) {
            assert HotSpotGraalRuntime.runtime().getConfig().useCompressedOops;
            this.x = x;
            this.y = y;
            this.state = state;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb, AMD64MacroAssembler masm) {
            Constant constant = (Constant) y;
            if (constant.isNull()) {
                masm.cmpl(x.toAddress(), 0);
            } else {
                if (y.getKind() == Kind.Object) {
                    crb.recordInlineDataInCode(new OopData(0, HotSpotObjectConstant.asObject(constant), true));
                } else if (y.getKind() == Kind.Long) {
                    crb.recordInlineDataInCode(new MetaspaceData(0, constant.asLong(), HotSpotMetaspaceConstant.getMetaspaceObject(constant), true));
                } else {
                    throw GraalInternalError.shouldNotReachHere();
                }
                if (state != null) {
                    crb.recordImplicitException(masm.position(), state);
                }
                masm.cmpl(x.toAddress(), 0xdeaddead);
            }
        }
    }

    AMD64HotSpotMemoryPeephole(AMD64NodeLIRBuilder gen) {
        super(gen);
    }

    @Override
    protected boolean emitCompareBranchMemory(ValueNode left, ValueNode right, Access access, Condition cond, boolean unorderedIsTrue, LabelRef trueLabel, LabelRef falseLabel,
                    double trueLabelProbability) {
        if (HotSpotGraalRuntime.runtime().getConfig().useCompressedOops) {
            ValueNode other = selectOtherInput(left, right, access);
            Kind kind = access.accessLocation().getValueKind();

            if (other.isConstant() && kind == Kind.Object && access.isCompressible()) {
                ensureEvaluated(other);
                gen.append(new CompareMemoryCompressedOp(makeAddress(access), other.asConstant(), getState(access)));
                Condition finalCondition = right == access ? cond.mirror() : cond;
                gen.append(new BranchOp(finalCondition, trueLabel, falseLabel, trueLabelProbability));
                return true;
            }
        }

        return super.emitCompareBranchMemory(left, right, access, cond, unorderedIsTrue, trueLabel, falseLabel, trueLabelProbability);
    }
}
