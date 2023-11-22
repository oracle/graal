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

package org.graalvm.wasm.debugging.parser;

import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.parser.bytecode.BytecodeGen;

/**
 * Representation of the offsets of the debug sections in the Wasm binary file.
 */
@SuppressWarnings("unused")
public class DebugUtil {
    public static final String ABBREV_NAME = ".debug_abbrev";
    public static final String INFO_NAME = ".debug_info";
    public static final String LINE_NAME = ".debug_line";
    public static final String LOC_NAME = ".debug_loc";
    public static final String RANGES_NAME = ".debug_ranges";
    public static final String STR_NAME = ".debug_str";

    public static final int CUSTOM_DATA_SIZE = 48;

    private static final int ABBREV_OFFSET = 0;
    private static final int ABBREV_LENGTH_OFFSET = 4;
    private static final int INFO_OFFSET = 8;
    private static final int INFO_LENGTH_OFFSET = 12;
    private static final int LINE_OFFSET = 16;
    private static final int LINE_LENGTH_OFFSET = 20;
    private static final int LOC_OFFSET = 24;
    private static final int LOC_LENGTH_OFFSET = 28;
    private static final int RANGES_OFFSET = 32;
    private static final int RANGES_LENGTH_OFFSET = 36;
    private static final int STR_OFFSET = 40;
    private static final int STR_LENGTH_OFFSET = 44;

    public static final int DEFAULT_I32 = -1;
    public static final long DEFAULT_I64 = -1L;
    public static final int UNDEFINED = -1;

    public static void initializeData(BytecodeGen customData, int debugInfoOffset) {
        customData.set(debugInfoOffset + ABBREV_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + ABBREV_LENGTH_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + INFO_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + INFO_LENGTH_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + LINE_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + LINE_LENGTH_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + LOC_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + LOC_LENGTH_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + RANGES_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + RANGES_LENGTH_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + STR_OFFSET, UNDEFINED);
        customData.set(debugInfoOffset + STR_LENGTH_OFFSET, UNDEFINED);
    }

    public static void setAbbrevOffset(BytecodeGen customData, int debugInfoOffset, int offset, int length) {
        customData.set(debugInfoOffset + ABBREV_OFFSET, offset);
        customData.set(debugInfoOffset + ABBREV_LENGTH_OFFSET, length);
    }

    public static int getAbbrevOffsetOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + ABBREV_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static int getAbbrevLengthOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + ABBREV_LENGTH_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static void setInfo(BytecodeGen customData, int debugInfoOffset, int offset, int length) {
        customData.set(debugInfoOffset + INFO_OFFSET, offset);
        customData.set(debugInfoOffset + INFO_LENGTH_OFFSET, length);
    }

    public static int getInfoOffsetOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + INFO_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static int getInfoLengthOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + INFO_LENGTH_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static void setLineOffset(BytecodeGen customData, int debugInfoOffset, int offset, int length) {
        customData.set(debugInfoOffset + LINE_OFFSET, offset);
        customData.set(debugInfoOffset + LINE_LENGTH_OFFSET, length);
    }

    public static int getLineOffsetOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + LINE_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static int getLineLengthOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + LINE_LENGTH_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static void setLocOffset(BytecodeGen customData, int debugInfoOffset, int offset, int length) {
        customData.set(debugInfoOffset + LOC_OFFSET, offset);
        customData.set(debugInfoOffset + LOC_LENGTH_OFFSET, length);
    }

    public static int getLocOffsetOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + LOC_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static int getLocLengthOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + LOC_LENGTH_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static void setRangesOffset(BytecodeGen customDataGen, int debugInfoOffset, int offset, int length) {
        customDataGen.set(debugInfoOffset + RANGES_OFFSET, offset);
        customDataGen.set(debugInfoOffset + RANGES_LENGTH_OFFSET, length);
    }

    public static int getRangesOffsetOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + RANGES_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static int getRangesLengthOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + RANGES_LENGTH_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static void setStrOffset(BytecodeGen customData, int debugInfoOffset, int offset, int length) {
        customData.set(debugInfoOffset + STR_OFFSET, offset);
        customData.set(debugInfoOffset + STR_LENGTH_OFFSET, length);
    }

    public static int getStrOffsetOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + STR_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }

    public static int getStrLengthOrUndefined(byte[] customData, int debugInfoOffset) {
        try {
            return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + STR_LENGTH_OFFSET);
        } catch (WasmException e) {
            return UNDEFINED;
        }
    }
}
