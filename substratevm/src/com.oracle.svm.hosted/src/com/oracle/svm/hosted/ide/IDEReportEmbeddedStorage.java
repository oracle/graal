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
package com.oracle.svm.hosted.ide;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.macho.MachOObjectFile;
import com.oracle.svm.core.util.UserError;

/** Adds an IDE report envelope and its locator symbols to an ELF or Mach-O image. */
public final class IDEReportEmbeddedStorage {
    public static final String ELF_SECTION_NAME = ".svm_ide_report";
    public static final String MACH_O_SECTION_NAME = "__svm_idereport";
    public static final String MACH_O_SEGMENT_NAME = "__TEXT";
    public static final String REPORT_SYMBOL_NAME = "ide_report";
    public static final String REPORT_LENGTH_SYMBOL_NAME = "ide_report_length";

    private IDEReportEmbeddedStorage() {
    }

    public static void embed(ObjectFile objectFile, byte[] envelope) {
        ensureSupported(objectFile.getFormat());

        byte[] sectionContent = createSectionContent(envelope, objectFile.getByteOrder());
        var sectionImpl = new BasicProgbitsSectionImpl(sectionContent);
        String sectionName = objectFile.getFormat() == ObjectFile.Format.ELF ? ELF_SECTION_NAME : MACH_O_SECTION_NAME;
        ObjectFile.Section section = objectFile.newProgbitsSection(sectionName, Long.BYTES, false, false, sectionImpl);
        if (section instanceof MachOObjectFile.MachOSection machOSection) {
            machOSection.setDestinationSegmentName(MACH_O_SEGMENT_NAME);
        }
        objectFile.createDefinedSymbol(REPORT_SYMBOL_NAME, section, 0, envelope.length, false, true, true);
        objectFile.createDefinedSymbol(REPORT_LENGTH_SYMBOL_NAME, section, envelope.length, Long.BYTES, false, true, true);
    }

    public static void ensureSupported(ObjectFile.Format format) {
        if (format != ObjectFile.Format.ELF && format != ObjectFile.Format.MACH_O) {
            throw UserError.abort("IDE report embed storage is currently supported only for ELF and Mach-O images. Use IDEReportStorage=split on this platform.");
        }
    }

    public static byte[] createSectionContent(byte[] envelope, ByteOrder byteOrder) {
        byte[] content = new byte[Math.addExact(envelope.length, Long.BYTES)];
        System.arraycopy(envelope, 0, content, 0, envelope.length);
        ByteBuffer.wrap(content).order(byteOrder).putLong(envelope.length, envelope.length);
        return content;
    }
}
