/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.agentscript.impl;

import java.util.Set;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * This class searches for an instrumetable location (SourceSection tagged with one of the element
 * tags) in a Source file.
 *
 * We can verify whether the initial location is accurate and if yes, no language adjustment is
 * necessary.
 *
 * We find one of following three nodes:
 * <ul>
 * <li>node whose source section contains the location,
 * <li>node before the location,
 * <li>node after the location
 * </ul>
 * Using this context node, the language determines the nearest tagged node.
 * <p>
 * This class is a copy of com.oracle.truffle.api.debug.SuspendableLocationFinder.
 */
// GR-39189 to merge multiple implementations to an API.
final class InstrumentableLocationFinder {

    private InstrumentableLocationFinder() {
    }

    static SourceSection findNearest(Source source, Set<Class<? extends Tag>> elementTags, int line, int column, boolean isOnEnter, TruffleInstrument.Env env) {
        if (!source.hasCharacters()) {
            return null;
        }
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
        return findNearestBound(source, elementTags, boundLine, boundColumn, isOnEnter, env);
    }

    private static SourceSection findNearestBound(Source source, Set<Class<? extends Tag>> elementTags,
                    int line, int column, boolean isOnEnter, TruffleInstrument.Env env) {
        int offset = source.getLineStartOffset(line);
        if (column > 0) {
            offset += column - 1;
        }
        NearestSections sectionsCollector = new NearestSections(elementTags, (column <= 0) ? line : 0, offset, isOnEnter);
        // All SourceSections of the Source are loaded already when the source was executed
        env.getInstrumenter().visitLoadedSourceSections(
                        SourceSectionFilter.newBuilder().sourceIs(source).build(),
                        sectionsCollector);
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
        if (!sectionsCollector.isOffsetInRoot) {
            // The offset position is not in any RootNode.
            SourceSection sourceSection = ((Node) contextNode).getSourceSection();
            // Handle a special case when the location is not in any RootNode,
            // but it's on a line with an existing code at a greater column:
            boolean onLineBeforeLocation = sourceSection != null && isOnEnter && line == sourceSection.getStartLine() && column <= sourceSection.getStartColumn();
            if (!onLineBeforeLocation) {
                return null;
            }
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
        private final boolean isOnEnter;
        private SourceSection exactLineMatch;
        private SourceSection exactIndexMatch;
        private SourceSection containsMatch;
        private LinkedNodes containsNode;
        private SourceSection previousMatch;
        private LinkedNodes previousNode;
        private SourceSection nextMatch;
        private LinkedNodes nextNode;
        private boolean isOffsetInRoot = false;

        NearestSections(Set<Class<? extends Tag>> elementTags, int line, int offset, boolean isOnEnter) {
            this.elementTags = elementTags;
            this.line = line;
            this.offset = offset;
            this.isOnEnter = isOnEnter;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            Node eventNode = event.getNode();
            if (!(eventNode instanceof InstrumentableNode && ((InstrumentableNode) eventNode).isInstrumentable())) {
                return;
            }
            if (!isOffsetInRoot) {
                SourceSection rootSection = eventNode.getRootNode().getSourceSection();
                if (rootSection != null) {
                    isOffsetInRoot = rootSection.getCharIndex() <= offset && offset < rootSection.getCharEndIndex();
                }
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
                if (isOnEnter) {
                    l = sourceSection.getStartLine();
                } else {
                    l = sourceSection.getEndLine();
                }
                if (line == l && isTaggedWith(node, elementTags)) {
                    if (exactLineMatch == null ||
                                    isOnEnter && sourceSection.getCharIndex() < exactLineMatch.getCharIndex() ||
                                    !isOnEnter && sourceSection.getCharEndIndex() > exactLineMatch.getCharEndIndex()) {
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
            if (isOnEnter) {
                o = o1;
            } else {
                o = o2;
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
                    containsNode = new LinkedNodes(node);
                } else if (containsMatch.getCharLength() == sourceSection.getCharLength()) {
                    containsNode.append(new LinkedNodes(node));
                }
            } else if (o2 < offset) {
                // Previous match. Find the nearest one (with the largest end index):
                if (previousMatch == null || previousMatch.getCharEndIndex() < sourceSection.getCharEndIndex() ||
                                // when equal end, find the largest one
                                previousMatch.getCharEndIndex() == sourceSection.getCharEndIndex() && previousMatch.getCharLength() < sourceSection.getCharLength()) {
                    previousMatch = sourceSection;
                    previousNode = new LinkedNodes(node);
                } else if (previousMatch.getCharEndIndex() == sourceSection.getCharEndIndex() && previousMatch.getCharLength() == sourceSection.getCharLength()) {
                    previousNode.append(new LinkedNodes(node));
                }
            } else {
                assert offset < o1;
                // Next match. Find the nearest one (with the smallest start index):
                if (nextMatch == null || nextMatch.getCharIndex() > sourceSection.getCharIndex() ||
                                // when equal start, find the largest one
                                nextMatch.getCharIndex() == sourceSection.getCharIndex() && nextMatch.getCharLength() < sourceSection.getCharLength()) {
                    nextMatch = sourceSection;
                    nextNode = new LinkedNodes(node);
                } else if (nextMatch.getCharIndex() == sourceSection.getCharIndex() && nextMatch.getCharLength() == sourceSection.getCharLength()) {
                    nextNode.append(new LinkedNodes(node));
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
            if (containsNode == null) {
                return null;
            }
            if (line > 0) {
                if (isOnEnter && line == containsMatch.getStartLine() || !isOnEnter && line == containsMatch.getEndLine()) {
                    return (InstrumentableNode) containsNode.getOuter(containsMatch.getCharLength());
                }
            } else {
                if (isOnEnter && offset == containsMatch.getCharIndex() || !isOnEnter && offset == containsMatch.getCharEndIndex() - 1) {
                    return (InstrumentableNode) containsNode.getOuter(containsMatch.getCharLength());
                }
            }
            return (InstrumentableNode) containsNode.getInner(containsMatch.getCharLength());
        }

        InstrumentableNode getPreviousNode() {
            if (previousNode == null) {
                return null;
            }
            return (InstrumentableNode) previousNode.getOuter(previousMatch.getCharLength());
        }

        InstrumentableNode getNextNode() {
            if (nextNode == null) {
                return null;
            }
            return (InstrumentableNode) nextNode.getOuter(nextMatch.getCharLength());
        }
    }

    /**
     * Linked list of nodes that have the same source sections.
     */
    private static final class LinkedNodes {
        final Node node;
        private LinkedNodes next;

        LinkedNodes(InstrumentableNode node) {
            this.node = (Node) node;
        }

        void append(LinkedNodes lns) {
            LinkedNodes tail = this;
            while (tail.next != null) {
                tail = tail.next;
            }
            tail.next = lns;
        }

        Node getInner(int sectionLength) {
            Node inner = this.node;
            LinkedNodes linkedNodes = this.next;
            while (linkedNodes != null) {
                Node inner2 = linkedNodes.node;
                if (isParentOf(inner, inner2)) {
                    // inner stays
                } else if (isParentOf(inner2, inner)) {
                    inner = inner2;
                } else {
                    // They are in different functions, find out which encloses the other
                    if (hasLargerParent(inner2, sectionLength)) {
                        // inner stays
                    } else {
                        inner = inner2;
                    }
                }
                linkedNodes = linkedNodes.next;
            }
            return inner;
        }

        Node getOuter(int sectionLength) {
            Node outer = this.node;
            LinkedNodes linkedNodes = this.next;
            while (linkedNodes != null) {
                Node outer2 = linkedNodes.node;
                if (isParentOf(outer, outer2)) {
                    outer = outer2;
                } else if (isParentOf(outer2, outer)) {
                    // outer stays
                } else {
                    // They are in different functions, find out which encloses the other
                    if (hasLargerParent(outer2, sectionLength)) {
                        outer = outer2;
                    } else {
                        // outer stays
                    }
                }
                linkedNodes = linkedNodes.next;
            }
            return outer;
        }

        @Override
        public String toString() {
            if (next == null) {
                return node.toString();
            }
            StringBuilder sb = new StringBuilder("[");
            LinkedNodes ln = this;
            while (ln != null) {
                sb.append(ln.node);
                sb.append(", ");
                ln = ln.next;
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append("]");
            return sb.toString();
        }

        private static boolean isParentOf(Node ch, Node p) {
            Node parent = ch.getParent();
            while (parent != null) {
                if (parent == p) {
                    return true;
                }
                parent = parent.getParent();
            }
            return false;
        }

        private static boolean hasLargerParent(Node ch, int sectionLength) {
            Node parent = ch.getParent();
            while (parent != null) {
                if (parent instanceof InstrumentableNode && ((InstrumentableNode) parent).isInstrumentable() || parent instanceof RootNode) {
                    SourceSection pss = parent.getSourceSection();
                    if (pss != null && pss.getCharLength() > sectionLength) {
                        return true;
                    }
                }
                parent = parent.getParent();
            }
            return false;
        }
    }
}
