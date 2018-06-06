/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

/**
 * This class searches for a suspendable locations (SourceSection tagged with StatementTag) in a
 * Source file.
 */
final class SuspendableLocationFinder {

    private static final Set<Class<? extends Tag>> SUSPENDABLE_TAGS_SET = Collections.singleton(StandardTags.StatementTag.class);
    private static final Class<?>[] SUSPENDABLE_TAGS = SUSPENDABLE_TAGS_SET.toArray(new Class<?>[SUSPENDABLE_TAGS_SET.size()]);

    private SuspendableLocationFinder() {
    }

    static Iterable<SourceSection> findSuspendableLocations(SourceSection range, boolean restrictToSingleFunction, DebuggerSession session, TruffleInstrument.Env env) {
        Source source = range.getSource();
        int startIndex = range.getCharIndex();
        int endIndex = range.getCharEndIndex();
        SectionsCollector sectionsCollector = collectSuspendableLocations(source, startIndex, endIndex, restrictToSingleFunction, env);
        List<SourceSection> sections = sectionsCollector.getSections();
        if (sections.isEmpty()) {
            AtomicReference<SourceSection> nearestSection = new AtomicReference<>();
            // Submit a test breakpoint that will be moved to the nerest suspendable location:
            Breakpoint breakpoint = Breakpoint.newBuilder(source).ignoreCount(Integer.MAX_VALUE).lineIs(range.getStartLine()).columnIs(range.getStartColumn()).resolveListener(
                            new Breakpoint.ResolveListener() {
                                @Override
                                public void breakpointResolved(Breakpoint b, SourceSection section) {
                                    nearestSection.set(section);
                                }
                            }).build();
            try {
                session.install(breakpoint);
            } finally {
                // Dispose the test breakpoint, a real breakpoint is likely to be submitted at that
                // location by the inspector client.
                breakpoint.dispose();
            }
            SourceSection suspendableSection = nearestSection.get();
            if (suspendableSection != null) {
                startIndex = suspendableSection.getCharIndex();
                endIndex = suspendableSection.getCharEndIndex();
                sectionsCollector = collectSuspendableLocations(source, startIndex, endIndex, restrictToSingleFunction, env);
                sections = sectionsCollector.getSections();
            }
        }
        return sections;
    }

    private static SectionsCollector collectSuspendableLocations(Source source, int startIndex, int endIndex, boolean restrictToSingleFunction, TruffleInstrument.Env env) {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(source).indexIn(IndexRange.between(startIndex, endIndex)).tagIs(SUSPENDABLE_TAGS).build();
        SectionsCollector sectionsCollector;
        if (restrictToSingleFunction) {
            sectionsCollector = new FunctionSectionsCollector(startIndex, env.getInstrumenter());
        } else {
            sectionsCollector = new SectionsCollector(startIndex);
        }
        env.getInstrumenter().visitLoadedSourceSections(filter, sectionsCollector);
        return sectionsCollector;
    }

    private static class SectionsCollector implements LoadSourceSectionListener {

        protected final int startIndex;
        private final List<SourceSection> sections = new ArrayList<>();

        SectionsCollector(int startIndex) {
            this.startIndex = startIndex;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            SourceSection section = event.getSourceSection();
            if (section.getCharIndex() >= startIndex) {
                sections.add(section);
            }
        }

        List<SourceSection> getSections() {
            return sections;
        }
    }

    private static final class FunctionSectionsCollector extends SectionsCollector {

        private final Instrumenter instrumenter;
        private Node rangeNode;
        private final Map<Node, List<SourceSection>> sectionsMap = new HashMap<>();

        FunctionSectionsCollector(int startIndex, Instrumenter instrumenter) {
            super(startIndex);
            this.instrumenter = instrumenter;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            Node node = event.getNode();
            Node root = findRoot(node);
            if (root != null) {
                SourceSection section = event.getSourceSection();
                if (section.getCharIndex() >= startIndex) {
                    List<SourceSection> list = sectionsMap.get(root);
                    if (list == null) {
                        list = new ArrayList<>();
                        sectionsMap.put(root, list);
                    }
                    list.add(section);
                    if (rangeNode == null || (section.getCharIndex() - startIndex) < (rangeNode.getSourceSection().getCharIndex() - startIndex)) {
                        rangeNode = node;
                    }
                }
            }
        }

        @Override
        List<SourceSection> getSections() {
            if (rangeNode == null) {
                return Collections.emptyList();
            }
            Node root = findRoot(rangeNode);
            return sectionsMap.get(root);
        }

        private Node findRoot(Node node) {
            if (instrumenter.queryTags(node).contains(StandardTags.RootTag.class)) {
                return node;
            } else {
                Node parent = node.getParent();
                if (parent == null) {
                    return null;
                } else {
                    return findRoot(parent);
                }
            }
        }
    }

}
