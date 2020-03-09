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
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;

import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_CIE_id;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_CIE_version;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_advance_loc;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_advance_loc1;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_advance_loc2;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_advance_loc4;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_def_cfa;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_def_cfa_offset;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_nop;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_offset;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_CFA_register;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_FRAME_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_LINE_SECTION_NAME;
/**
 * Section generic generator for debug_frame section.
 */
public abstract class DwarfFrameSectionImpl extends DwarfSectionImpl {

    public DwarfFrameSectionImpl(DwarfSections dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_FRAME_SECTION_NAME;
    }

    @Override
    public void createContent() {
        int pos = 0;

        /*
         * the frame section contains one CIE at offset 0
         * followed by an FIE for each method
         */
        pos = writeCIE(null, pos);
        pos = writeMethodFrames(null, pos);

        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent() {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        checkDebug(pos);

        /*
         * there are entries for the prologue region where the
         * stack is being built, the method body region(s) where
         * the code executes with a fixed size frame and the
         * epilogue region(s) where the stack is torn down
         */
        pos = writeCIE(buffer, pos);
        pos = writeMethodFrames(buffer, pos);

        if (pos != size) {
            System.out.format("pos = 0x%x  size = 0x%x", pos, size);
        }
        assert pos == size;
    }

    public int writeCIE(byte[] buffer, int p) {
        /*
         * we only need a vanilla CIE with default fields
         * because we have to have at least one
         * the layout is
         *
         * <ul>
         * <li><code>uint32 : length ............... length of remaining fields in this CIE</code>
         * <li><code>uint32 : CIE_id ................ unique id for CIE == 0xffffff</code>
         * <li><code>uint8 : version ................ == 1</code>
         * <li><code>uint8[] : augmentation ......... == "" so always 1 byte</code>
         * <li><code>ULEB : code_alignment_factor ... == 1 (could use 4 for Aarch64)</code>
         * <li><code>ULEB : data_alignment_factor ... == -8</code>
         * <li><code>byte : ret_addr reg id ......... x86_64 => 16 AArch64 => 32</code>
         * <li><code>byte[] : initial_instructions .. includes pad to 8-byte boundary</code>
         * </ul>
         */
        int pos = p;
        if (buffer == null) {
            pos += putInt(0, scratch, 0); // don't care about length
            pos += putInt(DW_CFA_CIE_id, scratch, 0);
            pos += putByte(DW_CFA_CIE_version, scratch, 0);
            pos += putAsciiStringBytes("", scratch, 0);
            pos += putULEB(1, scratch, 0);
            pos += putSLEB(-8, scratch, 0);
            pos += putByte((byte) getPCIdx(), scratch, 0);
            /*
             * write insns to set up empty frame
             */
            pos = writeInitialInstructions(buffer, pos);
            /*
             * pad to word alignment
             */
            pos = writePaddingNops(8, buffer, pos);
            /*
             * no need to write length
             */
            return pos;
        } else {
            int lengthPos = pos;
            pos = putInt(0, buffer, pos);
            pos = putInt(DW_CFA_CIE_id, buffer, pos);
            pos = putByte(DW_CFA_CIE_version, buffer, pos);
            pos = putAsciiStringBytes("", buffer, pos);
            pos = putULEB(1, buffer, pos);
            pos = putSLEB(-8, buffer, pos);
            pos = putByte((byte) getPCIdx(), buffer, pos);
            /*
             * write insns to set up empty frame
             */
            pos = writeInitialInstructions(buffer, pos);
            /*
             * pad to word alignment
             */
            pos = writePaddingNops(8, buffer, pos);
            patchLength(lengthPos, buffer, pos);
            return pos;
        }
    }

    public int writeMethodFrames(byte[] buffer, int p) {
        int pos = p;
        for (ClassEntry classEntry : getPrimaryClasses()) {
            for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
                long lo = primaryEntry.getPrimary().getLo();
                long hi = primaryEntry.getPrimary().getHi();
                int frameSize = primaryEntry.getFrameSize();
                int currentOffset = 0;
                int lengthPos = pos;
                pos = writeFDEHeader((int) lo, (int) hi, buffer, pos);
                for (DebugInfoProvider.DebugFrameSizeChange debugFrameSizeInfo : primaryEntry.getFrameSizeInfos()) {
                    int advance = debugFrameSizeInfo.getOffset() - currentOffset;
                    currentOffset += advance;
                    pos = writeAdvanceLoc(advance, buffer, pos);
                    if (debugFrameSizeInfo.getType() == DebugInfoProvider.DebugFrameSizeChange.Type.EXTEND) {
                        /*
                         * SP has been extended so rebase CFA using full frame
                         */
                        pos = writeDefCFAOffset(frameSize, buffer, pos);
                    } else {
                        /*
                         * SP has been contracted so rebase CFA using empty frame
                         */
                        pos = writeDefCFAOffset(8, buffer, pos);
                    }
                }
                pos = writePaddingNops(8, buffer, pos);
                patchLength(lengthPos, buffer, pos);
            }
        }
        return pos;
    }

    public int writeFDEHeader(int lo, int hi, byte[] buffer, int p) {
        /*
         * we only need a vanilla FDE header with default fields
         * the layout is
         *
         * <ul>
         * <li><code>uint32 : length ............ length of remaining fields in this FDE</code>
         * <li><code>uint32 : CIE_offset ........ always 0 i.e. identifies our only CIE header</code>
         * <li><code>uint64 : initial_location .. i.e. method lo address</code>
         * <li><code>uint64 : address_range ..... i.e. method hi - lo</code>
         * <li><code>byte[] : instructions ...... includes pad to 8-byte boundary</code>
         * </ul>
         */

        int pos = p;
        if (buffer == null) {
            /* dummy length */
            pos += putInt(0, scratch, 0);
            /* CIE_offset */
            pos += putInt(0, scratch, 0);
            /* initial address */
            pos += putLong(lo, scratch, 0);
            /* address range */
            return pos + putLong(hi - lo, scratch, 0);
        } else {
            /* dummy length */
            pos = putInt(0, buffer, pos);
            /* CIE_offset */
            pos = putInt(0, buffer, pos);
            /* initial address */
            pos = putRelocatableCodeOffset(lo, buffer, pos);
            /* address range */
            return putLong(hi - lo, buffer, pos);
        }
    }

    public int writePaddingNops(int alignment, byte[] buffer, int p) {
        int pos = p;
        assert (alignment & (alignment - 1)) == 0;
        while ((pos & (alignment - 1)) != 0) {
            if (buffer == null) {
                pos++;
            } else {
                pos = putByte(DW_CFA_nop, buffer, pos);
            }
        }
        return pos;
    }

    public int writeDefCFA(int register, int offset, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            pos += putByte(DW_CFA_def_cfa, scratch, 0);
            pos += putSLEB(register, scratch, 0);
            return pos + putULEB(offset, scratch, 0);
        } else {
            pos = putByte(DW_CFA_def_cfa, buffer, pos);
            pos = putULEB(register, buffer, pos);
            return putULEB(offset, buffer, pos);
        }
    }

    public int writeDefCFAOffset(int offset, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            pos += putByte(DW_CFA_def_cfa_offset, scratch, 0);
            return pos + putULEB(offset, scratch, 0);
        } else {
            pos = putByte(DW_CFA_def_cfa_offset, buffer, pos);
            return putULEB(offset, buffer, pos);
        }
    }

    public int writeAdvanceLoc(int offset, byte[] buffer, int pos) {
        if (offset <= 0x3f) {
            return writeAdvanceLoc0((byte) offset, buffer, pos);
        } else if (offset <= 0xff) {
            return writeAdvanceLoc1((byte) offset, buffer, pos);
        } else if (offset <= 0xffff) {
            return writeAdvanceLoc2((short) offset, buffer, pos);
        } else {
            return writeAdvanceLoc4(offset, buffer, pos);
        }
    }

    public int writeAdvanceLoc0(byte offset, byte[] buffer, int pos) {
        byte op = advanceLoc0Op(offset);
        if (buffer == null) {
            return pos + putByte(op, scratch, 0);
        } else {
            return putByte(op, buffer, pos);
        }
    }

    public int writeAdvanceLoc1(byte offset, byte[] buffer, int p) {
        int pos = p;
        byte op = DW_CFA_advance_loc1;
        if (buffer == null) {
            pos += putByte(op, scratch, 0);
            return pos + putByte(offset, scratch, 0);
        } else {
            pos = putByte(op, buffer, pos);
            return putByte(offset, buffer, pos);
        }
    }

    public int writeAdvanceLoc2(short offset, byte[] buffer, int p) {
        byte op = DW_CFA_advance_loc2;
        int pos = p;
        if (buffer == null) {
            pos += putByte(op, scratch, 0);
            return pos + putShort(offset, scratch, 0);
        } else {
            pos = putByte(op, buffer, pos);
            return putShort(offset, buffer, pos);
        }
    }

    public int writeAdvanceLoc4(int offset, byte[] buffer, int p) {
        byte op = DW_CFA_advance_loc4;
        int pos = p;
        if (buffer == null) {
            pos += putByte(op, scratch, 0);
            return pos + putInt(offset, scratch, 0);
        } else {
            pos = putByte(op, buffer, pos);
            return putInt(offset, buffer, pos);
        }
    }

    public int writeOffset(int register, int offset, byte[] buffer, int p) {
        byte op = offsetOp(register);
        int pos = p;
        if (buffer == null) {
            pos += putByte(op, scratch, 0);
            return pos + putULEB(offset, scratch, 0);
        } else {
            pos = putByte(op, buffer, pos);
            return putULEB(offset, buffer, pos);
        }
    }

    public int writeRegister(int savedReg, int savedToReg, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            pos += putByte(DW_CFA_register, scratch, 0);
            pos += putULEB(savedReg, scratch, 0);
            return pos + putULEB(savedToReg, scratch, 0);
        } else {
            pos = putByte(DW_CFA_register, buffer, pos);
            pos = putULEB(savedReg, buffer, pos);
            return putULEB(savedToReg, buffer, pos);
        }
    }

    public abstract int getPCIdx();

    public abstract int getSPIdx();

    public abstract int writeInitialInstructions(byte[] buffer, int pos);

    @Override
    protected void debug(String format, Object... args) {
        super.debug(format, args);
    }

    /**
     * debug_frame section content depends on debug_line section content and offset.
     */
    public static final String TARGET_SECTION_NAME = DW_LINE_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    public final LayoutDecision.Kind[] targetSectionKinds = {
            LayoutDecision.Kind.CONTENT,
            LayoutDecision.Kind.OFFSET
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }

    private static byte offsetOp(int register) {
        assert (register >> 6) == 0;
        return (byte) ((DW_CFA_offset << 6) | register);
    }

    private static byte advanceLoc0Op(int offset) {
        assert (offset >= 0 && offset <= 0x3f);
        return (byte) ((DW_CFA_advance_loc << 6) | offset);
    }
}
