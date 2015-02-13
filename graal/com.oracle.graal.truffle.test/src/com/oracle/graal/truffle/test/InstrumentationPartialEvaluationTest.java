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

import org.junit.*;

import com.oracle.graal.truffle.test.nodes.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.instrument.impl.*;

/**
 * Tests for a single simple PE test with various combinations of instrumentation attached. None of
 * the instrumentation ultimate does anything, so should compile away.
 */
public class InstrumentationPartialEvaluationTest extends PartialEvaluationTest {

    public static Object constant42() {
        return 42;
    }

    @Test
    public void constantValueUninstrumented() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        assertPartialEvalEquals("constant42", root);
    }

    @Ignore
    @Test
    public void constantValueProbedNoInstruments() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        result.probe();
        assertPartialEvalEquals("constant42", root);
    }

    @Ignore
    @Test
    public void constantValueProbedNullInstrument() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        Probe probe = result.probe();
        Instrument instrument = Instrument.create(new DefaultEventListener(), "Null test Instrument");
        probe.attach(instrument);
        assertPartialEvalEquals("constant42", root);
    }

    @Ignore
    @Test
    public void constantValueProbedNullInstrumentDisposed() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        Probe probe = result.probe();
        Instrument instrument = Instrument.create(new DefaultEventListener(), "Null test Instrument");
        probe.attach(instrument);
        instrument.dispose();
        assertPartialEvalEquals("constant42", root);
    }

    @Ignore
    @Test
    public void constantValueProbedTwoNullInstruments() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        Probe probe = result.probe();
        Instrument instrument1 = Instrument.create(new DefaultEventListener(), "Null test Instrument 1");
        probe.attach(instrument1);
        Instrument instrument2 = Instrument.create(new DefaultEventListener(), "Null test Instrument 2");
        probe.attach(instrument2);
        assertPartialEvalEquals("constant42", root);
    }

    @Ignore
    @Test
    public void constantValueProbedThreeNullInstruments() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        Probe probe = result.probe();
        Instrument instrument1 = Instrument.create(new DefaultEventListener(), "Null test Instrument 1");
        probe.attach(instrument1);
        Instrument instrument2 = Instrument.create(new DefaultEventListener(), "Null test Instrument 2");
        probe.attach(instrument2);
        Instrument instrument3 = Instrument.create(new DefaultEventListener(), "Null test Instrument 3");
        probe.attach(instrument3);
        assertPartialEvalEquals("constant42", root);
    }

    @Ignore
    @Test
    public void constantValueProbedThreeNullInstrumentsOneDisposed() {
        FrameDescriptor fd = new FrameDescriptor();
        AbstractTestNode result = new ConstantTestNode(42);
        RootTestNode root = new RootTestNode(fd, "constantValue", result);
        root.adoptChildren();
        Probe probe = result.probe();
        Instrument instrument1 = Instrument.create(new DefaultEventListener(), "Null test Instrument 1");
        probe.attach(instrument1);
        Instrument instrument2 = Instrument.create(new DefaultEventListener(), "Null test Instrument 2");
        probe.attach(instrument2);
        Instrument instrument3 = Instrument.create(new DefaultEventListener(), "Null test Instrument 3");
        probe.attach(instrument3);
        instrument2.dispose();
        assertPartialEvalEquals("constant42", root);
    }
}
