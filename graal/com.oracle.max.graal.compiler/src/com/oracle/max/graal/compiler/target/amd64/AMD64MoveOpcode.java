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

import static java.lang.Double.*;
import static java.lang.Float.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.lir.FrameMap.*;
import com.oracle.max.graal.compiler.util.*;
import com.sun.cri.ci.*;

public class AMD64MoveOpcode {

    public enum MoveOpcode implements StandardOpcode.MoveOpcode {
        MOVE;

        @Override
        public LIRInstruction create(CiValue result, CiValue input) {
            assert !result.isAddress() && !input.isAddress();
            assert result.kind == result.kind.stackKind() && result.kind != CiKind.Illegal;
            CiValue[] inputs = new CiValue[] {input};

            return new AMD64LIRInstruction(this, result, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue input = input(0);
                    move(tasm, masm, result(), input);
                }

                @Override
                public CiValue registerHint() {
                    return input(0);
                }
            };
        }
    }


    public enum LoadOpcode implements LIROpcode {
        LOAD;

        public LIRInstruction create(CiVariable result, CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement, final CiKind kind, LIRDebugInfo info) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex};

            return new AMD64LIRInstruction(this, result, info, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue addrBase = input(0);
                    CiValue addrIndex = input(1);
                    load(tasm, masm, result(), new CiAddress(CiKind.Illegal, addrBase, addrIndex, addrScale, addrDisplacement), kind, info);
                }
            };
        }
    }


    public enum StoreOpcode implements LIROpcode {
        STORE;

        public LIRInstruction create(CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement, CiValue input, final CiKind kind, LIRDebugInfo info) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex, input};

            return new AMD64LIRInstruction(this, CiValue.IllegalValue, info, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue addrBase = input(0);
                    CiValue addrIndex = input(1);
                    CiValue input = input(2);

                    store(tasm, masm, new CiAddress(CiKind.Illegal, addrBase, addrIndex, addrScale, addrDisplacement), input, kind, info);
                }
            };
        }
    }


    public enum LeaOpcode implements LIROpcode {
        LEA;

        public LIRInstruction create(CiVariable result, CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex};

            return new AMD64LIRInstruction(this, result, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue addrBase = input(0);
                    CiValue addrIndex = input(1);
                    masm.leaq(tasm.asLongReg(result()), new CiAddress(CiKind.Illegal, addrBase, addrIndex, addrScale, addrDisplacement));
                }
            };
        }
    }


    // Note: This LIR operation is similar to a LEA, so reusing the LEA op would be desirable.
    // However, the address that is loaded depends on the stack slot, and the stack slot numbers are
    // only fixed after register allocation when the number of spill slots is known. Therefore, the address
    // is not known when the LIR is generated.
    public enum LeaStackBlockOpcode implements LIROpcode {
        LEA_STACK_BLOCK;

        public LIRInstruction create(CiVariable result, final StackBlock stackBlock) {
            return new AMD64LIRInstruction(this, result, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    masm.leaq(tasm.asRegister(result()), tasm.compilation.frameMap().toStackAddress(stackBlock));
                }
            };
        }
    }


    public enum MembarOpcode implements LIROpcode {
        MEMBAR;

        public LIRInstruction create(final int barriers) {
            return new AMD64LIRInstruction(this, CiValue.IllegalValue, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    masm.membar(barriers);
                }
            };
        }
    }


    public enum NullCheckOpcode implements StandardOpcode.NullCheckOpcode {
        NULL_CHECK;

        @Override
        public LIRInstruction create(CiVariable input, LIRDebugInfo info) {
            CiValue[] inputs = new CiValue[] {input};

            return new AMD64LIRInstruction(this, CiValue.IllegalValue, info, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue input = input(0);
                    tasm.recordImplicitException(masm.codeBuffer.position(), info);
                    masm.nullCheck(tasm.asRegister(input));
                }
            };
        }
    }


    public enum CompareAndSwapOpcode implements LIROpcode {
        CAS;

        public LIRInstruction create(CiRegisterValue result, CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement, CiRegisterValue cmpValue, CiVariable newValue) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex, cmpValue, newValue};

            return new AMD64LIRInstruction(this, result, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    CiValue addrBase = input(0);
                    CiValue addrIndex = input(1);
                    CiValue cmpValue = input(2);
                    CiValue newValue = input(3);

                    compareAndSwap(tasm, masm, result(), new CiAddress(CiKind.Illegal, addrBase, addrIndex, addrScale, addrDisplacement), cmpValue, newValue);
                }
            };
        }
    }


    protected static void move(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        if (input.isRegister()) {
            if (result.isRegister()) {
                reg2reg(tasm, masm, result, input);
            } else if (result.isStackSlot()) {
                reg2stack(tasm, masm, result, input);
            } else {
                throw Util.shouldNotReachHere();
            }
        } else if (input.isStackSlot()) {
            if (result.isRegister()) {
                stack2reg(tasm, masm, result, input);
            } else if (result.isStackSlot()) {
                stack2stack(tasm, masm, result, input);
            } else {
                throw Util.shouldNotReachHere();
            }
        } else if (input.isConstant()) {
            if (result.isRegister()) {
                const2reg(tasm, masm, result, (CiConstant) input);
            } else if (result.isStackSlot()) {
                const2stack(tasm, masm, result, (CiConstant) input);
            } else {
                throw Util.shouldNotReachHere();
            }
        } else {
            throw Util.shouldNotReachHere();
        }
    }

    private static void reg2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        if (input.equals(result)) {
            return;
        }
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(tasm.asRegister(result),    tasm.asRegister(input)); break;
            case Long:   masm.movq(tasm.asRegister(result),    tasm.asRegister(input)); break;
            case Float:  masm.movflt(tasm.asFloatReg(result),  tasm.asFloatReg(input)); break;
            case Double: masm.movdbl(tasm.asDoubleReg(result), tasm.asDoubleReg(input)); break;
            case Object: masm.movq(tasm.asRegister(result),    tasm.asRegister(input)); break;
            default:     throw Util.shouldNotReachHere("kind=" + result.kind);
        }
    }

    private static void reg2stack(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(tasm.asAddress(result),   tasm.asRegister(input)); break;
            case Long:   masm.movq(tasm.asAddress(result),   tasm.asRegister(input)); break;
            case Float:  masm.movflt(tasm.asAddress(result), tasm.asFloatReg(input)); break;
            case Double: masm.movsd(tasm.asAddress(result),  tasm.asDoubleReg(input)); break;
            case Object: masm.movq(tasm.asAddress(result),   tasm.asRegister(input)); break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    private static void stack2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(tasm.asRegister(result),    tasm.asAddress(input)); break;
            case Long:   masm.movq(tasm.asRegister(result),    tasm.asAddress(input)); break;
            case Float:  masm.movflt(tasm.asFloatReg(result),  tasm.asAddress(input)); break;
            case Double: masm.movdbl(tasm.asDoubleReg(result), tasm.asAddress(input)); break;
            case Object: masm.movq(tasm.asRegister(result),    tasm.asAddress(input)); break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    private static void stack2stack(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        switch (result.kind) {
            case Jsr:
            case Int:
            case Float:
                masm.pushl(tasm.asAddress(input));
                masm.popl(tasm.asAddress(result));
                break;
            case Long:
            case Double:
            case Object:
                masm.pushq(tasm.asAddress(input));
                masm.popq(tasm.asAddress(result));
                break;
            default:
                throw Util.shouldNotReachHere();
        }
    }

    private static void const2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiConstant c) {
        switch (result.kind) {
            case Jsr:
            case Int:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movl(tasm.asRegister(result), tasm.asIntConst(c));
                break;
            case Long:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movq(tasm.asRegister(result), c.asLong());
                break;
            case Float:
                // This is *not* the same as 'constant == 0.0f' in the case where constant is -0.0f
                if (Float.floatToRawIntBits(c.asFloat()) == Float.floatToRawIntBits(0.0f)) {
                    masm.xorps(tasm.asFloatReg(result), tasm.asFloatReg(result));
                } else {
                    masm.movflt(tasm.asFloatReg(result), tasm.asFloatConstRef(c));
                }
                break;
            case Double:
                // This is *not* the same as 'constant == 0.0d' in the case where constant is -0.0d
                if (Double.doubleToRawLongBits(c.asDouble()) == Double.doubleToRawLongBits(0.0d)) {
                    masm.xorpd(tasm.asDoubleReg(result), tasm.asDoubleReg(result));
                } else {
                    masm.movdbl(tasm.asDoubleReg(result), tasm.asDoubleConstRef(c));
                }
                break;
            case Object:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (c.isNull()) {
                    masm.movq(tasm.asRegister(result), 0x0L);
                } else if (tasm.target.inlineObjects) {
                    tasm.recordDataReferenceInCode(c, 0);
                    masm.movq(tasm.asRegister(result), 0xDEADDEADDEADDEADL);
                } else {
                    masm.movq(tasm.asRegister(result), tasm.recordDataReferenceInCode(c, 0));
                }
                break;
            default:
                throw Util.shouldNotReachHere();
        }
    }

    private static void const2stack(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiConstant c) {
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(tasm.asAddress(result), c.asInt()); break;
            case Long:   masm.movlong(tasm.asAddress(result), c.asLong()); break;
            case Float:  masm.movl(tasm.asAddress(result), floatToRawIntBits(c.asFloat())); break;
            case Double: masm.movlong(tasm.asAddress(result), doubleToRawLongBits(c.asDouble())); break;
            case Object:
                if (c.isNull()) {
                    masm.movlong(tasm.asAddress(result), 0L);
                } else {
                    throw Util.shouldNotReachHere("Non-null object constants must be in register");
                }
                break;
            default:
                throw Util.shouldNotReachHere();
        }
    }


    protected static void load(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiAddress loadAddr, CiKind kind, LIRDebugInfo info) {
        if (info != null) {
            tasm.recordImplicitException(masm.codeBuffer.position(), info);
        }
        switch (kind) {
            case Boolean:
            case Byte:   masm.movsxb(tasm.asRegister(result),  loadAddr); break;
            case Char:   masm.movzxl(tasm.asRegister(result),  loadAddr); break;
            case Short:  masm.movswl(tasm.asRegister(result),  loadAddr); break;
            case Int:    masm.movslq(tasm.asRegister(result),  loadAddr); break;
            case Long:   masm.movq(tasm.asRegister(result),    loadAddr); break;
            case Float:  masm.movflt(tasm.asFloatReg(result),  loadAddr); break;
            case Double: masm.movdbl(tasm.asDoubleReg(result), loadAddr); break;
            case Object: masm.movq(tasm.asRegister(result),    loadAddr); break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    protected static void store(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiAddress storeAddr, CiValue input, CiKind kind, LIRDebugInfo info) {
        if (info != null) {
            tasm.recordImplicitException(masm.codeBuffer.position(), info);
        }

        if (input.isRegister()) {
            switch (kind) {
                case Boolean:
                case Byte:   masm.movb(storeAddr,   tasm.asRegister(input)); break;
                case Char:
                case Short:  masm.movw(storeAddr,   tasm.asRegister(input)); break;
                case Int:    masm.movl(storeAddr,   tasm.asRegister(input)); break;
                case Long:   masm.movq(storeAddr,   tasm.asRegister(input)); break;
                case Float:  masm.movflt(storeAddr, tasm.asFloatReg(input)); break;
                case Double: masm.movsd(storeAddr,  tasm.asDoubleReg(input)); break;
                case Object: masm.movq(storeAddr,   tasm.asRegister(input)); break;
                default:     throw Util.shouldNotReachHere("kind=" + kind);
            }
        } else if (input.isConstant()) {
            CiConstant c = (CiConstant) input;
            switch (kind) {
                case Boolean:
                case Byte:   masm.movb(storeAddr, c.asInt() & 0xFF); break;
                case Char:
                case Short:  masm.movw(storeAddr, c.asInt() & 0xFFFF); break;
                case Jsr:
                case Int:    masm.movl(storeAddr, c.asInt()); break;
                case Long:
                    if (Util.isInt(c.asLong())) {
                        masm.movslq(storeAddr, (int) c.asLong());
                    } else {
                        throw Util.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                    break;
                case Float:  masm.movl(storeAddr, floatToRawIntBits(c.asFloat())); break;
                case Double: throw Util.shouldNotReachHere("Cannot store 64-bit constants to memory");
                case Object:
                    if (c.isNull()) {
                        masm.movptr(storeAddr, 0);
                    } else {
                        throw Util.shouldNotReachHere("Cannot store 64-bit constants to memory");
                    }
                    break;
                default:
                    throw Util.shouldNotReachHere("kind=" + kind);
            }

        } else {
            throw Util.shouldNotReachHere();
        }
    }

    protected static void compareAndSwap(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiAddress address, CiValue cmpValue, CiValue newValue) {
        assert tasm.asRegister(cmpValue) == AMD64.rax && tasm.asRegister(result) == AMD64.rax;

        if (tasm.target.isMP) {
            masm.lock();
        }
        switch (cmpValue.kind) {
            case Int:    masm.cmpxchgl(tasm.asRegister(newValue), address); break;
            case Long:
            case Object: masm.cmpxchgq(tasm.asRegister(newValue), address); break;
            default:     throw Util.shouldNotReachHere();
        }
    }
}
