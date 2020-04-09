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

import com.oracle.objectfile.debugentry.FileEntry;
import com.oracle.objectfile.debugentry.PrimaryEntry;
import com.oracle.objectfile.debugentry.Range;

import static com.oracle.objectfile.pecoff.cv.CVConstants.mergeAdjacentLineRecords;
import static com.oracle.objectfile.pecoff.cv.CVConstants.skipGraalInternals;
import static com.oracle.objectfile.pecoff.cv.CVConstants.skipGraalIntrinsics;

public class CVLineRecordBuilder {

    private static final boolean HAS_COLUMNS = false;

    private CVDebugInfo cvDebugInfo;
    private CVLineRecord lineRecord;
    private PrimaryEntry primaryEntry;

    CVLineRecordBuilder(CVDebugInfo cvDebugInfo) {
        this.cvDebugInfo = cvDebugInfo;
    }

    public static void debug(@SuppressWarnings("unused") String format, @SuppressWarnings("unused") Object ... args) {
        //System.out.format(format, args);
    }

    /*
     * In CV4, the line table consists of a series of file headers followed by line number entries
     * to handle this, first we decide if we want to merge this with the previous range (only if same file and start of this range is end of previous range)
     * if we are emitting a new range to the same file, write the range, save it as the previous range and go on
     * If this is a different file, then update the length of the previous file header, write the new file header and write the new range
     * At the very end, make sure we update the last file header.
     *
     * In addition, optionally ignore Ranges that point into Graal innards, just adding them to the current enclosing range
     */

    /**
     * Build line number records for a function.
     * @param entry function to build line number table for
     * @return CVLineRecord containing any entries generated, or null if no entries generated
     */
    @SuppressWarnings("unused")
    CVLineRecord build(PrimaryEntry entry, String methodName) {
   //     long lowAddr = Long.MAX_VALUE;
   //     long highAddr = 0;
        this.primaryEntry = entry;

        assert (!HAS_COLUMNS); /* can't handle columns yet */

        Range primaryRange = primaryEntry.getPrimary();
        Range previousRange = null;

        /* option to not even bother with debug code for Graal */
        if (skipGraalInternals && CVRootPackages.isGraalClass(primaryRange.getClassName())) {
            debug("skipping Graal internal class %s\n", primaryRange);
            return null;
        }
        debug("DEBUG_S_LINES linerecord for 0x%05x file: %s:%d\n", primaryRange.getLo(), primaryRange.getFileName(), primaryRange.getLine());
        this.lineRecord = new CVLineRecord(cvDebugInfo, methodName, primaryEntry);
        debug("CVLineRecord.computeContents: processing primary range %s\n", primaryRange);
        previousRange = processRange(primaryRange, previousRange);
     //   lowAddr = Math.min(lowAddr, primaryRange.getLo());
      //  highAddr = Math.max(highAddr, primaryRange.getHi());

        for (Range subRange : primaryEntry.getSubranges()) {
            debug("CVLineRecord.computeContents: processing range %s\n", subRange);
            FileEntry subFileEntry = primaryEntry.getSubrangeFileEntry(subRange);
            if (subFileEntry == null) {
                continue;
            }
            previousRange = processRange(subRange, previousRange);
      //      lowAddr = Math.min(lowAddr, subRange.getLo());
      //      highAddr = Math.max(highAddr, subRange.getHi());
        }
        return lineRecord;
    }

    /**
     * Merge input Range structures into line number table.
     * The Range structures are assumed to be ordered by ascending address
     * merge with previous line entry if:
     *  - if a Range has a negative linenumber
     *  - if a range is part of Graal or the JDK, and skipGraalOption is true
     *  - if a range has the same line number, source file and function
     *
     * @param range to be merged or added to line number record
     * @param oldPreviousRange the previously processed Range
     * @return new value for previousRange in caller
     */
    private Range processRange(Range range, Range oldPreviousRange) {

        Range previousRange = oldPreviousRange;

        /* should we merge this range with the previous entry? */
        /* i.e. same line in same file, same class and function */
        if (shouldMerge(range, previousRange)) {
            debug("processRange: merging with previous\n");
            return previousRange;
            //range = new Range(previousRange, range.getLo(), range.getHi());
        } /*else if (range.getLine() == -1) {
            CVUtil.debug("     processRange: ignoring: bad line number\n");
            return previousRange;
        }*/

        /* is this a new file? if so we emit a new file record */
        boolean wantNewFile = previousRange == null || !previousRange.getFileAsPath().equals(range.getFileAsPath());
        if (wantNewFile) {
            FileEntry file = cvDebugInfo.findFile(range.getFileAsPath());
            if (file != null && file.getFileName() != null) {
                previousRange = null;
                debug("processRange: addNewFile: %s\n", file);
                lineRecord.addNewFile(file);
            } else {
                debug("processRange: range has no file: %s\n", range);
                return previousRange;
            }
        }

        if (wantNewRange(range, previousRange)) {
            previousRange = range;
            int lineLoAddr = range.getLo() - primaryEntry.getPrimary().getLo();
            int line = Math.max(range.getLine(), 1);
            debug("processRange:   addNewLine: 0x%05x %s\n", lineLoAddr, line);
            lineRecord.addNewLine(lineLoAddr, line);
        }
        return previousRange;
    }

    /**
     * Test to see if two ranges are adjacent, and can be combined into one.
     * @param previousRange the first range (lower address)
     * @param range the second range (higher address)
     * @return true if the two ranges can be combined
     */
    @SuppressWarnings("unused")
    private boolean shouldMerge(Range range, Range previousRange) {
        if (!mergeAdjacentLineRecords) {
            return false;
        }
        if (previousRange == null) {
            return false;
        }
        /* if we're in a different class that the primary Class, this is inlined code */
        final boolean isInlinedCode = !range.getClassName().equals(primaryEntry.getClassEntry().getClassName());
        // if (isInlinedCode && skipInlinedCode) { return true; }
        if (isInlinedCode && skipGraalIntrinsics &&  CVRootPackages.isGraalIntrinsic(range.getClassName())) {
            return true;
        }
        return previousRange.getFileAsPath().equals(range.getFileAsPath()) && (range.getLine() == -1 || previousRange.getLine() == range.getLine());
    }

    /**
     * Test to see if a new line record should be emitted.
     * @param previousRange previous range
     * @param range current range
     * @return true if the current range is on a different line or file from the previous one
     */
    private static boolean wantNewRange(@SuppressWarnings("unused") Range range, @SuppressWarnings("unused") Range previousRange) {
        return true;
        /*if (debug) {
            if (previousRange == null) {
                debug("wantNewRange() prevnull:true");
            } else {
                debug("wantNewRange() prevnull:false" + " linesdiffer:" + (previousRange.getLine() != range.getLine())
                        + " fndiffer:" + (previousRange.getFilePath() != range.getFilePath()) + " contig:" + (previousRange.getHi() < range.getLo()) + " delta:" + (range.getHi() - previousRange.getLo()));
            }
        }*
        if (previousRange == null)
            return true;
        if (previousRange.getLine() != range.getLine())
            return true;
        if (previousRange.getFilePath() != range.getFilePath())
            return true;
        /* it might actually be fine to merge if there's a gap between ranges *
        if (previousRange.getHi() < range.getLo())
            return true;
        //long delta = range.getHi() - previousRange.getLo();
        //return delta >= 127;
        return false; */
    }
}
