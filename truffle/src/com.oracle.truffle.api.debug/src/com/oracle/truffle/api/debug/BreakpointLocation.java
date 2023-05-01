/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.instrumentation.NearestSectionFilter;
import java.net.URI;
import java.util.function.Predicate;

import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
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

    protected final SourceElement[] sourceElements;

    protected BreakpointLocation(SourceElement[] sourceElements) {
        this.sourceElements = sourceElements;
    }

    /**
     * A source location with {@code key == null} that always matches.
     */
    static final BreakpointLocation ANY = new BreakpointSourceLocation();

    static final URI ANY_SOURCE = URI.create("");

    static BreakpointLocation create(Object key, SourceElement[] sourceElements, SourceSection sourceSection) {
        return new BreakpointSourceLocation(key, sourceElements, sourceSection);
    }

    static BreakpointLocation create(Object key, SourceElement[] sourceElements, int line, int column) {
        return new BreakpointSourceLocation(key, sourceElements, line, column);
    }

    static BreakpointLocation create(SourceElement[] sourceElements, SuspensionFilter filter) {
        return new BreakpointFilteredLocation(sourceElements, filter);
    }

    final boolean containsRoot() {
        if (sourceElements == null) {
            return true;
        }
        for (SourceElement elem : sourceElements) {
            if (SourceElement.ROOT == elem) {
                return true;
            }
        }
        return false;
    }

    abstract boolean isLoadBindingNeeded();

    abstract LocationFilters createLocationFilters(SuspendAnchor suspendAnchor);

    abstract SourceSectionFilter createExecLocationFilter(SourceSection location, SuspendAnchor suspendAnchor);

    private static Class<?>[] getTags(SourceElement[] sourceElements) {
        Class<?>[] elementTags = new Class<?>[sourceElements.length];
        for (int i = 0; i < elementTags.length; i++) {
            elementTags[i] = sourceElements[i].getTag();
        }
        return elementTags;
    }

    private static void setTags(SourceSectionFilter.Builder f, SourceElement[] sourceElements) {
        f.tagIs(getTags(sourceElements));
    }

    private static void setTags(NearestSectionFilter.Builder f, SourceElement[] sourceElements) {
        f.tagIs(getTags(sourceElements));
    }

    private static final class BreakpointSourceLocation extends BreakpointLocation {

        private final Object key;
        private final SourceSection sourceSection;
        private final int line;
        private final int column;

        /**
         * @param key non-null source identifier
         * @param line 1-based line number, -1 for unspecified
         */
        BreakpointSourceLocation(Object key, SourceElement[] sourceElements, SourceSection sourceSection) {
            super(sourceElements);
            assert key instanceof Source || key instanceof URI;
            this.key = key;
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
            super(sourceElements);
            assert key instanceof Source || key instanceof URI;
            assert line > 0 || line == -1;
            assert column > 0 || column == -1;
            this.key = key;
            this.line = line;
            this.column = column;
            this.sourceSection = null;
        }

        private BreakpointSourceLocation() {
            super(null);
            this.key = null;
            this.line = -1;
            this.column = -1;
            this.sourceSection = null;
        }

        private SourceFilter createSourceFilter() {
            if (key == null) {
                return null;
            }
            SourceFilter.Builder f = SourceFilter.newBuilder();
            if (key instanceof URI) {
                f.sourceIs(createSourcePredicate());
            } else {
                assert key instanceof Source;
                Source s = (Source) key;
                f.sourceIs(s);
            }
            return f.build();
        }

        private Predicate<Source> createSourcePredicate() {
            if (key == null) {
                return null;
            }
            if (key instanceof URI) {
                if (key == ANY_SOURCE) {
                    return new Predicate<>() {
                        @Override
                        public boolean test(Source s) {
                            return true;
                        }
                    };
                }
                final URI sourceUri = (URI) key;
                final String sourceRawPath = sourceUri.getRawPath() != null ? sourceUri.getRawPath() : sourceUri.getRawSchemeSpecificPart();
                return new Predicate<>() {
                    @Override
                    public boolean test(Source s) {
                        URI uri = s.getURI();
                        if (uri.isAbsolute()) {
                            return sourceUri.equals(uri);
                        } else {
                            return sourceRawPath != null && sourceRawPath.endsWith(uri.getRawPath());
                        }
                    }

                    @Override
                    public String toString() {
                        return "URI equals " + sourceUri;
                    }
                };
            } else {
                assert key instanceof Source;
                Source source = (Source) key;
                return new Predicate<>() {
                    @Override
                    public boolean test(Source s) {
                        return source.equals(s);
                    }
                };
            }
        }

        @Override
        boolean isLoadBindingNeeded() {
            return line != -1;
        }

        @Override
        LocationFilters createLocationFilters(SuspendAnchor suspendAnchor) {
            SourceSectionFilter.Builder f = SourceSectionFilter.newBuilder();
            NearestSectionFilter nearestFilter = null;
            if (key == null) {
                f.tagIs(DebuggerTags.AlwaysHalt.class);
            } else {
                if (key != ANY_SOURCE) {
                    f.sourceFilter(createSourceFilter());
                }
                if (line != -1) {
                    assert sourceSection == null;
                    NearestSectionFilter.Builder nearestBuilder = NearestSectionFilter.newBuilder(line, column);
                    nearestBuilder.anchorStart(SuspendAnchor.BEFORE == suspendAnchor);
                    setTags(nearestBuilder, sourceElements);
                    nearestFilter = nearestBuilder.build();
                } else {
                    if (sourceSection != null) {
                        f.sourceSectionEquals(sourceSection);
                    }
                    setTags(f, sourceElements);
                }
            }
            return new LocationFilters(f.build(), nearestFilter);
        }

        @Override
        SourceSectionFilter createExecLocationFilter(SourceSection location, SuspendAnchor suspendAnchor) {
            SourceSectionFilter.Builder f = SourceSectionFilter.newBuilder();
            if (key == null) {
                return f.tagIs(DebuggerTags.AlwaysHalt.class).build();
            }
            if (column > 0) {
                // we have precise column, we adhere to the location
                f.sourceSectionEquals(location);
            } else {
                // We do not have specific column, we need to filter the whole line
                switch (suspendAnchor) {
                    case BEFORE:
                        f.lineStartsIn(IndexRange.byLength(location.getStartLine(), 1));
                        break;
                    case AFTER:
                        f.lineEndsIn(IndexRange.byLength(location.getEndLine(), 1));
                        break;
                    default:
                        throw new IllegalArgumentException(suspendAnchor.name());
                }
                f.sourceIs(location.getSource());
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

        BreakpointFilteredLocation(SourceElement[] sourceElements, SuspensionFilter filter) {
            super(sourceElements);
            this.filter = filter;
        }

        @Override
        boolean isLoadBindingNeeded() {
            return false;
        }

        @Override
        LocationFilters createLocationFilters(SuspendAnchor suspendAnchor) {
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
            return new LocationFilters(f.build(), null);
        }

        @Override
        SourceSectionFilter createExecLocationFilter(SourceSection location, SuspendAnchor suspendAnchor) {
            return null;
        }

    }

    static final class LocationFilters {

        final SourceSectionFilter sectionFilter;
        final NearestSectionFilter nearestFilter;

        private LocationFilters(SourceSectionFilter sectionFilter, NearestSectionFilter nearestFilter) {
            this.sectionFilter = sectionFilter;
            this.nearestFilter = nearestFilter;
        }
    }
}
