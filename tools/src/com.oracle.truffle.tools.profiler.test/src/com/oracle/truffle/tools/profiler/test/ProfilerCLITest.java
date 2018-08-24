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
package com.oracle.truffle.tools.profiler.test;

import java.io.ByteArrayOutputStream;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.ProfilerNode;
import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

public class ProfilerCLITest {

    protected Source makeSource(String s) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, s, "test").buildLiteral();
    }

    @Test
    public void testSamplerJson() {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        Context context = Context.newBuilder().in(System.in).out(out).err(err).option("cpusampler", "true").option("cpusampler.Output", "json").build();
        Source defaultSourceForSampling = makeSource("ROOT(" +
                        "DEFINE(foo,ROOT(SLEEP(1)))," +
                        "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                        "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," +
                        "CALL(baz),CALL(bar)" +
                        ")");
        for (int i = 0; i < 10; i++) {
            context.eval(defaultSourceForSampling);
        }
        CPUSampler sampler = CPUSampler.find(context.getEngine());
        Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadToNodesMap = sampler.getThreadToNodesMap();
        final long period = sampler.getPeriod();
        final long sampleCount = sampler.getSampleCount();
        final boolean gatherSelfHitTimes = sampler.isGatherSelfHitTimes();
        context.close();
        JSONObject output = new JSONObject(out.toString());

        Assert.assertEquals("Period wrong in json", period, Long.valueOf((Integer) output.get("period")).longValue());
        Assert.assertEquals("Sample count not correct in json", sampleCount, Long.valueOf((Integer) output.get("sample_count")).longValue());
        Assert.assertEquals("Gather self times not correct in json", gatherSelfHitTimes, output.get("gathered_hit_times"));
        JSONArray profile = (JSONArray) output.get("profile");

        // We are single threaded in this test
        JSONObject perThreadProfile = (JSONObject) profile.get(0);
        JSONArray samples = (JSONArray) perThreadProfile.get("samples");
        Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes = threadToNodesMap.get(Thread.currentThread());
        deepCompare(samples, profilerNodes);
    }

    private void deepCompare(JSONArray samples, Collection<ProfilerNode<CPUSampler.Payload>> nodes) {
        for (int i = 0; i < samples.length(); i++) {
            JSONObject sample = (JSONObject) samples.get(i);
            ProfilerNode<CPUSampler.Payload> correspondingNode = findCorrespondingNode(sample, nodes);
            if (correspondingNode != null) {
                CPUSampler.Payload payload = correspondingNode.getPayload();
                final int selfCompiledHitCount = payload.getSelfCompiledHitCount();
                Assert.assertEquals("Wrong payload", selfCompiledHitCount, sample.get("self_compiled_hit_count"));
                final int selfInterpretedHitCount = payload.getSelfInterpretedHitCount();
                Assert.assertEquals("Wrong payload", selfInterpretedHitCount, sample.get("self_interpreted_hit_count"));
                final List<Long> selfHitTimes = payload.getSelfHitTimes();
                final JSONArray selfHitTimesJson = (JSONArray) sample.get("self_hit_times");
                Assert.assertEquals("Wrong payload", selfHitTimes.size(), selfHitTimesJson.length());
                for (int j = 0; j < selfHitTimes.size(); j++) {
                    Assert.assertEquals("Wrong payload", selfHitTimes.get(j).longValue(), selfHitTimesJson.getLong(j));
                }
                final int compiledHitCount = payload.getCompiledHitCount();
                Assert.assertEquals("Wrong payload", compiledHitCount, sample.get("compiled_hit_count"));
                final int interpretedHitCount = payload.getInterpretedHitCount();
                Assert.assertEquals("Wrong payload", interpretedHitCount, sample.get("interpreted_hit_count"));

                deepCompare((JSONArray) sample.get("children"), correspondingNode.getChildren());
            } else {
                Assert.fail("No corresponding node for one in JSON.");
            }
        }
    }

    private static ProfilerNode<CPUSampler.Payload> findCorrespondingNode(JSONObject sample, Collection<ProfilerNode<CPUSampler.Payload>> nodes) {
        String root = (String) sample.get("root_name");
        JSONObject sourceSection = (JSONObject) sample.get("source_section");
        String sourceName = (String) sourceSection.get("source_name");
        int startColumn = (int) sourceSection.get("start_column");
        int endColumn = (int) sourceSection.get("end_column");
        int startLine = (int) sourceSection.get("start_line");
        int endLine = (int) sourceSection.get("end_line");
        Iterator<ProfilerNode<CPUSampler.Payload>> iterator = nodes.iterator();
        while (iterator.hasNext()) {
            ProfilerNode<CPUSampler.Payload> next = iterator.next();
            SourceSection nextSourceSection = next.getSourceSection();
            if (next.getRootName().equals(root) && nextSourceSection.getSource().getName().equals(sourceName) && nextSourceSection.getStartColumn() == startColumn &&
                            nextSourceSection.getEndColumn() == endColumn && nextSourceSection.getStartLine() == startLine && nextSourceSection.getEndLine() == endLine) {
                return next;
            }
        }
        return null;
    }
}
