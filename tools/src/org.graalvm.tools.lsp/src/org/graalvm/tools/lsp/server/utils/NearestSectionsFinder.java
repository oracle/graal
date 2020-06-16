/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class NearestSectionsFinder {

    public enum NodeLocationType {
        CONTAINS,
        CONTAINS_END,
        PREVIOUS,
        NEXT,
        ROOT
    }

    private NearestSectionsFinder() {
    }

    public static NearestNode findNearestNode(Source source, int line, int character, Env env, TruffleLogger logger) {
        int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
        int oneBasedColumn = SourceUtils.zeroBasedColumnToOneBasedColumn(line, oneBasedLineNumber, character, source);
        NearestNode nearestNode = findNearestNodeOneBased(oneBasedLineNumber, oneBasedColumn, source, env);

        Node node = nearestNode.getNode();

        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "nearestNode: {0}\t{1}-\t{2}", new Object[]{
                            (node != null ? node.getClass().getSimpleName() : "--NULL--"),
                            nearestNode.getLocationType(),
                            (node != null ? node.getSourceSection() : "")});
        }

        return nearestNode;
    }

    public static NearestNode findExprNodeBeforePos(Source source, int line, int column, Env env) {
        int oneBasedLine = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
        int oneBasedColumn = SourceUtils.zeroBasedColumnToOneBasedColumn(line, oneBasedLine, column, source);
        SectionsBefore sectionsBefore = new SectionsBefore(oneBasedLine, oneBasedColumn);
        Class<?>[] exprTags = new Class<?>[]{StandardTags.ExpressionTag.class, StandardTags.ReadVariableTag.class, StandardTags.StatementTag.class};
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(source).tagIs(exprTags).build();
        env.getInstrumenter().attachLoadSourceSectionListener(filter, sectionsBefore, true).dispose();
        return sectionsBefore.getNearestNode();
    }

    private static NearestNode findNearestNodeOneBased(int oneBasedLineNumber, int column, Source source, Env env) {
        NearestSections nearestSections = findNearestSections(source, env, oneBasedLineNumber, column, false,
                        StandardTags.ExpressionTag.class, StandardTags.StatementTag.class, StandardTags.RootTag.class);
        SourceSection containsSection = nearestSections.getContainsSourceSection();

        NearestNode nearestNode;
        if (containsSection == null) {
            // The caret position is not contained in a source section of any valid node. This means
            // that there is no global root node in this language which wraps the whole file.
            nearestNode = new NearestNode(null, null, null);
        } else if (isEndOfSectionMatchingCaretPosition(oneBasedLineNumber, column, containsSection)) {
            // Our caret is directly behind the containing section, so we can simply use that one
            nearestNode = nearestSections.getContainsNode(true);
        } else if (nodeIsInChildHierarchyOf(nearestSections.nextNode, nearestSections.containsNode)) {
            // Great, the nextNode is a (indirect) sibling of our containing node, so it is in the
            // same scope as we are and we can use it to get local scope objects
            nearestNode = nearestSections.getNextNode();
        } else if (nodeIsInChildHierarchyOf(nearestSections.previousNode, nearestSections.containsNode)) {
            // In this case we actually want call findLocalScopes() with BEHIND-flag, i.e. give me
            // the local scope objects which are valid behind that node
            nearestNode = nearestSections.getPreviousNode();
        } else {
            // No next or previous node is in the same scope like us, so we can only take our
            // containing node is the "nearest"
            nearestNode = nearestSections.getContainsNode(false);
        }

        return nearestNode;
    }

    private static boolean isEndOfSectionMatchingCaretPosition(int line, int character, SourceSection section) {
        return section.getEndLine() == line && section.getEndColumn() == character;
    }

    private static boolean nodeIsInChildHierarchyOf(Node node, Node potentialParent) {
        if (node == null) {
            return false;
        }
        Node parent = node.getParent();
        while (parent != null) {
            if (parent.equals(potentialParent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    public static NearestSections findNearestSections(Source source, TruffleInstrument.Env env, int oneBasedLineNumber, int column, boolean instrumentableNodesOnly, Class<?>... tags) {
        return findNearestSections(source, env, SourceUtils.convertLineAndColumnToOffset(source, oneBasedLineNumber, column), instrumentableNodesOnly, tags);
    }

    protected static NearestSections findNearestSections(Source source, TruffleInstrument.Env env, int offset, boolean instrumentableNodesOnly, Class<?>... tags) {
        NearestSections sectionsCollector = new NearestSections(offset, instrumentableNodesOnly);
        Builder filter = SourceSectionFilter.newBuilder().sourceIs(source);
        if (tags.length > 0) {
            filter.tagIs(tags);
        }
        // All SourceSections of the Source are loaded already when the source was parsed
        env.getInstrumenter().attachLoadSourceSectionListener(
                        filter.build(),
                        sectionsCollector, true).dispose();
        return sectionsCollector;
    }

    private static final class SectionsBefore implements LoadSourceSectionListener {

        private final int line;
        private final int column;
        private SourceSection closestBefore;
        private Node node;
        private boolean hasExpression;

        SectionsBefore(int line, int column) {
            this.line = line;
            this.column = column;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            SourceSection sourceSection = event.getSourceSection();
            if (sourceSection.getEndLine() > line || sourceSection.getEndLine() == line && sourceSection.getEndColumn() > column) {
                // The section ends after line:column
                return;
            }
            if (closestBefore == null || sourceSection.getEndLine() > closestBefore.getEndLine()) {
                closestBefore = sourceSection;
                node = event.getNode();
                hasExpression = isExpression(event);
            } else if (sourceSection.getEndLine() == closestBefore.getEndLine()) {
                if (sourceSection.getEndColumn() > closestBefore.getEndColumn() ||
                                sourceSection.getEndColumn() == closestBefore.getEndColumn() &&
                                                sourceSection.getCharLength() > closestBefore.getCharLength()) {
                    boolean isExpression = isExpression(event);
                    // Prefer expressions:
                    if (!hasExpression || isExpression) {
                        closestBefore = sourceSection;
                        node = event.getNode();
                        hasExpression = isExpression;
                    }
                }
            }

            if (closestBefore == null) {
                closestBefore = sourceSection;
                node = event.getNode();
                hasExpression = isExpression(event);
            } else {
                boolean sameLine = sourceSection.getEndLine() == closestBefore.getEndLine();
                boolean sameColumn = sourceSection.getEndColumn() == closestBefore.getEndColumn();
                if (sourceSection.getEndLine() > closestBefore.getEndLine() ||
                                sameLine && sourceSection.getEndColumn() >= closestBefore.getEndColumn()) {
                    boolean isExpression = isExpression(event);
                    // Prefer expressions:
                    if (!hasExpression || isExpression) {
                        if (!sameLine || !sameColumn ||
                                        // Ends at the same position, prefer the longest expression,
                                        // if we don't have expressions, prefer not statements
                                        hasExpression && sourceSection.getCharLength() > closestBefore.getCharLength() ||
                                        !hasExpression && !isStatement(event)) {
                            closestBefore = sourceSection;
                            node = event.getNode();
                            hasExpression = isExpression;
                        }
                    }
                }
            }
        }

        private static boolean isExpression(LoadSourceSectionEvent event) {
            InstrumentableNode inode = (InstrumentableNode) event.getNode();
            return inode.hasTag(StandardTags.ExpressionTag.class);
        }

        private static boolean isStatement(LoadSourceSectionEvent event) {
            InstrumentableNode inode = (InstrumentableNode) event.getNode();
            return inode.hasTag(StandardTags.StatementTag.class);
        }

        NearestNode getNearestNode() {
            NodeLocationType type;
            if (closestBefore.getEndLine() == line && closestBefore.getEndColumn() == column) {
                type = NodeLocationType.CONTAINS_END;
            } else {
                type = NodeLocationType.PREVIOUS;
            }
            return new NearestNode(node, closestBefore, type);
        }
    }

    public static final class NearestSections implements LoadSourceSectionListener {

        private final int offset;
        private final boolean checkInstrumentable;
        private SourceSection containsMatch;
        private Node containsNode;
        private SourceSection previousMatch;
        private Node previousNode;
        private SourceSection nextMatch;
        private Node nextNode;

        NearestSections(int offset, boolean checkInstrumentable) {
            this.offset = offset;
            this.checkInstrumentable = checkInstrumentable;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            Node eventNode = event.getNode();
            if (checkInstrumentable && !(eventNode instanceof InstrumentableNode && ((InstrumentableNode) eventNode).isInstrumentable())) {
                return;
            }
            SourceSection sourceSection = event.getSourceSection();

            int o1 = sourceSection.getCharIndex();
            int o2;
            if (sourceSection.getCharLength() > 0) {
                o2 = sourceSection.getCharEndIndex() - 1;
            } else {
                o2 = sourceSection.getCharIndex();
            }
            // Offset approximation
            findOffsetApproximation(eventNode, sourceSection, o1, o2);
        }

        private void findOffsetApproximation(Node node, SourceSection sourceSection, int o1, int o2) {
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

        public NearestNode getContainsNode(boolean containsEnd) {
            return new NearestNode(containsNode, containsMatch, containsEnd ? NodeLocationType.CONTAINS_END : NodeLocationType.CONTAINS);
        }

        public NearestNode getPreviousNode() {
            return new NearestNode(previousNode, previousMatch, NodeLocationType.PREVIOUS);
        }

        public NearestNode getNextNode() {
            return new NearestNode(nextNode, nextMatch, NodeLocationType.NEXT);
        }

        public InstrumentableNode getInstrumentableContainsNode() {
            return (InstrumentableNode) containsNode;
        }

        public InstrumentableNode getInstrumentablePreviousNode() {
            return (InstrumentableNode) previousNode;
        }

        public InstrumentableNode getInstrumentableNextNode() {
            return (InstrumentableNode) nextNode;
        }

        public SourceSection getContainsSourceSection() {
            return containsNode != null ? containsNode.getSourceSection() : null;
        }

        public SourceSection getPreviousSourceSection() {
            return previousNode != null ? previousNode.getSourceSection() : null;
        }

        public SourceSection getNextSourceSection() {
            return nextNode != null ? nextNode.getSourceSection() : null;
        }
    }

}
