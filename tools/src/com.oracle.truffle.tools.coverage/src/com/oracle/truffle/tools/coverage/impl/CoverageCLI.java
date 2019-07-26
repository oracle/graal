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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.Coverage;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

final class CoverageCLI {

    private final PrintStream out;
    private final Map<Source, Coverage.PerSource> histogram;
    private final String format;
    private final String summaryHeader;
    private final int summaryHeaderLen;

    private CoverageCLI(PrintStream out, Coverage coverage) {
        this.out = out;
        histogram = coverage.getCoverage();
        format = getHistogramLineFormat(histogram);
        summaryHeader = String.format(format, "Path", "Statements", "Lines", "Roots");
        summaryHeaderLen = summaryHeader.length();
    }

    static void handleOutput(PrintStream out, Coverage coverage, CoverageInstrument.Output output) {
        switch (output) {
            case HISTOGRAM:
                new CoverageCLI(out, coverage).printHistogramOutput();
                break;
            case LINES:
                new CoverageCLI(out, coverage).printLinesOutput();
                break;
            case JSON:
                printJson(out, coverage);
                break;
        }
    }

    private static void printJson(PrintStream out, Coverage coverage) {
        JSONObject output = new JSONObject();
        final Map<Source, Coverage.PerSource> sourceMap = coverage.getCoverage();
        for (Map.Entry<Source, Coverage.PerSource> entry : sourceMap.entrySet()) {
            final Coverage.PerSource perSource = entry.getValue();
            final JSONObject perSourceJson = new JSONObject();
            perSourceJson.put("loaded_statements", statementsJson(perSource.getLoadedStatements()));
            perSourceJson.put("covered_statements", statementsJson(perSource.getCoveredStatements()));
            perSourceJson.put("loaded_roots", statementsJson(perSource.getLoadedRoots()));
            perSourceJson.put("covered_roots", statementsJson(perSource.getCoveredRoots()));
            perSourceJson.put("summary", jsonSummary(perSource));
            output.put(entry.getKey().getPath(), perSourceJson);
        }
        out.println(output.toString());
    }

    private static JSONObject jsonSummary(Coverage.PerSource perSource) {
        JSONObject summary = new JSONObject();
        summary.put("statement_coverage", perSource.statementCoverage());
        summary.put("root_coverage", perSource.rootCoverage());
        summary.put("line_coverage", perSource.lineCoverage());
        return summary;
    }

    private static JSONArray statementsJson(Set<SourceSection> statements) {
        final JSONArray array = new JSONArray();
        for (SourceSection statement : statements) {
            array.put(sourseSectionJson(statement));
        }
        return array;
    }

    private static JSONObject sourseSectionJson(SourceSection statement) {
        JSONObject sourceSection = new JSONObject();
        sourceSection.put("characters", statement.getCharacters());
        sourceSection.put("start_line", statement.getStartLine());
        sourceSection.put("end_line", statement.getEndLine());
        sourceSection.put("start_column", statement.getStartColumn());
        sourceSection.put("end_column", statement.getEndColumn());
        sourceSection.put("char_index", statement.getCharIndex());
        sourceSection.put("char_end_index", statement.getCharEndIndex());
        sourceSection.put("char_lenght", statement.getCharLength());
        return sourceSection;
    }

    private void printLinesOutput() {
        printLine();
        printLinesLegend();
        final List<Source> sources = sortedKeys();
        for (Source source : sources) {
            final String path = source.getPath();
            final Coverage.PerSource value = histogram.get(source);
            printLine();
            printSummaryHeader();
            out.println(String.format(format, path, statementCoverage(value), lineCoverage(value), rootCoverage(value)));
            out.println("");
            printLinesOfSource(source);
        }
        printLine();
    }

    private void printLinesOfSource(Source source) {
        Coverage.PerSource perSource = histogram.get(source);
        Set<Integer> nonCoveredLineNumbers = perSource.nonCoveredLineNumbers();
        Set<Integer> loadedLineNumbers = perSource.loadedLineNumbers();
        Set<Integer> coveredLineNumbers = perSource.coveredLineNumbers();
        Set<Integer> nonCoveredRootLineNumbers = perSource.nonCoveredRootLineNumbers();
        Set<Integer> loadedRootLineNumbers = perSource.loadedRootLineNumbers();
        Set<Integer> coveredRootLineNumbers = perSource.coveredRootLineNumbers();
        for (int i = 1; i <= source.getLineCount(); i++) {
            char covered = getCoverageCharacter(nonCoveredLineNumbers, loadedLineNumbers, coveredLineNumbers, i, 'p', '-', '+');
            char rootCovered = getCoverageCharacter(nonCoveredRootLineNumbers, loadedRootLineNumbers, coveredRootLineNumbers, i, '!', '!', ' ');
            out.println(String.format("%s%s %s", covered, rootCovered, source.getCharacters(i)));
        }
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
        for (Source source : sortedKeys()) {
            final String path = source.getPath();
            final Coverage.PerSource value = histogram.get(source);
            final String line = String.format(format, path, statementCoverage(value), lineCoverage(value), rootCoverage(value));
            out.println(line);
        }
        printLine();
    }

    private void printSummaryHeader() {
        out.println(summaryHeader);
    }

    private void printLine() {
        out.println(String.format("%" + summaryHeaderLen + "s", "").replace(' ', '-'));
    }

    private List<Source> sortedKeys() {
        final List<Source> sorted = new ArrayList<>();
        sorted.addAll(histogram.keySet());
        sorted.removeIf(source -> source.getPath() == null);
        Collections.sort(sorted, (o1, o2) -> o2.getPath().compareTo(o1.getPath()));
        return sorted;
    }

    private static String getHistogramLineFormat(Map<Source, Coverage.PerSource> histogram) {
        int maxPathLenght = 10;
        for (Source source : histogram.keySet()) {
            final String path = source.getPath();
            if (path != null) {
                maxPathLenght = Math.max(maxPathLenght, path.length());
            }
        }
        return " %-" + maxPathLenght + "s |  %10s |  %7s |  %7s ";
    }

    private static String percentFormat(double val) {
        return String.format("%.2f%%", val);
    }

    private static String statementCoverage(Coverage.PerSource coverage) {
        return percentFormat(100 * coverage.statementCoverage());
    }

    private static String rootCoverage(Coverage.PerSource coverage) {
        return percentFormat(100 * coverage.rootCoverage());
    }

    private static String lineCoverage(Coverage.PerSource coverage) {
        return percentFormat(100 * coverage.lineCoverage());
    }

}
