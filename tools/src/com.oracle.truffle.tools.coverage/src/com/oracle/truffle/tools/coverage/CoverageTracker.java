/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage;

import static com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.oracle.truffle.api.instrumentation.*;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;

public final class CoverageTracker implements AutoCloseable {

    private static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().includeInternal(false).build();

    static {
        CoverageInstrument.setFactory(new Function<Env, CoverageTracker>() {
            @Override
            public CoverageTracker apply(Env env) {
                return new CoverageTracker(env);
            }

        });
    }

    private final List<CoverageNode> coverageNodes = new ArrayList<>();
    private final List<LoadSourceSectionEvent> loadedEvents = new ArrayList<>();
    private final Env env;
    private boolean tracking;
    private boolean closed;
    private EventBinding<LoadSourceSectionListener> loadedBinding;
    private EventBinding<ExecutionEventNodeFactory> coveredBinding;

    private CoverageTracker(Env env) {
        this.env = env;
    }

    private static SourceCoverage[] sourceCoverage(Map<Source, Map<SourceSection, RootData>> mapping) {
        SourceCoverage[] coverage = new SourceCoverage[mapping.size()];
        int i = 0;
        for (Map.Entry<Source, Map<SourceSection, RootData>> entry : mapping.entrySet()) {
            coverage[i++] = new SourceCoverage(entry.getKey(), rootCoverage(entry.getValue()));
        }
        return coverage;
    }

    private static RootCoverage[] rootCoverage(Map<SourceSection, RootData> perRootData) {
        RootCoverage[] rootCoverage = new RootCoverage[perRootData.size()];
        int i = 0;
        for (Map.Entry<SourceSection, RootData> entry : perRootData.entrySet()) {
            final RootData rootData = entry.getValue();
            rootCoverage[i++] = new RootCoverage(sectionCoverage(rootData),
                            rootData.covered, rootData.sourceSection, rootData.name);
        }
        return rootCoverage;
    }

    private static SectionCoverage[] sectionCoverage(RootData rootData) {
        final List<SourceSection> loadedStatements = rootData.loadedStatements;
        SectionCoverage[] sectionCoverage = new SectionCoverage[loadedStatements.size()];
        int i = 0;
        for (SourceSection statement : loadedStatements) {
            sectionCoverage[i++] = new SectionCoverage(statement, rootData.coveredStatements.contains(statement));
        }
        return sectionCoverage;
    }

    public synchronized void start(SourceSectionFilter filter) {
        if (closed) {
            throw new IllegalStateException("Coverage Tracker is closed");
        }
        if (tracking) {
            throw new IllegalStateException("Coverage Tracker is already tracking");
        }
        tracking = true;
        SourceSectionFilter f = filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        final Instrumenter instrumenter = env.getInstrumenter();
        instrument(f, instrumenter);
    }

    public synchronized void end() {
        if (!tracking) {
            throw new IllegalStateException("Coverage tracker is not tracking");
        }
        tracking = false;
        disposeBindings();
    }

    public synchronized SourceCoverage[] getCoverage() {
        return sourceCoverage(mapping());
    }

    private Map<Source, Map<SourceSection, RootData>> mapping() {
        Map<Source, Map<SourceSection, RootData>> sourceCoverage = new HashMap<>();
        processLoaded(sourceCoverage);
        processCovered(sourceCoverage);
        return sourceCoverage;
    }

    private void processLoaded(Map<Source, Map<SourceSection, RootData>> sourceCoverage) {
        for (LoadSourceSectionEvent loadedEvent : loadedEvents) {
            final SourceSection section = loadedEvent.getSourceSection();
            final Source source = section.getSource();
            final Map<SourceSection, RootData> perRootData = sourceCoverage.computeIfAbsent(source, s -> new HashMap<>());
            final Node node = loadedEvent.getNode();
            final InstrumentableNode instrumentableNode = (InstrumentableNode) node;
            final RootNode rootNode = node.getRootNode();
            if (rootNode == null) {
                continue;
            }
            if (instrumentableNode.hasTag(StandardTags.RootTag.class)) {
                perRootData.put(rootNode.getSourceSection(), new RootData(section, rootNode.getName()));
                continue;
            }
            if (instrumentableNode.hasTag(StandardTags.StatementTag.class)) {
                final RootData rootData = perRootData.get(rootNode.getSourceSection());
                rootData.loadedStatements.add(section);
                continue;
            }
            throw new IllegalStateException("Found a node without adequate tag: " + instrumentableNode);
        }
    }

    private void processCovered(Map<Source, Map<SourceSection, RootData>> mapping) {
        for (CoverageNode coverageNode : coverageNodes) {
            final SourceSection section = coverageNode.sourceSection;
            final Source source = section.getSource();
            final Node node = coverageNode.instrumentedNode;
            final RootNode rootNode = node.getRootNode();
            if (rootNode == null) {
                continue;
            }
            final RootData rootData = mapping.get(source).get(rootNode.getSourceSection());
            if (coverageNode.isRoot) {
                rootData.covered = true;
                continue;
            }
            if (coverageNode.isStatement) {
                rootData.coveredStatements.add(section);
                continue;
            }
            throw new IllegalStateException("Found a node without adequate tag.");
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (tracking) {
            end();
        }
    }

    private void instrument(SourceSectionFilter f, Instrumenter instrumenter) {
        final SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class, StandardTags.StatementTag.class).and(f).build();
        loadedBinding = instrumenter.attachLoadSourceSectionListener(filter, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                synchronized (CoverageTracker.this) {
                    loadedEvents.add(event);
                }
            }
        }, true);
        coveredBinding = instrumenter.attachExecutionEventFactory(filter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                final boolean isRoot = context.hasTag(StandardTags.RootTag.class);
                final boolean isStatement = context.hasTag(StandardTags.StatementTag.class);
                final CoverageNode coverageNode = new CoverageNode(context.getInstrumentedSourceSection(), context.getInstrumentedNode(), isRoot, isStatement);
                coverageNodes.add(coverageNode);
                return coverageNode;
            }
        });

    }

    private void disposeBindings() {
        loadedBinding.dispose();
        coveredBinding.dispose();
    }

    private static class RootData {
        private final SourceSection sourceSection;
        private final List<SourceSection> loadedStatements = new ArrayList<>();
        private final List<SourceSection> coveredStatements = new ArrayList<>();
        private final String name;
        private boolean covered;

        RootData(SourceSection sourceSection, String name) {
            this.sourceSection = sourceSection;
            this.name = name;
        }

    }
}
