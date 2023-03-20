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
import org.graalvm.wasm.parser.bytecode.BytecodeGen;

/**
 * Representation of the offsets of the debug sections in the Wasm binary file.
 */
@SuppressWarnings("unused")
public class DebugUtil {
    public static final String ABBREV_NAME = ".debug_abbrev";
    public static final String ARANGES_NAME = ".debug_aranges";
    public static final String FRAME_NAME = ".debug_frame";
    public static final String INFO_NAME = ".debug_info";
    public static final String LINE_NAME = ".debug_line";
    public static final String LOC_NAME = ".debug_loc";
    public static final String MAC_INFO_NAME = ".debug_macinfo";
    public static final String PUBNAMES_NAME = ".debug_pubnames";
    public static final String PUBTYPES_NAME = ".debug_pubtypes";
    public static final String RANGES_NAME = ".debug_ranges";
    public static final String STR_NAME = ".debug_str";
    public static final String TYPES_NAME = ".debug_types";

    public static final int CUSTOM_DATA_SIZE = 56;

    private static final int ABBREV_OFFSET = 0;
    private static final int ARANGES_OFFSET = 4;
    private static final int FRAME_OFFSET = 8;
    private static final int INFO_OFFSET = 12;
    private static final int INFO_LENGTH_OFFSET = 16;
    private static final int LINE_OFFSET = 20;
    private static final int LOC_OFFSET = 24;
    private static final int MAC_INFO_OFFSET = 28;
    private static final int PUBNAMES_OFFSET = 32;
    private static final int PUBTYPES_OFFSET = 36;
    private static final int RANGES_OFFSET = 40;
    private static final int STR_OFFSET = 44;
    private static final int TYPES_OFFSET = 48;
    private static final int FUNCTION_DATA_OFFSET = 52;

    public static void setAbbrevOffset(BytecodeGen customData, int debugInfoOffset, int abbrevOffset) {
        customData.set(debugInfoOffset + ABBREV_OFFSET, abbrevOffset);
    }

    public static int abbrevOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + ABBREV_OFFSET);
    }

    public static void setArangesOffset(BytecodeGen customData, int debugInfoOffset, int arangesOffset) {
        customData.set(debugInfoOffset + ARANGES_OFFSET, arangesOffset);
    }

    public static int arangesOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + ARANGES_OFFSET);
    }

    public static void setFrameOffset(BytecodeGen customData, int debugInfoOffset, int frameOffset) {
        customData.set(debugInfoOffset + FRAME_OFFSET, frameOffset);
    }

    public static int frameOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + FRAME_OFFSET);
    }

    public static void setInfo(BytecodeGen customData, int debugInfoOffset, int offset, int length) {
        customData.set(debugInfoOffset + INFO_OFFSET, offset);
        customData.set(debugInfoOffset + INFO_LENGTH_OFFSET, length);
    }

    public static int infoOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + INFO_OFFSET);
    }

    public static int infoLength(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + INFO_LENGTH_OFFSET);
    }

    public static void setLineOffset(BytecodeGen customData, int debugInfoOffset, int lineOffset) {
        customData.set(debugInfoOffset + LINE_OFFSET, lineOffset);
    }

    public static int lineOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + LINE_OFFSET);
    }

    public static void setLocOffset(BytecodeGen customData, int debugInfoOffset, int locOffset) {
        customData.set(debugInfoOffset + LOC_OFFSET, locOffset);
    }

    public static int locOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + LOC_OFFSET);
    }

    public static void setMacInfoOffset(BytecodeGen customData, int debugInfoOffset, int macInfoOffset) {
        customData.set(debugInfoOffset + MAC_INFO_OFFSET, macInfoOffset);
    }

    public static int macInfoOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + MAC_INFO_OFFSET);
    }

    public static void setPubnamesOffset(BytecodeGen customData, int debugInfoOffset, int pubnamesOffset) {
        customData.set(debugInfoOffset + PUBNAMES_OFFSET, pubnamesOffset);
    }

    public static int pubnamesOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + PUBNAMES_OFFSET);
    }

    public static void setPubtypesOffset(BytecodeGen customData, int debugInfoOffset, int pubtypesOffset) {
        customData.set(debugInfoOffset + PUBTYPES_OFFSET, pubtypesOffset);
    }

    public static int pubtypesOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + PUBTYPES_OFFSET);
    }

    public static void setRangesOffset(BytecodeGen customDataGen, int debugInfoOffset, int rangesOffset) {
        customDataGen.set(debugInfoOffset + RANGES_OFFSET, rangesOffset);
    }

    public static int rangesOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + RANGES_OFFSET);
    }

    public static void setStrOffset(BytecodeGen customData, int debugInfoOffset, int strOffset) {
        customData.set(debugInfoOffset + STR_OFFSET, strOffset);
    }

    public static int strOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + STR_OFFSET);
    }

    public static void setTypesOffset(BytecodeGen customData, int debugInfoOffset, int typesOffset) {
        customData.set(debugInfoOffset + TYPES_OFFSET, typesOffset);
    }

    public static int typesOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + TYPES_OFFSET);
    }

    public static void setFunctionDataOffset(BytecodeGen customData, int debugInfoOffset, int functionTableOffset) {
        customData.set(debugInfoOffset + FUNCTION_DATA_OFFSET, functionTableOffset);
    }

    public static int functionDataOffset(byte[] customData, int debugInfoOffset) {
        return BinaryStreamParser.rawPeekI32(customData, debugInfoOffset + FUNCTION_DATA_OFFSET);
    }
}
