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
package com.oracle.truffle.api.debug;

import java.net.URI;
import java.util.function.Predicate;

import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * Description of a textual location in guest language source code where a {@link Breakpoint} can be
 * installed.
 * <p>
 * The location's <em>key</em> identifies a particular unit of source code, for example a
 * {@link Source} or {@link URI}. It can be optionally specialized to a 1-based line number and if
 * it is, then it may also be optionally specialized to a 1-based column number.
 * </p>
 *
 */
abstract class BreakpointLocation {

    /**
     * A source location with {@code key == null} that always matches.
     */
    static final BreakpointLocation ANY = new BreakpointSourceLocation();

    static BreakpointLocation create(Object key, SourceElement[] sourceElements, SourceSection sourceSection) {
        return new BreakpointSourceLocation(key, sourceElements, sourceSection);
    }

    static BreakpointLocation create(Object key, SourceElement[] sourceElements, int line, int column) {
        return new BreakpointSourceLocation(key, sourceElements, line, column);
    }

    static BreakpointLocation create(SourceElement[] sourceElements, SuspensionFilter filter) {
        return new BreakpointFilteredLocation(sourceElements, filter);
    }

    abstract SourceFilter createSourceFilter();

    abstract SourceSection adjustLocation(Source source, TruffleInstrument.Env env, SuspendAnchor suspendAnchor);

    abstract SourceSectionFilter createLocationFilter(Source source, SuspendAnchor suspendAnchor);

    private static void setTags(SourceSectionFilter.Builder f, SourceElement[] sourceElements) {
        Class<?>[] elementTags = new Class<?>[sourceElements.length];
        for (int i = 0; i < elementTags.length; i++) {
            elementTags[i] = sourceElements[i].getTag();
        }
        f.tagIs(elementTags);
    }

    private static final class BreakpointSourceLocation extends BreakpointLocation {

        private final Object key;
        private final SourceElement[] sourceElements;
        private final SourceSection sourceSection;
        private int line;
        private int column;

        /**
         * @param key non-null source identifier
         * @param line 1-based line number, -1 for unspecified
         */
        BreakpointSourceLocation(Object key, SourceElement[] sourceElements, SourceSection sourceSection) {
            assert key instanceof Source || key instanceof URI;
            this.key = key;
            this.sourceElements = sourceElements;
            this.sourceSection = sourceSection;
            this.line = -1;
            this.column = -1;
        }

        /**
         * @param key non-null source identifier
         * @param line 1-based line number
         * @param column 1-based column number, -1 for unspecified
         */
        BreakpointSourceLocation(Object key, SourceElement[] sourceElements, int line, int column) {
            assert key instanceof Source || key instanceof URI;
            assert line > 0;
            assert column > 0 || column == -1;
            this.key = key;
            this.sourceElements = sourceElements;
            this.line = line;
            this.column = column;
            this.sourceSection = null;
        }

        private BreakpointSourceLocation() {
            this.key = null;
            this.sourceElements = null;
            this.line = -1;
            this.column = -1;
            this.sourceSection = null;
        }

        @Override
        SourceFilter createSourceFilter() {
            if (key == null) {
                return SourceFilter.ANY;
            }
            SourceFilter.Builder f = SourceFilter.newBuilder();
            if (key instanceof URI) {
                final URI sourceUri = (URI) key;
                f.sourceIs(new Predicate<Source>() {
                    @Override
                    public boolean test(Source s) {
                        URI uri = s.getURI();
                        return sourceUri.equals(uri);
                    }

                    @Override
                    public String toString() {
                        return "URI equals " + sourceUri;
                    }
                });
            } else {
                assert key instanceof Source;
                Source s = (Source) key;
                f.sourceIs(s);
            }
            return f.build();
        }

        @Override
        SourceSection adjustLocation(Source source, TruffleInstrument.Env env, SuspendAnchor suspendAnchor) {
            if (sourceSection != null) {
                return sourceSection;
            }
            if (key == null) {
                return null;
            }
            boolean hasColumn = column > 0;
            SourceSection location = SuspendableLocationFinder.findNearest(source, sourceElements, line, column, suspendAnchor, env);
            if (location != null) {
                switch (suspendAnchor) {
                    case BEFORE:
                        line = location.getStartLine();
                        if (hasColumn) {
                            column = location.getStartColumn();
                        }
                        break;
                    case AFTER:
                        line = location.getEndLine();
                        if (hasColumn) {
                            column = location.getEndColumn();
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unknown suspend anchor: " + suspendAnchor);
                }
            }
            return location;
        }

        @Override
        SourceSectionFilter createLocationFilter(Source source, SuspendAnchor suspendAnchor) {
            SourceSectionFilter.Builder f = SourceSectionFilter.newBuilder();
            if (key == null) {
                return f.tagIs(DebuggerTags.AlwaysHalt.class).build();
            }
            if (source != null) {
                f.sourceIs(source);
            } else {
                f.sourceFilter(createSourceFilter());
            }
            if (line != -1) {
                switch (suspendAnchor) {
                    case BEFORE:
                        f.lineStartsIn(IndexRange.byLength(line, 1));
                        if (column != -1) {
                            f.columnStartsIn(IndexRange.byLength(column, 1));
                        }
                        break;
                    case AFTER:
                        f.lineEndsIn(IndexRange.byLength(line, 1));
                        if (column != -1) {
                            f.columnEndsIn(IndexRange.byLength(column, 1));
                        }
                        break;
                    default:
                        throw new IllegalArgumentException(suspendAnchor.name());
                }
            }
            if (sourceSection != null) {
                f.sourceSectionEquals(sourceSection);
            }
            setTags(f, sourceElements);
            return f.build();
        }

        @Override
        public String toString() {
            String keyDescription;
            if (key == null) {
                keyDescription = "AlwaysHalt";
            } else if (key instanceof Source) {
                keyDescription = "sourceName=" + ((Source) key).getName();
            } else if (key instanceof URI) {
                keyDescription = "uri=" + ((URI) key).toString();
            } else {
                keyDescription = key.toString();
            }
            return keyDescription + ", line=" + line + ", column=" + column;
        }

    }

    private static final class BreakpointFilteredLocation extends BreakpointLocation {

        private final SuspensionFilter filter;
        private final SourceElement[] sourceElements;

        BreakpointFilteredLocation(SourceElement[] sourceElements, SuspensionFilter filter) {
            this.filter = filter;
            this.sourceElements = sourceElements;
        }

        @Override
        SourceFilter createSourceFilter() {
            return null;
        }

        @Override
        SourceSection adjustLocation(Source source, TruffleInstrument.Env env, SuspendAnchor suspendAnchor) {
            return null;
        }

        @Override
        SourceSectionFilter createLocationFilter(Source source, SuspendAnchor suspendAnchor) {
            SourceSectionFilter.Builder f = SourceSectionFilter.newBuilder();
            SourceFilter.Builder sourceFilterBuilder = SourceFilter.newBuilder();
            if (filter != null) {
                Predicate<Source> sourcePredicate = filter.getSourcePredicate();
                if (sourcePredicate != null) {
                    sourceFilterBuilder.sourceIs(sourcePredicate);
                }
                sourceFilterBuilder.includeInternal(filter.isInternalIncluded());
            }
            SourceFilter sourceFilter = sourceFilterBuilder.build();
            f.sourceFilter(sourceFilter);
            setTags(f, sourceElements);
            return f.build();
        }

    }
}
