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

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Instrumenter;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;
import com.oracle.truffle.tools.coverage.impl.CoverageNode;

public class CoverageTracker implements AutoCloseable {

    public synchronized void startTracking(SourceSectionFilter filter) {
        if (closed) {
            throw new IllegalStateException("Coverage Tracker is closed");
        }
        if (tracking) {
            throw new IllegalStateException("Coverage Tracker is already tracking");
        }
        assert Thread.holdsLock(this);
        tracking = true;
        SourceSectionFilter f = filter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        final Instrumenter instrumenter = env.getInstrumenter();
        instrument(filter, instrumenter);
    }

    public synchronized void endTracking() {
        if (!tracking) {
            throw new IllegalStateException("Coverage tracker is not tracking");
        }
        tracking = false;
        disposeBindings();
    }

    public synchronized SourceCoverage[] getCoverage() {
        assert loadedSections.size() == loadedNodes.size();
        assert coveredSections.size() == coveredNodes.size();
        return getSourceCoverage(makeMapping());
    }

    private static SourceCoverage[] getSourceCoverage(Map<Source, Map<RootNode, RootData>> mapping) {
        SourceCoverage[] coverage = new SourceCoverage[mapping.size()];
        int i = 0;
        for (Map.Entry<Source, Map<RootNode, RootData>> entry : mapping.entrySet()) {
            coverage[i++] = new SourceCoverage(entry.getKey(), getRootCoverage(entry.getValue()));
        }
        return coverage;
    }

    private static RootCoverage[] getRootCoverage(Map<RootNode, RootData> perRootData) {
        RootCoverage[] rootCoverage = new RootCoverage[perRootData.size()];
        int i = 0;
        for (Map.Entry<RootNode, RootData> entry : perRootData.entrySet()) {
            final RootData rootData = entry.getValue();
            rootCoverage[i++] = new RootCoverage(getSectionCoverage(rootData),
                            rootData.covered, rootData.sourceSection, entry.getKey().getName());
        }
        return rootCoverage;
    }

    private static SectionCoverage[] getSectionCoverage(RootData rootData) {
        final List<SourceSection> loadedStatements = rootData.loadedStatements;
        SectionCoverage[] sectionCoverage = new SectionCoverage[loadedStatements.size()];
        int i = 0;
        for (SourceSection statement : loadedStatements) {
            sectionCoverage[i++] = new SectionCoverage(statement, rootData.coveredStatements.contains(statement));
        }
        return sectionCoverage;
    }

    private static class RootData {
        private final SourceSection sourceSection;

        private boolean covered;
        private final List<SourceSection> loadedStatements = new ArrayList<>();
        private final List<SourceSection> coveredStatements = new ArrayList<>();

        RootData(SourceSection sourceSection) {
            this.sourceSection = sourceSection;
        }

    }

    private Map<Source, Map<RootNode, RootData>> makeMapping() {
        Map<Source, Map<RootNode, RootData>> sourceCoverage = new HashMap<>();
        processLoaded(sourceCoverage);
        processCovered(sourceCoverage);
        return sourceCoverage;
    }

    private void processLoaded(Map<Source, Map<RootNode, RootData>> sourceCoverage) {
        for (int i = 0; i < loadedSections.size(); i++) {
            final SourceSection section = loadedSections.get(i);
            final Source source = section.getSource();
            final Map<RootNode, RootData> perRootData = sourceCoverage.computeIfAbsent(source, s -> new HashMap<>());
            final Node node = loadedNodes.get(i);
            final InstrumentableNode instrumentableNode = (InstrumentableNode) node;
            if (instrumentableNode.hasTag(StandardTags.RootTag.class)) {
                perRootData.put(node.getRootNode(), new RootData(section));
                continue;
            }
            if (instrumentableNode.hasTag(StandardTags.StatementTag.class)) {
                final RootData rootData = perRootData.get(node.getRootNode());
                rootData.loadedStatements.add(section);
                continue;
            }
            throw new IllegalStateException("Found a node without adequate tag: " + instrumentableNode);
        }
    }

    private void processCovered(Map<Source, Map<RootNode, RootData>> mapping) {
        for (int i = 0; i < coveredSections.size(); i++) {
            final SourceSection section = coveredSections.get(i);
            final Source source = section.getSource();
            final Node node = coveredNodes.get(i);
            final RootData rootData = mapping.get(source).get(node.getRootNode());
            final InstrumentableNode instrumentableNode = (InstrumentableNode) node;
            if (instrumentableNode.hasTag(StandardTags.RootTag.class)) {
                rootData.covered = true;
                continue;
            }
            if (instrumentableNode.hasTag(StandardTags.StatementTag.class)) {
                rootData.coveredStatements.add(section);
                continue;
            }
            throw new IllegalStateException("Found a node without adequate tag: " + instrumentableNode);
        }
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (tracking) {
            endTracking();
        }
    }

    private static final SourceSectionFilter DEFAULT_FILTER = SourceSectionFilter.newBuilder().includeInternal(false).build();

    static {
        CoverageInstrument.setFactory(new Function<Env, CoverageTracker>() {
            @Override
            public CoverageTracker apply(Env env) {
                return new CoverageTracker(env);
            }

        });
    }

    private final Env env;
    private boolean tracking;
    private boolean closed;
    private EventBinding<LoadSourceSectionListener> loadedBinding;
    private EventBinding<ExecutionEventNodeFactory> coveredBinding;
    private final List<SourceSection> loadedSections = new ArrayList<>();
    private final List<Node> loadedNodes = new ArrayList<>();
    final List<SourceSection> coveredSections = new ArrayList<>();
    private final List<Node> coveredNodes = new ArrayList<>();

    private CoverageTracker(Env env) {
        this.env = env;
    }

    private void instrument(SourceSectionFilter f, Instrumenter instrumenter) {
        f = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class, StandardTags.StatementTag.class).and(f).build();
        loadedBinding = instrumenter.attachLoadSourceSectionListener(f, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                loadedSections.add(event.getSourceSection());
                loadedNodes.add(event.getNode());
            }
        }, false);
        coveredBinding = instrumenter.attachExecutionEventFactory(f, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                return new CoverageNode(context.getInstrumentedSourceSection(), context.getInstrumentedNode()) {

                    @Override
                    protected void notifyTracker() {
                        coveredSections.add(sourceSection);
                        coveredNodes.add(instrumentedNode);
                    }
                };
            }
        });

    }

    private void disposeBindings() {
        loadedBinding.dispose();
        coveredBinding.dispose();
    }
}
