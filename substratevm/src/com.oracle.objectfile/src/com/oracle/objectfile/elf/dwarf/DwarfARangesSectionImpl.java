/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import com.oracle.objectfile.elf.dwarf.constants.DwarfVersion;
import org.graalvm.compiler.debug.DebugContext;

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.range.Range;

/**
 * Section generator for debug_aranges section.
 */
public class DwarfARangesSectionImpl extends DwarfSectionImpl {
    /* Headers have a fixed size but must align up to 2 * address size. */
    private static final int AR_HEADER_SIZE = 12;
    private static final int AR_HEADER_PAD_SIZE = 4;

    public DwarfARangesSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections, DwarfSectionName.DW_ARANGES_SECTION, DwarfSectionName.DW_FRAME_SECTION);
    }

    @Override
    public void createContent() {
        /*
         * We need an entry for each compilation unit that has compiled methods
         *
         * <ul>
         *
         * <li><code>uint32 length ............ in bytes (not counting these 4 bytes)</code>
         *
         * <li><code>uint16 dwarf_version ..... always 2</code>
         *
         * <li><code>uint32 info_offset ....... offset of compilation unit in debug_info -- always
         * 0</code>
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
         * Where N is the number of compiled methods.
         */
        assert !contentByteArrayCreated();
        Cursor byteCount = new Cursor();
        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            byteCount.add(entrySize(classEntry.compiledEntryCount()));
        });
        byte[] buffer = new byte[byteCount.get()];
        super.setContent(buffer);
    }

    private static int entrySize(int methodCount) {
        int size = 0;
        // allow for header data
        size += AR_HEADER_SIZE;
        // align to 2 * address size.
        size += AR_HEADER_PAD_SIZE;
        // count 16 bytes for each deopt compiled method
        size += methodCount * (2 * 8);
        // allow for two trailing zeroes to terminate
        size += 2 * 8;
        return size;
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
        Cursor cursor = new Cursor();

        enableLog(context, cursor.get());

        log(context, "  [0x%08x] DEBUG_ARANGES", cursor.get());
        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            int lengthPos = cursor.get();
            log(context, "  [0x%08x] class %s CU 0x%x", lengthPos, classEntry.getTypeName(), getCUIndex(classEntry));
            cursor.set(writeHeader(getCUIndex(classEntry), buffer, cursor.get()));
            classEntry.compiledEntries().forEachOrdered(compiledMethodEntry -> {
                cursor.set(writeARange(context, compiledMethodEntry, buffer, cursor.get()));
            });
            // write two terminating zeroes
            cursor.set(writeLong(0, buffer, cursor.get()));
            cursor.set(writeLong(0, buffer, cursor.get()));
            patchLength(lengthPos, buffer, cursor.get());
        });
        assert cursor.get() == size;
    }

    private int writeHeader(int cuIndex, byte[] buffer, int p) {
        int pos = p;
        // write dummy length for now
        pos = writeInt(0, buffer, pos);
        /* DWARF version is always 2. */
        pos = writeDwarfVersion(DwarfVersion.DW_VERSION_2, buffer, pos);
        pos = writeInfoSectionOffset(cuIndex, buffer, pos);
        /* Address size is always 8. */
        pos = writeByte((byte) 8, buffer, pos);
        /* Segment size is always 0. */
        pos = writeByte((byte) 0, buffer, pos);
        assert (pos - p) == AR_HEADER_SIZE;
        /*
         * Align to 2 * address size.
         */
        for (int i = 0; i < AR_HEADER_PAD_SIZE; i++) {
            pos = writeByte((byte) 0, buffer, pos);
        }
        return pos;
    }

    int writeARange(DebugContext context, CompiledMethodEntry compiledMethod, byte[] buffer, int p) {
        int pos = p;
        Range primary = compiledMethod.getPrimary();
        log(context, "  [0x%08x] %016x %016x %s", pos, debugTextBase + primary.getLo(), primary.getHi() - primary.getLo(), primary.getFullMethodNameWithParams());
        pos = writeRelocatableCodeOffset(primary.getLo(), buffer, pos);
        pos = writeLong(primary.getHi() - primary.getLo(), buffer, pos);
        return pos;
    }
}
