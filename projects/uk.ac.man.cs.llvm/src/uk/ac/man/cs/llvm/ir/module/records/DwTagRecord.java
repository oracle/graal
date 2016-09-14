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
package uk.ac.man.cs.llvm.ir.module.records;

public enum DwTagRecord {

    DW_TAG_UNKNOWN(-1),

    DW_TAG_ARRAY_TYPE(1),
    DW_TAG_ENUMERATION_TYPE(4),
    DW_TAG_FORMAL_PARAMETER(5),
    DW_TAG_LEXICAL_BLOCK(11),
    DW_TAG_MEMBER(13),
    DW_TAG_POINTER_TYPE(15),
    DW_TAG_REFERENCE_TYPE(16),
    DW_TAG_COMPILE_UNIT(17),
    DW_TAG_STRUCTURE_TYPE(19),
    DW_TAG_SUBROUTINE_TYPE(21),
    DW_TAG_TYPEDEF(22),
    DW_TAG_UNION_TYPE(23),
    DW_TAG_INHERITANCE(28),
    DW_TAG_PTR_TO_MEMBER_TYPE(31),
    DW_TAG_SUBRANGE_TYPE(33),
    DW_TAG_BASIC_TYPE(36),
    DW_TAG_CONST_TYPE(38),
    DW_TAG_ENUMERATOR(40),
    DW_TAG_FILE_TYPE(41),
    DW_TAG_SUBPROGRAM(46),
    DW_TAG_GLOBAL_VARIABLE(52),
    DW_TAG_VOLATILE_TYPE(53),
    DW_TAG_RESTRICTED_TYPE(55),
    DW_TAG_AUTO_VARIABLE(256),
    DW_TAG_ARG_VARIABLE(257),
    DW_TAG_RETURN_VARIABLE(258),
    DW_TAG_VECTOR_TYPE(259);

    private static final long DW_TAG_DECODE_LOWER_BYTES = 0x0000FFFF;

    public static DwTagRecord decode(long code) {
        long codePart = code & DW_TAG_DECODE_LOWER_BYTES;
        for (DwTagRecord cc : values()) {
            if (cc.code() == codePart) {
                return cc;
            }
        }
        return DW_TAG_UNKNOWN;
    }

    private final int code;

    DwTagRecord(int code) {
        this.code = code;
    }

    public long code() {
        return code;
    }
}
