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
package com.oracle.truffle.api.test.instrument;

import static com.oracle.truffle.api.test.instrument.InstrumentationTestingLanguage.ADD_TAG;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.instrument.AdvancedInstrumentRoot;
import com.oracle.truffle.api.instrument.AdvancedInstrumentRootFactory;
import com.oracle.truffle.api.instrument.Instrument;
import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.instrument.ProbeListener;
import com.oracle.truffle.api.instrument.SyntaxTag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.test.instrument.InstrumentationTestNodes.TestAdvancedInstrumentCounterRoot;
import com.oracle.truffle.api.vm.TruffleVM;

/**
 * Tests the kind of instrumentation where a client can provide an AST fragment to be
 * <em>spliced</em> directly into the AST.
 */
public class AdvancedInstrumentTest {

    @Test
    public void testAdvancedInstrumentListener() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {

        final TruffleVM vm = TruffleVM.newVM().build();
        final Field field = TruffleVM.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = Source.fromText("testAdvancedInstrumentListener text", "testAdvancedInstrumentListener").withMimeType("text/x-instTest");

        final Probe[] addNodeProbe = new Probe[1];
        instrumenter.addProbeListener(new ProbeListener() {

            public void startASTProbing(Source s) {
            }

            public void newProbeInserted(Probe p) {
            }

            public void probeTaggedAs(Probe probe, SyntaxTag tag, Object tagValue) {
                if (tag == ADD_TAG) {
                    assertNull("only one add node", addNodeProbe[0]);
                    addNodeProbe[0] = probe;
                }
            }

            public void endASTProbing(Source s) {
            }

        });
        assertEquals(vm.eval(source).get(), 13);
        assertNotNull("Add node should be probed", addNodeProbe[0]);

        // Attach a null factory; it never actually attaches a node.
        final Instrument instrument = Instrument.create(null, new AdvancedInstrumentRootFactory() {

            public AdvancedInstrumentRoot createInstrumentRoot(Probe p, Node n) {
                return null;
            }
        }, null, "test AdvancedInstrument");
        addNodeProbe[0].attach(instrument);

        assertEquals(vm.eval(source).get(), 13);

        final TestAdvancedInstrumentCounterRoot counter = new TestAdvancedInstrumentCounterRoot();

        // Attach a factory that splices an execution counter into the AST.
        addNodeProbe[0].attach(Instrument.create(null, new AdvancedInstrumentRootFactory() {

            public AdvancedInstrumentRoot createInstrumentRoot(Probe p, Node n) {
                return counter;
            }
        }, null, "test AdvancedInstrument"));
        assertEquals(0, counter.getCount());
        assertEquals(vm.eval(source).get(), 13);
        assertEquals(1, counter.getCount());
    }
}
