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

package com.oracle.objectfile.pecoff.cv;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SIGNATURE_C13;
import static com.oracle.objectfile.pecoff.cv.CVConstants.CV_SYMBOL_SECTION_NAME;

public final class CVSymbolSectionImpl extends CVSectionImpl {

    private static final int CV_VECTOR_DEFAULT_SIZE = 200;
    private static final int CV_STRINGTABLE_DEFAULT_SIZE = 200;

    private CVSections cvSections;
    private CVFileRecord fileRecord;

    private ArrayList<CVSymbolRecord> cvRecords = new ArrayList<>(CV_VECTOR_DEFAULT_SIZE);
    private CVStringTable stringTable = new CVStringTable(CV_STRINGTABLE_DEFAULT_SIZE);

    CVSymbolSectionImpl(CVSections cvSections) {
        this.cvSections = cvSections;
    }

    @Override
    public String getSectionName() {
        return CV_SYMBOL_SECTION_NAME;
    }

    /*
     * the CodeView symbol section ("debug$S") is actually a list of records containing sub-records
     */
    @Override
    public void createContent() {
        info("CVSymbolSectionImpl.createContent() adding records");
        addRecords();
        info("CVSymbolSectionImpl.createContent() start");
        int pos = 0;
        /* add header size */
        pos += Integer.BYTES;
        /* add sum of all record sizes */
        for (CVSymbolRecord record : cvRecords) {
            info("CVSymbolSectionImpl.createContent() computeFullSize %s", record);
            pos = CVUtil.align4(pos);
            pos = record.computeFullSize(pos);
        }
        /* create a buffer that holds it all */
        byte[] buffer = new byte[pos];
        super.setContent(buffer);
        info("CVSymbolSectionImpl.createContent() end");
    }

    @Override
    public void writeContent() {
        info("CVSymbolSectionImpl.writeContent() start");
        byte[] buffer = getContent();
        int pos = 0;
        /* write section header */
        pos = CVUtil.putInt(CV_SIGNATURE_C13, buffer, pos);
        /* write all records */
        for (CVSymbolRecord record : cvRecords) {
            info("CVSymbolSectionImpl.createContent() computeFullContentt %s", record);
            pos = CVUtil.align4(pos);
            pos = record.computeFullContents(buffer, pos);
        }
        info("CVSymbolSectionImpl.writeContent() end");
    }

    private void addRecords() {
        addPrologueRecords();
        addFunctionRecords();
        addTypeRecords();
        addFileRecords();
        addStringTableRecord();
    }

    private void addPrologueRecords() {
        CVSymbolRecord prologue = new CVSymbolSubsection(cvSections) {
            @Override
            void addSubrecords() {
                CVSymbolSubrecord.CVObjectNameRecord objectNameRecord = new CVSymbolSubrecord.CVObjectNameRecord(cvSections);
                if (objectNameRecord.isValid()) {
                    addRecord(objectNameRecord);
                }
                addRecord(new CVSymbolSubrecord.CVCompile3Record(cvSections));
                addRecord(new CVSymbolSubrecord.CVEnvBlockRecord(cvSections));
            }
        };
        addRecord(prologue);
    }

    private void addFunctionRecords() {
        new CVSymbolRecordBuilder(cvSections).build();
    }

    private void addTypeRecords() {
        /* not yet implemented.  S_UDT, etc */
        //CVSymbolRecord externs = new CVSymbolSubsection.CVExternalSymbolRecord(cvSections);
        //addRecord(externs);
    }

    private void addFileRecords() {
        addRecord(getFileRecord());
    }

    CVFileRecord getFileRecord() {
        if (fileRecord == null) {
            this.fileRecord = new CVFileRecord(cvSections, stringTable);
        }
        return fileRecord;
    }

    private void addStringTableRecord() {
        CVSymbolRecord stringTableRecord = new CVStringTableRecord(cvSections, stringTable);
        addRecord(stringTableRecord);
    }

    /* TODO: use ...objectfile.debugentry.StringTable instead */
    static final class CVStringTable {
        static final class StringTableEntry {
            public int offset;
            public String text;
            StringTableEntry(int offset, String text) {
                this.offset = offset;
                this.text = text;
            }
        }

        /* using LinkedHashMap so order is maintained when writing string table */
        private final HashMap<String, StringTableEntry> strings;
        private int currentOffset = 0;

        CVStringTable(int startSize) {
            strings = new LinkedHashMap<>(startSize);
            /* ensure that the empty string has index 0 */
            add("");
        }

        int add(String s) {
            StringTableEntry newEntry = new StringTableEntry(currentOffset, s);
            StringTableEntry entry = strings.putIfAbsent(s, newEntry);
            if (entry == null) {
                int utf8Length = s.getBytes(UTF_8).length; /* TODO: getting the enecoded size should be made more efficient */
                currentOffset += utf8Length + 1;
            }
            return entry == null ? newEntry.offset : entry.offset;
        }

        Collection<StringTableEntry> values() {
            return strings.values();
        }

        int size() {
            return strings.size();
        }
    }

    void addRecord(CVSymbolRecord record) {
        cvRecords.add(record);
    }
}
