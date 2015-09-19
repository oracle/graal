/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;

import org.junit.Test;

import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.TruffleVM;
import com.oracle.truffle.tools.LineToProbesMap;
import com.oracle.truffle.tools.test.ToolTestUtil.ToolTestTag;

public class LineToProbesMapTest {

    @Test
    public void testNoExecution() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final TruffleVM vm = TruffleVM.newVM().build();
        final Field field = TruffleVM.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = ToolTestUtil.createTestSource("testNoExecution");
        final LineToProbesMap probesMap = new LineToProbesMap();
        probesMap.install(instrumenter);
        assertNull(probesMap.findFirstProbe(source.createLineLocation(1)));
        assertNull(probesMap.findFirstProbe(source.createLineLocation(2)));
        assertNull(probesMap.findFirstProbe(source.createLineLocation(3)));
        probesMap.dispose();
    }

    @Test
    public void testMapping1() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {
        final TruffleVM vm = TruffleVM.newVM().build();
        final Field field = TruffleVM.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = ToolTestUtil.createTestSource("testMapping1");
        final LineToProbesMap probesMap = new LineToProbesMap();

        assertNull(probesMap.findFirstProbe(source.createLineLocation(1)));
        assertNull(probesMap.findFirstProbe(source.createLineLocation(2)));
        assertNull(probesMap.findFirstProbe(source.createLineLocation(3)));

        // Map installed before AST gets created
        probesMap.install(instrumenter);
        assertEquals(vm.eval(source).get(), 13);

        final Probe probe1 = probesMap.findFirstProbe(source.createLineLocation(1));
        assertNotNull(probe1);
        assertTrue(probe1.isTaggedAs(ToolTestTag.VALUE_TAG));
        final Probe probe2 = probesMap.findFirstProbe(source.createLineLocation(2));
        assertNotNull(probe2);
        assertTrue(probe2.isTaggedAs(ToolTestTag.ADD_TAG));
        final Probe probe3 = probesMap.findFirstProbe(source.createLineLocation(3));
        assertNotNull(probe3);
        assertTrue(probe3.isTaggedAs(ToolTestTag.VALUE_TAG));

        probesMap.dispose();
    }

    @Test
    public void testMapping2() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, IOException {
        final TruffleVM vm = TruffleVM.newVM().build();
        final Field field = TruffleVM.class.getDeclaredField("instrumenter");
        field.setAccessible(true);
        final Instrumenter instrumenter = (Instrumenter) field.get(vm);
        final Source source = ToolTestUtil.createTestSource("testMapping2");
        final LineToProbesMap probesMap = new LineToProbesMap();

        assertNull(probesMap.findFirstProbe(source.createLineLocation(1)));
        assertNull(probesMap.findFirstProbe(source.createLineLocation(2)));
        assertNull(probesMap.findFirstProbe(source.createLineLocation(3)));

        // Map installed after AST gets created
        assertEquals(vm.eval(source).get(), 13);
        probesMap.install(instrumenter);

        final Probe probe1 = probesMap.findFirstProbe(source.createLineLocation(1));
        assertNotNull(probe1);
        assertTrue(probe1.isTaggedAs(ToolTestTag.VALUE_TAG));
        final Probe probe2 = probesMap.findFirstProbe(source.createLineLocation(2));
        assertNotNull(probe2);
        assertTrue(probe2.isTaggedAs(ToolTestTag.ADD_TAG));
        final Probe probe3 = probesMap.findFirstProbe(source.createLineLocation(3));
        assertNotNull(probe3);
        assertTrue(probe3.isTaggedAs(ToolTestTag.VALUE_TAG));

        probesMap.dispose();
    }
}
