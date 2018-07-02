/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;

class AsmRegisterOperand implements AsmOperand {
    private String register;

    private static final Map<String, String> mapping;
    private static final Map<String, Type> width;
    private static final Map<String, Integer> shift;
    private static final Set<String> registers;

    private static final int REG16_HI_SHIFT = 8;
    private static final int REG64_COUNT = 16;

    static {
        mapping = new HashMap<>();
        mapping.put("ah", "r0");
        mapping.put("al", "r0");
        mapping.put("ax", "r0");
        mapping.put("eax", "r0");
        mapping.put("rax", "r0");
        mapping.put("r0d", "r0");
        mapping.put("r0w", "r0");
        mapping.put("r0l", "r0");
        mapping.put("r0", "r0");

        mapping.put("ch", "r1");
        mapping.put("cl", "r1");
        mapping.put("cx", "r1");
        mapping.put("ecx", "r1");
        mapping.put("rcx", "r1");
        mapping.put("r1d", "r1");
        mapping.put("r1w", "r1");
        mapping.put("r1l", "r1");
        mapping.put("r1", "r1");

        mapping.put("dh", "r2");
        mapping.put("dl", "r2");
        mapping.put("dx", "r2");
        mapping.put("edx", "r2");
        mapping.put("rdx", "r2");
        mapping.put("r2d", "r2");
        mapping.put("r2w", "r2");
        mapping.put("r2l", "r2");
        mapping.put("r2", "r2");

        mapping.put("bh", "r3");
        mapping.put("bl", "r3");
        mapping.put("bx", "r3");
        mapping.put("ebx", "r3");
        mapping.put("rbx", "r3");
        mapping.put("r3d", "r3");
        mapping.put("r3w", "r3");
        mapping.put("r3l", "r3");
        mapping.put("r3", "r3");

        mapping.put("sp", "r4");
        mapping.put("esp", "r4");
        mapping.put("rsp", "r4");
        mapping.put("r4d", "r4");
        mapping.put("r4w", "r4");
        mapping.put("r4l", "r4");
        mapping.put("r4", "r4");

        mapping.put("bp", "r5");
        mapping.put("ebp", "r5");
        mapping.put("rbp", "r5");
        mapping.put("r5d", "r5");
        mapping.put("r5w", "r5");
        mapping.put("r5l", "r5");
        mapping.put("r5", "r5");

        mapping.put("si", "r6");
        mapping.put("esi", "r6");
        mapping.put("rsi", "r6");
        mapping.put("r6d", "r6");
        mapping.put("r6w", "r6");
        mapping.put("r6l", "r6");
        mapping.put("r6", "r6");

        mapping.put("di", "r7");
        mapping.put("edi", "r7");
        mapping.put("rdi", "r7");
        mapping.put("r7d", "r7");
        mapping.put("r7w", "r7");
        mapping.put("r7l", "r7");
        mapping.put("r7", "r7");

        mapping.put("r8d", "r8");
        mapping.put("r8w", "r8");
        mapping.put("r8l", "r8");
        mapping.put("r8", "r8");

        mapping.put("r9d", "r9");
        mapping.put("r9w", "r9");
        mapping.put("r9l", "r9");
        mapping.put("r9", "r9");

        mapping.put("r10d", "r10");
        mapping.put("r10w", "r10");
        mapping.put("r10l", "r10");
        mapping.put("r10", "r10");

        mapping.put("r11d", "r11");
        mapping.put("r11w", "r11");
        mapping.put("r11l", "r11");
        mapping.put("r11", "r11");

        mapping.put("r12d", "r12");
        mapping.put("r12w", "r12");
        mapping.put("r12l", "r12");
        mapping.put("r12", "r12");

        mapping.put("r13d", "r13");
        mapping.put("r13w", "r13");
        mapping.put("r13l", "r13");
        mapping.put("r13", "r13");

        mapping.put("r14d", "r14");
        mapping.put("r14w", "r14");
        mapping.put("r14l", "r14");
        mapping.put("r14", "r14");

        mapping.put("r15d", "r15");
        mapping.put("r15w", "r15");
        mapping.put("r15l", "r15");
        mapping.put("r15", "r15");

        width = new HashMap<>();
        width.put("ah", PrimitiveType.I8);
        width.put("al", PrimitiveType.I8);
        width.put("ax", PrimitiveType.I16);
        width.put("eax", PrimitiveType.I32);
        width.put("rax", PrimitiveType.I64);
        width.put("r0d", PrimitiveType.I32);
        width.put("r0w", PrimitiveType.I16);
        width.put("r0l", PrimitiveType.I8);
        width.put("r0", PrimitiveType.I64);

        width.put("ch", PrimitiveType.I8);
        width.put("cl", PrimitiveType.I8);
        width.put("cx", PrimitiveType.I16);
        width.put("ecx", PrimitiveType.I32);
        width.put("rcx", PrimitiveType.I64);
        width.put("r1d", PrimitiveType.I32);
        width.put("r1w", PrimitiveType.I16);
        width.put("r1l", PrimitiveType.I8);
        width.put("r1", PrimitiveType.I64);

        width.put("dh", PrimitiveType.I8);
        width.put("dl", PrimitiveType.I8);
        width.put("dx", PrimitiveType.I16);
        width.put("edx", PrimitiveType.I32);
        width.put("rdx", PrimitiveType.I64);
        width.put("r2d", PrimitiveType.I32);
        width.put("r2w", PrimitiveType.I16);
        width.put("r2l", PrimitiveType.I8);
        width.put("r2", PrimitiveType.I64);

        width.put("bh", PrimitiveType.I8);
        width.put("bl", PrimitiveType.I8);
        width.put("bx", PrimitiveType.I16);
        width.put("ebx", PrimitiveType.I32);
        width.put("rbx", PrimitiveType.I64);
        width.put("r3d", PrimitiveType.I32);
        width.put("r3w", PrimitiveType.I16);
        width.put("r3l", PrimitiveType.I8);
        width.put("r3", PrimitiveType.I64);

        width.put("sp", PrimitiveType.I16);
        width.put("esp", PrimitiveType.I32);
        width.put("rsp", PrimitiveType.I64);
        width.put("r4d", PrimitiveType.I32);
        width.put("r4w", PrimitiveType.I16);
        width.put("r4l", PrimitiveType.I8);
        width.put("r4", PrimitiveType.I64);

        width.put("bp", PrimitiveType.I16);
        width.put("ebp", PrimitiveType.I32);
        width.put("rbp", PrimitiveType.I64);
        width.put("r5d", PrimitiveType.I32);
        width.put("r5w", PrimitiveType.I16);
        width.put("r5l", PrimitiveType.I8);
        width.put("r5", PrimitiveType.I64);

        width.put("si", PrimitiveType.I16);
        width.put("esi", PrimitiveType.I32);
        width.put("rsi", PrimitiveType.I64);
        width.put("r6d", PrimitiveType.I32);
        width.put("r6w", PrimitiveType.I16);
        width.put("r6l", PrimitiveType.I8);
        width.put("r6", PrimitiveType.I64);

        width.put("di", PrimitiveType.I16);
        width.put("edi", PrimitiveType.I32);
        width.put("rdi", PrimitiveType.I64);
        width.put("r7d", PrimitiveType.I32);
        width.put("r7w", PrimitiveType.I16);
        width.put("r7l", PrimitiveType.I8);
        width.put("r7", PrimitiveType.I64);

        width.put("r8d", PrimitiveType.I32);
        width.put("r8w", PrimitiveType.I16);
        width.put("r8l", PrimitiveType.I8);
        width.put("r8", PrimitiveType.I64);

        width.put("r9d", PrimitiveType.I32);
        width.put("r9w", PrimitiveType.I16);
        width.put("r9l", PrimitiveType.I8);
        width.put("r9", PrimitiveType.I64);

        width.put("r10d", PrimitiveType.I32);
        width.put("r10w", PrimitiveType.I16);
        width.put("r10l", PrimitiveType.I8);
        width.put("r10", PrimitiveType.I64);

        width.put("r11d", PrimitiveType.I32);
        width.put("r11w", PrimitiveType.I16);
        width.put("r11l", PrimitiveType.I8);
        width.put("r11", PrimitiveType.I64);

        width.put("r12d", PrimitiveType.I32);
        width.put("r12w", PrimitiveType.I16);
        width.put("r12l", PrimitiveType.I8);
        width.put("r12", PrimitiveType.I64);

        width.put("r13d", PrimitiveType.I32);
        width.put("r13w", PrimitiveType.I16);
        width.put("r13l", PrimitiveType.I8);
        width.put("r13", PrimitiveType.I64);

        width.put("r14d", PrimitiveType.I32);
        width.put("r14w", PrimitiveType.I16);
        width.put("r14l", PrimitiveType.I8);
        width.put("r14", PrimitiveType.I64);

        width.put("r15d", PrimitiveType.I32);
        width.put("r15w", PrimitiveType.I16);
        width.put("r15l", PrimitiveType.I8);
        width.put("r15", PrimitiveType.I64);

        shift = new HashMap<>();
        shift.put("ah", REG16_HI_SHIFT);
        shift.put("ch", REG16_HI_SHIFT);
        shift.put("dh", REG16_HI_SHIFT);
        shift.put("bh", REG16_HI_SHIFT);

        registers = new HashSet<>();
        for (int i = 0; i < REG64_COUNT; i++) {
            registers.add("r" + i);
        }
    }

    AsmRegisterOperand(String register) {
        this.register = register.charAt(0) == '%' ? register.substring(1) : register;
    }

    public String getRegister() {
        return register;
    }

    public String getBaseRegister() {
        return getBaseRegister(getRegister());
    }

    @Override
    public Type getType() {
        return getWidth(getRegister());
    }

    public int getShift() {
        return getShift(getRegister());
    }

    static boolean isRegister(String reg) {
        return mapping.containsKey(reg);
    }

    static String getBaseRegister(String reg) {
        return mapping.get(reg);
    }

    private static Type getWidth(String reg) {
        return width.get(reg);
    }

    private static int getShift(String reg) {
        Integer sh = shift.get(reg);
        return sh == null ? 0 : sh;
    }

    @Override
    public String toString() {
        return "%" + getRegister();
    }
}
