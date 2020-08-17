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
import com.oracle.objectfile.debugentry.Range;
import com.oracle.objectfile.debuginfo.DebugInfoProvider;
import org.graalvm.compiler.debug.DebugContext;

import java.util.List;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_CIE_id;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_CIE_version;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_advance_loc;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_advance_loc1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_advance_loc2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_advance_loc4;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_def_cfa;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_def_cfa_offset;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_nop;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_offset;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_register;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_CFA_restore;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FRAME_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_LINE_SECTION_NAME;

/**
 * Section generic generator for debug_frame section.
 */
public abstract class DwarfFrameSectionImpl extends DwarfSectionImpl {

    private static final int PADDING_NOPS_ALIGNMENT = 8;

    public DwarfFrameSectionImpl(DwarfDebugInfo dwarfSections) {
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
         * The frame section contains one CIE at offset 0 followed by an FIE for each method.
         */
        pos = writeCIE(null, pos);
        pos = writeMethodFrames(null, pos);

        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        /*
         * There are entries for the prologue region where the stack is being built, the method body
         * region(s) where the code executes with a fixed size frame and the epilogue region(s)
         * where the stack is torn down.
         */
        pos = writeCIE(buffer, pos);
        pos = writeMethodFrames(buffer, pos);

        if (pos != size) {
            System.out.format("pos = 0x%x  size = 0x%x", pos, size);
        }
        assert pos == size;
    }

    private int writeCIE(byte[] buffer, int p) {
        /*
         * We only need a vanilla CIE with default fields because we have to have at least one the
         * layout is:
         *
         * <ul>
         *
         * <li><code>uint32 : length ............... length of remaining fields in this CIE</code>
         *
         * <li><code>uint32 : CIE_id ................ unique id for CIE == 0xffffff</code>
         *
         * <li><code>uint8 : version ................ == 1</code>
         *
         * <li><code>uint8[] : augmentation ......... == "" so always 1 byte</code>
         *
         * <li><code>ULEB : code_alignment_factor ... == 1 (could use 4 for Aarch64)</code>
         *
         * <li><code>ULEB : data_alignment_factor ... == -8</code>
         *
         * <li><code>byte : ret_addr reg id ......... x86_64 => 16 AArch64 => 30</code>
         *
         * <li><code>byte[] : initial_instructions .. includes pad to 8-byte boundary</code>
         *
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
            pos += putByte((byte) getReturnPCIdx(), scratch, 0);
            /*
             * Write insns to set up empty frame.
             */
            pos = writeInitialInstructions(buffer, pos);
            /*
             * Pad to word alignment.
             */
            pos = writePaddingNops(buffer, pos);
            /*
             * No need to write length.
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
            pos = putByte((byte) getReturnPCIdx(), buffer, pos);
            /*
             * write insns to set up empty frame
             */
            pos = writeInitialInstructions(buffer, pos);
            /*
             * Pad to word alignment.
             */
            pos = writePaddingNops(buffer, pos);
            patchLength(lengthPos, buffer, pos);
            return pos;
        }
    }

    private int writeMethodFrames(byte[] buffer, int p) {
        int pos = p;
        /* write frames for normal methods */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
                Range range = primaryEntry.getPrimary();
                if (!range.isDeoptTarget()) {
                    long lo = range.getLo();
                    long hi = range.getHi();
                    int lengthPos = pos;
                    pos = writeFDEHeader((int) lo, (int) hi, buffer, pos);
                    pos = writeFDEs(primaryEntry.getFrameSize(), primaryEntry.getFrameSizeInfos(), buffer, pos);
                    pos = writePaddingNops(buffer, pos);
                    patchLength(lengthPos, buffer, pos);
                }
            }
        }
        /* now write frames for deopt targets */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            for (PrimaryEntry primaryEntry : classEntry.getPrimaryEntries()) {
                Range range = primaryEntry.getPrimary();
                if (range.isDeoptTarget()) {
                    long lo = range.getLo();
                    long hi = range.getHi();
                    int lengthPos = pos;
                    pos = writeFDEHeader((int) lo, (int) hi, buffer, pos);
                    pos = writeFDEs(primaryEntry.getFrameSize(), primaryEntry.getFrameSizeInfos(), buffer, pos);
                    pos = writePaddingNops(buffer, pos);
                    patchLength(lengthPos, buffer, pos);
                }
            }
        }
        return pos;
    }

    protected abstract int writeFDEs(int frameSize, List<DebugInfoProvider.DebugFrameSizeChange> frameSizeInfos, byte[] buffer, int pos);

    private int writeFDEHeader(int lo, int hi, byte[] buffer, int p) {
        /*
         * We only need a vanilla FDE header with default fields the layout is:
         *
         * <ul>
         *
         * <li><code>uint32 : length ............ length of remaining fields in this FDE</code>
         *
         * <li><code>uint32 : CIE_offset ........ always 0 i.e. identifies our only CIE
         * header</code>
         *
         * <li><code>uint64 : initial_location .. i.e. method lo address</code>
         *
         * <li><code>uint64 : address_range ..... i.e. method hi - lo</code>
         *
         * <li><code>byte[] : instructions ...... includes pad to 8-byte boundary</code>
         *
         * </ul>
         */

        int pos = p;
        if (buffer == null) {
            /* Dummy length. */
            pos += putInt(0, scratch, 0);
            /* CIE_offset */
            pos += putInt(0, scratch, 0);
            /* Initial address. */
            pos += putLong(lo, scratch, 0);
            /* Address range. */
            return pos + putLong(hi - lo, scratch, 0);
        } else {
            /* Dummy length. */
            pos = putInt(0, buffer, pos);
            /* CIE_offset */
            pos = putInt(0, buffer, pos);
            /* Initial address. */
            pos = putRelocatableCodeOffset(lo, buffer, pos);
            /* Address range. */
            return putLong(hi - lo, buffer, pos);
        }
    }

    private int writePaddingNops(byte[] buffer, int p) {
        int pos = p;
        while ((pos & (PADDING_NOPS_ALIGNMENT - 1)) != 0) {
            if (buffer == null) {
                pos++;
            } else {
                pos = putByte(DW_CFA_nop, buffer, pos);
            }
        }
        return pos;
    }

    protected int writeDefCFA(int register, int offset, byte[] buffer, int p) {
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

    protected int writeDefCFAOffset(int offset, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            pos += putByte(DW_CFA_def_cfa_offset, scratch, 0);
            return pos + putULEB(offset, scratch, 0);
        } else {
            pos = putByte(DW_CFA_def_cfa_offset, buffer, pos);
            return putULEB(offset, buffer, pos);
        }
    }

    protected int writeAdvanceLoc(int offset, byte[] buffer, int pos) {
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

    protected int writeAdvanceLoc0(byte offset, byte[] buffer, int pos) {
        byte op = advanceLoc0Op(offset);
        if (buffer == null) {
            return pos + putByte(op, scratch, 0);
        } else {
            return putByte(op, buffer, pos);
        }
    }

    protected int writeAdvanceLoc1(byte offset, byte[] buffer, int p) {
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

    protected int writeAdvanceLoc2(short offset, byte[] buffer, int p) {
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

    protected int writeAdvanceLoc4(int offset, byte[] buffer, int p) {
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

    protected int writeOffset(int register, int offset, byte[] buffer, int p) {
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

    protected int writeRestore(int register, byte[] buffer, int p) {
        byte op = restoreOp(register);
        int pos = p;
        if (buffer == null) {
            return pos + putByte(op, scratch, 0);
        } else {
            return putByte(op, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    protected int writeRegister(int savedReg, int savedToReg, byte[] buffer, int p) {
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

    protected abstract int getReturnPCIdx();

    @SuppressWarnings("unused")
    protected abstract int getSPIdx();

    protected abstract int writeInitialInstructions(byte[] buffer, int pos);

    /**
     * The debug_frame section content depends on debug_line section content and offset.
     */
    private static final String TARGET_SECTION_NAME = DW_LINE_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
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

    private static byte restoreOp(int register) {
        assert (register >> 6) == 0;
        return (byte) ((DW_CFA_restore << 6) | register);
    }

    private static byte advanceLoc0Op(int offset) {
        assert (offset >= 0 && offset <= 0x3f);
        return (byte) ((DW_CFA_advance_loc << 6) | offset);
    }
}
