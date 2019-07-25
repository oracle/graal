package com.oracle.truffle.tools.coverage;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class Coverage {

    private final Map<Source, PerSource> coverage;

    public Map<Source, PerSource> getCoverage() {
        return coverage;
    }

    Coverage() {
        coverage = new HashMap<>();
    }

    private Coverage(Map<Source, PerSource> coverage) {
        this.coverage = coverage;
    }

    void addCoveredStatement(SourceSection statementSection) {
        getPerSource(statementSection).coveredStatements.add(statementSection);
    }

    private PerSource getPerSource(SourceSection sourceSection) {
        return coverage.computeIfAbsent(sourceSection.getSource(), source -> new PerSource());
    }

    void addCoveredRoot(SourceSection rootSection) {
        getPerSource(rootSection).coveredRoots.add(rootSection);
    }

    void addLoadedStatement(SourceSection statementSection) {
        getPerSource(statementSection).loadedStatements.add(statementSection);
    }

    void addLoadedRoot(SourceSection rootSection) {
        getPerSource(rootSection).loadedRoots.add(rootSection);
    }

    Coverage readOnlyCopy() {
        Map<Source, PerSource> coverageCopy = new HashMap<>();
        for (Source source : coverage.keySet()) {
            coverageCopy.put(source, coverage.get(source).readOnlyCopy());
        }
        return new Coverage(Collections.unmodifiableMap(coverageCopy));
    }

    public static final class PerSource {
        private final Set<SourceSection> loadedStatements;
        private final Set<SourceSection> loadedRoots;
        private final Set<SourceSection> coveredStatements;
        private final Set<SourceSection> coveredRoots;

        private PerSource() {
            loadedStatements = new HashSet<>();
            loadedRoots = new HashSet<>();
            coveredStatements = new HashSet<>();
            coveredRoots = new HashSet<>();
        }

        private PerSource(Set<SourceSection> loadedStatements, Set<SourceSection> loadedRoots, Set<SourceSection> coveredStatements, Set<SourceSection> coveredRoots) {
            this.loadedStatements = loadedStatements;
            this.loadedRoots = loadedRoots;
            this.coveredStatements = coveredStatements;
            this.coveredRoots = coveredRoots;
        }

        private PerSource readOnlyCopy() {
            return new PerSource(
                            Collections.unmodifiableSet(loadedStatements),
                            Collections.unmodifiableSet(loadedRoots),
                            Collections.unmodifiableSet(coveredStatements),
                            Collections.unmodifiableSet(coveredRoots));
        }

        public Set<SourceSection> getLoadedStatements() {
            return loadedStatements;
        }

        public Set<SourceSection> getLoadedRoots() {
            return loadedRoots;
        }

        public Set<SourceSection> getCoveredStatements() {
            return coveredStatements;
        }

        public Set<SourceSection> getCoveredRoots() {
            return coveredRoots;
        }

        public Set<Integer> nonCoveredLineNumbers() {
            Set<SourceSection> nonCoveredSections = new HashSet<>();
            nonCoveredSections.addAll(loadedStatements);
            nonCoveredSections.removeAll(coveredStatements);
            return statementsToLineNumbers(nonCoveredSections);
        }

        public Set<Integer> coveredLineNumbers() {
            return statementsToLineNumbers(coveredStatements);
        }

        public Set<Integer> loadedLineNumbers() {
            return statementsToLineNumbers(loadedStatements);
        }

        public Set<Integer> coveredRootLineNumbers() {
            return statementsToLineNumbers(coveredRoots);
        }

        public Set<Integer> loadedRootLineNumbers() {
            return statementsToLineNumbers(loadedRoots);
        }

        public Set<Integer> nonCoveredRootLineNumbers() {
            Set<SourceSection> nonCoveredSections = new HashSet<>();
            nonCoveredSections.addAll(loadedRoots);
            nonCoveredSections.removeAll(coveredRoots);
            return statementsToLineNumbers(nonCoveredSections);
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
}
