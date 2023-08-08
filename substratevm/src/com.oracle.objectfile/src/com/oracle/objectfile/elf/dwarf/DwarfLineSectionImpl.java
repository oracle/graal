/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Red Hat Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.objectfile.elf.dwarf;

import java.util.Iterator;
import java.util.Map;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfLineOpcode;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;
import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.debugentry.range.SubRange;

/**
 * Section generator for debug_line section.
 */
public class DwarfLineSectionImpl extends DwarfSectionImpl {
    /**
     * 0 is used to indicate an invalid opcode.
     */
    private static final int LN_undefined = 0;

    /**
     * Line header section always contains fixed number of bytes.
     */
    private static final int LN_HEADER_SIZE = 28;
    /**
     * Current generator follows C++ with line base -5.
     */
    private static final int LN_LINE_BASE = -5;
    /**
     * Current generator follows C++ with line range 14 giving full range -5 to 8.
     */
    private static final int LN_LINE_RANGE = 14;
    /**
     * Current generator uses opcode base of 13 which must equal DW_LNS_set_isa + 1.
     */
    private static final int LN_OPCODE_BASE = 13;

    DwarfLineSectionImpl(DwarfDebugInfo dwarfSections) {
        // line section depends on string section
        super(dwarfSections, DwarfSectionName.DW_LINE_SECTION, DwarfSectionName.DW_STR_SECTION);
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        /*
         * We need to create a header, dir table, file table and line number table encoding for each
         * class CU that contains compiled methods.
         */

        Cursor byteCount = new Cursor();
        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            setLineIndex(classEntry, byteCount.get());
            int headerSize = headerSize();
            int dirTableSize = computeDirTableSize(classEntry);
            int fileTableSize = computeFileTableSize(classEntry);
            int prologueSize = headerSize + dirTableSize + fileTableSize;
            setLinePrologueSize(classEntry, prologueSize);
            // mark the start of the line table for this entry
            int lineNumberTableSize = computeLineNumberTableSize(classEntry);
            int totalSize = prologueSize + lineNumberTableSize;
            byteCount.add(totalSize);
        });
        byte[] buffer = new byte[byteCount.get()];
        super.setContent(buffer);
    }

    private static int headerSize() {
        /*
         * Header size is standard 31 bytes:
         *
         * <ul>
         *
         * <li><code>uint32 total_length</code>
         *
         * <li><code>uint16 version</code>
         *
         * <li><code>uint32 header_length</code>
         *
         * <li><code>uint8 min_insn_length</code>
         *
         * <li><code>uint8 max_operations_per_instruction</code>
         *
         * <li><code>uint8 default_is_stmt</code>
         *
         * <li><code>int8 line_base</code>
         *
         * <li><code>uint8 line_range</code>
         *
         * <li><code>uint8 opcode_base</code>
         *
         * <li><code>uint8[opcode_base-1] standard_opcode_lengths</code>
         *
         * </ul>
         */

        return LN_HEADER_SIZE;
    }

    private int computeDirTableSize(ClassEntry classEntry) {
        /*
         * Table contains a sequence of 'nul'-terminated UTF8 dir name bytes followed by an extra
         * 'nul'.
         */
        Cursor cursor = new Cursor();
        classEntry.dirStream().forEachOrdered(dirEntry -> {
            int length = countUTF8Bytes(dirEntry.getPathString());
            // We should never have a null or zero length entry in local dirs
            assert length > 0;
            cursor.add(length + 1);
        });
        /*
         * Allow for terminator nul.
         */
        cursor.add(1);
        return cursor.get();
    }

    private int computeFileTableSize(ClassEntry classEntry) {
        /*
         * Table contains a sequence of file entries followed by an extra 'nul'
         *
         * each file entry consists of a 'nul'-terminated UTF8 file name, a dir entry idx and two 0
         * time stamps
         */
        Cursor cursor = new Cursor();
        classEntry.fileStream().forEachOrdered(fileEntry -> {
            // We want the file base name excluding path.
            String baseName = fileEntry.getFileName();
            int length = countUTF8Bytes(baseName);
            // We should never have a null or zero length entry in local files.
            assert length > 0;
            cursor.add(length + 1);
            // The dir index gets written as a ULEB
            int dirIdx = classEntry.getDirIdx(fileEntry);
            cursor.add(writeULEB(dirIdx, scratch, 0));
            // The two zero timestamps require 1 byte each
            cursor.add(2);
        });
        /*
         * Allow for terminator nul.
         */
        cursor.add(1);
        return cursor.get();
    }

    private int computeLineNumberTableSize(ClassEntry classEntry) {
        /*
         * Sigh -- we have to do this by generating the content even though we cannot write it into
         * a byte[].
         */
        return writeLineNumberTable(null, classEntry, null, 0);
    }

    @Override
    public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        ObjectFile.Element textElement = getElement().getOwner().elementForName(".text");
        LayoutDecisionMap decisionMap = alreadyDecided.get(textElement);
        if (decisionMap != null) {
            Object valueObj = decisionMap.getDecidedValue(LayoutDecision.Kind.VADDR);
            if (valueObj != null && valueObj instanceof Number) {
                /*
                 * This may not be the final vaddr for the text segment but it will be close enough
                 * to make debug easier i.e. to within a 4k page or two.
                 */
                debugTextBase = ((Number) valueObj).longValue();
            }
        }
        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        Cursor cursor = new Cursor();

        enableLog(context, cursor.get());
        log(context, "  [0x%08x] DEBUG_LINE", cursor.get());
        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            int pos = cursor.get();
            setLineIndex(classEntry, pos);
            int lengthPos = pos;
            pos = writeHeader(classEntry, buffer, pos);
            log(context, "  [0x%08x] headerSize = 0x%08x", pos, pos);
            int dirTablePos = pos;
            pos = writeDirTable(context, classEntry, buffer, pos);
            log(context, "  [0x%08x] dirTableSize = 0x%08x", pos, pos - dirTablePos);
            int fileTablePos = pos;
            pos = writeFileTable(context, classEntry, buffer, pos);
            log(context, "  [0x%08x] fileTableSize = 0x%08x", pos, pos - fileTablePos);
            int lineNumberTablePos = pos;
            pos = writeLineNumberTable(context, classEntry, buffer, pos);
            log(context, "  [0x%08x] lineNumberTableSize = 0x%x", pos, pos - lineNumberTablePos);
            log(context, "  [0x%08x] size = 0x%x", pos, pos - lengthPos);
            patchLength(lengthPos, buffer, pos);
            cursor.set(pos);
        });
        assert cursor.get() == buffer.length;
    }

    private int writeHeader(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        /*
         * write dummy 4 ubyte length field.
         */
        pos = writeInt(0, buffer, pos);
        /*
         * 2 ubyte version is always 2.
         */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_4, buffer, pos);
        /*
         * 4 ubyte prologue length includes rest of header and dir + file table section.
         */
        int prologueSize = getLinePrologueSize(classEntry) - (4 + 2 + 4);
        pos = writeInt(prologueSize, buffer, pos);
        /*
         * 1 ubyte min instruction length is always 1.
         */
        pos = writeByte((byte) 1, buffer, pos);
        /*
         * 1 ubyte max operations per instruction is always 1.
         */
        pos = writeByte((byte) 1, buffer, pos);
        /*
         * 1 byte default is_stmt is always 1.
         */
        pos = writeByte((byte) 1, buffer, pos);
        /*
         * 1 byte line base is always -5.
         */
        pos = writeByte((byte) LN_LINE_BASE, buffer, pos);
        /*
         * 1 ubyte line range is always 14 giving range -5 to 8.
         */
        pos = writeByte((byte) LN_LINE_RANGE, buffer, pos);
        /*
         * 1 ubyte opcode base is always 13.
         */
        pos = writeByte((byte) LN_OPCODE_BASE, buffer, pos);
        /*
         * specify opcode arg sizes for the standard opcodes.
         */
        /* DW_LNS_copy */
        writeByte((byte) 0, buffer, pos);
        /* DW_LNS_advance_pc */
        writeByte((byte) 1, buffer, pos + 1);
        /* DW_LNS_advance_line */
        writeByte((byte) 1, buffer, pos + 2);
        /* DW_LNS_set_file */
        writeByte((byte) 1, buffer, pos + 3);
        /* DW_LNS_set_column */
        writeByte((byte) 1, buffer, pos + 4);
        /* DW_LNS_negate_stmt */
        writeByte((byte) 0, buffer, pos + 5);
        /* DW_LNS_set_basic_block */
        writeByte((byte) 0, buffer, pos + 6);
        /* DW_LNS_const_add_pc */
        writeByte((byte) 0, buffer, pos + 7);
        /* DW_LNS_fixed_advance_pc */
        writeByte((byte) 1, buffer, pos + 8);
        /* DW_LNS_set_prologue_end */
        writeByte((byte) 0, buffer, pos + 9);
        /* DW_LNS_set_epilogue_begin */
        writeByte((byte) 0, buffer, pos + 10);
        /* DW_LNS_set_isa */
        pos = writeByte((byte) 1, buffer, pos + 11);
        return pos;
    }

    private int writeDirTable(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        verboseLog(context, "  [0x%08x] Dir Name", p);
        /*
         * Write out the list of dirs
         */
        Cursor cursor = new Cursor(p);
        Cursor idx = new Cursor(1);
        classEntry.dirStream().forEach(dirEntry -> {
            int dirIdx = idx.get();
            assert (classEntry.getDirIdx(dirEntry) == dirIdx);
            String dirPath = dirEntry.getPathString();
            verboseLog(context, "  [0x%08x] %-4d %s", cursor.get(), dirIdx, dirPath);
            cursor.set(writeUTF8StringBytes(dirPath, buffer, cursor.get()));
            idx.add(1);
        });
        /*
         * Separate dirs from files with a nul.
         */
        cursor.set(writeByte((byte) 0, buffer, cursor.get()));
        return cursor.get();
    }

    private int writeFileTable(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        verboseLog(context, "  [0x%08x] Entry Dir  Name", p);
        /*
         * Write out the list of files
         */
        Cursor cursor = new Cursor(p);
        Cursor idx = new Cursor(1);
        classEntry.fileStream().forEach(fileEntry -> {
            int pos = cursor.get();
            int fileIdx = idx.get();
            assert classEntry.getFileIdx(fileEntry) == fileIdx;
            int dirIdx = classEntry.getDirIdx(fileEntry);
            String baseName = fileEntry.getFileName();
            verboseLog(context, "  [0x%08x] %-5d %-5d %s", pos, fileIdx, dirIdx, baseName);
            pos = writeUTF8StringBytes(baseName, buffer, pos);
            pos = writeULEB(dirIdx, buffer, pos);
            pos = writeULEB(0, buffer, pos);
            pos = writeULEB(0, buffer, pos);
            cursor.set(pos);
            idx.add(1);
        });
        /*
         * Terminate files with a nul.
         */
        cursor.set(writeByte((byte) 0, buffer, cursor.get()));
        return cursor.get();
    }

    private long debugLine = 1;
    private int debugCopyCount = 0;

    private int writeCompiledMethodLineInfo(DebugContext context, ClassEntry classEntry, CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        Range primaryRange = compiledEntry.getPrimary();
        // the compiled method might be a substitution and not in the file of the class entry
        FileEntry fileEntry = primaryRange.getFileEntry();
        if (fileEntry == null) {
            log(context, "  [0x%08x] primary range [0x%08x, 0x%08x] skipped (no file) %s", pos, debugTextBase + primaryRange.getLo(), debugTextBase + primaryRange.getHi(),
                            primaryRange.getFullMethodNameWithParams());
            return pos;
        }
        String file = fileEntry.getFileName();
        int fileIdx = classEntry.getFileIdx(fileEntry);
        /*
         * Each primary represents a method i.e. a contiguous sequence of subranges. For normal
         * methods we expect the first leaf range to start at offset 0 covering the method prologue.
         * In that case we can rely on it to set the initial file, line and address for the state
         * machine. Otherwise we need to default the initial state and copy it to the file.
         */
        long line = primaryRange.getLine();
        long address = primaryRange.getLo();
        Range prologueRange = prologueLeafRange(compiledEntry);
        if (prologueRange != null) {
            // use the line for the range and use its file if available
            line = prologueRange.getLine();
            if (line > 0) {
                FileEntry firstFileEntry = prologueRange.getFileEntry();
                if (firstFileEntry != null) {
                    fileIdx = classEntry.getFileIdx(firstFileEntry);
                }
            }
        }
        if (line < 0) {
            // never emit a negative line
            line = 0;
        }

        /*
         * Set state for primary.
         */
        log(context, "  [0x%08x] primary range [0x%08x, 0x%08x] %s %s:%d", pos, debugTextBase + primaryRange.getLo(), debugTextBase + primaryRange.getHi(),
                        primaryRange.getFullMethodNameWithParams(),
                        file, primaryRange.getLine());

        /*
         * Initialize and write a row for the start of the compiled method.
         */
        pos = writeSetFileOp(context, file, fileIdx, buffer, pos);
        pos = writeSetBasicBlockOp(context, buffer, pos);
        /*
         * Address is currently at offset 0.
         */
        pos = writeSetAddressOp(context, address, buffer, pos);
        /*
         * State machine value of line is currently 1 increment to desired line.
         */
        if (line != 1) {
            pos = writeAdvanceLineOp(context, line - 1, buffer, pos);
        }
        pos = writeCopyOp(context, buffer, pos);

        /*
         * Now write a row for each subrange lo and hi.
         */
        Iterator<SubRange> iterator = compiledEntry.leafRangeIterator();
        if (prologueRange != null) {
            // skip already processed range
            SubRange first = iterator.next();
            assert first == prologueRange;
        }
        while (iterator.hasNext()) {
            SubRange subrange = iterator.next();
            assert subrange.getLo() >= primaryRange.getLo();
            assert subrange.getHi() <= primaryRange.getHi();
            FileEntry subFileEntry = subrange.getFileEntry();
            if (subFileEntry == null) {
                continue;
            }
            String subfile = subFileEntry.getFileName();
            int subFileIdx = classEntry.getFileIdx(subFileEntry);
            assert subFileIdx > 0;
            long subLine = subrange.getLine();
            long subAddressLo = subrange.getLo();
            long subAddressHi = subrange.getHi();
            log(context, "  [0x%08x] sub range [0x%08x, 0x%08x] %s %s:%d", pos, debugTextBase + subAddressLo, debugTextBase + subAddressHi, subrange.getFullMethodNameWithParams(), subfile,
                            subLine);
            if (subLine < 0) {
                /*
                 * No line info so stay at previous file:line.
                 */
                subLine = line;
                subfile = file;
                subFileIdx = fileIdx;
                verboseLog(context, "  [0x%08x] missing line info - staying put at %s:%d", pos, file, line);
            }
            /*
             * There is a temptation to append end sequence at here when the hiAddress lies strictly
             * between the current address and the start of the next subrange because, ostensibly,
             * we have void space between the end of the current subrange and the start of the next
             * one. however, debug works better if we treat all the insns up to the next range start
             * as belonging to the current line.
             *
             * If we have to update to a new file then do so.
             */
            if (subFileIdx != fileIdx) {
                /*
                 * Update the current file.
                 */
                pos = writeSetFileOp(context, subfile, subFileIdx, buffer, pos);
                file = subfile;
                fileIdx = subFileIdx;
            }
            long lineDelta = subLine - line;
            long addressDelta = subAddressLo - address;
            /*
             * Check if we can advance line and/or address in one byte with a special opcode.
             */
            byte opcode = isSpecialOpcode(addressDelta, lineDelta);
            if (opcode != LN_undefined) {
                /*
                 * Ignore pointless write when addressDelta == lineDelta == 0.
                 */
                if (addressDelta != 0 || lineDelta != 0) {
                    pos = writeSpecialOpcode(context, opcode, buffer, pos);
                }
            } else {
                /*
                 * Does it help to divide and conquer using a fixed address increment.
                 */
                int remainder = isConstAddPC(addressDelta);
                if (remainder > 0) {
                    pos = writeConstAddPCOp(context, buffer, pos);
                    /*
                     * The remaining address can be handled with a special opcode but what about the
                     * line delta.
                     */
                    opcode = isSpecialOpcode(remainder, lineDelta);
                    if (opcode != LN_undefined) {
                        /*
                         * Address remainder and line now fit.
                         */
                        pos = writeSpecialOpcode(context, opcode, buffer, pos);
                    } else {
                        /*
                         * Ok, bump the line separately then use a special opcode for the address
                         * remainder.
                         */
                        opcode = isSpecialOpcode(remainder, 0);
                        assert opcode != LN_undefined;
                        pos = writeAdvanceLineOp(context, lineDelta, buffer, pos);
                        pos = writeSpecialOpcode(context, opcode, buffer, pos);
                    }
                } else {
                    /*
                     * Increment line and pc separately.
                     */
                    if (lineDelta != 0) {
                        pos = writeAdvanceLineOp(context, lineDelta, buffer, pos);
                    }
                    /*
                     * n.b. we might just have had an out of range line increment with a zero
                     * address increment.
                     */
                    if (addressDelta > 0) {
                        /*
                         * See if we can use a ushort for the increment.
                         */
                        if (isFixedAdvancePC(addressDelta)) {
                            pos = writeFixedAdvancePCOp(context, (short) addressDelta, buffer, pos);
                        } else {
                            pos = writeAdvancePCOp(context, addressDelta, buffer, pos);
                        }
                    }
                    pos = writeCopyOp(context, buffer, pos);
                }
            }
            /*
             * Move line and address range on.
             */
            line += lineDelta;
            address += addressDelta;
        }
        /*
         * Append a final end sequence just below the next primary range.
         */
        if (address < primaryRange.getHi()) {
            long addressDelta = primaryRange.getHi() - address;
            /*
             * Increment address before we write the end sequence.
             */
            pos = writeAdvancePCOp(context, addressDelta, buffer, pos);
        }
        pos = writeEndSequenceOp(context, buffer, pos);

        return pos;
    }

    private int writeLineNumberTable(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        Cursor cursor = new Cursor(p);
        classEntry.compiledEntries().forEachOrdered(compiledMethod -> {
            int pos = cursor.get();
            String methodName = compiledMethod.getPrimary().getFullMethodNameWithParams();
            String fileName = compiledMethod.getClassEntry().getFullFileName();
            log(context, "  [0x%08x] %s %s", pos, methodName, fileName);
            pos = writeCompiledMethodLineInfo(context, classEntry, compiledMethod, buffer, pos);
            cursor.set(pos);
        });
        return cursor.get();
    }

    private static SubRange prologueLeafRange(CompiledMethodEntry compiledEntry) {
        Iterator<SubRange> iterator = compiledEntry.leafRangeIterator();
        if (iterator.hasNext()) {
            SubRange range = iterator.next();
            if (range.getLo() == compiledEntry.getPrimary().getLo()) {
                return range;
            }
        }
        return null;
    }

    private int writeCopyOp(DebugContext context, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_copy;
        int pos = p;
        debugCopyCount++;
        verboseLog(context, "  [0x%08x] Copy %d", pos, debugCopyCount);
        return writeLineOpcode(opcode, buffer, pos);
    }

    private int writeAdvancePCOp(DebugContext context, long uleb, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_advance_pc;
        int pos = p;
        debugAddress += uleb;
        verboseLog(context, "  [0x%08x] Advance PC by %d to 0x%08x", pos, uleb, debugAddress);
        pos = writeLineOpcode(opcode, buffer, pos);
        return writeULEB(uleb, buffer, pos);
    }

    private int writeAdvanceLineOp(DebugContext context, long sleb, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_advance_line;
        int pos = p;
        debugLine += sleb;
        verboseLog(context, "  [0x%08x] Advance Line by %d to %d", pos, sleb, debugLine);
        pos = writeLineOpcode(opcode, buffer, pos);
        return writeSLEB(sleb, buffer, pos);
    }

    private int writeSetFileOp(DebugContext context, String file, long uleb, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_set_file;
        int pos = p;
        verboseLog(context, "  [0x%08x] Set File Name to entry %d in the File Name Table (%s)", pos, uleb, file);
        pos = writeLineOpcode(opcode, buffer, pos);
        return writeULEB(uleb, buffer, pos);
    }

    @SuppressWarnings("unused")
    private int writeSetColumnOp(DebugContext context, long uleb, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_set_column;
        int pos = p;
        pos = writeLineOpcode(opcode, buffer, pos);
        return writeULEB(uleb, buffer, pos);
    }

    @SuppressWarnings("unused")
    private int writeNegateStmtOp(DebugContext context, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_negate_stmt;
        int pos = p;
        return writeLineOpcode(opcode, buffer, pos);
    }

    private int writeSetBasicBlockOp(DebugContext context, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_set_basic_block;
        int pos = p;
        verboseLog(context, "  [0x%08x] Set basic block", pos);
        return writeLineOpcode(opcode, buffer, pos);
    }

    private int writeConstAddPCOp(DebugContext context, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_const_add_pc;
        int pos = p;
        int advance = opcodeAddress((byte) 255);
        debugAddress += advance;
        verboseLog(context, "  [0x%08x] Advance PC by constant %d to 0x%08x", pos, advance, debugAddress);
        return writeLineOpcode(opcode, buffer, pos);
    }

    private int writeFixedAdvancePCOp(DebugContext context, short arg, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNS_fixed_advance_pc;
        int pos = p;
        debugAddress += arg;
        verboseLog(context, "  [0x%08x] Fixed advance Address by %d to 0x%08x", pos, arg, debugAddress);
        pos = writeLineOpcode(opcode, buffer, pos);
        return writeShort(arg, buffer, pos);
    }

    private int writeEndSequenceOp(DebugContext context, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNE_end_sequence;
        int pos = p;
        verboseLog(context, "  [0x%08x] Extended opcode 1: End sequence", pos);
        debugAddress = debugTextBase;
        debugLine = 1;
        debugCopyCount = 0;
        pos = writePrefixOpcode(buffer, pos);
        /*
         * Insert extended insn byte count as ULEB.
         */
        pos = writeULEB(1, buffer, pos);
        return writeLineOpcode(opcode, buffer, pos);
    }

    private int writeSetAddressOp(DebugContext context, long arg, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNE_set_address;
        int pos = p;
        debugAddress = debugTextBase + (int) arg;
        verboseLog(context, "  [0x%08x] Extended opcode 2: Set Address to 0x%08x", pos, debugAddress);
        pos = writePrefixOpcode(buffer, pos);
        /*
         * Insert extended insn byte count as ULEB.
         */
        pos = writeULEB(9, buffer, pos);
        pos = writeLineOpcode(opcode, buffer, pos);
        return writeRelocatableCodeOffset(arg, buffer, pos);
    }

    @SuppressWarnings("unused")
    private int writeDefineFileOp(DebugContext context, String file, long uleb1, long uleb2, long uleb3, byte[] buffer, int p) {
        DwarfLineOpcode opcode = DwarfLineOpcode.DW_LNE_define_file;
        int pos = p;
        /*
         * Calculate bytes needed for opcode + args.
         */
        int fileBytes = countUTF8Bytes(file) + 1;
        long insnBytes = 1;
        insnBytes += fileBytes;
        insnBytes += writeULEB(uleb1, scratch, 0);
        insnBytes += writeULEB(uleb2, scratch, 0);
        insnBytes += writeULEB(uleb3, scratch, 0);
        verboseLog(context, "  [0x%08x] Extended opcode 3: Define File %s idx %d ts1 %d ts2 %d", pos, file, uleb1, uleb2, uleb3);
        pos = writePrefixOpcode(buffer, pos);
        /*
         * Insert insn length as uleb.
         */
        pos = writeULEB(insnBytes, buffer, pos);
        /*
         * Insert opcode and args.
         */
        pos = writeLineOpcode(opcode, buffer, pos);
        pos = writeUTF8StringBytes(file, buffer, pos);
        pos = writeULEB(uleb1, buffer, pos);
        pos = writeULEB(uleb2, buffer, pos);
        return writeULEB(uleb3, buffer, pos);
    }

    private int writePrefixOpcode(byte[] buffer, int p) {
        return writeLineOpcode(DwarfLineOpcode.DW_LNS_extended_prefix, buffer, p);
    }

    private int writeLineOpcode(DwarfLineOpcode opcode, byte[] buffer, int p) {
        return writeByte(opcode.value(), buffer, p);
    }

    private static int opcodeId(byte opcode) {
        int iopcode = opcode & 0xff;
        return iopcode - LN_OPCODE_BASE;
    }

    private static int opcodeAddress(byte opcode) {
        int iopcode = opcode & 0xff;
        return (iopcode - LN_OPCODE_BASE) / LN_LINE_RANGE;
    }

    private static int opcodeLine(byte opcode) {
        int iopcode = opcode & 0xff;
        return ((iopcode - LN_OPCODE_BASE) % LN_LINE_RANGE) + LN_LINE_BASE;
    }

    private int writeSpecialOpcode(DebugContext context, byte opcode, byte[] buffer, int p) {
        int pos = p;
        if (debug && opcode == 0) {
            verboseLog(context, "  [0x%08x] ERROR Special Opcode %d: Address 0x%08x Line %d", debugAddress, debugLine);
        }
        debugAddress += opcodeAddress(opcode);
        debugLine += opcodeLine(opcode);
        verboseLog(context, "  [0x%08x] Special Opcode %d: advance Address by %d to 0x%08x and Line by %d to %d",
                        pos, opcodeId(opcode), opcodeAddress(opcode), debugAddress, opcodeLine(opcode), debugLine);
        return writeByte(opcode, buffer, pos);
    }

    private static final int MAX_ADDRESS_ONLY_DELTA = (0xff - LN_OPCODE_BASE) / LN_LINE_RANGE;
    private static final int MAX_ADDPC_DELTA = MAX_ADDRESS_ONLY_DELTA + (MAX_ADDRESS_ONLY_DELTA - 1);

    private static byte isSpecialOpcode(long addressDelta, long lineDelta) {
        if (addressDelta < 0) {
            return LN_undefined;
        }
        if (lineDelta >= LN_LINE_BASE) {
            long offsetLineDelta = lineDelta - LN_LINE_BASE;
            if (offsetLineDelta < LN_LINE_RANGE) {
                /*
                 * The line delta can be encoded. Check if address is ok.
                 */
                if (addressDelta <= MAX_ADDRESS_ONLY_DELTA) {
                    long opcode = LN_OPCODE_BASE + (addressDelta * LN_LINE_RANGE) + offsetLineDelta;
                    if (opcode <= 255) {
                        return (byte) opcode;
                    }
                }
            }
        }

        /*
         * Answer no by returning an invalid opcode.
         */
        return LN_undefined;
    }

    private static int isConstAddPC(long addressDelta) {
        if (addressDelta < MAX_ADDRESS_ONLY_DELTA) {
            return 0;
        }
        if (addressDelta <= MAX_ADDPC_DELTA) {
            return (int) (addressDelta - MAX_ADDRESS_ONLY_DELTA);
        } else {
            return 0;
        }
    }

    private static boolean isFixedAdvancePC(long addressDiff) {
        return addressDiff >= 0 && addressDiff < 0xffff;
    }
}
