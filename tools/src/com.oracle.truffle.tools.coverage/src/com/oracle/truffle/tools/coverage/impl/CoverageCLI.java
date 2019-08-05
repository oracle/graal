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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

final class CoverageCLI {

    private final PrintStream out;
    private final String format;
    private final String summaryHeader;
    private final int summaryHeaderLen;
    private final SourceCoverage[] coverage;

    private CoverageCLI(PrintStream out, SourceCoverage[] coverage) {
        this.out = out;
        this.coverage = coverage;
        sortCoverage();
        format = getHistogramLineFormat(coverage);
        summaryHeader = String.format(format, "Path", "Statements", "Lines", "Roots");
        summaryHeaderLen = summaryHeader.length();
    }

    static void handleOutput(PrintStream out, SourceCoverage[] coverage, CoverageInstrument.Output output) {
        switch (output) {
            case HISTOGRAM:
                new CoverageCLI(out, coverage).printHistogramOutput();
                break;
            case LINES:
                new CoverageCLI(out, coverage).printLinesOutput();
                break;
            case JSON:
                new JSONPrinter(out, coverage).print();
                break;
        }
    }

    private void printLinesOutput() {
        printLine();
        printLinesLegend();
        for (SourceCoverage sourceCoverage : coverage) {
            final String path = sourceCoverage.getSource().getPath();
            printLine();
            printSummaryHeader();
            out.println(String.format(format, path, statementCoverage(sourceCoverage), lineCoverage(sourceCoverage), rootCoverage(sourceCoverage)));
            out.println();
            printLinesOfSource(sourceCoverage);
        }
        printLine();
    }

    private static Set<Integer> nonCoveredLineNumbers(SourceCoverage sourceCoverage) {
        Set<SourceSection> nonCoveredSections = loadedSourceSections(sourceCoverage);
        nonCoveredSections.removeAll(coveredSourceSections(sourceCoverage));
        return statementsToLineNumbers(nonCoveredSections);
    }

    private static Set<SourceSection> coveredSourceSections(SourceCoverage sourceCoverage) {
        Set<SourceSection> sourceSections = new HashSet<>();
        for (RootCoverage root : sourceCoverage.getRoots()) {
            sourceSections.addAll(Arrays.asList(root.getCoveredStatements()));
        }
        return sourceSections;
    }

    private static Set<SourceSection> loadedSourceSections(SourceCoverage sourceCoverage) {
        Set<SourceSection> sourceSections = new HashSet<>();
        for (RootCoverage root : sourceCoverage.getRoots()) {
            sourceSections.addAll(Arrays.asList(root.getLoadedStatements()));
        }
        return sourceSections;
    }

    private static Set<Integer> loadedLineNumbers(SourceCoverage sourceCoverage) {
        return statementsToLineNumbers(loadedSourceSections(sourceCoverage));
    }

    private static Set<Integer> coveredLineNumbers(SourceCoverage source) {
        return statementsToLineNumbers(coveredSourceSections(source));
    }

    private static Set<Integer> statementsToLineNumbers(Set<SourceSection> sourceSections) {
        Set<Integer> lines = new HashSet<>();
        for (SourceSection ss : sourceSections) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                lines.add(i);
            }
        }
        return lines;
    }

    private void printLinesOfSource(SourceCoverage sourceCoverage) {
        Set<Integer> nonCoveredLineNumbers = nonCoveredLineNumbers(sourceCoverage);
        Set<Integer> loadedLineNumbers = loadedLineNumbers(sourceCoverage);
        Set<Integer> coveredLineNumbers = coveredLineNumbers(sourceCoverage);
        Set<Integer> nonCoveredRootLineNumbers = nonCoveredRootLineNumbers(sourceCoverage);
        Set<Integer> loadedRootLineNumbers = loadedRootLineNumbers(sourceCoverage);
        Set<Integer> coveredRootLineNumbers = coveredRootLineNumbers(sourceCoverage);
        final Source source = sourceCoverage.getSource();
        for (int i = 1; i <= source.getLineCount(); i++) {
            char covered = getCoverageCharacter(nonCoveredLineNumbers, loadedLineNumbers, coveredLineNumbers, i, 'p', '-', '+');
            char rootCovered = getCoverageCharacter(nonCoveredRootLineNumbers,
                            loadedRootLineNumbers, coveredRootLineNumbers, i, '!', '!', ' ');
            out.println(String.format("%s%s %s", covered, rootCovered, source.getCharacters(i)));
        }
    }

    private Set<Integer> nonCoveredRootLineNumbers(SourceCoverage sourceCoverage) {
        final HashSet<SourceSection> sections = loadedRootSections(sourceCoverage);
        sections.removeAll(coveredRootSections(sourceCoverage));
        return statementsToLineNumbers(sections);
    }

    private Set<Integer> coveredRootLineNumbers(SourceCoverage sourceCoverage) {
        return statementsToLineNumbers(coveredRootSections(sourceCoverage));
    }

    private HashSet<SourceSection> coveredRootSections(SourceCoverage sourceCoverage) {
        final HashSet<SourceSection> sections = new HashSet<>();
        for (RootCoverage rootCoverage : sourceCoverage.getRoots()) {
            if (rootCoverage.isCovered()) {
                sections.add(rootCoverage.getSourceSection());
            }
        }
        return sections;
    }

    private Set<Integer> loadedRootLineNumbers(SourceCoverage sourceCoverage) {
        return statementsToLineNumbers(loadedRootSections(sourceCoverage));
    }

    private HashSet<SourceSection> loadedRootSections(SourceCoverage sourceCoverage) {
        final HashSet<SourceSection> sections = new HashSet<>();
        for (RootCoverage rootCoverage : sourceCoverage.getRoots()) {
            sections.add(rootCoverage.getSourceSection());
        }
        return sections;
    }

    private void printLinesLegend() {
        out.println("Code coverage per line of code and what percent of each element was covered during execution (per source)");
        out.println("  + indicates the line is part of a statement that was covered during execution");
        out.println("  - indicates the line is part of a statement that was not covered during execution");
        out.println("  p indicates the line is part of a statement that was partially covered during execution");
        out.println("    e.g. a not-taken branch of a covered if statement");
        out.println("  ! indicates the line is part of a root that was NOT covered during execution");
    }

    private static char getCoverageCharacter(Set<Integer> nonCoveredLineNumbers, Set<Integer> loadedLineNumbers, Set<Integer> coveredLineNumbers, int i, char partly, char not, char yes) {
        if (loadedLineNumbers.contains(i)) {
            if (coveredLineNumbers.contains(i) && nonCoveredLineNumbers.contains(i)) {
                return partly;
            }
            return nonCoveredLineNumbers.contains(i) ? not : yes;
        } else {
            return ' ';
        }
    }

    private void printHistogramOutput() {
        printLine();
        out.println("Code coverage histogram.");
        out.println("  Shows what percent of each element was covered during execution");
        printLine();
        printSummaryHeader();
        printLine();
        for (SourceCoverage sourceCoverage : coverage) {
            final String path = sourceCoverage.getSource().getPath();
            final String line = String.format(format, path, statementCoverage(sourceCoverage), lineCoverage(sourceCoverage), rootCoverage(sourceCoverage));
            out.println(line);
        }
        printLine();
    }

    private void sortCoverage() {
        Arrays.sort(coverage, new Comparator<SourceCoverage>() {
            @Override
            public int compare(SourceCoverage o1, SourceCoverage o2) {
                return o1.getSource().getPath().compareTo(o2.getSource().getPath());
            }
        });
    }

    private void printSummaryHeader() {
        out.println(summaryHeader);
    }

    private void printLine() {
        out.println(String.format("%" + summaryHeaderLen + "s", "").replace(' ', '-'));
    }

    private List<Source> sortedKeys() {
        final List<Source> sorted = new ArrayList<>();
        for (SourceCoverage sourceCoverage : coverage) {
            sorted.add(sourceCoverage.getSource());
        }
        sorted.removeIf(source -> source.getPath() == null);
        Collections.sort(sorted, (o1, o2) -> o2.getPath().compareTo(o1.getPath()));
        return sorted;
    }

    private static String getHistogramLineFormat(SourceCoverage[] coverage) {
        int maxPathLength = 10;
        for (SourceCoverage source : coverage) {
            final String path = source.getSource().getPath();
            if (path != null) {
                maxPathLength = Math.max(maxPathLength, path.length());
            }
        }
        return " %-" + maxPathLength + "s |  %10s |  %7s |  %7s ";
    }

    private static String percentFormat(double val) {
        return String.format("%.2f%%", val);
    }

    private static String statementCoverage(SourceCoverage coverage) {
        int loaded = 0;
        int covered = 0;
        for (RootCoverage root : coverage.getRoots()) {
            loaded += root.getLoadedStatements().length;
            covered += root.getCoveredStatements().length;
        }
        return percentFormat(100 * (double) covered / loaded);
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

    private static String lineCoverage(SourceCoverage sourceCoverage) {
        final int loadedSize = loadedLineNumbers(sourceCoverage).size();
        final int coveredSize = nonCoveredLineNumbers(sourceCoverage).size();
        final double coverage = ((double) loadedSize - coveredSize) / loadedSize;
        return percentFormat(100 * coverage);
    }

}
