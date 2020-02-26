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
import com.oracle.objectfile.pecoff.cv.DebugInfoBase.PrimaryEntry;
import com.oracle.objectfile.ObjectFile;

import java.util.ArrayList;

/*
 * a line record (DEBUG_S_LINES) consists of a list of (file block record + subrecords)
 * Graal will generate one CVLineRecord per function.
 */
final class CVLineRecord extends CVSymbolRecord {

    private static final boolean HAS_COLUMNS = false;

    private static final int DEFAULT_LINE_BLOCK_COUNT = 100;
    private static final int DEFAULT_LINE_ENTRY_COUNT = 100;

    private static final short CB_HAS_COLUMNS_FLAG = 0x01;
    private static final short CB_HAS_NO_COLUMNS_FLAG = 0x00;

    private String symbolName;
    private PrimaryEntry primaryEntry;
    private ArrayList<FileBlock> fileBlocks = new ArrayList<>(DEFAULT_LINE_BLOCK_COUNT);

    /*
     * FileBlock is a section of contiguous code in a compilation unit, associated with a single source file.
     * if a function includes inlined code, that code needs its own FileBlock, surroneded by FileBlocks descibing the inclosing source file
     * A fileBlock consists of a list of LineEntries
     */
    private static class FileBlock {

        ArrayList<LineEntry> lineEntries = new ArrayList<>(DEFAULT_LINE_ENTRY_COUNT);
        int highAddr = 0;
        FileEntry file;

        FileBlock(FileEntry file) {
            this.file = file;
        }

        void addEntry(LineEntry le) {
            highAddr = Math.max(highAddr, le.addr);
            lineEntries.add(le);
        }

        int computeContents(byte[] buffer, int initialPos) {
            int pos = initialPos;
            pos = CVUtil.putInt(file.getFileId(), buffer, pos);
            pos = CVUtil.putInt(lineEntries.size(), buffer, pos);
            /* if HAS_COLUMNS is true, this formula is incorrect */
            assert !HAS_COLUMNS;
            pos = CVUtil.putInt(Integer.BYTES * 3 + lineEntries.size() * LineEntry.LINE_ENTRY_SIZE, buffer, pos);
            for (LineEntry lineEntry : lineEntries) {
                pos = lineEntry.computeContents(buffer, pos);
            }
            return pos;
        }

        int computeSize(int initialPos) {
            /* if HAS_COLUMNS is true, this formula is incorrect */
            assert !HAS_COLUMNS;
            return initialPos + Integer.BYTES * 3 + LineEntry.LINE_ENTRY_SIZE * lineEntries.size();
        }

        int getHighAddr() {
            return highAddr;
        }
    }

    /*
     * LineEntry associates some object code (at 'addr', relative to the start of this DEBUG_S_LINES record)
     * with a source line in the current FileBlock file
     */
    static class LineEntry {

        static final int LINE_ENTRY_SIZE = 2 * Integer.BYTES;

        int addr;
        int lineAndFLags;
/*
        LineEntry(int addr, int line, int deltaEnd, boolean isStatement) {
            this.addr = addr;
            assert line <= 0xffffff;
            assert line >= 0;
            assert deltaEnd <= 0x7f;
            assert deltaEnd >= 0;
            lineAndFLags = line | (deltaEnd << 24) | (isStatement ? 0x80000000 : 0);
        }
*/
        LineEntry(int addr, int line) {
            this.addr = addr;
            this.lineAndFLags = line;
        }

        int computeContents(byte[] buffer, int initialPos) {
            int pos = initialPos;
            pos = CVUtil.putInt(addr, buffer, pos);
            pos = CVUtil.putInt(lineAndFLags, buffer, pos);
            return pos;
        }
    }

    CVLineRecord(CVSections cvSections, String symbolName, PrimaryEntry primaryEntry) {
        super(cvSections, DEBUG_S_LINES);
        this.primaryEntry = primaryEntry;
        this.symbolName = symbolName;
    }

    void addNewFile(FileEntry file) {
        fileBlocks.add(new FileBlock(file));
    }

    void addNewLine(int addr, int line) {
        fileBlocks.get(fileBlocks.size() - 1).addEntry(new LineEntry(addr, line));
    }

    @Override
    protected int computeSize(int startPos) {
        /* header */
        int pos = startPos + Integer.BYTES + Short.BYTES * 2 + Integer.BYTES;
        /* all blocks */
        for (FileBlock fileBlock : fileBlocks) {
            pos = fileBlock.computeSize(pos);
        }
        return pos;
    }

    @Override
    protected int computeContents(byte[] buffer, int initialPos) {
        int pos = initialPos;

        assert symbolName != null;
        /* can't handle columns yet */
        assert !HAS_COLUMNS;

        if (buffer != null) {
            cvSections.getCVSymbolSection().markRelocationSite(pos, 4, ObjectFile.RelocationKind.SECREL, symbolName, false, 1L);
        }
        pos = CVUtil.putInt(0, buffer, pos);
        if (buffer != null) {
            cvSections.getCVSymbolSection().markRelocationSite(pos, 2, ObjectFile.RelocationKind.SECTION, symbolName, false, 1L);
        }
        pos = CVUtil.putShort((short) 0, buffer, pos);
        final short flags = HAS_COLUMNS ? CB_HAS_COLUMNS_FLAG : CB_HAS_NO_COLUMNS_FLAG;
        pos = CVUtil.putShort(flags, buffer, pos);      /* flags */
        final int cbConPos = pos;                       /* save position of length int32 */
        pos = CVUtil.putInt(0, buffer, pos);         /* highAddr = length of this chunk in object file (fill in correctly later) */
        int highAddr = 0;
        for (FileBlock fileBlock : fileBlocks) {
            highAddr = Math.max(highAddr, fileBlock.getHighAddr());
            pos = fileBlock.computeContents(buffer, pos);
        }
        CVUtil.putInt(highAddr, buffer, cbConPos);
        return pos;
    }

    @Override
    public String toString() {
        return String.format("CVLineRecord(type=0x%04x pos=0x%05x size=0x%d)", type, pos, fileBlocks.size());
    }
}
