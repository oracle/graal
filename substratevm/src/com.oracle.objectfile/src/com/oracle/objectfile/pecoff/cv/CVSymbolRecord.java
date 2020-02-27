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

import com.oracle.objectfile.pecoff.cv.DebugInfoBase.FileEntry;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;

import static com.oracle.objectfile.pecoff.cv.CVConstants.GRAAL_SOURCE_BASE;
import static com.oracle.objectfile.pecoff.cv.CVConstants.JDK_SOURCE_BASE;

/*
 * A Symbol record is a top-level record in the CodeView .debug$S section
 */
abstract class CVSymbolRecord implements CVDebugConstants {

    CVSections cvSections;
    protected int pos;
    protected final int type;

    CVSymbolRecord(CVSections cvSections, int type) {
        this.cvSections = cvSections;
        this.type = type;
    }

    int computeFullSize(int pos) {
        this.pos = pos;
        pos += Integer.BYTES * 2;
        return computeSize(pos);
    }

    int computeFullContents(byte[] buffer, int pos) {
        pos = CVUtil.putInt(type, buffer, pos);
        int lenPos = pos;
        pos = computeContents(buffer, pos + Integer.BYTES);
        /* length does not include debug record header (4 bytes record id + 4 bytes length) */
        CVUtil.putInt(pos - lenPos - Integer.BYTES, buffer, lenPos);
        return pos;
    }

    protected abstract int computeSize(int pos);
    protected abstract int computeContents(byte[] buffer, int pos);

    @Override
    public String toString() {
        return "CVSymbolRecord(type=" + type + ",pos=" + pos + ")";
    }

    public void dump(PrintStream out) {
        out.format("%s\n", this);
    }

    static final class CVStringTableRecord extends CVSymbolRecord {

        private final CVSymbolSectionImpl.CVStringTable stringTable;

        CVStringTableRecord(CVSections cvSections, CVSymbolSectionImpl.CVStringTable stringTable) {
            super(cvSections, DEBUG_S_STRINGTABLE);
            this.stringTable = stringTable;
        }

        int add(String string) {
            return stringTable.add(string);
        }

        @Override
        public int computeSize(int pos) {
            return computeContents(null, pos);
        }

        @Override
        public int computeContents(byte[] buffer, int pos) {
            for (CVSymbolSectionImpl.CVStringTable.StringTableEntry entry : stringTable.values()) {
                pos = CVUtil.putUTF8StringBytes(entry.text, buffer, pos);
            }
            return pos;
        }

        @Override
        public String toString() {
            return String.format("CVStringTableRecord(type=0x%04x pos=0x%06x size=%d)", type, pos, stringTable.size());
        }

        @Override
        public void dump(PrintStream out) {
            int idx = 0;
            out.format("%s:\n", this);
            for (CVSymbolSectionImpl.CVStringTable.StringTableEntry entry : stringTable.values()) {
                out.format("%4d 0x%08x %s\n", idx, entry.offset, entry.text);
                idx += 1;
            }
        }
    }

    static final class CVFileRecord extends CVSymbolRecord {

        static final boolean debug = false;

        static final byte CHECKSUM_NONE = 0x00;
        static final byte CHECKSUM_MD5 = 0x01;
        static final byte CB_VALUE = 0x10;

        static final int FILE_RECORD_LENGTH = 24;

        static final int CHECKSUM_LENGTH = 16;
        static final byte[] EMPTY_CHECKSUM = new byte[CHECKSUM_LENGTH];

        final CVSections cvSections;
        final CVSymbolSectionImpl.CVStringTable strings;

        CVFileRecord(CVSections cvSections, CVSymbolSectionImpl.CVStringTable strings) {
            super(cvSections, DEBUG_S_FILECHKSMS);
            this.cvSections = cvSections;
            this.strings = strings;
        }

        /*
         * Convert a simple path into an absolute path by determining if it's 
         * part of Graal, the JDK, or use code.
         * 
         * This method is incompletely implemented.
         * TODO: replace this with the new SourceCache system
         */
        private String fixPath(String fn) {
            String substrateDir = GRAAL_SOURCE_BASE + "substratevm\\src\\";
            String compilerDir = GRAAL_SOURCE_BASE + "compiler\\src\\";
            String newFn;
            if (fn.startsWith("com/oracle/svm/core/snippets")) {
                newFn = substrateDir + "com.oracle.svm.core.snippets\\src\\" + fn.replace("/", "\\");
            } else if (fn.startsWith("org/graalvm/compiler/replacements")) {
                newFn = compilerDir + "org.graalvm.compiler.replacements\\src\\" + fn.replace("/", "\\");
            } else if (fn.startsWith("com/oracle/svm/core/genscavenge")) {
                newFn = substrateDir + "com.oracle.svm.core.genscavenge\\src\\" + fn.replace("/", "\\");
            } else if (fn.startsWith("com/oracle/svm/core")) {
                newFn = substrateDir + "com.oracle.svm.core\\src\\" + fn.replace("/", "\\");
            } else if (CVRootPackages.isJavaFile(fn)) {
                newFn = JDK_SOURCE_BASE + fn.replace("/", "\\");
            } else {
                newFn = fn;
            }
            return newFn;
        }

        @Override
        public int computeSize(int initialPos) {
            if (cvSections.getFiles().isEmpty()) {
                return initialPos;
            }
            /* first, insert fileIds into file table for use by line number table */
            int fileId = 0;
            for (FileEntry entry : cvSections.getFiles()) {
                entry.setFileId(fileId);
                strings.add(fixPath(entry.getFileName())); // create required stringtable entries
                fileId += FILE_RECORD_LENGTH;
            }
            return initialPos + (cvSections.getFiles().size() * FILE_RECORD_LENGTH);
        }

        private int put(FileEntry entry, byte[] buffer, int initialPos) {
            String fn = fixPath(entry.getFileName());
            int stringId = strings.add(fn);
            int pos = CVUtil.putInt(stringId, buffer, initialPos); /* stringtable index */
            pos = CVUtil.putByte(CB_VALUE, buffer, pos); /* Cb (unknown what this is) */
            byte[] checksum = calculateMD5Sum(fn);
            if (checksum != null) {
                pos = CVUtil.putByte(CHECKSUM_MD5, buffer, pos); /* checksum type (0x01 == MD5) */
                pos = CVUtil.putBytes(checksum, buffer, pos);
            } else {
                pos = CVUtil.putByte(CHECKSUM_NONE, buffer, pos);
                pos = CVUtil.putBytes(EMPTY_CHECKSUM, buffer, pos);
            }
            pos = CVUtil.align4(pos);
            return pos;
        }

        private byte[] calculateMD5Sum(String fn) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                md.update(Files.readAllBytes(Paths.get(fn)));
                return md.digest();
            } catch (NoSuchFileException e) {
                return null;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public int computeContents(byte[] buffer, int pos) {
            CVUtil.debug("file computeContents(%d) nf=%d\n", pos, cvSections.getFiles().size());
            for (FileEntry entry : cvSections.getFiles()) {
                pos = put(entry, buffer, pos);
            }
            return pos;
        }

        @Override
        public String toString() {
            return "CVFileRecord(type=" + type + ",pos=" + pos + ", size=" + 999 + ")";
        }

        @Override
        public void dump(PrintStream out) {
            int idx = 0;
            int offset = 0;
            out.format("%s:\n", this);
            for (FileEntry entry : cvSections.getFiles()) {
                out.format("%4d 0x%08x %2d %2d %s\n", idx, offset, 0x10, 1, entry.getFileName());
                idx += 1;
                offset += 24;
            }
        }
    }
}
