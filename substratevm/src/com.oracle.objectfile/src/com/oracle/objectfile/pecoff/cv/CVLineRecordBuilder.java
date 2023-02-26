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
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.objectfile.debugentry.Range;

import java.util.Iterator;

/*
 * In CV4, the line table consists of a series of file headers followed by line number entries.
 * If this is a different file, then update the length of the previous file header, write the
 * new file header and write the new range At the very end, make sure we update the last file header.
 */
public class CVLineRecordBuilder {

    private final CVDebugInfo cvDebugInfo;
    private CVLineRecord lineRecord;
    private CompiledMethodEntry compiledEntry;

    CVLineRecordBuilder(CVDebugInfo cvDebugInfo) {
        this.cvDebugInfo = cvDebugInfo;
    }

    public void debug(String format, Object... args) {
        cvDebugInfo.getCVSymbolSection().verboseLog(format, args);
    }

    /**
     * Build line number records for a function.
     *
     * @param entry function to build line number table for
     * @return CVLineRecord containing any entries generated, or null if no entries generated
     */
    CVLineRecord build(CompiledMethodEntry entry) {
        this.compiledEntry = entry;
        Range primaryRange = compiledEntry.getPrimary();

        debug("DEBUG_S_LINES linerecord for 0x%05x file: %s:%d", primaryRange.getLo(), primaryRange.getFileName(), primaryRange.getLine());
        this.lineRecord = new CVLineRecord(cvDebugInfo, primaryRange.getSymbolName());
        debug("CVLineRecord.computeContents: processing primary range %s", primaryRange);

        processRange(primaryRange);
        Iterator<Range> iterator = compiledEntry.leafRangeIterator();
        while (iterator.hasNext()) {
            Range subRange = iterator.next();
            debug("CVLineRecord.computeContents: processing range %s", subRange);
            processRange(subRange);
        }
        return lineRecord;
    }

    /**
     * Merge input Range structures into line number table. The Range structures are assumed to be
     * ordered by ascending address.
     *
     * @param range to be merged or added to line number record
     */
    private void processRange(Range range) {

        FileEntry file = range.getFileEntry();
        if (file == null) {
            debug("  processRange: range has no file: %s", range);
            return;
        }

        if (range.getLine() < 0) {
            debug("  processRange: ignoring: bad line number: %d", range.getLine());
            return;
        }

        int fileId = cvDebugInfo.getCVSymbolSection().getFileTableRecord().addFile(file);
        if (lineRecord.isEmpty() || lineRecord.getCurrentFileId() != fileId) {
            debug("  processRange: addNewFile: %s", file);
            lineRecord.addNewFile(fileId);
        }

        /* Add line record. */
        int lineLoAddr = range.getLo() - compiledEntry.getPrimary().getLo();
        int line = Math.max(range.getLine(), 1);
        debug("  processRange:   addNewLine: 0x%05x-0x%05x %s", lineLoAddr, range.getHi() - compiledEntry.getPrimary().getLo(), line);
        lineRecord.addNewLine(lineLoAddr, line);
    }
}
