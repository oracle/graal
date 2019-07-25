package com.oracle.truffle.tools.coverage.impl;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

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
        final int divLength = 80;
        printLine(out, divLength);
        out.println("Code coverage per line of code.");
        out.println("  + indicates the line was covered during execution");
        out.println("  - indicates the line was not covered during execution");
        out.println("  p indicates the line was partially covered during execution");
        out.println("    e.g. a not-taken branch of a covered if statement");
        out.println("");
        printLine(out, divLength);
        final Map<Source, PerSourceCoverage> histogram = buildHistogram(coverage);
        final List<Source> sources = sortedKeys(histogram);
        for (Source source : sources) {
            out.println(source.getPath());
            printLine(out, divLength);
            final PerSourceCoverage perSourceCoverage = histogram.get(source);
            final Set<Integer> nonCoveredLineNumbers = perSourceCoverage.nonCoveredLineNumbers();
            final Set<Integer> loadedLineNumbers = perSourceCoverage.loadedLineNumbers();
            final Set<Integer> coveredLineNumbers = perSourceCoverage.coveredLineNumbers();
            for (int i = 1; i <= source.getLineCount(); i++) {
                char covered = getCoverageCharacter(nonCoveredLineNumbers, loadedLineNumbers, coveredLineNumbers,i);
                out.println(String.format("%s %s", covered, source.getCharacters(i)));
            }
        }
        printLine(out, divLength);
    }

    private static char getCoverageCharacter(Set<Integer> nonCoveredLineNumbers, Set<Integer> loadedLineNumbers, Set<Integer> coveredLineNumbers, int i) {
        if (loadedLineNumbers.contains(i)) {
            if (coveredLineNumbers.contains(i) && nonCoveredLineNumbers.contains(i)) {
                return 'p';
            }
            return nonCoveredLineNumbers.contains(i) ? '-' : '+';
        } else {
            return ' ';
        }
    }

    private static class PerSourceCoverage {
        final Set<SourceSection> loadedStatements = new HashSet<>();
        final Set<SourceSection> loadedRoots = new HashSet<>();
        final Set<SourceSection> coveredStatements = new HashSet<>();
        final Set<SourceSection> coveredRoots = new HashSet<>();

        String percentFormat(double val) {
            return String.format("%.2f%%", val);
        }

        String statementCoverage() {
            return percentFormat(100 * (double) coveredStatements.size() / loadedStatements.size());
        }

        String rootCoverage() {
            return percentFormat(100 * (double) coveredRoots.size() / loadedRoots.size());
        }

        String lineCoverage() {
            final int loadedSize = loadedLineNumbers().size();
            final int coveredSize = nonCoveredLineNumbers().size();
            return percentFormat(100 * ((double) loadedSize - coveredSize) / loadedSize);
        }

        Set<Integer> nonCoveredLineNumbers() {
            Set<SourceSection> nonCoveredSections = new HashSet<>();
            nonCoveredSections.addAll(loadedStatements);
            nonCoveredSections.removeAll(coveredStatements);
            return statementsToLineNumbers(nonCoveredSections);
        }

        Set<Integer> coveredLineNumbers() {
            return statementsToLineNumbers(coveredStatements);
        }

        Set<Integer> loadedLineNumbers() {
            return statementsToLineNumbers(loadedStatements);
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
    }

    private static void printHistogram(PrintStream out, Coverage coverage) {
        final Map<Source, PerSourceCoverage> histogram = buildHistogram(coverage);
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
            final PerSourceCoverage value = histogram.get(source);
            final String line = String.format(format, path, value.statementCoverage(), value.lineCoverage(), value.rootCoverage());
            out.println(line);
        }
        printLine(out, headerLen);
    }

    private static void printLine(PrintStream out, int length) {
        out.println(String.format("%" + length + "s", "").replace(' ', '-'));
    }

    private static List<Source> sortedKeys(Map<Source, PerSourceCoverage> histogram) {
        final List<Source> sorted = new ArrayList<>();
        sorted.addAll(histogram.keySet());
        sorted.removeIf(source -> source.getPath() == null);
        Collections.sort(sorted, (o1, o2) -> o2.getPath().compareTo(o1.getPath()));
        return sorted;
    }

    private static String getHistogramLineFormat(Map<Source, PerSourceCoverage> histogram) {
        int maxPathLenght = 0;
        for (Source source : histogram.keySet()) {
            final String path = source.getPath();
            if (path != null) {
                maxPathLenght = Math.max(maxPathLenght, path.length());
            }
        }
        return " %-" + maxPathLenght + "s |  %10s |  %7s |  %7s ";
    }

    private static Map<Source, PerSourceCoverage> buildHistogram(Coverage coverage) {
        Map<Source, PerSourceCoverage> histogram = new HashMap<>();
        populateHistogramEntries(histogram, coverage.getLoadedRoots(), (histogramEntry, loadedRoot) -> {
            histogramEntry.loadedRoots.add(loadedRoot);
        });
        populateHistogramEntries(histogram, coverage.getCoveredRoots(), (histogramEntry, coveredRoot) -> {
            histogramEntry.coveredRoots.add(coveredRoot);
        });
        populateHistogramEntries(histogram, coverage.getLoadedStatements(), (histogramEntry, loadedStatement) -> {
            histogramEntry.loadedStatements.add(loadedStatement);
        });
        populateHistogramEntries(histogram, coverage.getCoveredStatements(), (histogramEntry, coveredStatement) -> {
            histogramEntry.coveredStatements.add(coveredStatement);
        });
        return histogram;
    }

    private static void populateHistogramEntries(Map<Source, PerSourceCoverage> histogram, Set<SourceSection> roots, BiConsumer<PerSourceCoverage, SourceSection> add) {
        for (SourceSection loadedRoot : roots) {
            final PerSourceCoverage histogramEntry = histogram.computeIfAbsent(loadedRoot.getSource(), source -> new PerSourceCoverage());
            add.accept(histogramEntry, loadedRoot);
        }
    }
}
