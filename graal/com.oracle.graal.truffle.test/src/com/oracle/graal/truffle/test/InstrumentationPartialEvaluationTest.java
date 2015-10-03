/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.test;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.oracle.graal.truffle.test.InstrumentationPETestLanguage.TestRootNode;
import com.oracle.truffle.api.instrument.ASTProber;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.impl.DefaultSimpleInstrumentListener;
import com.oracle.truffle.api.instrument.impl.DefaultStandardInstrumentListener;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

// TODO add some tests for the replacement for AdvancedInstrumentRootFactory

/**
 * Partial evaluation tests on a constant valued RootNode with various instrumentation operations
 * applied to it. None of the instrumentation ultimately does anything, so should compile away.
 * <p>
 * A specialized test language produces a root with a single constant-valued child, no matter what
 * source is eval'd.
 * <p>
 * Taking care to avoid creating sources that appear equal so there won't be any sharing in the
 * engine.
 */
public class InstrumentationPartialEvaluationTest extends PartialEvaluationTest {

    PolyglotEngine vm;
    Instrumenter instrumenter;

    public static Object constant42() {
        return 42;
    }

    @Before
    public void before() {
        // TODO (mlvdv) eventually abstract this
        try {
            vm = PolyglotEngine.buildNew().build();
            final Field field = PolyglotEngine.class.getDeclaredField("instrumenter");
            field.setAccessible(true);
            instrumenter = (Instrumenter) field.get(vm);
            final java.lang.reflect.Field testVMField = Instrumenter.class.getDeclaredField("testVM");
            testVMField.setAccessible(true);
            testVMField.set(instrumenter, vm);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException ex) {
            fail("Reflective access to Instrumenter for testing");
        }
    }

    @Override
    @After
    public void after() {
        vm.dispose();
        vm = null;
        instrumenter = null;
        super.after();
    }

    // TODO (mlvdv) fix other PE tests; move down the before/after setup

    @Test
    public void uninstrumented() throws IOException {

        final Source source = Source.fromText("uninstrumented", "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);
                root[0] = rootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedNoListeners() throws IOException {
        final String testName = "probedNoListeners";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node, but attach no listeners
                instrumenter.probe(testRootNode.getBody());

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithNullSimpleListener() throws IOException {
        final String testName = "probedWithNullSimpleListener";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach a "simple" empty listener
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithNullStandardListener() throws IOException {
        final String testName = "probedWithNullStandardListener";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach a "standard" empty listener
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithNullSimpleListenerDisposed() throws IOException {
        final String testName = "probedWithNullSimpleListenerDisposed";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach a "simple" empty listener
                final Instrument instrument = instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                // Detach the listener
                instrument.dispose();

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithNullStandardListenerDisposed() throws IOException {
        final String testName = "probedWithNullStandardListenerDisposed";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach a "standard" empty listener
                final Instrument instrument = instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                // Detach the listener
                instrument.dispose();

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithTwoSimpleListeners() throws IOException {
        final String testName = "probedWithTwoSimpleListeners";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach two "simple" empty listeners
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithTwoStandardListeners() throws IOException {
        final String testName = "probedWithTwoStandardListeners";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRotoNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRotoNode.getBody());
                // Attach two "standard" empty listeners
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);

                root[0] = testRotoNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithThreeSimpleListeners() throws IOException {
        final String testName = "probedWithThreeSimpleListeners";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach three "simple" empty listeners
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithThreeStandardListeners() throws IOException {
        final String testName = "probedWithThreeStandardListeners";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach three "standard" empty listeners
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithThreeSimpleListenersOneDisposed() throws IOException {
        final String testName = "probedWithThreeSimpleListenersOneDisposed";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        // Abuse instrumentation to get a copy of the root node before execution
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach three "simple" empty listeners
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                final Instrument disposeMe = instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultSimpleInstrumentListener(), testName);
                disposeMe.dispose();

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    @Test
    public void probedWithThreeStandardListenersOneDisposed() throws IOException {
        final String testName = "probedWithThreeStandardListenersOneDisposed";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        instrumenter.registerASTProber(new ASTProber() {

            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                final Probe probe = instrumenter.probe(testRootNode.getBody());
                // Attach three "standard" empty listeners
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                final Instrument disposeMe = instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                instrumenter.attach(probe, new DefaultStandardInstrumentListener(), testName);
                disposeMe.dispose();

                root[0] = testRootNode;
            }
        });
        Assert.assertEquals(vm.eval(source).get(), 42);
        assertPartialEvalEquals("constant42", root[0]);
    }

    /**
     * Sketch of how a test for deopt might work.
     */
    @Test
    public void instrumentDeopt() throws IOException {

        final String testName = "instrumentDeopt";
        final Source source = Source.fromText(testName, "any text").withMimeType("text/x-instPETest");
        final RootNode[] root = new RootNode[1];
        final Probe[] probe = new Probe[1];
        final int[] count = {0};

        // Register a "prober" that will get applied when CallTarget gets created.
        instrumenter.registerASTProber(new ASTProber() {

            @Override
            public void probeAST(Instrumenter inst, RootNode rootNode) {
                instrumenter.unregisterASTProber(this);

                final TestRootNode testRootNode = (TestRootNode) rootNode;
                // Probe the value node
                probe[0] = instrumenter.probe(testRootNode.getBody());
                root[0] = testRootNode;
            }
        });

        Assert.assertEquals(vm.eval(source).get(), 42);
        Assert.assertEquals(0, count[0]); // Didn't count anything

        // Add a counting instrument; this changes the "Probe state" and should cause a deopt
        final Instrument countingInstrument = instrumenter.attach(probe[0], new DefaultSimpleInstrumentListener() {

            @Override
            public void onEnter(Probe p) {
                count[0] = count[0] + 1;
            }
        }, testName);

        Assert.assertEquals(vm.eval(source).get(), 42);
        Assert.assertEquals(1, count[0]); // Counted the first call

        // Remove the counting instrument; this changes the "Probe state" and should cause a
        // deopt
        countingInstrument.dispose();

        Assert.assertEquals(vm.eval(source).get(), 42);
        Assert.assertEquals(1, count[0]); // Didn't count this time

    }
}
