package de.hpi.swa.trufflelsp.server.utils;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class NearestSectionsFinder {

    public static enum NodeLocationType {
        CONTAINS,
        CONTAINS_END,
        PREVIOUS,
        NEXT
    }

    private NearestSectionsFinder() {
    }

    public static NearestNodeHolder findNearestNode(Source source, int line, int character, Env env) {
        int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
        NearestNodeHolder nearestNodeHolder = findNearestNodeOneBased(oneBasedLineNumber, character, source, env);

        // TODO(ds) wrap debug printing
        Node nearestNode = nearestNodeHolder.getNearestNode();
        System.out.println("nearestNode: " +
                        (nearestNode != null ? nearestNode.getClass().getSimpleName() : "--NULL--") + "\t-" + nearestNodeHolder.getLocationType() + "-\t" +
                        (nearestNode != null ? nearestNode.getSourceSection() : ""));

        return nearestNodeHolder;
    }

    protected static NearestNodeHolder findNearestNodeOneBased(int oneBasedLineNumber, int column, Source source, Env env) {
        NearestSections nearestSections = getNearestSections(source, env, oneBasedLineNumber, column);
        SourceSection containsSection = nearestSections.getContainsSourceSection();

        Node nearestNode;
        NodeLocationType locationType;
        if (containsSection == null) {
            // We are not in a local scope, so only top scope objects possible
            nearestNode = null;
            locationType = null;
        } else if (isEndOfSectionMatchingCaretPosition(oneBasedLineNumber, column, containsSection)) {
            // Our caret is directly behind the containing section, so we can simply use that one to
            // find local scope objects
            nearestNode = (Node) nearestSections.getContainsNode();
            locationType = NodeLocationType.CONTAINS_END;
        } else if (nodeIsInChildHirarchyOf((Node) nearestSections.getNextNode(), (Node) nearestSections.getContainsNode())) {
            // Great, the nextNode is a (indirect) sibling of our containing node, so it is in the
            // same scope as we are and we can use it to get local scope objects
            nearestNode = (Node) nearestSections.getNextNode();
            locationType = NodeLocationType.NEXT;
        } else if (nodeIsInChildHirarchyOf((Node) nearestSections.getPreviousNode(), (Node) nearestSections.getContainsNode())) {
            // In this case we want call findLocalScopes() with BEHIND-flag, i.e. give me the local
            // scope objects which are valid behind that node
            nearestNode = (Node) nearestSections.getPreviousNode();
            locationType = NodeLocationType.PREVIOUS;
        } else {
            // No next or previous node is in the same scope like us, so we can only take our
            // containing node to get local scope objects
            nearestNode = (Node) nearestSections.getContainsNode();
            locationType = NodeLocationType.CONTAINS;
        }

        return new NearestNodeHolder(nearestNode, locationType);
    }

    private static boolean isEndOfSectionMatchingCaretPosition(int line, int character, SourceSection section) {
        return section.getEndLine() == line && section.getEndColumn() == character;
    }

    private static boolean nodeIsInChildHirarchyOf(Node node, Node potentialParent) {
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

    private static int fixColumn(Source source, int column, int boundLine) {
        int boundColumn = column;
        int maxColumn = source.getLineLength(boundLine) + 1;
        if (boundColumn > maxColumn) {
            boundColumn = maxColumn;
        }
        return boundColumn;
    }

    private static int fixLine(Source source, int line) {
        int boundLine = line;
        int maxLine = source.getLineCount();
        if (boundLine > maxLine) {
            boundLine = maxLine;
        }
        return boundLine;
    }

    public static NearestSections getNearestSections(Source source, TruffleInstrument.Env env, int line, int column) {
        int boundLine = fixLine(source, line);
        int boundColumn = fixColumn(source, column, boundLine);
        return getNearestSections(source, env, convertLineAndColumnToOffset(source, boundLine, boundColumn));
    }

    protected static NearestSections getNearestSections(Source source, TruffleInstrument.Env env, int offset) {
        NearestSections sectionsCollector = new NearestSections(offset);
        // All SourceSections of the Source are loaded already when the source was parsed
        env.getInstrumenter().attachLoadSourceSectionListener(
                        SourceSectionFilter.newBuilder().sourceIs(source).build(),
                        sectionsCollector, true).dispose();
        return sectionsCollector;
    }

    public static int convertLineAndColumnToOffset(Source source, int line, int column) {
        int offset = source.getLineStartOffset(line);
        if (column > 0) {
            offset += column - 1;
        }
        return offset;
    }

    public static class NearestSections implements LoadSourceSectionListener {

        private final int offset;
        private SourceSection containsMatch;
        private InstrumentableNode containsNode;
        private SourceSection previousMatch;
        private InstrumentableNode previousNode;
        private SourceSection nextMatch;
        private InstrumentableNode nextNode;

        NearestSections(int offset) {
            this.offset = offset;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            Node eventNode = event.getNode();
            if (!(eventNode instanceof InstrumentableNode && ((InstrumentableNode) eventNode).isInstrumentable())) {
                return;
            }
            InstrumentableNode node = (InstrumentableNode) eventNode;
            SourceSection sourceSection = event.getSourceSection();

            int o1 = sourceSection.getCharIndex();
            int o2;
            if (sourceSection.getCharLength() > 0) {
                o2 = sourceSection.getCharEndIndex() - 1;
            } else {
                o2 = sourceSection.getCharIndex();
            }
            // Offset approximation
            findOffsetApproximation(node, sourceSection, o1, o2);
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

        public InstrumentableNode getContainsNode() {
            return containsNode;
        }

        public InstrumentableNode getPreviousNode() {
            return previousNode;
        }

        public InstrumentableNode getNextNode() {
            return nextNode;
        }

        public SourceSection getContainsSourceSection() {
            return containsNode instanceof Node ? ((Node) containsNode).getSourceSection() : null;
        }

        public SourceSection getPreviousSourceSection() {
            return previousNode instanceof Node ? ((Node) previousNode).getSourceSection() : null;
        }

        public SourceSection getNextSourceSection() {
            return nextNode instanceof Node ? ((Node) nextNode).getSourceSection() : null;
        }
    }

}
