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

import java.util.LinkedList;

import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_ABBREV_CODE_compile_unit;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_ABBREV_CODE_subprogram;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_ABBREV_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_FLAG_true;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_INFO_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_LANG_Java;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_VERSION_2;

/**
 * Section generator for debug_info section.
 */
public class DwarfInfoSectionImpl extends DwarfSectionImpl {
    /**
     * an info header section always contains a fixed number of bytes.
     */
    private static final int DW_DIE_HEADER_SIZE = 11;

    public DwarfInfoSectionImpl(DwarfSections dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_INFO_SECTION_NAME;
    }

    @Override
    public void createContent() {
        /*
         * we need a single level 0 DIE for each compilation unit (CU). Each CU's Level 0 DIE is
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
         * a DIE is a recursively defined structure. it starts with a code for the associated abbrev
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
         * note that a null_DIE looks like
         *
         * <ul>
         *
         * <li><code>LEB128 abbrev_code ....... == 0</code>
         *
         * </ul>
         *
         * i.e. it also looks like a null_value
         */

        byte[] buffer = null;
        int pos = 0;

        for (ClassEntry classEntry : getPrimaryClasses()) {
            int lengthPos = pos;
            pos = writeCUHeader(buffer, pos);
            assert pos == lengthPos + DW_DIE_HEADER_SIZE;
            pos = writeCU(classEntry, buffer, pos);
            /*
             * no need to backpatch length at lengthPos
             */
        }
        buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent() {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        checkDebug(pos);

        debug("  [0x%08x] DEBUG_INFO\n", pos);
        debug("  [0x%08x] size = 0x%08x\n", pos, size);
        for (ClassEntry classEntry : getPrimaryClasses()) {
            /*
             * save the offset of this file's CU so it can be used when writing the aranges section
             */
            classEntry.setCUIndex(pos);
            int lengthPos = pos;
            pos = writeCUHeader(buffer, pos);
            debug("  [0x%08x] Compilation Unit\n", pos, size);
            assert pos == lengthPos + DW_DIE_HEADER_SIZE;
            pos = writeCU(classEntry, buffer, pos);
            /*
             * backpatch length at lengthPos (excluding length field)
             */
            patchLength(lengthPos, buffer, pos);
        }
        assert pos == size;
    }

    public int writeCUHeader(byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            /* CU length */
            pos += putInt(0, scratch, 0);
            /* dwarf version */
            pos += putShort(DW_VERSION_2, scratch, 0);
            /* abbrev offset */
            pos += putInt(0, scratch, 0);
            /* address size */
            return pos + putByte((byte) 8, scratch, 0);
        } else {
            /* CU length */
            pos = putInt(0, buffer, pos);
            /* dwarf version */
            pos = putShort(DW_VERSION_2, buffer, pos);
            /* abbrev offset */
            pos = putInt(0, buffer, pos);
            /* address size */
            return putByte((byte) 8, buffer, pos);
        }
    }

    public int writeCU(ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
        debug("  [0x%08x] <0> Abbrev Number %d\n", pos, DW_ABBREV_CODE_compile_unit);
        pos = writeAbbrevCode(DW_ABBREV_CODE_compile_unit, buffer, pos);
        debug("  [0x%08x]     language  %s\n", pos, "DW_LANG_Java");
        pos = writeAttrData1(DW_LANG_Java, buffer, pos);
        debug("  [0x%08x]     name  0x%x (%s)\n", pos, debugStringIndex(classEntry.getFileName()), classEntry.getFileName());
        pos = writeAttrStrp(classEntry.getFileName(), buffer, pos);
        debug("  [0x%08x]     low_pc  0x%08x\n", pos, classPrimaryEntries.getFirst().getPrimary().getLo());
        pos = writeAttrAddress(classPrimaryEntries.getFirst().getPrimary().getLo(), buffer, pos);
        debug("  [0x%08x]     hi_pc  0x%08x\n", pos, classPrimaryEntries.getLast().getPrimary().getHi());
        pos = writeAttrAddress(classPrimaryEntries.getLast().getPrimary().getHi(), buffer, pos);
        debug("  [0x%08x]     stmt_list  0x%08x\n", pos, classEntry.getLineIndex());
        pos = writeAttrData4(classEntry.getLineIndex(), buffer, pos);
        for (PrimaryEntry primaryEntry : classPrimaryEntries) {
            pos = writePrimary(primaryEntry, buffer, pos);
        }
        /*
         * write a terminating null attribute for the the level 2 primaries
         */
        return writeAttrNull(buffer, pos);

    }

    public int writePrimary(PrimaryEntry primaryEntry, byte[] buffer, int p) {
        int pos = p;
        Range primary = primaryEntry.getPrimary();
        debug("  [0x%08x] <1> Abbrev Number  %d\n", pos, DW_ABBREV_CODE_subprogram);
        pos = writeAbbrevCode(DW_ABBREV_CODE_subprogram, buffer, pos);
        debug("  [0x%08x]     name  0x%X (%s)\n", pos, debugStringIndex(primary.getFullMethodName()), primary.getFullMethodName());
        pos = writeAttrStrp(primary.getFullMethodName(), buffer, pos);
        debug("  [0x%08x]     low_pc  0x%08x\n", pos, primary.getLo());
        pos = writeAttrAddress(primary.getLo(), buffer, pos);
        debug("  [0x%08x]     high_pc  0x%08x\n", pos, primary.getHi());
        pos = writeAttrAddress(primary.getHi(), buffer, pos);
        /*
         * need to pass true only if method is public
         */
        debug("  [0x%08x]     external  true\n", pos);
        return writeFlag(DW_FLAG_true, buffer, pos);
    }

    public int writeAttrStrp(String value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + putInt(0, scratch, 0);
        } else {
            int idx = debugStringIndex(value);
            return putInt(idx, buffer, pos);
        }
    }

    public int writeAttrString(String value, byte[] buffer, int p) {
        int pos = p;
        if (buffer == null) {
            return pos + value.length() + 1;
        } else {
            return putAsciiStringBytes(value, buffer, pos);
        }
    }

    @Override
    protected void debug(String format, Object... args) {
        if (((int) args[0] - debugBase) < 0x100000) {
            super.debug(format, args);
        } else if (format.startsWith("  [0x%08x] primary file")) {
            super.debug(format, args);
        }
    }

    /**
     * debug_info section content depends on abbrev section content and offset.
     */
    public static final String TARGET_SECTION_NAME = DW_ABBREV_SECTION_NAME;

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
}
