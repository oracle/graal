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

import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.debugentry.StringEntry;
import org.graalvm.compiler.debug.DebugContext;

/**
 * Generator for debug_str section.
 */
public class DwarfStrSectionImpl extends DwarfSectionImpl {
    public DwarfStrSectionImpl(DwarfDebugInfo dwarfSections) {
        super(dwarfSections);
    }

    @Override
    public String getSectionName() {
        return DwarfDebugInfo.DW_STR_SECTION_NAME;
    }

    @Override
    public void createContent() {
        assert !contentByteArrayCreated();

        int pos = 0;
        for (StringEntry stringEntry : dwarfSections.getStringTable()) {
            if (stringEntry.isAddToStrSection()) {
                stringEntry.setOffset(pos);
                String string = stringEntry.getString();
                pos += countUTF8Bytes(string) + 1;
            }
        }
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
    }

    @Override
    public void writeContent(DebugContext context) {
        assert contentByteArrayCreated();

        byte[] buffer = getContent();
        int size = buffer.length;
        int pos = 0;

        enableLog(context, pos);

        verboseLog(context, " [0x%08x] DEBUG_STR", pos);
        for (StringEntry stringEntry : dwarfSections.getStringTable()) {
            if (stringEntry.isAddToStrSection()) {
                assert stringEntry.getOffset() == pos;
                String string = stringEntry.getString();
                pos = writeUTF8StringBytes(string, buffer, pos);
                verboseLog(context, " [0x%08x] string = %s", pos, string);
            }
        }
        assert pos == size;
    }

    /**
     * The debug_str section depends on info section.
     */
    private static final String TARGET_SECTION_NAME = DwarfDebugInfo.DW_INFO_SECTION_NAME;

    @Override
    public String targetSectionName() {
        return TARGET_SECTION_NAME;
    }

    private final LayoutDecision.Kind[] targetSectionKinds = {
                    LayoutDecision.Kind.CONTENT,
                    LayoutDecision.Kind.SIZE,
    };

    @Override
    public LayoutDecision.Kind[] targetSectionKinds() {
        return targetSectionKinds;
    }
}
