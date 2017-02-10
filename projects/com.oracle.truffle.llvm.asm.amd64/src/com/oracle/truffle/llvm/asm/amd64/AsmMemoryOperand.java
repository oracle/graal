/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.asm.amd64;

class AsmMemoryOperand implements AsmOperand {
    public static final int HEX = 16;
    public static final int SCALE_1 = 1;
    public static final int SCALE_2 = 2;
    public static final int SCALE_4 = 4;

    private final String displacement;
    private final AsmOperand base;
    private final AsmOperand offset;
    private final int scale;

    AsmMemoryOperand(String displacement, AsmOperand base, AsmOperand offset, int scale) {
        this.displacement = displacement;
        this.base = base;
        this.offset = offset;
        this.scale = scale;
    }

    public int getDisplacement() {
        if (displacement.startsWith("0x")) {
            return Integer.parseInt(displacement.substring(2), HEX);
        } else {
            return Integer.parseInt(displacement);
        }
    }

    public AsmOperand getBase() {
        return base;
    }

    public AsmOperand getOffset() {
        return offset;
    }

    public int getScale() {
        return scale;
    }

    public int getShift() {
        switch (scale) {
            case SCALE_1:
                return 0;
            case SCALE_2:
                return 1;
            case SCALE_4:
                return 2;
            default:
                throw new AsmParseException("invalid scale: " + scale);
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append(displacement);
        if (base != null || offset != null) {
            b.append("(");
            if (base != null) {
                b.append(base);
            }
            if (offset != null) {
                b.append(",");
                b.append(offset);
                if (scale != 1) {
                    b.append(",");
                    b.append(scale);
                }
            }
            b.append(")");
        }
        return b.toString();
    }
}
