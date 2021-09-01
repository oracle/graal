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
package com.oracle.truffle.tools.profiler.test;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.CPUSampler.Payload;
import com.oracle.truffle.tools.profiler.CPUSamplerData;
import com.oracle.truffle.tools.profiler.ProfilerNode;

public class CPUSamplerTest extends AbstractProfilerTest {

    private CPUSampler sampler;
    public static final int FIRST_TIER_THRESHOLD = 10;

    final int executionCount = 10;

    @Before
    public void setupSampler() {
        sampler = CPUSampler.find(context.getEngine());
        Assert.assertNotNull(sampler);
        sampler.setGatherSelfHitTimes(true);
    }

    @Test
    public void testInitializeContext() {
        RootNode dummy = RootNode.createConstantNode(42);

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(LanguageContext c) throws Exception {
                for (int i = 0; i < 50; i++) {
                    Thread.sleep(1);
                    TruffleSafepoint.pollHere(dummy);
                }
            }
        });

        sampler.setPeriod(1);
        sampler.clearData();
        sampler.setCollecting(true);
        context.initialize(ProxyLanguage.ID);
        sampler.setCollecting(false);

        Map<TruffleContext, CPUSamplerData> data = sampler.getData();
        assertEquals(1, data.size());

        assertEquals(1, searchInitializeContext(data).size());
    }

    @Test
    public void testSampleContextInitialization() {
        RootNode dummy = RootNode.createConstantNode(42);

        ProxyLanguage.setDelegate(new ProxyLanguage() {
            @Override
            protected void initializeContext(LanguageContext c) throws Exception {
                for (int i = 0; i < 50; i++) {
                    Thread.sleep(1);
                    TruffleSafepoint.pollHere(dummy);
                }
            }
        });

        sampler.setPeriod(1);
        sampler.setSampleContextInitialization(true);
        sampler.setCollecting(true);
        context.initialize(ProxyLanguage.ID);
        sampler.setCollecting(false);

        Map<TruffleContext, CPUSamplerData> data = sampler.getData();
        assertEquals(1, data.size());

        assertEquals(0, searchInitializeContext(data).size());
    }

    private static List<ProfilerNode<Payload>> searchInitializeContext(Map<TruffleContext, CPUSamplerData> data) {
        List<ProfilerNode<Payload>> found = new ArrayList<>();
        for (CPUSamplerData d : data.values()) {
            Map<Thread, Collection<ProfilerNode<Payload>>> threadData = d.getThreadData();
            assertEquals(threadData.toString(), 1, threadData.size());

            searchNodes(found, threadData.values().iterator().next(), (node) -> {
                return node.getRootName().equals("<<" + ProxyLanguage.ID + ":initializeContext>>");
            });

        }
        return found;
    }

    private static void searchNodes(List<ProfilerNode<CPUSampler.Payload>> results, Collection<ProfilerNode<CPUSampler.Payload>> data, Predicate<ProfilerNode<CPUSampler.Payload>> predicate) {
        for (ProfilerNode<CPUSampler.Payload> node : data) {
            if (predicate.test(node)) {
                results.add(node);
            }
            searchNodes(results, node.getChildren(), predicate);
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testCollectingAndHasData() {

        sampler.setCollecting(true);

        Assert.assertEquals(0, sampler.getSampleCount());
        Assert.assertTrue(sampler.isCollecting());
        Assert.assertFalse(sampler.hasData());

        for (int i = 0; i < executionCount; i++) {
            eval(defaultSourceForSampling);
        }

        Assert.assertNotEquals(0, sampler.getSampleCount());
        Assert.assertTrue(sampler.isCollecting());
        Assert.assertTrue(sampler.hasData());

        sampler.setCollecting(false);

        Assert.assertFalse(sampler.isCollecting());
        Assert.assertTrue(sampler.hasData());

        sampler.clearData();
        Assert.assertFalse(sampler.isCollecting());
        Assert.assertEquals(0, sampler.getSampleCount());

        Assert.assertFalse(sampler.hasData());
    }

    Source defaultSourceForSampling = makeSource("ROOT(" +
                    "DEFINE(foo,ROOT(SLEEP(1)))," +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," +
                    "CALL(baz),CALL(bar)" +
                    ")");

    @Test
    @SuppressWarnings("deprecation")
    public void testCorrectRootStructure() {

        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < executionCount; i++) {
            eval(defaultSourceForSampling);
        }

        Collection<ProfilerNode<CPUSampler.Payload>> children = sampler.getRootNodes();
        Assert.assertEquals(1, children.size());
        ProfilerNode<CPUSampler.Payload> program = children.iterator().next();
        Assert.assertEquals("", program.getRootName());
        checkTimeline(program.getPayload());

        children = program.getChildren();
        Assert.assertEquals(2, children.size());
        Iterator<ProfilerNode<CPUSampler.Payload>> iterator = children.iterator();
        ProfilerNode<CPUSampler.Payload> baz = iterator.next();
        if (!"baz".equals(baz.getRootName())) {
            baz = iterator.next();
        }
        Assert.assertEquals("baz", baz.getRootName());
        checkTimeline(baz.getPayload());

        children = baz.getChildren();
        Assert.assertEquals(1, children.size());
        ProfilerNode<CPUSampler.Payload> bar = children.iterator().next();
        Assert.assertEquals("bar", bar.getRootName());
        checkTimeline(bar.getPayload());

        children = bar.getChildren();
        Assert.assertEquals(1, children.size());
        ProfilerNode<CPUSampler.Payload> foo = children.iterator().next();
        Assert.assertEquals("foo", foo.getRootName());
        checkTimeline(foo.getPayload());

        children = foo.getChildren();
        Assert.assertTrue(children.size() == 0);
    }

    final Source defaultRecursiveSourceForSampling = makeSource("ROOT(" +
                    "DEFINE(rfoo,ROOT(BLOCK(RECURSIVE_CALL(foo, 10),SLEEP(1))))," +
                    "DEFINE(rbar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                    "CALL(bar)" +
                    ")");

    @Test
    @Ignore("non-deterministic failures on spark")
    @SuppressWarnings("deprecation")
    public void testCorrectRootStructureRecursive() {

        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < executionCount; i++) {
            eval(defaultRecursiveSourceForSampling);
        }
        sampler.setCollecting(false);

        Collection<ProfilerNode<CPUSampler.Payload>> children = sampler.getRootNodes();
        Assert.assertEquals(1, children.size());
        ProfilerNode<CPUSampler.Payload> program = children.iterator().next();
        Assert.assertEquals("", program.getRootName());
        checkTimeline(program.getPayload());

        children = program.getChildren();
        Assert.assertEquals(1, children.size());
        ProfilerNode<CPUSampler.Payload> bar = children.iterator().next();
        Assert.assertEquals("rbar", bar.getRootName());
        checkTimeline(bar.getPayload());

        children = bar.getChildren();
        Assert.assertEquals(1, children.size());
        ProfilerNode<CPUSampler.Payload> foo = children.iterator().next();
        Assert.assertEquals("rfoo", foo.getRootName());
        checkTimeline(foo.getPayload());

        // RECURSIVE_CALL does recursions to depth 10
        for (int i = 0; i < 10; i++) {
            children = foo.getChildren();
            Assert.assertEquals(1, children.size());
            foo = children.iterator().next();
            Assert.assertEquals("rfoo", foo.getRootName());
            checkTimeline(bar.getPayload());
        }

        children = foo.getChildren();
        Assert.assertTrue(children.size() == 0);
    }

    @Test
    public void testShadowStackOverflows() {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setStackLimit(2);
        sampler.setCollecting(true);
        for (int i = 0; i < executionCount; i++) {
            eval(defaultSourceForSampling);
        }
        sampler.setCollecting(false);
        Assert.assertTrue(sampler.hasData());
        Assert.assertTrue(sampler.hasStackOverflowed());
    }

    private static void checkTimeline(CPUSampler.Payload payload) {
        Assert.assertEquals("Timeline length and self hit count to not match!", payload.getSelfHitCount(), payload.getSelfHitTimes().size());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testMultiThreadedRecursive() {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < executionCount; i++) {
                    eval(defaultSourceForSampling);
                }
            }
        };
        runnable.run();
        Runnable recursiveRunnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < executionCount; i++) {
                    eval(defaultRecursiveSourceForSampling);
                }
            }
        };
        recursiveRunnable.run();
        sampler.setCollecting(true);
        Thread first = new Thread(runnable);
        first.start();
        try {
            first.join();
        } catch (InterruptedException e) {
            Assert.fail("Thread interrupted");
        }

        Thread second = new Thread(recursiveRunnable);
        second.start();
        try {
            second.join();
        } catch (InterruptedException e) {
            Assert.fail("Thread interrupted");
        }
        sampler.setCollecting(false);
        Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadToNodesMap = sampler.getThreadToNodesMap();
        Collection<ProfilerNode<CPUSampler.Payload>> rootNodes = sampler.getRootNodes();
        traverseAndCompareForDifferentSources(rootNodes, threadToNodesMap.get(first));
        traverseAndCompareForDifferentSources(rootNodes, threadToNodesMap.get(second));
    }

    private static void traverseAndCompareForDifferentSources(Collection<ProfilerNode<CPUSampler.Payload>> merged, Collection<ProfilerNode<CPUSampler.Payload>> perThread) {
        for (ProfilerNode<CPUSampler.Payload> node : perThread) {
            ProfilerNode<CPUSampler.Payload> mergedNode = findNodeBySourceAndRoot(merged, node.getSourceSection(), node.getRootName());
            Assert.assertTrue("Merged structure does not mach per thread structure", mergedNode != null);
            CPUSampler.Payload mergedNodePayload = mergedNode.getPayload();
            CPUSampler.Payload nodePayload = node.getPayload();
            Assert.assertTrue("Merged structure does not mach per thread structure", nodePayload.getSelfHitCount() == mergedNodePayload.getSelfHitCount());
            Assert.assertTrue("Merged structure does not mach per thread structure", nodePayload.getHitCount() == mergedNodePayload.getHitCount());
            traverseAndCompareForDifferentSources(mergedNode.getChildren(), node.getChildren());
        }
    }

    private static ProfilerNode<CPUSampler.Payload> findNodeBySourceAndRoot(Collection<ProfilerNode<CPUSampler.Payload>> merged, SourceSection sourceSection, String rootName) {
        for (ProfilerNode<CPUSampler.Payload> node : merged) {
            if (node.getSourceSection().equals(sourceSection) && node.getRootName().equals(rootName)) {
                return node;
            }
        }
        return null;
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testMultiThreaded() {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < executionCount; i++) {
                    eval(defaultSourceForSampling);
                }
            }
        };
        runnable.run();
        sampler.setCollecting(true);
        Thread first = new Thread(runnable);
        first.start();
        try {
            first.join();
        } catch (InterruptedException e) {
            Assert.fail("Thread interrupted");
        }

        Thread second = new Thread(runnable);
        second.start();
        try {
            second.join();
        } catch (InterruptedException e) {
            Assert.fail("Thread interrupted");
        }
        sampler.setCollecting(false);
        Map<Thread, Collection<ProfilerNode<CPUSampler.Payload>>> threadToNodesMap = sampler.getThreadToNodesMap();
        Collection<ProfilerNode<CPUSampler.Payload>> rootNodes = sampler.getRootNodes();
        traverseAndCompareForSameSource(rootNodes, threadToNodesMap.get(first), threadToNodesMap.get(second));
    }

    private void traverseAndCompareForSameSource(Collection<ProfilerNode<CPUSampler.Payload>> rootNodes, Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes1,
                    Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes2) {
        for (ProfilerNode<CPUSampler.Payload> node : rootNodes) {
            ProfilerNode<CPUSampler.Payload> found1 = findNodeBySourceAndRoot(profilerNodes1, node.getSourceSection(), node.getRootName());
            ProfilerNode<CPUSampler.Payload> found2 = findNodeBySourceAndRoot(profilerNodes2, node.getSourceSection(), node.getRootName());
            Assert.assertTrue("Merged structure does not mach per thread structure", found1 != null);
            Assert.assertTrue("Merged structure does not mach per thread structure", found2 != null);
            CPUSampler.Payload nodePayload = node.getPayload();
            CPUSampler.Payload found1Payload = found1.getPayload();
            CPUSampler.Payload found2Payload = found2.getPayload();
            Assert.assertEquals("Merged structure does not mach per thread structure", nodePayload.getSelfHitCount(), found1Payload.getSelfHitCount() + found2Payload.getSelfHitCount());
            Assert.assertEquals("Merged structure does not mach per thread structure", nodePayload.getHitCount(), found1Payload.getHitCount() + found2Payload.getHitCount());
            traverseAndCompareForSameSource(node.getChildren(), found1.getChildren(), found2.getChildren());
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testThreadSafe() throws InterruptedException {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicInteger iterations = new AtomicInteger();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while (!cancelled.get()) {
                    iterations.incrementAndGet();
                    eval(defaultSourceForSampling);
                }
            }
        };
        Thread execThread = new Thread(runnable);
        execThread.start();
        ensureIterations(iterations, 2);
        sampler.setCollecting(false);
        Collection<ProfilerNode<CPUSampler.Payload>> oldNodes = sampler.getRootNodes();
        try {
            // NOTE: Execution is still running in a separate thread.
            for (int i = 0; i < 5; i++) {
                Collection<ProfilerNode<CPUSampler.Payload>> newNodes = sampler.getRootNodes();
                isSuperset(oldNodes, newNodes);
                oldNodes = newNodes;
                ensureIterations(iterations, 1);
            }
        } finally {
            cancelled.set(true);
        }
        execThread.join(10000);
    }

    private static void ensureIterations(AtomicInteger iterations, int numberIterations) throws InterruptedException {
        iterations.set(0);
        while (iterations.get() < numberIterations) {
            Thread.sleep(5);
        }
    }

    private static void isSuperset(Collection<ProfilerNode<CPUSampler.Payload>> firstRootNodes, Collection<ProfilerNode<CPUSampler.Payload>> secondRootNodes) {
        for (ProfilerNode<CPUSampler.Payload> firstNode : firstRootNodes) {
            ProfilerNode<CPUSampler.Payload> secondNode = null;
            Iterator<ProfilerNode<CPUSampler.Payload>> iterator = secondRootNodes.iterator();
            while (iterator.hasNext()) {
                secondNode = iterator.next();
                if (secondNode.getSourceSection().equals(firstNode.getSourceSection()) && secondNode.getRootName().equals(firstNode.getRootName())) {
                    break;
                }
            }
            Assert.assertTrue("Profile taken later in execution is not superset of earlier one", secondNode != null);
            Assert.assertTrue("Profile taken later in execution is not superset of earlier one", firstNode.getPayload().getSelfHitCount() <= secondNode.getPayload().getSelfHitCount());
            isSuperset(firstNode.getChildren(), secondNode.getChildren());
        }
    }

    @Test
    public void testNegativePeriod() {
        expectProfilerException(() -> sampler.setPeriod(-1), () -> sampler.setCollecting(true));
    }

    @Test
    public void testNegativeDelay() {
        expectProfilerException(() -> sampler.setDelay(-1), () -> sampler.setCollecting(true));
    }

    @Test
    public void testStackLimit() {
        expectProfilerException(() -> sampler.setStackLimit(-1), () -> sampler.setCollecting(true));
    }

    @Test
    public void testClosedConfig() {
        expectProfilerException(() -> {
            sampler.close();
            sampler.setDelay(1);
        }, () -> sampler.setCollecting(true));
    }

    @Test
    public void testTiers() {
        Assume.assumeFalse(Truffle.getRuntime().getClass().toString().contains("Default"));
        Context.Builder builder = Context.newBuilder().option("engine.FirstTierCompilationThreshold", Integer.toString(FIRST_TIER_THRESHOLD)).option("engine.LastTierCompilationThreshold",
                        Integer.toString(2 * FIRST_TIER_THRESHOLD)).option("engine.BackgroundCompilation", "false");
        Map<TruffleContext, CPUSamplerData> data;
        try (Context c = builder.build()) {
            CPUSampler cpuSampler = CPUSampler.find(c.getEngine());
            cpuSampler.setCollecting(true);
            for (int i = 0; i < 3 * FIRST_TIER_THRESHOLD; i++) {
                c.eval(defaultSourceForSampling);
            }
            data = cpuSampler.getData();
        }
        CPUSamplerData samplerData = data.values().iterator().next();
        Collection<ProfilerNode<CPUSampler.Payload>> profilerNodes = samplerData.getThreadData().values().iterator().next();
        ProfilerNode<CPUSampler.Payload> root = profilerNodes.iterator().next();
        for (ProfilerNode<CPUSampler.Payload> child : root.getChildren()) {
            CPUSampler.Payload payload = child.getPayload();
            int numberOfTiers = payload.getNumberOfTiers();
            Assert.assertEquals(3, numberOfTiers);
            for (int i = 0; i < numberOfTiers; i++) {
                Assert.assertTrue(payload.getTierTotalCount(i) >= 0);
                Assert.assertTrue(payload.getTierSelfCount(i) >= 0);
            }

        }
    }
}
