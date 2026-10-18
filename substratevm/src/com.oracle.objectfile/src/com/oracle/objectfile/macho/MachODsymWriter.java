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
package com.oracle.objectfile.macho;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.oracle.objectfile.macho.MachOObjectFile.FileType;
import com.oracle.objectfile.macho.MachOObjectFile.LoadCommandKind;

/**
 * Writes a dSYM bundle, the standard macOS container for separate debug info, from the DWARF
 * sections of a Native Image object file.
 *
 * On macOS, the linker strips DWARF sections and relies on STABS debug map entries (OSO) to let
 * {@code dsymutil} find them in the original object files. Native Image emits DWARF directly and
 * no debug map, so {@code dsymutil} cannot recover anything from the executable. Since the DWARF
 * produced by Native Image already refers to final executable addresses, no relocation is needed:
 * the sections only have to be packaged into an {@link FileType#DSYM MH_DSYM} file that carries
 * the executable's UUID and segment layout.
 */
public final class MachODsymWriter {

    /**
     * The address of {@code __TEXT,__text} in a native-image executable: {@code __TEXT} starts
     * right after the 4GB {@code __PAGEZERO} segment and the linker places {@code __text} after the
     * first 16K page holding the Mach-O header and load commands. The DWARF for Mach-O is emitted
     * relative to this address because the object file is written before linking.
     */
    public static final long EXPECTED_TEXT_SECTION_VADDR = 0x100004000L;

    private static final int MACH_HEADER_64_SIZE = 32;
    private static final int SEGMENT_COMMAND_64_SIZE = 72;
    private static final int SECTION_64_SIZE = 80;
    private static final int UUID_COMMAND_SIZE = 24;
    private static final int SYMTAB_COMMAND_SIZE = 24;
    private static final int NAME_LENGTH = 16;

    private static final String DWARF_SEGMENT_NAME = "__DWARF";
    private static final String DWARF_SECTION_PREFIX = "__debug";

    private static final int LC_SEGMENT_64 = (int) LoadCommandKind.SEGMENT_64.getValue();
    private static final int LC_UUID = (int) LoadCommandKind.UUID.getValue();
    private static final int LC_SYMTAB = (int) LoadCommandKind.SYMTAB.getValue();

    private MachODsymWriter() {
    }

    /** Metadata of a linked Mach-O executable that a matching dSYM must replicate. */
    public record ExecutableInfo(byte[] uuid, int cpuType, int cpuSubType, List<SegmentInfo> segments) {

        /** Returns the address of {@code __TEXT,__text}, or -1 if there is no such section. */
        public long textSectionAddress() {
            for (SegmentInfo seg : segments) {
                for (SectionInfo sect : seg.sections()) {
                    if ("__TEXT".equals(sect.segname()) && "__text".equals(sect.sectname())) {
                        return sect.addr();
                    }
                }
            }
            return -1;
        }
    }

    public record SegmentInfo(String name, long vmaddr, long vmsize, int maxprot, int initprot, List<SectionInfo> sections) {
    }

    public record SectionInfo(String sectname, String segname, long addr, long size) {
    }

    /** A DWARF section as found in an object file; {@code align} is a power-of-two exponent. */
    public record DwarfSection(String name, byte[] content, int align) {
    }

    /**
     * Creates a dSYM bundle at {@code dsymPath} for {@code executablePath} using the DWARF sections
     * of {@code objectFilePath}.
     *
     * @return {@code null} if the bundle was written, otherwise the reason why it was not
     */
    public static String createDsym(Path executablePath, Path objectFilePath, Path dsymPath, String imageName) throws IOException {
        ExecutableInfo execInfo = readExecutableInfo(executablePath);
        if (execInfo == null || execInfo.uuid() == null) {
            return executablePath + " is not a 64-bit Mach-O file with a UUID";
        }
        long textAddress = execInfo.textSectionAddress();
        if (textAddress != EXPECTED_TEXT_SECTION_VADDR) {
            return String.format("the __text section of %s is at 0x%x instead of the expected 0x%x", executablePath, textAddress, EXPECTED_TEXT_SECTION_VADDR);
        }
        List<DwarfSection> dwarfSections = readDwarfSections(objectFilePath);
        if (dwarfSections.isEmpty()) {
            return objectFilePath + " contains no DWARF sections";
        }
        Path dwarfDir = dsymPath.resolve("Contents/Resources/DWARF");
        Files.createDirectories(dwarfDir);
        Files.write(dwarfDir.resolve(imageName), buildDsymFile(execInfo, dwarfSections));
        Files.writeString(dsymPath.resolve("Contents/Info.plist"), infoPlist(imageName));
        return null;
    }

    /**
     * Reads the header and load commands of a 64-bit Mach-O file, or returns {@code null} if the
     * file is not one.
     */
    private static ByteBuffer readLoadCommands(RandomAccessFile file) throws IOException {
        if (file.length() < MACH_HEADER_64_SIZE) {
            return null;
        }
        ByteBuffer header = ByteBuffer.allocate(MACH_HEADER_64_SIZE);
        file.readFully(header.array());
        /* ByteBuffer starts out big-endian; reading the magic byte-swapped means little-endian. */
        int magic = header.getInt(0);
        ByteOrder order;
        if (magic == MachOObjectFile.MAGIC) {
            order = ByteOrder.BIG_ENDIAN;
        } else if (magic == MachOObjectFile.CIGAM) {
            order = ByteOrder.LITTLE_ENDIAN;
        } else {
            return null;
        }
        header.order(order);
        int sizeofcmds = header.getInt(20);
        if (sizeofcmds < 0 || sizeofcmds > file.length() - MACH_HEADER_64_SIZE) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(MACH_HEADER_64_SIZE + sizeofcmds).order(order);
        buf.put(header.array());
        file.readFully(buf.array(), MACH_HEADER_64_SIZE, sizeofcmds);
        return buf;
    }

    private static String readFixedString(ByteBuffer buf, int offset) {
        int end = 0;
        while (end < NAME_LENGTH && buf.get(offset + end) != 0) {
            end++;
        }
        return new String(buf.array(), offset, end, StandardCharsets.UTF_8);
    }

    /**
     * Reads the UUID, CPU type and segment layout of a Mach-O executable, or returns {@code null}
     * if the file is not a 64-bit Mach-O file.
     */
    public static ExecutableInfo readExecutableInfo(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            ByteBuffer buf = readLoadCommands(file);
            if (buf == null) {
                return null;
            }
            int ncmds = buf.getInt(16);
            int end = buf.capacity();
            byte[] uuid = null;
            List<SegmentInfo> segments = new ArrayList<>();
            int offset = MACH_HEADER_64_SIZE;
            for (int i = 0; i < ncmds && offset + 8 <= end; i++) {
                int cmd = buf.getInt(offset);
                int cmdsize = buf.getInt(offset + 4);
                if (cmdsize <= 0 || offset + cmdsize > end) {
                    break;
                }
                if (cmd == LC_UUID && cmdsize >= UUID_COMMAND_SIZE) {
                    uuid = new byte[16];
                    buf.get(offset + 8, uuid);
                } else if (cmd == LC_SEGMENT_64 && cmdsize >= SEGMENT_COMMAND_64_SIZE) {
                    int nsects = buf.getInt(offset + 64);
                    List<SectionInfo> sections = new ArrayList<>();
                    for (int j = 0; j < nsects; j++) {
                        int sectOffset = offset + SEGMENT_COMMAND_64_SIZE + j * SECTION_64_SIZE;
                        if (sectOffset + SECTION_64_SIZE > offset + cmdsize) {
                            break;
                        }
                        sections.add(new SectionInfo(readFixedString(buf, sectOffset), readFixedString(buf, sectOffset + NAME_LENGTH),
                                        buf.getLong(sectOffset + 32), buf.getLong(sectOffset + 40)));
                    }
                    segments.add(new SegmentInfo(readFixedString(buf, offset + 8), buf.getLong(offset + 24), buf.getLong(offset + 32),
                                    buf.getInt(offset + 56), buf.getInt(offset + 60), sections));
                }
                offset += cmdsize;
            }
            return new ExecutableInfo(uuid, buf.getInt(4), buf.getInt(8), segments);
        }
    }

    /**
     * Reads the DWARF ({@code __debug*}) sections of a Mach-O object file. Malformed or truncated
     * load commands end the scan rather than causing an exception.
     */
    public static List<DwarfSection> readDwarfSections(Path path) throws IOException {
        List<DwarfSection> dwarfSections = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            ByteBuffer buf = readLoadCommands(file);
            if (buf == null) {
                return dwarfSections;
            }
            int ncmds = buf.getInt(16);
            int end = buf.capacity();
            int offset = MACH_HEADER_64_SIZE;
            for (int i = 0; i < ncmds && offset + 8 <= end; i++) {
                int cmd = buf.getInt(offset);
                int cmdsize = buf.getInt(offset + 4);
                if (cmdsize <= 0 || offset + cmdsize > end) {
                    break;
                }
                if (cmd == LC_SEGMENT_64 && cmdsize >= SEGMENT_COMMAND_64_SIZE) {
                    int nsects = buf.getInt(offset + 64);
                    for (int j = 0; j < nsects; j++) {
                        int sectOffset = offset + SEGMENT_COMMAND_64_SIZE + j * SECTION_64_SIZE;
                        if (sectOffset + SECTION_64_SIZE > offset + cmdsize) {
                            break;
                        }
                        String sectName = readFixedString(buf, sectOffset);
                        if (sectName.startsWith(DWARF_SECTION_PREFIX)) {
                            long size = buf.getLong(sectOffset + 40);
                            long fileOffset = Integer.toUnsignedLong(buf.getInt(sectOffset + 48));
                            int align = buf.getInt(sectOffset + 52);
                            if (size < 0 || size > Integer.MAX_VALUE || fileOffset + size > file.length()) {
                                throw new IOException("Section " + sectName + " exceeds the bounds of " + path);
                            }
                            byte[] content = new byte[(int) size];
                            file.seek(fileOffset);
                            file.readFully(content);
                            dwarfSections.add(new DwarfSection(sectName, content, align));
                        }
                    }
                }
                offset += cmdsize;
            }
        }
        return dwarfSections;
    }

    /**
     * Builds an {@code MH_DSYM} Mach-O file that mirrors the segment layout of the executable
     * (without file contents) followed by a {@code __DWARF} segment holding the DWARF sections.
     */
    public static byte[] buildDsymFile(ExecutableInfo execInfo, List<DwarfSection> dwarfSections) {
        int loadCmdsSize = UUID_COMMAND_SIZE + SYMTAB_COMMAND_SIZE;
        for (SegmentInfo seg : execInfo.segments()) {
            loadCmdsSize += SEGMENT_COMMAND_64_SIZE + seg.sections().size() * SECTION_64_SIZE;
        }
        loadCmdsSize += SEGMENT_COMMAND_64_SIZE + dwarfSections.size() * SECTION_64_SIZE;

        int maxSectionAlign = 8;
        for (DwarfSection ds : dwarfSections) {
            maxSectionAlign = Math.max(maxSectionAlign, 1 << ds.align());
        }
        int dwarfDataOffset = alignUp(MACH_HEADER_64_SIZE + loadCmdsSize, maxSectionAlign);
        int dwarfDataEnd = dwarfDataOffset;
        for (DwarfSection ds : dwarfSections) {
            dwarfDataEnd = alignUp(dwarfDataEnd, 1 << ds.align()) + ds.content().length;
        }

        ByteBuffer buf = ByteBuffer.allocate(dwarfDataEnd).order(ByteOrder.LITTLE_ENDIAN);

        /* mach_header_64 */
        buf.putInt(MachOObjectFile.MAGIC);
        buf.putInt(execInfo.cpuType());
        buf.putInt(execInfo.cpuSubType());
        buf.putInt(FileType.DSYM.value);
        buf.putInt(2 + execInfo.segments().size() + 1);
        buf.putInt(loadCmdsSize);
        buf.putInt(0); // flags
        buf.putInt(0); // reserved

        buf.putInt(LC_UUID);
        buf.putInt(UUID_COMMAND_SIZE);
        buf.put(execInfo.uuid());

        /* An empty symbol table. */
        buf.putInt(LC_SYMTAB);
        buf.putInt(SYMTAB_COMMAND_SIZE);
        buf.putInt(0); // symoff
        buf.putInt(0); // nsyms
        buf.putInt(0); // stroff
        buf.putInt(0); // strsize

        /* The executable's segments, without any file contents. */
        for (SegmentInfo seg : execInfo.segments()) {
            putSegmentCommand(buf, seg.name(), seg.vmaddr(), seg.vmsize(), 0, 0, seg.maxprot(), seg.initprot(), seg.sections().size());
            for (SectionInfo sect : seg.sections()) {
                putSection(buf, sect.sectname(), sect.segname(), sect.addr(), sect.size(), 0, 0);
            }
        }

        int dwarfDataSize = dwarfDataEnd - dwarfDataOffset;
        putSegmentCommand(buf, DWARF_SEGMENT_NAME, 0, dwarfDataSize, dwarfDataOffset, dwarfDataSize, 0, 0, dwarfSections.size());
        int sectionOffset = dwarfDataOffset;
        for (DwarfSection ds : dwarfSections) {
            sectionOffset = alignUp(sectionOffset, 1 << ds.align());
            putSection(buf, ds.name(), DWARF_SEGMENT_NAME, 0, ds.content().length, sectionOffset, ds.align());
            sectionOffset += ds.content().length;
        }

        /* Section contents; the buffer is zero-initialized, so padding only needs skipping. */
        sectionOffset = dwarfDataOffset;
        for (DwarfSection ds : dwarfSections) {
            sectionOffset = alignUp(sectionOffset, 1 << ds.align());
            buf.put(sectionOffset, ds.content());
            sectionOffset += ds.content().length;
        }
        return buf.array();
    }

    private static void putSegmentCommand(ByteBuffer buf, String name, long vmaddr, long vmsize, long fileoff, long filesize, int maxprot, int initprot, int nsects) {
        buf.putInt(LC_SEGMENT_64);
        buf.putInt(SEGMENT_COMMAND_64_SIZE + nsects * SECTION_64_SIZE);
        putFixedString(buf, name);
        buf.putLong(vmaddr);
        buf.putLong(vmsize);
        buf.putLong(fileoff);
        buf.putLong(filesize);
        buf.putInt(maxprot);
        buf.putInt(initprot);
        buf.putInt(nsects);
        buf.putInt(0); // flags
    }

    private static void putSection(ByteBuffer buf, String sectname, String segname, long addr, long size, int offset, int align) {
        putFixedString(buf, sectname);
        putFixedString(buf, segname);
        buf.putLong(addr);
        buf.putLong(size);
        buf.putInt(offset);
        buf.putInt(align);
        buf.putInt(0); // reloff
        buf.putInt(0); // nreloc
        buf.putInt(0); // flags
        buf.putInt(0); // reserved1
        buf.putInt(0); // reserved2
        buf.putInt(0); // reserved3
    }

    private static void putFixedString(ByteBuffer buf, String s) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        int length = Math.min(bytes.length, NAME_LENGTH);
        buf.put(bytes, 0, length);
        for (int i = length; i < NAME_LENGTH; i++) {
            buf.put((byte) 0);
        }
    }

    private static int alignUp(int value, int alignment) {
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    private static String infoPlist(String imageName) {
        return """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
                        <plist version="1.0">
                        <dict>
                            <key>CFBundleDevelopmentRegion</key>
                            <string>English</string>
                            <key>CFBundleIdentifier</key>
                            <string>com.oracle.graalvm.%s.dSYM</string>
                            <key>CFBundleInfoDictionaryVersion</key>
                            <string>6.0</string>
                            <key>CFBundlePackageType</key>
                            <string>dSYM</string>
                            <key>CFBundleSignature</key>
                            <string>????</string>
                            <key>CFBundleVersion</key>
                            <string>1</string>
                        </dict>
                        </plist>
                        """.formatted(imageName.replaceAll("[^a-zA-Z0-9._-]", "_"));
    }
}
