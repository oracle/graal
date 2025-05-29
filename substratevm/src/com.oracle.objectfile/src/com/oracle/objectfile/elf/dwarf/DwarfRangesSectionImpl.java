/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import java.util.Map;

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfRangeListEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;

import jdk.graal.compiler.debug.DebugContext;

public class DwarfRangesSectionImpl extends DwarfSectionImpl {
    public DwarfRangesSectionImpl(DwarfDebugInfo dwarfSections) {
        // debug_rnglists section depends on debug_aranges section
        super(dwarfSections, DwarfSectionName.DW_RNGLISTS_SECTION, DwarfSectionName.DW_ARANGES_SECTION);
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        byte[] buffer = null;
        int len = generateContent(null, buffer);

        buffer = new byte[len];
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
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);
        log(context, "  [0x%08x] DEBUG_RANGES", pos);
        log(context, "  [0x%08x] size = 0x%08x", pos, size);

        pos = generateContent(context, buffer);
        assert pos == size;
    }

    private int generateContent(DebugContext context, byte[] buffer) {
        int pos = 0;

        int lengthPos = pos;
        pos = writeRangeListsHeader(buffer, pos);

        pos = writeRangeLists(context, buffer, pos);

        patchLength(lengthPos, buffer, pos);
        return pos;
    }

    private int writeRangeListsHeader(byte[] buffer, int p) {
        int pos = p;
        /* Rnglists length. */
        pos = writeInt(0, buffer, pos);
        /* DWARF version. */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_5, buffer, pos);
        /* Address size. */
        pos = writeByte((byte) 8, buffer, pos);
        /* Segment selector size. */
        pos = writeByte((byte) 0, buffer, pos);
        /*
         * Offset entry count. Not needed because we just use ranges in top level compile unit DIEs
         */
        return writeInt(0, buffer, pos);
    }

    private int writeRangeLists(DebugContext context, byte[] buffer, int p) {
        Cursor entryCursor = new Cursor(p);

        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            int pos = entryCursor.get();
            setCodeRangesIndex(classEntry, pos);
            /* Write range list for a class */
            entryCursor.set(writeRangeList(context, classEntry, buffer, pos));
        });
        return entryCursor.get();
    }

    private int writeRangeList(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] ranges start for class %s", pos, classEntry.getTypeName());
        int base = classEntry.compiledEntriesBase();
        log(context, "  [0x%08x] base 0x%x", pos, base);
        pos = writeRangeListEntry(DwarfRangeListEntry.DW_RLE_base_address, buffer, pos);
        pos = writeRelocatableCodeOffset(base, buffer, pos);

        Cursor cursor = new Cursor(pos);
        classEntry.compiledEntries().forEach(compiledMethodEntry -> {
            cursor.set(writeRangeListEntry(DwarfRangeListEntry.DW_RLE_offset_pair, buffer, cursor.get()));

            int loOffset = compiledMethodEntry.getPrimary().getLo() - base;
            int hiOffset = compiledMethodEntry.getPrimary().getHi() - base;
            log(context, "  [0x%08x] lo 0x%x (%s)", cursor.get(), loOffset, compiledMethodEntry.getPrimary().getFullMethodNameWithParams());
            cursor.set(writeULEB(loOffset, buffer, cursor.get()));
            log(context, "  [0x%08x] hi 0x%x", cursor.get(), hiOffset);
            cursor.set(writeULEB(hiOffset, buffer, cursor.get()));
        });
        pos = cursor.get();
        // write end marker
        pos = writeRangeListEntry(DwarfRangeListEntry.DW_RLE_end_of_list, buffer, pos);
        log(context, "  [0x%08x] ranges size 0x%x  for class %s", pos, pos - p, classEntry.getTypeName());
        return pos;
    }
}
