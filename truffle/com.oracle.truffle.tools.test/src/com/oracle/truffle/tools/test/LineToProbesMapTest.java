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

import static com.oracle.truffle.tools.test.TestNodes.createExpr13TestRootNode;
import static com.oracle.truffle.tools.test.TestNodes.expr13Line1;
import static com.oracle.truffle.tools.test.TestNodes.expr13Line2;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.oracle.truffle.api.instrument.Instrumenter;
import com.oracle.truffle.api.instrument.Probe;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.LineLocation;
import com.oracle.truffle.tools.LineToProbesMap;

public class LineToProbesMapTest {

    @Test
    public void testToolCreatedTooLate() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final RootNode expr13rootNode = createExpr13TestRootNode(instrumenter);
        final Node addNode = expr13rootNode.getChildren().iterator().next();
        final Probe probe = instrumenter.probe(addNode);
        final LineLocation lineLocation = probe.getProbedSourceSection().getLineLocation();
        assertEquals(lineLocation, expr13Line2);

        final LineToProbesMap tool = new LineToProbesMap();
        tool.install(instrumenter);

        assertNull(tool.findFirstProbe(expr13Line1));
        assertNull(tool.findFirstProbe(expr13Line2));
        tool.dispose();
    }

    @Test
    public void testToolInstalledTooLate() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final LineToProbesMap tool = new LineToProbesMap();

        final RootNode expr13rootNode = createExpr13TestRootNode(instrumenter);
        final Node addNode = expr13rootNode.getChildren().iterator().next();
        final Probe probe = instrumenter.probe(addNode);
        final LineLocation lineLocation = probe.getProbedSourceSection().getLineLocation();
        assertEquals(lineLocation, expr13Line2);

        tool.install(instrumenter);

        assertNull(tool.findFirstProbe(expr13Line1));
        assertNull(tool.findFirstProbe(expr13Line2));
        tool.dispose();
    }

    @Test
    public void testMapping() throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        final Instrumenter instrumenter = TestNodes.createInstrumenter();
        final LineToProbesMap tool = new LineToProbesMap();
        tool.install(instrumenter);

        final RootNode expr13rootNode = createExpr13TestRootNode(instrumenter);
        final Node addNode = expr13rootNode.getChildren().iterator().next();
        final Probe probe = instrumenter.probe(addNode);
        final LineLocation lineLocation = probe.getProbedSourceSection().getLineLocation();
        assertEquals(lineLocation, expr13Line2);

        assertNull(tool.findFirstProbe(expr13Line1));
        assertEquals(tool.findFirstProbe(expr13Line2), probe);
        tool.dispose();
    }

}
