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

class CoverageCLI {

    static void handleOutput(PrintStream out, Coverage coverage, CoverageInstrument.Output output) {
        switch (output) {
            case HISTOGRAM:
                printHistogram(out, coverage);
                break;
            case LINES:
                printLines(out, coverage);
                break;
            case JSON:
                printJson(out, coverage);
                break;
        }
    }

    private static void printJson(PrintStream out, Coverage coverage) {
        throw new UnsupportedOperationException();
    }

    private static void printLines(PrintStream out, Coverage coverage) {
        Map<Source, Coverage.PerSource> histogram = coverage.getCoverage();
        String format = getHistogramLineFormat(histogram);
        String header = String.format(format, "Path", "Statements", "Lines", "Roots");
        int length = header.length();
        printLine(out, length);
        printLinesLegend(out);
        printLine(out, length);
        final List<Source> sources = sortedKeys(histogram);
        for (Source source : sources) {
            final String path = source.getPath();
            final Coverage.PerSource value = histogram.get(source);
            out.println(header);
            out.println(String.format(format, path, statementCoverage(value), lineCoverage(value), rootCoverage(value)));
            out.println("");
            printLinesOfSource(out, histogram, source);
        }
        printLine(out, length);
    }

    private static void printLinesOfSource(PrintStream out, Map<Source, Coverage.PerSource> histogram, Source source) {
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

    private static void printLinesLegend(PrintStream out) {
        out.println("Code coverage per line of code and what percent of each element was covered during execution (per source)");
        out.println("  + indicates the line is part of a statement that was covered during execution");
        out.println("  - indicates the line is part of a statement that was not covered during execution");
        out.println("  p indicates the line is part of a statement that was partially covered during execution");
        out.println("    e.g. a not-taken branch of a covered if statement");
        out.println("  ! indicates the line is part of a root that was NOT covered during execution");
        out.println("");
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

    private static void printHistogram(PrintStream out, Coverage coverage) {
        final Map<Source, Coverage.PerSource> histogram = coverage.getCoverage();
        final String format = getHistogramLineFormat(histogram);
        final String header = String.format(format, "Path", "Statements", "Lines", "Roots");
        final int headerLen = header.length();
        printLine(out, headerLen);
        out.println("Code coverage histogram.");
        out.println("  Shows what percent of each element was covered during execution");
        printLine(out, headerLen);
        out.println(header);
        printLine(out, headerLen);
        for (Source source : sortedKeys(histogram)) {
            final String path = source.getPath();
            final Coverage.PerSource value = histogram.get(source);
            final String line = String.format(format, path, statementCoverage(value), lineCoverage(value), rootCoverage(value));
            out.println(line);
        }
        printLine(out, headerLen);
    }

    private static void printLine(PrintStream out, int length) {
        out.println(String.format("%" + length + "s", "").replace(' ', '-'));
    }

    private static List<Source> sortedKeys(Map<Source, Coverage.PerSource> histogram) {
        final List<Source> sorted = new ArrayList<>();
        sorted.addAll(histogram.keySet());
        sorted.removeIf(source -> source.getPath() == null);
        Collections.sort(sorted, (o1, o2) -> o2.getPath().compareTo(o1.getPath()));
        return sorted;
    }

    private static String getHistogramLineFormat(Map<Source, Coverage.PerSource> histogram) {
        int maxPathLenght = 0;
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
        final Set<SourceSection> coveredStatements = coverage.getCoveredStatements();
        final Set<SourceSection> loadedStatements = coverage.getLoadedStatements();
        return percentFormat(100 * (double) coveredStatements.size() / loadedStatements.size());
    }

    private static String rootCoverage(Coverage.PerSource coverage) {
        final Set<SourceSection> coveredRoots = coverage.getCoveredRoots();
        final Set<SourceSection> loadedRoots = coverage.getLoadedRoots();
        return percentFormat(100 * (double) coveredRoots.size() / loadedRoots.size());
    }

    private static String lineCoverage(Coverage.PerSource coverage) {
        final int loadedSize = coverage.loadedLineNumbers().size();
        final int coveredSize = coverage.nonCoveredLineNumbers().size();
        return percentFormat(100 * ((double) loadedSize - coveredSize) / loadedSize);
    }

}
