/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.test;

import static org.graalvm.compiler.core.common.calc.CanonicalCondition.BT;
import static org.graalvm.compiler.core.common.calc.CanonicalCondition.LT;
import static org.graalvm.compiler.core.test.GraalCompilerTest.getInitialOptions;
import static org.junit.Assert.assertEquals;

import org.graalvm.compiler.core.common.calc.CanonicalCondition;
import org.graalvm.compiler.core.common.type.IntegerStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.test.GraphTest;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.calc.NarrowNode;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;

/**
 * This class tests that {@link NarrowNode#preservesOrder(CanonicalCondition)} returns correct
 * value.
 */
public class NarrowPreservesOrderTest extends GraphTest {

    private StructuredGraph graph;

    @Before
    public void before() {
        OptionValues options = getInitialOptions();
        DebugContext debug = getDebug(options);
        graph = new StructuredGraph.Builder(options, debug, AllowAssumptions.YES).build();
    }

    private void testPreserveOrder(Stamp inputStamp, int resultBits, CanonicalCondition cond, boolean expected) {
        ParameterNode input = new ParameterNode(0, StampPair.createSingle(inputStamp));
        NarrowNode narrow = new NarrowNode(input, resultBits);
        assertEquals(expected, narrow.preservesOrder(cond));
    }

    @Test
    public void testBoolean() {
        testPreserveOrder(IntegerStamp.create(32, 0, 0), 1, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0, 0), 1, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 1, 1), 1, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 1, 1), 1, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0, 1), 1, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0, 1), 1, BT, true);

        testPreserveOrder(IntegerStamp.create(32, 0xFFFFFF80, 0x7F), 1, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFFFF80, 0x7F), 1, BT, false);
    }

    @Test
    public void testByte() {
        testPreserveOrder(IntegerStamp.create(32, 0, 0), 8, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0, 0), 8, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0x7F, 0x7F), 8, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0x7F, 0x7F), 8, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0xFF, 0xFF), 8, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0xFF, 0xFF), 8, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFFFF80, 0x7F), 8, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFFFF80, 0x7F), 8, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0, 0xFF), 8, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0, 0xFF), 8, BT, true);

        testPreserveOrder(IntegerStamp.create(32, 0xFFFF8000, 0x7FFF), 8, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFF8000, 0x7FFF), 8, BT, false);
    }

    @Test
    public void testShort() {
        testPreserveOrder(IntegerStamp.create(32, 0, 0), 16, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0, 0), 16, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0x7FFF, 0x7FFF), 16, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0x7FFF, 0x7FFF), 16, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFF, 0xFFFF), 16, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFF, 0xFFFF), 16, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFF8000, 0x7FFF), 16, LT, true);
        testPreserveOrder(IntegerStamp.create(32, 0xFFFF8000, 0x7FFF), 16, BT, true);
        testPreserveOrder(IntegerStamp.create(32, 0, 0xFFFF), 16, LT, false);
        testPreserveOrder(IntegerStamp.create(32, 0, 0xFFFF), 16, BT, true);

        testPreserveOrder(StampFactory.intValue(), 16, LT, false);
        testPreserveOrder(StampFactory.intValue(), 16, BT, false);
    }

    @Test
    public void testInt() {
        testPreserveOrder(IntegerStamp.create(64, 0, 0), 32, LT, true);
        testPreserveOrder(IntegerStamp.create(64, 0, 0), 32, BT, true);
        testPreserveOrder(IntegerStamp.create(64, 0x7FFFFFFF, 0x7FFFFFFF), 32, LT, true);
        testPreserveOrder(IntegerStamp.create(64, 0x7FFFFFFF, 0x7FFFFFFF), 32, BT, true);
        testPreserveOrder(IntegerStamp.create(64, 0x00000000FFFFFFFFL, 0x00000000FFFFFFFFL), 32, LT, false);
        testPreserveOrder(IntegerStamp.create(64, 0x00000000FFFFFFFFL, 0x00000000FFFFFFFFL), 32, BT, true);
        testPreserveOrder(IntegerStamp.create(64, 0x80000000, 0x7FFFFFFF), 32, LT, true);
        testPreserveOrder(IntegerStamp.create(64, 0x80000000, 0x7FFFFFFF), 32, BT, true);
        testPreserveOrder(IntegerStamp.create(64, 0, 0x00000000FFFFFFFFL), 32, LT, false);
        testPreserveOrder(IntegerStamp.create(64, 0, 0x00000000FFFFFFFFL), 32, BT, true);

        testPreserveOrder(StampFactory.forKind(JavaKind.Long), 32, LT, false);
        testPreserveOrder(StampFactory.forKind(JavaKind.Long), 32, BT, false);
    }
}
