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
package jdk.graal.compiler.api.directives.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.duplication.phases.simulation.DuplicationOptions;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.PackFloatNode;
import jdk.graal.compiler.options.OptionValues;

public class AssumeFloatFromDoubleDirectiveTest extends GraalCompilerTest {
    public static int snippet(double value) {
        return Float.floatToRawIntBits(GraalDirectives.assumeFloat(value));
    }

    public static int pack(float value) {
        return Float.floatToRawIntBits(GraalDirectives.assumeFloat(GraalDirectives.packFloat(value)));
    }

    public static float merge(float fallback, double packed, boolean condition) {
        float value;
        if (condition) {
            GraalDirectives.blackhole(1);
            value = GraalDirectives.assumeFloat(packed);
        } else {
            GraalDirectives.blackhole(2);
            value = fallback;
        }
        GraalDirectives.blackhole(packed);
        return value;
    }

    @Test
    public void testMerge() {
        double packed = Double.longBitsToDouble(0x12345678ffc00456L);
        // Keep the phi: duplicating the return into each branch hides cast-kind mismatches.
        OptionValues options = new OptionValues(getInitialOptions(), DuplicationOptions.ExcludeFunctionFromDuplication, "AssumeFloatFromDoubleDirectiveTest.merge");
        test(options, "merge", -0.0f, packed, true);
        test(options, "merge", -0.0f, packed, false);
    }

    public static int spillBeforePack(float value) {
        GraalDirectives.spillRegisters();
        return pack(value);
    }

    public static int spillAfterPack(float value) {
        double packed = GraalDirectives.packFloat(value);
        GraalDirectives.bindToRegister(packed);
        GraalDirectives.spillRegisters();
        return Float.floatToRawIntBits(GraalDirectives.assumeFloat(packed));
    }

    @Test
    public void testPacking() {
        for (int bits : new int[]{0, 0x80000000, 0x3f800000, 0x7f800000, 0xff800000, 0x7fc00123, 0xffc00456}) {
            float value = Float.intBitsToFloat(bits);
            test("pack", value);
            test("spillBeforePack", value);
            test("spillAfterPack", value);
        }
    }

    @Test
    public void testRawBits() {
        for (int bits : new int[]{0, 0x80000000, 0x3f800000, 0x7f800000, 0xff800000, 0x7fc00123, 0xffc00456}) {
            test("snippet", Double.longBitsToDouble(Integer.toUnsignedLong(bits)));
            test("snippet", Double.longBitsToDouble(0x1234567800000000L | Integer.toUnsignedLong(bits)));
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals(graph.method().getName().equals("snippet") || graph.method().getName().equals("merge") ? 0 : 1, graph.getNodes().filter(PackFloatNode.class).count());
        Assert.assertTrue(graph.getNodes().filter(FloatConvertNode.class).isEmpty());
    }
}
