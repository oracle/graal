/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.coverage.test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.test.InstrumentationTestLanguage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.coverage.CoverageTracker;
import com.oracle.truffle.tools.coverage.RootCoverage;
import com.oracle.truffle.tools.coverage.SectionCoverage;
import com.oracle.truffle.tools.coverage.SourceCoverage;
import com.oracle.truffle.tools.coverage.impl.CoverageInstrument;

public final class CoverageTest {

    private static final String defaultSourceString = "ROOT(\n" +
                    "DEFINE(foo,ROOT(SLEEP(1))),\n" +
                    "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo))))),\n" +
                    "DEFINE(neverCalled,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar))))),\n" +
                    "CALL(bar)\n" +
                    ")";
    private static final Source defaultSource = makeSource(defaultSourceString);
    private ByteArrayOutputStream out;
    private ByteArrayOutputStream err;

    private static Source makeSource(String s) {
        return Source.newBuilder(InstrumentationTestLanguage.ID, s, "test").buildLiteral();
    }

    private static void assertCoverage(RootCoverage root, int expectedLoaded, int expectedCovered, String name, boolean covered) {
        Assert.assertEquals("Wrong root name!", name, root.getName());
        Assert.assertEquals("Unexpected \"" + name + "\" root coverage", covered, root.isCovered());
        final SectionCoverage[] sectionCoverage = root.getSectionCoverage();
        Assert.assertEquals("Unexpected number of statements loaded ", expectedLoaded, sectionCoverage.length);
        Assert.assertEquals("Unexpected number of statements covered", expectedCovered, countCovered(sectionCoverage));
    }

    private static int countCovered(SectionCoverage[] sectionCoverage) {
        int count = 0;
        for (SectionCoverage coverage : sectionCoverage) {
            if (coverage.isCovered()) {
                count++;
            }
        }
        return count;
    }

    private static SourceCoverage[] execInServiceAndGetCoverage(Context context, ExecutorService executorService, CoverageTracker tracker) throws InterruptedException, ExecutionException {
        List<Future<Value>> tasks = new ArrayList<>(2);
        tasks.add(executorService.submit(() -> context.eval(defaultSource)));
        for (Future<Value> task : tasks) {
            task.get();
        }
        return tracker.getCoverage();
    }

    @Before
    public void setUp() {
        out = new ByteArrayOutputStream();
        err = new ByteArrayOutputStream();
    }

    @Test
    public void testBasic() {
        try (Context context = Context.newBuilder().in(System.in).out(out).err(err).option(CoverageInstrument.ID, "true").build()) {
            context.eval(defaultSource);
            final CoverageTracker tracker = CoverageInstrument.getTracker(context.getEngine());
            final SourceCoverage[] coverage = tracker.getCoverage();
            Assert.assertEquals("Unexpected number of sources in coverage", 1, coverage.length);
            Assert.assertEquals("Unexpected number of roots in coverage", 4, coverage[0].getRoots().length);
            for (RootCoverage root : coverage[0].getRoots()) {
                switch (root.getName()) {
                    case "foo":
                        assertCoverage(root, 0, 0, "foo", true);
                        break;
                    case "bar":
                        assertCoverage(root, 1, 1, "bar", true);
                        break;
                    case "neverCalled":
                        assertCoverage(root, 1, 0, "neverCalled", false);
                        break;
                    case "":
                        assertCoverage(root, 0, 0, "", true);
                        break;
                }
            }
        }
    }

    @Test
    public void testMultiThreaded() throws InterruptedException, ExecutionException {
        try (Context context = Context.newBuilder().in(System.in).out(out).err(err).build()) {
            ExecutorService executorService = Executors.newFixedThreadPool(2);
            final CoverageTracker tracker = CoverageInstrument.getTracker(context.getEngine());
            // Is the coverage empty when not tracking?
            Assert.assertEquals(0, execInServiceAndGetCoverage(context, executorService, tracker).length);
            // Start tracking
            tracker.start(new CoverageTracker.Config(SourceSectionFilter.ANY, true));
            // Is the coverage non-empty when tracking?
            final SourceCoverage[] initial = execInServiceAndGetCoverage(context, executorService, tracker);
            Assert.assertEquals(1, initial.length);
            // Is the coverage growing?
            final SourceCoverage[] grown = execInServiceAndGetCoverage(context, executorService, tracker);
            Assert.assertEquals(1, grown.length);
            Assert.assertTrue(grown[0].getRoots()[0].getCount() >= initial[0].getRoots()[0].getCount());
            // Does the coverage stop growing when we end?
            tracker.end();
            final SourceCoverage[] afterEnd = execInServiceAndGetCoverage(context, executorService, tracker);
            final SourceCoverage[] afterEnd2 = execInServiceAndGetCoverage(context, executorService, tracker);
            Assert.assertEquals(afterEnd[0].getRoots()[0].getCount(), afterEnd2[0].getRoots()[0].getCount());
        }
    }

    @Test
    public void testRootAndStatementInDifferentSources() {
        try (Context c = Context.newBuilder().in(System.in).out(out).err(err).build();
                        CoverageTracker tracker = CoverageInstrument.getTracker(c.getEngine())) {
            tracker.start(new CoverageTracker.Config(SourceSectionFilter.ANY, false));
            c.eval(RootAndStatementInDifferentSources.ID, "");
            final SourceCoverage[] coverage = tracker.getCoverage();
            Assert.assertEquals(2, coverage.length);
            for (SourceCoverage sourceCoverage : coverage) {
                if (sourceCoverage.getSource().equals(RootAndStatementInDifferentSources.rootSource)) {
                    Assert.assertEquals(1, sourceCoverage.getRoots().length);
                    final RootCoverage rootCoverage = sourceCoverage.getRoots()[0];
                    Assert.assertTrue(rootCoverage.isCovered());
                    Assert.assertEquals(0, rootCoverage.getSectionCoverage().length);
                }
                if (sourceCoverage.getSource().equals(RootAndStatementInDifferentSources.statementSource)) {
                    Assert.assertEquals(1, sourceCoverage.getRoots().length);
                    final RootCoverage rootCoverage = sourceCoverage.getRoots()[0];
                    Assert.assertFalse(rootCoverage.isCovered());
                    Assert.assertEquals(1, rootCoverage.getSectionCoverage().length);
                    final SectionCoverage sectionCoverage = rootCoverage.getSectionCoverage()[0];
                    Assert.assertTrue(sectionCoverage.isCovered());
                }
            }
        }
    }

    @Test
    public void rootSourceSectionChanges() {
        try (Context c = Context.newBuilder().in(System.in).out(out).err(err).build();
                        CoverageTracker tracker = CoverageInstrument.getTracker(c.getEngine())) {
            tracker.start(new CoverageTracker.Config(SourceSectionFilter.ANY, false));
            c.eval(RootSourceChanges.ID, "");
            final SourceCoverage[] coverage = tracker.getCoverage();
            Assert.assertTrue(coverage.length > 0);
        }
    }

    @Test
    public void testResetCoverage() {
        try (Context context = Context.newBuilder().in(System.in).out(out).err(err).option(CoverageInstrument.ID, "true").build();
                        CoverageTracker tracker = CoverageInstrument.getTracker(context.getEngine())) {
            for (int i = 0; i < 10; i++) {
                context.eval(defaultSource);
                final SourceCoverage[] coverage = tracker.resetCoverage();
                Assert.assertEquals("Unexpected number of sources in coverage", 1, coverage.length);
                Assert.assertEquals("Unexpected number of roots in coverage", 4, coverage[0].getRoots().length);
                for (RootCoverage root : coverage[0].getRoots()) {
                    switch (root.getName()) {
                        case "foo":
                            assertCoverage(root, 0, 0, "foo", true);
                            break;
                        case "bar":
                            assertCoverage(root, 1, 1, "bar", true);
                            break;
                        case "neverCalled":
                            assertCoverage(root, 1, 0, "neverCalled", false);
                            break;
                        case "":
                            assertCoverage(root, 0, 0, "", true);
                            break;
                    }
                }
                SourceCoverage[] resetCoverage = tracker.getCoverage();
                for (RootCoverage root : resetCoverage[0].getRoots()) {
                    switch (root.getName()) {
                        case "foo":
                            assertCoverage(root, 0, 0, "foo", false);
                            break;
                        case "bar":
                            assertCoverage(root, 1, 0, "bar", false);
                            break;
                        case "neverCalled":
                            assertCoverage(root, 1, 0, "neverCalled", false);
                            break;
                        case "":
                            assertCoverage(root, 0, 0, "", false);
                            break;
                    }
                }
            }
        }
    }

    @TruffleLanguage.Registration(id = RootAndStatementInDifferentSources.ID, name = "RootAndStatementInDifferentSources", version = "0")
    @ProvidedTags({StandardTags.RootTag.class, StandardTags.StatementTag.class})
    public static class RootAndStatementInDifferentSources extends ProxyLanguage {

        public static final String ID = "RootAndStatementInDifferentSources";
        static final com.oracle.truffle.api.source.Source rootSource = com.oracle.truffle.api.source.Source.newBuilder(RootAndStatementInDifferentSources.ID, "for use in root", "root").build();
        static final com.oracle.truffle.api.source.Source statementSource = com.oracle.truffle.api.source.Source.newBuilder(RootAndStatementInDifferentSources.ID, "for use in statement",
                        "statement").build();

        public RootAndStatementInDifferentSources() {
            wrapper = false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {
                @Child CoverageTest.SuperclassNode child = new CoverageTest.TestRootNode(new CoverageTest.TestStatementNode());

                @Override
                public Object execute(VirtualFrame frame) {
                    return child.execute(frame);
                }
            }.getCallTarget();
        }

    }

    @TruffleLanguage.Registration(id = RootSourceChanges.ID, name = "RootSourceChanges", version = "0")
    @ProvidedTags({StandardTags.RootTag.class})
    public static class RootSourceChanges extends ProxyLanguage {
        public static final String ID = "RootSourceChanges";

        static final com.oracle.truffle.api.source.Source rootNodeSource = com.oracle.truffle.api.source.Source.newBuilder(
                        RootSourceChanges.ID, "for use in root", "root").build();
        static final com.oracle.truffle.api.source.Source rootNodeSource2 = com.oracle.truffle.api.source.Source.newBuilder(
                        RootAndStatementInDifferentSources.ID, "Another root", "root2").build();

        public RootSourceChanges() {
            this.wrapper = false;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            return new RootNode(this) {
                final SourceSection sourceSection = rootNodeSource.createSection(1);

                @Override
                public SourceSection getSourceSection() {
                    return sourceSection;
                }

                @Child SuperclassNode child = new TestChangesSourceRootNode(new TestStatementNode());

                @Override
                public Object execute(VirtualFrame frame) {
                    return child.execute(frame);
                }
            }.getCallTarget();
        }
    }

    public static class TestChangesSourceRootNode extends SuperclassNode {
        static boolean otherSource = false;

        private final SourceSection section = RootSourceChanges.rootNodeSource.createSection(1);
        private final SourceSection section2 = RootSourceChanges.rootNodeSource2.createSection(1);

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == StandardTags.RootTag.class;
        }

        TestChangesSourceRootNode(TestStatementNode testStatementNode) {
            node = testStatementNode;
        }

        @Override
        public SourceSection getSourceSection() {
            if (otherSource) {
                return section2;
            }
            otherSource = true;
            return section;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return super.execute(frame);
        }
    }

    @GenerateWrapper
    static class SuperclassNode extends Node implements InstrumentableNode {

        @Child SuperclassNode node;

        @Override
        public boolean isInstrumentable() {
            return true;
        }

        @Override
        public WrapperNode createWrapper(ProbeNode probe) {
            return new SuperclassNodeWrapper(this, probe);
        }

        public Object execute(VirtualFrame frame) {
            if (node == null) {
                return 1;
            }
            return node.execute(frame);
        }
    }

    static class TestRootNode extends SuperclassNode {

        private final SourceSection section;

        TestRootNode(TestStatementNode testStatementNode) {
            node = testStatementNode;
            section = RootAndStatementInDifferentSources.rootSource.createSection(1);
        }

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == StandardTags.RootTag.class;
        }

        @Override
        public SourceSection getSourceSection() {
            return section;
        }
    }

    static class TestStatementNode extends SuperclassNode {

        TestStatementNode() {
            this.section = RootAndStatementInDifferentSources.statementSource.createSection(1);
        }

        private final SourceSection section;

        @Override
        public boolean hasTag(Class<? extends Tag> tag) {
            return tag == StandardTags.StatementTag.class;
        }

        @Override
        public SourceSection getSourceSection() {
            return section;
        }
    }
}
