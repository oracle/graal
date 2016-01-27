/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.instrumentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
 * @see Instrumenter#attachFactory(SourceSectionFilter, EventNodeFactory)
 * @see Instrumenter#attachListener(SourceSectionFilter, EventListener)
 */
public final class SourceSectionFilter {

    private final EventFilterExpression[] nodeExpressions;
    private final EventFilterExpression[] rootNodeExpressions;

    private SourceSectionFilter(EventFilterExpression[] rootNodeExpressions, EventFilterExpression[] nodeExpressions) {
        this.rootNodeExpressions = rootNodeExpressions;
        this.nodeExpressions = nodeExpressions;
    }

    /**
     * Creates a new {@link SourceSectionFilter} expression using a {@link Builder builder} pattern.
     * Individual builder statements are interpreted as conjunctions (AND) while multiple parameters
     * for individual filter expressions are treated as disjunctions (OR). To create the final
     * filter finalize the expression using {@link Builder#build()}.
     *
     * @see Builder#sourceIs(Source...)
     * @see Builder#mimeTypeIs(String...)
     * @see Builder#tagIs(String...)
     * @see Builder#tagIsNot(String...)
     * @see Builder#sourceSectionEquals(SourceSection...)
     * @see Builder#indexIn(int, int)
     * @see Builder#lineIn(int, int)
     * @see Builder#lineIs(int)
     * @see Builder#build()
     *
     * @return a new builder to create
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    public static final class Builder {

        private List<EventFilterExpression> nodeExpressions = new ArrayList<>();
        private List<EventFilterExpression> rootNodeExpressions = new ArrayList<>();

        /**
         * Add a filter for all source sections that reference one of the given sources.
         */
        public Builder sourceIs(Source... source) {
            verifyNotNull(source);
            rootNodeExpressions.add(new EventFilterExpression.SourceIs(source));
            return this;
        }

        /**
         * Add a filter for all source sections that declare one of the given mime-types.
         */
        public Builder mimeTypeIs(String... mimeTypes) {
            verifyNotNull(mimeTypes);
            rootNodeExpressions.add(new EventFilterExpression.MimeTypeIs(mimeTypes));
            return this;
        }

        /**
         * Add a filter for all source sections that are tagged with one of the given String tags.
         */
        public Builder tagIs(String... tags) {
            verifyNotNull(tags);
            nodeExpressions.add(new EventFilterExpression.TagIs(tags));
            return this;
        }

        /**
         * Add a filter for all sources sections that declare not one of the given String tags.
         */
        public Builder tagIsNot(String... tags) {
            verifyNotNull(tags);
            nodeExpressions.add(new EventFilterExpression.TagIsNot(tags));
            return this;
        }

        /**
         * Add a filter for all sources sections that equal one of the given source sections.
         */
        public Builder sourceSectionEquals(SourceSection... section) {
            verifyNotNull(section);
            EventFilterExpression expression = new EventFilterExpression.SourceSectionEquals(section);
            rootNodeExpressions.add(expression);
            nodeExpressions.add(expression);
            return this;
        }

        /**
         * Add a filter for all sources sections where the index is inside a startIndex (inclusive)
         * plus a given length (exclusive).
         */
        public Builder indexIn(int startIndex, int length) {
            if (startIndex < 0) {
                throw new IllegalArgumentException(String.format("The argument startIndex must be positive but is %s.", startIndex));
            } else if (length < 0) {
                throw new IllegalArgumentException(String.format("The argument length must be positive but is %s.", length));
            }

            EventFilterExpression.IndexIn e = new EventFilterExpression.IndexIn(startIndex, length);
            rootNodeExpressions.add(e);
            nodeExpressions.add(e);
            return this;
        }

        /**
         * Add a filter for all sources sections where the line is inside a startLine (first index
         * inclusive) plus a given length (last index exclusive).
         */
        public Builder lineIn(int startLine, int length) {
            if (startLine < 1) {
                throw new IllegalArgumentException(String.format("The argument startLine must >= 1 but is %s.", startLine));
            } else if (length < 0) {
                throw new IllegalArgumentException(String.format("The argument length must be positive but is %s.", length));
            }

            EventFilterExpression.LineIn e = new EventFilterExpression.LineIn(startLine, length);
            rootNodeExpressions.add(e);
            nodeExpressions.add(e);
            return this;
        }

        /**
         * Add a filter for all sources sections where the line is exactly the given line.
         */
        public Builder lineIs(int line) {
            return lineIn(line, 1);
        }

        /**
         * Finalizes and constructs the {@link SourceSectionFilter} instance.
         */
        public SourceSectionFilter build() {
            Collections.sort(rootNodeExpressions);
            Collections.sort(nodeExpressions);
            return new SourceSectionFilter(rootNodeExpressions.toArray(new EventFilterExpression[rootNodeExpressions.size()]),
                            nodeExpressions.toArray(new EventFilterExpression[nodeExpressions.size()]));
        }

        private static void verifyNotNull(Object[] values) {
            if (values == null) {
                throw new IllegalArgumentException("Given arguments must not be null.");
            }
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null) {
                    throw new IllegalArgumentException("None of the given argument values must be null.");
                }
            }
        }

    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("SourceSectionFilter[");
        String sep = "";
        for (EventFilterExpression expression : rootNodeExpressions) {
            b.append(sep);
            sep = " and ";
            b.append(expression.toString());
        }

        for (EventFilterExpression expression : nodeExpressions) {
            b.append(sep);
            sep = " and ";
            b.append(expression.toString());
        }
        b.append("]");

        return b.toString();
    }

    // implementation

    boolean isInstrumentedRoot(SourceSection rootSourceSection) {
        for (EventFilterExpression exp : rootNodeExpressions) {
            if (!exp.isRootIncluded(rootSourceSection)) {
                return false;
            }
        }
        return true;
    }

    boolean isInstrumentedNode(SourceSection sourceSection) {
        for (EventFilterExpression exp : nodeExpressions) {
            if (!exp.isIncluded(sourceSection)) {
                return false;
            }
        }
        return true;
    }

    boolean isInstrumented(SourceSection sourceSection) {
        return isInstrumentedRoot(sourceSection) && isInstrumentedNode(sourceSection);
    }

    private abstract static class EventFilterExpression implements Comparable<EventFilterExpression> {

        protected abstract int getOrder();

        @SuppressWarnings("unused")
        boolean isIncluded(SourceSection sourceSection) {
            return true;
        }

        @SuppressWarnings("unused")
        boolean isRootIncluded(SourceSection rootSection) {
            return true;
        }

        public final int compareTo(EventFilterExpression o) {
            return o.getOrder() - getOrder();
        }

        private static final class SourceIs extends EventFilterExpression {

            private final Source[] sources;

            SourceIs(Source... source) {
                this.sources = source;
            }

            @Override
            boolean isRootIncluded(SourceSection s) {
                if (s == null) {
                    return false;
                }
                Source src = s.getSource();
                for (Source otherSource : sources) {
                    if (src == otherSource) {
                        return true;
                    }
                }
                return false;
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
            boolean isRootIncluded(SourceSection source) {
                if (source == null) {
                    return false;
                }
                String mimeType = source.getSource().getMimeType();
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
            protected int getOrder() {
                return 2;
            }

            @Override
            public String toString() {
                return String.format("mime-type is one-of %s", Arrays.toString(mimeTypes));
            }
        }

        private static String[] checkAndInternTags(String[] tags) {
            for (int i = 0; i < tags.length; i++) {
                String tag = tags[i];
                if (tag == null) {
                    throw new IllegalArgumentException("Tags must not be null.");
                }
                // ensure interned
                tags[i] = tag.intern();
            }
            return tags;
        }

        private static final class TagIs extends EventFilterExpression {

            private final String[] tags;

            TagIs(String... tags) {
                this.tags = checkAndInternTags(tags);
            }

            @Override
            @SuppressFBWarnings("ES_COMPARING_STRINGS_WITH_EQ")
            boolean isIncluded(SourceSection sourceSection) {
                String[] filterTags = this.tags;
                String[] sectionTags = sourceSection.getTags();
                for (int i = 0; i < filterTags.length; i++) {
                    String tag = filterTags[i];
                    for (int j = 0; j < sectionTags.length; j++) {
                        if (tag == sectionTags[j]) {
                            return true;
                        }
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

        private static final class TagIsNot extends EventFilterExpression {

            private final String[] tags;

            TagIsNot(String... tags) {
                this.tags = tags;
            }

            @Override
            @SuppressFBWarnings("ES_COMPARING_STRINGS_WITH_EQ")
            boolean isIncluded(SourceSection sourceSection) {
                String[] filterTags = this.tags;
                String[] sectionTags = sourceSection.getTags();
                for (int i = 0; i < filterTags.length; i++) {
                    String tag = filterTags[i];
                    for (int j = 0; j < sectionTags.length; j++) {
                        if (tag == sectionTags[j]) {
                            return false;
                        }
                    }
                }
                return true;
            }

            @Override
            protected int getOrder() {
                return 5;
            }

            @Override
            public String toString() {
                return String.format("tag is not one of %s", Arrays.toString(tags));
            }
        }

        private static final class SourceSectionEquals extends EventFilterExpression {

            private final SourceSection[] sourceSections;

            SourceSectionEquals(SourceSection... sourceSection) {
                this.sourceSections = sourceSection;
            }

            @Override
            boolean isIncluded(SourceSection s) {
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
            boolean isRootIncluded(SourceSection rootSection) {
                if (rootSection == null) {
                    return true;
                }
                Source rootSource = rootSection.getSource();
                if (rootSource != null) {
                    for (SourceSection compareSection : sourceSections) {
                        if (rootSource.equals(compareSection.getSource())) {
                            return true;
                        }
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

            private final int start;
            private final int end;

            IndexIn(int startIndex, int length) {
                this.start = startIndex;
                this.end = startIndex + length;
            }

            @Override
            boolean isRootIncluded(SourceSection rootSourceSection) {
                if (rootSourceSection == null) {
                    return true;
                }
                return isIncluded(rootSourceSection);
            }

            @Override
            boolean isIncluded(SourceSection sourceSection) {
                if (sourceSection == null) {
                    return false;
                }
                int otherStart = sourceSection.getCharIndex();
                int otherEnd = otherStart + sourceSection.getCharLength();
                return start <= otherEnd && otherStart < end;
            }

            @Override
            protected int getOrder() {
                return 8;
            }

            @Override
            public String toString() {
                return String.format("index between %s-%s", start, end);
            }
        }

        private static final class LineIn extends EventFilterExpression {

            private final int start;
            private final int end;

            LineIn(int startLine, int length) {
                this.start = startLine;
                this.end = start + length;
            }

            @Override
            boolean isRootIncluded(SourceSection rootSourceSection) {
                if (rootSourceSection == null) {
                    return true;
                }
                return isIncluded(rootSourceSection);
            }

            @Override
            boolean isIncluded(SourceSection sourceSection) {
                if (sourceSection == null) {
                    return false;
                }
                int otherStart = sourceSection.getStartLine();
                int otherEnd;
                if (sourceSection.getSource() == null) {
                    otherEnd = otherStart;
                } else {
                    otherEnd = sourceSection.getEndLine();
                }
                return start <= otherEnd && otherStart < end;
            }

            @Override
            protected int getOrder() {
                return 9;
            }

            @Override
            public String toString() {
                return String.format("index between %s-%s", start, end);
            }
        }

    }

}
