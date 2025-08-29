/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
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
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context);
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
        int pos = p;

        for (ClassEntry classEntry : getInstanceClassesWithCompilation()) {
            setCodeRangesIndex(classEntry, pos);
            /* Write range list for a class */
            pos = writeRangeList(context, classEntry, buffer, pos);
        }
        return pos;
    }

    private int writeRangeList(DebugContext context, ClassEntry classEntry, byte[] buffer, int p) {
        int pos = p;
        log(context, "  [0x%08x] ranges start for class %s", pos, classEntry.getTypeName());
        long base = classEntry.lowpc();
        log(context, "  [0x%08x] base 0x%x", pos, base);
        pos = writeRangeListEntry(DwarfRangeListEntry.DW_RLE_base_address, buffer, pos);
        pos = writeCodeOffset(base, buffer, pos);

        for (CompiledMethodEntry compiledMethodEntry : classEntry.compiledMethods()) {
            pos = writeRangeListEntry(DwarfRangeListEntry.DW_RLE_offset_pair, buffer, pos);

            long loOffset = compiledMethodEntry.primary().getLo() - base;
            long hiOffset = compiledMethodEntry.primary().getHi() - base;
            log(context, "  [0x%08x] lo 0x%x (%s)", pos, loOffset, compiledMethodEntry.primary().getFullMethodNameWithParams());
            pos = writeULEB(loOffset, buffer, pos);
            log(context, "  [0x%08x] hi 0x%x", pos, hiOffset);
            pos = writeULEB(hiOffset, buffer, pos);
        }
        // write end marker
        pos = writeRangeListEntry(DwarfRangeListEntry.DW_RLE_end_of_list, buffer, pos);
        log(context, "  [0x%08x] ranges size 0x%x  for class %s", pos, pos - p, classEntry.getTypeName());
        return pos;
    }
}
