/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.wasm.debugging.encoding;

/**
 * Attributes byte format as defined by the Dwarf Debugging Information Format version 4.
 */
@SuppressWarnings("unused")
public final class Attributes {
    public static final int SIBLING = 0x01;
    public static final int LOCATION = 0x02;
    public static final int NAME = 0x03;
    public static final int ORDERING = 0x09;
    public static final int BYTE_SIZE = 0x0b;
    public static final int BIT_OFFSET = 0x0c;
    public static final int BIT_SIZE = 0x0d;
    public static final int STMT_LIST = 0x10;
    public static final int LOW_PC = 0x11;
    public static final int HIGH_PC = 0x12;
    public static final int LANGUAGE = 0x13;
    public static final int DISCR = 0x15;
    public static final int DISCR_VALUE = 0x16;
    public static final int VISIBILITY = 0x17;
    public static final int IMPORT = 0x18;
    public static final int STRING_LENGTH = 0x19;
    public static final int COMMON_REFERENCE = 0x1A;
    public static final int COMP_DIR = 0x1B;
    public static final int CONST_VALUE = 0x1C;
    public static final int CONTAINING_TYPE = 0x1D;
    public static final int DEFAULT_VALUE = 0x1E;
    public static final int INLINE = 0x20;
    public static final int IS_OPTIONAL = 0x21;
    public static final int LOWER_BOUND = 0x22;
    public static final int PRODUCER = 0x25;
    public static final int PROTOTYPED = 0x27;
    public static final int RETURN_ADDR = 0x2A;
    public static final int START_SCOPE = 0x2C;
    public static final int BIT_STRIDE = 0x2E;
    public static final int UPPER_BOUND = 0x2F;
    public static final int ABSTRACT_ORIGIN = 0x31;
    public static final int ACCESSIBILITY = 0x32;
    public static final int ADDRESS_CLASS = 0x33;
    public static final int ARTIFICIAL = 0x34;
    public static final int BASE_TYPE = 0x35;
    public static final int CALLING_CONVENTION = 0x36;
    public static final int COUNT = 0x37;
    public static final int DATA_MEMBER_LOCATION = 0x38;
    public static final int DECL_COLUMN = 0x39;
    public static final int DECL_FILE = 0x3A;
    public static final int DECL_LINE = 0x3B;
    public static final int DECLARATION = 0x3C;
    public static final int DISCR_LIST = 0x3D;
    public static final int ENCODING = 0x3E;
    public static final int EXTERNAL = 0x3F;
    public static final int FRAME_BASE = 0x40;
    public static final int FRIEND = 0x41;
    public static final int IDENTIFIER_CASE = 0x42;
    public static final int MACRO_INFO = 0x43;
    public static final int NAMELIST_INFO = 0x44;
    public static final int PRIORITY = 0x45;
    public static final int SEGMENT = 0x46;
    public static final int SPECIFICATION = 0x47;
    public static final int STATIC_LINK = 0x48;
    public static final int TYPE = 0x49;
    public static final int USE_LOCATION = 0x4A;
    public static final int VARIABLE_PARAMETER = 0x4B;
    public static final int VIRTUALITY = 0x4C;
    public static final int VTABLE_ELEM_LOCATION = 0x4D;
    public static final int ALLOCATED = 0x4E;
    public static final int ASSOCIATED = 0x4F;
    public static final int DATA_LOCATION = 0x50;
    public static final int BYTE_STRIDE = 0x51;
    public static final int ENTRY_PC = 0x52;
    public static final int USE_UTF8 = 0x53;
    public static final int EXTENSION = 0x54;
    public static final int RANGES = 0x55;
    public static final int TRAMPOLINE = 0x56;
    public static final int CALL_COLUMN = 0x57;
    public static final int CALL_FILE = 0x58;
    public static final int CALL_LINE = 0x59;
    public static final int DESCRIPTION = 0x5A;
    public static final int BINARY_SCALE = 0x5B;
    public static final int DECIMAL_SCALE = 0x5C;
    public static final int SMALL = 0x5D;
    public static final int DECIMAL_SIGN = 0x5E;
    public static final int DIGIT_COUNT = 0x5F;
    public static final int PICTURE_STRING = 0x60;
    public static final int MUTABLE = 0x61;
    public static final int THREADS_SCALED = 0x62;
    public static final int EXPLICIT = 0x63;
    public static final int OBJECT_POINTER = 0x64;
    public static final int ENDIANITY = 0x65;
    public static final int ELEMENTAL = 0x66;
    public static final int PURE = 0x67;
    public static final int RECURSIVE = 0x68;
    public static final int SIGNATURE = 0x69;
    public static final int MAIN_SUBPROGRAM = 0x6A;
    public static final int DATA_BIT_OFFSET = 0x6B;
    public static final int CONST_EXPR = 0x6C;
    public static final int ENUM_CLASS = 0x6D;
    public static final int LINKAGE_NAME = 0x6E;

    private Attributes() {
    }
}
