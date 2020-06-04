/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Red Hat Inc. All rights reserved.
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

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.DirEntry;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider.DebugFrameSizeChange;
import org.graalvm.compiler.debug.DebugContext;

import java.util.Map;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_LINE_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_STR_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_VERSION_2;

/**
 * Section generator for debug_line section.
 */
public class DwarfLineSectionImpl extends DwarfSectionImpl {
    /**
     * Line header section always contains fixed number of bytes.
     */
    private static final int DW_LN_HEADER_SIZE = 27;
    /**
     * Current generator follows C++ with line base -5.
     */
    private static final int DW_LN_LINE_BASE = -5;
    /**
     * Current generator follows C++ with line range 14 giving full range -5 to 8.
     */
    private static final int DW_LN_LINE_RANGE = 14;
    /**
     * Current generator uses opcode base of 13 which must equal DW_LNS_define_file + 1.
     */
    private static final int DW_LN_OPCODE_BASE = 13;

    /*
     * Standard opcodes defined by Dwarf 2
     */
    /*
     * 0 can be returned to indicate an invalid opcode.
     */
    private static final byte DW_LNS_undefined = 0;
    /*
     * 0 can be inserted as a prefix for extended opcodes.
     */
    private static final byte DW_LNS_extended_prefix = 0;
    /*
     * Append current state as matrix row 0 args.
     */
    private static final byte DW_LNS_copy = 1;
    /*
     * Increment address 1 uleb arg.
     */
    private static final byte DW_LNS_advance_pc = 2;
    /*
     * Increment line 1 sleb arg.
     */
    private static final byte DW_LNS_advance_line = 3;
    /*
     * Set file 1 uleb arg.
     */
    private static final byte DW_LNS_set_file = 4;
    /*
     * sSet column 1 uleb arg.
     */
    private static final byte DW_LNS_set_column = 5;
    /*
     * Flip is_stmt 0 args.
     */
    private static final byte DW_LNS_negate_stmt = 6;
    /*
     * Set end sequence and copy row 0 args.
     */
    private static final byte DW_LNS_set_basic_block = 7;
    /*
     * Increment address as per opcode 255 0 args.
     */
    private static final byte DW_LNS_const_add_pc = 8;
    /*
     * Increment address 1 ushort arg.
     */
    private static final byte DW_LNS_fixed_advance_pc = 9;

    /*
     * Extended opcodes defined by DWARF 2.
     */
    /*
     * There is no extended opcode 0.
     */
    @SuppressWarnings("unused") private static final byte DW_LNE_undefined = 0;
    /*
     * End sequence of addresses.
     */
    private static final byte DW_LNE_end_sequence = 1;
    /*
     * Set address as explicit long argument.
     */
    private static final byte DW_LNE_set_address = 2;
    /*
     * Set file as explicit string argument.
     */
    private static final byte DW_LNE_define_file = 3;

    DwarfLineSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_LINE_SECTION_NAME;
    }

    @Override
    public void createContent() {
        /*
         * We need to create a header, dir table, file table and line number table encoding for each
         * CU.
         */

        /*
         * Write entries for each file listed in the primary list.
         */
        int pos = 0;
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.getFileName().length() != 0) {
                int startPos = pos;
                classEntry.setLineIndex(startPos);
                int headerSize = headerSize();
                int dirTableSize = computeDirTableSize(classEntry);
                int fileTableSize = computeFileTableSize(classEntry);
                int prologueSize = headerSize + dirTableSize + fileTableSize;
                classEntry.setLinePrologueSize(prologueSize);
                int lineNumberTableSize = computeLineNUmberTableSize(classEntry);
                int totalSize = prologueSize + lineNumberTableSize;
                classEntry.setTotalSize(totalSize);
                pos += totalSize;
            }
        }
        byte[] buffer = new byte[pos];
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
         * <li><code>uint32 prologue_length</code>
         *
         * <li><code>uint8 min_insn_length</code>
         *
         * <li><code>uint8 default_is_stmt</code>
         *
         * <li><code>int8 line_base</code>
         *
         * <li><code>uint8 line_range</code>
         *
         * <li><code>uint8 opcode_base</code>
         *
         * <li><code>uint8 li_opcode_base</code>
         *
         * <li><code>uint8[opcode_base-1] standard_opcode_lengths</code>
         *
         * </ul>
         */

        return DW_LN_HEADER_SIZE;
    }

    private static int computeDirTableSize(ClassEntry classEntry) {
        /*
         * Table contains a sequence of 'nul'-terminated dir name bytes followed by an extra 'nul'
         * and then a sequence of 'nul'-terminated file name bytes followed by an extra 'nul'.
         *
         * For now we assume dir and file names are ASCII byte strings.
         */
        int dirSize = 0;
        for (DirEntry dir : classEntry.getLocalDirs()) {
            dirSize += dir.getPathString().length() + 1;
        }
        /*
         * Allow for separator nul.
         */
        dirSize++;
        return dirSize;
    }

    private int computeFileTableSize(ClassEntry classEntry) {
        /*
         * Table contains a sequence of 'nul'-terminated dir name bytes followed by an extra 'nul'
         * and then a sequence of 'nul'-terminated file name bytes followed by an extra 'nul'.
         * 
         * For now we assume dir and file names are ASCII byte strings.
         */
        int fileSize = 0;
        for (FileEntry localEntry : classEntry.getLocalFiles()) {
            /*
             * We want the file base name excluding path.
             */
            String baseName = localEntry.getFileName();
            int length = baseName.length();
            /* We should never have a null or zero length entry in local files. */
            assert length > 0;
            fileSize += length + 1;
            DirEntry dirEntry = localEntry.getDirEntry();
            int idx = classEntry.localDirsIdx(dirEntry);
            fileSize += putULEB(idx, scratch, 0);
            /*
             * The two zero timestamps require 1 byte each.
             */
            fileSize += 2;
        }
        /*
         * Allow for terminator nul.
         */
        fileSize++;
        return fileSize;
    }

    private int computeLineNUmberTableSize(ClassEntry classEntry) {
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
        byte[] buffer = getContent();

        int pos = 0;
        enableLog(context, pos);
        log(context, "  [0x%08x] DEBUG_LINE", pos);

        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.getFileName().length() != 0) {
                int startPos = pos;
                assert classEntry.getLineIndex() == startPos;
                log(context, "  [0x%08x] Compile Unit for %s", pos, classEntry.getFileName());
                pos = writeHeader(classEntry, buffer, pos);
                log(context, "  [0x%08x] headerSize = 0x%08x", pos, pos - startPos);
                int dirTablePos = pos;
                pos = writeDirTable(context, classEntry, buffer, pos);
                log(context, "  [0x%08x] dirTableSize = 0x%08x", pos, pos - dirTablePos);
                int fileTablePos = pos;
                pos = writeFileTable(context, classEntry, buffer, pos);
                log(context, "  [0x%08x] fileTableSize = 0x%08x", pos, pos - fileTablePos);
                int lineNumberTablePos = pos;
                pos = writeLineNumberTable(context, classEntry, buffer, pos);
                log(context, "  [0x%08x] lineNumberTableSize = 0x%x", pos, pos - lineNumberTablePos);
                log(context, "  [0x%08x] size = 0x%x", pos, pos - startPos);
            }
        }
        assert pos == buffer.length;
    }

    private int writeHeader(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        /*
         * 4 ubyte length field.
         */
        pos = putInt(classEntry.getTotalSize() - 4, buffer, pos);
        /*
         * 2 ubyte version is always 2.
         */
        pos = putShort(DW_VERSION_2, buffer, pos);
        /*
         * 4 ubyte prologue length includes rest of header and dir + file table section.
         */
        int prologueSize = classEntry.getLinePrologueSize() - (4 + 2 + 4);
        pos = putInt(prologueSize, buffer, pos);
        /*
         * 1 ubyte min instruction length is always 1.
         */
        pos = putByte((byte) 1, buffer, pos);
        /*
         * 1 byte default is_stmt is always 1.
         */
        pos = putByte((byte) 1, buffer, pos);
        /*
         * 1 byte line base is always -5.
         */
        pos = putByte((byte) DW_LN_LINE_BASE, buffer, pos);
        /*
         * 1 ubyte line range is always 14 giving range -5 to 8.
         */
        pos = putByte((byte) DW_LN_LINE_RANGE, buffer, pos);
        /*
         * 1 ubyte opcode base is always 13.
         */
        pos = putByte((byte) DW_LN_OPCODE_BASE, buffer, pos);
        /*
         * specify opcode arg sizes for the standard opcodes.
         */
        /* DW_LNS_copy */
        putByte((byte) 0, buffer, pos);
        /* DW_LNS_advance_pc */
        putByte((byte) 1, buffer, pos + 1);
        /* DW_LNS_advance_line */
        putByte((byte) 1, buffer, pos + 2);
        /* DW_LNS_set_file */
        putByte((byte) 1, buffer, pos + 3);
        /* DW_LNS_set_column */
        putByte((byte) 1, buffer, pos + 4);
        /* DW_LNS_negate_stmt */
        putByte((byte) 0, buffer, pos + 5);
        /* DW_LNS_set_basic_block */
        putByte((byte) 0, buffer, pos + 6);
        /* DW_LNS_const_add_pc */
        putByte((byte) 0, buffer, pos + 7);
        /* DW_LNS_fixed_advance_pc */
        putByte((byte) 1, buffer, pos + 8);
        /* DW_LNS_end_sequence */
        putByte((byte) 0, buffer, pos + 9);
        /* DW_LNS_set_address */
        putByte((byte) 0, buffer, pos + 10);
        /* DW_LNS_define_file */
        pos = putByte((byte) 1, buffer, pos + 11);
        return pos;
    }

    private int writeDirTable(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        verboseLog(context, "  [0x%08x] Dir  Name", pos);
        /*
         * Write out the list of dirs referenced form this file entry.
         */
        int dirIdx = 1;
        for (DirEntry dir : classEntry.getLocalDirs()) {
            /*
             * write nul terminated string text.
             */
            verboseLog(context, "  [0x%08x] %-4d %s", pos, dirIdx, dir.getPath());
            pos = putAsciiStringBytes(dir.getPathString(), buffer, pos);
            dirIdx++;
        }
        /*
         * Separate dirs from files with a nul.
         */
        pos = putByte((byte) 0, buffer, pos);
        return pos;
    }

    private int writeFileTable(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        int fileIdx = 1;
        verboseLog(context, "  [0x%08x] Entry Dir  Name", pos);
        for (FileEntry localEntry : classEntry.getLocalFiles()) {
            /*
             * We need the file name minus path, the associated dir index, and 0 for time stamps.
             */
            String baseName = localEntry.getFileName();
            DirEntry dirEntry = localEntry.getDirEntry();
            int dirIdx = classEntry.localDirsIdx(dirEntry);
            verboseLog(context, "  [0x%08x] %-5d %-5d %s", pos, fileIdx, dirIdx, baseName);
            pos = putAsciiStringBytes(baseName, buffer, pos);
            pos = putULEB(dirIdx, buffer, pos);
            pos = putULEB(0, buffer, pos);
            pos = putULEB(0, buffer, pos);
            fileIdx++;
        }
        /*
         * Terminate files with a nul.
         */
        pos = putByte((byte) 0, buffer, pos);
        return pos;
    }

    private int debugLine = 1;
    private int debugCopyCount = 0;

    private int writeLineNumberTable(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        FileEntry fileEntry = classEntry.getFileEntry();
        if (fileEntry == null) {
            return pos;
        }
        /*
         * The primary file entry should always be first in the local files list.
         */
        assert classEntry.localFilesIdx(fileEntry) == 1;
        String primaryClassName = classEntry.getClassName();
        String primaryFileName = classEntry.getFileName();
        String file = primaryFileName;
        int fileIdx = 1;
        log(context, "  [0x%08x] primary class %s", pos, primaryClassName);
        log(context, "  [0x%08x] primary file %s", pos, primaryFileName);
        for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
            Range primaryRange = primaryEntry.getPrimary();
            assert primaryRange.getFileName().equals(primaryFileName);
            /*
             * Each primary represents a method i.e. a contiguous sequence of subranges. we assume
             * the default state at the start of each sequence because we always post an
             * end_sequence when we finish all the subranges in the method.
             */
            long line = primaryRange.getLine();
            if (line < 0 && primaryEntry.getSubranges().size() > 0) {
                line = primaryEntry.getSubranges().get(0).getLine();
            }
            if (line < 0) {
                line = 0;
            }
            long address = primaryRange.getLo();

            /*
             * Set state for primary.
             */
            log(context, "  [0x%08x] primary range [0x%08x, 0x%08x] %s:%d", pos, debugTextBase + primaryRange.getLo(), debugTextBase + primaryRange.getHi(), primaryRange.getFullMethodName(),
                            primaryRange.getLine());

            /*
             * Initialize and write a row for the start of the primary method.
             */
            pos = writeSetFileOp(context, file, fileIdx, buffer, pos);
            pos = writeSetBasicBlockOp(context, buffer, pos);
            /*
             * Address is currently 0.
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
             * On AArch64 gdb expects to see a line record at the start of the method and a second
             * one at the end of the prologue marking the point where the method code begins for
             * real. If we don't provide it then gdb will skip to the second line record when we
             * place a breakpoint on the method.
             *
             * We can identify the end of the prologue for normal methods by noting where the stack
             * frame height is first adjusted. This should normally be no more a few instructions in
             * total.
             */
            if (isAArch64() && !primaryEntry.getFrameSizeInfos().isEmpty()) {
                DebugFrameSizeChange frameSizeChange = primaryEntry.getFrameSizeInfos().get(0);
                assert frameSizeChange.getType() == DebugFrameSizeChange.Type.EXTEND;
                long addressDelta = frameSizeChange.getOffset();
                if (addressDelta < 16 && (primaryRange.getLo() + addressDelta) < primaryRange.getHi()) {
                    /*
                     * we should be able to write this with a special opcode as the prologue should
                     * only be a few instructions
                     */
                    byte opcode = isSpecialOpcode(addressDelta, 0);
                    assert opcode != DW_LNS_undefined;
                    pos = writeSpecialOpcode(context, opcode, buffer, pos);
                    pos = writeCopyOp(context, buffer, pos);
                    address += addressDelta;
                }
            }
            /*
             * Now write a row for each subrange lo and hi.
             */
            for (Range subrange : primaryEntry.getSubranges()) {
                assert subrange.getLo() >= primaryRange.getLo();
                assert subrange.getHi() <= primaryRange.getHi();
                FileEntry subFileEntry = primaryEntry.getSubrangeFileEntry(subrange);
                if (subFileEntry == null) {
                    continue;
                }
                String subfile = subFileEntry.getFileName();
                int subFileIdx = classEntry.localFilesIdx(subFileEntry);
                long subLine = subrange.getLine();
                long subAddressLo = subrange.getLo();
                long subAddressHi = subrange.getHi();
                log(context, "  [0x%08x] sub range [0x%08x, 0x%08x] %s:%d", pos, debugTextBase + subAddressLo, debugTextBase + subAddressHi, subrange.getFullMethodName(), subLine);
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
                 * There is a temptation to append end sequence at here when the hiAddress lies
                 * strictly between the current address and the start of the next subrange because,
                 * ostensibly, we have void space between the end of the current subrange and the
                 * start of the next one. however, debug works better if we treat all the insns up
                 * to the next range start as belonging to the current line.
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
                /*
                 * Check if we can advance line and/or address in one byte with a special opcode.
                 */
                long lineDelta = subLine - line;
                long addressDelta = subAddressLo - address;
                byte opcode = isSpecialOpcode(addressDelta, lineDelta);
                if (opcode != DW_LNS_undefined) {
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
                         * The remaining address can be handled with a special opcode but what about
                         * the line delta.
                         */
                        opcode = isSpecialOpcode(remainder, lineDelta);
                        if (opcode != DW_LNS_undefined) {
                            /*
                             * Address remainder and line now fit.
                             */
                            pos = writeSpecialOpcode(context, opcode, buffer, pos);
                        } else {
                            /*
                             * Ok, bump the line separately then use a special opcode for the
                             * address remainder.
                             */
                            opcode = isSpecialOpcode(remainder, 0);
                            assert opcode != DW_LNS_undefined;
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
        }
        log(context, "  [0x%08x] primary file processed %s", pos, primaryFileName);

        return pos;
    }

    private int writeCopyOp(DebugContext context, byte[] buffer, int p) {
        byte opcode = DW_LNS_copy;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            debugCopyCount++;
            verboseLog(context, "  [0x%08x] Copy %d", pos, debugCopyCount);
            return putByte(opcode, buffer, pos);
        }
    }

    private int writeAdvancePCOp(DebugContext context, long uleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_advance_pc;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putULEB(uleb, scratch, 0);
        } else {
            debugAddress += uleb;
            verboseLog(context, "  [0x%08x] Advance PC by %d to 0x%08x", pos, uleb, debugAddress);
            pos = putByte(opcode, buffer, pos);
            return putULEB(uleb, buffer, pos);
        }
    }

    private int writeAdvanceLineOp(DebugContext context, long sleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_advance_line;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putSLEB(sleb, scratch, 0);
        } else {
            debugLine += sleb;
            verboseLog(context, "  [0x%08x] Advance Line by %d to %d", pos, sleb, debugLine);
            pos = putByte(opcode, buffer, pos);
            return putSLEB(sleb, buffer, pos);
        }
    }

    private int writeSetFileOp(DebugContext context, String file, long uleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_set_file;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putULEB(uleb, scratch, 0);
        } else {
            verboseLog(context, "  [0x%08x] Set File Name to entry %d in the File Name Table (%s)", pos, uleb, file);
            pos = putByte(opcode, buffer, pos);
            return putULEB(uleb, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    private int writeSetColumnOp(DebugContext context, long uleb, byte[] buffer, int p) {
        byte opcode = DW_LNS_set_column;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putULEB(uleb, scratch, 0);
        } else {
            pos = putByte(opcode, buffer, pos);
            return putULEB(uleb, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    private int writeNegateStmtOp(DebugContext context, byte[] buffer, int p) {
        byte opcode = DW_LNS_negate_stmt;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            return putByte(opcode, buffer, pos);
        }
    }

    private int writeSetBasicBlockOp(DebugContext context, byte[] buffer, int p) {
        byte opcode = DW_LNS_set_basic_block;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            verboseLog(context, "  [0x%08x] Set basic block", pos);
            return putByte(opcode, buffer, pos);
        }
    }

    private int writeConstAddPCOp(DebugContext context, byte[] buffer, int p) {
        byte opcode = DW_LNS_const_add_pc;
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            int advance = opcodeAddress((byte) 255);
            debugAddress += advance;
            verboseLog(context, "  [0x%08x] Advance PC by constant %d to 0x%08x", pos, advance, debugAddress);
            return putByte(opcode, buffer, pos);
        }
    }

    private int writeFixedAdvancePCOp(DebugContext context, short arg, byte[] buffer, int p) {
        byte opcode = DW_LNS_fixed_advance_pc;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putShort(arg, scratch, 0);
        } else {
            debugAddress += arg;
            verboseLog(context, "  [0x%08x] Fixed advance Address by %d to 0x%08x", pos, arg, debugAddress);
            pos = putByte(opcode, buffer, pos);
            return putShort(arg, buffer, pos);
        }
    }

    private int writeEndSequenceOp(DebugContext context, byte[] buffer, int p) {
        byte opcode = DW_LNE_end_sequence;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
            /*
             * Insert extended insn byte count as ULEB.
             */
            pos = pos + putULEB(1, scratch, 0);
            return pos + putByte(opcode, scratch, 0);
        } else {
            verboseLog(context, "  [0x%08x] Extended opcode 1: End sequence", pos);
            debugAddress = debugTextBase;
            debugLine = 1;
            debugCopyCount = 0;
            pos = putByte(DW_LNS_extended_prefix, buffer, pos);
            /*
             * Insert extended insn byte count as ULEB.
             */
            pos = putULEB(1, buffer, pos);
            return putByte(opcode, buffer, pos);
        }
    }

    private int writeSetAddressOp(DebugContext context, long arg, byte[] buffer, int p) {
        byte opcode = DW_LNE_set_address;
        int pos = p;
        if (buffer == null) {
            pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
            /*
             * Insert extended insn byte count as ULEB.
             */
            pos = pos + putULEB(9, scratch, 0);
            pos = pos + putByte(opcode, scratch, 0);
            return pos + putLong(arg, scratch, 0);
        } else {
            debugAddress = debugTextBase + (int) arg;
            verboseLog(context, "  [0x%08x] Extended opcode 2: Set Address to 0x%08x", pos, debugAddress);
            pos = putByte(DW_LNS_extended_prefix, buffer, pos);
            /*
             * Insert extended insn byte count as ULEB.
             */
            pos = putULEB(9, buffer, pos);
            pos = putByte(opcode, buffer, pos);
            return putRelocatableCodeOffset(arg, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    private int writeDefineFileOp(DebugContext context, String file, long uleb1, long uleb2, long uleb3, byte[] buffer, int p) {
        byte opcode = DW_LNE_define_file;
        int pos = p;
        /*
         * Calculate bytes needed for opcode + args.
         */
        int fileBytes = file.length() + 1;
        long insnBytes = 1;
        insnBytes += fileBytes;
        insnBytes += putULEB(uleb1, scratch, 0);
        insnBytes += putULEB(uleb2, scratch, 0);
        insnBytes += putULEB(uleb3, scratch, 0);
        if (buffer == null) {
            pos = pos + putByte(DW_LNS_extended_prefix, scratch, 0);
            /*
             * Write insnBytes as a ULEB.
             */
            pos += putULEB(insnBytes, scratch, 0);
            return pos + (int) insnBytes;
        } else {
            verboseLog(context, "  [0x%08x] Extended opcode 3: Define File %s idx %d ts1 %d ts2 %d", pos, file, uleb1, uleb2, uleb3);
            pos = putByte(DW_LNS_extended_prefix, buffer, pos);
            /*
             * Insert insn length as uleb.
             */
            pos = putULEB(insnBytes, buffer, pos);
            /*
             * Insert opcode and args.
             */
            pos = putByte(opcode, buffer, pos);
            pos = putAsciiStringBytes(file, buffer, pos);
            pos = putULEB(uleb1, buffer, pos);
            pos = putULEB(uleb2, buffer, pos);
            return putULEB(uleb3, buffer, pos);
        }
    }

    private static int opcodeId(byte opcode) {
        int iopcode = opcode & 0xff;
        return iopcode - DW_LN_OPCODE_BASE;
    }

    private static int opcodeAddress(byte opcode) {
        int iopcode = opcode & 0xff;
        return (iopcode - DW_LN_OPCODE_BASE) / DW_LN_LINE_RANGE;
    }

    private static int opcodeLine(byte opcode) {
        int iopcode = opcode & 0xff;
        return ((iopcode - DW_LN_OPCODE_BASE) % DW_LN_LINE_RANGE) + DW_LN_LINE_BASE;
    }

    private int writeSpecialOpcode(DebugContext context, byte opcode, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + putByte(opcode, scratch, 0);
        } else {
            if (debug && opcode == 0) {
                verboseLog(context, "  [0x%08x] ERROR Special Opcode %d: Address 0x%08x Line %d", debugAddress, debugLine);
            }
            debugAddress += opcodeAddress(opcode);
            debugLine += opcodeLine(opcode);
            verboseLog(context, "  [0x%08x] Special Opcode %d: advance Address by %d to 0x%08x and Line by %d to %d",
                            pos, opcodeId(opcode), opcodeAddress(opcode), debugAddress, opcodeLine(opcode), debugLine);
            return putByte(opcode, buffer, pos);
        }
    }

    private static final int MAX_ADDRESS_ONLY_DELTA = (0xff - DW_LN_OPCODE_BASE) / DW_LN_LINE_RANGE;
    private static final int MAX_ADDPC_DELTA = MAX_ADDRESS_ONLY_DELTA + (MAX_ADDRESS_ONLY_DELTA - 1);

    private static byte isSpecialOpcode(long addressDelta, long lineDelta) {
        if (addressDelta < 0) {
            return DW_LNS_undefined;
        }
        if (lineDelta >= DW_LN_LINE_BASE) {
            long offsetLineDelta = lineDelta - DW_LN_LINE_BASE;
            if (offsetLineDelta < DW_LN_LINE_RANGE) {
                /*
                 * The line delta can be encoded. Check if address is ok.
                 */
                if (addressDelta <= MAX_ADDRESS_ONLY_DELTA) {
                    long opcode = DW_LN_OPCODE_BASE + (addressDelta * DW_LN_LINE_RANGE) + offsetLineDelta;
                    if (opcode <= 255) {
                        return (byte) opcode;
                    }
                }
            }
        }

        /*
         * Answer no by returning an invalid opcode.
         */
        return DW_LNS_undefined;
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

    /**
     * The debug_line section content depends on debug_str section content and offset.
     */
    private static final String TARGET_SECTION_NAME = DW_STR_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.OFFSET,
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
