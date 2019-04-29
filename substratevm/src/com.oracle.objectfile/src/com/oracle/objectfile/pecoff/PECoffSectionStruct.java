/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.pecoff;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.oracle.objectfile.pecoff.PECoff.IMAGE_SECTION_HEADER;

final class PECoffSectionStruct {
    private final ByteBuffer section;
    private final int sectionIndex;
    private final int align;

    PECoffSectionStruct(String sectName, int sectFlags0, int sectAlign, int sectIndex) {

        section = PECoffByteBuffer.allocate(IMAGE_SECTION_HEADER.totalsize);

        // Copy only Max allowed bytes to Section Entry
        byte[] sectionName = sectName.getBytes(StandardCharsets.UTF_8);
        int max = sectionName.length <= IMAGE_SECTION_HEADER.Name.sz ? sectionName.length : IMAGE_SECTION_HEADER.Name.sz;

        assert !(sectAlign < 1 || sectAlign > 1024 || (sectAlign & (sectAlign - 1)) != 0) : "section alignment is not valid: " + sectAlign;
        align = sectAlign;

        // Using 32 because IMAGE_SCN_ALIGN_*BYTES is value + 1
        int sectAlignBits = (32 - Integer.numberOfLeadingZeros(align)) << IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_SHIFT;
        // Clear and set alignment bits
        int sectFlags = (sectFlags0 & ~IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_MASK) | (sectAlignBits & IMAGE_SECTION_HEADER.IMAGE_SCN_ALIGN_MASK);

        section.put(sectionName, IMAGE_SECTION_HEADER.Name.off, max);

        section.putInt(IMAGE_SECTION_HEADER.VirtualSize.off, 0);
        section.putInt(IMAGE_SECTION_HEADER.VirtualAddress.off, 0);
        section.putInt(IMAGE_SECTION_HEADER.PointerToLinenumbers.off, 0);
        section.putChar(IMAGE_SECTION_HEADER.NumberOfLinenumbers.off, (char) 0);

        section.putInt(IMAGE_SECTION_HEADER.Characteristics.off, sectFlags);

        sectionIndex = sectIndex;
    }

    int getSize() {
        return section.getInt(IMAGE_SECTION_HEADER.SizeOfRawData.off);
    }

    int getDataAlign() {
        return (align);
    }

    // Alignment requirements for the IMAGE_SECTION_HEADER structures
    static int getShdrAlign() {
        return (4);
    }

    byte[] getArray() {
        return section.array();
    }

    void setOffset(int offset) {
        section.putInt(IMAGE_SECTION_HEADER.PointerToRawData.off, offset);
    }

    void setSize(int size) {
        section.putInt(IMAGE_SECTION_HEADER.SizeOfRawData.off, size);
    }

    void setVirtualAddress(int addr) {
        section.putInt(IMAGE_SECTION_HEADER.VirtualAddress.off, addr);
    }

    int getOffset() {
        return (section.getInt(IMAGE_SECTION_HEADER.PointerToRawData.off));
    }

    void setReloff(int offset) {
        section.putInt(IMAGE_SECTION_HEADER.PointerToRelocations.off, offset);
    }

    void setRelcount(int count) {
        // If the number of relocs is larger than 65K, then set
        // the overflow bit. The real count will be written to
        // the first reloc entry for this section.
        if (count > 0xFFFF) {
            int flags;
            section.putChar(IMAGE_SECTION_HEADER.NumberOfRelocations.off, (char) 0xFFFF);
            flags = section.getInt(IMAGE_SECTION_HEADER.Characteristics.off);
            flags |= IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_NRELOC_OVFL;
            section.putInt(IMAGE_SECTION_HEADER.Characteristics.off, flags);
        } else {
            section.putChar(IMAGE_SECTION_HEADER.NumberOfRelocations.off, (char) count);
        }
    }

    int getSectionId() {
        return sectionIndex;
    }

}
