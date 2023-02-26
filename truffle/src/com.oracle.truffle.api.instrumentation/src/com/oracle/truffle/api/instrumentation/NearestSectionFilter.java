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

import java.util.Arrays;
import java.util.Set;

import com.oracle.truffle.api.instrumentation.NearestNodesCollector.Position;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Add a filter for source sections that are nearest to the given source position, according to the
 * guest language control flow.
 * <p>
 * The selection of the nearest source location happens based on the provided
 * {@link SourceSectionFilter} and this nearest section filter to the {@link Instrumenter}
 * create/attach methods. First location candidates are selected according to the
 * {@link SourceSectionFilter}. Based on this selection a nearest location is found using this
 * {@link NearestSectionFilter}. If new nearer locations are loaded then the listener/factory will
 * be notified again. There is at most one nearest {@link SourceSection} at a time, therefore it is
 * possible to detect updates by remembering and comparing the source section in the
 * listener/factory.
 * <p>
 * Start building the nearest filter by calling {@link NearestSectionFilter#newBuilder(int, int)}
 * and complete it by calling {@link Builder#build()}.
 * <p>
 * A use-case is a debugger breakpoint, for instance, where the exact line or column is not always
 * precise and the location needs to be updated when new code is loaded.
 *
 * @see NearestSectionFilter#newBuilder(int, int)
 * @see Instrumenter#createLoadSourceSectionBinding(NearestSectionFilter, SourceSectionFilter,
 *      LoadSourceSectionListener, boolean)
 * @see Instrumenter#attachLoadSourceSectionListener(NearestSectionFilter, SourceSectionFilter,
 *      LoadSourceSectionListener, boolean)
 * @see Instrumenter#attachExecutionEventFactory(NearestSectionFilter, SourceSectionFilter,
 *      ExecutionEventNodeFactory)
 *
 * @since 23.0
 */
// Uses InstrumentableNode#findNearestNodeAt(int, int, Set) to provide the final nearest section.
public final class NearestSectionFilter {

    private final Position position;
    private final boolean anchorStart;
    private final Class<?>[] tags;
    private final Set<Class<? extends Tag>> tagClasses;

    private NearestSectionFilter(Position position, boolean anchorStart, Class<?>[] tags) {
        this.position = position;
        this.anchorStart = anchorStart;
        this.tags = tags;
        this.tagClasses = convertTags(tags);
    }

    /**
     * Creates a new {@link NearestSectionFilter} using a {@link Builder builder} pattern. The
     * filter will find source sections that are nearest to the given source line and column. To
     * create the final filter finalize it using {@link Builder#build()}.
     *
     * @param line the line, greater than or equal to <code>1</code>
     * @param column the column, or &lt; 1 when column is unknown
     * @return a new builder to create new {@link NearestSectionFilter} instances
     * @since 23.0
     */
    public static Builder newBuilder(int line, int column) {
        return new Builder(line, column);
    }

    /**
     * @return the filter attributes in a human readable form for debugging.
     * @since 23.0
     */
    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("NearestSectionFilter[");
        b.append("To ");
        b.append(position);
        b.append(" with anchor '");
        b.append(anchorStart ? "start" : "end");
        b.append("' and tag is one of ");
        b.append(Arrays.toString(tags));
        b.append("]");
        return b.toString();
    }

    Position getPosition() {
        return position;
    }

    boolean isAnchorStart() {
        return anchorStart;
    }

    Set<Class<?>> getReferencedTags() {
        return tags != null ? Set.of(tags) : Set.of();
    }

    Set<Class<? extends Tag>> getTagClasses() {
        return tagClasses;
    }

    @SuppressWarnings("unchecked")
    private static Set<Class<? extends Tag>> convertTags(Class<?>[] tags) {
        if (tags != null) {
            for (Class<?> tag : tags) {
                if (!Tag.class.isAssignableFrom(tag)) {
                    throw new IllegalArgumentException("Illegal tag " + tag + ", not of class " + Tag.class.getName());
                }
            }
            return (Set<Class<? extends Tag>>) (Set<?>) Set.of(tags);
        } else {
            return null;
        }
    }

    /**
     * Builder to configure {@link NearestSectionFilter} before creating its instance. Specify
     * various parameters by calling individual {@link Builder} methods. When done, call
     * {@link #build()}.
     *
     * @since 23.0
     */
    public static final class Builder {

        private final int line;
        private final int column;
        private boolean anchorStart = true;
        private Class<?>[] theTags;

        private Builder(int line, int column) {
            if (line < 1) {
                throw new IllegalArgumentException("line " + line + " < 1");
            }
            this.line = line;
            this.column = column;
        }

        /**
         * Specify the nearest anchor. By default, the start of source section is used.
         *
         * @param start specify if we search for the nearest start or nearest end of the filtered
         *            source sections to the given location. <code>true</code> for the start and
         *            <code>false</code> for the end.
         * @return the builder to chain calls
         * @since 23.0
         */
        public Builder anchorStart(boolean start) {
            this.anchorStart = start;
            return this;
        }

        /**
         * Specify tags of the nearest source section. More accurate result might be provided when
         * tags are not specified in the base source section filter by
         * {@link SourceSectionFilter.Builder#tagIs(Class...)}, but here instead.
         *
         * @param tags a set of tags, the nearest node needs to be tagged with at least one tag from
         *            this set
         * @return the builder to chain calls
         * @since 23.0
         */
        public Builder tagIs(Class<?>... tags) {
            SourceSectionFilter.verifyNotNull(tags);
            this.theTags = tags;
            return this;
        }

        /**
         * Finalizes and constructs the {@link NearestSectionFilter} instance.
         *
         * @return the built filter
         * @since 23.0
         */
        public NearestSectionFilter build() {
            return new NearestSectionFilter(new Position(line, column, -1), anchorStart, theTags);
        }
    }
}
