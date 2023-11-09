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

import com.oracle.objectfile.debugentry.FileEntry;
import jdk.graal.compiler.debug.GraalError;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

final class CVFileTableRecord extends CVSymbolRecord {

    private static final int FILE_TABLE_INITIAL_SIZE = 200;

    private final CVSymbolSectionImpl.CVStringTable strings;

    private int currentOffset = 0;

    /* Use a LinkedHashMap to maintain insertion order. */
    private final Map<FileEntry, FileRecord> fileEntryToRecordMap = new LinkedHashMap<>(FILE_TABLE_INITIAL_SIZE);

    CVFileTableRecord(CVDebugInfo cvDebugInfo, CVSymbolSectionImpl.CVStringTable strings) {
        super(cvDebugInfo, CVDebugConstants.DEBUG_S_FILECHKSMS);
        this.strings = strings;
    }

    int addFile(FileEntry entry) {
        if (fileEntryToRecordMap.containsKey(entry)) {
            return fileEntryToRecordMap.get(entry).getFileTableId();
        } else {
            /* Create required stringtable entry. */
            int stringTableOffset = strings.add(entry.getFullName());
            fileEntryToRecordMap.put(entry, new FileRecord(entry, currentOffset, stringTableOffset));
            currentOffset += FileRecord.FILE_RECORD_LENGTH;
            return currentOffset - FileRecord.FILE_RECORD_LENGTH;
        }
    }

    @Override
    public int computeSize(int initialPos) {
        return initialPos + (fileEntryToRecordMap.size() * FileRecord.FILE_RECORD_LENGTH);
    }

    @Override
    public int computeContents(byte[] buffer, int initialPos) {
        int pos = initialPos;
        for (FileRecord record : fileEntryToRecordMap.values()) {
            pos = record.put(buffer, pos);
        }
        return pos;
    }

    @Override
    public String toString() {
        return "CVFileRecord(type=" + type + ",pos=" + recordStartPosition + ", size=" + fileEntryToRecordMap.size() + ")";
    }

    private static final class FileRecord {

        static final int FILE_RECORD_LENGTH = 24;

        private static final byte CB_VALUE = 0x10;
        private static final int CHECKSUM_LENGTH = 16;
        private static final byte CHECKSUM_NONE = 0x00;
        private static final byte CHECKSUM_MD5 = 0x01;
        private static final byte[] EMPTY_CHECKSUM = new byte[CHECKSUM_LENGTH];

        private final FileEntry entry;
        private final int fileTableId;
        private final int stringTableId;

        FileRecord(FileEntry entry, int fileTableId, int stringTableId) {
            this.entry = entry;
            this.fileTableId = fileTableId;
            this.stringTableId = stringTableId;
        }

        private int put(byte[] buffer, int initialPos) {
            String fn = entry.getFullName();
            int pos = CVUtil.putInt(stringTableId, buffer, initialPos); /* Stringtable index. */
            pos = CVUtil.putByte(CB_VALUE, buffer, pos); /* Cb (unknown what this is). */
            byte[] checksum = calculateMD5Sum(fn);
            if (checksum != null) {
                pos = CVUtil.putByte(CHECKSUM_MD5, buffer, pos); /* Checksum type (0x01 == MD5). */
                pos = CVUtil.putBytes(checksum, buffer, pos);
            } else {
                pos = CVUtil.putByte(CHECKSUM_NONE, buffer, pos);
                pos = CVUtil.putBytes(EMPTY_CHECKSUM, buffer, pos);
            }
            pos = CVUtil.align4(pos);
            assert pos == initialPos + FILE_RECORD_LENGTH;
            return pos;
        }

        int getFileTableId() {
            return fileTableId;
        }

        /**
         * Calculate the MD5 checksum of a file.
         *
         * @param fn path to file
         * @return a byte array containing the checksum, or null if there was an error reading the
         *         file.
         */
        private static byte[] calculateMD5Sum(String fn) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(Files.readAllBytes(Paths.get(fn)));
                return md.digest();
            } catch (IOException e) {
                return null;
            } catch (NoSuchAlgorithmException e) {
                throw GraalError.shouldNotReachHere(e); // ExcludeFromJacocoGeneratedReport
            }
        }
    }
}
