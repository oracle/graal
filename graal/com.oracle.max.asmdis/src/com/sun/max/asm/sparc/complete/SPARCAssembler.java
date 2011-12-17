/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.sparc.complete;

import static com.sun.max.asm.sparc.GPR.*;

import com.sun.max.asm.*;
import com.sun.max.asm.sparc.*;
import com.sun.max.lang.*;
import com.sun.max.program.*;

/**
 * The base class for the 32-bit and 64-bit SPARC assemblers. This class also defines
 * the more complex synthetic SPARC instructions.
 */
public abstract class SPARCAssembler extends SPARCLabelAssembler {

    public static SPARCAssembler createAssembler(WordWidth wordWidth) {
        switch (wordWidth) {
            case BITS_32:
                return new SPARC32Assembler();
            case BITS_64:
                return new SPARC64Assembler();
            default:
                throw ProgramError.unexpected("Invalid word width specification");
        }
    }

    // Utilities:

    public static int hi(int i) {
        return (i & 0xfffffc00) >> 10;
    }

    public static int lo(int i) {
        return i & 0x000003ff;
    }

    public static int hi(long i) {
        return hi((int) i);
    }

    public static int lo(long i) {
        return lo((int) i);
    }

    public static int uhi(long i) {
        return hi((int) (i >> 32));
    }

    public static int ulo(long i) {
        return lo((int) (i >> 32));
    }

    public static boolean isSimm11(int value) {
        return Ints.numberOfEffectiveSignedBits(value) <= 11;
    }

    public static boolean isSimm13(int value) {
        return Ints.numberOfEffectiveSignedBits(value) <= 13;
    }

    public static boolean isSimm13(long value) {
        return Longs.numberOfEffectiveSignedBits(value) <= 13;
    }

    public static boolean isSimm19(int value) {
        return Ints.numberOfEffectiveSignedBits(value) <= 19;
    }

    public static int setswNumberOfInstructions(int imm) {
        if (0 <= imm && lo(imm) == 0) {
            return 1;
        } else if (-4096 <= imm && imm <= 4095) {
            return 1;
        } else if (imm >= 0 || lo(imm) == 0) {
            return 2;
        } else {
            return 3;
        }
    }

    public static int setuwNumberOfInstructions(int imm) {
        if (lo(imm) == 0) {
            return 1;
        } else if (0 <= imm && imm <= 4095) {
            return 1;
        } else {
            return 2;
        }
    }

    @Override
    protected void emitPadding(int numberOfBytes) throws AssemblyException {
        if ((numberOfBytes & 0x3) != 0) {
            throw new AssemblyException("Cannot pad instruction stream with a number of bytes not divisble by 4");
        }
        for (int i = 0; i < numberOfBytes >> 2; i++) {
            nop();
        }
    }

    // Complex synthetic instructions according to appendix G3 of the SPARC Architecture Manual V9:

    public void setuw(int imm, GPR rd) {
        if (lo(imm) == 0) {
            sethi(hi(imm), rd);
        } else if (0 <= imm && imm <= 4095) {
            or(G0, imm, rd);
        } else {
            sethi(hi(imm), rd);
            or(rd, lo(imm), rd);
        }
    }

    public void set(int imm, GPR rd) {
        setuw(imm, rd);
    }

    public void setsw(int imm, GPR rd) {
        if (0 <= imm && lo(imm) == 0) {
            sethi(hi(imm), rd);
        } else if (-4096 <= imm && imm <= 4095) {
            or(G0, imm, rd);
        } else if (imm < 0 && lo(imm) == 0) {
            sethi(hi(imm), rd);
            sra(rd, G0, rd);
        } else if (imm >= 0) {
            sethi(hi(imm), rd);
            or(rd, lo(imm), rd);
        } else {
            sethi(hi(imm), rd);
            or(rd, lo(imm), rd);
            sra(rd, G0, rd);
        }
    }

    public void setx(long imm, GPR temp, GPR rd) {
        sethi(uhi(imm), temp);
        or(temp, ulo(imm), temp);
        sllx(temp, 32, temp);
        sethi(hi(imm), rd);
        or(rd, temp, rd);
        or(rd, lo(imm), rd);
    }

    public void sethi(Label label, final GPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithAddress(this, startPosition, startPosition + 4, label) {
            @Override
            protected void assemble() throws AssemblyException {
                final int imm22 = hi(addressAsInt());
                sethi(imm22, rd);
            }
        };
    }

    public void add(final GPR rs1, final Label label, final GPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithAddress(this, startPosition, startPosition + 4, label) {
            @Override
            protected void assemble() throws AssemblyException {
                final int simm13 = lo(addressAsInt());
                add(rs1, simm13, rd);
            }
        };
    }

    public void ld(final GPR rs1, final Label base, final Label target, final SFPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithOffset(this, startPosition, startPosition + 4, target) {
            @Override
            protected void assemble() throws AssemblyException {
                ld(rs1, labelOffsetRelative(target, base), rd);
            }
        };
    }

    public void ldd(final GPR rs1, final Label base, final Label target, final DFPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithOffset(this, startPosition, startPosition + 4, target) {
            @Override
            protected void assemble() throws AssemblyException {
                ldd(rs1, labelOffsetRelative(target, base), rd);
            }
        };
    }

    public void ldx(final GPR rs1, final Label base, final Label target, final GPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithOffset(this, startPosition, startPosition + 4, target) {
            @Override
            protected void assemble() throws AssemblyException {
                ldx(rs1, labelOffsetRelative(target, base), rd);
            }
        };
    }

    public void add(final GPR rs1, final Label base, final Label target, final GPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithOffset(this, startPosition, startPosition + 4, target) {
            @Override
            protected void assemble() throws AssemblyException {
                add(rs1, labelOffsetRelative(target, base), rd);
            }
        };
    }
    public void addcc(final GPR rs1, final Label base, final Label target, final GPR rd) {
        final int startPosition = currentPosition();
        emitInt(0);
        new InstructionWithOffset(this, startPosition, startPosition + 4, target) {
            @Override
            protected void assemble() throws AssemblyException {
                addcc(rs1, labelOffsetRelative(target, base), rd);
            }
        };
    }
}
