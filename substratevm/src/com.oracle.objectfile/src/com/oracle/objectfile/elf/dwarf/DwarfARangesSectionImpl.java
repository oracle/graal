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
import org.graalvm.compiler.debug.DebugContext;

import java.util.LinkedList;
import java.util.Map;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_ARANGES_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_INFO_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_VERSION_2;

/**
 * Section generator for debug_aranges section.
 */
public class DwarfARangesSectionImpl extends DwarfSectionImpl {
    private static final int DW_AR_HEADER_SIZE = 12;
    private static final int DW_AR_HEADER_PAD_SIZE = 4; // align up to 2 * address size

    public DwarfARangesSectionImpl(DwarfDebugInfo dwarfSections) {
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
         * We need an entry for each compilation unit.
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
         * That is 12 bytes followed by padding aligning up to 2 * address size.
         *
         * <ul>
         *
         * <li><code>uint8 pad[4]</code>
         *
         * </ul>
         *
         * Followed by N + 1 times:
         *
         * <ul> <li><code>uint64 lo ................ lo address of range</code>
         *
         * <li><code>uint64 length ............ number of bytes in range</code>
         *
         * </ul>
         *
         * Where N is the number of ranges belonging to the compilation unit and the last range
         * contains two zeroes.
         */

        for (ClassEntry classEntry : getPrimaryClasses()) {
            pos += DW_AR_HEADER_SIZE;
            /*
             * Align to 2 * address size.
             */
            pos += DW_AR_HEADER_PAD_SIZE;
            LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
            if (classEntry.includesDeoptTarget()) {
                /* Deopt targets are in a higher address range so delay emit for them. */
                for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                    if (!classPrimaryEntry.getPrimary().isDeoptTarget()) {
                        pos += 2 * 8;
                    }
                }
            } else {
                pos += classPrimaryEntries.size() * 2 * 8;
            }
            pos += 2 * 8;
        }
        /* Now allow for deopt target ranges. */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.includesDeoptTarget()) {
                pos += DW_AR_HEADER_SIZE;
                /*
                 * Align to 2 * address size.
                 */
                pos += DW_AR_HEADER_PAD_SIZE;
                LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
                for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                    if (classPrimaryEntry.getPrimary().isDeoptTarget()) {
                        pos += 2 * 8;
                    }
                }
                pos += 2 * 8;
            }
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
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        log(context, "  [0x%08x] DEBUG_ARANGES", pos);
        for (ClassEntry classEntry : getPrimaryClasses()) {
            int lastpos = pos;
            int length = DW_AR_HEADER_SIZE + DW_AR_HEADER_PAD_SIZE - 4;
            int cuIndex = classEntry.getCUIndex();
            LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
            /*
             * Count only real methods, omitting deopt targets.
             */
            for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                Range primary = classPrimaryEntry.getPrimary();
                if (!primary.isDeoptTarget()) {
                    length += 2 * 8;
                }
            }
            /*
             * Add room for a final null entry.
             */
            length += 2 * 8;
            log(context, "  [0x%08x] %s CU %d length 0x%x", pos, classEntry.getFileName(), cuIndex, length);
            pos = putInt(length, buffer, pos);
            /* DWARF version is always 2. */
            pos = putShort(DW_VERSION_2, buffer, pos);
            pos = putInt(cuIndex, buffer, pos);
            /* Address size is always 8. */
            pos = putByte((byte) 8, buffer, pos);
            /* Segment size is always 0. */
            pos = putByte((byte) 0, buffer, pos);
            assert (pos - lastpos) == DW_AR_HEADER_SIZE;
            /*
             * Align to 2 * address size.
             */
            for (int i = 0; i < DW_AR_HEADER_PAD_SIZE; i++) {
                pos = putByte((byte) 0, buffer, pos);
            }
            log(context, "  [0x%08x] Address          Length           Name", pos);
            for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                Range primary = classPrimaryEntry.getPrimary();
                /*
                 * Emit only real methods, omitting linkage stubs.
                 */
                if (!primary.isDeoptTarget()) {
                    log(context, "  [0x%08x] %016x %016x %s", pos, debugTextBase + primary.getLo(), primary.getHi() - primary.getLo(), primary.getFullMethodName());
                    pos = putRelocatableCodeOffset(primary.getLo(), buffer, pos);
                    pos = putLong(primary.getHi() - primary.getLo(), buffer, pos);
                }
            }
            pos = putLong(0, buffer, pos);
            pos = putLong(0, buffer, pos);
        }
        /* now write ranges for deopt targets */
        for (ClassEntry classEntry : getPrimaryClasses()) {
            if (classEntry.includesDeoptTarget()) {
                int lastpos = pos;
                int length = DW_AR_HEADER_SIZE + DW_AR_HEADER_PAD_SIZE - 4;
                int cuIndex = classEntry.getDeoptCUIndex();
                LinkedList<PrimaryEntry> classPrimaryEntries = classEntry.getPrimaryEntries();
                /*
                 * Count only linkage stubs.
                 */
                for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                    Range primary = classPrimaryEntry.getPrimary();
                    if (primary.isDeoptTarget()) {
                        length += 2 * 8;
                    }
                }
                /* we must have seen at least one stub */
                assert length > DW_AR_HEADER_SIZE + DW_AR_HEADER_PAD_SIZE - 4;
                /*
                 * Add room for a final null entry.
                 */
                length += 2 * 8;
                log(context, "  [0x%08x] %s CU linkage stubs %d length 0x%x", pos, classEntry.getFileName(), cuIndex, length);
                pos = putInt(length, buffer, pos);
                /* DWARF version is always 2. */
                pos = putShort(DW_VERSION_2, buffer, pos);
                pos = putInt(cuIndex, buffer, pos);
                /* Address size is always 8. */
                pos = putByte((byte) 8, buffer, pos);
                /* Segment size is always 0. */
                pos = putByte((byte) 0, buffer, pos);
                assert (pos - lastpos) == DW_AR_HEADER_SIZE;
                /*
                 * Align to 2 * address size.
                 */
                for (int i = 0; i < DW_AR_HEADER_PAD_SIZE; i++) {
                    pos = putByte((byte) 0, buffer, pos);
                }
                log(context, "  [0x%08x] Address          Length           Name", pos);
                for (PrimaryEntry classPrimaryEntry : classPrimaryEntries) {
                    Range primary = classPrimaryEntry.getPrimary();
                    /*
                     * Emit only linkage stubs.
                     */
                    if (primary.isDeoptTarget()) {
                        log(context, "  [0x%08x] %016x %016x %s", pos, debugTextBase + primary.getLo(), primary.getHi() - primary.getLo(), primary.getFullMethodName());
                        pos = putRelocatableCodeOffset(primary.getLo(), buffer, pos);
                        pos = putLong(primary.getHi() - primary.getLo(), buffer, pos);
                    }
                }
                pos = putLong(0, buffer, pos);
                pos = putLong(0, buffer, pos);
            }
        }
        assert pos == size;
    }

    /*
     * The debug_aranges section content depends on debug_info section content and offset.
     */
    private static final String TARGET_SECTION_NAME = DW_INFO_SECTION_NAME;

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
