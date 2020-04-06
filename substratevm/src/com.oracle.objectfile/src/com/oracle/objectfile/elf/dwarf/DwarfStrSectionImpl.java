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
import com.oracle.objectfile.debugentry.StringEntry;
import org.graalvm.compiler.debug.DebugContext;

import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.DW_STR_SECTION_NAME;
import static com.oracle.objectfile.elf.dwarf.DwarfDebugInfo.TEXT_SECTION_NAME;

/**
 * Generator for debug_str section.
 */
public class DwarfStrSectionImpl extends DwarfSectionImpl {
    public DwarfStrSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DW_STR_SECTION_NAME;
    }

    @Override
    public void createContent() {
        int pos = 0;
        for (StringEntry stringEntry : dwarfSections.getStringTable()) {
            if (stringEntry.isAddToStrSection()) {
                stringEntry.setOffset(pos);
                String string = stringEntry.getString();
                pos += string.length() + 1;
            }
        }
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        for (StringEntry stringEntry : dwarfSections.getStringTable()) {
            if (stringEntry.isAddToStrSection()) {
                assert stringEntry.getOffset() == pos;
                String string = stringEntry.getString();
                pos = putAsciiStringBytes(string, buffer, pos);
            }
        }
        assert pos == size;
    }

    /**
     * The debug_str section content depends on text section content and offset.
     */
    private static final String TARGET_SECTION_NAME = TEXT_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    /**
     * The debug_str section content depends on text section content and offset.
     */
    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.OFFSET,
                    /* Add this so we can use the text section base address for debug. */
                    LayoutDecision.Kind.VADDR,
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
