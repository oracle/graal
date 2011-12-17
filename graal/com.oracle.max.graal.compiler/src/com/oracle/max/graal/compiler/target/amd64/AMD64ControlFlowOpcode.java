/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.target.amd64;

import com.oracle.max.asm.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.asm.target.amd64.AMD64Assembler.ConditionFlag;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.nodes.calc.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiAddress.Scale;
import com.sun.cri.ci.CiTargetMethod.JumpTable;
import com.sun.cri.ci.CiValue.Formatter;

public class AMD64ControlFlowOpcode {

    public enum LabelOpcode implements LIROpcode {
        LABEL;

        public LIRInstruction create(final Label label, final boolean align) {
            return new AMD64LIRInstruction(this, CiValue.IllegalValue, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    if (align) {
                        masm.align(tasm.target.wordSize);
                    }
                    masm.bind(label);
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    return label.toString();
                }
            };
        }
    }


    public enum ReturnOpcode implements StandardOpcode.ReturnOpcode {
        RETURN;

        public LIRInstruction create(CiValue input) {
            CiValue[] inputs = new CiValue[] {input};

            return new AMD64LIRInstruction(this, CiValue.IllegalValue, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    masm.ret(0);
                }
            };
        }
    }


    public enum JumpOpcode implements LIROpcode {
        JUMP;

        public LIRInstruction create(LabelRef label, LIRDebugInfo info) {
            return new LIRBranch(this, null, false, label, info) {
                @Override
                public void emitCode(TargetMethodAssembler tasm) {
                    AMD64MacroAssembler masm = (AMD64MacroAssembler) tasm.asm;
                    masm.jmp(this.destination.label());
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    return  "[" + destination + "]";
                }
            };
        }
    }


    public enum BranchOpcode implements LIROpcode {
        BRANCH;

        public LIRInstruction create(Condition cond, LabelRef label, LIRDebugInfo info) {
            return new LIRBranch(this, cond, false, label, info) {
                @Override
                public void emitCode(TargetMethodAssembler tasm) {
                    AMD64MacroAssembler masm = (AMD64MacroAssembler) tasm.asm;
                    masm.jcc(intCond(cond), destination.label());
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    return cond.operator + " [" + destination + "]";
                }
            };
        }
    }


    public enum FloatBranchOpcode implements LIROpcode {
        FLOAT_BRANCH;

        public LIRInstruction create(Condition cond, boolean unorderedIsTrue, LabelRef label, LIRDebugInfo info) {
            return new LIRBranch(this, cond, unorderedIsTrue, label, info) {
                @Override
                public void emitCode(TargetMethodAssembler tasm) {
                    floatJcc(tasm, (AMD64MacroAssembler) tasm.asm, cond, unorderedIsTrue, destination.label());
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    return cond.operator + " [" + destination + "]" + (unorderedIsTrue ? " unorderedIsTrue" : " unorderedIsFalse");
                }
            };
        }
    }


    public enum TableSwitchOpcode implements LIROpcode {
        TABLE_SWITCH;

        public LIRInstruction create(final int lowKey, final LabelRef defaultTarget, final LabelRef[] targets, CiVariable index, CiVariable scratch) {
            CiValue[] alives = new CiValue[] {index};
            CiValue[] temps = new CiValue[] {scratch};

            return new AMD64LIRInstruction(this, CiValue.IllegalValue, null, LIRInstruction.NO_OPERANDS, alives, temps) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue index = alive(0);
                    CiValue scratch = temp(0);
                    tableswitch(tasm, masm, lowKey, defaultTarget, targets, tasm.asIntReg(index), tasm.asLongReg(scratch));
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    StringBuilder buf = new StringBuilder(super.operationString(operandFmt));
                    buf.append("\ndefault: [").append(defaultTarget).append(']');
                    int key = lowKey;
                    for (LabelRef l : targets) {
                        buf.append("\ncase ").append(key).append(": [").append(l).append(']');
                        key++;
                    }
                    return buf.toString();
                }
            };
        }
    }


    public enum CondMoveOpcode implements LIROpcode {
        CMOVE;

        public LIRInstruction create(CiVariable result, final Condition condition, CiVariable trueValue, CiValue falseValue) {
            CiValue[] inputs = new CiValue[] {falseValue};
            CiValue[] alives = new CiValue[] {trueValue};

            return new AMD64LIRInstruction(this, result, null, inputs, alives, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue trueValue = alive(0);
                    CiValue falseValue = input(0);
                    cmove(tasm, masm, result(), false, condition, false, trueValue, falseValue);
                }

                @Override
                public CiValue registerHint() {
                    return input(0);
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    return condition.toString() + " " + super.operationString(operandFmt);
                }
            };
        }
    }


    public enum FloatCondMoveOpcode implements LIROpcode {
        FLOAT_CMOVE;

        public LIRInstruction create(CiVariable result, final Condition condition, final boolean unorderedIsTrue, CiVariable trueValue, CiVariable falseValue) {
            CiValue[] alives = new CiValue[] {trueValue, falseValue};

            return new AMD64LIRInstruction(this, result, null, LIRInstruction.NO_OPERANDS, alives, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue trueValue = alive(0);
                    CiValue falseValue = alive(1);
                    cmove(tasm, masm, result(), true, condition, unorderedIsTrue, trueValue, falseValue);
                }

                @Override
                public String operationString(Formatter operandFmt) {
                    return condition.toString() + " unordered=" + unorderedIsTrue + " " + super.operationString(operandFmt);
                }
            };
        }
    }


    private static void tableswitch(TargetMethodAssembler tasm, AMD64MacroAssembler masm, int lowKey, LabelRef defaultTarget, LabelRef[] targets, CiRegister value, CiRegister scratch) {
        Buffer buf = masm.codeBuffer;
        // Compare index against jump table bounds
        int highKey = lowKey + targets.length - 1;
        if (lowKey != 0) {
            // subtract the low value from the switch value
            masm.subl(value, lowKey);
            masm.cmpl(value, highKey - lowKey);
        } else {
            masm.cmpl(value, highKey);
        }

        // Jump to default target if index is not within the jump table
        masm.jcc(ConditionFlag.above, defaultTarget.label());

        // Set scratch to address of jump table
        int leaPos = buf.position();
        masm.leaq(scratch, new CiAddress(tasm.target.wordKind, AMD64.rip.asValue(), 0));
        int afterLea = buf.position();

        // Load jump table entry into scratch and jump to it
        masm.movslq(value, new CiAddress(CiKind.Int, scratch.asValue(), value.asValue(), Scale.Times4, 0));
        masm.addq(scratch, value);
        masm.jmp(scratch);

        // Inserting padding so that jump table address is 4-byte aligned
        if ((buf.position() & 0x3) != 0) {
            masm.nop(4 - (buf.position() & 0x3));
        }

        // Patch LEA instruction above now that we know the position of the jump table
        int jumpTablePos = buf.position();
        buf.setPosition(leaPos);
        masm.leaq(scratch, new CiAddress(tasm.target.wordKind, AMD64.rip.asValue(), jumpTablePos - afterLea));
        buf.setPosition(jumpTablePos);

        // Emit jump table entries
        for (LabelRef target : targets) {
            Label label = target.label();
            int offsetToJumpTableBase = buf.position() - jumpTablePos;
            if (label.isBound()) {
                int imm32 = label.position() - jumpTablePos;
                buf.emitInt(imm32);
            } else {
                label.addPatchAt(buf.position());

                buf.emitByte(0); // psuedo-opcode for jump table entry
                buf.emitShort(offsetToJumpTableBase);
                buf.emitByte(0); // padding to make jump table entry 4 bytes wide
            }
        }

        JumpTable jt = new JumpTable(jumpTablePos, lowKey, highKey, 4);
        tasm.targetMethod.addAnnotation(jt);
    }

    private static void floatJcc(TargetMethodAssembler tasm, AMD64MacroAssembler masm, Condition condition, boolean unorderedIsTrue, Label label) {
        ConditionFlag cond = floatCond(condition);
        Label endLabel = new Label();
        if (unorderedIsTrue && !trueOnUnordered(cond)) {
            masm.jcc(ConditionFlag.parity, label);
        } else if (!unorderedIsTrue && trueOnUnordered(cond)) {
            masm.jcc(ConditionFlag.parity, endLabel);
        }
        masm.jcc(cond, label);
        masm.bind(endLabel);
    }

    private static void cmove(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, boolean isFloat, Condition condition, boolean unorderedIsTrue, CiValue trueValue, CiValue falseValue) {
        ConditionFlag cond = isFloat ? floatCond(condition) : intCond(condition);
        // check that we don't overwrite an input operand before it is used.
        assert !result.equals(trueValue);

        AMD64MoveOpcode.move(tasm, masm, result, falseValue);
        cmove(tasm, masm, result, cond, trueValue);

        if (isFloat) {
            if (unorderedIsTrue && !trueOnUnordered(cond)) {
                cmove(tasm, masm, result, ConditionFlag.parity, trueValue);
            } else if (!unorderedIsTrue && trueOnUnordered(cond)) {
                cmove(tasm, masm, result, ConditionFlag.parity, falseValue);
            }
        }
    }

    private static void cmove(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, ConditionFlag cond, CiValue other) {
        if (other.isRegister()) {
            assert tasm.asRegister(other) != tasm.asRegister(result) : "other already overwritten by previous move";
            switch (other.kind) {
                case Int:  masm.cmovl(cond, tasm.asRegister(result), tasm.asRegister(other)); break;
                case Long: masm.cmovq(cond, tasm.asRegister(result), tasm.asRegister(other)); break;
                default:   throw Util.shouldNotReachHere();
            }
        } else {
            switch (other.kind) {
                case Int:  masm.cmovl(cond, tasm.asRegister(result), tasm.asAddress(other)); break;
                case Long: masm.cmovq(cond, tasm.asRegister(result), tasm.asAddress(other)); break;
                default:   throw Util.shouldNotReachHere();
            }
        }
    }

    private static ConditionFlag intCond(Condition cond) {
        switch (cond) {
            case EQ: return ConditionFlag.equal;
            case NE: return ConditionFlag.notEqual;
            case LT: return ConditionFlag.less;
            case LE: return ConditionFlag.lessEqual;
            case GE: return ConditionFlag.greaterEqual;
            case GT: return ConditionFlag.greater;
            case BE: return ConditionFlag.belowEqual;
            case AE: return ConditionFlag.aboveEqual;
            case AT: return ConditionFlag.above;
            case BT: return ConditionFlag.below;
            case OF: return ConditionFlag.overflow;
            case NOF: return ConditionFlag.noOverflow;
            default: throw Util.shouldNotReachHere();
        }
    }

    private static ConditionFlag floatCond(Condition cond) {
        switch (cond) {
            case EQ: return ConditionFlag.equal;
            case NE: return ConditionFlag.notEqual;
            case BT: return ConditionFlag.below;
            case BE: return ConditionFlag.belowEqual;
            case AE: return ConditionFlag.aboveEqual;
            case AT: return ConditionFlag.above;
            default: throw Util.shouldNotReachHere();
        }
    }

    private static boolean trueOnUnordered(ConditionFlag condition) {
        switch(condition) {
            case aboveEqual:
            case notEqual:
            case above:
            case less:
            case overflow:
                return false;
            case equal:
            case belowEqual:
            case below:
            case greaterEqual:
            case noOverflow:
                return true;
            default:
                throw Util.shouldNotReachHere();
        }
    }
}
