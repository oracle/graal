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

import java.util.ArrayList;

/*
 * A line record (DEBUG_S_LINES) consists of a list of (file block record + subrecords).
 * Graal will generate one CVLineRecord per function.
 */
final class CVLineRecord extends CVSymbolRecord {

    /* Header: addr (4 bytes):section (2 bytes) flags (2 bytes) chunck length (4 bytes). */
    private static final int LINE_RECORD_HEADER_SIZE = Integer.BYTES + Short.BYTES * 2 + Integer.BYTES;

    private static final int DEFAULT_LINE_BLOCK_COUNT = 100;
    private static final int DEFAULT_LINE_ENTRY_COUNT = 100;

    /* Has columns flag = 0x80 - not supported. */
    private static final short CB_HAS_NO_COLUMNS_FLAG = 0x00;

    private final String symbolName;
    private final ArrayList<FileBlock> fileBlocks = new ArrayList<>(DEFAULT_LINE_BLOCK_COUNT);

    CVLineRecord(CVDebugInfo cvDebugInfo, String symbolName) {
        super(cvDebugInfo, CVDebugConstants.DEBUG_S_LINES);
        this.symbolName = symbolName;
    }

    void addNewFile(int fileId) {
        fileBlocks.add(new FileBlock(fileId));
    }

    void addNewLine(int addr, int line) {
        fileBlocks.get(fileBlocks.size() - 1).addEntry(new LineEntry(addr, line));
    }

    int getCurrentFileId() {
        assert !fileBlocks.isEmpty();
        return fileBlocks.get(fileBlocks.size() - 1).fileId;
    }

    @Override
    protected int computeSize(int initialPos) {
        return computeContents(null, initialPos);
    }

    @Override
    protected int computeContents(byte[] buffer, int initialPos) {
        /* Line record header. */
        int pos = computeHeader(buffer, initialPos);
        /* All blocks. */
        for (FileBlock fileBlock : fileBlocks) {
            pos = fileBlock.computeContents(buffer, pos);
        }
        return pos;
    }

    private int computeHeader(byte[] buffer, int initialPos) {

        if (buffer == null) {
            return initialPos + LINE_RECORD_HEADER_SIZE;
        }

        assert symbolName != null;
        int pos = initialPos;

        /* Emit addr:section relocation records. */
        pos = cvDebugInfo.getCVSymbolSection().markRelocationSite(buffer, pos, symbolName);

        /* Emit flags. */
        pos = CVUtil.putShort(CB_HAS_NO_COLUMNS_FLAG, buffer, pos);

        /* Length of this chunk in object file (= highAddr since it's zero based. */
        assert !fileBlocks.isEmpty();
        int length = fileBlocks.get(fileBlocks.size() - 1).getHighAddr();
        pos = CVUtil.putInt(length, buffer, pos);
        return pos;
    }

    boolean isEmpty() {
        return fileBlocks.isEmpty();
    }

    @Override
    public String toString() {
        return String.format("CVLineRecord(type=0x%04x pos=0x%05x size=0x%d)", type, recordStartPosition, fileBlocks.size());
    }

    /*
     * FileBlock is a section of contiguous code in a compilation unit, associated with a single
     * source file. If a function includes inlined code, that code needs its own FileBlock,
     * surrounded by FileBlocks describing the enclosing source file. A FileBlock consists of a list
     * of LineEntries.
     */
    private static class FileBlock {

        /* Fileblock header: fileId (4 bytes) lineEntry count (4 bytes) tablesize (4 bytes) */
        static final int FILE_BLOCK_HEADER_SIZE = Integer.BYTES * 3;

        /* Initial value (that can never occur) for previousLine when there is no previous line. */
        static final int NO_PREVIOUS_LINE = -2;

        private final ArrayList<LineEntry> lineEntries = new ArrayList<>(DEFAULT_LINE_ENTRY_COUNT);
        private final int fileId;
        private int previousLine;

        FileBlock(int fileId) {
            this.fileId = fileId;
            this.previousLine = NO_PREVIOUS_LINE;
        }

        void addEntry(LineEntry le) {
            assert le.lineAndFlags != NO_PREVIOUS_LINE;
            /* Only add an entry if the line number has changed. */
            if (le.lineAndFlags != previousLine) {
                lineEntries.add(le);
                previousLine = le.lineAndFlags;
            }
        }

        int computeContents(byte[] buffer, int initialPos) {
            if (buffer == null) {
                return computeSize(initialPos);
            }
            int pos = initialPos;
            pos = CVUtil.putInt(fileId, buffer, pos);
            pos = CVUtil.putInt(lineEntries.size(), buffer, pos);
            pos = CVUtil.putInt(computeSize(0), buffer, pos);
            for (LineEntry lineEntry : lineEntries) {
                pos = lineEntry.computeContents(buffer, pos);
            }
            return pos;
        }

        int computeSize(int initialPos) {
            return initialPos + FILE_BLOCK_HEADER_SIZE + LineEntry.LINE_ENTRY_SIZE * lineEntries.size();
        }

        int getHighAddr() {
            assert !lineEntries.isEmpty();
            return lineEntries.get(lineEntries.size() - 1).addr;
        }
    }

    /*
     * LineEntry associates some object code (at 'addr', relative to the start of this DEBUG_S_LINES
     * record) with a source line in the current FileBlock file.
     */
    private static class LineEntry {

        /* Entry: address(4 bytes) line number+flags (4 bytes) */
        static final int LINE_ENTRY_SIZE = 2 * Integer.BYTES;

        int addr;
        int lineAndFlags;

        LineEntry(int addr, int line, int deltaEnd, boolean isStatement) {
            this.addr = addr;
            assert line <= 0xffffff;
            assert line >= 0;
            assert deltaEnd <= 0x7f;
            assert deltaEnd >= 0;
            lineAndFlags = line | (deltaEnd << 24) | (isStatement ? 0x80000000 : 0);
        }

        LineEntry(int addr, int line) {
            this(addr, line, 0, false);
        }

        int computeContents(byte[] buffer, int initialPos) {
            int pos = initialPos;
            pos = CVUtil.putInt(addr, buffer, pos);
            pos = CVUtil.putInt(lineAndFlags, buffer, pos);
            return pos;
        }
    }
}
