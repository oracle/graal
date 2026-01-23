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
package com.oracle.svm.test.debuginfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class DsymMachOLayoutTest {
    private static final int MH_MAGIC_64 = 0xfeedfacf;
    private static final int LC_SEGMENT_64 = 0x19;

    @Test
    public void testDsymSectionOffsetsRespectAlignment() throws Exception {
        Class<?> featureClass = Class.forName("com.oracle.svm.hosted.image.NativeImageDebugInfoStripFeature");
        Class<?> execInfoClass = Class.forName("com.oracle.svm.hosted.image.NativeImageDebugInfoStripFeature$MachOExecutableInfo");
        Class<?> sectionClass = Class.forName("com.oracle.svm.hosted.image.NativeImageDebugInfoStripFeature$DwarfSectionData");

        Object execInfo = execInfoClass.getDeclaredConstructor().newInstance();
        setField(execInfoClass, execInfo, "uuid", new byte[16]);
        setField(execInfoClass, execInfo, "cpuType", 7);
        setField(execInfoClass, execInfo, "cpuSubType", 3);

        Constructor<?> sectionCtor = sectionClass.getDeclaredConstructor(String.class, byte[].class, int.class);
        sectionCtor.setAccessible(true);
        List<Object> dwarfSections = new ArrayList<>();
        dwarfSections.add(sectionCtor.newInstance("__debug_info", new byte[3], 4));
        dwarfSections.add(sectionCtor.newInstance("__debug_abbrev", new byte[5], 2));

        Method buildDsymFile = featureClass.getDeclaredMethod("buildDsymFile", execInfoClass, List.class);
        buildDsymFile.setAccessible(true);
        byte[] dsymBytes = (byte[]) buildDsymFile.invoke(null, execInfo, dwarfSections);

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
        Class<?> featureClass = Class.forName("com.oracle.svm.hosted.image.NativeImageDebugInfoStripFeature");
        Method readDwarfSections = featureClass.getDeclaredMethod("readDwarfSections", Path.class);
        readDwarfSections.setAccessible(true);

        byte[] malformedMachO = createTruncatedSegmentCommand();
        Path tempFile = Files.createTempFile("malformed-dsym", ".o");
        try {
            Files.write(tempFile, malformedMachO);
            try {
                Object result = readDwarfSections.invoke(null, tempFile);
                @SuppressWarnings("unchecked")
                List<Object> sections = (List<Object>) result;
                Assert.assertTrue("Expected truncated file to yield no DWARF sections.", sections.isEmpty());
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                Assert.fail("readDwarfSections should tolerate truncated load commands: " + cause);
            }
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

    private static void setField(Class<?> targetClass, Object target, String fieldName, Object value) throws Exception {
        Field field = targetClass.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
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
