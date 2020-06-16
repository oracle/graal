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

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.profiler.CPUSampler;
import com.oracle.truffle.tools.profiler.ProfilerNode;

public class CPUSamplerTest extends AbstractProfilerTest {

    private static CPUSampler sampler;

    final int executionCount = 10;

    @Before
    public void setupSampler() {
        sampler = CPUSampler.find(context.getEngine());
        Assert.assertNotNull(sampler);
        synchronized (sampler) {
            sampler.setGatherSelfHitTimes(true);
            sampler.setDelaySamplingUntilNonInternalLangInit(false);
        }
    }

    @Test
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
    public void testCorrectRootStructureRecursive() {

        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        for (int i = 0; i < executionCount; i++) {
            eval(defaultRecursiveSourceForSampling);
        }

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
        Assert.assertTrue(sampler.hasStackOverflowed());
    }

    private static void checkTimeline(CPUSampler.Payload payload) {
        Assert.assertEquals("Timeline length and self hit count to not match!", payload.getSelfHitCount(), payload.getSelfHitTimes().size());
    }

    @TruffleLanguage.Registration(id = RecreateShadowStackTestLanguage.ID, name = "RecreateShadowStackTestLanguage", version = "0.1")
    @ProvidedTags({StandardTags.StatementTag.class, StandardTags.RootTag.class})
    public static class RecreateShadowStackTestLanguage extends TruffleLanguage<Integer> {

        public static final String ID = "RecreateShadowStackTestLanguage";

        @Override
        protected Integer createContext(Env env) {
            return 0;
        }

        @Override
        protected boolean isThreadAccessAllowed(Thread thread, boolean singleThreaded) {
            return true;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            com.oracle.truffle.api.source.Source source = request.getSource();
            SourceSection statement = null;
            SourceSection root = null;
            StatementNode startSamplerChild = null;

            String sourceString = source.getCharacters().toString();
            if (sourceString.equals("Statement Root")) {
                // Case when we want to test statements and roots
                statement = source.createSection(0, 9);
                root = source.createSection(10, 4);
                startSamplerChild = new StatementNode(statement, new SleepNode());
            } else if (sourceString.equals("Root Root")) {
                // Case when we want to test roots only
                statement = source.createUnavailableSection();
                root = source.createSection(0, 4);
                RootCallTarget sleepTarget = Truffle.getRuntime().createCallTarget(new SRootNode(this, new RootNode(root, new SleepNode())));
                startSamplerChild = new StatementNode(statement, new CallNode(Truffle.getRuntime().createDirectCallNode(sleepTarget)));
            } else {
                Assert.fail("Unsupported parse request.");
            }

            RootCallTarget innerTarget = Truffle.getRuntime().createCallTarget(
                            new SRootNode(this, new RootNode(root,
                                            new DummyNode(new StatementNode(statement,
                                                            new DummyNode(new StatementNode(statement, new DummyNode(new StartSamplerNode(sampler, startSamplerChild)))))))));
            DirectCallNode directCallNode = Truffle.getRuntime().createDirectCallNode(innerTarget);
            return Truffle.getRuntime().createCallTarget(
                            new SRootNode(this,
                                            new RootNode(root, new DummyNode(new StatementNode(statement, new DummyNode(new StatementNode(statement, new DummyNode(new CallNode(directCallNode)))))))));

        }

        abstract static class SamplerTestNode extends Node {
            public abstract Object execute(VirtualFrame frame);
        }

        @GenerateWrapper
        abstract static class SamplerTestInstrumentableNode extends SamplerTestNode implements InstrumentableNode {

            final SourceSection sourceSection;

            protected SamplerTestInstrumentableNode(SourceSection sourceSection) {
                this.sourceSection = sourceSection;
            }

            @Override
            public abstract Object execute(VirtualFrame frame);

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public SourceSection getSourceSection() {
                return sourceSection;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new SamplerTestInstrumentableNodeWrapper(sourceSection, this, probe);
            }
        }

        static class SleepNode extends SamplerTestNode {

            @Override
            public Object execute(VirtualFrame frame) {
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Assert.fail("Thread interrupted");
                }
                return 5;
            }
        }

        static class DummyNode extends SamplerTestNode {
            @Child SamplerTestNode node;

            DummyNode(SamplerTestNode node) {
                this.node = node;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(frame);
            }
        }

        static class StatementNode extends SamplerTestInstrumentableNode {

            @Child SamplerTestNode node;

            StatementNode(SourceSection sourceSection, SamplerTestNode node) {
                super(sourceSection);
                this.node = node;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.StatementTag.class;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(frame);
            }
        }

        static class RootNode extends SamplerTestInstrumentableNode {

            @Child SamplerTestNode node;

            RootNode(SourceSection sourceSection, SamplerTestNode node) {
                super(sourceSection);
                this.node = node;
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return tag == StandardTags.RootTag.class;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(frame);
            }
        }

        static class SRootNode extends com.oracle.truffle.api.nodes.RootNode {

            @Child SamplerTestNode child;

            SRootNode(RecreateShadowStackTestLanguage language, SamplerTestNode child) {
                super(language);
                this.child = child;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return child.execute(frame);
            }
        }

        static class CallNode extends SamplerTestNode {
            @Child DirectCallNode callNode;

            CallNode(DirectCallNode callNode) {
                this.callNode = callNode;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                return callNode.call(new Object[]{});
            }
        }

        static class StartSamplerNode extends SamplerTestNode {
            CPUSampler sampler;
            @Child SamplerTestNode child;

            StartSamplerNode(CPUSampler sampler, SamplerTestNode child) {
                this.sampler = sampler;
                this.child = child;
            }

            @Override
            public Object execute(VirtualFrame frame) {
                Assert.assertTrue("Found roots before enabling sampler", sampler.getRootNodes().isEmpty());
                sampler.setCollecting(true);
                return child.execute(frame);
            }
        }
    }

    @Test
    public void testCorrectInitShadowStackStatements() {
        sampler.setMode(CPUSampler.Mode.STATEMENTS);
        sampler.setFilter(NO_INTERNAL_STATEMENT_TAG_FILTER);
        Source test = Source.newBuilder(RecreateShadowStackTestLanguage.ID, "Statement Root", "test").buildLiteral();
        context.eval(test);
        Collection<ProfilerNode<CPUSampler.Payload>> rootNodes = sampler.getRootNodes();

        ProfilerNode<CPUSampler.Payload> current = rootNodes.iterator().next();
        current = checkStackState(current, "Statement", false);
        current = checkStackState(current, "Statement", false);
        current = checkStackState(current, "Statement", false);
        current = checkStackState(current, "Statement", false);
        checkStackState(current, "Statement", true);
    }

    private static ProfilerNode<CPUSampler.Payload> checkStackState(ProfilerNode<CPUSampler.Payload> current, String expectedSource, boolean top) {
        Assert.assertEquals("Stack not correct", expectedSource, current.getSourceSection().getCharacters().toString());
        if (top) {
            Assert.assertFalse("Stack too deep", current.getChildren().iterator().hasNext());
            return null;
        } else {
            Assert.assertTrue("Stack not deep enough", current.getChildren().iterator().hasNext());
            return current.getChildren().iterator().next();
        }

    }

    @Test
    public void testCorrectInitShadowStackRoots() {
        sampler.setMode(CPUSampler.Mode.ROOTS);
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        Source test = Source.newBuilder(RecreateShadowStackTestLanguage.ID, "Root Root", "test").buildLiteral();
        context.eval(test);
        Collection<ProfilerNode<CPUSampler.Payload>> rootNodes = sampler.getRootNodes();

        ProfilerNode<CPUSampler.Payload> current = rootNodes.iterator().next();
        current = checkStackState(current, "Root", false);
        current = checkStackState(current, "Root", false);
        checkStackState(current, "Root", true);
    }

    @Test
    public void testMultiThreadedRecursive() {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < executionCount; i++) {
                    eval(defaultSourceForSampling);
                }
            }
        };
        Runnable recursiveRunnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < executionCount; i++) {
                    eval(defaultRecursiveSourceForSampling);
                }
            }
        };
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
    public void testMultiThreaded() {
        sampler.setFilter(NO_INTERNAL_ROOT_TAG_FILTER);
        sampler.setCollecting(true);
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < executionCount; i++) {
                    eval(defaultSourceForSampling);
                }
            }
        };
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
}
