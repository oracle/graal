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
package com.oracle.truffle.llvm.runtime.types;

/*
 * source: https://github.com/llvm-mirror/llvm/blob/release_32/include/llvm/Support/Dwarf.h
 */
public enum DwLangNameRecord {

    DW_LANG_UNKNOWN(-1),

    // Language names
    DW_LANG_C89(0x0001),
    DW_LANG_C(0x0002),
    DW_LANG_ADA83(0x0003),
    DW_LANG_C_PLUS_PLUS(0x0004),
    DW_LANG_COBOL74(0x0005),
    DW_LANG_COBOL85(0x0006),
    DW_LANG_FORTRAN77(0x0007),
    DW_LANG_FORTRAN90(0x0008),
    DW_LANG_PASCAL83(0x0009),
    DW_LANG_MODULA2(0x000A),
    DW_LANG_JAVA(0x000B),
    DW_LANG_C99(0x000C),
    DW_LANG_ADA95(0x000D),
    DW_LANG_FORTRAN95(0x000E),
    DW_LANG_PLI(0x000F),
    DW_LANG_OBJC(0x0010),
    DW_LANG_OJC_PLUS_PLUS(0x0011),
    DW_LANG_UPC(0x0012),
    DW_LANG_D(0x0013),
    DW_LANG_PYTHON(0x0014),
    DW_LANG_LO_USER(0x8000), // lower boundary for user defined tags
    DW_LANG_MIPS_ASSEMBLER(0x8001),
    DW_LANG_HI_USER(0xFFFF); // upper boundary for user defined tags

    private static final DwLangNameRecord[] VALUES = values();

    public static DwLangNameRecord decode(long code) {
        for (DwLangNameRecord cc : VALUES) {
            if (cc.code() == code) {
                return cc;
            }
        }
        return DW_LANG_UNKNOWN;
    }

    private final int code;

    DwLangNameRecord(int code) {
        this.code = code;
    }

    public long code() {
        return code;
    }
}
