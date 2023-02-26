/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.lang.ref.WeakReference;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Collects the nearest nodes according to {@link NearestSectionFilter}. This algorithm finds any
 * {@link InstrumentableNode instrumentable node} that is close to the specified {@link Position
 * position} and then calls {@link InstrumentableNode#findNearestNodeAt(int, int, Set)} to provide
 * the final nearest node.
 * <p>
 * An instance of this class is used by InstrumentationHandler.VisitOperation during node traversal.
 * The {@link #loadedSection(Node, SourceSection, SourceSection)} is called with the visited
 * {@link Node} and {@link SourceSection}. Every call updates data about the nearest node/section.
 * These data are stored in four categories: <code>exact</code>, <code>contains</code>,
 * <code>previous</code> and <code>next</code>. After the AST traversal is done, the
 * {@link #getNearest(Set)} is called from post-visit. It uses the collected data to find a
 * <code>contextNode</code>. If the exact node was matched, it is returned. When not,
 * {@link InstrumentableNode#findNearestNodeAt(int, int, Set)} is called on the
 * <code>contextNode</code> to provide the final nearest node.
 * <p>
 * The typical use of search for a nearest node is when the source code location is specified by a
 * user, a debugger breakpoint or Insight tracepoint, for instance. Use this via
 * {@link NearestSectionFilter}.
 *
 * @see NearestSectionFilter
 */
final class NearestNodesCollector {

    private final Position position;
    private final boolean anchorBefore;
    private final Set<Class<? extends Tag>> tags;

    // Set when no column is specified and line is matched exactly
    private InstrumentableNode exactLineNode;
    private SourceSection exactLineSection;
    // Set when both line and column are matched exactly
    private InstrumentableNode exactIndexNode;
    private SourceSection exactIndexSection;
    // The containing (enclosing) node
    private SourceSection containsSection;
    private LinkedNodes containsNode;
    // Previous node
    private SourceSection previousSection;
    private LinkedNodes previousNode;
    // Next node
    private SourceSection nextSection;
    private LinkedNodes nextNode;
    // Whether the position is in a RootNode's SourceSection.
    private boolean isOffsetInRoot = false;

    NearestNodesCollector(NearestSectionFilter nearestFilter) {
        this.position = nearestFilter.getPosition();
        this.anchorBefore = nearestFilter.isAnchorStart();
        this.tags = nearestFilter.getTagClasses();
    }

    Position getPosition() {
        return position;
    }

    void loadedSection(Node node, SourceSection sourceSection, SourceSection rootSection) {
        assert node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable();
        if (!isOffsetInRoot) {
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
        InstrumentableNode inode = (InstrumentableNode) node;
        if (matchSectionLine(inode, sourceSection)) {
            // We have exact line match, we do not need to do anything more
            return;
        }
        Position p1 = Position.startOf(sourceSection);
        Position p2 = Position.endOf(sourceSection);
        if (matchSectionPosition(inode, sourceSection, p1, p2)) {
            // We have exact offset index match, we do not need to do anything more
            return;
        }
        // Offset approximation
        findOffsetApproximation(inode, sourceSection, p1, p2);
    }

    private static int getOffset(Source source, int line, int column) {
        if (!source.hasCharacters()) {
            return -1;
        }
        int offset = source.getLineStartOffset(line);
        if (column > 0) {
            int c = Math.min(column, source.getLineLength(line) + 1);
            offset += c - 1;
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

    private static boolean isTaggedWith(InstrumentableNode node, Set<Class<? extends Tag>> tags) {
        if (tags == null) {
            return true;
        }
        for (Class<? extends Tag> tag : tags) {
            if (node.hasTag(tag)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchSectionLine(InstrumentableNode node, SourceSection sourceSection) {
        if (position.line > 0 && position.column <= 0) {
            int l = anchorBefore ? sourceSection.getStartLine() : sourceSection.getEndLine();
            if (position.line == l && isTaggedWith(node, tags)) {
                // Either the exactLineSection was not set yet,
                // or this section starts before or ends after it,
                // or is greater than it
                boolean match = false;
                if (exactLineSection == null) {
                    match = true;
                } else if (anchorBefore) {
                    Position p1 = Position.startOf(sourceSection);
                    Position ep1 = Position.startOf(exactLineSection);
                    if (p1.isLessThan(ep1) || p1.equals(ep1) && Position.endOf(sourceSection).isGreaterThan(Position.endOf(exactLineSection))) {
                        match = true;
                    }
                } else {
                    Position p2 = Position.endOf(sourceSection);
                    Position ep2 = Position.endOf(exactLineSection);
                    if (p2.isGreaterThan(ep2) || p2.equals(ep2) && Position.startOf(sourceSection).isLessThan(Position.startOf(exactLineSection))) {
                        match = true;
                    }
                }
                if (match) {
                    exactLineSection = sourceSection;
                    exactLineNode = node;
                }
            }
            if (exactLineSection != null) {
                return true;
            }
        }
        return false;
    }

    private boolean matchSectionPosition(InstrumentableNode node, SourceSection sourceSection, Position p1, Position p2) {
        Position p = anchorBefore ? p1 : p2;
        if (position.equals(p) && isTaggedWith(node, tags)) {
            // Either the exactIndexMatch was not set yet, or this section is greater than it
            if (exactIndexSection == null || anchorBefore && p2.isGreaterThan(Position.endOf(exactIndexSection)) || !anchorBefore && p1.isLessThan(Position.startOf(exactIndexSection))) {
                exactIndexSection = sourceSection;
                exactIndexNode = node;
            }
        }
        if (exactIndexSection != null) {
            return true;
        }
        return false;
    }

    private void findOffsetApproximation(InstrumentableNode node, SourceSection sourceSection, Position p1, Position p2) {
        if (p1.isLessThanOrEqual(position) && position.isLessThanOrEqual(p2)) {
            // Exact match. There can be more of these, find the smallest one:
            if (containsSection == null || isEnclosing(sourceSection, containsSection)) {
                containsSection = sourceSection;
                containsNode = new LinkedNodes(node);
            } else if (containsSection.equals(sourceSection)) {
                containsNode.append(new LinkedNodes(node));
            }
        } else if (p2.isLessThan(position)) {
            // Previous match. Find the nearest one (with the largest end index):
            if (previousSection == null || Position.endOf(previousSection).isLessThan(Position.endOf(sourceSection)) ||
                            // when equal end, find the largest one
                            Position.endOf(previousSection).equals(Position.endOf(sourceSection)) && Position.startOf(previousSection).isLessThan(Position.startOf(sourceSection))) {
                previousSection = sourceSection;
                previousNode = new LinkedNodes(node);
            } else if (previousSection.equals(sourceSection)) {
                previousNode.append(new LinkedNodes(node));
            }
        } else {
            assert position.isLessThan(p1);
            // Next match. Find the nearest one (with the smallest start index):
            if (nextSection == null || Position.startOf(nextSection).isGreaterThan(Position.startOf(sourceSection)) ||
                            // when equal start, find the largest one
                            Position.startOf(nextSection).equals(Position.startOf(sourceSection)) && Position.endOf(nextSection).isLessThan(Position.endOf(sourceSection))) {
                nextSection = sourceSection;
                nextNode = new LinkedNodes(node);
            } else if (nextSection.equals(sourceSection)) {
                nextNode.append(new LinkedNodes(node));
            }
        }
    }

    private InstrumentableNode getContainsNode() {
        if (containsNode == null) {
            return null;
        }
        if (anchorBefore && position.equals(Position.startOf(containsSection)) || !anchorBefore && position.equals(Position.endOf(containsSection))) {
            return (InstrumentableNode) containsNode.getOuter(containsSection);
        } else {
            return (InstrumentableNode) containsNode.getInner(containsSection);
        }
    }

    private InstrumentableNode getPreviousNode() {
        if (previousNode == null) {
            return null;
        }
        return (InstrumentableNode) previousNode.getOuter(previousSection);
    }

    private InstrumentableNode getNextNode() {
        if (nextNode == null) {
            return null;
        }
        return (InstrumentableNode) nextNode.getOuter(nextSection);
    }

    // Test whether other node is in the same root as the position inside an inner function
    private boolean isOtherInWithPosition(Node contains, Node otherNode, SourceSection otherSection) {
        if (isEnclosing(otherSection, containsSection)) {
            RootNode containsRoot = contains.getRootNode();
            RootNode otherRoot = otherNode.getRootNode();
            if (containsRoot != otherRoot) {
                // Contains section is in an enclosing function
                SourceSection nextRootSection = otherRoot.getSourceSection();
                if (position.isIn(nextRootSection)) {
                    // But the position is in the inner function
                    return true;
                }
            }
        }
        return false;
    }

    NodeSection getNearest(Set<Class<? extends Tag>> allProvidedTags) {
        Node nearestNode;
        SourceSection nearestSection;
        if (exactLineNode != null) {
            nearestNode = (Node) exactLineNode;
            nearestSection = exactLineSection;
        } else if (exactIndexNode != null) {
            nearestNode = (Node) exactIndexNode;
            nearestSection = exactIndexSection;
        } else {
            InstrumentableNode contextNode = null;
            InstrumentableNode contains = getContainsNode();
            InstrumentableNode next = getNextNode();
            InstrumentableNode previous = getPreviousNode();
            if (contains != null && next != null) {
                if (isOtherInWithPosition((Node) contains, (Node) next, nextSection)) {
                    contextNode = next;
                }
            }
            if (contextNode == null && contains != null && previous != null) {
                if (isOtherInWithPosition((Node) contains, (Node) previous, previousSection)) {
                    contextNode = previous;
                }
            }
            if (contextNode == null) {
                contextNode = contains;
            }
            if (contextNode == null) {
                contextNode = next;
            }
            if (contextNode == null) {
                contextNode = previous;
            }
            if (contextNode == null) {
                return null; // No nearest node
            }
            if (!isOffsetInRoot) {
                // The offset position is not in any RootNode.
                SourceSection sourceSection = ((Node) contextNode).getSourceSection();
                // Handle a special case when the location is not in any RootNode,
                // but it's on a line with an existing code at a greater column:
                boolean onLineBeforeLocation = sourceSection != null && anchorBefore &&
                                sourceSection.hasLines() && position.line == sourceSection.getStartLine() && (!sourceSection.hasColumns() || position.column <= sourceSection.getStartColumn());
                if (!onLineBeforeLocation) {
                    return null; // Outside of a RootNode
                }
            }
            Node node;
            SourceSection contextSection = ((Node) contextNode).getSourceSection();
            int offset = position.offset;
            if (offset < 0 && position.column >= 1) {
                offset = getOffset(contextSection.getSource(), position.line, position.column);
            }
            Set<Class<? extends Tag>> theTags = (tags != null) ? tags : allProvidedTags;
            if (offset >= 0 && contextSection.hasCharIndex()) {
                node = contextNode.findNearestNodeAt(offset, theTags);
            } else {
                node = contextNode.findNearestNodeAt(position.line, position.column, theTags);
            }
            if (node == null) {
                return null;
            }
            nearestNode = node;
            nearestSection = node.getSourceSection();
        }
        return new NodeSection(nearestNode, nearestSection);
    }

    static boolean isCloser(NodeSection newNearest, SourceSection rootSourceSection, Node oldNearestNode, SourceSection oldNearestSourceSection, NearestSectionFilter filter,
                    Set<Class<? extends Tag>> allTags) {
        NearestNodesCollector collector = new NearestNodesCollector(filter);
        collector.loadedSection(oldNearestNode, oldNearestSourceSection, oldNearestNode.getRootNode().getSourceSection());
        collector.loadedSection(newNearest.node, newNearest.section, rootSourceSection);
        NodeSection nearest = collector.getNearest(allTags);
        // Return true when the new is the nearest one.
        return newNearest.section.equals(nearest.section);
    }

    static final class NodeSection {

        final Node node;
        final SourceSection section;

        NodeSection(Node node, SourceSection section) {
            this.node = node;
            this.section = section;
        }
    }

    static final class NodeListSection {

        final List<WeakReference<Node>> nodes;
        final SourceSection section;

        NodeListSection(List<WeakReference<Node>> nodes, SourceSection section) {
            this.nodes = nodes;
            this.section = section;
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
            assert offset >= 0 || line >= 1 : toString();
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

        @Override
        public String toString() {
            return "Position[(" + line + ", " + column + ") offset: " + offset + "]";
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
