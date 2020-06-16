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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.test.polyglot.ProxyLanguage;
import com.oracle.truffle.tools.profiler.MemoryTracer;
import com.oracle.truffle.tools.profiler.ProfilerNode;

public class MemoryTracerTest extends AbstractProfilerTest {

    private MemoryTracer tracer;

    @Before
    public void setupTracer() {
        tracer = MemoryTracer.find(context.getEngine());
        Assert.assertNotNull(tracer);
    }

    @Test
    public void testNoObjectAllocations() {
        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        eval(defaultSource);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 0, rootNodes.size());
    }

    @Test
    public void testNoObjectAllocationsRecursive() {
        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        eval(defaultRecursiveSource);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 0, rootNodes.size());
    }

    @Test
    public void testOneAllocationInRoot() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(STATEMENT))," + "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                        "DEFINE(baz,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(bar)))))," + "ALLOCATION,CALL(baz),CALL(bar)" + ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        eval(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 1, rootNodes.size());
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        if (node.getChildren() != null) {
            Assert.assertEquals("Nested allocations found!", 0, node.getChildren().size());
        }

        Assert.assertEquals("Incorect number of events!", 1, node.getPayload().getEvents().size());

        tracer.clearData();

        Assert.assertFalse(tracer.hasData());
    }

    @Test
    public void testOneAllocationInRootRecursive() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(BLOCK(STATEMENT,RECURSIVE_CALL(foo, 10))))," + "DEFINE(bar,ROOT(BLOCK(STATEMENT,LOOP(10, CALL(foo)))))," +
                        "ALLOCATION,CALL(bar)" +
                        ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        eval(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        Assert.assertEquals("More allocations found", 1, rootNodes.size());
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        if (node.getChildren() != null) {
            Assert.assertEquals("Nested allocations found!", 0, node.getChildren().size());
        }

        Assert.assertEquals("Incorect number of events!", 1, node.getPayload().getEvents().size());

        tracer.clearData();

        Assert.assertFalse(tracer.hasData());
    }

    @Test
    public void testMultipleAllocation() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(ALLOCATION,STATEMENT))," + "DEFINE(bar,ROOT(BLOCK(ALLOCATION,STATEMENT,LOOP(10, CALL(foo)))))," +
                        "DEFINE(baz,ROOT(BLOCK(ALLOCATION,STATEMENT,LOOP(10, CALL(bar)))))," + "ALLOCATION,CALL(baz),CALL(bar)" + ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        eval(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        // ROOT
        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        Assert.assertEquals("Incorrect number of allocations found", 123, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // BAZ
        node = node.getChildren().iterator().next();
        if (!node.getRootName().equals("baz")) {
            node = node.getChildren().iterator().next();
        }
        Assert.assertEquals("Incorrect number of allocations found", 111, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // BAR (from BAZ, not the ROOT)
        node = node.getChildren().iterator().next();
        Assert.assertEquals("Incorrect number of allocations found", 110, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 10, node.getPayload().getEvents().size());

        // BAR (from BAZ, not the ROOT)
        node = node.getChildren().iterator().next();
        Assert.assertEquals("Incorrect number of allocations found", 100, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 100, node.getPayload().getEvents().size());

        Assert.assertTrue("Children too deep found!",
                        node.getChildren() == null || node.getChildren().isEmpty());

    }

    @Test
    public void testMultipleAllocationRecursive() {
        final String oneAllocationSource = "ROOT(" + "DEFINE(foo,ROOT(BLOCK(ALLOCATION,STATEMENT,RECURSIVE_CALL(foo, 10))))," + "DEFINE(bar,ROOT(BLOCK(ALLOCATION,STATEMENT,LOOP(10, CALL(foo)))))," +
                        "ALLOCATION,CALL(bar)" + ")";

        Assert.assertFalse(tracer.isCollecting());

        tracer.setCollecting(true);

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertFalse(tracer.hasData());

        eval(makeSource(oneAllocationSource));

        Assert.assertTrue(tracer.isCollecting());
        Assert.assertTrue(tracer.hasData());

        final int totalAllocationsExpected = 112;

        // ROOT
        Collection<ProfilerNode<MemoryTracer.Payload>> rootNodes = tracer.getRootNodes();
        ProfilerNode<MemoryTracer.Payload> node = rootNodes.iterator().next();
        Assert.assertEquals("Incorrect number of allocations found",
                        totalAllocationsExpected, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // BAR
        node = node.getChildren().iterator().next();
        Assert.assertEquals("Incorrect number of allocations found",
                        totalAllocationsExpected - 1, node.getPayload().getTotalAllocations());
        Assert.assertEquals("Incorrect number of events found", 1, node.getPayload().getEvents().size());

        // FOO - 11 times because RECURSIVE_CALL goes to recursion depth of 10
        for (int i = 0; i < 11; i++) {
            node = node.getChildren().iterator().next();
            Assert.assertEquals("Incorrect number of allocations found",
                            totalAllocationsExpected - 2 - (10 * i), node.getPayload().getTotalAllocations());
            Assert.assertEquals("Incorrect number of events found", 10, node.getPayload().getEvents().size());
        }
        Assert.assertTrue("Children too deep found!",
                        node.getChildren() == null || node.getChildren().isEmpty());
    }

    @TruffleLanguage.Registration(id = AllocatesDuringReportingAllocation.ID, name = "AllocatesDuringReportingAllocation", version = "1.0")
    @ProvidedTags({StandardTags.RootTag.class})
    public static class AllocatesDuringReportingAllocation extends ProxyLanguage {

        @GenerateWrapper
        static class ADRANode extends Node implements InstrumentableNode {

            @Override
            public SourceSection getSourceSection() {
                return getRootNode().getSourceSection();
            }

            @Override
            public boolean isInstrumentable() {
                return true;
            }

            @Override
            public WrapperNode createWrapper(ProbeNode probe) {
                return new ADRANodeWrapper(this, probe);
            }

            @Override
            public boolean hasTag(Class<? extends Tag> tag) {
                return StandardTags.RootTag.class == tag;
            }

            @SuppressWarnings("unused")
            public Object execute(VirtualFrame frame) {
                final AllocationReporter allocationReporter = AllocatesDuringReportingAllocation.getCurrentContext(AllocatesDuringReportingAllocation.class).getEnv().lookup(AllocationReporter.class);
                allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
                allocationReporter.onReturnValue("", 0, AllocationReporter.SIZE_UNKNOWN);
                return "";
            }
        }

        static class ADRARootNode extends RootNode {

            SourceSection section;

            void setSection(SourceSection section) {
                this.section = section;
            }

            ADRARootNode(TruffleLanguage<?> language) {
                super(language);
            }

            @Override
            public SourceSection getSourceSection() {
                return section;
            }

            @Child ADRANode node = new ADRANode();

            @Override
            public Object execute(VirtualFrame frame) {
                return node.execute(frame);
            }
        }

        static final String ID = "AllocatesDuringReportingAllocation";

        @Override
        protected CallTarget parse(ParsingRequest request) {
            final ADRARootNode rootNode = new ADRARootNode(this);
            rootNode.setSection(request.getSource().createSection(1));
            return Truffle.getRuntime().createCallTarget(rootNode);
        }

        @Override
        protected String toString(LanguageContext context, Object value) {
            final AllocationReporter allocationReporter = AllocatesDuringReportingAllocation.getCurrentContext(AllocatesDuringReportingAllocation.class).getEnv().lookup(AllocationReporter.class);
            allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
            allocationReporter.onReturnValue("", 0, AllocationReporter.SIZE_UNKNOWN);
            return "";
        }
    }

    @Test
    public void testAllocateDuringAllocationReport() {
        Context c = Context.create(AllocatesDuringReportingAllocation.ID);
        tracer = MemoryTracer.find(c.getEngine());
        tracer.setCollecting(true);
        c.eval(Source.newBuilder(AllocatesDuringReportingAllocation.ID, "", "").buildLiteral());
    }
}
