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

package com.oracle.objectfile.pecoff.cv;

import com.oracle.objectfile.ObjectFile.RelocationKind;
import jdk.graal.compiler.debug.DebugContext;

import com.oracle.objectfile.io.Utf8;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SIGNATURE_C13;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SYMBOL_SECTION_NAME;

public final class CVSymbolSectionImpl extends CVSectionImpl {

    private static final int CV_VECTOR_DEFAULT_SIZE = 200;
    private static final int CV_STRINGTABLE_DEFAULT_SIZE = 200;

    private final ArrayList<CVSymbolRecord> cvRecords;
    private final CVStringTable stringTable;
    private final CVFileTableRecord fileTableRecord;

    CVSymbolSectionImpl(CVDebugInfo cvDebugInfo) {
        super(cvDebugInfo);
        this.cvRecords = new ArrayList<>(CV_VECTOR_DEFAULT_SIZE);
        this.stringTable = new CVStringTable(CV_STRINGTABLE_DEFAULT_SIZE);
        this.fileTableRecord = new CVFileTableRecord(cvDebugInfo, stringTable);
    }

    @Override
    public String getSectionName() {
        return CV_SYMBOL_SECTION_NAME;
    }

    /*
     * Any (there may be sewveral) CodeView symbol section ("debug$S") is actually a list of
     * records, some of which containing sub-records.
     */
    @Override
    public void createContent(DebugContext debugContext) {
        int pos = 0;
        enableLog(debugContext);
        log("CVSymbolSectionImpl.createContent() adding records");
        addRecords();
        log("CVSymbolSectionImpl.createContent() start");
        /* Add header size. */
        pos += Integer.BYTES;
        /* Add sum of all record sizes. */
        for (CVSymbolRecord record : cvRecords) {
            pos = CVUtil.align4(pos);
            pos = record.computeFullSize(pos);
        }
        /* Create a buffer that holds it all. */
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
        log("CVSymbolSectionImpl.createContent() end");
    }

    @Override
    public void writeContent(DebugContext debugContext) {
        int pos = 0;
        enableLog(debugContext);
        log("CVSymbolSectionImpl.writeContent() start recordcount=%d", cvRecords.size());
        byte[] buffer = getContent();
        /* Write section header. */
        log("  [0x%08x] CV_SIGNATURE_C13", pos);
        pos = CVUtil.putInt(CV_SIGNATURE_C13, buffer, pos);
        /* Write all records. */
        for (CVSymbolRecord record : cvRecords) {
            pos = CVUtil.align4(pos);
            log("  [0x%08x] %s", pos, record.toString());
            record.logContents();
            pos = record.computeFullContents(buffer, pos);
        }
        log("CVSymbolSectionImpl.writeContent() end");
    }

    private void addRecords() {
        addPrologueRecord();
        addFunctionRecords();
        addFileRecord();
        addStringTableRecord();
    }

    private void addPrologueRecord() {
        CVSymbolSubsection prologue = new CVSymbolSubsection(getCvDebugInfo());
        CVSymbolSubrecord.CVObjectNameRecord objectNameRecord = new CVSymbolSubrecord.CVObjectNameRecord(getCvDebugInfo());
        if (objectNameRecord.isValid()) {
            prologue.addRecord(objectNameRecord);
        }
        prologue.addRecord(new CVSymbolSubrecord.CVCompile3Record(getCvDebugInfo()));
        prologue.addRecord(new CVSymbolSubrecord.CVEnvBlockRecord(getCvDebugInfo()));
        addRecord(prologue);
    }

    private void addFunctionRecords() {
        /* This will build and add many records for each function. */
        new CVSymbolSubsectionBuilder(getCvDebugInfo()).build();
    }

    private void addFileRecord() {
        /* Files are added to this record during function record building. */
        addRecord(fileTableRecord);
    }

    CVFileTableRecord getFileTableRecord() {
        return this.fileTableRecord;
    }

    private void addStringTableRecord() {
        CVSymbolRecord stringTableRecord = new CVStringTableRecord(getCvDebugInfo(), stringTable);
        addRecord(stringTableRecord);
    }

    static final class CVStringTable {
        static final class StringTableEntry {
            public int offset;
            public String text;

            StringTableEntry(int offset, String text) {
                this.offset = offset;
                this.text = text;
            }
        }

        /* Use LinkedHashMap so order is maintained when writing string table. */
        private final HashMap<String, StringTableEntry> strings;
        private int currentOffset = 0;

        CVStringTable(int startSize) {
            strings = new LinkedHashMap<>(startSize);
            /* Ensure that the empty string has index 0. */
            add("");
        }

        int add(String s) {
            StringTableEntry newEntry = new StringTableEntry(currentOffset, s);
            StringTableEntry entry = strings.putIfAbsent(s, newEntry);
            if (entry == null) {
                currentOffset += Utf8.utf8Length(s) + 1;
            }
            return entry == null ? newEntry.offset : entry.offset;
        }

        Collection<StringTableEntry> values() {
            return strings.values();
        }

        int size() {
            return strings.size();
        }

        int getCurrentOffset() {
            return currentOffset;
        }
    }

    void addRecord(CVSymbolRecord record) {
        cvRecords.add(record);
    }

    /**
     * Mark an offset:segment relocation site for linker or loader fixup.
     *
     * @param buffer output buffer
     * @param initialPos position of fixup in output buffer
     * @param symbolName symbolname to reference
     * @return new position in output buffer
     */
    public int markRelocationSite(byte[] buffer, int initialPos, String symbolName) {
        return markRelocationSite(buffer, initialPos, symbolName, 0);
    }

    /**
     * Mark an offset:segment + offset relocation site for linker or loader fixup.
     *
     * @param buffer output buffer
     * @param initialPos position of fixup in output buffer
     * @param symbolName symbolname to reference
     * @param offset offset from symbol
     * @return new position in output buffer
     */
    public int markRelocationSite(byte[] buffer, int initialPos, String symbolName, long offset) {
        int pos = markRelocationSite(buffer, initialPos, symbolName, RelocationKind.SECREL_4, offset);
        return markRelocationSite(buffer, pos, symbolName, RelocationKind.SECTION_2, 0);
    }

    private int markRelocationSite(byte[] buffer, int initialPos, String symbolName, RelocationKind kind, long offset) {
        if (buffer != null) {
            markRelocationSite(initialPos, kind, symbolName, offset);
        }
        return initialPos + RelocationKind.getRelocationSize(kind);
    }
}
