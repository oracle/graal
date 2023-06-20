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

import java.nio.file.InvalidPathException;
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
import org.graalvm.wasm.debugging.WasmDebugException;
import org.graalvm.wasm.debugging.data.DebugAddressSize;
import org.graalvm.wasm.debugging.encoding.DataEncoding;
import org.graalvm.wasm.debugging.encoding.Opcodes;
import org.graalvm.wasm.debugging.encoding.Tags;
import org.graalvm.wasm.exception.WasmException;
import org.graalvm.wasm.nodes.WasmDataAccess;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;

/**
 * Represents a parser for the DWARF Debug Information Format.
 */
public class DebugParser {
    private static final int SUPPORTED_VERSION = 4;
    private static final int SUPPORTED_ADDRESS_LENGTH = 4;

    private static final int UNIT_HEADER_LENGTH = 4;

    private final byte[] data;
    private int offset;
    private int endOffset;

    public DebugParser(byte[] data) {
        this.data = data;
    }

    /**
     * Reads a compilation unit and all its child entries based on a given unit offset.
     * 
     * @param debugInfoOffset the offset of the debug information in the custom data.
     * @param unitOffset the unit offset.
     * @return A {@link DebugParseUnit} or null, if the debug information is malformed.
     */
    @TruffleBoundary
    public DebugParseUnit readEntries(int debugInfoOffset, int unitOffset) {
        final int infoOffset = DebugUtil.getInfoOffsetOrUndefined(data, debugInfoOffset);
        final int infoLength = DebugUtil.getInfoLengthOrUndefined(data, debugInfoOffset);
        final int abbrevOffset = DebugUtil.getAbbrevOffsetOrUndefined(data, debugInfoOffset);
        final int abbrevLength = DebugUtil.getAbbrevLengthOrUndefined(data, debugInfoOffset);
        if (infoOffset == DebugUtil.UNDEFINED || infoLength == DebugUtil.UNDEFINED || abbrevOffset == DebugUtil.UNDEFINED || abbrevLength == DebugUtil.UNDEFINED) {
            return null;
        }
        if (unitOffset == 0) {
            offset = infoOffset;
        } else {
            offset = unitOffset;
        }
        final int unitEndOffset = offset + infoLength;

        endOffset = unitEndOffset;
        if (offset < unitEndOffset) {
            int unitStartOffset = offset - infoOffset;
            try {
                // read header
                if (is64Bit()) {
                    return null;
                }

                final int unitLength = readInitialLength();
                if (unitLength == -1) {
                    // 64 bit length
                    return null;
                }
                final int version = readUnsigned2();
                if (version != SUPPORTED_VERSION) {
                    // Unsupported version
                    return null;
                }

                final int debugAbbrevOffset = read4();
                if (Integer.compareUnsigned(debugAbbrevOffset, abbrevLength) >= 0) {
                    // abbrev offset outside abbrev section
                    return null;
                }
                // read address size
                final int addressSize = read1();
                if (addressSize != SUPPORTED_ADDRESS_LENGTH) {
                    // unsupported address size
                    return null;
                }

                endOffset = unitStartOffset + unitLength + UNIT_HEADER_LENGTH;

                final EconomicMap<Integer, AbbreviationDeclaration> abbreviationTable = readAbbrevSection(abbrevOffset + debugAbbrevOffset, abbrevLength);
                final EconomicMap<Integer, DebugData> entries = EconomicMap.create();
                final DebugData compilationUnit = readDebugEntry(abbreviationTable, debugInfoOffset, unitStartOffset, entries, true);
                if (compilationUnit != null && Integer.compareUnsigned(endOffset, offset) == 0) {
                    return new DebugParseUnit(compilationUnit, entries);
                }
            } catch (WasmDebugException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Computes the offset of the next compilation unit based on a given offset.
     * 
     * @param debugInfoOffset the offset of the debug information in the custom data.
     * @param unitOffset the current unit offset.
     * @return the offset of the next compilation unit or -1, if no unit was found.
     */
    @TruffleBoundary
    public int getNextCompilationUnitOffset(int debugInfoOffset, int unitOffset) {
        final int infoOffset = DebugUtil.getInfoOffsetOrUndefined(data, debugInfoOffset);
        final int infoLength = DebugUtil.getInfoLengthOrUndefined(data, debugInfoOffset);
        if (infoOffset == DebugUtil.UNDEFINED || infoLength == DebugUtil.UNDEFINED) {
            return -1;
        }
        offset = infoOffset;
        endOffset = offset + infoLength;
        if (unitOffset != 0) {
            offset = unitOffset;
        }

        if (offset < endOffset) {
            try {
                if (is64Bit()) {
                    return -1;
                }
                int unitLength = readInitialLength();
                return offset + unitLength;
            } catch (WasmDebugException e) {
                return -1;
            }
        }
        return -1;
    }

    /**
     * Reads the compilation unit at the given unitOffset.
     * 
     * @param debugInfoOffset the offset of the debug information in the custom data.
     * @param unitOffset the unit offset.
     * @return A {@link DebugParseUnit} or null, if the debug information is malformed.
     */
    @TruffleBoundary
    public DebugParseUnit readCompilationUnit(int debugInfoOffset, int unitOffset) {
        final int infoOffset = DebugUtil.getInfoOffsetOrUndefined(data, debugInfoOffset);
        final int infoLength = DebugUtil.getInfoLengthOrUndefined(data, debugInfoOffset);
        final int abbrevOffset = DebugUtil.getAbbrevOffsetOrUndefined(data, debugInfoOffset);
        final int abbrevLength = DebugUtil.getAbbrevLengthOrUndefined(data, debugInfoOffset);
        if (infoOffset == DebugUtil.UNDEFINED || infoLength == DebugUtil.UNDEFINED || abbrevOffset == DebugUtil.UNDEFINED || abbrevLength == DebugUtil.UNDEFINED) {
            return null;
        }
        offset = infoOffset;
        final int unitEndOffset = offset + infoLength;
        if (unitOffset != 0) {
            offset = unitOffset;
        }
        endOffset = unitEndOffset;

        if (offset < unitEndOffset) {
            int unitStartOffset = offset;
            try {
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
                if (version != SUPPORTED_VERSION) {
                    // unsupported version
                    return null;
                }

                final int debugAbbrevOffset = read4();
                if (Integer.compareUnsigned(debugAbbrevOffset, abbrevLength) >= 0) {
                    // abbrev offset outside abbrev section
                    return null;
                }
                // read address size
                final int addressSize = read1();
                if (addressSize != SUPPORTED_ADDRESS_LENGTH) {
                    // unsupported address size
                    return null;
                }

                endOffset = unitStartOffset + unitLength + UNIT_HEADER_LENGTH;

                final EconomicMap<Integer, AbbreviationDeclaration> abbreviationTable = readAbbrevSection(abbrevOffset + debugAbbrevOffset, abbrevLength);
                final EconomicMap<Integer, DebugData> entries = EconomicMap.create();
                final DebugData compilationUnit = readDebugEntry(abbreviationTable, debugInfoOffset, unitStartOffset, entries, false);
                if (compilationUnit != null && compilationUnit.tag() == Tags.COMPILATION_UNIT) {
                    return new DebugParseUnit(compilationUnit, entries);
                }
            } catch (WasmDebugException e) {
                return null;
            }
        }
        return null;
    }

    private EconomicMap<Integer, AbbreviationDeclaration> readAbbrevSection(int abbrevOffset, int abbrevLength) throws WasmDebugException {
        final int currentOffset = this.offset;
        final int currentEndOffset = this.endOffset;
        this.offset = abbrevOffset;
        this.endOffset = abbrevOffset + abbrevLength;
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
        this.endOffset = currentEndOffset;
        return table;
    }

    private AbbreviationDeclaration readAbbrevEntry() throws WasmDebugException {
        final int index = readUnsignedInt();
        final int tag = readUnsignedInt();
        boolean children = read1() != 0;
        final AbbreviationDeclaration entry = new AbbreviationDeclaration(index, tag, children);
        for (;;) {
            final int attributeName = readUnsignedInt();
            final int attributeForm = readUnsignedInt();
            if (attributeName == 0 && attributeForm == 0) {
                break;
            }
            entry.addAttribute(attributeName, attributeForm);
        }
        return entry;
    }

    private DebugData readDebugEntry(EconomicMap<Integer, AbbreviationDeclaration> abbrevTable, int debugInfoOffset, int compilationUnitOffset,
                    EconomicMap<Integer, DebugData> entries, boolean followChildren) throws WasmDebugException {
        final int startOffset = offset;
        final int abbrevDeclarationIndex = readUnsignedInt();
        final AbbreviationDeclaration declaration = abbrevTable.get(abbrevDeclarationIndex);
        final int infoOffset = DebugUtil.getInfoOffsetOrUndefined(data, debugInfoOffset);
        if (declaration == null || infoOffset == DebugUtil.UNDEFINED) {
            // Malformed abbreviation table. Declaration not found
            return null;
        }
        final int entryOffset = startOffset - infoOffset - compilationUnitOffset;

        final long[] attributeInfo = new long[declaration.attributeCount()];
        final Object[] attributes = new Object[declaration.attributeCount()];
        // read attributes
        for (int i = 0; i != declaration.attributeCount(); ++i) {
            final int attribute = declaration.attribute(i);
            final int attributeEncoding = DataEncoding.fromForm(declaration.attributeForm(i));
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
                    final int length;
                    try {
                        length = BinaryStreamParser.peekLeb128Length(data, offset);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new WasmDebugException(e.getMessage());
                    }
                    if (length > 4) {
                        checkOffset(length);
                        offset += length;
                        value = 0;
                    } else {
                        value = readInt();
                    }
                } else if (DataEncoding.isLeb128Unsigned(attributeEncoding)) {
                    final int length;
                    try {
                        length = BinaryStreamParser.peekLeb128Length(data, offset);
                    } catch (ArrayIndexOutOfBoundsException e) {
                        throw new WasmDebugException(e.getMessage());
                    }
                    if (length > 4) {
                        checkOffset(length);
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
                    final int stringOffset = read4();
                    final int strOffset = DebugUtil.getStrOffsetOrUndefined(data, debugInfoOffset);
                    final int strLength = DebugUtil.getStrLengthOrUndefined(data, debugInfoOffset);
                    if (strOffset == DebugUtil.UNDEFINED || strLength == DebugUtil.UNDEFINED || Integer.compareUnsigned(stringOffset, strLength) >= 0) {
                        return null;
                    }
                    value = readString(strOffset + stringOffset, strLength);
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
        final List<DebugData> children = new ArrayList<>();
        if (declaration.hasChildren() && followChildren) {
            while (offset < endOffset) {
                if (peekUnsignedInt() == 0) {
                    readUnsignedInt();
                    break;
                }
                final DebugData child = readDebugEntry(abbrevTable, debugInfoOffset, compilationUnitOffset, entries, true);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        final DebugData entry = new DebugData(declaration.tag(), entryOffset, attributeInfo, attributes, children.toArray(new DebugData[0]));
        entries.put(entryOffset, entry);
        return entry;
    }

    /**
     * Creates a {@link DebugLocation} representing a frame base based on the given expression.
     * 
     * @param expressionData the data of the expression.
     * @param frame the current frame.
     * @param dataAccess a data access object.
     * @param addressSize the used address size.
     * @return A new {@link DebugLocation} or null, if the given expression is malformed.
     */
    @TruffleBoundary
    public static DebugLocation readFrameBaseExpressionOrNull(byte[] expressionData, MaterializedFrame frame, WasmDataAccess dataAccess, DebugAddressSize addressSize) {
        try {
            return readExpression(expressionData, frame, dataAccess, null, null, addressSize);
        } catch (WasmDebugException e) {
            return null;
        }
    }

    /**
     * Creates a {@link DebugLocation} based on the given expression.
     * 
     * @param expressionData the data of the expression.
     * @param location the base location that should be manipulated.
     * @return A new {@link DebugLocation}. If the given expression is malformed an invalid location
     *         is returned.
     */
    @TruffleBoundary
    public static DebugLocation readExpression(byte[] expressionData, DebugLocation location) {
        try {
            return readExpression(expressionData, location.frame(), location.dataAccess(), location.frameBase(), location, location.addressSize());
        } catch (WasmDebugException e) {
            return location.invalidate();
        }
    }

    private static DebugLocation readExpression(byte[] expressionData, MaterializedFrame frame, WasmDataAccess dataAccess, DebugLocation frameBase, DebugLocation baseLocation,
                    DebugAddressSize addressSize) throws WasmDebugException {
        final Deque<DebugLocation> valueStack = new ArrayDeque<>();
        if (baseLocation != null) {
            valueStack.push(baseLocation);
        }
        int exprOffset = 0;
        final int expressionEndOffset = expressionData.length;
        while (exprOffset < expressionEndOffset) {
            final byte opcode = peek1(expressionData, exprOffset, expressionEndOffset);
            exprOffset++;
            switch (opcode) {
                case Opcodes.WASM_LOCATION:
                    final byte type = peek1(expressionData, exprOffset, expressionEndOffset);
                    exprOffset++;
                    switch (type) {
                        case 0x00: {
                            // local
                            final long valueAndLength = peekUnsignedIntValueAndLength(expressionData, exprOffset, expressionEndOffset);
                            final int value = BinaryStreamParser.value(valueAndLength);
                            valueStack.push(DebugLocation.createLocalAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += BinaryStreamParser.length(valueAndLength);
                            break;
                        }
                        case 0x01: {
                            // global
                            final long valueAndLength = peekUnsignedIntValueAndLength(expressionData, exprOffset, expressionEndOffset);
                            final int value = BinaryStreamParser.value(valueAndLength);
                            valueStack.push(DebugLocation.createGlobalAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += BinaryStreamParser.length(valueAndLength);
                            break;
                        }
                        case 0x02: {
                            // stack value
                            final long valueAndLength = peekUnsignedIntValueAndLength(expressionData, exprOffset, expressionEndOffset);
                            final int value = BinaryStreamParser.value(valueAndLength);
                            valueStack.push(DebugLocation.createStackAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += BinaryStreamParser.length(valueAndLength);
                            break;
                        }
                        case 0x03:
                            // global
                            final int value = peek4(expressionData, exprOffset, expressionEndOffset);
                            valueStack.push(DebugLocation.createGlobalAccess(value, frame, dataAccess, frameBase, addressSize));
                            exprOffset += 4;
                            break;
                    }
                    break;
                case Opcodes.STACK_VALUE:
                    return valueStack.pop();
                case Opcodes.FBREG:
                    final long offsetValueAndLength = peekIntValueAndLength(expressionData, exprOffset, expressionEndOffset);
                    final int offsetValue = BinaryStreamParser.value(offsetValueAndLength);
                    valueStack.push(frameBase.addOffset(offsetValue));
                    exprOffset += BinaryStreamParser.length(offsetValueAndLength);
                    break;
                case Opcodes.ADDR:
                    final int value = peek4(expressionData, exprOffset, expressionEndOffset);
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
    private String readString(int stringOffset, int stringLength) throws WasmDebugException {
        final int currentOffset = this.offset;
        final int currentEndOffset = this.endOffset;
        this.offset = stringOffset;
        this.endOffset = stringOffset + stringLength;
        final String s = readString();
        this.offset = currentOffset;
        this.endOffset = currentEndOffset;
        return s;
    }

    /**
     * Reads the line map at the given offset in the line section.
     * 
     * @param debugLineOffset the line offset.
     * @param debugLineLength the length of the line section.
     * @param compilationPath the compilation path of the compilation unit.
     * @return A {@link DebugLineMap} array or null, if the line section contains unsupported data
     *         or is malformed.
     */
    @TruffleBoundary
    public DebugLineMap[] readLineSectionOrNull(int debugLineOffset, int debugLineLength, String compilationPath) {
        final int currentOffset = this.offset;
        final int currentEndOffset = this.endOffset;
        this.offset = debugLineOffset;
        this.endOffset = debugLineOffset + debugLineLength;
        final DebugState state;
        try {
            state = readLineSectionHeader(compilationPath);
        } catch (WasmDebugException e) {
            return null;
        }
        if (state == null) {
            return null;
        }
        final int sectionLength = debugLineOffset + state.length();
        while (offset < sectionLength) {
            try {
                final int opcode = readUnsigned1();
                switch (opcode) {
                    case Opcodes.EXTENDED_OPCODE:
                        final int length = readUnsignedInt();
                        if (length == 0) {
                            break;
                        }
                        final int extendedOpcode = readUnsigned1();
                        switch (extendedOpcode) {
                            case Opcodes.LNE_END_SEQUENCE:
                                state.setEndSequence();
                                break;
                            case Opcodes.LNE_SET_ADDRESS:
                                final int address = read4();
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
                                final int discriminator = readUnsignedInt();
                                state.setDiscriminator(discriminator);
                                break;
                        }
                        break;
                    case Opcodes.LNS_COPY:
                        state.addRow();
                        break;
                    case Opcodes.LNS_ADVANCE_PC:
                        final int advance = readUnsignedInt();
                        state.advancePc(advance);
                        break;
                    case Opcodes.LNS_ADVANCE_LINE:
                        final int lineAdvance = readInt();
                        state.advanceLine(lineAdvance);
                        break;
                    case Opcodes.LNS_SET_FILE:
                        final int file = readUnsignedInt();
                        state.setFile(file);
                        break;
                    case Opcodes.LNS_SET_COLUMN:
                        final int column = readUnsignedInt();
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
                        final int fixedAdvance = read2();
                        state.addFixedPc(fixedAdvance);
                        break;
                    case Opcodes.LNS_SET_PROLOGUE_END:
                        state.setPrologueEnd();
                        break;
                    case Opcodes.LNS_SET_EPILOGUE_BEGIN:
                        state.setEpilogueBegin();
                        break;
                    case Opcodes.LNS_SET_ISA:
                        final int isa = readUnsignedInt();
                        state.setIsa(isa);
                        break;
                    default:
                        state.specialOpcode(opcode);
                        break;
                }
            } catch (WasmDebugException e) {
                return null;
            }
        }
        this.offset = currentOffset;
        this.endOffset = currentEndOffset;
        return state.lineMaps();
    }

    /**
     * Reads the line section header.
     * 
     * @return a {@link DebugState} object or null, if the header contains unsupported information.
     * @throws WasmDebugException If the line section is malformed.
     */
    private DebugState readLineSectionHeader(String compilationPath) throws WasmDebugException {
        final int length = read4();
        final int version = read2(); // read version
        if (version != SUPPORTED_VERSION) {
            return null;
        }
        final int headerLength = read4(); // read header length
        final int headerEndOffset = offset + headerLength;
        final int minInstrLength = readUnsigned1();
        final int maxOpsPerInstr = readUnsigned1();
        final int defaultIsStmt = readUnsigned1();
        final int lineBase = read1();
        final int lineRange = readUnsigned1();
        final int opcodeBase = readUnsigned1();

        // read standard opcode lengths
        for (byte i = 0; i < opcodeBase - 1; i++) {
            read1();
        }

        final List<String> paths = new ArrayList<>();

        // read included directories
        byte lastByte = peek1();
        while (lastByte != 0) {
            paths.add(readString());
            lastByte = peek1();
        }
        read1();

        final List<Path> filePaths = new ArrayList<>();
        try {
            filePaths.add(Paths.get(compilationPath));
        } catch (InvalidPathException e) {
            throw new WasmDebugException(e.getMessage());
        }
        // read file names
        lastByte = peek1();
        while (lastByte != 0) {
            final String name = readString();
            final int pathIndex = readUnsignedInt();
            readUnsignedInt();
            readUnsignedInt();
            lastByte = peek1();
            final String containingPath = pathIndex == 0 ? compilationPath : paths.get(pathIndex - 1);
            try {
                if (containingPath.startsWith(compilationPath)) {
                    filePaths.add(Paths.get(containingPath, name));
                } else {
                    filePaths.add(Paths.get(compilationPath, containingPath, name));
                }
            } catch (InvalidPathException e) {
                throw new WasmDebugException(e.getMessage());
            }
        }
        read1();

        if (filePaths.size() == 1) {
            return null;
        }
        if (offset != headerEndOffset) {
            return null;
        }
        return new DebugState(defaultIsStmt != 0, lineBase, lineRange, opcodeBase, minInstrLength, maxOpsPerInstr, length, filePaths);
    }

    /**
     * Reads the range list at the given offset from the range section.
     * 
     * @param rangeOffset the range offset.
     * @param rangeLength the length of the range section.
     * @return an {@link IntArrayList} containing the start and end of the range or null, if the
     *         range section is malformed.
     */
    @TruffleBoundary
    public IntArrayList readRangeSectionOrNull(int rangeOffset, int rangeLength) {
        final int currentOffset = this.offset;
        final int currentEndOffset = this.endOffset;
        this.offset = rangeOffset;
        this.endOffset = rangeOffset + rangeLength;
        final IntArrayList ranges = new IntArrayList();
        for (;;) {
            final int start;
            final int end;
            try {
                start = read4();
                end = read4();
            } catch (WasmDebugException e) {
                return null;
            }
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
        this.endOffset = currentEndOffset;
        return ranges;
    }

    /**
     * Reads the location list at the given offset from the location section.
     * 
     * @param locOffset the location offset.
     * @param locLength the length of the location section.
     * @return a byte array containing the locations or null, if the location section is malformed.
     */
    @TruffleBoundary
    public byte[] readLocationListOrNull(int locOffset, int locLength) {
        final int currentOffset = this.offset;
        final int currentEndOffset = this.endOffset;
        this.offset = locOffset;
        this.endOffset = locOffset + locLength;
        byte[] b = null;
        for (;;) {
            try {
                final int start = read4();
                final int end = read4();
                if (start == -1) {
                    continue;
                }
                if (start == 0 && end == 0) {
                    break;
                }
                final int length = read2();
                b = new byte[length];
                for (int i = 0; i < length; i++) {
                    b[i] = read1();
                }
            } catch (WasmDebugException e) {
                return null;
            }
        }
        this.offset = currentOffset;
        this.endOffset = currentEndOffset;
        return b;
    }

    private boolean is64Bit() throws WasmDebugException {
        return Integer.compareUnsigned(peek4(), 0xFFFF_FFFF) == 0;
    }

    private int readInitialLength() throws WasmDebugException {
        final int value = read4();
        if (Integer.compareUnsigned(value, 0xFFFF_FFF0) > 0) {
            return -1;
        }
        return value;
    }

    /**
     * @throws WasmDebugException if the given value is beyond the given endOffset.
     */
    private static void checkOffset(int offset, int endOffset, int length) throws WasmDebugException {
        if (Integer.compareUnsigned(offset + length, endOffset) > 0) {
            throw new WasmDebugException("out of bounds data access");
        }
    }

    /**
     * @throws WasmDebugException if the given value is beyond the current endOffset.
     */
    private void checkOffset(int length) throws WasmDebugException {
        checkOffset(offset, endOffset, length);
    }

    /**
     * Reads a single byte from the given byte array without advancing the offset pointer.
     * 
     * @throws WasmDebugException if the data is beyond the endOffset.
     */
    private static byte peek1(byte[] data, int offset, int endOffset) throws WasmDebugException {
        checkOffset(offset, endOffset, 1);
        try {
            return BinaryStreamParser.peek1(data, offset);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads a single byte from the internal byte array without advancing the offset pointer.
     *
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private byte peek1() throws WasmDebugException {
        return peek1(data, offset, endOffset);
    }

    /**
     * Reads a single byte from the internal byte array and advances the offset pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private byte read1() throws WasmDebugException {
        final byte value = peek1();
        offset += 1;
        return value;
    }

    /**
     * Reads a single byte as an unsigned int value from the internal byte array and advances the
     * offset pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int readUnsigned1() throws WasmDebugException {
        return read1() & 0xff;
    }

    /**
     * Reads two bytes as a short value from the internal byte array and advances the offset
     * pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private short read2() throws WasmDebugException {
        checkOffset(2);
        try {
            final short value = BinaryStreamParser.peek2(data, offset);
            offset += 2;
            return value;
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads two bytes as an unsigned int value from the internal byte array and advances the offset
     * pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int readUnsigned2() throws WasmDebugException {
        return read2() & 0xffff;
    }

    /**
     * Reads four bytes as an int value from the internal byte array without advancing the offset
     * pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int peek4() throws WasmDebugException {
        return peek4(data, offset, endOffset);
    }

    /**
     * Reads four bytes as an in value from the given byte array without advancing the offset
     * pointer.
     *
     * @throws WasmDebugException if the data is beyond the given endOffset.
     */
    private static int peek4(byte[] data, int offset, int endOffset) throws WasmDebugException {
        checkOffset(offset, endOffset, 4);
        try {
            return BinaryStreamParser.peek4(data, offset);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads four bytes as an int value from the internal byte array and advances the offset
     * pointer.
     *
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int read4() throws WasmDebugException {
        final int value = peek4();
        offset += 4;
        return value;
    }

    /**
     * Reads eight bytes as a long value from the internal byte array and advances the offset
     * pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private long read8() throws WasmDebugException {
        checkOffset(8);
        try {
            long value = BinaryStreamParser.peek8(data, offset);
            offset += 8;
            return value;
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads a LEB128-encoded int value from the internal byte array and advances the offset
     * pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int readInt() throws WasmDebugException {
        try {
            final long valueAndLength = BinaryStreamParser.peekSignedInt32AndLength(data, offset);
            final int length = BinaryStreamParser.length(valueAndLength);
            checkOffset(length);
            offset += length;
            return BinaryStreamParser.value(valueAndLength);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads the int value and length of a LEB128-encoded int value from the given byte array
     * without advancing the offset pointer.
     * 
     * @throws WasmDebugException if the data is beyond the given endOffset.
     */
    private static long peekIntValueAndLength(byte[] data, int offset, int endOffset) throws WasmDebugException {
        try {
            final int length = BinaryStreamParser.peekLeb128Length(data, offset);
            checkOffset(offset, endOffset, length);
            return BinaryStreamParser.peekSignedInt32AndLength(data, offset);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads the LEB128-encoded unsigned int value from the internal byte array without advancing
     * the offset pointer.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int peekUnsignedInt() throws WasmDebugException {
        try {
            final long valueAndLength = BinaryStreamParser.peekUnsignedInt32AndLength(data, offset);
            final int length = BinaryStreamParser.length(valueAndLength);
            checkOffset(length);
            return BinaryStreamParser.value(valueAndLength);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads the int value and length of a LEB128-encoded unsigned int value from the given byte
     * array without advancing the offset pointer.
     * 
     * @throws WasmDebugException if the data is beyond the given endOffset.
     */
    private static long peekUnsignedIntValueAndLength(byte[] data, int offset, int endOffset) throws WasmDebugException {
        try {
            final int length = BinaryStreamParser.peekLeb128Length(data, offset);
            checkOffset(offset, endOffset, length);
            return BinaryStreamParser.peekUnsignedInt32AndLength(data, offset);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads the LEB128-encoded unsigned int value from the internal byte array and advances the
     * offset pointer.
     *
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private int readUnsignedInt() throws WasmDebugException {
        try {
            final long valueAndLength = BinaryStreamParser.peekUnsignedInt32AndLength(data, offset);
            final int length = BinaryStreamParser.length(valueAndLength);
            checkOffset(length);
            offset += length;
            return BinaryStreamParser.value(valueAndLength);
        } catch (WasmException e) {
            throw new WasmDebugException(e.getMessage());
        }
    }

    /**
     * Reads a null terminated string.
     * 
     * @throws WasmDebugException if the data is beyond the current endOffset.
     */
    private String readString() throws WasmDebugException {
        final int startOffset = offset;
        byte stringByte = read1();
        while (stringByte != 0) {
            stringByte = read1();
        }
        return new String(data, startOffset, offset - startOffset - 1);
    }
}
