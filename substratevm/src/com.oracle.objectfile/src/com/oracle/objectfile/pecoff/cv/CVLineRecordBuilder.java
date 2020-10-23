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

import org.graalvm.compiler.debug.DebugContext;
import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.Range;

/*
 * In CV4, the line table consists of a series of file headers followed by line number entries.
 * If this is a different file, then update the length of the previous file header, write the
 * new file header and write the new range At the very end, make sure we update the last file header.
 */
public class CVLineRecordBuilder {

    private CVDebugInfo cvDebugInfo;
    private DebugContext debugContext;
    private CVLineRecord lineRecord;
    private PrimaryEntry primaryEntry;

    CVLineRecordBuilder(DebugContext theDebugContext, CVDebugInfo cvDebugInfo) {
        this.debugContext = theDebugContext;
        this.cvDebugInfo = cvDebugInfo;
    }

    public void debug(String format, Object... args) {
        cvDebugInfo.getCVSymbolSection().verboseLog(debugContext, format, args);
    }

    /**
     * Build line number records for a function.
     *
     * @param entry function to build line number table for
     * @return CVLineRecord containing any entries generated, or null if no entries generated
     */
    CVLineRecord build(PrimaryEntry entry) {
        this.primaryEntry = entry;
        Range primaryRange = primaryEntry.getPrimary();

        debug("DEBUG_S_LINES linerecord for 0x%05x file: %s:%d\n", primaryRange.getLo(), primaryRange.getFileName(), primaryRange.getLine());
        this.lineRecord = new CVLineRecord(cvDebugInfo, primaryRange.getSymbolName());
        debug("CVLineRecord.computeContents: processing primary range %s\n", primaryRange);

        processRange(primaryRange);
        for (Range subRange : primaryEntry.getSubranges()) {
            debug("CVLineRecord.computeContents: processing range %s\n", subRange);
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

        FileEntry file = cvDebugInfo.findFile(range.getFileAsPath());
        if (file == null) {
            debug("processRange: range has no file: %s\n", range);
            return;
        }

        if (range.getLine() == -1) {
            debug("processRange: ignoring: bad line number\n");
            return;
        }

        int fileId = cvDebugInfo.getCVSymbolSection().getFileTableRecord().addFile(file);
        if (lineRecord.isEmpty() || lineRecord.getCurrentFileId() != fileId) {
            debug("processRange: addNewFile: %s\n", file);
            lineRecord.addNewFile(fileId);
        }

        /* Add line record. */
        /* An optimization would be to merge adjacent line records. */
        int lineLoAddr = range.getLo() - primaryEntry.getPrimary().getLo();
        int line = Math.max(range.getLine(), 1);
        debug("processRange:   addNewLine: 0x%05x %s\n", lineLoAddr, line);
        lineRecord.addNewLine(lineLoAddr, line);
    }
}
