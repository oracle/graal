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
package com.oracle.truffle.llvm.parser.records;

/*
 * source: https://github.com/llvm-mirror/llvm/blob/release_32/include/llvm/Support/Dwarf.h
 */
public enum DwTagRecord {

    DW_TAG_UNKNOWN(-1),

    // Tags
    DW_TAG_ARRAY_TYPE(0x01),
    DW_TAG_CLASS_TYPE(0x02),
    DW_TAG_ENTRY_POINT(0x03),
    DW_TAG_ENUMERATION_TYPE(0x04),
    DW_TAG_FORMAL_PARAMETER(0x05),
    DW_TAG_IMPORTED_DECLARATION(0x08),
    DW_TAG_LABEL(0x0A),
    DW_TAG_LEXICAL_BLOCK(0x0B),
    DW_TAG_MEMBER(0x0D),
    DW_TAG_POINTER_TYPE(0x0F),
    DW_TAG_REFERENCE_TYPE(0x10),
    DW_TAG_COMPILE_UNIT(0x11),
    DW_TAG_STRING_TYPE(0x12),
    DW_TAG_STRUCTURE_TYPE(0x13),
    DW_TAG_SUBROUTINE_TYPE(0x15),
    DW_TAG_TYPEDEF(0x16),
    DW_TAG_UNION_TYPE(0x17),
    DW_TAG_UNSPECIFIED_PARAMETERS(0x18),
    DW_TAG_VARIANT(0x19),
    DW_TAG_COMMON_BLOCK(0x1A),
    DW_TAG_COMMON_INCLUSION(0x1B),
    DW_TAG_INHERITANCE(0x1C),
    DW_TAG_INLINE_SUBROUTINE(0x1D),
    DW_TAG_MODULE(0x1E),
    DW_TAG_PTR_TO_MEMBER_TYPE(0x1F),
    DW_TAG_SET_TYPE(0x20),
    DW_TAG_SUBRANGE_TYPE(0x21),
    DW_TAG_WITH_STMT(0x22),
    DW_TAG_ACCESS_DECLARATION(0x23),
    DW_TAG_BASE_TYPE(0x24),
    DW_TAG_CATCH_BLOCK(0x25),
    DW_TAG_CONST_TYPE(0x26),
    DW_TAG_CONSTANT(0x27),
    DW_TAG_ENUMERATOR(0x28),
    DW_TAG_FILE_TYPE(0x29),
    DW_TAG_FRIEND(0x2A),
    DW_TAG_NAMELIST(0x2B),
    DW_TAG_NAMELIST_ITEM(0x2C),
    DW_TAG_PACKED_TYPE(0x2D),
    DW_TAG_SUBPROGRAM(0x2E),
    DW_TAG_TEMPLATE_TYPE_PARAMETER(0x2F),
    DW_TAG_TEMPLATE_VALUE_PARAMETER(0x30),
    DW_TAG_THROWN_TYPE(0x31),
    DW_TAG_TRY_BLOCK(0x32),
    DW_TAG_VARIANT_PART(0x33),
    DW_TAG_VARIABLE(0x34),
    DW_TAG_VOLATILE_TYPE(0x35),
    DW_TAG_DWARF_PROCEDURE(0x36),
    DW_TAG_RESTRICT_TYPE(0x37),
    DW_TAG_INTERFACE_TYPE(0x38),
    DW_TAG_NAMESPACE(0x39),
    DW_TAG_IMPORTED_MODULE(0x3A),
    DW_TAG_CONDITION(0x3F),
    DW_TAG_SHARED_TYPE(0x40),
    DW_TAG_TYPE_UNIT(0x41),
    DW_TAG_RVALUE_REFERENCE_TYPE(0x42),
    DW_TAG_TEMPLATE_ALIAS(0x43),
    DW_TAG_LO_USER(0x4080), // lower boundary for user defined tags
    DW_TAG_MIPS_LOOP(0x4081),
    DW_TAG_FORMAT_LABEL(0x4101),
    DW_TAG_FUNCTION_TEMPLATE(0x4102),
    DW_TAG_CLASS_TEMPLATE(0x4103),
    DW_TAG_GNU_TEMPLATE_TEMPLATE_PARAM(0x4106),
    DW_TAG_GNU_TEMPLATE_PARAMETER_PACK(0x4107),
    DW_TAG_GNU_FORMAL_PARAMETER_PACK(0x4108),
    DW_TAG_APPLE_PROPERTY(0x4200),
    DW_TAG_HI_USER(0xFFFF), // upper boundary for user defined tags

    // Dwarf constants
    DW_TAG_AUTO_VARIABLE(0x100),
    DW_TAG_ARG_VARIABLE(0x101),
    DW_TAG_RETURN_VARIABLE(0x102),
    DW_TAG_VECTOR_TYPE(0x103);

    private static final DwTagRecord[] VALUES = values();

    private static final long DWARF_CONSTANT_VALUE_MASK = 0x0000FFFF;
    private static final long DWARF_CONSTANT_VERSION_MASK = 0xFFFF0000;

    public static long getDwarfConstant(long code) {
        return code & DWARF_CONSTANT_VALUE_MASK;
    }

    public static DwTagRecord decode(long code) {
        long codePart = getDwarfConstant(code);
        for (DwTagRecord cc : VALUES) {
            if (cc.code() == codePart) {
                return cc;
            }
        }
        return DW_TAG_UNKNOWN;
    }

    public static boolean isDwarfDescriptor(long val) {
        if (val <= 0 || (val & DWARF_CONSTANT_VERSION_MASK) == 0) {
            return false;
        }
        final DwTagRecord decoded = decode(val);
        switch (decoded) {
            case DW_TAG_UNKNOWN:
            case DW_TAG_HI_USER:
            case DW_TAG_LO_USER:
                return false;
            default:
                return true;
        }
    }

    private final int code;

    DwTagRecord(int code) {
        this.code = code;
    }

    public long code() {
        return code;
    }
}
