/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * This class searches for a suspendable location (SourceSection tagged with one of the element
 * tags) in a Source file.
 *
 * We can verify whether the initial suspended location is accurate and if yes, no language
 * adjustment is necessary.
 *
 * We find one of following three nodes:
 * <ul>
 * <li>node whose source section contains the location,
 * <li>node before the location,
 * <li>node after the location
 * </ul>
 * Using this context node, the language determines the nearest tagged node.
 */
final class SuspendableLocationFinder {

    private SuspendableLocationFinder() {
    }

    static SourceSection findNearest(Source source, SourceElement[] sourceElements, int line, int column, SuspendAnchor anchor, TruffleInstrument.Env env) {
        int boundLine = line;
        int boundColumn = column;
        int maxLine = source.getLineCount();
        if (boundLine > maxLine) {
            boundLine = maxLine;
        }
        int maxColumn = source.getLineLength(boundLine) + 1;
        if (boundColumn > maxColumn) {
            boundColumn = maxColumn;
        }
        return findNearestBound(source, getElementTags(sourceElements), boundLine, boundColumn, anchor, env);
    }

    private static Set<Class<? extends Tag>> getElementTags(SourceElement[] sourceElements) {
        if (sourceElements.length == 1) {
            return Collections.singleton(sourceElements[0].getTag());
        }
        Set<Class<? extends Tag>> elementTags = new HashSet<>();
        for (int i = 0; i < sourceElements.length; i++) {
            elementTags.add(sourceElements[i].getTag());
        }
        return elementTags;
    }

    private static SourceSection findNearestBound(Source source, Set<Class<? extends Tag>> elementTags,
                    int line, int column, SuspendAnchor anchor, TruffleInstrument.Env env) {
        int offset = source.getLineStartOffset(line);
        if (column > 0) {
            offset += column - 1;
        }
        NearestSections sectionsCollector = new NearestSections(elementTags, (column <= 0) ? line : 0, offset, anchor);
        // All SourceSections of the Source are loaded already when the source was executed
        env.getInstrumenter().attachLoadSourceSectionListener(
                        SourceSectionFilter.newBuilder().sourceIs(source).build(),
                        sectionsCollector, true).dispose();
        SourceSection section = sectionsCollector.getExactSection();
        if (section != null) {
            return section;
        }
        InstrumentableNode contextNode = sectionsCollector.getContainsNode();
        if (contextNode == null) {
            contextNode = sectionsCollector.getNextNode();
        }
        if (contextNode == null) {
            contextNode = sectionsCollector.getPreviousNode();
        }
        if (contextNode == null) {
            return null;
        }
        Node node = contextNode.findNearestNodeAt(offset, elementTags);
        if (node == null) {
            return null;
        }
        return node.getSourceSection();
    }

    private static class NearestSections implements LoadSourceSectionListener {

        private final Set<Class<? extends Tag>> elementTags;
        private final int line;
        private final int offset;
        private final SuspendAnchor anchor;
        private SourceSection exactLineMatch;
        private SourceSection exactIndexMatch;
        private SourceSection containsMatch;
        private InstrumentableNode containsNode;
        private SourceSection previousMatch;
        private InstrumentableNode previousNode;
        private SourceSection nextMatch;
        private InstrumentableNode nextNode;

        NearestSections(Set<Class<? extends Tag>> elementTags, int line, int offset, SuspendAnchor anchor) {
            this.elementTags = elementTags;
            this.line = line;
            this.offset = offset;
            this.anchor = anchor;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            Node eventNode = event.getNode();
            if (!(eventNode instanceof InstrumentableNode && ((InstrumentableNode) eventNode).isInstrumentable())) {
                return;
            }
            InstrumentableNode node = (InstrumentableNode) eventNode;
            SourceSection sourceSection = event.getSourceSection();
            if (matchSectionLine(node, sourceSection)) {
                // We have exact line match, we do not need to do anything more
                return;
            }
            int o1 = sourceSection.getCharIndex();
            int o2;
            if (sourceSection.getCharLength() > 0) {
                o2 = sourceSection.getCharEndIndex() - 1;
            } else {
                o2 = sourceSection.getCharIndex();
            }
            if (matchSectionOffset(node, sourceSection, o1, o2)) {
                // We have exact offset index match, we do not need to do anything more
                return;
            }
            // Offset approximation
            findOffsetApproximation(node, sourceSection, o1, o2);
        }

        private boolean matchSectionLine(InstrumentableNode node, SourceSection sourceSection) {
            if (line > 0) {
                int l;
                switch (anchor) {
                    case BEFORE:
                        l = sourceSection.getStartLine();
                        break;
                    case AFTER:
                        l = sourceSection.getEndLine();
                        break;
                    default:
                        throw new IllegalArgumentException(anchor.name());
                }
                if (line == l && isTaggedWith(node, elementTags)) {
                    if (exactLineMatch == null ||
                                    (anchor == SuspendAnchor.BEFORE) && sourceSection.getCharIndex() < exactLineMatch.getCharIndex() ||
                                    (anchor == SuspendAnchor.AFTER) && sourceSection.getCharEndIndex() > exactLineMatch.getCharEndIndex()) {
                        exactLineMatch = sourceSection;
                    }
                }
                if (exactLineMatch != null) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchSectionOffset(InstrumentableNode node, SourceSection sourceSection, int o1, int o2) {
            int o;
            switch (anchor) {
                case BEFORE:
                    o = o1;
                    break;
                case AFTER:
                    o = o2;
                    break;
                default:
                    throw new IllegalArgumentException(anchor.name());
            }
            if (offset == o && isTaggedWith(node, elementTags)) {
                if (exactIndexMatch == null || sourceSection.getCharLength() > exactIndexMatch.getCharLength()) {
                    exactIndexMatch = sourceSection;
                }
            }
            if (exactIndexMatch != null) {
                return true;
            }
            return false;
        }

        private void findOffsetApproximation(InstrumentableNode node, SourceSection sourceSection, int o1, int o2) {
            if (o1 <= offset && offset <= o2) {
                // Exact match. There can be more of these, find the smallest one:
                if (containsMatch == null || containsMatch.getCharLength() > sourceSection.getCharLength()) {
                    containsMatch = sourceSection;
                    containsNode = node;
                }
            } else if (o2 < offset) {
                // Previous match. Find the nearest one (with the largest end index):
                if (previousMatch == null || previousMatch.getCharEndIndex() < sourceSection.getCharEndIndex() ||
                                // when equal end, find the largest one
                                previousMatch.getCharEndIndex() == sourceSection.getCharEndIndex() && previousMatch.getCharLength() < sourceSection.getCharLength()) {
                    previousMatch = sourceSection;
                    previousNode = node;
                }
            } else {
                assert offset < o1;
                // Next match. Find the nearest one (with the smallest start index):
                if (nextMatch == null || nextMatch.getCharIndex() > sourceSection.getCharIndex() ||
                                // when equal start, find the largest one
                                nextMatch.getCharIndex() == sourceSection.getCharIndex() && nextMatch.getCharLength() < sourceSection.getCharLength()) {
                    nextMatch = sourceSection;
                    nextNode = node;
                }
            }
        }

        private static boolean isTaggedWith(InstrumentableNode node, Set<Class<? extends Tag>> tags) {
            for (Class<? extends Tag> tag : tags) {
                if (node.hasTag(tag)) {
                    return true;
                }
            }
            return false;
        }

        SourceSection getExactSection() {
            if (exactLineMatch != null) {
                return exactLineMatch;
            }
            if (exactIndexMatch != null) {
                return exactIndexMatch;
            }
            return null;
        }

        InstrumentableNode getContainsNode() {
            return containsNode;
        }

        InstrumentableNode getPreviousNode() {
            return previousNode;
        }

        InstrumentableNode getNextNode() {
            return nextNode;
        }
    }

}
