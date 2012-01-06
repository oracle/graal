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
package com.oracle.max.graal.compiler.target.amd64;

import static com.oracle.max.cri.ci.CiValueUtil.*;
import static java.lang.Double.*;
import static java.lang.Float.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.compiler.asm.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.compiler.util.*;

public class AMD64MoveOpcode {

    public enum MoveOpcode implements StandardOpcode.MoveOpcode {
        MOVE;

        @Override
        public LIRInstruction create(CiValue result, CiValue input) {
            assert result.kind == result.kind.stackKind() && result.kind != CiKind.Illegal;
            CiValue[] inputs = new CiValue[] {input};
            CiValue[] outputs = new CiValue[] {result};

            return new AMD64LIRInstruction(this, outputs, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    move(tasm, masm, output(0), input(0));
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

        public LIRInstruction create(Variable result, CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement, final CiKind kind, LIRDebugInfo info) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex};
            CiValue[] outputs = new CiValue[] {result};

            return new AMD64LIRInstruction(this, outputs, info, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    load(tasm, masm, output(0), new CiAddress(CiKind.Illegal, input(0), input(1), addrScale, addrDisplacement), kind, info);
                }
            };
        }
    }


    public enum StoreOpcode implements LIROpcode {
        STORE;

        public LIRInstruction create(CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement, CiValue input, final CiKind kind, LIRDebugInfo info) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex, input};

            return new AMD64LIRInstruction(this, LIRInstruction.NO_OPERANDS, info, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    store(tasm, masm, new CiAddress(CiKind.Illegal, input(0), input(1), addrScale, addrDisplacement), input(2), kind, info);
                }
            };
        }
    }


    public enum LeaMemoryOpcode implements LIROpcode {
        LEA_MEMORY;

        public LIRInstruction create(Variable result, CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex};
            CiValue[] outputs = new CiValue[] {result};

            return new AMD64LIRInstruction(this, outputs, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    masm.leaq(asLongReg(output(0)), new CiAddress(CiKind.Illegal, input(0), input(1), addrScale, addrDisplacement));
                }
            };
        }
    }


    // Note: This LIR operation is similar to a LEA, so reusing the LEA op would be desirable.
    // However, the address that is loaded depends on the stack slot, and the stack slot numbers are
    // only fixed after register allocation when the number of spill slots is known. Therefore, the address
    // is not known when the LIR is generated.
    public enum LeaStackOpcode implements LIROpcode {
        LEA_STACK;

        public LIRInstruction create(Variable result, final CiStackSlot stackBlock) {
            CiValue[] outputs = new CiValue[] {result};

            return new AMD64LIRInstruction(this, outputs, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    masm.leaq(asRegister(output(0)), tasm.asAddress(stackBlock));
                }
            };
        }
    }


    public enum MembarOpcode implements LIROpcode {
        MEMBAR;

        public LIRInstruction create(final int barriers) {
            return new AMD64LIRInstruction(this, LIRInstruction.NO_OPERANDS, null, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
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
        public LIRInstruction create(Variable input, LIRDebugInfo info) {
            CiValue[] inputs = new CiValue[] {input};

            return new AMD64LIRInstruction(this, LIRInstruction.NO_OPERANDS, info, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    tasm.recordImplicitException(masm.codeBuffer.position(), info);
                    masm.nullCheck(asRegister(input(0)));
                }
            };
        }
    }


    public enum CompareAndSwapOpcode implements LIROpcode {
        CAS;

        public LIRInstruction create(CiRegisterValue result, CiValue addrBase, CiValue addrIndex, final CiAddress.Scale addrScale, final int addrDisplacement, CiRegisterValue cmpValue, Variable newValue) {
            CiValue[] inputs = new CiValue[] {addrBase, addrIndex, cmpValue, newValue};
            CiValue[] outputs = new CiValue[] {result};

            return new AMD64LIRInstruction(this, outputs, null, inputs, LIRInstruction.NO_OPERANDS, LIRInstruction.NO_OPERANDS) {
                @Override
                public void emitCode(TargetMethodAssembler tasm, AMD64MacroAssembler masm) {
                    compareAndSwap(tasm, masm, output(0), new CiAddress(CiKind.Illegal, input(0), input(1), addrScale, addrDisplacement), input(2), input(3));
                }
            };
        }
    }


    protected static void move(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        if (isRegister(input)) {
            if (isRegister(result)) {
                reg2reg(masm, result, input);
            } else if (isStackSlot(result)) {
                reg2stack(tasm, masm, result, input);
            } else {
                throw Util.shouldNotReachHere();
            }
        } else if (isStackSlot(input)) {
            if (isRegister(result)) {
                stack2reg(tasm, masm, result, input);
            } else {
                throw Util.shouldNotReachHere();
            }
        } else if (isConstant(input)) {
            if (isRegister(result)) {
                const2reg(tasm, masm, result, (CiConstant) input);
            } else if (isStackSlot(result)) {
                const2stack(tasm, masm, result, (CiConstant) input);
            } else {
                throw Util.shouldNotReachHere();
            }
        } else {
            throw Util.shouldNotReachHere();
        }
    }

    private static void reg2reg(AMD64MacroAssembler masm, CiValue result, CiValue input) {
        if (input.equals(result)) {
            return;
        }
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(asRegister(result),    asRegister(input)); break;
            case Long:   masm.movq(asRegister(result),    asRegister(input)); break;
            case Float:  masm.movflt(asFloatReg(result),  asFloatReg(input)); break;
            case Double: masm.movdbl(asDoubleReg(result), asDoubleReg(input)); break;
            case Object: masm.movq(asRegister(result),    asRegister(input)); break;
            default:     throw Util.shouldNotReachHere("kind=" + result.kind);
        }
    }

    private static void reg2stack(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(tasm.asAddress(result),   asRegister(input)); break;
            case Long:   masm.movq(tasm.asAddress(result),   asRegister(input)); break;
            case Float:  masm.movflt(tasm.asAddress(result), asFloatReg(input)); break;
            case Double: masm.movsd(tasm.asAddress(result),  asDoubleReg(input)); break;
            case Object: masm.movq(tasm.asAddress(result),   asRegister(input)); break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    private static void stack2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiValue input) {
        switch (result.kind) {
            case Jsr:
            case Int:    masm.movl(asRegister(result),    tasm.asAddress(input)); break;
            case Long:   masm.movq(asRegister(result),    tasm.asAddress(input)); break;
            case Float:  masm.movflt(asFloatReg(result),  tasm.asAddress(input)); break;
            case Double: masm.movdbl(asDoubleReg(result), tasm.asAddress(input)); break;
            case Object: masm.movq(asRegister(result),    tasm.asAddress(input)); break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    private static void const2reg(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiValue result, CiConstant c) {
        switch (result.kind) {
            case Jsr:
            case Int:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movl(asRegister(result), tasm.asIntConst(c));
                break;
            case Long:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                masm.movq(asRegister(result), c.asLong());
                break;
            case Float:
                // This is *not* the same as 'constant == 0.0f' in the case where constant is -0.0f
                if (Float.floatToRawIntBits(c.asFloat()) == Float.floatToRawIntBits(0.0f)) {
                    masm.xorps(asFloatReg(result), asFloatReg(result));
                } else {
                    masm.movflt(asFloatReg(result), tasm.asFloatConstRef(c));
                }
                break;
            case Double:
                // This is *not* the same as 'constant == 0.0d' in the case where constant is -0.0d
                if (Double.doubleToRawLongBits(c.asDouble()) == Double.doubleToRawLongBits(0.0d)) {
                    masm.xorpd(asDoubleReg(result), asDoubleReg(result));
                } else {
                    masm.movdbl(asDoubleReg(result), tasm.asDoubleConstRef(c));
                }
                break;
            case Object:
                // Do not optimize with an XOR as this instruction may be between
                // a CMP and a Jcc in which case the XOR will modify the condition
                // flags and interfere with the Jcc.
                if (c.isNull()) {
                    masm.movq(asRegister(result), 0x0L);
                } else if (tasm.target.inlineObjects) {
                    tasm.recordDataReferenceInCode(c, 0);
                    masm.movq(asRegister(result), 0xDEADDEADDEADDEADL);
                } else {
                    masm.movq(asRegister(result), tasm.recordDataReferenceInCode(c, 0));
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
            case Byte:   masm.movsxb(asRegister(result),  loadAddr); break;
            case Char:   masm.movzxl(asRegister(result),  loadAddr); break;
            case Short:  masm.movswl(asRegister(result),  loadAddr); break;
            case Int:    masm.movslq(asRegister(result),  loadAddr); break;
            case Long:   masm.movq(asRegister(result),    loadAddr); break;
            case Float:  masm.movflt(asFloatReg(result),  loadAddr); break;
            case Double: masm.movdbl(asDoubleReg(result), loadAddr); break;
            case Object: masm.movq(asRegister(result),    loadAddr); break;
            default:     throw Util.shouldNotReachHere();
        }
    }

    protected static void store(TargetMethodAssembler tasm, AMD64MacroAssembler masm, CiAddress storeAddr, CiValue input, CiKind kind, LIRDebugInfo info) {
        if (info != null) {
            tasm.recordImplicitException(masm.codeBuffer.position(), info);
        }

        if (isRegister(input)) {
            switch (kind) {
                case Boolean:
                case Byte:   masm.movb(storeAddr,   asRegister(input)); break;
                case Char:
                case Short:  masm.movw(storeAddr,   asRegister(input)); break;
                case Int:    masm.movl(storeAddr,   asRegister(input)); break;
                case Long:   masm.movq(storeAddr,   asRegister(input)); break;
                case Float:  masm.movflt(storeAddr, asFloatReg(input)); break;
                case Double: masm.movsd(storeAddr,  asDoubleReg(input)); break;
                case Object: masm.movq(storeAddr,   asRegister(input)); break;
                default:     throw Util.shouldNotReachHere("kind=" + kind);
            }
        } else if (isConstant(input)) {
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
        assert asRegister(cmpValue) == AMD64.rax && asRegister(result) == AMD64.rax;

        if (tasm.target.isMP) {
            masm.lock();
        }
        switch (cmpValue.kind) {
            case Int:    masm.cmpxchgl(asRegister(newValue), address); break;
            case Long:
            case Object: masm.cmpxchgq(asRegister(newValue), address); break;
            default:     throw Util.shouldNotReachHere();
        }
    }
}
