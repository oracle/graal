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
 * Attribute Encodings byte format as defined by the Dwarf Debugging Information Format version 4.
 */
@SuppressWarnings("unused")
public final class AttributeEncodings {
    public static final int ADDRESS = 0x01;
    public static final int BOOLEAN = 0x02;
    public static final int COMPLEX_FLOAT = 0x03;
    public static final int FLOAT = 0x04;
    public static final int SIGNED = 0x05;
    public static final int SIGNED_CHAR = 0x06;
    public static final int UNSIGNED = 0x07;
    public static final int UNSIGNED_CHAR = 0x08;
    public static final int IMAGINARY_FLOAT = 0x09;
    public static final int PACKED_DECIMAL = 0x0A;
    public static final int NUMERIC_STRING = 0x0B;
    public static final int EDITED = 0x0C;
    public static final int SIGNED_FIXED = 0x0D;
    public static final int UNSIGNED_FIXED = 0x0E;
    public static final int DECIMAL_FLOAT = 0x0F;
    public static final int UTF = 0x10;

    private AttributeEncodings() {
    }
}
