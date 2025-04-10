/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.profiler.impl;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.graalvm.shadowed.org.json.JSONObject;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

abstract class ProfilerCLI {

    public static final String UNKNOWN = "<Unknown>";

    static SourceSectionFilter buildFilter(boolean roots, boolean statements, boolean calls, boolean internals,
                    WildcardFilter filterRootName, WildcardFilter filterFile, String filterMimeType, String filterLanguage) {
        SourceSectionFilter.Builder builder = SourceSectionFilter.newBuilder();
        if (!internals || filterFile != null || filterMimeType != null || filterLanguage != null) {
            builder.sourceIs(new SourceSectionFilter.SourcePredicate() {
                @Override
                public boolean test(Source source) {
                    boolean internal = (internals || !source.isInternal());
                    boolean file = filterFile.testWildcardExpressions(source.getPath());
                    boolean mimeType = filterMimeType.equals("") || filterMimeType.equals(source.getMimeType());
                    final boolean languageId = filterLanguage.equals("") || filterMimeType.equals(source.getLanguage());
                    return internal && file && mimeType && languageId;
                }
            });
        }

        List<Class<?>> tags = new ArrayList<>();
        if (roots) {
            tags.add(StandardTags.RootTag.class);
        }
        if (statements) {
            tags.add(StandardTags.StatementTag.class);
        }
        if (calls) {
            tags.add(StandardTags.CallTag.class);
        }

        if (!roots && !statements && !calls) {
            throw new IllegalArgumentException(
                            "No elements specified. Either roots, statements or calls must remain enabled.");
        }
        builder.tagIs(tags.toArray(new Class<?>[0]));
        builder.rootNameIs(new Predicate<String>() {
            @Override
            public boolean test(String s) {
                return filterRootName.testWildcardExpressions(s);
            }
        });

        return builder.build();
    }

    static String repeat(String s, int times) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < times; i++) {
            b.append(s);
        }
        return b.toString();
    }

    // custom version of SourceSection#getShortDescription
    static String getShortDescription(SourceSection sourceSection) {
        if (sourceSection == null) {
            return UNKNOWN;
        }
        if (sourceSection.getSource() == null) {
            // TODO the source == null branch can be removed if the deprecated
            // SourceSection#createUnavailable has be removed.
            return UNKNOWN;
        }
        StringBuilder b = new StringBuilder();
        Source source = sourceSection.getSource();
        URL url = source.getURL();
        if (url != null && !"file".equals(url.getProtocol())) {
            b.append(url.toExternalForm());
        } else if (source.getPath() != null) {
            try {
                /*
                 * On Windows, getPath for a local file URL returns a path in the format
                 * `/C:/Documents/`, which is not a valid file system path on Windows. Attempting to
                 * parse this path using Path#of results in a failure. However, java.io.File
                 * correctly handles this format by removing the invalid leading `/` character.
                 */
                Path pathAbsolute = new File(source.getPath()).toPath();
                Path pathBase = new File("").getAbsoluteFile().toPath();
                Path pathRelative = pathBase.relativize(pathAbsolute);
                b.append(pathRelative.toFile());
            } catch (IllegalArgumentException e) {
                b.append(source.getName());
            }
        } else {
            b.append(source.getName());
        }

        b.append("~").append(formatIndices(sourceSection, true));
        return b.toString();
    }

    static String formatIndices(SourceSection sourceSection, boolean needsColumnSpecifier) {
        if (sourceSection == null) {
            return UNKNOWN;
        }
        StringBuilder b = new StringBuilder();
        boolean singleLine = sourceSection.getStartLine() == sourceSection.getEndLine();
        if (singleLine) {
            b.append(sourceSection.getStartLine());
        } else {
            b.append(sourceSection.getStartLine()).append("-").append(sourceSection.getEndLine());
        }
        if (needsColumnSpecifier) {
            b.append(":");
            if (sourceSection.getCharLength() <= 1) {
                b.append(sourceSection.getCharIndex());
            } else {
                b.append(sourceSection.getCharIndex()).append("-").append(sourceSection.getCharIndex() + sourceSection.getCharLength() - 1);
            }
        }
        return b.toString();
    }

    static boolean testWildcardExpressions(String value, Object[] fileFilters) {
        if (fileFilters == null || fileFilters.length == 0) {
            return true;
        }
        if (value == null) {
            return false;
        }
        for (Object filter : fileFilters) {
            if (filter instanceof Pattern) {
                if (((Pattern) filter).matcher(value).matches()) {
                    return true;
                }
            } else if (filter instanceof String) {
                if (filter.equals(value)) {
                    return true;
                }
            } else {
                throw new AssertionError();
            }
        }
        return false;
    }

    static JSONObject sourceSectionToJSON(SourceSection sourceSection) {
        JSONObject sourceSectionJson = new JSONObject();
        if (sourceSection != null) {
            Source source = sourceSection.getSource();
            if (source != null) {
                if (source.getLanguage() != null) {
                    sourceSectionJson.put("language", source.getLanguage().toString());
                }
                String path = source.getPath();
                if (path != null) {
                    sourceSectionJson.put("path", path);
                }
            }
            sourceSectionJson.put("source_name", sourceSection.getSource().getName());
            sourceSectionJson.put("start_line", sourceSection.getStartLine());
            sourceSectionJson.put("end_line", sourceSection.getEndLine());
            sourceSectionJson.put("start_column", sourceSection.getStartColumn());
            sourceSectionJson.put("end_column", sourceSection.getEndColumn());
        }
        return sourceSectionJson;
    }

    static class SourceLocation {

        private final SourceSection sourceSection;
        private final String rootName;

        SourceLocation(SourceSection sourceSection, String rootName) {
            this.sourceSection = sourceSection;
            this.rootName = rootName;
        }

        SourceSection getSourceSection() {
            return sourceSection;
        }

        public String getRootName() {
            return rootName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            SourceLocation that = (SourceLocation) o;
            if (!Objects.equals(sourceSection, that.sourceSection)) {
                return false;
            }
            return Objects.equals(rootName, that.rootName);
        }

        @Override
        public int hashCode() {
            int result = sourceSection != null ? sourceSection.hashCode() : 0;
            result = 31 * result + (rootName != null ? rootName.hashCode() : 0);
            return result;
        }
    }

    protected static AbstractTruffleException handleFileNotFound() {
        return new AbstractTruffleException() {
            static final long serialVersionUID = -1;

            @Override
            public String getMessage() {
                return "File IO Exception caught during output printing.";
            }
        };
    }
}
