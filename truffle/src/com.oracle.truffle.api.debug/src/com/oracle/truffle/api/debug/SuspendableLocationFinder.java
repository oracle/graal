/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.debug;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
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
 * <p>
 * This class has a copy at com.oracle.truffle.tools.agentscript.impl.InstrumentableLocationFinder.
 */
// GR-39189 to merge multiple implementations to an API.
final class SuspendableLocationFinder {

    private SuspendableLocationFinder() {
    }

    static SourceSection findNearest(Source source, SourceElement[] sourceElements, int line, int column, SuspendAnchor anchor, TruffleInstrument.Env env) {
        int boundLine = line;
        int boundColumn = column;
        if (source.hasCharacters()) {
            int maxLine = source.getLineCount();
            if (boundLine > maxLine) {
                boundLine = maxLine;
            }
            int maxColumn = source.getLineLength(boundLine) + 1;
            if (boundColumn > maxColumn) {
                boundColumn = maxColumn;
            }
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
        int offset = getOffset(source, line, column);
        NearestSections sectionsCollector = new NearestSections(elementTags, new Position(line, column, offset), anchor);
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
            boolean onLineBeforeLocation = sourceSection != null && anchor == SuspendAnchor.BEFORE && sourceSection.hasLines() && line == sourceSection.getStartLine() &&
                            column <= sourceSection.getStartColumn();
            if (!onLineBeforeLocation) {
                return null;
            }
        }
        Node node;
        SourceSection contextSection = ((Node) contextNode).getSourceSection();
        if (offset >= 0 && contextSection.hasCharIndex()) {
            node = contextNode.findNearestNodeAt(offset, elementTags);
        } else {
            node = DefaultNearestNodeSearch.findNearestNodeAt(line, column, (Node) contextNode, elementTags);
        }
        if (node == null) {
            return null;
        }
        return node.getSourceSection();
    }

    private static int getOffset(Source source, int line, int column) {
        if (!source.hasCharacters()) {
            return -1;
        }
        int offset = source.getLineStartOffset(line);
        if (column > 0) {
            offset += column - 1;
        }
        return offset;
    }

    private static boolean isEnclosing(SourceSection section, SourceSection enclosingSection) {
        Position s1 = Position.startOf(section);
        Position s2 = Position.endOf(section);
        Position es1 = Position.startOf(enclosingSection);
        Position es2 = Position.endOf(enclosingSection);
        return es1.isLessThanOrEqual(s1) && s2.isLessThan(es2) ||
                        es1.isLessThan(s1) && s2.isLessThanOrEqual(es2);
    }

    private static class NearestSections implements LoadSourceSectionListener {

        private final Set<Class<? extends Tag>> elementTags;
        private final Position position;
        private final SuspendAnchor anchor;
        private SourceSection exactLineMatch;
        private SourceSection exactIndexMatch;
        private SourceSection containsMatch;
        private LinkedNodes containsNode;
        private SourceSection previousMatch;
        private LinkedNodes previousNode;
        private SourceSection nextMatch;
        private LinkedNodes nextNode;
        private boolean isOffsetInRoot = false;

        NearestSections(Set<Class<? extends Tag>> elementTags, Position position, SuspendAnchor anchor) {
            this.elementTags = elementTags;
            this.position = position;
            this.anchor = anchor;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            Node eventNode = event.getNode();
            if (!(eventNode instanceof InstrumentableNode && ((InstrumentableNode) eventNode).isInstrumentable())) {
                return;
            }
            SourceSection sourceSection = event.getSourceSection();
            if (!isOffsetInRoot) {
                SourceSection rootSection = eventNode.getRootNode().getSourceSection();
                if (rootSection != null) {
                    boolean is = position.isIn(rootSection);
                    if (!is && rootSection.hasLines() && rootSection.getStartLine() == rootSection.getEndLine()) {
                        // It's likely that the root source section is incomplete
                        // Check if the position is between root start and the end of this section
                        is = Position.startOf(rootSection).isLessThanOrEqual(position) && position.isLessThanOrEqual(Position.endOf(sourceSection));
                    }
                    isOffsetInRoot = is;
                }
            }
            InstrumentableNode node = (InstrumentableNode) eventNode;
            if (matchSectionLine(node, sourceSection)) {
                // We have exact line match, we do not need to do anything more
                return;
            }
            Position p1 = Position.startOf(sourceSection);
            Position p2 = Position.endOf(sourceSection);
            if (matchSectionPosition(node, sourceSection, p1, p2)) {
                // We have exact offset index match, we do not need to do anything more
                return;
            }
            // Offset approximation
            findOffsetApproximation(node, sourceSection, p1, p2);
        }

        private boolean matchSectionLine(InstrumentableNode node, SourceSection sourceSection) {
            if (position.line > 0 && position.column <= 0) {
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
                if (position.line == l && isTaggedWith(node, elementTags)) {
                    // Either the exactIndexMatch was not set yet,
                    // or this section starts before or ends after it,
                    // or is greater than it
                    boolean match = false;
                    if (exactLineMatch == null) {
                        match = true;
                    } else if (anchor == SuspendAnchor.BEFORE) {
                        Position p1 = Position.startOf(sourceSection);
                        Position ep1 = Position.startOf(exactLineMatch);
                        if (p1.isLessThan(ep1) || p1.equals(ep1) && Position.endOf(sourceSection).isGreaterThan(Position.endOf(exactLineMatch))) {
                            match = true;
                        }
                    } else {
                        Position p2 = Position.endOf(sourceSection);
                        Position ep2 = Position.endOf(exactLineMatch);
                        if (p2.isGreaterThan(ep2) || p2.equals(ep2) && Position.startOf(sourceSection).isLessThan(Position.startOf(exactLineMatch))) {
                            match = true;
                        }
                    }
                    if (match) {
                        exactLineMatch = sourceSection;
                    }
                }
                if (exactLineMatch != null) {
                    return true;
                }
            }
            return false;
        }

        private boolean matchSectionPosition(InstrumentableNode node, SourceSection sourceSection, Position p1, Position p2) {
            boolean anchorBefore = anchor == SuspendAnchor.BEFORE;
            Position p = anchorBefore ? p1 : p2;
            if (position.equals(p) && isTaggedWith(node, elementTags)) {
                // Either the exactIndexMatch was not set yet, or this section is greater than it
                if (exactIndexMatch == null || anchorBefore && p2.isGreaterThan(Position.endOf(exactIndexMatch)) || !anchorBefore && p1.isLessThan(Position.startOf(exactIndexMatch))) {
                    exactIndexMatch = sourceSection;
                }
            }
            if (exactIndexMatch != null) {
                return true;
            }
            return false;
        }

        private void findOffsetApproximation(InstrumentableNode node, SourceSection sourceSection, Position p1, Position p2) {
            if (p1.isLessThanOrEqual(position) && position.isLessThanOrEqual(p2)) {
                // Exact match. There can be more of these, find the smallest one:
                if (containsMatch == null || isEnclosing(sourceSection, containsMatch)) {
                    containsMatch = sourceSection;
                    containsNode = new LinkedNodes(node);
                } else if (containsMatch.equals(sourceSection)) {
                    containsNode.append(new LinkedNodes(node));
                }
            } else if (p2.isLessThan(position)) {
                // Previous match. Find the nearest one (with the largest end index):
                if (previousMatch == null || Position.endOf(previousMatch).isLessThan(Position.endOf(sourceSection)) ||
                                // when equal end, find the largest one
                                Position.endOf(previousMatch).equals(Position.endOf(sourceSection)) && Position.startOf(previousMatch).isLessThan(Position.startOf(sourceSection))) {
                    previousMatch = sourceSection;
                    previousNode = new LinkedNodes(node);
                } else if (previousMatch.equals(sourceSection)) {
                    previousNode.append(new LinkedNodes(node));
                }
            } else {
                assert position.isLessThan(p1);
                // Next match. Find the nearest one (with the smallest start index):
                if (nextMatch == null || Position.startOf(nextMatch).isGreaterThan(Position.startOf(sourceSection)) ||
                                // when equal start, find the largest one
                                Position.startOf(nextMatch).equals(Position.startOf(sourceSection)) && Position.endOf(nextMatch).isLessThan(Position.endOf(sourceSection))) {
                    nextMatch = sourceSection;
                    nextNode = new LinkedNodes(node);
                } else if (nextMatch.equals(sourceSection)) {
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
            boolean anchorBefore = anchor == SuspendAnchor.BEFORE;
            if (anchorBefore && position.equals(Position.startOf(containsMatch)) || !anchorBefore && position.equals(Position.endOf(containsMatch))) {
                return (InstrumentableNode) containsNode.getOuter(containsMatch);
            } else {
                return (InstrumentableNode) containsNode.getInner(containsMatch);
            }
        }

        InstrumentableNode getPreviousNode() {
            if (previousNode == null) {
                return null;
            }
            return (InstrumentableNode) previousNode.getOuter(previousMatch);
        }

        InstrumentableNode getNextNode() {
            if (nextNode == null) {
                return null;
            }
            return (InstrumentableNode) nextNode.getOuter(nextMatch);
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

        Node getInner(SourceSection section) {
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
                    if (hasLargerParent(inner2, section)) {
                        // inner stays
                    } else {
                        inner = inner2;
                    }
                }
                linkedNodes = linkedNodes.next;
            }
            return inner;
        }

        Node getOuter(SourceSection section) {
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
                    if (hasLargerParent(outer2, section)) {
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

        private static boolean hasLargerParent(Node ch, SourceSection section) {
            Node parent = ch.getParent();
            while (parent != null) {
                if (parent instanceof InstrumentableNode && ((InstrumentableNode) parent).isInstrumentable() || parent instanceof RootNode) {
                    SourceSection pss = parent.getSourceSection();
                    if (pss != null && isEnclosing(section, pss)) {
                        return true;
                    }
                }
                parent = parent.getParent();
            }
            return false;
        }
    }

    static final class Position {

        private final int line;     // 1-based line, or <= 0 when unknown
        private final int column;   // 1-based column, or <= 0 when unknown
        private final int offset;   // 0-based offset, or < 0 when unknown

        Position(int line, int column, int offset) {
            this.line = line;
            this.column = column;
            this.offset = offset;
        }

        static Position startOf(SourceSection section) {
            int line = section.hasLines() ? section.getStartLine() : -1;
            int column = section.hasColumns() ? section.getStartColumn() : -1;
            int offset = section.hasCharIndex() ? section.getCharIndex() : -1;
            return new Position(line, column, offset);
        }

        static Position endOf(SourceSection section) {
            int line = section.hasLines() ? section.getEndLine() : -1;
            int column = section.hasColumns() ? section.getEndColumn() : -1;
            int offset;
            if (section.hasCharIndex()) {
                if (section.getCharLength() > 0) {
                    offset = section.getCharEndIndex() - 1;
                } else {
                    offset = section.getCharIndex();
                }
            } else {
                offset = -1;
            }
            return new Position(line, column, offset);
        }

        boolean isIn(SourceSection section) {
            if (offset >= 0 && section.hasCharIndex()) {
                return section.getCharIndex() <= offset && offset < section.getCharEndIndex();
            }
            if (line > 0 && section.hasLines()) {
                if (section.getStartLine() <= line && line <= section.getEndLine()) {
                    if (column > 0 && section.hasColumns()) {
                        if (section.getStartLine() == line) {
                            if (column < section.getStartColumn()) {
                                return false;
                            }
                        }
                        if (section.getEndLine() == line) {
                            if (section.getEndColumn() < column) {
                                return false;
                            }
                        }
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 29 * hash + this.line;
            hash = 29 * hash + this.column;
            hash = 29 * hash + this.offset;
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj instanceof Position) {
                final Position other = (Position) obj;
                if (this.offset >= 0 && other.offset >= 0) {
                    return this.offset == other.offset;
                }
                if (this.line != other.line) {
                    return false;
                }
                if (this.column > 0 && other.column > 0) {
                    return this.column == other.column;
                } else {
                    // If one of the columns is undefined, it still matches
                    return true;
                }
            } else {
                return false;
            }
        }

        boolean isLessThan(Position other) {
            if (this.offset >= 0 && other.offset >= 0) {
                return this.offset < other.offset;
            }
            if (0 < this.line && this.line < other.line) {
                return true;
            }
            return this.line == other.line && 0 < this.column && this.column < other.column;
        }

        boolean isLessThanOrEqual(Position other) {
            if (this.offset >= 0 && other.offset >= 0) {
                return this.offset <= other.offset;
            }
            if (0 < this.line && this.line < other.line) {
                return true;
            }
            return this.line == other.line && (0 < this.column && this.column <= other.column || this.column <= 0 || other.column <= 0);
        }

        boolean isGreaterThan(Position other) {
            return other.isLessThan(this);
        }

        boolean isGreaterThanOrEqual(Position other) {
            return other.isGreaterThanOrEqual(this);
        }

        static Comparator<? super Position> COMPARATOR = new Comparator<>() {

            @Override
            public int compare(Position p1, Position p2) {
                if (p1.equals(p2)) {
                    return 0;
                }
                if (p1.isLessThan(p2)) {
                    return -1;
                } else {
                    return +1;
                }
            }
        };
    }
}
