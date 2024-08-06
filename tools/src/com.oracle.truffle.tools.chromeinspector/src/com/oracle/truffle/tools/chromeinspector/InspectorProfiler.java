/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.domains.ProfilerDomain;
import com.oracle.truffle.tools.chromeinspector.instrument.Enabler;
import com.oracle.truffle.tools.chromeinspector.instrument.TypeProfileInstrument;
import com.oracle.truffle.tools.chromeinspector.server.ConnectionWatcher;
import com.oracle.truffle.tools.chromeinspector.types.CoverageRange;
import com.oracle.truffle.tools.chromeinspector.types.FunctionCoverage;
import com.oracle.truffle.tools.chromeinspector.types.Profile;
import com.oracle.truffle.tools.chromeinspector.types.ProfileNode;
import com.oracle.truffle.tools.chromeinspector.types.RuntimeCallFrame;
import com.oracle.truffle.tools.chromeinspector.types.Script;
import com.oracle.truffle.tools.chromeinspector.types.ScriptCoverage;
import com.oracle.truffle.tools.chromeinspector.types.ScriptTypeProfile;
import com.oracle.truffle.tools.chromeinspector.types.TypeObject;
import com.oracle.truffle.tools.chromeinspector.types.TypeProfileEntry;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.CPUTracer;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import com.oracle.truffle.tools.profiler.impl.CPUSamplerInstrument;
import com.oracle.truffle.tools.profiler.impl.CPUTracerInstrument;

public final class InspectorProfiler extends ProfilerDomain {

    private CPUSampler sampler;
    private CPUTracer tracer;
    private TypeHandler typeHandler;
    private ScriptsHandler slh;
    private long startTimestamp;
    private boolean oldGatherSelfHitTimes;

    private final InspectorExecutionContext context;
    private final ConnectionWatcher connectionWatcher;
    private Enabler enabler;

    public InspectorProfiler(InspectorExecutionContext context, ConnectionWatcher connectionWatcher) {
        this.context = context;
        this.connectionWatcher = connectionWatcher;
    }

    @Override
    public void doEnable() {
        slh = context.acquireScriptsHandler();
        sampler = context.getEnv().lookup(context.getEnv().getInstruments().get(CPUSamplerInstrument.ID), CPUSampler.class);
        tracer = context.getEnv().lookup(context.getEnv().getInstruments().get(CPUTracerInstrument.ID), CPUTracer.class);
        InstrumentInfo instrumentInfo = context.getEnv().getInstruments().get(TypeProfileInstrument.ID);
        this.enabler = context.getEnv().lookup(instrumentInfo, Enabler.class);
        enabler.enable();
        typeHandler = context.getEnv().lookup(instrumentInfo, TypeHandler.Provider.class).getTypeHandler();
    }

    @Override
    public void doDisable() {
        if (slh != null) {
            context.releaseScriptsHandler();
            slh = null;
            sampler = null;
            tracer = null;
            typeHandler = null;
            enabler.disable();
            enabler = null;
        }
    }

    @Override
    public void setSamplingInterval(long interval) {
        sampler.setPeriod(Math.max(1, TimeUnit.MICROSECONDS.toMillis(interval)));
    }

    @Override
    public void start() {
        connectionWatcher.setWaitForClose();
        synchronized (sampler) {
            oldGatherSelfHitTimes = sampler.isGatherSelfHitTimes();
            sampler.setGatherSelfHitTimes(true);
            sampler.setFilter(SourceSectionFilter.newBuilder().includeInternal(context.isInspectInternal()).build());
            sampler.setCollecting(true);
        }
        startTimestamp = System.currentTimeMillis();
    }

    @Override
    public Params stop() {
        long time = System.currentTimeMillis();
        List<CPUSamplerData> data;
        long period;
        synchronized (sampler) {
            sampler.setCollecting(false);
            sampler.setGatherSelfHitTimes(oldGatherSelfHitTimes);
            data = sampler.getDataList();
            sampler.clearData();
            period = sampler.getPeriod();
        }
        long idleHitCount = (time - startTimestamp) / period - getSampleCount(data);
        Params profile = getProfile(getRootNodes(data), idleHitCount, startTimestamp, time);
        return profile;
    }

    private static Collection<ProfilerNode<CPUSampler.Payload>> getRootNodes(List<CPUSamplerData> data) {
        Collection<ProfilerNode<CPUSampler.Payload>> retVal = new ArrayList<>();
        for (CPUSamplerData samplerData : data) {
            for (Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes : samplerData.getThreadData().values()) {
                retVal.addAll(profilerNodes);
            }
        }
        return retVal;
    }

    private static long getSampleCount(List<CPUSamplerData> data) {
        return data.stream().map(CPUSamplerData::getSamples).reduce(0L, Long::sum);
    }

    @Override
    public void startPreciseCoverage(boolean callCount, boolean detailed) {
        connectionWatcher.setWaitForClose();
        synchronized (tracer) {
            tracer.setFilter(SourceSectionFilter.newBuilder().tagIs(detailed ? StandardTags.StatementTag.class : StandardTags.RootTag.class).includeInternal(context.isInspectInternal()).build());
            tracer.setCollecting(true);
        }
    }

    @Override
    public void stopPreciseCoverage() {
        synchronized (tracer) {
            tracer.setCollecting(false);
            tracer.clearData();
        }
    }

    @Override
    public Params takePreciseCoverage() {
        synchronized (tracer) {
            Params coverage = getCoverage(tracer.getPayloads());
            tracer.clearData();
            return coverage;
        }
    }

    @Override
    public Params getBestEffortCoverage() {
        synchronized (tracer) {
            Params coverage = getCoverage(tracer.getPayloads());
            tracer.clearData();
            return coverage;
        }
    }

    @Override
    public void startTypeProfile() {
        connectionWatcher.setWaitForClose();
        typeHandler.start(context.isInspectInternal());
    }

    @Override
    public void stopTypeProfile() {
        synchronized (typeHandler) {
            typeHandler.stop();
            typeHandler.clearData();
        }
    }

    @Override
    public Params takeTypeProfile() {
        synchronized (typeHandler) {
            Params typeProfile = getTypeProfile(typeHandler.getSectionTypeProfiles());
            typeHandler.clearData();
            return typeProfile;
        }
    }

    private Params getCoverage(Collection<CPUTracer.Payload> payloads) {
        JSONObject json = new JSONObject();
        Map<Source, Map<String, Collection<CPUTracer.Payload>>> sourceToRoots = new LinkedHashMap<>();
        payloads.forEach(payload -> {
            SourceSection sourceSection = payload.getSourceSection();
            if (sourceSection != null) {
                Map<String, Collection<CPUTracer.Payload>> rootsToPayloads = sourceToRoots.computeIfAbsent(sourceSection.getSource(), s -> new LinkedHashMap<>());
                Collection<CPUTracer.Payload> pls = rootsToPayloads.computeIfAbsent(payload.getRootName(), t -> new LinkedList<>());
                pls.add(payload);
            }
        });
        JSONArray result = new JSONArray();
        sourceToRoots.entrySet().stream().map(sourceEntry -> {
            List<FunctionCoverage> functions = new ArrayList<>();
            sourceEntry.getValue().entrySet().forEach(rootEntry -> {
                boolean isBlockCoverage = false;
                List<CoverageRange> ranges = new ArrayList<>();
                for (CPUTracer.Payload payload : rootEntry.getValue()) {
                    isBlockCoverage |= payload.getTags().contains(StandardTags.StatementTag.class);
                    ranges.add(new CoverageRange(payload.getSourceSection().getCharIndex(), payload.getSourceSection().getCharEndIndex(), payload.getCount()));
                }
                functions.add(new FunctionCoverage(rootEntry.getKey(), isBlockCoverage, ranges.toArray(new CoverageRange[ranges.size()])));
            });
            Script script = slh.assureLoaded(sourceEntry.getKey());
            return new ScriptCoverage(script.getId(), script.getUrl(), functions.toArray(new FunctionCoverage[functions.size()]));
        }).forEachOrdered(scriptCoverage -> {
            result.put(scriptCoverage.toJSON());
        });
        json.put("result", result);
        return new Params(json);
    }

    private Params getProfile(Collection<ProfilerNode<CPUSampler.Payload>> rootProfilerNodes, long idleHitCount, long startTime, long endTime) {
        List<ProfileNode> nodes = new ArrayList<>();
        List<Profile.TimeLineItem> timeLine = new ArrayList<>();
        int counter = 1;
        ProfileNode root = new ProfileNode(counter++, new RuntimeCallFrame("(root)", 0, "", 0, 0), idleHitCount);
        nodes.add(root);
        fillChildren(root, rootProfilerNodes, nodes, timeLine, counter);
        Collections.sort(timeLine, (item1, item2) -> Long.compare(item1.getTimestamp(), item2.getTimestamp()));
        JSONObject json = new JSONObject();
        json.put("profile", new Profile(nodes.toArray(new ProfileNode[nodes.size()]), startTime, endTime, timeLine.toArray(new Profile.TimeLineItem[timeLine.size()])).toJSON());
        return new Params(json);
    }

    private void fillChildren(ProfileNode node, Collection<ProfilerNode<CPUSampler.Payload>> childProfilerNodes,
                    List<ProfileNode> nodes, List<Profile.TimeLineItem> timeLine, int lastCounter) {
        Map<ProfilerNode<CPUSampler.Payload>, Integer> node2id = new HashMap<>();
        ArrayDeque<ProfilerNode<CPUSampler.Payload>> dequeue = new ArrayDeque<>();
        dequeue.addAll(childProfilerNodes);
        int counter = assignChildIDs(node, childProfilerNodes, node2id, lastCounter);
        while (!dequeue.isEmpty()) {
            ProfilerNode<CPUSampler.Payload> childProfilerNode = dequeue.pollFirst();
            int id = node2id.get(childProfilerNode);
            if (id < 0) { // not computed yet
                id = -id;
                SourceSection sourceSection = childProfilerNode.getSourceSection();
                RuntimeCallFrame callFrame;
                if (sourceSection != null) {
                    Script script = slh.assureLoaded(sourceSection.getSource());
                    callFrame = new RuntimeCallFrame(childProfilerNode.getRootName(), script.getId(), script.getUrl(),
                                    sourceSection.getStartLine(), sourceSection.getStartColumn());
                } else {
                    callFrame = new RuntimeCallFrame(childProfilerNode.getRootName(), -1, "", 0, 0);
                }
                ProfileNode childNode = new ProfileNode(id, callFrame, childProfilerNode.getPayload().getSelfHitCount());
                nodes.add(childNode);
                for (Long timestamp : childProfilerNode.getPayload().getSelfHitTimes()) {
                    timeLine.add(new Profile.TimeLineItem(timestamp, id));
                }
                node2id.put(childProfilerNode, id);
                Collection<ProfilerNode<CPUSampler.Payload>> grandChildren = childProfilerNode.getChildren();
                counter = assignChildIDs(childNode, grandChildren, node2id, counter);
                dequeue.addAll(grandChildren);
            }
        }
    }

    private static int assignChildIDs(ProfileNode node, Collection<ProfilerNode<CPUSampler.Payload>> childProfilerNodes, Map<ProfilerNode<CPUSampler.Payload>, Integer> node2id, int lastCounter) {
        int counter = lastCounter;
        for (ProfilerNode<CPUSampler.Payload> child : childProfilerNodes) {
            Integer id = node2id.get(child);
            if (id == null) {
                id = counter++;
                node2id.put(child, -id); // negative ID for children that are not computed yet
            }
            node.addChild(Math.abs(id));
        }
        return counter;
    }

    private Params getTypeProfile(Collection<TypeHandler.SectionTypeProfile> profiles) {
        JSONObject json = new JSONObject();
        Map<Source, Collection<TypeHandler.SectionTypeProfile>> sourceToProfiles = new LinkedHashMap<>();
        profiles.forEach(profile -> {
            Collection<TypeHandler.SectionTypeProfile> pfs = sourceToProfiles.computeIfAbsent(profile.getSourceSection().getSource(), t -> new LinkedList<>());
            pfs.add(profile);
        });
        JSONArray result = new JSONArray();
        sourceToProfiles.entrySet().forEach(entry -> {
            List<TypeProfileEntry> entries = new ArrayList<>();
            entry.getValue().forEach(sectionProfile -> {
                List<TypeObject> types = new ArrayList<>();
                sectionProfile.getTypes().forEach(type -> {
                    types.add(new TypeObject(type));
                });
                if (!types.isEmpty()) {
                    entries.add(new TypeProfileEntry(sectionProfile.getSourceSection().getCharEndIndex(), types.toArray(new TypeObject[types.size()])));
                }
            });
            Script script = slh.assureLoaded(entry.getKey());
            result.put(new ScriptTypeProfile(script.getId(), script.getUrl(), entries.toArray(new TypeProfileEntry[entries.size()])).toJSON());
        });
        json.put("result", result);
        return new Params(json);
    }
}
