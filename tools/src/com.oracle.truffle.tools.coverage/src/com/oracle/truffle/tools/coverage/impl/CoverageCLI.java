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
import java.util.Arrays;
import java.util.Comparator;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;

final class CoverageCLI {

    private final PrintStream out;
    private final String format;
    private final String summaryHeader;
    private final int summaryHeaderLen;
    private final SourceCoverage[] coverage;
    private final boolean strictLines;

    CoverageCLI(PrintStream out, SourceCoverage[] coverage, boolean strictLines) {
        this.out = out;
        this.coverage = coverage;
        this.strictLines = strictLines;
        sortCoverage();
        format = getHistogramLineFormat(coverage);
        summaryHeader = String.format(format, "Path", "Statements", "Lines", "Roots");
        summaryHeaderLen = summaryHeader.length();
    }

    private static String getName(Source source) {
        if (source.getPath() == null) {
            return source.getName();
        } else {
            return source.getPath();
        }
    }

    private static String getHistogramLineFormat(SourceCoverage[] coverage) {
        int maxNameLength = 10;
        for (SourceCoverage source : coverage) {
            final String name = getName(source.getSource());
            maxNameLength = Math.max(maxNameLength, name.length());
        }
        return " %-" + maxNameLength + "s |  %10s |  %7s |  %7s ";
    }

    private static String percentFormat(double val) {
        return String.format("%.2f%%", val);
    }

    private static String statementCoverage(SourceCoverage coverage) {
        int loaded = 0;
        int covered = 0;
        for (RootCoverage root : coverage.getRoots()) {
            final SectionCoverage[] sectionCoverage = root.getSectionCoverage();
            loaded += sectionCoverage.length;
            covered += getCoveredCount(sectionCoverage);
        }
        return percentFormat(100 * (double) covered / loaded);
    }

    private static long getCoveredCount(SectionCoverage[] sectionCoverage) {
        return Arrays.stream(sectionCoverage).filter(SectionCoverage::isCovered).count();
    }

    private static String rootCoverage(SourceCoverage coverage) {
        int covered = 0;
        for (RootCoverage root : coverage.getRoots()) {
            if (root.isCovered()) {
                covered++;
            }
        }
        return percentFormat(100 * (double) covered / coverage.getRoots().length);
    }

    private static String lineCoverage(LineCoverage lineCoverage) {
        final double coverage = lineCoverage.getCoverage();
        if (Double.isNaN(coverage)) {
            return "";
        }
        return percentFormat(100 * coverage);
    }

    void printLinesOutput() {
        printLine();
        printLinesLegend();
        for (SourceCoverage sourceCoverage : coverage) {
            final Source source = sourceCoverage.getSource();
            final String name = getName(source);
            printLine();
            printSummaryHeader();
            final LineCoverage lineCoverage = new LineCoverage(sourceCoverage, strictLines);
            out.println(String.format(format, name, statementCoverage(sourceCoverage), lineCoverage(lineCoverage), rootCoverage(sourceCoverage)));
            out.println();
            if (source.hasCharacters() || source.hasBytes()) {
                printLinesOfSource(source, lineCoverage);
            } else {
                out.println("NO CONTENT AVAILABLE");
            }
        }
        printLine();
    }

    private void printLinesOfSource(Source source, LineCoverage lineCoverage) {
        for (int i = 1; i <= source.getLineCount(); i++) {
            char covered = lineCoverage.getStatementCoverageCharacter(i);
            out.println(String.format("%s %s", covered, source.getCharacters(i)));
        }
    }

    private void printLinesLegend() {
        out.println("Code coverage per line of code and what percent of each element was covered during execution (per source)");
        out.println("  + indicates the line is covered during execution");
        out.println("  - indicates the line is not covered during execution");
        if (strictLines) {
            out.println("  p indicates the line is part of a statement that was incidentally covered during execution");
            out.println("    e.g. a not-taken branch of a covered if statement");
        }
    }

    void printHistogramOutput() {
        printLine();
        out.println("Code coverage histogram.");
        out.println("  Shows what percent of each element was covered during execution");
        printLine();
        printSummaryHeader();
        printLine();
        for (SourceCoverage sourceCoverage : coverage) {
            final String name = getName(sourceCoverage.getSource());
            final String line = String.format(format, name,
                            statementCoverage(sourceCoverage),
                            lineCoverage(new LineCoverage(sourceCoverage, strictLines)),
                            rootCoverage(sourceCoverage));
            out.println(line);
        }
        printLine();
    }

    private void sortCoverage() {
        Arrays.sort(coverage, new Comparator<SourceCoverage>() {
            @Override
            public int compare(SourceCoverage o1, SourceCoverage o2) {
                return getName(o1.getSource()).compareTo(getName(o2.getSource()));
            }
        });
    }

    private void printSummaryHeader() {
        out.println(summaryHeader);
    }

    private void printLine() {
        out.println(String.format("%" + summaryHeaderLen + "s", "").replace(' ', '-'));
    }

}
