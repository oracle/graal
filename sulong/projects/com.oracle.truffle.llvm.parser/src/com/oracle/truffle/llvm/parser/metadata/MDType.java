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
package com.oracle.truffle.llvm.parser.metadata;

public abstract class MDType extends MDName {

    private final DwarfTag tag;

    private final long size;
    private final long align;
    private final long offset;
    private final long line;
    private final long flags;

    private MDBaseNode file;

    MDType(long tag, long size, long align, long offset, long line, long flags) {
        this.tag = DwarfTag.decode(tag);
        this.size = size;
        this.align = align;
        this.offset = offset;
        this.line = line;
        this.flags = flags;
        this.file = MDVoidNode.INSTANCE;
    }

    public long getSize() {
        return size;
    }

    public long getAlign() {
        return align;
    }

    public long getOffset() {
        return offset;
    }

    public MDBaseNode getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public long getFlags() {
        return flags;
    }

    public DwarfTag getTag() {
        return tag;
    }

    public void setFile(MDBaseNode file) {
        this.file = file;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (file == oldValue) {
            file = newValue;
        }
    }

    // @see: https://github.com/llvm-mirror/llvm/blob/master/include/llvm/BinaryFormat/Dwarf.def
    public enum DwarfTag {
        UNKNOWN(-1),

        DW_TAG_ARRAY_TYPE(0x1),          // MDCompositeType
        DW_TAG_CLASS_TYPE(0x2),          // MDCompositeType
        DW_TAG_ENUMERATION_TYPE(0x4),    // MDCompositeType
        DW_TAG_FORMAL_PARAMETER(0x5),
        DW_TAG_MEMBER(0xD),              // MDDerivedType
        DW_TAG_POINTER_TYPE(0xF),        // MDDerivedType
        DW_TAG_REFERENCE_TYPE(0x10),     // MDDerivedType
        DW_TAG_STRUCTURE_TYPE(0x13),     // MDCompositeType
        DW_TAG_SUBROUTINE_TYPE(0x15),
        DW_TAG_TYPEDEF(0x16),            // MDDerivedType
        DW_TAG_UNION_TYPE(0x17),         // MDCompositeType
        DW_TAG_INHERITANCE(0x1C),        // MDDerivedType
        DW_TAG_PTR_TO_MEMBER_TYPE(0x1F), // MDDerivedType
        DW_TAG_BASE_TYPE(0x24),          // MDBasicType
        DW_TAG_CONST_TYPE(0x26),         // MDDerivedType
        DW_TAG_FRIEND(0x2A),             // MDDerivedType
        DW_TAG_VOLATILE_TYPE(0x35),      // MDDerivedType

        // DWARF v3
        DW_TAG_RESTRICT_TYPE(0x37),      // MDDerivedType
        DW_TAG_UNSPECIFIED_TYPE(0x3B),   // MDBasicType

        // DWARF v5
        DW_TAG_ATOMIC_TYPE(0x47),        // MDDerivedType

        // removed tags
        DW_TAG_VECTOR_TYPE(0x103);       // MDCompositeType

        private static final DwarfTag[] VALUES = values();

        private final int id;

        DwarfTag(int id) {
            this.id = id;
        }

        static DwarfTag decode(long val) {
            for (DwarfTag tag : VALUES) {
                if (tag.id == val) {
                    return tag;
                }
            }
            return UNKNOWN;
        }
    }
}
