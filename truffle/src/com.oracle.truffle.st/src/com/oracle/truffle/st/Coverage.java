package com.oracle.truffle.st;

import com.oracle.truffle.api.source.SourceSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contains per {@link com.oracle.truffle.api.source.Source} coverage by keeping track of loaded and
 * covered {@link com.oracle.truffle.api.source.SourceSection}s
 */
public class Coverage {
    private Set<SourceSection> loaded = new HashSet<>();
    private Set<SourceSection> covered = new HashSet<>();

    void addCovered(SourceSection instrumentedSourceSection) {
        covered.add(instrumentedSourceSection);
    }

    void addLoaded(SourceSection sourceSection) {
        loaded.add(sourceSection);
    }

    private Set<SourceSection> nonCoveredSections() {
        final HashSet<SourceSection> nonCovered = new HashSet<>();
        nonCovered.addAll(loaded);
        nonCovered.removeAll(covered);
        return nonCovered;
    }

    List<Integer> nonCoveredLineNumbers() {
        Set<Integer> linesNotCovered = new HashSet<>();
        for (SourceSection ss : nonCoveredSections()) {
            for (int i = ss.getStartLine(); i <= ss.getEndLine(); i++) {
                linesNotCovered.add(i);
            }
        }
        List<Integer> sortedLines = new ArrayList<>(linesNotCovered.size());
        sortedLines.addAll(linesNotCovered);
        sortedLines.sort(Integer::compare);
        return sortedLines;
    }
}
