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
import org.graalvm.compiler.debug.DebugContext;

import java.util.LinkedList;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_compile_unit_1;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_compile_unit_2;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_CODE_subprogram;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ABBREV_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_FLAG_true;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_INFO_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_LANG_Java;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_VERSION_2;

/**
 * Section generator for debug_info section.
 */
public class DwarfInfoSectionImpl extends DwarfSectionImpl {
    /**
     * an info header section always contains a fixed number of bytes.
     */
    private static final int DW_DIE_HEADER_SIZE = 11;

    public DwarfInfoSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_INFO_SECTION_NAME;
    }

    @Override
    public void createContent() {
        /*
         * We need a single level 0 DIE for each compilation unit (CU). Each CU's Level 0 DIE is
         * preceded by a fixed header and terminated by a null DIE:
         *
         * <ul>
         *
         * <li><code>uint32 length ......... excluding this length field</code>
         *
         * <li><code>uint16 dwarf_version .. always 2 ??</code>
         *
         * <li><code>uint32 abbrev offset .. always 0 ??</code>
         *
         * <li><code>uint8 address_size .... always 8</code>
         *
         * <li><code>DIE* .................. sequence of top-level and nested child entries</code>
         *
         * <li><code>null_DIE .............. == 0</code>
         *
         * </ul>
         *
         * A DIE is a recursively defined structure. it starts with a code for the associated abbrev
         * entry followed by a series of attribute values, as determined by the entry, terminated by
         * a null value and followed by zero or more child DIEs (zero iff has_children ==
         * no_children).
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code != 0 .. non-zero value indexes tag + attr layout of
         * DIE</code>
         *
         * <li><code>attribute_value* ......... value sequence as determined by abbrev entry</code>
         *
         * <li><code>DIE* ..................... sequence of child DIEs (if appropriate)</code>
         * <li><code>
         *
         * <li><code>null_value ............... == 0</code>
         *
         * </ul>
         *
         * Note that a null_DIE looks like:
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code ....... == 0</code>
         *
         * </ul>
         *
         * i.e. it also looks like a null_value.
         */

        byte[] buffer = null;
        int pos = 0;

        /* CUs for normal methods */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            int lengthPos = pos;
            pos = writeCUHeader(buffer, pos);
            assert pos == lengthPos + DW_DIE_HEADER_SIZE;
            pos = writeCU(null, classEntry, false, buffer, pos);
            /*
             * No need to backpatch length at lengthPos.
             */
        }
        /* CUs for deopt targets */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.includesDeoptTarget()) {
                int lengthPos = pos;
                pos = writeCUHeader(buffer, pos);
                assert pos == lengthPos + DW_DIE_HEADER_SIZE;
                pos = writeCU(null, classEntry, true, buffer, pos);
                /*
                 * No need to backpatch length at lengthPos.
                 */
            }
        }
        buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        log(context, "  [0x%08x] DEBUG_INFO", pos);
        log(context, "  [0x%08x] size = 0x%08x", pos, size);
        /* write CUs for normal methods */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            /*
             * Save the offset of this file's CU so it can be used when writing the aranges section.
             */
            classEntry.setCUIndex(pos);
            int lengthPos = pos;
            pos = writeCUHeader(buffer, pos);
            log(context, "  [0x%08x] Compilation Unit", pos, size);
            assert pos == lengthPos + DW_DIE_HEADER_SIZE;
            pos = writeCU(context, classEntry, false, buffer, pos);
            /*
             * Backpatch length at lengthPos (excluding length field).
             */
            patchLength(lengthPos, buffer, pos);
        }
        /* write CUs for deopt targets */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.includesDeoptTarget()) {
                /*
                 * Save the offset of this file's CU so it can be used when writing the aranges
                 * section.
                 */
                classEntry.setDeoptCUIndex(pos);
                int lengthPos = pos;
                pos = writeCUHeader(buffer, pos);
                log(context, "  [0x%08x] Compilation Unit (deopt targets)", pos, size);
                assert pos == lengthPos + DW_DIE_HEADER_SIZE;
                pos = writeCU(context, classEntry, true, buffer, pos);
                /*
                 * Backpatch length at lengthPos (excluding length field).
                 */
                patchLength(lengthPos, buffer, pos);
            }
        }
        assert pos == size;
    }

    private int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            /* CU length. */
            pos += putInt(0, scratch, 0);
            /* DWARF version. */
            pos += putShort(DW_VERSION_2, scratch, 0);
            /* Abbrev offset. */
            pos += putInt(0, scratch, 0);
            /* Address size. */
            return pos + putByte((byte) 8, scratch, 0);
        } else {
            /* CU length. */
            pos = putInt(0, buffer, pos);
            /* DWARF version. */
            pos = putShort(DW_VERSION_2, buffer, pos);
            /* Abbrev offset. */
            pos = putInt(0, buffer, pos);
            /* Address size. */
            return putByte((byte) 8, buffer, pos);
        }
    }

    private static int findLo(LinkedList<PrimaryEntry> classPrimaryEntries, boolean isDeoptTargetCU) {
        if (!isDeoptTargetCU) {
            /* First entry is the one we want. */
            return classPrimaryEntries.getFirst().getPrimary().getLo();
        } else {
            /* Need the first entry which is a deopt target. */
            for (PrimaryEntry primaryEntry : classPrimaryEntries) {
                Range range = primaryEntry.getPrimary();
                if (range.isDeoptTarget()) {
                    return range.getLo();
                }
            }
        }
        // we should never get here
        assert false;
        return 0;
    }

    private static int findHi(LinkedList<PrimaryEntry> classPrimaryEntries, boolean includesDeoptTarget, boolean isDeoptTargetCU) {
        if (isDeoptTargetCU || !includesDeoptTarget) {
            /* Either way the last entry is the one we want. */
            return classPrimaryEntries.getLast().getPrimary().getHi();
        } else {
            /* Need the last entry which is not a deopt target. */
            int hi = 0;
            for (PrimaryEntry primaryEntry : classPrimaryEntries) {
                Range range = primaryEntry.getPrimary();
                if (!range.isDeoptTarget()) {
                    hi = range.getHi();
                } else {
                    return hi;
                }
            }
        }
        // should never get here
        assert false;
        return 0;
    }

    private int writeCU(DebugContext context, ClassEntry classEntry, boolean isDeoptTargetCU, byte[] buffer, int p) {
        int pos = p;
        LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        int lineIndex = classEntry.getLineIndex();
        int abbrevCode = (lineIndex >= 0 ? DW_ABBREV_CODE_compile_unit_1 : DW_ABBREV_CODE_compile_unit_2);
        log(context, "  [0x%08x] <0> Abbrev Number %d", pos, abbrevCode);
        pos = writeAbbrevCode(abbrevCode, buffer, pos);
        log(context, "  [0x%08x]     language  %s", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);
        log(context, "  [0x%08x]     name  0x%x (%s)", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
        pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
        String compilationDirectory = classEntry.getCachePath();
        log(context, "  [0x%08x]     comp_dir  0x%x (%s)", pos, debugStringIndex(compilationDirectory), compilationDirectory);
        pos = writeAttrStrp(compilationDirectory, buffer, pos);
        int lo = findLo(classPrimaryEntries, isDeoptTargetCU);
        int hi = findHi(classPrimaryEntries, classEntry.includesDeoptTarget(), isDeoptTargetCU);
        log(context, "  [0x%08x]     lo_pc  0x%08x", pos, lo);
        pos = writeAttrAddress(lo, buffer, pos);
        log(context, "  [0x%08x]     hi_pc  0x%08x", pos, hi);
        pos = writeAttrAddress(hi, buffer, pos);
        if (abbrevCode == DW_ABBREV_CODE_compile_unit_1) {
            log(context, "  [0x%08x]     stmt_list  0x%08x", pos, lineIndex);
            pos = writeAttrData4(lineIndex, buffer, pos);
        }
        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            Range range = primaryEntry.getPrimary();
            if (isDeoptTargetCU == range.isDeoptTarget()) {
                pos = writePrimary(context, range, buffer, pos);
            }
        }
        /*
         * Write a terminating null attribute for the the level 2 primaries.
         */
        return writeAttrNull(buffer, pos);

    }

    private int writePrimary(DebugContext context, Range range, byte[] buffer, int p) {
        int pos = p;
        verboseLog(context, "  [0x%08x] <1> Abbrev Number  %d", pos, DW_ABBREV_CODE_subprogram);
        pos = writeAbbrevCode(DW_ABBREV_CODE_subprogram, buffer, pos);
        verboseLog(context, "  [0x%08x]     name  0x%X (%s)", pos, debugStringIndex(range.getFullMethodName()), range.getFullMethodName());
        pos = writeAttrStrp(range.getFullMethodName(), buffer, pos);
        verboseLog(context, "  [0x%08x]     lo_pc  0x%08x", pos, range.getLo());
        pos = writeAttrAddress(range.getLo(), buffer, pos);
        verboseLog(context, "  [0x%08x]     hi_pc  0x%08x", pos, range.getHi());
        pos = writeAttrAddress(range.getHi(), buffer, pos);
        /*
         * Need to pass true only if method is public.
         */
        verboseLog(context, "  [0x%08x]     external  true", pos);
        return writeFlag(DW_FLAG_true, buffer, pos);
    }

    private int writeAttrStrp(String value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + putInt(0, scratch, 0);
        } else {
            int idx = debugStringIndex(value);
            return putInt(idx, buffer, pos);
        }
    }

    @SuppressWarnings("unused")
    public int writeAttrString(String value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + value.length() + 1;
        } else {
            return putAsciiStringBytes(value, buffer, pos);
        }
    }

    /**
     * The debug_info section content depends on abbrev section content and offset.
     */
    private static final String TARGET_SECTION_NAME = DW_ABBREV_SECTION_NAME;

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
}
