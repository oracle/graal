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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.wasm.BinaryStreamParser;
import org.graalvm.wasm.collection.IntArrayList;
import org.graalvm.wasm.debugging.DebugLineMap;
import org.graalvm.wasm.debugging.DebugLocation;
import org.graalvm.wasm.debugging.data.DebugAddressSize;
import org.graalvm.wasm.debugging.encoding.DataEncoding;
import org.graalvm.wasm.debugging.encoding.Opcodes;
import org.graalvm.wasm.debugging.encoding.Tags;
import org.graalvm.wasm.nodes.WasmDataAccess;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;

/**
 * Represents a parser for the DWARF Debug Information Format.
 */
public class DebugParser {

    private final byte[] data;
    private int offset;

    public DebugParser(byte[] data) {
        this.data = data;
    }

    @TruffleBoundary
    public DebugParseUnit readEntries(int debugInfoOffset, int unitOffset) {
        offset = DebugUtil.infoOffset(data, debugInfoOffset);
        int endOffset = offset + DebugUtil.infoLength(data, debugInfoOffset);
        if (unitOffset != 0) {
            offset = unitOffset;
        }

        if (offset < endOffset) {
            int unitStartOffset = offset - DebugUtil.infoOffset(data, debugInfoOffset);

            // read header
            if (is64Bit()) {
                return null;
            }

            final int unitLength = readInitialLength();
            if (unitLength == -1) {
                // 64 bit length
                return null;
            }
            int unitEndOffset = offset + unitLength;
            final int version = readUnsigned2();
            if (version != 4) {
                // Unsupported version
                return null;
            }

            final int debugAbbrevOffset = read4();
            // read address size
            read1();

            final EconomicMap<Integer, AbbreviationDeclaration> abbreviationTable = readAbbrevSection(DebugUtil.abbrevOffset(data, debugInfoOffset) + debugAbbrevOffset);
            final EconomicMap<Integer, DebugData> entries = EconomicMap.create();
            final DebugData compilationUnit = readDebugEntry(abbreviationTable, debugInfoOffset, unitStartOffset, unitEndOffset, entries, true);
            return new DebugParseUnit(compilationUnit, entries);
        }
        return null;
    }

    @TruffleBoundary
    public int getNextCompilationUnitOffset(int debugInfoOffset, int unitOffset) {
        offset = DebugUtil.infoOffset(data, debugInfoOffset);
        int endOffset = offset + DebugUtil.infoLength(data, debugInfoOffset);
        if (unitOffset != 0) {
            offset = unitOffset;
        }

        if (offset < endOffset) {
            if (is64Bit()) {
                return -1;
            }
            int unitLength = readInitialLength();
            return offset + unitLength;
        }
        return -1;
    }

    @TruffleBoundary
    public DebugParseUnit readCompilationUnit(int debugInfoOffset, int unitOffset) {
        offset = DebugUtil.infoOffset(data, debugInfoOffset);
        int endOffset = offset + DebugUtil.infoLength(data, debugInfoOffset);
        if (unitOffset != 0) {
            offset = unitOffset;
        }

        if (offset < endOffset) {
            int unitStartOffset = offset;

            // read header
            if (is64Bit()) {
                return null;
            }

            final int unitLength = readInitialLength();
            if (unitLength == -1) {
                // 64 bit length
                return null;
            }
            final int version = read2();
            if (version != 4) {
                // Unsupported version
                return null;
            }

            final int debugAbbrevOffset = read4();
            // read address size
            read1();

            final EconomicMap<Integer, AbbreviationDeclaration> abbreviationTable = readAbbrevSection(DebugUtil.abbrevOffset(data, debugInfoOffset) + debugAbbrevOffset);
            final EconomicMap<Integer, DebugData> entries = EconomicMap.create();
            final DebugData compilationUnit = readDebugEntry(abbreviationTable, debugInfoOffset, unitStartOffset, endOffset, entries, false);
            if (compilationUnit != null && compilationUnit.tag() == Tags.COMPILATION_UNIT) {
                return new DebugParseUnit(compilationUnit, entries);
            } else {
                return null;
            }
        }
        return null;
    }

    private EconomicMap<Integer, AbbreviationDeclaration> readAbbrevSection(int abbrevOffset) {
        final int currentOffset = this.offset;
        this.offset = abbrevOffset;
        final EconomicMap<Integer, AbbreviationDeclaration> table = EconomicMap.create();
        for (;;) {
            if (peekUnsignedInt() == 0) {
                readUnsignedInt();
                break;
            }
            AbbreviationDeclaration d = readAbbrevEntry();
            table.put(d.index(), d);
        }
        this.offset = currentOffset;
        return table;
    }

    private AbbreviationDeclaration readAbbrevEntry() {
        int index = readUnsignedInt();
        int tag = readUnsignedInt();
        boolean children = read1() != 0;
        AbbreviationDeclaration entry = new AbbreviationDeclaration(index, tag, children);
        for (;;) {
            int attributeName = readUnsignedInt();
            int attributeForm = readUnsignedInt();
            if (attributeName == 0 && attributeForm == 0) {
                break;
            }
            entry.addAttribute(attributeName, attributeForm);
        }
        return entry;
    }

    private DebugData readDebugEntry(EconomicMap<Integer, AbbreviationDeclaration> abbrevTable, int debugInfoOffset, int compilationUnitOffset, int unitEndOffset,
                    EconomicMap<Integer, DebugData> entries, boolean followChildren) {
        final int startOffset = offset;
        final int abbrevDeclarationIndex = readUnsignedInt();
        final AbbreviationDeclaration declaration = abbrevTable.get(abbrevDeclarationIndex);
        if (declaration == null) {
            // Malformed abbreviation table. Declaration not found
            return null;
        }
        final int entryOffset = startOffset - DebugUtil.infoOffset(data, debugInfoOffset) - compilationUnitOffset;
        final long[] attributeInfo = new long[declaration.attributeCount()];
        final Object[] attributes = new Object[declaration.attributeCount()];
        // read attributes
        for (int i = 0; i != declaration.attributeCount(); ++i) {
            int attribute = declaration.attribute(i);
            int attributeEncoding = DataEncoding.fromForm(declaration.attributeForm(i));
            Object value = null;
            if (DataEncoding.isNumber(attributeEncoding)) {
                if (DataEncoding.isLen1(attributeEncoding)) {
                    value = read1();
                } else if (DataEncoding.isLen2(attributeEncoding)) {
                    value = read2();
                } else if (DataEncoding.isLen4(attributeEncoding)) {
                    value = read4();
                } else if (DataEncoding.isLen8(attributeEncoding)) {
                    value = read8();
                } else if (DataEncoding.isLeb128Signed(attributeEncoding)) {
                    int length = BinaryStreamParser.peekLeb128Length(data, offset);
                    if (length > 4) {
                        offset += length;
                        value = 0;
                    } else {
                        value = readInt();
                    }
                } else if (DataEncoding.isLeb128Unsigned(attributeEncoding)) {
                    int length = BinaryStreamParser.peekLeb128Length(data, offset);
                    if (length > 4) {
                        offset += length;
                        value = 0;
                    } else {
                        value = readUnsignedInt();
                    }
                }
            } else if (DataEncoding.isBoolean(attributeEncoding)) {
                if (DataEncoding.isLen1(attributeEncoding)) {
                    value = read1() != 0;
                } else if (DataEncoding.isFlag(attributeEncoding)) {
                    value = true;
                }
            } else if (DataEncoding.isString(attributeEncoding)) {
                if (DataEncoding.isLen4(attributeEncoding)) {
                    int stringOffset = read4();
                    value = readString(DebugUtil.strOffset(data, debugInfoOffset) + stringOffset);
                } else {
                    value = readString();
                }
            } else if (DataEncoding.isByteArray(attributeEncoding)) {
                int length = 0;
                if (DataEncoding.isLen1(attributeEncoding)) {
                    length = read1();
                }
                if (DataEncoding.isLen2(attributeEncoding)) {
                    length = read2();
                }
                if (DataEncoding.isLen4(attributeEncoding)) {
                    length = read4();
                }
                if (DataEncoding.isLeb128Unsigned(attributeEncoding)) {
                    length = readUnsignedInt();
                }
                final byte[] blockData = new byte[length];
                for (int blockOffset = 0; blockOffset != length; ++blockOffset) {
                    blockData[blockOffset] = read1();
                }
                value = blockData;
            }
            attributeInfo[i] = (long) attributeEncoding << 32 | attribute;
            attributes[i] = value;
        }
        List<DebugData> children = new ArrayList<>();
        if (declaration.hasChildren() && followChildren) {

            while (offset < unitEndOffset) {
                if (peekUnsignedInt() == 0) {
                    readUnsignedInt();
                    break;
                }
                final DebugData child = readDebugEntry(abbrevTable, debugInfoOffset, compilationUnitOffset, unitEndOffset, entries, true);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        final DebugData entry = new DebugData(declaration.tag(), entryOffset, attributeInfo, attributes, children.toArray(new DebugData[0]));
        entries.put(entryOffset, entry);
        return entry;
    }

    @TruffleBoundary
    public static DebugLocation readFrameBaseExpression(byte[] expressionData, MaterializedFrame frame, WasmDataAccess dataAccess, DebugAddressSize addressSize) {
        return readExpression(expressionData, frame, dataAccess, null, null, addressSize);
    }

    @TruffleBoundary
    public static DebugLocation readExpression(byte[] expressionData, DebugLocation location) {
        return readExpression(expressionData, location.frame(), location.dataAccess(), location.frameBase(), location, location.addressSize());
    }

    private static DebugLocation readExpression(byte[] expressionData, MaterializedFrame frame, WasmDataAccess dataAccess, DebugLocation frameBase, DebugLocation baseLocation,
                    DebugAddressSize addressSize) {
        Deque<DebugLocation> valueStack = new ArrayDeque<>();
        if (baseLocation != null) {
            valueStack.push(baseLocation);
        }
        int exprOffset = 0;
        while (exprOffset < expressionData.length) {
            byte opcode = expressionData[exprOffset];
            exprOffset++;
            switch (opcode) {
                case Opcodes.WASM_LOCATION:
                    byte type = expressionData[exprOffset];
                    exprOffset++;
                    switch (type) {
                        case 0x00: {
                            // local
                            long valueAndLength = peekUnsignedInt(expressionData, exprOffset);
                            int value = BinaryStreamParser.value(valueAndLength);
                            valueStack.push(DebugLocation.createLocalAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += BinaryStreamParser.length(valueAndLength);
                            break;
                        }
                        case 0x01: {
                            // global
                            long valueAndLength = peekUnsignedInt(expressionData, exprOffset);
                            int value = BinaryStreamParser.value(valueAndLength);
                            valueStack.push(DebugLocation.createGlobalAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += BinaryStreamParser.length(valueAndLength);
                            break;
                        }
                        case 0x02: {
                            // stack value
                            long valueAndLength = peekUnsignedInt(expressionData, exprOffset);
                            int value = BinaryStreamParser.value(valueAndLength);
                            valueStack.push(DebugLocation.createStackAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += BinaryStreamParser.length(valueAndLength);
                            break;
                        }
                        case 0x03:
                            // global
                            int value = peek4(expressionData, exprOffset);
                            valueStack.push(DebugLocation.createGlobalAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += 4;
                            break;
                    }
                    break;
                case Opcodes.STACK_VALUE:
                    return valueStack.pop();
                case Opcodes.FBREG:
                    long offsetValueAndLength = peekInt(expressionData, exprOffset);
                    int offsetValue = BinaryStreamParser.value(offsetValueAndLength);
                    valueStack.push(frameBase.addOffset(offsetValue));
                    exprOffset += BinaryStreamParser.length(offsetValueAndLength);
                    break;
                case Opcodes.ADDR:
                    int value = peek4(expressionData, exprOffset);
                    exprOffset += 4;
                    valueStack.push(DebugLocation.createMemoryAccess(value, frame, dataAccess, frameBase, addressSize));
                    break;
                case Opcodes.DEREF:
                    final DebugLocation loc = valueStack.pop();
                    valueStack.push(loc.loadAsLocation());
                    break;
            }
        }
        return valueStack.pop();
    }

    /**
     * Reads a string at the given offset in the string section of the debug information.
     */
    private String readString(int stringOffset) {
        final int currentOffset = this.offset;
        this.offset = stringOffset;
        String s = readString();
        this.offset = currentOffset;
        return s;
    }

    @TruffleBoundary
    public DebugLineMap[] readLineSection(int debugLineOffset, String compilationPath, String compilationUnitName) {
        final int currentOffset = this.offset;
        this.offset = debugLineOffset;
        DebugState state = readLineSectionHeader(compilationPath, compilationUnitName);
        int sectionLength = debugLineOffset + state.length();
        while (offset < sectionLength) {
            int opcode = readUnsigned1();
            switch (opcode) {
                case Opcodes.EXTENDED_OPCODE:
                    int length = readUnsignedInt();
                    if (length == 0) {
                        break;
                    }
                    int extendedOpcode = readUnsigned1();
                    switch (extendedOpcode) {
                        case Opcodes.LNE_END_SEQUENCE:
                            state.setEndSequence();
                            break;
                        case Opcodes.LNE_SET_ADDRESS:
                            int address = read4();
                            state.setAddress(address);
                            break;
                        case Opcodes.LNE_DEFINE_FILE:
                            readString();
                            read1();
                            readUnsignedInt();
                            readUnsignedInt();
                            readUnsignedInt();
                            break;
                        case Opcodes.LNE_SET_DISCRIMINATOR:
                            int discriminator = readUnsignedInt();
                            state.setDiscriminator(discriminator);
                            break;
                    }
                    break;
                case Opcodes.LNS_COPY:
                    state.addRow();
                    break;
                case Opcodes.LNS_ADVANCE_PC:
                    int advance = readUnsignedInt();
                    state.advancePc(advance);
                    break;
                case Opcodes.LNS_ADVANCE_LINE:
                    int lineAdvance = readInt();
                    state.advanceLine(lineAdvance);
                    break;
                case Opcodes.LNS_SET_FILE:
                    int file = readUnsignedInt();
                    state.setFile(file);
                    break;
                case Opcodes.LNS_SET_COLUMN:
                    int column = readUnsignedInt();
                    state.setColumn(column);
                    break;
                case Opcodes.LNS_NEGATE_STMT:
                    state.negateStatement();
                    break;
                case Opcodes.LNS_SET_BASIC_BLOCK:
                    state.setBasicBlock();
                    break;
                case Opcodes.LNS_CONST_ADD_PC:
                    state.addConstantPc();
                    break;
                case Opcodes.LNS_FIXED_ADVANCE_PC:
                    int fixedAdvance = read2();
                    state.addFixedPc(fixedAdvance);
                    break;
                case Opcodes.LNS_SET_PROLOGUE_END:
                    state.setPrologueEnd();
                    break;
                case Opcodes.LNS_SET_EPILOGUE_BEGIN:
                    state.setEpilogueBegin();
                    break;
                case Opcodes.LNS_SET_ISA:
                    int isa = readUnsignedInt();
                    state.setIsa(isa);
                    break;
                default:
                    state.specialOpcode(opcode);
                    break;
            }
        }
        this.offset = currentOffset;
        return state.lineMaps();
    }

    private DebugState readLineSectionHeader(String compilationPath, String compilationUnitName) {
        int length = read4();
        read2(); // read version
        read4(); // read header length
        int minInstrLength = readUnsigned1();
        int maxOpsPerInstr = readUnsigned1();
        int defaultIsStmt = readUnsigned1();
        int lineBase = read1();
        int lineRange = readUnsigned1();
        int opcodeBase = readUnsigned1();

        // read standard opcode lengths
        for (byte i = 0; i < opcodeBase - 1; i++) {
            read1();
        }

        List<String> paths = new ArrayList<>();

        // read included directories
        byte lastByte = peek1();
        while (lastByte != 0) {
            paths.add(readString());
            lastByte = peek1();
        }
        read1();

        List<Path> filePaths = new ArrayList<>();
        if (compilationUnitName.startsWith(compilationPath)) {
            filePaths.add(Paths.get(compilationUnitName));
        } else {
            filePaths.add(Paths.get(compilationPath, compilationUnitName));
        }

        // read file names
        lastByte = peek1();
        while (lastByte != 0) {
            String name = readString();
            int pathIndex = readUnsignedInt();
            readUnsignedInt();
            readUnsignedInt();
            lastByte = peek1();
            String containingPath = pathIndex == 0 ? compilationPath : paths.get(pathIndex - 1);
            if (containingPath.startsWith(compilationPath)) {
                filePaths.add(Paths.get(containingPath, name));
            } else {
                filePaths.add(Paths.get(compilationPath, containingPath, name));
            }
        }
        read1();
        return new DebugState(defaultIsStmt != 0, lineBase, lineRange, opcodeBase, minInstrLength, maxOpsPerInstr, length, filePaths);
    }

    @TruffleBoundary
    public IntArrayList readRangeSection(int rangeOffset) {
        final int currentOffset = this.offset;
        this.offset = rangeOffset;
        IntArrayList ranges = new IntArrayList();
        for (;;) {
            int start = read4();
            int end = read4();
            if (start == -1) {
                continue;
            }
            if (start == 0 && end == 0) {
                break;
            }
            ranges.add(start);
            ranges.add(end);
        }
        this.offset = currentOffset;
        return ranges;
    }

    @TruffleBoundary
    public byte[] readLocationList(int locOffset) {
        final int currentOffset = this.offset;
        this.offset = locOffset;
        byte[] b = null;
        for (;;) {
            int start = read4();
            int end = read4();
            if (start == -1) {
                continue;
            }
            if (start == 0 && end == 0) {
                break;
            }
            int length = read2();
            b = new byte[length];
            for (int i = 0; i < length; i++) {
                b[i] = read1();
            }
        }
        this.offset = currentOffset;
        return b;
    }

    private boolean is64Bit() {
        return Integer.compareUnsigned(peek4(), 0xFFFF_FFFF) == 0;
    }

    private int readInitialLength() {
        int value = read4();
        if (Integer.compareUnsigned(value, 0xFFFF_FFF0) > 0) {
            return -1;
        }
        return value;
    }

    private byte peek1() {
        return BinaryStreamParser.peek1(data, offset);
    }

    private byte read1() {
        byte value = peek1();
        offset += 1;
        return value;
    }

    private int readUnsigned1() {
        return read1() & 0xff;
    }

    private short read2() {
        short value = BinaryStreamParser.peek2(data, offset);
        offset += 2;
        return value;
    }

    private int readUnsigned2() {
        return read2() & 0xffff;
    }

    private int peek4() {
        return peek4(data, offset);
    }

    private static int peek4(byte[] data, int offset) {
        return BinaryStreamParser.peek4(data, offset);
    }

    private int read4() {
        int value = peek4();
        offset += 4;
        return value;
    }

    private long read8() {
        long value = BinaryStreamParser.peek8(data, offset);
        offset += 8;
        return value;
    }

    private int readInt() {
        long valueAndLength = BinaryStreamParser.peekSignedInt32AndLength(data, offset);
        offset += BinaryStreamParser.length(valueAndLength);
        return BinaryStreamParser.value(valueAndLength);
    }

    private static long peekInt(byte[] data, int offset) {
        return BinaryStreamParser.peekSignedInt32AndLength(data, offset);
    }

    private int peekUnsignedInt() {
        long valueAndLength = BinaryStreamParser.peekUnsignedInt32AndLength(data, offset);
        return BinaryStreamParser.value(valueAndLength);
    }

    private static long peekUnsignedInt(byte[] data, int offset) {
        return BinaryStreamParser.peekUnsignedInt32AndLength(data, offset);
    }

    private int readUnsignedInt() {
        long valueAndLength = BinaryStreamParser.peekUnsignedInt32AndLength(data, offset);
        offset += BinaryStreamParser.length(valueAndLength);
        return BinaryStreamParser.value(valueAndLength);
    }

    /**
     * Reads a null terminated string.
     */
    private String readString() {
        int startOffset = offset;
        byte stringByte = read1();
        while (stringByte != 0) {
            stringByte = read1();
        }
        return new String(data, startOffset, offset - startOffset - 1);
    }
}
