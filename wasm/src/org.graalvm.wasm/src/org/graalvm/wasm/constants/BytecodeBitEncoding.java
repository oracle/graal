/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.wasm.constants;

/**
 * Representation of bit encoded bytecode values.
 */
public class BytecodeBitEncoding {
    // Labels

    public static final int LABEL_U8_RESULT_MASK = 0b1100_0000;
    public static final int LABEL_U8_RESULT_NUM = 0b1000_0000;
    public static final int LABEL_U8_RESULT_OBJ = 0b1100_0000;
    public static final int LABEL_U8_STACK_VALUE = 0b0011_1111;

    public static final int LABEL_U16_RESULT_TYPE_MASK = 0b1100_0000;
    public static final int LABEL_U16_RESULT_TYPE_NUM = 0b0100_0000;
    public static final int LABEL_U16_RESULT_TYPE_OBJ = 0b1000_0000;
    public static final int LABEL_U16_RESULT_TYPE_MIX = 0b1100_0000;
    public static final int LABEL_U16_RESULT_TYPE_SHIFT = 6;
    public static final int LABEL_U16_RESULT_VALUE = 0b0011_1111;

    public static final int LABEL_RESULT_TYPE_NUM = 0b0000_0001;
    public static final int LABEL_RESULT_TYPE_OBJ = 0b0000_0010;
    public static final int LABEL_RESULT_TYPE_MIX = 0b0000_0011;

    public static final int MEMORY_64_FLAG = 0b1000_0000;
    public static final int MEMORY_INDEX_MASK = 0b0110_0000;
    public static final int MEMORY_INDEX_ZERO = 0b0000_0000;
    public static final int MEMORY_INDEX_U8 = 0b0010_0000;
    public static final int MEMORY_INDEX_U16 = 0b0100_0000;
    public static final int MEMORY_INDEX_I32 = 0b0110_0000;
    public static final int MEMORY_OFFSET_MASK = 0b0000_1111;
    public static final int MEMORY_OFFSET_U8 = 0b0000_0001;
    public static final int MEMORY_OFFSET_U32 = 0b0000_0100;
    public static final int MEMORY_OFFSET_I64 = 0b0000_1000;

    // Data sections

    // byte 1
    public static final int DATA_SEG_LENGTH_MASK = 0b1100_0000;
    public static final int DATA_SEG_LENGTH_U8 = 0b0100_0000;
    public static final int DATA_SEG_LENGTH_U16 = 0b1000_0000;
    public static final int DATA_SEG_LENGTH_I32 = 0b1100_0000;

    public static final int DATA_SEG_HAS_MEMORY_INDEX_ZERO = 0b0010_0000;

    public static final int DATA_SEG_BYTECODE_OR_OFFSET_MASK = 0b0001_0000;
    public static final int DATA_SEG_BYTECODE = 0b0000_0000;
    public static final int DATA_SEG_OFFSET = 0b0001_0000;

    public static final int DATA_SEG_VALUE_MASK = 0b0000_1110;
    public static final int DATA_SEG_VALUE_UNDEFINED = 0b0000_0000;
    public static final int DATA_SEG_VALUE_U8 = 0b0000_0010;
    public static final int DATA_SEG_VALUE_U16 = 0b0000_0100;
    public static final int DATA_SEG_VALUE_U32 = 0b0000_0110;
    public static final int DATA_SEG_VALUE_I64 = 0b0000_1000;

    public static final int DATA_SEG_MODE_VALUE = 0b0000_0001;

    // byte 2
    public static final int DATA_SEG_MEMORY_INDEX_MASK = 0b1100_0000;
    public static final int DATA_SEG_MEMORY_INDEX_U6 = 0b0000_0000;
    public static final int DATA_SEG_MEMORY_INDEX_U8 = 0b0100_0000;
    public static final int DATA_SEG_MEMORY_INDEX_U16 = 0b1000_0000;
    public static final int DATA_SEG_MEMORY_INDEX_I32 = 0b1100_0000;
    public static final int DATA_SEG_MEMORY_INDEX_VALUE = 0b0011_1111;

    // Runtime data sections
    public static final int DATA_SEG_RUNTIME_HEADER_LENGTH = 1;
    public static final int DATA_SEG_RUNTIME_LENGTH_MASK = 0b1100_0000;
    public static final int DATA_SEG_RUNTIME_LENGTH_INLINE = 0b0000_0000;
    public static final int DATA_SEG_RUNTIME_LENGTH_U8 = 0b0100_0000;
    public static final int DATA_SEG_RUNTIME_LENGTH_U16 = 0b1000_0000;
    public static final int DATA_SEG_RUNTIME_LENGTH_I32 = 0b1100_0000;
    public static final int DATA_SEG_RUNTIME_UNSAFE_ADDRESS_LENGTH = 8;

    // Elem sections

    public static final int ELEM_SEG_COUNT_MASK = 0b1100_0000;
    public static final int ELEM_SEG_COUNT_U8 = 0b0100_0000;
    public static final int ELEM_SEG_COUNT_U16 = 0b1000_0000;
    public static final int ELEM_SEG_COUNT_I32 = 0b1100_0000;

    public static final int ELEM_SEG_TABLE_INDEX_MASK = 0b0011_0000;
    public static final int ELEM_SEG_TABLE_INDEX_ZERO = 0b0000_0000;
    public static final int ELEM_SEG_TABLE_INDEX_U8 = 0b0001_0000;
    public static final int ELEM_SEG_TABLE_INDEX_U16 = 0b0010_0000;
    public static final int ELEM_SEG_TABLE_INDEX_I32 = 0b0011_0000;

    public static final int ELEM_SEG_OFFSET_BYTECODE_MASK = 0b0000_1100;
    public static final int ELEM_SEG_OFFSET_BYTECODE_UNDEFINED = 0b0000_0000;
    public static final int ELEM_SEG_OFFSET_BYTECODE_LENGTH_U8 = 0b0000_0100;
    public static final int ELEM_SEG_OFFSET_BYTECODE_LENGTH_U16 = 0b0000_1000;
    public static final int ELEM_SEG_OFFSET_BYTECODE_LENGTH_I32 = 0b0000_1100;

    public static final int ELEM_SEG_OFFSET_ADDRESS_MASK = 0b0000_0011;
    public static final int ELEM_SEG_OFFSET_ADDRESS_UNDEFINED = 0b0000_0000;
    public static final int ELEM_SEG_OFFSET_ADDRESS_U8 = 0b0000_0001;
    public static final int ELEM_SEG_OFFSET_ADDRESS_U16 = 0b0000_0010;
    public static final int ELEM_SEG_OFFSET_ADDRESS_I32 = 0b0000_0011;

    public static final int ELEM_SEG_TYPE_FUNREF = 0b0001_0000;
    public static final int ELEM_SEG_TYPE_EXTERNREF = 0b0010_0000;

    public static final int ELEM_SEG_MODE_VALUE = 0b0000_1111;

    // Elem items

    public static final int ELEM_ITEM_TYPE_MASK = 0b1000_0000;
    public static final int ELEM_ITEM_TYPE_FUNCTION_INDEX = 0b0000_0000;
    public static final int ELEM_ITEM_TYPE_GLOBAL_INDEX = 0b1000_0000;

    public static final int ELEM_ITEM_LENGTH_MASK = 0b0110_0000;
    public static final int ELEM_ITEM_LENGTH_INLINE = 0b0000_0000;
    public static final int ELEM_ITEM_LENGTH_U8 = 0b0010_0000;
    public static final int ELEM_ITEM_LENGTH_U16 = 0b0100_0000;
    public static final int ELEM_ITEM_LENGTH_I32 = 0b0110_0000;

    public static final int ELEM_ITEM_NULL_FLAG = 0b0001_0000;

    public static final int ELEM_ITEM_INLINE_VALUE = 0b0000_1111;

    // Code entries

    public static final int CODE_ENTRY_FUNCTION_INDEX_MASK = 0b1100_0000;
    public static final int CODE_ENTRY_FUNCTION_INDEX_ZERO = 0b0000_0000;
    public static final int CODE_ENTRY_FUNCTION_INDEX_U8 = 0b0100_0000;
    public static final int CODE_ENTRY_FUNCTION_INDEX_U16 = 0b1000_0000;
    public static final int CODE_ENTRY_FUNCTION_INDEX_I32 = 0b1100_0000;

    public static final int CODE_ENTRY_MAX_STACK_SIZE_MASK = 0b0011_0000;
    public static final int CODE_ENTRY_MAX_STACK_SIZE_ZERO = 0b0000_0000;
    public static final int CODE_ENTRY_MAX_STACK_SIZE_U8 = 0b0001_0000;
    public static final int CODE_ENTRY_MAX_STACK_SIZE_U16 = 0b0010_0000;
    public static final int CODE_ENTRY_MAX_STACK_SIZE_I32 = 0b0011_0000;

    public static final int CODE_ENTRY_LENGTH_MASK = 0b0000_1100;
    public static final int CODE_ENTRY_LENGTH_U8 = 0b0000_0100;
    public static final int CODE_ENTRY_LENGTH_U16 = 0b0000_1000;
    public static final int CODE_ENTRY_LENGTH_I32 = 0b0000_1100;

    public static final int CODE_ENTRY_LOCALS_FLAG = 0b0000_0010;
    public static final int CODE_ENTRY_RESULT_FLAG = 0b0000_0001;
}
