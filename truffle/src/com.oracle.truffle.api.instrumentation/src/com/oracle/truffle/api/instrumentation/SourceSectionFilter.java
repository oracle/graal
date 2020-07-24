/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A source section filter represents an expression for a subset of tagged source sections that are
 * used in an Truffle interpreter.
 * <p>
 * Start building event filters by calling {@link SourceSectionFilter#newBuilder()} and complete
 * them by calling {@link Builder#build()}.
 *
 * @see SourceSectionFilter#newBuilder()
 * @see Instrumenter#attachExecutionEventFactory(SourceSectionFilter, ExecutionEventNodeFactory)
 * @see Instrumenter#attachExecutionEventListener(SourceSectionFilter, ExecutionEventListener)
 * @since 0.12
 */
public final class SourceSectionFilter {

    /**
     * A filter that matches everything.
     *
     * @since 0.18
     */
    public static final SourceSectionFilter ANY = newBuilder().build();

    private final EventFilterExpression[] expressions;

    private SourceSectionFilter(EventFilterExpression[] expressions) {
        this.expressions = expressions;
    }

    /**
     * Creates a new {@link SourceSectionFilter} expression using a {@link Builder builder} pattern.
     * Individual builder statements are interpreted as conjunctions (AND) while multiple parameters
     * for individual filter expressions are treated as disjunctions (OR). To create the final
     * filter finalize the expression using {@link Builder#build()}.
     *
     * @see Builder#sourceIs(Source...)
     * @see Builder#mimeTypeIs(String...)
     * @see Builder#tagIs(Class...)
     * @see Builder#tagIsNot(Class...)
     * @see Builder#sourceSectionEquals(SourceSection...)
     * @see Builder#indexIn(int, int)
     * @see Builder#lineIn(int, int)
     * @see Builder#lineIs(int)
     * @see Builder#rootNameIs(Predicate)
     * @see Builder#build()
     *
     * @return a new builder to create new {@link SourceSectionFilter} instances
     * @since 0.12
     */
    public static Builder newBuilder() {
        return new SourceSectionFilter(null).new Builder();
    }

    /**
     * @return the filter expressions in a human readable form for debugging.
     * @since 0.12
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("SourceSectionFilter[");
        String sep = "";
        for (EventFilterExpression expression : expressions) {
            b.append(sep);
            b.append(expression.toString());
            sep = " and ";
        }
        b.append("]");
        return b.toString();
    }

    /**
     * Checks if the filter includes the given node, i.e. do the properties of the node's source
     * section meet the conditions set by the filter.
     *
     * @param node The node to check.
     * @return True of the filter includes the node, false otherwise.
     * @since 19.0.
     */
    public boolean includes(Node node) {
        if (!InstrumentationHandler.isInstrumentableNode(node)) {
            return false;
        }
        Set<Class<?>> tags = getProvidedTags(node);
        for (EventFilterExpression exp : expressions) {
            if (!exp.isIncluded(tags, node, node.getSourceSection())) {
                return false;
            }
        }
        return true;
    }

    private static Set<Class<?>> getProvidedTags(Node node) {
        Objects.requireNonNull(node);
        RootNode root = node.getRootNode();
        if (root == null) {
            return Collections.emptySet();
        }
        Object polyglotEngine = InstrumentAccessor.nodesAccess().getPolyglotEngine(root);
        if (polyglotEngine == null) {
            return Collections.emptySet();
        }
        InstrumentationHandler handler = (InstrumentationHandler) InstrumentAccessor.engineAccess().getInstrumentationHandler(polyglotEngine);
        return handler.getProvidedTags(node);
    }

    /**
     * Returns which tags are required to be materialized in order for this filter to be correct.
     * Returns <code>null</code> to indicate that all provided tags are required.
     */
    Set<Class<?>> getLimitedTags() {
        Set<Class<?>> requiredTags = null;
        for (EventFilterExpression expression : expressions) {
            if (expression instanceof EventFilterExpression.TagIs) {
                if (requiredTags == null) {
                    requiredTags = new HashSet<>();
                }
                expression.collectReferencedTags(requiredTags);
            }
        }
        return requiredTags;
    }

    // implementation
    Set<Class<?>> getReferencedTags() {
        Set<Class<?>> usedTags = new HashSet<>();
        for (EventFilterExpression expression : expressions) {
            expression.collectReferencedTags(usedTags);
        }
        return usedTags;
    }

    boolean isSourceOnly() {
        for (EventFilterExpression eventFilterExpression : expressions) {
            if (!eventFilterExpression.isSourceOnly()) {
                return false;
            }
        }
        return true;
    }

    boolean isInstrumentedRoot(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
        for (EventFilterExpression exp : expressions) {
            if (!exp.isRootIncluded(providedTags, rootSourceSection, rootNode, rootNodeBits)) {
                return false;
            }
        }
        return true;
    }

    boolean isInstrumentedNode(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
        assert InstrumentationHandler.isInstrumentableNode(instrumentedNode);
        for (EventFilterExpression exp : expressions) {
            if (!exp.isIncluded(providedTags, instrumentedNode, sourceSection)) {
                return false;
            }
        }
        return true;
    }

    boolean isInstrumentedSource(Source source) {
        if (source == null) {
            return false;
        }
        for (EventFilterExpression exp : expressions) {
            assert exp.isSourceOnly() : exp.toString();
            if (!exp.isSourceIncluded(source)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Configure your own {@link SourceSectionFilter} before creating its instance. Specify various
     * parameters by calling individual {@link Builder} methods. When done, call {@link #build()}.
     *
     * @since 0.12
     */
    public final class Builder {
        private List<EventFilterExpression> expressions = new ArrayList<>();
        private boolean includeInternal = true;

        private Builder() {
        }

        /**
         * Add a source filter.
         *
         * @since 0.32
         */
        public Builder sourceFilter(SourceFilter sourceFilter) {
            expressions.addAll(Arrays.asList(sourceFilter.expressions));
            return this;
        }

        /**
         * Add a filter for all source sections that reference one of the given sources.
         *
         * @since 0.12
         */
        public Builder sourceIs(Source... source) {
            verifyNotNull(source);
            expressions.add(new EventFilterExpression.SourceIs(source));
            return this;
        }

        /**
         * Adds custom predicate to filter inclusion of {@link Source sources}. The predicate must
         * always return the same result for a source instance otherwise the behavior is undefined.
         * The predicate should be able run on multiple threads at the same time.
         *
         * @param predicate a test for inclusion
         * @since 0.17
         */
        public Builder sourceIs(SourcePredicate predicate) {
            if (predicate == null) {
                throw new IllegalArgumentException("SourcePredicate must not be null.");
            }
            expressions.add(new EventFilterExpression.SourceFilterIs(predicate));
            return this;
        }

        /**
         * Adds custom predicate to filter inclusion for {@link RootNode#getName() root names}. The
         * root name might be <code>null</code> if not provided by the guest language. If the
         * language returns a changing value it is unspecified which root name is going to be
         * matched. The predicate must always return the same result for a {@link String} instance
         * otherwise the behavior is undefined. The predicate should be able run on multiple threads
         * at the same time.
         *
         * @param predicate a test for inclusion
         * @since 0.27
         */
        public Builder rootNameIs(Predicate<String> predicate) {
            if (predicate == null) {
                throw new IllegalArgumentException("Predicate must not be null.");
            }
            expressions.add(new EventFilterExpression.RootNameIs(predicate));
            return this;
        }

        /**
         * Add a filter for all source sections that declare one of the given mime-types. Mime-types
         * which are compared must match exactly one of the mime-types specified by the target guest
         * language.
         *
         * @param mimeTypes matches one of the given mime types
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder mimeTypeIs(String... mimeTypes) {
            verifyNotNull(mimeTypes);
            expressions.add(new EventFilterExpression.MimeTypeIs(mimeTypes));
            return this;
        }

        /**
         * Add a filter for all source sections that are tagged with one of the given tags.
         *
         * @param tags matches one of the given tags
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder tagIs(Class<?>... tags) {
            verifyNotNull(tags);
            expressions.add(new EventFilterExpression.TagIs(tags));
            return this;
        }

        /**
         * Add a filter for all source sections that are not tagged with one of the given tags.
         *
         * @param tags matches not one of the given tags
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder tagIsNot(Class<?>... tags) {
            verifyNotNull(tags);
            expressions.add(new Not(new EventFilterExpression.TagIs(tags)));
            return this;
        }

        /**
         * Add a filter for all source sections that equal one of the given source sections.
         *
         * @param section matches one of the given source sections
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder sourceSectionEquals(SourceSection... section) {
            verifyNotNull(section);
            expressions.add(new EventFilterExpression.SourceSectionEquals(section));
            return this;
        }

        /**
         * Add a filter for all root source sections that equal one of the given source sections.
         * All descendant source sections of a matching root source section are included in the
         * filter. This can mean in the dynamic language domain that all nodes of a function for
         * which the root source section matches the given source section is instrumented but its
         * inner functions and its nodes are not instrumented.
         *
         * @param section matches one of the given root source sections
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder rootSourceSectionEquals(SourceSection... section) {
            verifyNotNull(section);
            expressions.add(new EventFilterExpression.RootSourceSectionEquals(section));
            return this;
        }

        /**
         * Add a filter for all source sections which indices are not contained in one of the given
         * index ranges.
         *
         * @param ranges matches indices that are not contained in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder indexNotIn(IndexRange... ranges) {
            verifyNotNull(ranges);
            expressions.add(new Not(new EventFilterExpression.IndexIn(ranges)));
            return this;
        }

        /**
         * Add a filter for all source sections which indices are contained in one of the given
         * index ranges.
         *
         * @param ranges matches indices that are contained in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder indexIn(IndexRange... ranges) {
            verifyNotNull(ranges);
            expressions.add(new EventFilterExpression.IndexIn(ranges));
            return this;
        }

        /**
         * Add a filter for all source sections where the index is inside a startIndex (inclusive)
         * plus a given length (exclusive).
         *
         * @param startIndex the start index (inclusive)
         * @param length the number of matched characters
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder indexIn(int startIndex, int length) {
            return indexIn(IndexRange.byLength(startIndex, length));
        }

        /**
         * Add a filter for all source sections where lines are contained in one of the given index
         * ranges. Line indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches lines that are contained in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder lineIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new EventFilterExpression.LineIn(ranges));
            return this;
        }

        /**
         * Add a filter for all source sections where lines are not contained in one of the given
         * index ranges. Line indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches lines that are not contained in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder lineNotIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new Not(new EventFilterExpression.LineIn(ranges)));
            return this;
        }

        /**
         * Add a filter for all source sections where the line is inside a startLine (first index
         * inclusive) plus a given length (last index exclusive).
         *
         * @param startLine the start line (inclusive)
         * @param length the number of matched lines
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder lineIn(int startLine, int length) {
            if (startLine < 1) {
                throw new IllegalArgumentException(String.format("Start line indices must be >= 1 but were %s.", startLine));
            }
            return lineIn(IndexRange.byLength(startLine, length));
        }

        /**
         * Add a filter for all source sections where the line starts in one of the given index
         * ranges. Line indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches lines that start in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder lineStartsIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new EventFilterExpression.LineStartsIn(ranges));
            return this;
        }

        /**
         * Add a filter for all source sections where the line ends in one of the given index
         * ranges. Line indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches lines that end in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder lineEndsIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new EventFilterExpression.LineEndsIn(ranges));
            return this;
        }

        /**
         * Add a filter for all source sections where the columns are contained in one of the given
         * index ranges. Column indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches columns that are contained in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.33
         */
        public Builder columnIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new EventFilterExpression.ColumnIn(ranges));
            return this;
        }

        /**
         * Add a filter for all source sections where columns are not contained in one of the given
         * index ranges. Column indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches columns that are not contained in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.33
         */
        public Builder columnNotIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new Not(new EventFilterExpression.ColumnIn(ranges)));
            return this;
        }

        /**
         * Add a filter for all source sections where the column is inside a startColumn (first
         * index inclusive) plus a given length (last index exclusive).
         *
         * @param startColumn the start column (inclusive)
         * @param length the number of matched columns
         * @return the builder to chain calls
         * @since 0.33
         */
        public Builder columnIn(int startColumn, int length) {
            if (startColumn < 1) {
                throw new IllegalArgumentException(String.format("Start line indices must be >= 1 but were %s.", startColumn));
            }
            return columnIn(IndexRange.byLength(startColumn, length));
        }

        /**
         * Add a filter for all source sections where the column starts in one of the given index
         * ranges. Column indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches columns that start in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.33
         */
        public Builder columnStartsIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new EventFilterExpression.ColumnStartsIn(ranges));
            return this;
        }

        /**
         * Add a filter for all sources sections where the column ends in one of the given index
         * ranges. Column indices must be greater than or equal to <code>1</code>.
         *
         * @param ranges matches columns that end in one of the given index ranges
         * @return the builder to chain calls
         * @since 0.33
         */
        public Builder columnEndsIn(IndexRange... ranges) {
            verifyLineIndices(ranges);
            expressions.add(new EventFilterExpression.ColumnEndsIn(ranges));
            return this;
        }

        private void verifyLineIndices(IndexRange... ranges) {
            verifyNotNull(ranges);
            for (IndexRange indexRange : ranges) {
                if (indexRange.startIndex < 1) {
                    throw new IllegalArgumentException(String.format("Start line/column must be >= 1 but was %s.", indexRange.startIndex));
                }
            }
        }

        /**
         * Add a filter for all sources sections where the line is exactly the given line. Line
         * indices must be greater than or equal to <code>1</code>. *
         *
         * @param line the line to be matched
         * @return the builder to chain calls
         * @since 0.12
         */
        public Builder lineIs(int line) {
            return lineIn(line, 1);
        }

        /**
         * Add a filter that includes or excludes {@link RootNode#isInternal() internal root nodes}.
         * By default, internal roots are included, call with <code>false</code> to exclude internal
         * code from instrumentation.
         *
         * @return the builder to chain calls
         * @since 0.29
         */
        public Builder includeInternal(boolean internal) {
            this.includeInternal = internal;
            return this;
        }

        /**
         * Adds all the filters defined in the given {@link SourceSectionFilter filter}.
         *
         * @param filter an existing filter to be included
         * @return the builder to chain calls
         * @since 0.30
         */
        public Builder and(SourceSectionFilter filter) {
            for (EventFilterExpression e : filter.expressions) {
                expressions.add(e);
            }
            return this;
        }

        /**
         * Finalizes and constructs the {@link SourceSectionFilter} instance.
         *
         * @return the built filter expression
         * @since 0.12
         */
        public SourceSectionFilter build() {
            if (!includeInternal) {
                expressions.add(new EventFilterExpression.IgnoreInternal());
            }
            Collections.sort(expressions);
            return new SourceSectionFilter(expressions.toArray(new EventFilterExpression[0]));
        }

    }

    static void verifyNotNull(Object[] values) {
        if (values == null) {
            throw new IllegalArgumentException("Given arguments must not be null.");
        }
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null) {
                throw new IllegalArgumentException("None of the given argument values must be null.");
            }
        }
    }

    /**
     * Represents a predicate for source objects.
     *
     * @since 0.17
     */
    public interface SourcePredicate extends Predicate<Source> {

        /**
         * Returns <code>true</code> if the given source should be tested positive and
         * <code>false</code> if the sources should be filtered.
         *
         * @param source the source object to filter
         * @since 0.17
         */
        boolean test(Source source);
    }

    /**
     * Represents a range between two indices within a {@link SourceSectionFilter source section
     * filter}. Instances are immutable.
     *
     * @see SourceSectionFilter
     * @see #between(int, int)
     * @see #byLength(int, int)
     * @since 0.12
     */
    public static final class IndexRange {

        final int startIndex;
        final int endIndex;

        IndexRange(int startIndex, int endIndex) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
        }

        /**
         * Constructs a new index range between one a first index inclusive and a second index
         * exclusive. Parameters must comply <code>startIndex >= 0</code> and
         * <code>startIndex <= endIndex</code>.
         *
         * @param startIndex the start index (inclusive)
         * @param endIndex the end index (exclusive)
         * @return a new index range
         * @throws IllegalArgumentException if parameter invariants are violated
         * @since 0.12
         */
        public static IndexRange between(int startIndex, int endIndex) {
            if (startIndex < 0) {
                throw new IllegalArgumentException(String.format("The argument startIndex must be positive but is %s.", startIndex));
            } else if (endIndex < startIndex) {
                throw new IllegalArgumentException(String.format("Invalid range %s:%s.", startIndex, endIndex));
            }
            return new IndexRange(startIndex, endIndex);
        }

        /**
         * Constructs a new index range with a given first index inclusive and a given length.
         * Parameters must comply <code>startIndex >= 0</code> and <code>length >= 0</code>.
         *
         * @param startIndex the start index (inclusive)
         * @param length the length of the range
         * @return a new index range
         * @throws IllegalArgumentException if parameter invariants are violated
         * @since 0.12
         */
        public static IndexRange byLength(int startIndex, int length) {
            if (length < 0) {
                throw new IllegalArgumentException(String.format("The argument length must be positive but is %s.", length));
            } else if (startIndex < 0) {
                throw new IllegalArgumentException(String.format("The argument startIndex must be positive but is %s.", startIndex));
            }
            return new IndexRange(startIndex, startIndex + length);
        }

        boolean contains(int otherStartIndex, int otherEndIndex) {
            return startIndex <= otherEndIndex && otherStartIndex < endIndex;
        }

        /**
         * @return a human readable version of the index range
         * @since 0.12
         */
        @Override
        public String toString() {
            return "[" + startIndex + "-" + endIndex + "]";
        }

    }

    abstract static class EventFilterExpression implements Comparable<EventFilterExpression> {

        protected abstract int getOrder();

        void collectReferencedTags(@SuppressWarnings("unused") Set<Class<?>> collectTags) {
            // default implementation does nothing
        }

        boolean isSourceIncluded(@SuppressWarnings("unused") Source source) {
            return false;
        }

        abstract boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection);

        abstract boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits);

        boolean isSourceOnly() {
            return false;
        }

        @Override
        public final int compareTo(EventFilterExpression o) {
            return getOrder() - o.getOrder();
        }

        static void appendRanges(StringBuilder builder, IndexRange[] ranges) {
            String sep = "";
            for (IndexRange range : ranges) {
                builder.append(sep).append(range);
                sep = " or ";
            }
        }

        static final class SourceFilterIs extends EventFilterExpression {

            private final Predicate<Source> predicate;

            SourceFilterIs(Predicate<Source> predicate) {
                this.predicate = predicate;
            }

            @Override
            boolean isSourceOnly() {
                return true;
            }

            @Override
            boolean isSourceIncluded(Source src) {
                if (src == null) {
                    return false;
                }
                return predicate.test(src);
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSameSource(rootNodeBits) && rootSourceSection != null) {
                    return isSourceIncluded(rootSourceSection.getSource());
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                if (sourceSection == null) {
                    return false;
                }
                return isSourceIncluded(sourceSection.getSource());
            }

            @Override
            protected int getOrder() {
                return 1;
            }

            @Override
            public String toString() {
                return String.format("source is included by custom filter %s", predicate.toString());
            }
        }

        private static final class RootNameIs extends EventFilterExpression {

            private final Predicate<String> predicate;

            RootNameIs(Predicate<String> predicate) {
                this.predicate = predicate;
            }

            @Override
            boolean isSourceOnly() {
                return false;
            }

            @Override
            boolean isSourceIncluded(Source src) {
                return true;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
                return predicate.test(rootNode.getName());
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                return true;
            }

            @Override
            protected int getOrder() {
                return 3;
            }

            @Override
            public String toString() {
                return String.format("root name is included by custom filter %s", predicate.toString());
            }
        }

        static final class SourceIs extends EventFilterExpression {

            private final Source[] sources;

            SourceIs(Source... source) {
                this.sources = source;
            }

            @Override
            boolean isSourceOnly() {
                return true;
            }

            @Override
            boolean isSourceIncluded(Source src) {
                for (Source otherSource : sources) {
                    if (src.equals(otherSource)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSameSource(rootNodeBits) && rootSourceSection != null) {
                    return isSourceIncluded(rootSourceSection.getSource());
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                if (sourceSection == null) {
                    return false;
                }
                return isSourceIncluded(sourceSection.getSource());
            }

            @Override
            protected int getOrder() {
                return 1;
            }

            @Override
            public String toString() {
                return String.format("source is %s", Arrays.toString(sources));
            }
        }

        private static final class MimeTypeIs extends EventFilterExpression {

            private final String[] mimeTypes;

            MimeTypeIs(String... mimeTypes) {
                this.mimeTypes = mimeTypes;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSameSource(rootNodeBits) && rootSourceSection != null) {
                    return isSourceIncluded(rootSourceSection.getSource());
                }
                return true;
            }

            @Override
            boolean isSourceOnly() {
                return true;
            }

            @Override
            boolean isSourceIncluded(Source source) {
                String mimeType = source.getMimeType();
                if (mimeType != null) {
                    for (String otherMimeType : mimeTypes) {
                        if (otherMimeType.equals(mimeType)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                if (sourceSection == null) {
                    return false;
                }
                return isSourceIncluded(sourceSection.getSource());
            }

            @Override
            protected int getOrder() {
                return 2;
            }

            @Override
            public String toString() {
                return String.format("mime-type is one-of %s", Arrays.toString(mimeTypes));
            }
        }

        private static Class<?>[] checkTags(Class<?>[] tags) {
            for (int i = 0; i < tags.length; i++) {
                if (tags[i] == null) {
                    throw new IllegalArgumentException("Tags must not be null.");
                }
            }
            return tags;
        }

        private static final class TagIs extends EventFilterExpression {

            private final Class<?>[] tags;

            TagIs(Class<?>... tags) {
                this.tags = checkTags(tags);
            }

            @Override
            void collectReferencedTags(Set<Class<?>> collectTags) {
                for (Class<?> tag : tags) {
                    collectTags.add(tag);
                }
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                Class<?>[] filterTags = this.tags;
                for (int i = 0; i < filterTags.length; i++) {
                    Class<?> tag = filterTags[i];
                    if (InstrumentationHandler.hasTagImpl(providedTags, instrumentedNode, tag)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                for (Class<?> tag : tags) {
                    if (providedTags.contains(tag)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 4;
            }

            @Override
            public String toString() {
                return String.format("tag is one of %s", Arrays.toString(tags));
            }
        }

        private static final class SourceSectionEquals extends EventFilterExpression {

            private final SourceSection[] sourceSections;

            SourceSectionEquals(SourceSection... sourceSection) {
                this.sourceSections = sourceSection;
                // clear tags
                for (int i = 0; i < sourceSection.length; i++) {
                    sourceSections[i] = sourceSection[i];
                }
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection s) {
                if (s == null) {
                    return false;
                }
                for (SourceSection compareSection : sourceSections) {
                    if (s.equals(compareSection)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (rootSourceSection == null) {
                    return true;
                }
                boolean rootIncluded = canContainSource(rootSourceSection, rootNodeBits);
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootIncluded) {
                    int rootStart = rootSourceSection.getCharIndex();
                    int rootEnd = rootSourceSection.getCharEndIndex();
                    for (SourceSection compareSection : sourceSections) {
                        int compareStart = compareSection.getCharIndex();
                        int compareEnd = compareSection.getCharEndIndex();
                        if (compareStart >= rootStart && compareEnd <= rootEnd) {
                            return true;
                        }
                    }
                    /*
                     * If the source section is not contained within the root and the source
                     * sections are hierarchical the source section cannot be contained in this root
                     * node.
                     */
                    return false;
                }
                return rootIncluded;
            }

            private boolean canContainSource(SourceSection rootSourceSection, int rootNodeBits) {
                if (RootNodeBits.isSameSource(rootNodeBits)) {
                    Source rootSource = rootSourceSection.getSource();
                    for (SourceSection compareSection : sourceSections) {
                        if (rootSource.equals(compareSection.getSource())) {
                            return true;
                        }
                    }
                    return false;
                } else {
                    return true;
                }
            }

            @Override
            protected int getOrder() {
                return 6;
            }

            @Override
            public String toString() {
                return String.format("source-section equals one-of %s", Arrays.toString(sourceSections));
            }

        }

        private static final class RootSourceSectionEquals extends EventFilterExpression {

            private final SourceSection[] sourceSections;

            RootSourceSectionEquals(SourceSection... sourceSection) {
                this.sourceSections = sourceSection;
                // clear tags
                for (int i = 0; i < sourceSection.length; i++) {
                    sourceSections[i] = sourceSection[i];
                }
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection s) {
                return true;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (rootSection == null) {
                    return false;
                }
                for (SourceSection compareSection : sourceSections) {
                    if (rootSection.equals(compareSection)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 6;
            }

            @Override
            public String toString() {
                return String.format("source-section equals one-of %s", Arrays.toString(sourceSections));
            }

        }

        private static final class IndexIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            IndexIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSourceSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSourceSection != null) {
                    return IndexIn.isIndexIn(rootSourceSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                return isIndexIn(sourceSection, ranges);
            }

            private static boolean isIndexIn(SourceSection sourceSection, IndexRange[] ranges) {
                if (sourceSection == null || !sourceSection.isAvailable()) {
                    return false;
                }
                int otherStart = sourceSection.getCharIndex();
                int otherEnd = otherStart + sourceSection.getCharLength();
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherStart, otherEnd)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 8;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(index-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class LineStartsIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            LineStartsIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSection != null) {
                    return LineIn.isLineIn(rootSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                if (sourceSection == null || !sourceSection.isAvailable()) {
                    return false;
                }
                int otherStart = sourceSection.getStartLine();
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherStart, otherStart)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 10;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(line-starts-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class LineEndsIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            LineEndsIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSection != null) {
                    return LineIn.isLineIn(rootSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                if (sourceSection == null || !sourceSection.isAvailable()) {
                    return false;
                }
                int otherStart = sourceSection.getStartLine();
                int otherEnd;
                if (sourceSection.getSource() == null) {
                    otherEnd = otherStart;
                } else {
                    otherEnd = sourceSection.getEndLine();
                }
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherEnd, otherEnd)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 10;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(line-ends-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class LineIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            LineIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSection != null) {
                    return LineIn.isLineIn(rootSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                return isLineIn(sourceSection, ranges);
            }

            static boolean isLineIn(SourceSection sourceSection, IndexRange[] ranges) {
                if (sourceSection == null || !sourceSection.isAvailable()) {
                    return false;
                }
                int otherStart = sourceSection.getStartLine();
                int otherEnd;
                if (sourceSection.getSource() == null) {
                    otherEnd = otherStart;
                } else {
                    otherEnd = sourceSection.getEndLine();
                }
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherStart, otherEnd)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 10;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(line-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class ColumnStartsIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            ColumnStartsIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSection != null &&
                                rootSection.getStartLine() == rootSection.getEndLine()) {
                    return ColumnIn.isColumnIn(rootSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                if (!sourceSection.isAvailable()) {
                    return false;
                }
                int otherStart = sourceSection.getStartColumn();
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherStart, otherStart)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 12;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(column-starts-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class ColumnEndsIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            ColumnEndsIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSection != null &&
                                rootSection.getStartLine() == rootSection.getEndLine()) {
                    return ColumnIn.isColumnIn(rootSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                int otherStart = sourceSection.getStartColumn();
                int otherEnd;
                if (sourceSection.getSource() == null) {
                    otherEnd = otherStart;
                } else {
                    otherEnd = sourceSection.getEndColumn();
                }
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherEnd, otherEnd)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 12;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(column-ends-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class ColumnIn extends EventFilterExpression {

            private final IndexRange[] ranges;

            ColumnIn(IndexRange[] ranges) {
                this.ranges = ranges;
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                if (RootNodeBits.isNoSourceSection(rootNodeBits)) {
                    return false;
                }
                if (RootNodeBits.isSourceSectionsHierachical(rootNodeBits) && rootSection != null &&
                                rootSection.getStartLine() == rootSection.getEndLine()) {
                    return isColumnIn(rootSection, ranges);
                }
                return true;
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
                return isColumnIn(sourceSection, ranges);
            }

            static boolean isColumnIn(SourceSection sourceSection, IndexRange[] ranges) {
                if (!sourceSection.isAvailable()) {
                    return false;
                }
                int otherStart = sourceSection.getStartColumn();
                int otherEnd;
                if (sourceSection.getSource() == null) {
                    otherEnd = otherStart;
                } else {
                    otherEnd = sourceSection.getEndColumn();
                }
                for (IndexRange indexRange : ranges) {
                    if (indexRange.contains(otherStart, otherEnd)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            protected int getOrder() {
                return 12;
            }

            @Override
            public String toString() {
                StringBuilder builder = new StringBuilder("(column-between ");
                appendRanges(builder, ranges);
                builder.append(")");
                return builder.toString();
            }
        }

        private static final class IgnoreInternal extends EventFilterExpression {

            IgnoreInternal() {
            }

            @Override
            boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection s) {
                return s == null || !s.getSource().isInternal();
            }

            @Override
            boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
                // assert that the RootNode is internal when it's Source is internal
                assert rootNode == null ||
                                rootSection == null ||
                                !rootSection.getSource().isInternal() ||
                                rootSection.getSource().isInternal() && rootNode.isInternal() : //
                "The root's source is internal, but the root node is not. Root node = " + rootNode.getClass();
                return rootNode == null || !rootNode.isInternal();
            }

            @Override
            protected int getOrder() {
                return 1;
            }

            @Override
            public String toString() {
                return "ignore internal";
            }

        }

    }

    private static final class Not extends EventFilterExpression {

        final EventFilterExpression delegate;

        Not(EventFilterExpression delegate) {
            this.delegate = delegate;
        }

        @Override
        boolean isSourceOnly() {
            return delegate.isSourceOnly();
        }

        @Override
        boolean isSourceIncluded(Source source) {
            return !delegate.isSourceIncluded(source);
        }

        @Override
        void collectReferencedTags(Set<Class<?>> collectTags) {
            delegate.collectReferencedTags(collectTags);
        }

        @Override
        boolean isRootIncluded(Set<Class<?>> providedTags, SourceSection rootSection, RootNode rootNode, int rootNodeBits) {
            return true;
        }

        @Override
        boolean isIncluded(Set<Class<?>> providedTags, Node instrumentedNode, SourceSection sourceSection) {
            return !delegate.isIncluded(providedTags, instrumentedNode, sourceSection);
        }

        @Override
        protected int getOrder() {
            return delegate.getOrder();
        }

        @Override
        public String toString() {
            return "not(" + delegate.toString() + ")";
        }

    }

}
