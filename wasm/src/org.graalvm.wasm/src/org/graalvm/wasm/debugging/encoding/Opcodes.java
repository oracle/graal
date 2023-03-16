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
 * Opcodes byte format as defined by the Dwarf Debugging Information Format version 4.
 */
public final class Opcodes {
    public static final int EXTENDED_OPCODE = 0x00;
    public static final int LNS_COPY = 0x01;
    public static final int LNS_ADVANCE_PC = 0x02;
    public static final int LNS_ADVANCE_LINE = 0x03;
    public static final int LNS_SET_FILE = 0x04;
    public static final int LNS_SET_COLUMN = 0x05;
    public static final int LNS_NEGATE_STMT = 0x06;
    public static final int LNS_SET_BASIC_BLOCK = 0x07;
    public static final int LNS_CONST_ADD_PC = 0x08;
    public static final int LNS_FIXED_ADVANCE_PC = 0x09;
    public static final int LNS_SET_PROLOGUE_END = 0x0a;
    public static final int LNS_SET_EPILOGUE_BEGIN = 0x0b;
    public static final int LNS_SET_ISA = 0x0c;

    public static final int LNE_END_SEQUENCE = 0x01;
    public static final int LNE_SET_ADDRESS = 0x02;
    public static final int LNE_DEFINE_FILE = 0x03;
    public static final int LNE_SET_DISCRIMINATOR = 0x04;

    public static final byte ADDR = (byte) 0x03;
    public static final byte DEREF = (byte) 0x06;
    public static final byte FBREG = (byte) 0x91;
    public static final byte WASM_LOCATION = (byte) 0xED;
    public static final byte STACK_VALUE = (byte) 0x9F;

    private Opcodes() {
    }
}
