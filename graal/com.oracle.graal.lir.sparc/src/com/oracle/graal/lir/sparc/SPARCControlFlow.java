/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRInstruction.OperandFlag.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CompilationResult.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.*;
import com.oracle.graal.asm.sparc.*;
import com.oracle.graal.asm.sparc.SPARCAssembler.CC;
import com.oracle.graal.asm.sparc.SPARCAssembler.Bpe;
import com.oracle.graal.asm.sparc.SPARCAssembler.Subcc;
import com.oracle.graal.graph.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.sparc.SPARC;

public class SPARCControlFlow {

    public static class ReturnOp extends SPARCLIRInstruction {

        @Use({ REG, ILLEGAL })
        protected Value x;

        public ReturnOp(Value x) {
            this.x = x;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            if (tasm.frameContext != null) {
                tasm.frameContext.leave(tasm);
            }
            // masm.return();
        }
    }

    public static class SequentialSwitchOp extends SPARCLIRInstruction
            implements FallThroughOp {

        @Use({ CONST })
        protected Constant[] keyConstants;
        private final LabelRef[] keyTargets;
        private LabelRef defaultTarget;
        @Alive({ REG })
        protected Value key;
        @Temp({ REG, ILLEGAL })
        protected Value scratch;

        public SequentialSwitchOp(Constant[] keyConstants,
                LabelRef[] keyTargets, LabelRef defaultTarget, Value key,
                Value scratch) {
            assert keyConstants.length == keyTargets.length;
            this.keyConstants = keyConstants;
            this.keyTargets = keyTargets;
            this.defaultTarget = defaultTarget;
            this.key = key;
            this.scratch = scratch;
        }

        @Override
        @SuppressWarnings("unused")
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            if (key.getKind() == Kind.Int) {
                Register intKey = asIntReg(key);
                for (int i = 0; i < keyConstants.length; i++) {
                    if (tasm.runtime.needsDataPatch(keyConstants[i])) {
                        tasm.recordDataReferenceInCode(keyConstants[i], 0, true);
                    }
                    long lc = keyConstants[i].asLong();
                    assert NumUtil.isInt(lc);
                    new Subcc(masm, intKey, (int) lc, SPARC.r0); // CMP
                    Label l = keyTargets[i].label();
                    l.addPatchAt(tasm.asm.codeBuffer.position());
                    new Bpe(masm, CC.Icc, l);
                }
            } else if (key.getKind() == Kind.Long) {
                Register longKey = asLongReg(key);
                for (int i = 0; i < keyConstants.length; i++) {
                    // masm.setp_eq_s64(tasm.asLongConst(keyConstants[i]),
                    // longKey);
                    // masm.at();
                    Label l = keyTargets[i].label();
                    l.addPatchAt(tasm.asm.codeBuffer.position());
                    new Bpe(masm, CC.Xcc, l);
                }
            } else if (key.getKind() == Kind.Object) {
                Register intKey = asObjectReg(key);
                Register temp = asObjectReg(scratch);
                for (int i = 0; i < keyConstants.length; i++) {
                    SPARCMove.move(tasm, masm, temp.asValue(Kind.Object),
                            keyConstants[i]);
                    new Subcc(masm, intKey, temp, SPARC.r0); // CMP
                    new Bpe(masm, CC.Icc, keyTargets[i].label());
                }
            } else {
                throw new GraalInternalError(
                        "sequential switch only supported for int, long and object");
            }
            if (defaultTarget != null) {
                masm.jmp(defaultTarget.label());
            } else {
                // masm.hlt();
            }
        }

        @Override
        public LabelRef fallThroughTarget() {
            return defaultTarget;
        }

        @Override
        public void setFallThroughTarget(LabelRef target) {
            defaultTarget = target;
        }
    }

    public static class TableSwitchOp extends SPARCLIRInstruction {

        private final int lowKey;
        private final LabelRef defaultTarget;
        private final LabelRef[] targets;
        @Alive
        protected Value index;
        @Temp
        protected Value scratch;

        public TableSwitchOp(final int lowKey, final LabelRef defaultTarget,
                final LabelRef[] targets, Variable index, Variable scratch) {
            this.lowKey = lowKey;
            this.defaultTarget = defaultTarget;
            this.targets = targets;
            this.index = index;
            this.scratch = scratch;
        }

        @Override
        public void emitCode(TargetMethodAssembler tasm, SPARCAssembler masm) {
            tableswitch(tasm, masm, lowKey, defaultTarget, targets,
                    asIntReg(index), asLongReg(scratch));
        }
    }

    @SuppressWarnings("unused")
    private static void tableswitch(TargetMethodAssembler tasm,
            SPARCAssembler masm, int lowKey, LabelRef defaultTarget,
            LabelRef[] targets, Register value, Register scratch) {
        Buffer buf = masm.codeBuffer;
        // Compare index against jump table bounds
        int highKey = lowKey + targets.length - 1;
        if (lowKey != 0) {
            // subtract the low value from the switch value
            // masm.sub_s32(value, value, lowKey);
            // masm.setp_gt_s32(value, highKey - lowKey);
        } else {
            // masm.setp_gt_s32(value, highKey);
        }

        // Jump to default target if index is not within the jump table
        if (defaultTarget != null) {
            // masm.at();
            // masm.bra(defaultTarget.label().toString());
        }

        // address of jump table
        int tablePos = buf.position();

        JumpTable jt = new JumpTable(tablePos, lowKey, highKey, 4);
        tasm.compilationResult.addAnnotation(jt);

        // SPARC: unimp: tableswitch extract
    }
}
