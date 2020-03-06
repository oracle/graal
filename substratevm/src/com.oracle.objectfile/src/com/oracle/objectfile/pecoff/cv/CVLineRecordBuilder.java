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

import static com.oracle.objectfile.pecoff.cv.CVConstants.skipGraalInternals;
import static com.oracle.objectfile.pecoff.cv.CVConstants.skipGraalIntrinsics;
import static com.oracle.objectfile.pecoff.cv.CVRootPackages.isJavaPackage;

public class CVLineRecordBuilder {

    private static final boolean debug = true;
    private static final boolean HAS_COLUMNS = false;

    private CVSections cvSections;
    private CVLineRecord lineRecord;
    private Range previousRange = null;
    private PrimaryEntry primaryEntry;

    CVLineRecordBuilder(CVSections cvSections) {
        this.cvSections = cvSections;
    }

    /*
     * In CV4, the line table consists of a series of file headers followed by line number entries
     * to handle this, first we decide if we want to merge this with the previous range (only if same file and start of this range is end of previous range)
     * if we are emitting a new range to the same file, write the range, save it as the previous range and go on
     * If this is a different file, then update the length of the previous file header, write the new file header and write the new range
     * At the very end, make sure we update the last file header
     *
     * In addition, optionally ignore Ranges that point into Graal innards, just adding them to the current enclosing ramge
     */

    /**
     * Feed Range structures to processRange.
     * @param primaryEntry input containing sub ranges
     * @return CVLineRecord containing any entries generated, or null if o entries
     */
    CVLineRecord build(String methodName, PrimaryEntry primaryEntry) {
        long lowAddr = Long.MAX_VALUE;
        long highAddr = 0;
        this.primaryEntry = primaryEntry;
        previousRange = null;

        assert (!HAS_COLUMNS); /* can't handle columns yet */

        Range primaryRange = primaryEntry.getPrimary();
        if (skipGraalInternals && isGraalIntrinsic(primaryRange.getClassName())) {
            CVUtil.debug("  skipping Graal internal class %s\n", primaryRange);
            return null;
        }
        CVUtil.debug("  DEBUG_S_LINES linerecord for 0x%05x file: %s:%d\n", primaryRange.getLo(), primaryRange.getFileName(), primaryRange.getLine());
        this.lineRecord = new CVLineRecord(cvSections, methodName, primaryEntry);
        CVUtil.debug("     CVLineRecord.computeContents: processing primary range %s\n", primaryRange);
        processRange(primaryRange);
        lowAddr = Math.min(lowAddr, primaryRange.getLo());
        highAddr = Math.max(highAddr, primaryRange.getHi());

        for (Range subRange : primaryEntry.getSubranges()) {
            CVUtil.debug("     CVLineRecord.computeContents: processing range %s\n", subRange);
            processRange(subRange);
            lowAddr = Math.min(lowAddr, subRange.getLo());
            highAddr = Math.max(highAddr, subRange.getHi());
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
     * @param range to be merged or added to line number record
     */
    private void processRange(Range range) {

        /* should we merge this range with the previous entry? */
        if (shouldMerge(previousRange, range)) {
            range = new Range(previousRange, range.getLo(), range.getHi());
        } else if (range.getLine() == -1) {
            return;
        }

        boolean wantNewFile = previousRange == null || !previousRange.getFileName().equals(range.getFileName());
        if (wantNewFile) {
            FileEntry file = cvSections.ensureFileEntry(range);
            previousRange =  null;
            CVUtil.debug("     adding linerecord for file: %s\n", file.getFileName());
            lineRecord.addNewFile(file);
        }

        if (wantNewRange(previousRange, range)) {
            previousRange = range;
            int lineLo = range.getLo() - primaryEntry.getPrimary().getLo();
            CVUtil.debug("            line: 0x%05x %s\n", lineLo, range.getLine());
            lineRecord.addNewLine(lineLo, range.getLine());
        }
    }

    private boolean isGraalIntrinsic(String className) {
        return className.startsWith("com.oracle.svm") || className.startsWith("org.graalvm") || isJavaPackage(className);
    }

    private boolean shouldMerge(Range previousRange, Range range) {
        if (previousRange == null) {
            return false;
        }
        if (skipGraalIntrinsics && isGraalIntrinsic(range.getClassName())) {
            return true;
        }
        return previousRange.getFileName().equals(range.getFileName()) && (range.getLine() == -1 || previousRange.getLine() == range.getLine());
    }

    private boolean wantNewRange(Range previous, Range range) {
        return true;
        /*if (debug) {
            if (previous == null) {
                CVUtil.debug("wantNewRange() prevnull:true");
            } else {
                CVUtil.debug("wantNewRange() prevnull:false" + " linesdiffer:" + (previous.getLine() != range.getLine())
                        + " fndiffer:" + (!previous.sameFileName(range)) + " contig:" + (previous.getHi() < range.getLo()) + " delta:" + (range.getHi() - previousRange.getLo()));
            }
        }*/
        /*
        if (previous == null)
            return true;
        if (previous.getLine() != range.getLine())
            return true;
        if (!previous.sameFileName(range))
            return true;
        if (previous.getHi() < range.getLo())
            return true;
        long delta = range.getHi() - previousRange.getLo();
        return delta >= 127;
         */
    }


    @Override
    public String toString() {
        return "CVLineRecordBuilder()";
    }
}
