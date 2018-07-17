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

import com.oracle.truffle.llvm.parser.listeners.Metadata;
import com.oracle.truffle.llvm.parser.records.DwTagRecord;

public final class MDBasicType extends MDType implements MDBaseNode {
    // http://llvm.org/releases/3.2/docs/SourceLevelDebugging.html#format_basic_type

    private final DwarfEncoding encoding;

    private MDBasicType(long tag, long line, long size, long align, long offset, long flags, long encoding) {
        super(tag, size, align, offset, line, flags);
        this.encoding = DwarfEncoding.decode(encoding);
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public DwarfEncoding getEncoding() {
        return encoding;
    }

    public enum DwarfEncoding {

        UNKNOWN(-1),
        DW_ATE_ADDRESS(1),
        DW_ATE_BOOLEAN(2),
        DW_ATE_FLOAT(4),
        DW_ATE_SIGNED(5),
        DW_ATE_SIGNED_CHAR(6),
        DW_ATE_UNSIGNED(7),
        DW_ATE_UNSIGNED_CHAR(8);

        private static final DwarfEncoding[] VALUES = values();

        private final int id;

        DwarfEncoding(int id) {
            this.id = id;
        }

        private static DwarfEncoding decode(long val) {
            for (DwarfEncoding encoding : VALUES) {
                if (encoding.id == val) {
                    return encoding;
                }
            }
            return UNKNOWN;
        }
    }

    private static final int ARGINDEX_TAG = 1;
    private static final int ARGINDEX_NAME = 2;
    private static final int ARGINDEX_SIZE = 3;
    private static final int ARGINDEX_ALIGN = 4;
    private static final int ARGINDEX_ENCODING = 5;

    public static MDBasicType create38(long[] args, MetadataValueList md) {
        // [distinct, tag, name, size, align, enc]
        final long tag = args[ARGINDEX_TAG];
        final long size = args[ARGINDEX_SIZE];
        final long align = args[ARGINDEX_ALIGN];
        final long encoding = args[ARGINDEX_ENCODING];

        final MDBasicType basicType = new MDBasicType(tag, -1L, size, align, -1L, -1L, encoding);
        basicType.setName(md.getNullable(args[ARGINDEX_NAME], basicType));
        return basicType;
    }

    private static final int ARGINDEX_32_NAME = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_LINE = 4;
    private static final int ARGINDEX_32_SIZE = 5;
    private static final int ARGINDEX_32_ALIGN = 6;
    private static final int ARGINDEX_32_OFFSET = 7;
    private static final int ARGINDEX_32_FLAGS = 8;
    private static final int ARGINDEX_32_ENCODING = 9;

    public static MDBasicType create32(long[] args, Metadata md) {
        // final MDReference scope = metadata.getReference(args[1]);
        final long line = ParseUtil.asInt(args, ARGINDEX_32_LINE, md);
        final long size = ParseUtil.asLong(args, ARGINDEX_32_SIZE, md);
        final long align = ParseUtil.asLong(args, ARGINDEX_32_ALIGN, md);
        final long offset = ParseUtil.asLong(args, ARGINDEX_32_OFFSET, md);
        final long flags = ParseUtil.asInt(args, ARGINDEX_32_FLAGS, md);
        final long encoding = ParseUtil.asInt(args, ARGINDEX_32_ENCODING, md);

        final MDBasicType basicType = new MDBasicType(DwTagRecord.DW_TAG_BASE_TYPE.code(), line, size, align, offset, flags, encoding);
        basicType.setName(ParseUtil.resolveReference(args, ARGINDEX_32_NAME, basicType, md));
        basicType.setFile(ParseUtil.resolveReference(args, ARGINDEX_32_FILE, basicType, md));
        return basicType;
    }
}
