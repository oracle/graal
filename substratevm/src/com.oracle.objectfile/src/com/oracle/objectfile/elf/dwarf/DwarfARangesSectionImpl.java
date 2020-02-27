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
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.Range;

import java.util.LinkedList;
import java.util.Map;

import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_ARANGES_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_INFO_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfSections.DW_VERSION_2;

/**
 * Section generator for debug_aranges section.
 */
public class DwarfARangesSectionImpl extends DwarfSectionImpl {
    private static final int DW_AR_HEADER_SIZE = 12;
    private static final int DW_AR_HEADER_PAD_SIZE = 4; // align up to 2 * address size

    public DwarfARangesSectionImpl(DwarfSections dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_ARANGES_SECTION_NAME;
    }

    @Override
    public void createContent() {
        int pos = 0;
        /*
         * we need an entry for each compilation unit
         *
         * <ul>
         *
         * <li><code>uint32 length ............ in bytes (not counting these 4 bytes)</code>
         *
         * <li><code>uint16 dwarf_version ..... always 2</code>
         *
         * <li><code>uint32 info_offset ....... offset of compilation unit on debug_info</code>
         *
         * <li><code>uint8 address_size ....... always 8</code>
         *
         * <li><code>uint8 segment_desc_size .. ???</code>
         *
         * </ul>
         *
         * i.e. 12 bytes followed by padding aligning up to 2 * address size
         *
         * <ul>
         *
         * <li><code>uint8 pad[4]</code>
         *
         * </ul>
         *
         * followed by N + 1 times
         *
         * <ul> <li><code>uint64 lo ................ lo address of range</code>
         *
         * <li><code>uint64 length ............ number of bytes in range</code>
         *
         * </ul>
         *
         * where N is the number of ranges belonging to the compilation unit and the last range
         * contains two zeroes
         */

        for (ClassEntry classEntry : getPrimaryClasses()) {
            pos += DW_AR_HEADER_SIZE;
            /*
             * align to 2 * address size
             */
            pos += DW_AR_HEADER_PAD_SIZE;
            pos += classEntry.getPrimaryEntries().size() * 2 * 8;
            pos += 2 * 8;
        }
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public byte[] getOrDecideContent(Map<ObjectFile.Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        ObjectFile.Element textElement = getElement().getOwner().elementForName(".text");
        LayoutDecisionMap decisionMap = alreadyDecided.get(textElement);
        if (decisionMap != null) {
            Object valueObj = decisionMap.getDecidedValue(LayoutDecision.Kind.VADDR);
            if (valueObj != null && valueObj instanceof Number) {
                /*
                 * this may not be the final vaddr for the text segment but it will be close enough
                 * to make debug easier i.e. to within a 4k page or two
                 */
                debugTextBase = ((Number) valueObj).longValue();
            }
        }
        return super.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public void writeContent() {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        checkDebug(pos);

        debug("  [0x%08x] DEBUG_ARANGES\n", pos);
        for (ClassEntry classEntry : getPrimaryClasses()) {
            int lastpos = pos;
            int length = DW_AR_HEADER_SIZE + DW_AR_HEADER_PAD_SIZE - 4;
            int cuIndex = classEntry.getCUIndex();
            LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
            /*
             * add room for each entry into length count
             */
            length += classPrimaryEntries.size() * 2 * 8;
            length += 2 * 8;
            debug("  [0x%08x] %s CU %d length 0x%x\n", pos, classEntry.getFileName(), cuIndex, length);
            pos = putInt(length, buffer, pos);
            /* dwarf version is always 2 */
            pos = putShort(DW_VERSION_2, buffer, pos);
            pos = putInt(cuIndex, buffer, pos);
            /* address size is always 8 */
            pos = putByte((byte) 8, buffer, pos);
            /* segment size is always 0 */
            pos = putByte((byte) 0, buffer, pos);
            assert (pos - lastpos) == DW_AR_HEADER_SIZE;
            /*
             * align to 2 * address size
             */
            for (int i = 0; i < DW_AR_HEADER_PAD_SIZE; i++) {
                pos = putByte((byte) 0, buffer, pos);
            }
            debug("  [0x%08x] Address          Length           Name\n", pos);
            for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                Range primary = classPrimaryEntry.getPrimary();
                debug("  [0x%08x] %016x %016x %s\n", pos, debugTextBase + primary.getLo(), primary.getHi() - primary.getLo(), primary.getFullMethodName());
                pos = putRelocatableCodeOffset(primary.getLo(), buffer, pos);
                pos = putLong(primary.getHi() - primary.getLo(), buffer, pos);
            }
            pos = putLong(0, buffer, pos);
            pos = putLong(0, buffer, pos);
        }

        assert pos == size;
    }

    @Override
    protected void debug(String format, Object... args) {
        super.debug(format, args);
    }

    /*
     * debug_aranges section content depends on debug_info section content and offset
     */
    public static final String TARGET_SECTION_NAME = DW_INFO_SECTION_NAME;

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
