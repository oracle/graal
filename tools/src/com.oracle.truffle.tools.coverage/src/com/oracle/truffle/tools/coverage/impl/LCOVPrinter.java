/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage.impl;

import java.io.PrintStream;
import java.util.HashMap;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;

class LCOVPrinter {

    private final PrintStream out;
    private final SourceCoverage[] coverage;
    private final boolean strictLines;

    LCOVPrinter(PrintStream out, SourceCoverage[] coverage, boolean strictLines) {
        this.out = out;
        this.coverage = coverage;
        this.strictLines = strictLines;
    }

    private HashMap<Integer, Long> linesToCount(SourceCoverage coverage) {
        final HashMap<Integer, Long> linesToCount = new HashMap<>();
        for (RootCoverage root : coverage.getRoots()) {
            final SectionCoverage[] sectionCoverage = root.getSectionCoverage();
            addCoverageCounts(linesToCount, sectionCoverage);
            if (strictLines) {
                removeIncidentalCoverage(linesToCount, sectionCoverage);
            }
        }
        return linesToCount;
    }

    private static void addCoverageCounts(HashMap<Integer, Long> linesToCount, SectionCoverage[] sectionCoverage) {
        for (SectionCoverage section : sectionCoverage) {
            addSectionCoverageCount(linesToCount, section);
        }
    }

    private static void addSectionCoverageCount(HashMap<Integer, Long> linesToCount, SectionCoverage section) {
        final SourceSection sourceSection = section.getSourceSection();
        for (int i = sourceSection.getStartLine(); i <= sourceSection.getEndLine(); i++) {
            linesToCount.compute(i, (key, old) -> {
                if (section.isCovered()) {
                    final long count = section.getCount();
                    // If counting was not enabled in the instrument
                    if (count == -1) {
                        return 1L;
                    }
                    return (old != null) ? Math.max(old, count) : count;
                } else {
                    return 0L;
                }
            });
        }
    }

    private static void removeIncidentalCoverage(HashMap<Integer, Long> linesToCount, SectionCoverage[] sectionCoverage) {
        for (SectionCoverage section : sectionCoverage) {
            if (!section.isCovered()) {
                final SourceSection sourceSection = section.getSourceSection();
                for (int i = sourceSection.getStartLine(); i <= sourceSection.getEndLine(); i++) {
                    linesToCount.put(i, 0L);
                }
            }
        }
    }

    void print() {
        for (SourceCoverage sourceCoverage : coverage) {
            printSourceCoverage(sourceCoverage);
        }
    }

    private void printSourceCoverage(SourceCoverage sourceCoverage) {
        printTestName();
        printSourceFile(sourceCoverage);
        printRootData(sourceCoverage);
        printLineData(sourceCoverage);
        out.println("end_of_record");

    }

    private void printLineData(SourceCoverage sourceCoverage) {
        int consideredLines = 0;
        int coveredLines = 0;
        final HashMap<Integer, Long> linesToCount = linesToCount(sourceCoverage);
        for (int i = 1; i <= sourceCoverage.getSource().getLineCount(); i++) {
            if (linesToCount.containsKey(i)) {
                consideredLines++;
                final long executionCount = linesToCount.get(i);
                if (executionCount > 0) {
                    coveredLines++;
                }
                out.println("DA:" + i + "," + executionCount);
            }
        }
        out.println("LF:" + consideredLines);
        out.println("LH:" + coveredLines);
    }

    private void printRootData(SourceCoverage sourceCoverage) {
        final RootCoverage[] roots = sourceCoverage.getRoots();
        for (RootCoverage root : roots) {
            printRoot(root);
        }
        int coveredRoots = 0;
        for (RootCoverage root : roots) {
            if (root.isCovered()) {
                coveredRoots++;
            }
            printRootCoverage(root);
        }
        printRootCount(roots);
        printCoveredRootCount(coveredRoots);
    }

    private void printCoveredRootCount(int coveredRoots) {
        out.println("FNH:" + coveredRoots);
    }

    private void printRootCount(RootCoverage[] roots) {
        out.println("FNF:" + roots.length);
    }

    private void printRootCoverage(RootCoverage root) {
        final long count = (root.isCovered() && root.getCount() == -1) ? 1 : root.getCount();
        out.println("FNDA:" + count + "," + root.getName());
    }

    private void printRoot(RootCoverage root) {
        out.println("FN:" + root.getSourceSection().getStartLine() + "," + root.getName());
    }

    private void printSourceFile(SourceCoverage sourceCoverage) {
        out.println("SF:" + sourceCoverage.getSource().getPath());
    }

    private void printTestName() {
        out.println("TN:");
    }
}
