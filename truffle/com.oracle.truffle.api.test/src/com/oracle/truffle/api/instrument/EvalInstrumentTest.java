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
package com.oracle.truffle.api.instrument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrument.InstrumentationTestingLanguage.InstrumentTestTag;
import com.oracle.truffle.api.instrument.impl.DefaultProbeListener;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;

/**
 * Tests the kind of instrumentation where a client can provide guest language code to be
 * <em>spliced</em> directly into the AST.
 */
public class EvalInstrumentTest {

    PolyglotEngine vm;
    Instrumenter instrumenter;

    @Before
    public void before() {
        try {
            vm = PolyglotEngine.newBuilder().build();
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

    @After
    public void after() {
        vm.dispose();
        vm = null;
        instrumenter = null;
    }

    @Test
    public void testEvalInstrumentListener() throws IOException {

        instrumenter.registerASTProber(new InstrumentationTestingLanguage.TestASTProber());
        final Source source13 = InstrumentationTestingLanguage.createAdditionSource13("testEvalInstrumentListener");

        final Probe[] addNodeProbe = new Probe[1];
        instrumenter.addProbeListener(new DefaultProbeListener() {

            @Override
            public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
                if (tag == InstrumentTestTag.ADD_TAG) {
                    addNodeProbe[0] = probe;
                }
            }
        });
        assertEquals(vm.eval(source13).get(), 13);
        assertNotNull("Add node should be probed", addNodeProbe[0]);

        final Source source42 = InstrumentationTestingLanguage.createConstantSource42("testEvalInstrumentListener");
        final int[] evalResult = {0};
        final int[] evalCount = {0};
        final Instrument instrument = instrumenter.attach(addNodeProbe[0], source42, new EvalInstrumentListener() {

            public void onExecution(Node node, VirtualFrame frame, Object result) {
                evalCount[0] = evalCount[0] + 1;
                if (result instanceof Integer) {
                    evalResult[0] = (Integer) result;
                }
            }

            public void onFailure(Node node, VirtualFrame frame, Exception ex) {
                fail("Eval test evaluates without exception");

            }
        }, "test EvalInstrument", null);

        assertEquals(vm.eval(source13).get(), 13);
        assertEquals(evalCount[0], 1);
        assertEquals(evalResult[0], 42);

        // Second execution; same result
        assertEquals(vm.eval(source13).get(), 13);
        assertEquals(evalCount[0], 2);
        assertEquals(evalResult[0], 42);

        // Add new eval instrument with no listener, no effect on third execution
        instrumenter.attach(addNodeProbe[0], source42, null, "", null);
        assertEquals(vm.eval(source13).get(), 13);
        assertEquals(evalCount[0], 3);
        assertEquals(evalResult[0], 42);

        // Remove original instrument; no further effect from fourth execution
        instrument.dispose();
        evalResult[0] = 0;
        assertEquals(vm.eval(source13).get(), 13);
        assertEquals(evalCount[0], 3);
        assertEquals(evalResult[0], 0);
    }
}
