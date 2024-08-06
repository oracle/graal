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

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.debugentry.ClassEntry;
import com.oracle.objectfile.elf.dwarf.constants.DwarfSectionName;
import jdk.graal.compiler.debug.DebugContext;

import java.util.Map;

public class DwarfRangesSectionImpl extends DwarfSectionImpl {
    public DwarfRangesSectionImpl(DwarfDebugInfo dwarfSections) {
        // debug_ranges section depends on debug_aranges section
        super(dwarfSections, DwarfSectionName.DW_RANGES_SECTION, DwarfSectionName.DW_ARANGES_SECTION);
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();
        Cursor cursor = new Cursor();
        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            setCodeRangesIndex(classEntry, cursor.get());
            // base address
            cursor.add(2 * 8);
            // per method lo and hi offsets
            cursor.add(2 * 8 * classEntry.compiledEntryCount());
            // end marker
            cursor.add(2 * 8);
        });
        byte[] buffer = new byte[cursor.get()];
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
        Cursor cursor = new Cursor();

        enableLog(context, cursor.get());
        log(context, "  [0x%08x] DEBUG_RANGES", cursor.get());

        instanceClassStream().filter(ClassEntry::hasCompiledEntries).forEachOrdered(classEntry -> {
            int pos = cursor.get();
            int start = pos;
            setCodeRangesIndex(classEntry, pos);
            log(context, "  [0x%08x] ranges start for class %s", pos, classEntry.getTypeName());
            int base = classEntry.compiledEntriesBase();
            log(context, "  [0x%08x] base 0x%x", pos, base);
            pos = writeLong(-1L, buffer, pos);
            pos = writeRelocatableCodeOffset(base, buffer, pos);
            cursor.set(pos);
            classEntry.compiledEntries().forEach(compiledMethodEntry -> {
                int lo = compiledMethodEntry.getPrimary().getLo();
                int hi = compiledMethodEntry.getPrimary().getHi();
                log(context, "  [0x%08x] lo 0x%x (%s)", cursor.get(), lo - base, compiledMethodEntry.getPrimary().getFullMethodNameWithParams());
                cursor.set(writeLong(lo - base, buffer, cursor.get()));
                log(context, "  [0x%08x] hi 0x%x", cursor.get(), hi - base);
                cursor.set(writeLong(hi - base, buffer, cursor.get()));
            });
            pos = cursor.get();
            // write end marker
            pos = writeLong(0, buffer, pos);
            pos = writeLong(0, buffer, pos);
            log(context, "  [0x%08x] ranges size 0x%x  for class %s", pos, pos - start, classEntry.getTypeName());
            cursor.set(pos);
        });

        assert cursor.get() == size;
    }
}
