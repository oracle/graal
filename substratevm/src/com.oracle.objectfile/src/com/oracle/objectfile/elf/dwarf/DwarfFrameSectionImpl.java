/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.FrameSizeChangeEntry;
import com.oracle.objectfile.debugentry.range.Range;
import com.oracle.objectfile.elf.dwarf.constants.DwarfFrameValue;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;

import jdk.graal.compiler.debug.DebugContext;

/**
 * Section generic generator for debug_frame section.
 */
public abstract class DwarfFrameSectionImpl extends DwarfSectionImpl {

    private static final int PADDING_NOPS_ALIGNMENT = 8;

    private static final int CFA_CIE_id_default = -1;

    public DwarfFrameSectionImpl(DwarfDebugInfo dwarfSections) {
        // debug_frame section depends on debug_line_str section
        super(dwarfSections, DwarfSectionName.DW_FRAME_SECTION, DwarfSectionName.DW_LINE_STR_SECTION);
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        int pos = 0;

        /*
         * The frame section contains one CIE at offset 0 followed by an FIE for each compiled
         * method.
         */
        pos = writeCIE(null, pos);
        pos = writeMethodFrames(null, pos);

        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context);

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
         * <li><code>uint32 : CIE_id ................ unique id for CIE == 0xffffffff</code>
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
        int lengthPos = pos;
        pos = writeInt(0, buffer, pos);
        pos = writeInt(CFA_CIE_id_default, buffer, pos);
        pos = writeCIEVersion(buffer, pos);
        pos = writeByte((byte) 0, buffer, pos);
        pos = writeULEB(1, buffer, pos);
        pos = writeSLEB(-8, buffer, pos);
        pos = writeByte((byte) getReturnPCIdx(), buffer, pos);
        /*
         * Write insns to set up empty frame.
         */
        pos = writeInitialInstructions(buffer, pos);
        /*
         * Pad to word alignment.
         */
        pos = writePaddingNops(buffer, pos);
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeCIEVersion(byte[] buffer, int pos) {
        return writeByte(DwarfFrameValue.DW_CFA_CIE_version.value(), buffer, pos);
    }

    private int writeMethodFrame(CompiledMethodEntry compiledEntry, byte[] buffer, int p) {
        int pos = p;
        int lengthPos = pos;
        Range range = compiledEntry.primary();
        long lo = range.getLo();
        long hi = range.getHi();
        pos = writeFDEHeader(lo, hi, buffer, pos);
        pos = writeFDEs(compiledEntry.frameSize(), compiledEntry.frameSizeInfos(), buffer, pos);
        pos = writePaddingNops(buffer, pos);
        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeMethodFrames(byte[] buffer, int p) {
        int pos = p;
        for (CompiledMethodEntry compiledMethod : getCompiledMethods()) {
            pos = writeMethodFrame(compiledMethod, buffer, pos);
        }
        return pos;
    }

    protected abstract int writeFDEs(int frameSize, List<FrameSizeChangeEntry> frameSizeInfos, byte[] buffer, int pos);

    private int writeFDEHeader(long lo, long hi, byte[] buffer, int p) {
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
        /* Dummy length. */
        pos = writeInt(0, buffer, pos);
        /* CIE_offset */
        pos = writeInt(0, buffer, pos);
        /* Initial address. */
        pos = writeCodeOffset(lo, buffer, pos);
        /* Address range. */
        return writeLong(hi - lo, buffer, pos);
    }

    private int writePaddingNops(byte[] buffer, int p) {
        int pos = p;
        while ((pos & (PADDING_NOPS_ALIGNMENT - 1)) != 0) {
            pos = writeByte(DwarfFrameValue.DW_CFA_nop.value(), buffer, pos);
        }
        return pos;
    }

    protected int writeDefCFA(int register, int offset, byte[] buffer, int p) {
        int pos = p;
        pos = writeByte(DwarfFrameValue.DW_CFA_def_cfa.value(), buffer, pos);
        pos = writeULEB(register, buffer, pos);
        return writeULEB(offset, buffer, pos);
    }

    protected int writeDefCFAOffset(int offset, byte[] buffer, int p) {
        int pos = p;
        byte op = DwarfFrameValue.DW_CFA_def_cfa_offset.value();
        pos = writeByte(op, buffer, pos);
        return writeULEB(offset, buffer, pos);
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
        return writeByte(op, buffer, pos);
    }

    protected int writeAdvanceLoc1(byte offset, byte[] buffer, int p) {
        int pos = p;
        byte op = DwarfFrameValue.DW_CFA_advance_loc1.value();
        pos = writeByte(op, buffer, pos);
        return writeByte(offset, buffer, pos);
    }

    protected int writeAdvanceLoc2(short offset, byte[] buffer, int p) {
        byte op = DwarfFrameValue.DW_CFA_advance_loc2.value();
        int pos = p;
        pos = writeByte(op, buffer, pos);
        return writeShort(offset, buffer, pos);
    }

    protected int writeAdvanceLoc4(int offset, byte[] buffer, int p) {
        byte op = DwarfFrameValue.DW_CFA_advance_loc4.value();
        int pos = p;
        pos = writeByte(op, buffer, pos);
        return writeInt(offset, buffer, pos);
    }

    protected int writeOffset(int register, int offset, byte[] buffer, int p) {
        byte op = offsetOp(register);
        int pos = p;
        pos = writeByte(op, buffer, pos);
        return writeULEB(offset, buffer, pos);
    }

    protected int writeRestore(int register, byte[] buffer, int p) {
        byte op = restoreOp(register);
        return writeByte(op, buffer, p);
    }

    @SuppressWarnings("unused")
    protected int writeRegister(int savedReg, int savedToReg, byte[] buffer, int p) {
        int pos = p;
        byte op = DwarfFrameValue.DW_CFA_register.value();
        pos = writeByte(op, buffer, pos);
        pos = writeULEB(savedReg, buffer, pos);
        return writeULEB(savedToReg, buffer, pos);
    }

    protected abstract int getReturnPCIdx();

    @SuppressWarnings("unused")
    protected abstract int getSPIdx();

    protected abstract int writeInitialInstructions(byte[] buffer, int pos);

    private static byte offsetOp(int register) {
        byte op = DwarfFrameValue.DW_CFA_offset.value();
        return encodeOp(op, register);
    }

    private static byte restoreOp(int register) {
        byte op = DwarfFrameValue.DW_CFA_restore.value();
        return encodeOp(op, register);
    }

    private static byte advanceLoc0Op(int offset) {
        byte op = DwarfFrameValue.DW_CFA_advance_loc.value();
        return encodeOp(op, offset);
    }

    private static byte encodeOp(byte op, int value) {
        assert (value >> 6) == 0;
        return (byte) ((op << 6) | value);
    }
}
