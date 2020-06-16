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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;

final class LineCoverage {

    private static final char STATEMENT_NOT = '-';
    private static final char STATEMENT_YES = '+';
    private static final char STATEMENT_PARTLY = 'p';
    private static final char STATEMENT_EMPTY = ' ';
    private final Map<Integer, LineState> lines;

    LineCoverage(SourceCoverage coverage, boolean strictLines) {
        lines = makeLines(coverage, strictLines);
    }

    private static Map<Integer, LineState> makeLines(SourceCoverage coverage, boolean strictLines) {
        final Source source = coverage.getSource();
        if (!source.hasCharacters()) {
            return Collections.emptyMap();
        }
        final int lineCount = source.getLineCount();
        final HashMap<Integer, List<SectionCoverage>> lineContent = new HashMap<>(lineCount);
        for (RootCoverage rootCoverage : coverage.getRoots()) {
            for (SectionCoverage section : rootCoverage.getSectionCoverage()) {
                final SourceSection sectionSourceSection = section.getSourceSection();
                for (int i = sectionSourceSection.getStartLine(); i <= sectionSourceSection.getEndLine(); i++) {
                    lineContent.computeIfAbsent(i, key -> new ArrayList<>()).add(section);
                }

            }
        }
        final HashMap<Integer, LineState> lines = new HashMap<>(lineCount);
        for (Map.Entry<Integer, List<SectionCoverage>> content : lineContent.entrySet()) {
            lines.put(content.getKey(), strictLines ? strictState(content.getValue()) : lenientState(content.getValue()));
        }
        return lines;
    }

    private static LineState lenientState(List<SectionCoverage> sections) {
        if (sections.stream().anyMatch(SectionCoverage::isCovered)) {
            return LineState.Covered;
        }
        return LineState.NotCovered;
    }

    private static LineState strictState(List<SectionCoverage> sections) {
        if (sections.stream().allMatch(SectionCoverage::isCovered)) {
            return LineState.Covered;
        }
        if (sections.stream().noneMatch(SectionCoverage::isCovered)) {
            return LineState.NotCovered;
        }
        if (isIncidental(sections)) {
            return LineState.NotCovered;
        }
        return LineState.Partial;
    }

    private static boolean isIncidental(List<SectionCoverage> sections) {
        return sections.stream().anyMatch(e -> !e.isCovered() && hasCoveredSuperSection(sections, e.getSourceSection()));
    }

    private static boolean hasCoveredSuperSection(List<SectionCoverage> entries, SourceSection incidentalCandidate) {
        return entries.stream().anyMatch(e -> {
            final SourceSection sourceSection = e.getSourceSection();
            if (!e.isCovered() || sourceSection == incidentalCandidate) {
                return false;
            }
            return sourceSection.getCharIndex() <= incidentalCandidate.getCharIndex() &&
                            sourceSection.getCharEndIndex() >= incidentalCandidate.getCharEndIndex() &&
                            (sourceSection.getStartLine() < incidentalCandidate.getStartLine() ||
                                            sourceSection.getEndLine() > incidentalCandidate.getEndLine());
        });
    }

    double getCoverage() {
        final long loadedSize = lines.size();
        final long coveredSize = lines.values().stream().filter(lineState -> lineState == LineState.Covered).count();
        return ((double) coveredSize) / loadedSize;
    }

    char getStatementCoverageCharacter(int i) {
        if (!lines.containsKey(i)) {
            return STATEMENT_EMPTY;
        }
        switch (lines.get(i)) {
            case Covered:
                return STATEMENT_YES;
            case Partial:
                return STATEMENT_PARTLY;
            case NotCovered:
                return STATEMENT_NOT;
            default:
                throw new IllegalStateException();
        }
    }

    enum LineState {
        Covered,
        Partial,
        NotCovered,
    }

}
