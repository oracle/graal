/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.image;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.InternalPlatform;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.BuildArtifacts.ArtifactType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.AfterImageWriteAccessImpl;
import com.oracle.svm.hosted.c.util.FileUtils;
import com.oracle.svm.util.LogUtils;

import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.debug.Indent;

@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
public class NativeImageDebugInfoStripFeature implements InternalFeature {

    private Boolean hasStrippedSuccessfully = null;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.StripDebugInfo.getValue();
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        AfterImageWriteAccessImpl accessImpl = (AfterImageWriteAccessImpl) access;
        try (Indent _ = accessImpl.getDebugContext().logAndIndent("Stripping debuginfo")) {
            switch (ObjectFile.getNativeFormat()) {
                case ELF:
                    hasStrippedSuccessfully = stripLinux(accessImpl);
                    break;
                case PECOFF:
                    // debug info is always "stripped" to a pdb file by linker
                    hasStrippedSuccessfully = true;
                    break;
                case MACH_O:
                    hasStrippedSuccessfully = stripMacOS(accessImpl);
                    break;
                default:
                    throw UserError.abort("Unsupported object file format");
            }
        }
    }

    public boolean hasStrippedSuccessfully() {
        if (hasStrippedSuccessfully == null) {
            throw VMError.shouldNotReachHere("hasStrippedSuccessfully not available yet");
        }
        return hasStrippedSuccessfully;
    }

    @SuppressFBWarnings(value = "", justification = "FB reports null pointer dereferencing although it is not possible in this case.")
    private static boolean stripLinux(AfterImageWriteAccessImpl accessImpl) {
        String objcopyExe = "objcopy";
        String debugExtension = ".debug";
        Path imagePath = accessImpl.getImagePath();
        if (imagePath == null) {
            assert !Platform.includedIn(InternalPlatform.NATIVE_ONLY.class);
            return false;
        }

        Path imageName = imagePath.getFileName();
        boolean objcopyAvailable = false;
        try {
            objcopyAvailable = FileUtils.executeCommand(objcopyExe, "--version") == 0;
        } catch (IOException e) {
            /* Fall through to `if (!objcopyAvailable)` */
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding("Interrupted during checking for " + objcopyExe + " availability");
        }

        if (!objcopyAvailable) {
            LogUtils.warning("%s not available. The debuginfo will remain embedded in the executable.", objcopyExe);
            return false;
        } else {
            try {
                Path outputDirectory = imagePath.getParent();
                String imageFilePath = outputDirectory.resolve(imageName).toString();
                if (SubstrateOptions.useDebugInfoGeneration()) {
                    /* Generate a separate debug file before stripping the executable. */
                    String debugInfoName = imageName + debugExtension;
                    Path debugInfoFilePath = outputDirectory.resolve(debugInfoName);
                    FileUtils.executeCommand(objcopyExe, "--only-keep-debug", imageFilePath, debugInfoFilePath.toString());
                    BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, debugInfoFilePath);
                    FileUtils.executeCommand(objcopyExe, "--add-gnu-debuglink=" + debugInfoFilePath, imageFilePath);
                }
                if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                    /* Strip debug info and local symbols. */
                    FileUtils.executeCommand(objcopyExe, "--strip-all", imageFilePath);
                } else {
                    /* Strip debug info only. */
                    FileUtils.executeCommand(objcopyExe, "--strip-debug", imageFilePath);
                }
                return true;
            } catch (IOException e) {
                throw UserError.abort("Generation of separate debuginfo file failed", e);
            } catch (InterruptedException e) {
                throw new InterruptImageBuilding("Interrupted during debuginfo file splitting of image " + imageName);
            }
        }
    }

    @SuppressFBWarnings(value = "", justification = "FB reports null pointer dereferencing although it is not possible in this case.")
    private static boolean stripMacOS(AfterImageWriteAccessImpl accessImpl) {
        String dsymutilExe = "dsymutil";
        String stripExe = "strip";
        String dsymExtension = ".dSYM";
        Path imagePath = accessImpl.getImagePath();
        if (imagePath == null) {
            assert !Platform.includedIn(InternalPlatform.NATIVE_ONLY.class);
            return false;
        }

        Path imageName = imagePath.getFileName();
        boolean dsymutilAvailable = false;
        try {
            dsymutilAvailable = FileUtils.executeCommand(dsymutilExe, "--version") == 0;
        } catch (IOException e) {
            /* Fall through to `if (!dsymutilAvailable)` */
        } catch (InterruptedException e) {
            throw new InterruptImageBuilding("Interrupted during checking for " + dsymutilExe + " availability");
        }

        if (!dsymutilAvailable) {
            LogUtils.warning("%s not available. The debuginfo will remain embedded in the executable.", dsymutilExe);
            return false;
        } else {
            try {
                Path outputDirectory = imagePath.getParent();
                String imageFilePath = imagePath.toString();
                if (SubstrateOptions.useDebugInfoGeneration()) {
                    /* Generate a dSYM bundle with debug info before stripping the executable. */
                    String dsymName = imageName + dsymExtension;
                    Path dsymPath = outputDirectory.resolve(dsymName);
                    /*
                     * On macOS, the linker strips DWARF debug sections and creates OSO entries
                     * pointing to the original object files. However, since we generate DWARF
                     * directly without STABS debug map symbols, the linker doesn't create OSO
                     * entries, so dsymutil cannot extract debug info from our executable.
                     *
                     * Instead, we create the dSYM bundle manually by copying the object file
                     * (which contains all DWARF sections) and patching it to:
                     * 1. Change file type from MH_OBJECT to MH_DSYM
                     * 2. Set the UUID to match the executable
                     */
                    Path tempDirectory = accessImpl.getTempDirectory();
                    String objectFileName = imageName.toString().replaceAll("\\.[^.]+$", "") + ".o";
                    Path objectFilePath = tempDirectory.resolve(objectFileName);
                    if (Files.exists(objectFilePath)) {
                        /*
                         * Create a dSYM bundle from the object file. The object file contains all
                         * DWARF debug sections with addresses already adjusted for the executable.
                         * We patch the Mach-O structure to make it a proper dSYM file and set the
                         * segment vmaddr to match the executable's load address.
                         */
                        createDsymFromObjectFile(imagePath, objectFilePath, dsymPath, imageName.toString());
                    } else {
                        /* Fall back to running dsymutil on the executable (for C library debug info) */
                        FileUtils.executeCommand(dsymutilExe, "-o", dsymPath.toString(), imageFilePath);
                    }
                    BuildArtifacts.singleton().add(ArtifactType.DEBUG_INFO, dsymPath);
                }
                if (SubstrateOptions.DeleteLocalSymbols.getValue()) {
                    /* Strip debug info and local symbols. */
                    FileUtils.executeCommand(stripExe, imageFilePath);
                } else {
                    /* Strip debug info only. */
                    FileUtils.executeCommand(stripExe, "-S", imageFilePath);
                }
                return true;
            } catch (IOException e) {
                throw UserError.abort("Generation of dSYM debug info bundle failed", e);
            } catch (InterruptedException e) {
                throw new InterruptImageBuilding("Interrupted during debuginfo file splitting of image " + imageName);
            }
        }
    }

    /**
     * Mach-O magic numbers, file types, and load command constants.
     */
    private static final int MH_MAGIC_64 = 0xfeedfacf;
    private static final int MH_CIGAM_64 = 0xcffaedfe;
    private static final int MH_DSYM = 0xa;
    private static final int LC_SEGMENT_64 = 0x19;
    private static final int LC_UUID = 0x1b;
    private static final int LC_SYMTAB = 0x02;

    /** Holds information extracted from a Mach-O executable needed to build a dSYM. */
    private static class MachOExecutableInfo {
        byte[] uuid;
        int cpuType;
        int cpuSubType;
        List<SegmentInfo> segments = new ArrayList<>();

        static class SegmentInfo {
            String name;
            long vmaddr;
            long vmsize;
            int maxprot;
            int initprot;
            List<SectionInfo> sections = new ArrayList<>();
        }

        static class SectionInfo {
            String sectname;
            String segname;
            long addr;
            long size;
        }
    }

    /** Holds DWARF section data extracted from an object file. */
    private static class DwarfSectionData {
        String name;
        byte[] content;
        int align;

        DwarfSectionData(String name, byte[] content, int align) {
            this.name = name;
            this.content = content;
            this.align = align;
        }
    }

    /**
     * Creates a dSYM bundle from an object file containing DWARF debug info.
     * The dSYM bundle is the standard macOS format for separate debug info.
     *
     * This method constructs a proper dSYM Mach-O file from scratch by:
     * 1. Reading the executable's UUID and segment layout
     * 2. Extracting DWARF sections from the object file
     * 3. Building a new Mach-O file with proper segment structure
     * 4. Creating the Info.plist file
     *
     * The key insight is that native-image's DWARF already has pre-relocated addresses
     * (final executable addresses), so we don't need dsymutil's relocation - we just
     * need to package the DWARF correctly in a proper dSYM structure.
     *
     * @param executablePath path to the linked executable (for UUID and segment layout)
     * @param objectFilePath path to the object file with DWARF sections
     * @param dsymPath path for the output .dSYM bundle
     * @param imageName the name of the executable (for the DWARF file)
     */
    private static void createDsymFromObjectFile(Path executablePath, Path objectFilePath,
                                                  Path dsymPath, String imageName) throws IOException {
        // Create dSYM directory structure
        Path dwarfDir = dsymPath.resolve("Contents/Resources/DWARF");
        Files.createDirectories(dwarfDir);

        // Read executable metadata (UUID, segment layout)
        MachOExecutableInfo execInfo = readExecutableInfo(executablePath);
        if (execInfo.uuid == null) {
            LogUtils.warning("Could not read UUID from %s", executablePath);
            return;
        }

        // Read DWARF sections from object file
        List<DwarfSectionData> dwarfSections = readDwarfSections(objectFilePath);
        if (dwarfSections.isEmpty()) {
            LogUtils.warning("No DWARF sections found in %s", objectFilePath);
            return;
        }

        // Build the dSYM Mach-O file
        byte[] dsymContent = buildDsymFile(execInfo, dwarfSections);

        // Write to dSYM bundle
        Path dsymDwarfFile = dwarfDir.resolve(imageName);
        Files.write(dsymDwarfFile, dsymContent);

        // Create Info.plist
        createInfoPlist(dsymPath, imageName);
    }

    /**
     * Reads executable metadata (UUID, CPU type, segment layout) from a Mach-O file.
     *
     * @param path path to the Mach-O executable
     * @return MachOExecutableInfo with the extracted metadata
     */
    private static MachOExecutableInfo readExecutableInfo(Path path) throws IOException {
        MachOExecutableInfo info = new MachOExecutableInfo();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            ByteBuffer header = ByteBuffer.allocate(32);
            file.read(header.array());

            int magic = header.getInt(0);
            ByteOrder order;
            /*
             * ByteBuffer defaults to BIG_ENDIAN. The magic number tells us the file's byte order:
             * - If we read MH_MAGIC_64 (0xfeedfacf), the file matches our default (big-endian)
             * - If we read MH_CIGAM_64 (0xcffaedfe), the file is in opposite order (little-endian)
             */
            if (magic == MH_MAGIC_64) {
                order = ByteOrder.BIG_ENDIAN;
            } else if (magic == MH_CIGAM_64) {
                order = ByteOrder.LITTLE_ENDIAN;
            } else {
                return info; // Not a 64-bit Mach-O file
            }
            header.order(order);

            info.cpuType = header.getInt(4);
            info.cpuSubType = header.getInt(8);
            int ncmds = header.getInt(16);
            int sizeofcmds = header.getInt(20);

            file.seek(32);
            ByteBuffer cmds = ByteBuffer.allocate(sizeofcmds);
            cmds.order(order);
            file.read(cmds.array());

            int offset = 0;
            for (int i = 0; i < ncmds && offset + 8 <= sizeofcmds; i++) {
                int cmd = cmds.getInt(offset);
                int cmdsize = cmds.getInt(offset + 4);
                if (cmdsize <= 0 || offset + cmdsize > sizeofcmds) {
                    break;
                }

                if (cmd == LC_UUID && cmdsize >= 24) {
                    info.uuid = new byte[16];
                    cmds.position(offset + 8);
                    cmds.get(info.uuid);
                } else if (cmd == LC_SEGMENT_64 && cmdsize >= 72) {
                    MachOExecutableInfo.SegmentInfo seg = new MachOExecutableInfo.SegmentInfo();
                    byte[] nameBytes = new byte[16];
                    cmds.position(offset + 8);
                    cmds.get(nameBytes);
                    seg.name = new String(nameBytes, StandardCharsets.UTF_8).replace("\0", "");
                    seg.vmaddr = cmds.getLong(offset + 24);
                    seg.vmsize = cmds.getLong(offset + 32);
                    seg.maxprot = cmds.getInt(offset + 56);
                    seg.initprot = cmds.getInt(offset + 60);
                    int nsects = cmds.getInt(offset + 64);

                    for (int j = 0; j < nsects && offset + 72 + (j + 1) * 80 <= sizeofcmds; j++) {
                        int sectOffset = offset + 72 + j * 80;
                        MachOExecutableInfo.SectionInfo sect = new MachOExecutableInfo.SectionInfo();
                        byte[] sectNameBytes = new byte[16];
                        byte[] segNameBytes = new byte[16];
                        cmds.position(sectOffset);
                        cmds.get(sectNameBytes);
                        cmds.get(segNameBytes);
                        sect.sectname = new String(sectNameBytes, StandardCharsets.UTF_8).replace("\0", "");
                        sect.segname = new String(segNameBytes, StandardCharsets.UTF_8).replace("\0", "");
                        sect.addr = cmds.getLong(sectOffset + 32);
                        sect.size = cmds.getLong(sectOffset + 40);
                        seg.sections.add(sect);
                    }
                    info.segments.add(seg);
                }
                offset += cmdsize;
            }
        }
        return info;
    }

    /**
     * Reads DWARF debug sections from a Mach-O object file.
     *
     * @param path path to the Mach-O object file
     * @return list of DwarfSectionData containing the section name, content, and alignment
     */
    private static List<DwarfSectionData> readDwarfSections(Path path) throws IOException {
        List<DwarfSectionData> dwarfSections = new ArrayList<>();
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            ByteBuffer header = ByteBuffer.allocate(32);
            file.read(header.array());

            int magic = header.getInt(0);
            ByteOrder order;
            if (magic == MH_MAGIC_64) {
                order = ByteOrder.BIG_ENDIAN;
            } else if (magic == MH_CIGAM_64) {
                order = ByteOrder.LITTLE_ENDIAN;
            } else {
                return dwarfSections; // Not a 64-bit Mach-O file
            }
            header.order(order);

            int ncmds = header.getInt(16);
            int sizeofcmds = header.getInt(20);

            file.seek(32);
            ByteBuffer cmds = ByteBuffer.allocate(sizeofcmds);
            cmds.order(order);
            file.read(cmds.array());

            int offset = 0;
            for (int i = 0; i < ncmds; i++) {
                int cmd = cmds.getInt(offset);
                int cmdsize = cmds.getInt(offset + 4);
                if (cmdsize <= 0 || offset + cmdsize > sizeofcmds) {
                    break;
                }

                if (cmd == LC_SEGMENT_64) {
                    if (offset + 72 > sizeofcmds) {
                        break;
                    }
                    int nsects = cmds.getInt(offset + 64);
                    for (int j = 0; j < nsects; j++) {
                        int sectOffset = offset + 72 + j * 80;
                        if (sectOffset + 80 > sizeofcmds) {
                            break;
                        }
                        byte[] nameBytes = new byte[16];
                        cmds.position(sectOffset);
                        cmds.get(nameBytes);
                        String sectName = new String(nameBytes, StandardCharsets.UTF_8).replace("\0", "");

                        if (sectName.startsWith("__debug")) {
                            long size = cmds.getLong(sectOffset + 40);
                            int fileOffset = cmds.getInt(sectOffset + 48);
                            int align = cmds.getInt(sectOffset + 52);

                            byte[] content = new byte[(int) size];
                            file.seek(fileOffset);
                            file.read(content);
                            dwarfSections.add(new DwarfSectionData(sectName, content, align));
                        }
                    }
                }
                offset += cmdsize;
            }
        }
        return dwarfSections;
    }

    /**
     * Builds a complete dSYM Mach-O file from executable info and DWARF sections.
     *
     * @param execInfo metadata from the executable (UUID, CPU type, segments)
     * @param dwarfSections DWARF sections extracted from the object file
     * @return byte array containing the complete dSYM Mach-O file
     */
    private static byte[] buildDsymFile(MachOExecutableInfo execInfo, List<DwarfSectionData> dwarfSections) {
        ByteOrder order = ByteOrder.LITTLE_ENDIAN;

        // Calculate load commands size
        int loadCmdsSize = 24 + 24;  // LC_UUID + LC_SYMTAB
        for (MachOExecutableInfo.SegmentInfo seg : execInfo.segments) {
            loadCmdsSize += 72 + seg.sections.size() * 80;
        }
        loadCmdsSize += 72 + dwarfSections.size() * 80;  // __DWARF segment

        int headerAndCmds = 32 + loadCmdsSize;
        int maxSectionAlign = 1;
        for (DwarfSectionData ds : dwarfSections) {
            int alignment = 1 << ds.align;
            if (alignment > maxSectionAlign) {
                maxSectionAlign = alignment;
            }
        }

        int dwarfDataOffset = alignUp(headerAndCmds, Math.max(8, maxSectionAlign));

        int totalDwarfSize = 0;
        for (DwarfSectionData ds : dwarfSections) {
            totalDwarfSize = alignUp(totalDwarfSize, 1 << ds.align);
            totalDwarfSize += ds.content.length;
        }

        ByteBuffer buf = ByteBuffer.allocate(dwarfDataOffset + totalDwarfSize);
        buf.order(order);

        // Write mach_header_64
        int ncmds = 2 + execInfo.segments.size() + 1;
        buf.putInt(MH_MAGIC_64);
        buf.putInt(execInfo.cpuType);
        buf.putInt(execInfo.cpuSubType);
        buf.putInt(MH_DSYM);
        buf.putInt(ncmds);
        buf.putInt(loadCmdsSize);
        buf.putInt(0);  // flags
        buf.putInt(0);  // reserved

        // Write LC_UUID
        buf.putInt(LC_UUID);
        buf.putInt(24);
        buf.put(execInfo.uuid);

        // Write LC_SYMTAB (zeroed)
        buf.putInt(LC_SYMTAB);
        buf.putInt(24);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);
        buf.putInt(0);

        // Write executable segments with filesize=0
        for (MachOExecutableInfo.SegmentInfo seg : execInfo.segments) {
            writeSegment(buf, seg, 0, 0);
        }

        // Write __DWARF segment
        writeDwarfSegment(buf, dwarfSections, dwarfDataOffset, totalDwarfSize);

        // Pad to DWARF data offset
        while (buf.position() < dwarfDataOffset) {
            buf.put((byte) 0);
        }

        // Write DWARF section data
        int currentOffset = dwarfDataOffset;
        for (DwarfSectionData ds : dwarfSections) {
            int alignedOffset = alignUp(currentOffset, 1 << ds.align);
            while (buf.position() < alignedOffset) {
                buf.put((byte) 0);
            }
            buf.put(ds.content);
            currentOffset = alignedOffset + ds.content.length;
        }

        return buf.array();
    }

    /**
     * Writes a segment load command to the buffer.
     */
    private static void writeSegment(ByteBuffer buf, MachOExecutableInfo.SegmentInfo seg,
                                      long fileoff, long filesize) {
        int cmdsize = 72 + seg.sections.size() * 80;
        buf.putInt(LC_SEGMENT_64);
        buf.putInt(cmdsize);
        writeFixedString(buf, seg.name, 16);
        buf.putLong(seg.vmaddr);
        buf.putLong(seg.vmsize);
        buf.putLong(fileoff);
        buf.putLong(filesize);
        buf.putInt(seg.maxprot);
        buf.putInt(seg.initprot);
        buf.putInt(seg.sections.size());
        buf.putInt(0);  // flags

        for (MachOExecutableInfo.SectionInfo sect : seg.sections) {
            writeFixedString(buf, sect.sectname, 16);
            writeFixedString(buf, sect.segname, 16);
            buf.putLong(sect.addr);
            buf.putLong(sect.size);
            buf.putInt(0);  // offset
            buf.putInt(0);  // align
            buf.putInt(0);  // reloff
            buf.putInt(0);  // nreloc
            buf.putInt(0);  // flags
            buf.putInt(0);  // reserved1
            buf.putInt(0);  // reserved2
            buf.putInt(0);  // reserved3
        }
    }

    /**
     * Writes the __DWARF segment load command to the buffer.
     */
    private static void writeDwarfSegment(ByteBuffer buf, List<DwarfSectionData> sections,
                                           int fileoff, int totalSize) {
        int cmdsize = 72 + sections.size() * 80;
        buf.putInt(LC_SEGMENT_64);
        buf.putInt(cmdsize);
        writeFixedString(buf, "__DWARF", 16);
        buf.putLong(0);  // vmaddr
        buf.putLong(totalSize);  // vmsize
        buf.putLong(fileoff);
        buf.putLong(totalSize);
        buf.putInt(0);  // maxprot
        buf.putInt(0);  // initprot
        buf.putInt(sections.size());
        buf.putInt(0);  // flags

        int currentOffset = fileoff;
        for (DwarfSectionData ds : sections) {
            currentOffset = alignUp(currentOffset, 1 << ds.align);
            writeFixedString(buf, ds.name, 16);
            writeFixedString(buf, "__DWARF", 16);
            buf.putLong(0);  // addr
            buf.putLong(ds.content.length);
            buf.putInt(currentOffset);
            buf.putInt(ds.align);
            buf.putInt(0);  // reloff
            buf.putInt(0);  // nreloc
            buf.putInt(0);  // flags
            buf.putInt(0);  // reserved1
            buf.putInt(0);  // reserved2
            buf.putInt(0);  // reserved3
            currentOffset += ds.content.length;
        }
    }

    private static int alignUp(int value, int alignment) {
        int mask = alignment - 1;
        return (value + mask) & ~mask;
    }

    /**
     * Writes a fixed-length string to the buffer, padding with zeros.
     */
    private static void writeFixedString(ByteBuffer buf, String s, int length) {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        buf.put(bytes, 0, Math.min(bytes.length, length));
        for (int i = bytes.length; i < length; i++) {
            buf.put((byte) 0);
        }
    }

    /**
     * Creates the Info.plist file for the dSYM bundle.
     */
    private static void createInfoPlist(Path dsymPath, String imageName) throws IOException {
        Path infoPlistPath = dsymPath.resolve("Contents/Info.plist");
        String plist = String.format("""
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
            """, imageName.replaceAll("[^a-zA-Z0-9._-]", "_"));
        Files.writeString(infoPlistPath, plist);
    }

    /**
     * Reads the LC_UUID load command from a Mach-O file.
     * This method is kept for compatibility with existing code that may need to read UUIDs.
     *
     * @param path the path to the Mach-O file
     * @return the 16-byte UUID, or null if not found
     */
    @SuppressWarnings("unused")
    private static byte[] readMachOUuid(Path path) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < 32) {
                return null; // File too small for Mach-O header
            }

            ByteBuffer header = ByteBuffer.allocate(32);
            file.read(header.array());

            int magic = header.getInt(0);
            ByteOrder order;
            /*
             * ByteBuffer defaults to BIG_ENDIAN. The magic number tells us the file's byte order:
             * - If we read MH_MAGIC_64 (0xfeedfacf), the file matches our default (big-endian)
             * - If we read MH_CIGAM_64 (0xcffaedfe), the file is in opposite order (little-endian)
             */
            if (magic == MH_MAGIC_64) {
                order = ByteOrder.BIG_ENDIAN;
            } else if (magic == MH_CIGAM_64) {
                order = ByteOrder.LITTLE_ENDIAN;
            } else {
                return null; // Not a 64-bit Mach-O file
            }
            header.order(order);
            header.rewind();

            // Re-read header fields with correct byte order
            header.getInt(); // skip magic
            header.getInt(); // skip cputype
            header.getInt(); // skip cpusubtype
            header.getInt(); // skip filetype
            int ncmds = header.getInt();
            int sizeofcmds = header.getInt();

            if (sizeofcmds <= 0 || sizeofcmds > file.length() - 32) {
                return null; // Invalid sizeofcmds
            }

            // Skip past header to load commands (header is 32 bytes for 64-bit)
            file.seek(32);
            ByteBuffer cmds = ByteBuffer.allocate(sizeofcmds);
            cmds.order(order);
            file.read(cmds.array());

            int offset = 0;
            for (int i = 0; i < ncmds && offset + 8 <= sizeofcmds; i++) {
                int cmd = cmds.getInt(offset);
                int cmdsize = cmds.getInt(offset + 4);

                if (cmdsize <= 0) {
                    break; // Invalid command size
                }

                if (cmd == LC_UUID && offset + 24 <= sizeofcmds) {
                    byte[] uuid = new byte[16];
                    cmds.position(offset + 8);
                    cmds.get(uuid);
                    return uuid;
                }

                offset += cmdsize;
            }

            return null; // No LC_UUID found
        }
    }
}
