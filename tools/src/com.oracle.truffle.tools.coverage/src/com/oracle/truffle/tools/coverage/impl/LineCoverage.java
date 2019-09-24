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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;

final class LineCoverage {

    private final Set<SourceSection> loadedSourceSections;
    private final Set<SourceSection> coveredSourceSections;
    private final Set<SourceSection> loadedRootSections;
    private final Set<SourceSection> coveredRootSections;
    private final Set<Integer> loadedLineNumbers;
    private final Set<Integer> coveredLineNumbers;
    private final Set<Integer> nonCoveredLineNumbers;
    private final Set<Integer> loadedRootLineNumbers;
    private final Set<Integer> coveredRootLineNumbers;
    private final Set<Integer> nonCoveredRootLineNumbers;

    LineCoverage(SourceCoverage coverage) {
        this(coverage, true);
    }

    LineCoverage(SourceCoverage coverage, boolean detailed) {
        loadedSourceSections = loadedSourceSections(coverage);
        coveredSourceSections = coveredSourceSections(coverage);
        loadedRootSections = detailed ? loadedRootSections(coverage) : null;
        coveredRootSections = detailed ? coveredRootSections(coverage) : null;

        loadedLineNumbers = loadedLineNumbers();
        coveredLineNumbers = coveredLineNumbers();
        nonCoveredLineNumbers = nonCoveredLineNumbers();
        loadedRootLineNumbers = detailed ? loadedRootLineNumbers() : null;
        coveredRootLineNumbers = detailed ? coveredRootLineNumbers() : null;
        nonCoveredRootLineNumbers = detailed ? nonCoveredRootLineNumbers() : null;
    }

    private static char getCoverageChar(int i, char partly, char not, char yes, Set<Integer> loaded, Set<Integer> covered, Set<Integer> nonCovered) {
        if (loaded.contains(i)) {
            if (covered.contains(i) && nonCovered.contains(i)) {
                return partly;
            }
            return nonCovered.contains(i) ? not : yes;
        } else {
            return ' ';
        }
    }

    private static Set<SourceSection> coveredSourceSections(SourceCoverage sourceCoverage) {
        Set<SourceSection> sourceSections = new HashSet<>();
        for (RootCoverage root : sourceCoverage.getRoots()) {
            // @formatter:off
            final List<SourceSection> covered = Arrays.stream(root.getSectionCoverage()).
                    filter(SectionCoverage::isCovered).
                    map(SectionCoverage::getSourceSection).
                    collect(Collectors.toList());
            // @formatter:on
            sourceSections.addAll(covered);
        }
        return sourceSections;
    }

    private static Set<SourceSection> loadedSourceSections(SourceCoverage sourceCoverage) {
        Set<SourceSection> sourceSections = new HashSet<>();
        for (RootCoverage root : sourceCoverage.getRoots()) {
            // @formatter:off
            final List<SourceSection> loaded = Arrays.stream(root.getSectionCoverage()).
                    map(SectionCoverage::getSourceSection).
                    collect(Collectors.toList());
            // @formatter:on
            sourceSections.addAll(loaded);
        }
        return sourceSections;
    }

    private static Set<SourceSection> coveredRootSections(SourceCoverage sourceCoverage) {
        final HashSet<SourceSection> sections = new HashSet<>();
        for (RootCoverage rootCoverage : sourceCoverage.getRoots()) {
            if (rootCoverage.isCovered()) {
                sections.add(rootCoverage.getSourceSection());
            }
        }
        return sections;
    }

    private static Set<SourceSection> loadedRootSections(SourceCoverage sourceCoverage) {
        final HashSet<SourceSection> sections = new HashSet<>();
        for (RootCoverage rootCoverage : sourceCoverage.getRoots()) {
            sections.add(rootCoverage.getSourceSection());
        }
        return sections;
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

    double getCoverage() {
        final int loadedSize = loadedLineNumbers.size();
        final int nonCoveredSize = nonCoveredLineNumbers.size();
        return ((double) loadedSize - nonCoveredSize) / loadedSize;
    }

    char getStatementCoverageCharacter(int i) {
        return getCoverageChar(i, 'i', '-', '+', loadedLineNumbers, coveredLineNumbers, nonCoveredLineNumbers);
    }

    char getRootCoverageCharacter(int i) {
        return getCoverageChar(i, '!', '!', ' ', loadedRootLineNumbers, coveredRootLineNumbers, nonCoveredRootLineNumbers);
    }

    private Set<Integer> nonCoveredLineNumbers() {
        Set<SourceSection> nonCoveredSections = new HashSet<>();
        nonCoveredSections.addAll(loadedSourceSections);
        nonCoveredSections.removeAll(coveredSourceSections);
        return statementsToLineNumbers(nonCoveredSections);
    }

    private Set<Integer> loadedLineNumbers() {
        return statementsToLineNumbers(loadedSourceSections);
    }

    private Set<Integer> coveredLineNumbers() {
        return statementsToLineNumbers(coveredSourceSections);
    }

    private Set<Integer> nonCoveredRootLineNumbers() {
        final HashSet<SourceSection> sections = new HashSet<>();
        sections.addAll(loadedRootSections);
        sections.removeAll(coveredRootSections);
        return statementsToLineNumbers(sections);
    }

    private Set<Integer> coveredRootLineNumbers() {
        return statementsToLineNumbers(coveredRootSections);
    }

    private Set<Integer> loadedRootLineNumbers() {
        return LineCoverage.statementsToLineNumbers(loadedRootSections);
    }
}
