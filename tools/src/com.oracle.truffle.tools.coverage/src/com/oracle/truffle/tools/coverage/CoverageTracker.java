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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
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

    private final List<AbstractCoverageNode> coverageNodes = new ArrayList<>();
    private final List<LoadSourceSectionEvent> loadedRoots = new ArrayList<>();
    private final List<LoadSourceSectionEvent> loadedStatements = new ArrayList<>();
    private final Env env;
    private boolean tracking;
    private boolean closed;
    private EventBinding<LoadSourceSectionListener> loadedRootsBinding;
    private EventBinding<ExecutionEventNodeFactory> coveredBinding;
    private EventBinding<LoadSourceSectionListener> loadedStatementBinding;

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
                            rootData.covered, rootData.count, rootData.sourceSection, rootData.name);
        }
        return rootCoverage;
    }

    private static SectionCoverage[] sectionCoverage(RootData rootData) {
        final Set<SourceSection> loadedStatements = rootData.loadedStatements;
        SectionCoverage[] sectionCoverage = new SectionCoverage[loadedStatements.size()];
        int i = 0;
        for (SourceSection statement : loadedStatements) {
            final Long count = rootData.coveredStatements.get(statement);
            sectionCoverage[i++] = new SectionCoverage(statement, count != null, count == null ? -1 : count);
        }
        return sectionCoverage;
    }

    private static AbstractCoverageNode makeCoverageNode(EventContext context, Config config) {
        final boolean isRoot = context.hasTag(StandardTags.RootTag.class);
        final boolean isStatement = context.hasTag(StandardTags.StatementTag.class);
        if (config.count) {
            return new CountingCoverageNode(context.getInstrumentedSourceSection(), context.getInstrumentedNode(), isRoot, isStatement);
        } else {
            return new BooleanCoverageNode(context.getInstrumentedSourceSection(), context.getInstrumentedNode(), isRoot, isStatement);
        }
    }

    private static long getCount(AbstractCoverageNode coverageNode) {
        return coverageNode instanceof CountingCoverageNode ? ((CountingCoverageNode) coverageNode).getCount() : -1;
    }

    /**
     * Start coverage tracking with the given config.
     * 
     * @param config The configuration for the coverage tracking.
     * @throws IllegalStateException if the tracker is {@link CoverageTracker#close() closed} or
     *             already started.
     * @since 19.3.0
     */
    public synchronized void start(Config config) {
        if (closed) {
            throw new IllegalStateException("Coverage Tracker is closed");
        }
        if (tracking) {
            throw new IllegalStateException("Coverage Tracker is already tracking");
        }
        clearData();
        tracking = true;
        final Instrumenter instrumenter = env.getInstrumenter();
        instrument(config, instrumenter);
    }

    private synchronized void clearData() {
        this.loadedRoots.clear();
        this.loadedStatements.clear();
        this.coverageNodes.clear();
    }

    /**
     * Stop tracking coverage.
     * 
     * @throws IllegalStateException if called on a tracker that has not been
     *             {@link CoverageTracker#start(Config) started}
     * @since 19.3.0
     */
    public synchronized void end() {
        if (!tracking) {
            throw new IllegalStateException("Coverage tracker is not tracking");
        }
        tracking = false;
        disposeBindings();
    }

    /**
     * @return the coverage gathered thus far.
     * @since 19.3.0
     */
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
        processLoadedRoots(sourceCoverage);
        processLoadedSections(sourceCoverage);
    }

    private void processLoadedSections(Map<Source, Map<SourceSection, RootData>> sourceCoverage) {
        for (LoadSourceSectionEvent loadedEvent : loadedStatements) {
            final SourceSection section = loadedEvent.getSourceSection();
            final Source source = section.getSource();
            final Node node = loadedEvent.getNode();
            final RootNode rootNode = node.getRootNode();
            final Map<SourceSection, RootData> perSourceData = sourceCoverage.computeIfAbsent(source, s -> new HashMap<>());
            final RootData rootData = perSourceData.computeIfAbsent(rootNode.getSourceSection(), s -> new RootData(s, rootNode.getName()));
            rootData.loadedStatements.add(section);
        }
    }

    private void processLoadedRoots(Map<Source, Map<SourceSection, RootData>> sourceCoverage) {
        for (LoadSourceSectionEvent loadedEvent : loadedRoots) {
            final SourceSection section = loadedEvent.getSourceSection();
            final Source source = section.getSource();
            final Map<SourceSection, RootData> perRootData = sourceCoverage.computeIfAbsent(source, s -> new HashMap<>());
            final Node node = loadedEvent.getNode();
            final RootNode rootNode = node.getRootNode();
            if (rootNode == null) {
                continue;
            }
            perRootData.put(rootNode.getSourceSection(), new RootData(section, rootNode.getName()));
        }
    }

    private void processCovered(Map<Source, Map<SourceSection, RootData>> mapping) {
        for (AbstractCoverageNode coverageNode : coverageNodes) {
            final SourceSection section = coverageNode.sourceSection;
            final Source source = section.getSource();
            final Node node = coverageNode.instrumentedNode;
            final RootNode rootNode = node.getRootNode();
            if (rootNode == null || !coverageNode.isCovered()) {
                continue;
            }
            final RootData rootData = mapping.get(source).get(rootNode.getSourceSection());
            final long count = getCount(coverageNode);
            if (coverageNode.isRoot && coverageNode.isCovered()) {
                rootData.covered = true;
                rootData.count = count;
                continue;
            }
            if (coverageNode.isStatement) {
                rootData.coveredStatements.put(section, count);
                continue;
            }
            throw new IllegalStateException("Found a node without adequate tag.");
        }
    }

    /**
     * Closes the CoverageTracker. This makes it unusable further.
     * 
     * @since 19.3.0
     */
    @Override
    public synchronized void close() {
        closed = true;
        if (tracking) {
            end();
        }
    }

    private void instrument(Config config, Instrumenter instrumenter) {
        SourceSectionFilter f = config.sourceSectionFilter;
        if (f == null) {
            f = DEFAULT_FILTER;
        }
        instrumentLoadedRoots(instrumenter, f);
        instrumentLoadedStatements(instrumenter, f);
        instrumentExecution(config, instrumenter, f);

    }

    private void instrumentExecution(Config config, Instrumenter instrumenter, SourceSectionFilter f) {
        final SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class, StandardTags.StatementTag.class).and(f).build();
        coveredBinding = instrumenter.attachExecutionEventFactory(filter, new ExecutionEventNodeFactory() {
            @Override
            public ExecutionEventNode create(EventContext context) {
                final AbstractCoverageNode coverageNode = makeCoverageNode(context, config);
                addCoverageNode(coverageNode);
                return coverageNode;
            }
        });
    }

    private synchronized void addCoverageNode(AbstractCoverageNode coverageNode) {
        coverageNodes.add(coverageNode);
    }

    private void instrumentLoadedStatements(Instrumenter instrumenter, SourceSectionFilter f) {
        final SourceSectionFilter statementFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).and(f).build();
        loadedStatementBinding = instrumenter.attachLoadSourceSectionListener(statementFilter, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                addStatement(event);
            }
        }, true);
    }

    private void instrumentLoadedRoots(Instrumenter instrumenter, SourceSectionFilter f) {
        final SourceSectionFilter rootFilter = SourceSectionFilter.newBuilder().tagIs(StandardTags.RootTag.class).and(f).build();
        loadedRootsBinding = instrumenter.attachLoadSourceSectionListener(rootFilter, new LoadSourceSectionListener() {
            @Override
            public void onLoad(LoadSourceSectionEvent event) {
                addRoot(event);
            }
        }, true);
    }

    private synchronized void addRoot(LoadSourceSectionEvent event) {
        loadedRoots.add(event);
    }

    private synchronized void addStatement(LoadSourceSectionEvent event) {
        loadedStatements.add(event);
    }

    private void disposeBindings() {
        loadedRootsBinding.dispose();
        loadedStatementBinding.dispose();
        coveredBinding.dispose();
    }

    private static class RootData {
        private final SourceSection sourceSection;
        private final Set<SourceSection> loadedStatements = new HashSet<>();
        private final Map<SourceSection, Long> coveredStatements = new HashMap<>();
        private final String name;
        private long count;
        private boolean covered;

        RootData(SourceSection sourceSection, String name) {
            this.sourceSection = sourceSection;
            this.name = name;
        }

    }

    /**
     * Configuration for the {@link CoverageTracker}. Specifies the {@link SourceSectionFilter
     * filter} for which {@link SourceSection source sections} to include in tracking as well as
     * whether to keep track of how many times a particular source section was executed.
     * 
     * @since 19.3.0
     */
    public static class Config {
        private final SourceSectionFilter sourceSectionFilter;
        private final boolean count;

        public Config(SourceSectionFilter sourceSectionFilter, boolean count) {
            this.sourceSectionFilter = sourceSectionFilter;
            this.count = count;
        }
    }
}
