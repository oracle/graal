/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.api.instrument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.InstrumentationTestNodes.TestAdditionNode;
import com.oracle.truffle.api.instrument.InstrumentationTestNodes.TestLanguageNode;
import com.oracle.truffle.api.instrument.InstrumentationTestNodes.TestValueNode;
import com.oracle.truffle.api.instrument.InstrumentationTestingLanguage.InstrumentTestTag;
import com.oracle.truffle.api.instrument.impl.DefaultProbeListener;
import com.oracle.truffle.api.instrument.impl.DefaultSimpleInstrumentListener;
import com.oracle.truffle.api.instrument.impl.DefaultStandardInstrumentListener;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * <h3>AST Instrumentation</h3>
 *
 * Instrumentation allows the insertion into Truffle ASTs language-specific instances of
 * {@link WrapperNode} that propagate execution events through a {@link Probe} to any instances of
 * {@link ProbeInstrument} that might be attached to the particular probe by tools.
 */
public class InstrumentationTest {

    @Test
    public void testProbing() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {
        final PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        final Field field = PolyglotEngine.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        instrumenter.registerASTProber(new TestASTProber(instrumenter));
        final Source source = InstrumentationTestingLanguage.createAdditionSource13("testProbing");

        final Probe[] probes = new Probe[3];
        instrumenter.addProbeListener(new DefaultProbeListener() {

            @Override
            public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
                if (tag == InstrumentTestTag.ADD_TAG) {
                    assertEquals(probes[0], null);
                    probes[0] = probe;
                } else if (tag == InstrumentTestTag.VALUE_TAG) {
                    if (probes[1] == null) {
                        probes[1] = probe;
                    } else if (probes[2] == null) {
                        probes[2] = probe;
                    } else {
                        fail("Should only be three probes");
                    }
                }
            }
        });
        assertEquals(vm.eval(source).get(), 13);
        assertNotNull("Add node should be probed", probes[0]);
        assertNotNull("Value nodes should be probed", probes[1]);
        assertNotNull("Value nodes should be probed", probes[2]);
        // Check instrumentation with the simplest kind of counters.
        // They should all be removed when the check is finished.
        checkCounters(probes[0], vm, source, new TestSimpleInstrumentCounter(instrumenter), new TestSimpleInstrumentCounter(instrumenter), new TestSimpleInstrumentCounter(instrumenter));

        // Now try with the more complex flavor of listener
        checkCounters(probes[0], vm, source, new TestStandardInstrumentCounter(instrumenter), new TestStandardInstrumentCounter(instrumenter), new TestStandardInstrumentCounter(instrumenter));

    }

    @Test
    public void testTagging() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {

        final PolyglotEngine vm = PolyglotEngine.newBuilder().build();
        final Field field = PolyglotEngine.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = InstrumentationTestingLanguage.createAdditionSource13("testTagging");

        // Applies appropriate tags
        final TestASTProber astProber = new TestASTProber(instrumenter);
        instrumenter.registerASTProber(astProber);

        // Listens for probes and tags being added
        final TestProbeListener probeListener = new TestProbeListener();
        instrumenter.addProbeListener(probeListener);

        assertEquals(13, vm.eval(source).get());

        // Check that the prober added probes to the tree
        assertEquals(probeListener.probeCount, 3);
        assertEquals(probeListener.tagCount, 3);

        assertEquals(instrumenter.findProbesTaggedAs(InstrumentTestTag.ADD_TAG).size(), 1);
        assertEquals(instrumenter.findProbesTaggedAs(InstrumentTestTag.VALUE_TAG).size(), 2);
    }

    private static void checkCounters(Probe probe, PolyglotEngine vm, Source source, TestCounter counterA, TestCounter counterB, TestCounter counterC) throws IOException {

        // Attach a counting instrument to the probe
        counterA.attach(probe);

        // Attach a second counting instrument to the probe
        counterB.attach(probe);

        // Run it again and check that the two instruments are working
        assertEquals(13, vm.eval(source).get());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 1);
        assertEquals(counterB.leaveCount(), 1);

        // Remove counterA
        counterA.dispose();

        // Run it again and check that instrument B is still working but not A
        assertEquals(13, vm.eval(source).get());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 2);
        assertEquals(counterB.leaveCount(), 2);

        // Attach a second instrument to the probe
        counterC.attach(probe);

        // Run the original and check that instruments B,C working but not A
        assertEquals(13, vm.eval(source).get());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 3);
        assertEquals(counterB.leaveCount(), 3);
        assertEquals(counterC.enterCount(), 1);
        assertEquals(counterC.leaveCount(), 1);

        // Remove instrumentC
        counterC.dispose();

        // Run the original and check that instrument B working but not A,C
        assertEquals(13, vm.eval(source).get());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 4);
        assertEquals(counterB.leaveCount(), 4);
        assertEquals(counterC.enterCount(), 1);
        assertEquals(counterC.leaveCount(), 1);

        // Remove instrumentB
        counterB.dispose();

        // Check that no instruments working
        assertEquals(13, vm.eval(source).get());
        assertEquals(counterA.enterCount(), 1);
        assertEquals(counterA.leaveCount(), 1);
        assertEquals(counterB.enterCount(), 4);
        assertEquals(counterB.leaveCount(), 4);
        assertEquals(counterC.enterCount(), 1);
        assertEquals(counterC.leaveCount(), 1);
    }

    private interface TestCounter {

        int enterCount();

        int leaveCount();

        void attach(Probe probe);

        void dispose();
    }

    /**
     * A counter for the number of times execution enters and leaves a probed AST node.
     */
    private class TestSimpleInstrumentCounter implements TestCounter {

        public int enterCount = 0;
        public int leaveCount = 0;
        public Instrumenter instrumenter;
        private ProbeInstrument instrument;

        TestSimpleInstrumentCounter(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
        }

        @Override
        public int enterCount() {
            return enterCount;
        }

        @Override
        public int leaveCount() {
            return leaveCount;
        }

        @Override
        public void attach(Probe probe) {
            instrument = instrumenter.attach(probe, new SimpleInstrumentListener() {

                public void onEnter(Probe p) {
                    enterCount++;
                }

                public void onReturnVoid(Probe p) {
                    leaveCount++;
                }

                public void onReturnValue(Probe p, Object result) {
                    leaveCount++;
                }

                public void onReturnExceptional(Probe p, Throwable exception) {
                    leaveCount++;
                }
            }, "Instrumentation Test Counter");
        }

        @Override
        public void dispose() {
            instrument.dispose();
        }
    }

    /**
     * A counter for the number of times execution enters and leaves a probed AST node.
     */
    private class TestStandardInstrumentCounter implements TestCounter {

        public int enterCount = 0;
        public int leaveCount = 0;
        public final Instrumenter instrumenter;
        public ProbeInstrument instrument;

        TestStandardInstrumentCounter(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
        }

        @Override
        public int enterCount() {
            return enterCount;
        }

        @Override
        public int leaveCount() {
            return leaveCount;
        }

        @Override
        public void attach(Probe probe) {
            instrument = instrumenter.attach(probe, new StandardInstrumentListener() {

                public void onEnter(Probe p, Node node, VirtualFrame vFrame) {
                    enterCount++;
                }

                public void onReturnVoid(Probe p, Node node, VirtualFrame vFrame) {
                    leaveCount++;
                }

                public void onReturnValue(Probe p, Node node, VirtualFrame vFrame, Object result) {
                    leaveCount++;
                }

                public void onReturnExceptional(Probe p, Node node, VirtualFrame vFrame, Throwable exception) {
                    leaveCount++;
                }
            }, "Instrumentation Test Counter");
        }

        @Override
        public void dispose() {
            instrument.dispose();
        }
    }

    /**
     * Tags selected nodes on newly constructed ASTs.
     */
    private static final class TestASTProber implements NodeVisitor, ASTProber {

        private final Instrumenter instrumenter;

        TestASTProber(Instrumenter instrumenter) {
            this.instrumenter = instrumenter;
        }

        @Override
        public boolean visit(Node node) {
            if (node instanceof TestLanguageNode) {

                final TestLanguageNode testNode = (TestLanguageNode) node;

                if (node instanceof TestValueNode) {
                    instrumenter.probe(testNode).tagAs(InstrumentTestTag.VALUE_TAG, null);

                } else if (node instanceof TestAdditionNode) {
                    instrumenter.probe(testNode).tagAs(InstrumentTestTag.ADD_TAG, null);

                }
            }
            return true;
        }

        @Override
        public void probeAST(Instrumenter inst, RootNode rootNode) {
            rootNode.accept(this);
        }
    }

    /**
     * Counts the number of "enter" events at probed nodes using the simplest AST listener.
     */
    static final class TestSimpleInstrumentListener extends DefaultSimpleInstrumentListener {

        public int counter = 0;

        @Override
        public void onEnter(Probe probe) {
            counter++;
        }
    }

    /**
     * Counts the number of "enter" events at probed nodes using the AST listener.
     */
    static final class TestASTInstrumentListener extends DefaultStandardInstrumentListener {

        public int counter = 0;

        @Override
        public void onEnter(Probe probe, Node node, VirtualFrame vFrame) {
            counter++;
        }
    }

    private static final class TestProbeListener extends DefaultProbeListener {

        public int probeCount = 0;
        public int tagCount = 0;

        @Override
        public void newProbeInserted(Probe probe) {
            probeCount++;
        }

        @Override
        public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
            tagCount++;
        }
    }
}
