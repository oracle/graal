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
 * Tags byte format as defined by the Dwarf Debugging Information Format version 4.
 */

@SuppressWarnings("unused")
public final class Tags {
    public static final int ARRAY_TYPE = 0x01;
    public static final int CLASS_TYPE = 0x02;
    public static final int ENUMERATION_TYPE = 0x04;
    public static final int FORMAL_PARAMETER = 0x05;
    public static final int IMPORTED_DECLARATION = 0x08;
    public static final int LEXICAL_BLOCK = 0x0B;
    public static final int MEMBER = 0x0D;
    public static final int POINTER_TYPE = 0x0f;
    public static final int REFERENCE_TYPE = 0x10;
    public static final int COMPILATION_UNIT = 0x11;
    public static final int STRUCTURE_TYPE = 0x13;
    public static final int SUBROUTINE_TYPE = 0x15;
    public static final int TYPEDEF = 0x16;
    public static final int UNION_TYPE = 0x17;
    public static final int UNSPECIFIED_PARAMETERS = 0x18;
    public static final int VARIANT = 0x19;
    public static final int INHERITANCE = 0x1C;
    public static final int PTR_TO_MEMBER_TYPE = 0x1F;
    public static final int SUBRANGE_TYPE = 0x21;
    public static final int ACCESS_DECLARATION = 0x23;
    public static final int BASE_TYPE = 0x24;
    public static final int CONST_TYPE = 0x26;
    public static final int ENUMERATOR = 0x28;
    public static final int SUBPROGRAM = 0x2E;
    public static final int VARIANT_PART = 0x33;
    public static final int VARIABLE = 0x34;
    public static final int VOLATILE_TYPE = 0x35;
    public static final int RESTRICT_TYPE = 0x37;
    public static final int NAMESPACE = 0x39;
    public static final int IMPORTED_MODULE = 0x3A;
    public static final int UNSPECIFIED_TYPE = 0x3B;
    public static final int RVALUE_REFERENCE_TYPE = 0x42;

    private Tags() {
    }
}
