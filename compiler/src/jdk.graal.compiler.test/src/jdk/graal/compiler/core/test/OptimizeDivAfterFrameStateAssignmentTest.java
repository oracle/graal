/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.core.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.FrameStateAssignmentPhase;
import jdk.graal.compiler.phases.common.OptimizeDivPhase;
import jdk.graal.compiler.phases.tiers.Suites;

/**
 * Checks the division state before low-tier lowering so that hosts without snippet-based division
 * lowering also detect state lost by remainder optimization after frame-state assignment.
 */
public class OptimizeDivAfterFrameStateAssignmentTest extends GraalCompilerTest {

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(new OptionValues(options, GraalOptions.OptimizeDiv, true));
        var midTier = suites.getMidTier();
        var position = midTier.findPhase(OptimizeDivPhase.class);
        var optimizeDiv = position.previous();
        position.remove();
        midTier.insertAfterPhase(FrameStateAssignmentPhase.class, optimizeDiv);
        return suites;
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        var divisions = graph.getNodes(UnsignedDivNode.TYPE);
        Assert.assertEquals("Expected one division after remainder optimization", 1, divisions.count());
        Assert.assertNotNull("The division must retain the remainder's deoptimization state", divisions.first().stateBefore());
    }

    public static int unsignedIntRemainder(int value) {
        return Integer.remainderUnsigned(value, -1);
    }

    public static long unsignedLongRemainder(long value) {
        return Long.remainderUnsigned(value, -1L);
    }

    @Test
    public void testIntRemainder() {
        test("unsignedIntRemainder", 0);
        test("unsignedIntRemainder", 1);
        test("unsignedIntRemainder", -1);
        test("unsignedIntRemainder", Integer.MIN_VALUE);
        test("unsignedIntRemainder", Integer.MAX_VALUE);
    }

    @Test
    public void testLongRemainder() {
        test("unsignedLongRemainder", 0L);
        test("unsignedLongRemainder", 1L);
        test("unsignedLongRemainder", -1L);
        test("unsignedLongRemainder", Long.MIN_VALUE);
        test("unsignedLongRemainder", Long.MAX_VALUE);
    }
}
