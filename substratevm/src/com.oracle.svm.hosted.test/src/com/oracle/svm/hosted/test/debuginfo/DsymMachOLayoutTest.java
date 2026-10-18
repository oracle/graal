/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.test.debuginfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.objectfile.macho.MachODsymWriter;
import com.oracle.objectfile.macho.MachODsymWriter.DwarfSection;
import com.oracle.objectfile.macho.MachODsymWriter.ExecutableInfo;

public class DsymMachOLayoutTest {
    private static final int MH_MAGIC_64 = 0xfeedfacf;
    private static final int LC_SEGMENT_64 = 0x19;

    @Test
    public void testDsymSectionOffsetsRespectAlignment() {
        ExecutableInfo execInfo = new ExecutableInfo(new byte[16], 7, 3, List.of());
        List<DwarfSection> dwarfSections = List.of(
                        new DwarfSection("__debug_info", new byte[3], 4),
                        new DwarfSection("__debug_abbrev", new byte[5], 2));
        byte[] dsymBytes = MachODsymWriter.buildDsymFile(execInfo, dwarfSections);

        List<SectionInfo> sections = parseDwarfSections(dsymBytes);
        Assert.assertEquals("Expected two DWARF sections in __DWARF segment.", 2, sections.size());
        Assert.assertTrue("Expected __debug_info section.", sections.stream().anyMatch(s -> "__debug_info".equals(s.name)));
        Assert.assertTrue("Expected __debug_abbrev section.", sections.stream().anyMatch(s -> "__debug_abbrev".equals(s.name)));

        for (SectionInfo section : sections) {
            int alignment = 1 << section.align;
            Assert.assertEquals("Section offset should respect alignment for " + section.name, 0, section.offset % alignment);
        }
    }

    @Test
    public void testReadDwarfSectionsIgnoresTruncatedLoadCommands() throws Exception {
        Path tempFile = Files.createTempFile("malformed-dsym", ".o");
        try {
            Files.write(tempFile, createTruncatedSegmentCommand());
            Assert.assertTrue("Expected truncated file to yield no DWARF sections.", MachODsymWriter.readDwarfSections(tempFile).isEmpty());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static byte[] createTruncatedSegmentCommand() {
        ByteBuffer buffer = ByteBuffer.allocate(40).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(MH_MAGIC_64);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(1);
        buffer.putInt(8);
        buffer.putInt(0);
        buffer.putInt(0);
        buffer.putInt(LC_SEGMENT_64);
        buffer.putInt(8);
        return buffer.array();
    }

    private static List<SectionInfo> parseDwarfSections(byte[] dsymBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(dsymBytes).order(ByteOrder.LITTLE_ENDIAN);
        int ncmds = buffer.getInt(16);
        int sizeofcmds = buffer.getInt(20);
        int offset = 32;
        int cmdsEnd = 32 + sizeofcmds;
        List<SectionInfo> sections = new ArrayList<>();

        for (int i = 0; i < ncmds && offset + 8 <= cmdsEnd; i++) {
            int cmd = buffer.getInt(offset);
            int cmdsize = buffer.getInt(offset + 4);
            if (cmd == LC_SEGMENT_64) {
                String segname = readFixedString(buffer, offset + 8, 16);
                int nsects = buffer.getInt(offset + 64);
                if ("__DWARF".equals(segname)) {
                    for (int j = 0; j < nsects; j++) {
                        int sectOffset = offset + 72 + j * 80;
                        String sectname = readFixedString(buffer, sectOffset, 16);
                        int sectionOffset = buffer.getInt(sectOffset + 48);
                        int align = buffer.getInt(sectOffset + 52);
                        sections.add(new SectionInfo(sectname, sectionOffset, align));
                    }
                    break;
                }
            }
            if (cmdsize <= 0) {
                break;
            }
            offset += cmdsize;
        }
        return sections;
    }

    private static String readFixedString(ByteBuffer buffer, int offset, int length) {
        byte[] bytes = new byte[length];
        int saved = buffer.position();
        buffer.position(offset);
        buffer.get(bytes);
        buffer.position(saved);
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end);
    }

    private static final class SectionInfo {
        final String name;
        final int offset;
        final int align;

        private SectionInfo(String name, int offset, int align) {
            this.name = name;
            this.offset = offset;
            this.align = align;
        }
    }
}
