/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.SafepointNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.junit.Test;

import java.util.Random;

/**
 * Tests that the {@link BciBlockMapping} can handle code with a lot of blocks. The test code is
 * derived from the {@code ImplicitStringConcatShapes} test in OpenJDK.
 */
public class BciBlockMappingTest extends GraalCompilerTest {
    static int async(int n, int increment) {
        int x = 42;

        try {
            for (int i = 0; i < n; i += increment) {
            }
            return -1;

        } catch (Throwable ex) {
            return x;
        }
    }

    @Override
    protected void checkMidTierGraph(StructuredGraph graph) {
        for (SafepointNode safepoint : graph.getNodes().filter(SafepointNode.class)) {
            FrameState frameState = safepoint.stateBefore();
            if (frameState.bci == 0) {
                continue;
            }
            ValueNode value = frameState.localAt(2);
            if (value.isJavaConstant() && (value.asJavaConstant().asInt() == 42 || value.asJavaConstant().asLong() == 41)) {
                continue;
            }
            if (value instanceof ValuePhiNode) {
                for (ValueNode v : ((ValuePhiNode) value).values()) {
                    if (!v.isJavaConstant() || (v.asJavaConstant().asLong() != 42 && v.asJavaConstant().asLong() != 41)) {
                        throw new AssertionError("Constant 42 is not live in safepoint FrameState" + frameState);
                    }
                }
                continue;
            }
            throw new AssertionError("Constant 42 is not live in safepoint FrameState" + frameState);
        }
    }

    @Test
    public void asyncLiveness() {
        test("async", 4, 1);
    }

    @Test
    public void asyncLiveness2() {
        test("async2", 4, 1);
    }

    static int async2(int n, int increment) {
        int x = 42;

        if (increment == 4) {
            x = 41;
            for (int i = 0; i < n; i += increment) {
            }
        }

        try {
            for (int i = 0; i < n; i += increment) {
            }
            return -1;

        } catch (Throwable ex) {
            return x;
        }
    }

    @Test
    public void test() {
        parseEager("run", AllowAssumptions.NO);
    }

    @SuppressWarnings("unused")
    public static void blackhole(String expected, String actual) {
    }

    static double aDouble = -96.0d;

    public void run() {
        blackhole("-96.0", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("\u045176", "" + aDouble);
        blackhole("92", "" + aDouble);
        blackhole("51", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("-54", "" + aDouble);
        blackhole("-87.0", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("19", "" + aDouble);
        blackhole("-41", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("T", "" + aDouble);
        blackhole("-42.0", "" + aDouble);
        blackhole("25", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("-1410065408", "" + aDouble);
        blackhole("8.0", "" + aDouble);
        blackhole("55.0", "" + aDouble);
        blackhole("97000000", "" + aDouble);
        blackhole("-9900", "" + aDouble);
        blackhole("935228928", "" + aDouble);
        blackhole("-8400", "" + aDouble);
        blackhole("C(82)", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("true", "" + aDouble);
        blackhole("3900", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("94000000", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("true", "" + aDouble);
        blackhole("5500", "" + aDouble);
        blackhole("-2900", "" + aDouble);
        blackhole("-194313216", "" + aDouble);
        blackhole("12", "" + aDouble);
        blackhole("C(87)", "" + aDouble);
        blackhole("91", "" + aDouble);
        blackhole("21", "" + aDouble);
        blackhole("18", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("\u045180", "" + aDouble);
        blackhole("C", "" + aDouble);
        blackhole("75", "" + aDouble);
        blackhole("-43", "" + aDouble);
        blackhole("80", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("-52.0", "" + aDouble);
        blackhole("75000000", "" + aDouble);
        blackhole("44", "" + aDouble);
        blackhole("-1705032704", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("83.0", "" + aDouble);
        blackhole("I", "" + aDouble);
        blackhole("94.0", "" + aDouble);
        blackhole("12.0", "" + aDouble);
        blackhole("-99.0", "" + aDouble);
        blackhole("17.0", "" + aDouble);
        blackhole("-84.0", "" + aDouble);
        blackhole("58000000", "" + aDouble);
        blackhole("-55000000", "" + aDouble);
        blackhole("1460392448", "" + aDouble);
        blackhole("C(70)", "" + aDouble);
        blackhole("\u04511", "" + aDouble);
        blackhole("8000", "" + aDouble);
        blackhole("18", "" + aDouble);
        blackhole("-1000000", "" + aDouble);
        blackhole("1000000", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("false", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("-2000000", "" + aDouble);
        blackhole("-820130816", "" + aDouble);
        blackhole("null", "" + aDouble);
        blackhole("25000000", "" + aDouble);
        blackhole("-96.0-96.0", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0\u045176", "" + aDouble);
        blackhole("-96.092", "" + aDouble);
        blackhole("-96.051", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0-54", "" + aDouble);
        blackhole("-96.0-87.0", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.019", "" + aDouble);
        blackhole("-96.0-41", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0T", "" + aDouble);
        blackhole("-96.0-42.0", "" + aDouble);
        blackhole("-96.025", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0-1410065408", "" + aDouble);
        blackhole("-96.08.0", "" + aDouble);
        blackhole("-96.055.0", "" + aDouble);
        blackhole("-96.097000000", "" + aDouble);
        blackhole("-96.0-9900", "" + aDouble);
        blackhole("-96.0935228928", "" + aDouble);
        blackhole("-96.0-8400", "" + aDouble);
        blackhole("-96.0C(82)", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0true", "" + aDouble);
        blackhole("-96.03900", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.094000000", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0true", "" + aDouble);
        blackhole("-96.05500", "" + aDouble);
        blackhole("-96.0-2900", "" + aDouble);
        blackhole("-96.0-194313216", "" + aDouble);
        blackhole("-96.012", "" + aDouble);
        blackhole("-96.0C(87)", "" + aDouble);
        blackhole("-96.091", "" + aDouble);
        blackhole("-96.021", "" + aDouble);
        blackhole("-96.018", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0\u045180", "" + aDouble);
        blackhole("-96.0C", "" + aDouble);
        blackhole("-96.075", "" + aDouble);
        blackhole("-96.0-43", "" + aDouble);
        blackhole("-96.080", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0-52.0", "" + aDouble);
        blackhole("-96.075000000", "" + aDouble);
        blackhole("-96.044", "" + aDouble);
        blackhole("-96.0-1705032704", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.083.0", "" + aDouble);
        blackhole("-96.0I", "" + aDouble);
        blackhole("-96.094.0", "" + aDouble);
        blackhole("-96.012.0", "" + aDouble);
        blackhole("-96.0-99.0", "" + aDouble);
        blackhole("-96.017.0", "" + aDouble);
        blackhole("-96.0-84.0", "" + aDouble);
        blackhole("-96.058000000", "" + aDouble);
        blackhole("-96.0-55000000", "" + aDouble);
        blackhole("-96.01460392448", "" + aDouble);
        blackhole("-96.0C(70)", "" + aDouble);
        blackhole("-96.0\u04511", "" + aDouble);
        blackhole("-96.08000", "" + aDouble);
        blackhole("-96.018", "" + aDouble);
        blackhole("-96.0-1000000", "" + aDouble);
        blackhole("-96.01000000", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0false", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.0-2000000", "" + aDouble);
        blackhole("-96.0-820130816", "" + aDouble);
        blackhole("-96.0null", "" + aDouble);
        blackhole("-96.025000000", "" + aDouble);
        blackhole("null-96.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045176", "" + aDouble);
        blackhole("null92", "" + aDouble);
        blackhole("null51", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-54", "" + aDouble);
        blackhole("null-87.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null19", "" + aDouble);
        blackhole("null-41", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullT", "" + aDouble);
        blackhole("null-42.0", "" + aDouble);
        blackhole("null25", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-1410065408", "" + aDouble);
        blackhole("null8.0", "" + aDouble);
        blackhole("null55.0", "" + aDouble);
        blackhole("null97000000", "" + aDouble);
        blackhole("null-9900", "" + aDouble);
        blackhole("null935228928", "" + aDouble);
        blackhole("null-8400", "" + aDouble);
        blackhole("nullC(82)", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null3900", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null94000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null5500", "" + aDouble);
        blackhole("null-2900", "" + aDouble);
        blackhole("null-194313216", "" + aDouble);
        blackhole("null12", "" + aDouble);
        blackhole("nullC(87)", "" + aDouble);
        blackhole("null91", "" + aDouble);
        blackhole("null21", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045180", "" + aDouble);
        blackhole("nullC", "" + aDouble);
        blackhole("null75", "" + aDouble);
        blackhole("null-43", "" + aDouble);
        blackhole("null80", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-52.0", "" + aDouble);
        blackhole("null75000000", "" + aDouble);
        blackhole("null44", "" + aDouble);
        blackhole("null-1705032704", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null83.0", "" + aDouble);
        blackhole("nullI", "" + aDouble);
        blackhole("null94.0", "" + aDouble);
        blackhole("null12.0", "" + aDouble);
        blackhole("null-99.0", "" + aDouble);
        blackhole("null17.0", "" + aDouble);
        blackhole("null-84.0", "" + aDouble);
        blackhole("null58000000", "" + aDouble);
        blackhole("null-55000000", "" + aDouble);
        blackhole("null1460392448", "" + aDouble);
        blackhole("nullC(70)", "" + aDouble);
        blackhole("null\u04511", "" + aDouble);
        blackhole("null8000", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("null-1000000", "" + aDouble);
        blackhole("null1000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullfalse", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-2000000", "" + aDouble);
        blackhole("null-820130816", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null25000000", "" + aDouble);
        blackhole("\u045176-96.0", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176\u045176", "" + aDouble);
        blackhole("\u04517692", "" + aDouble);
        blackhole("\u04517651", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176-54", "" + aDouble);
        blackhole("\u045176-87.0", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u04517619", "" + aDouble);
        blackhole("\u045176-41", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176T", "" + aDouble);
        blackhole("\u045176-42.0", "" + aDouble);
        blackhole("\u04517625", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176-1410065408", "" + aDouble);
        blackhole("\u0451768.0", "" + aDouble);
        blackhole("\u04517655.0", "" + aDouble);
        blackhole("\u04517697000000", "" + aDouble);
        blackhole("\u045176-9900", "" + aDouble);
        blackhole("\u045176935228928", "" + aDouble);
        blackhole("\u045176-8400", "" + aDouble);
        blackhole("\u045176C(82)", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176true", "" + aDouble);
        blackhole("\u0451763900", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u04517694000000", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176true", "" + aDouble);
        blackhole("\u0451765500", "" + aDouble);
        blackhole("\u045176-2900", "" + aDouble);
        blackhole("\u045176-194313216", "" + aDouble);
        blackhole("\u04517612", "" + aDouble);
        blackhole("\u045176C(87)", "" + aDouble);
        blackhole("\u04517691", "" + aDouble);
        blackhole("\u04517621", "" + aDouble);
        blackhole("\u04517618", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176\u045180", "" + aDouble);
        blackhole("\u045176C", "" + aDouble);
        blackhole("\u04517675", "" + aDouble);
        blackhole("\u045176-43", "" + aDouble);
        blackhole("\u04517680", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176-52.0", "" + aDouble);
        blackhole("\u04517675000000", "" + aDouble);
        blackhole("\u04517644", "" + aDouble);
        blackhole("\u045176-1705032704", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u04517683.0", "" + aDouble);
        blackhole("\u045176I", "" + aDouble);
        blackhole("\u04517694.0", "" + aDouble);
        blackhole("\u04517612.0", "" + aDouble);
        blackhole("\u045176-99.0", "" + aDouble);
        blackhole("\u04517617.0", "" + aDouble);
        blackhole("\u045176-84.0", "" + aDouble);
        blackhole("\u04517658000000", "" + aDouble);
        blackhole("\u045176-55000000", "" + aDouble);
        blackhole("\u0451761460392448", "" + aDouble);
        blackhole("\u045176C(70)", "" + aDouble);
        blackhole("\u045176\u04511", "" + aDouble);
        blackhole("\u0451768000", "" + aDouble);
        blackhole("\u04517618", "" + aDouble);
        blackhole("\u045176-1000000", "" + aDouble);
        blackhole("\u0451761000000", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176false", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u045176-2000000", "" + aDouble);
        blackhole("\u045176-820130816", "" + aDouble);
        blackhole("\u045176null", "" + aDouble);
        blackhole("\u04517625000000", "" + aDouble);
        blackhole("92-96.0", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92\u045176", "" + aDouble);
        blackhole("9292", "" + aDouble);
        blackhole("9251", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92-54", "" + aDouble);
        blackhole("92-87.0", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("9219", "" + aDouble);
        blackhole("92-41", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92T", "" + aDouble);
        blackhole("92-42.0", "" + aDouble);
        blackhole("9225", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92-1410065408", "" + aDouble);
        blackhole("928.0", "" + aDouble);
        blackhole("9255.0", "" + aDouble);
        blackhole("9297000000", "" + aDouble);
        blackhole("92-9900", "" + aDouble);
        blackhole("92935228928", "" + aDouble);
        blackhole("92-8400", "" + aDouble);
        blackhole("92C(82)", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92true", "" + aDouble);
        blackhole("923900", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("9294000000", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92true", "" + aDouble);
        blackhole("925500", "" + aDouble);
        blackhole("92-2900", "" + aDouble);
        blackhole("92-194313216", "" + aDouble);
        blackhole("9212", "" + aDouble);
        blackhole("92C(87)", "" + aDouble);
        blackhole("9291", "" + aDouble);
        blackhole("9221", "" + aDouble);
        blackhole("9218", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92\u045180", "" + aDouble);
        blackhole("92C", "" + aDouble);
        blackhole("9275", "" + aDouble);
        blackhole("92-43", "" + aDouble);
        blackhole("9280", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92-52.0", "" + aDouble);
        blackhole("9275000000", "" + aDouble);
        blackhole("9244", "" + aDouble);
        blackhole("92-1705032704", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("9283.0", "" + aDouble);
        blackhole("92I", "" + aDouble);
        blackhole("9294.0", "" + aDouble);
        blackhole("9212.0", "" + aDouble);
        blackhole("92-99.0", "" + aDouble);
        blackhole("9217.0", "" + aDouble);
        blackhole("92-84.0", "" + aDouble);
        blackhole("9258000000", "" + aDouble);
        blackhole("92-55000000", "" + aDouble);
        blackhole("921460392448", "" + aDouble);
        blackhole("92C(70)", "" + aDouble);
        blackhole("92\u04511", "" + aDouble);
        blackhole("928000", "" + aDouble);
        blackhole("9218", "" + aDouble);
        blackhole("92-1000000", "" + aDouble);
        blackhole("921000000", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92false", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("92-2000000", "" + aDouble);
        blackhole("92-820130816", "" + aDouble);
        blackhole("92null", "" + aDouble);
        blackhole("9225000000", "" + aDouble);
        blackhole("51-96.0", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51\u045176", "" + aDouble);
        blackhole("5192", "" + aDouble);
        blackhole("5151", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51-54", "" + aDouble);
        blackhole("51-87.0", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("5119", "" + aDouble);
        blackhole("51-41", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51T", "" + aDouble);
        blackhole("51-42.0", "" + aDouble);
        blackhole("5125", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51-1410065408", "" + aDouble);
        blackhole("518.0", "" + aDouble);
        blackhole("5155.0", "" + aDouble);
        blackhole("5197000000", "" + aDouble);
        blackhole("51-9900", "" + aDouble);
        blackhole("51935228928", "" + aDouble);
        blackhole("51-8400", "" + aDouble);
        blackhole("51C(82)", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51true", "" + aDouble);
        blackhole("513900", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("5194000000", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51true", "" + aDouble);
        blackhole("515500", "" + aDouble);
        blackhole("51-2900", "" + aDouble);
        blackhole("51-194313216", "" + aDouble);
        blackhole("5112", "" + aDouble);
        blackhole("51C(87)", "" + aDouble);
        blackhole("5191", "" + aDouble);
        blackhole("5121", "" + aDouble);
        blackhole("5118", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51\u045180", "" + aDouble);
        blackhole("51C", "" + aDouble);
        blackhole("5175", "" + aDouble);
        blackhole("51-43", "" + aDouble);
        blackhole("5180", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51-52.0", "" + aDouble);
        blackhole("5175000000", "" + aDouble);
        blackhole("5144", "" + aDouble);
        blackhole("51-1705032704", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("5183.0", "" + aDouble);
        blackhole("51I", "" + aDouble);
        blackhole("5194.0", "" + aDouble);
        blackhole("5112.0", "" + aDouble);
        blackhole("51-99.0", "" + aDouble);
        blackhole("5117.0", "" + aDouble);
        blackhole("51-84.0", "" + aDouble);
        blackhole("5158000000", "" + aDouble);
        blackhole("51-55000000", "" + aDouble);
        blackhole("511460392448", "" + aDouble);
        blackhole("51C(70)", "" + aDouble);
        blackhole("51\u04511", "" + aDouble);
        blackhole("518000", "" + aDouble);
        blackhole("5118", "" + aDouble);
        blackhole("51-1000000", "" + aDouble);
        blackhole("511000000", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51false", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("51-2000000", "" + aDouble);
        blackhole("51-820130816", "" + aDouble);
        blackhole("51null", "" + aDouble);
        blackhole("5125000000", "" + aDouble);
        blackhole("null-96.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045176", "" + aDouble);
        blackhole("null92", "" + aDouble);
        blackhole("null51", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-54", "" + aDouble);
        blackhole("null-87.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null19", "" + aDouble);
        blackhole("null-41", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullT", "" + aDouble);
        blackhole("null-42.0", "" + aDouble);
        blackhole("null25", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-1410065408", "" + aDouble);
        blackhole("null8.0", "" + aDouble);
        blackhole("null55.0", "" + aDouble);
        blackhole("null97000000", "" + aDouble);
        blackhole("null-9900", "" + aDouble);
        blackhole("null935228928", "" + aDouble);
        blackhole("null-8400", "" + aDouble);
        blackhole("nullC(82)", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null3900", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null94000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null5500", "" + aDouble);
        blackhole("null-2900", "" + aDouble);
        blackhole("null-194313216", "" + aDouble);
        blackhole("null12", "" + aDouble);
        blackhole("nullC(87)", "" + aDouble);
        blackhole("null91", "" + aDouble);
        blackhole("null21", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045180", "" + aDouble);
        blackhole("nullC", "" + aDouble);
        blackhole("null75", "" + aDouble);
        blackhole("null-43", "" + aDouble);
        blackhole("null80", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-52.0", "" + aDouble);
        blackhole("null75000000", "" + aDouble);
        blackhole("null44", "" + aDouble);
        blackhole("null-1705032704", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null83.0", "" + aDouble);
        blackhole("nullI", "" + aDouble);
        blackhole("null94.0", "" + aDouble);
        blackhole("null12.0", "" + aDouble);
        blackhole("null-99.0", "" + aDouble);
        blackhole("null17.0", "" + aDouble);
        blackhole("null-84.0", "" + aDouble);
        blackhole("null58000000", "" + aDouble);
        blackhole("null-55000000", "" + aDouble);
        blackhole("null1460392448", "" + aDouble);
        blackhole("nullC(70)", "" + aDouble);
        blackhole("null\u04511", "" + aDouble);
        blackhole("null8000", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("null-1000000", "" + aDouble);
        blackhole("null1000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullfalse", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-2000000", "" + aDouble);
        blackhole("null-820130816", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null25000000", "" + aDouble);
        blackhole("-54-96.0", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54\u045176", "" + aDouble);
        blackhole("-5492", "" + aDouble);
        blackhole("-5451", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54-54", "" + aDouble);
        blackhole("-54-87.0", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-5419", "" + aDouble);
        blackhole("-54-41", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54T", "" + aDouble);
        blackhole("-54-42.0", "" + aDouble);
        blackhole("-5425", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54-1410065408", "" + aDouble);
        blackhole("-548.0", "" + aDouble);
        blackhole("-5455.0", "" + aDouble);
        blackhole("-5497000000", "" + aDouble);
        blackhole("-54-9900", "" + aDouble);
        blackhole("-54935228928", "" + aDouble);
        blackhole("-54-8400", "" + aDouble);
        blackhole("-54C(82)", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54true", "" + aDouble);
        blackhole("-543900", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-5494000000", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54true", "" + aDouble);
        blackhole("-545500", "" + aDouble);
        blackhole("-54-2900", "" + aDouble);
        blackhole("-54-194313216", "" + aDouble);
        blackhole("-5412", "" + aDouble);
        blackhole("-54C(87)", "" + aDouble);
        blackhole("-5491", "" + aDouble);
        blackhole("-5421", "" + aDouble);
        blackhole("-5418", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54\u045180", "" + aDouble);
        blackhole("-54C", "" + aDouble);
        blackhole("-5475", "" + aDouble);
        blackhole("-54-43", "" + aDouble);
        blackhole("-5480", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54-52.0", "" + aDouble);
        blackhole("-5475000000", "" + aDouble);
        blackhole("-5444", "" + aDouble);
        blackhole("-54-1705032704", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-5483.0", "" + aDouble);
        blackhole("-54I", "" + aDouble);
        blackhole("-5494.0", "" + aDouble);
        blackhole("-5412.0", "" + aDouble);
        blackhole("-54-99.0", "" + aDouble);
        blackhole("-5417.0", "" + aDouble);
        blackhole("-54-84.0", "" + aDouble);
        blackhole("-5458000000", "" + aDouble);
        blackhole("-54-55000000", "" + aDouble);
        blackhole("-541460392448", "" + aDouble);
        blackhole("-54C(70)", "" + aDouble);
        blackhole("-54\u04511", "" + aDouble);
        blackhole("-548000", "" + aDouble);
        blackhole("-5418", "" + aDouble);
        blackhole("-54-1000000", "" + aDouble);
        blackhole("-541000000", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54false", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-54-2000000", "" + aDouble);
        blackhole("-54-820130816", "" + aDouble);
        blackhole("-54null", "" + aDouble);
        blackhole("-5425000000", "" + aDouble);
        blackhole("-87.0-96.0", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0\u045176", "" + aDouble);
        blackhole("-87.092", "" + aDouble);
        blackhole("-87.051", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0-54", "" + aDouble);
        blackhole("-87.0-87.0", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.019", "" + aDouble);
        blackhole("-87.0-41", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0T", "" + aDouble);
        blackhole("-87.0-42.0", "" + aDouble);
        blackhole("-87.025", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0-1410065408", "" + aDouble);
        blackhole("-87.08.0", "" + aDouble);
        blackhole("-87.055.0", "" + aDouble);
        blackhole("-87.097000000", "" + aDouble);
        blackhole("-87.0-9900", "" + aDouble);
        blackhole("-87.0935228928", "" + aDouble);
        blackhole("-87.0-8400", "" + aDouble);
        blackhole("-87.0C(82)", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0true", "" + aDouble);
        blackhole("-87.03900", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.094000000", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0true", "" + aDouble);
        blackhole("-87.05500", "" + aDouble);
        blackhole("-87.0-2900", "" + aDouble);
        blackhole("-87.0-194313216", "" + aDouble);
        blackhole("-87.012", "" + aDouble);
        blackhole("-87.0C(87)", "" + aDouble);
        blackhole("-87.091", "" + aDouble);
        blackhole("-87.021", "" + aDouble);
        blackhole("-87.018", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0\u045180", "" + aDouble);
        blackhole("-87.0C", "" + aDouble);
        blackhole("-87.075", "" + aDouble);
        blackhole("-87.0-43", "" + aDouble);
        blackhole("-87.080", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0-52.0", "" + aDouble);
        blackhole("-87.075000000", "" + aDouble);
        blackhole("-87.044", "" + aDouble);
        blackhole("-87.0-1705032704", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.083.0", "" + aDouble);
        blackhole("-87.0I", "" + aDouble);
        blackhole("-87.094.0", "" + aDouble);
        blackhole("-87.012.0", "" + aDouble);
        blackhole("-87.0-99.0", "" + aDouble);
        blackhole("-87.017.0", "" + aDouble);
        blackhole("-87.0-84.0", "" + aDouble);
        blackhole("-87.058000000", "" + aDouble);
        blackhole("-87.0-55000000", "" + aDouble);
        blackhole("-87.01460392448", "" + aDouble);
        blackhole("-87.0C(70)", "" + aDouble);
        blackhole("-87.0\u04511", "" + aDouble);
        blackhole("-87.08000", "" + aDouble);
        blackhole("-87.018", "" + aDouble);
        blackhole("-87.0-1000000", "" + aDouble);
        blackhole("-87.01000000", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0false", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.0-2000000", "" + aDouble);
        blackhole("-87.0-820130816", "" + aDouble);
        blackhole("-87.0null", "" + aDouble);
        blackhole("-87.025000000", "" + aDouble);
        blackhole("null-96.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045176", "" + aDouble);
        blackhole("null92", "" + aDouble);
        blackhole("null51", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-54", "" + aDouble);
        blackhole("null-87.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null19", "" + aDouble);
        blackhole("null-41", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullT", "" + aDouble);
        blackhole("null-42.0", "" + aDouble);
        blackhole("null25", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-1410065408", "" + aDouble);
        blackhole("null8.0", "" + aDouble);
        blackhole("null55.0", "" + aDouble);
        blackhole("null97000000", "" + aDouble);
        blackhole("null-9900", "" + aDouble);
        blackhole("null935228928", "" + aDouble);
        blackhole("null-8400", "" + aDouble);
        blackhole("nullC(82)", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null3900", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null94000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null5500", "" + aDouble);
        blackhole("null-2900", "" + aDouble);
        blackhole("null-194313216", "" + aDouble);
        blackhole("null12", "" + aDouble);
        blackhole("nullC(87)", "" + aDouble);
        blackhole("null91", "" + aDouble);
        blackhole("null21", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045180", "" + aDouble);
        blackhole("nullC", "" + aDouble);
        blackhole("null75", "" + aDouble);
        blackhole("null-43", "" + aDouble);
        blackhole("null80", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-52.0", "" + aDouble);
        blackhole("null75000000", "" + aDouble);
        blackhole("null44", "" + aDouble);
        blackhole("null-1705032704", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null83.0", "" + aDouble);
        blackhole("nullI", "" + aDouble);
        blackhole("null94.0", "" + aDouble);
        blackhole("null12.0", "" + aDouble);
        blackhole("null-99.0", "" + aDouble);
        blackhole("null17.0", "" + aDouble);
        blackhole("null-84.0", "" + aDouble);
        blackhole("null58000000", "" + aDouble);
        blackhole("null-55000000", "" + aDouble);
        blackhole("null1460392448", "" + aDouble);
        blackhole("nullC(70)", "" + aDouble);
        blackhole("null\u04511", "" + aDouble);
        blackhole("null8000", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("null-1000000", "" + aDouble);
        blackhole("null1000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullfalse", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-2000000", "" + aDouble);
        blackhole("null-820130816", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null25000000", "" + aDouble);
        blackhole("19-96.0", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19\u045176", "" + aDouble);
        blackhole("1992", "" + aDouble);
        blackhole("1951", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19-54", "" + aDouble);
        blackhole("19-87.0", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("1919", "" + aDouble);
        blackhole("19-41", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19T", "" + aDouble);
        blackhole("19-42.0", "" + aDouble);
        blackhole("1925", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19-1410065408", "" + aDouble);
        blackhole("198.0", "" + aDouble);
        blackhole("1955.0", "" + aDouble);
        blackhole("1997000000", "" + aDouble);
        blackhole("19-9900", "" + aDouble);
        blackhole("19935228928", "" + aDouble);
        blackhole("19-8400", "" + aDouble);
        blackhole("19C(82)", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19true", "" + aDouble);
        blackhole("193900", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("1994000000", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19true", "" + aDouble);
        blackhole("195500", "" + aDouble);
        blackhole("19-2900", "" + aDouble);
        blackhole("19-194313216", "" + aDouble);
        blackhole("1912", "" + aDouble);
        blackhole("19C(87)", "" + aDouble);
        blackhole("1991", "" + aDouble);
        blackhole("1921", "" + aDouble);
        blackhole("1918", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19\u045180", "" + aDouble);
        blackhole("19C", "" + aDouble);
        blackhole("1975", "" + aDouble);
        blackhole("19-43", "" + aDouble);
        blackhole("1980", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19-52.0", "" + aDouble);
        blackhole("1975000000", "" + aDouble);
        blackhole("1944", "" + aDouble);
        blackhole("19-1705032704", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("1983.0", "" + aDouble);
        blackhole("19I", "" + aDouble);
        blackhole("1994.0", "" + aDouble);
        blackhole("1912.0", "" + aDouble);
        blackhole("19-99.0", "" + aDouble);
        blackhole("1917.0", "" + aDouble);
        blackhole("19-84.0", "" + aDouble);
        blackhole("1958000000", "" + aDouble);
        blackhole("19-55000000", "" + aDouble);
        blackhole("191460392448", "" + aDouble);
        blackhole("19C(70)", "" + aDouble);
        blackhole("19\u04511", "" + aDouble);
        blackhole("198000", "" + aDouble);
        blackhole("1918", "" + aDouble);
        blackhole("19-1000000", "" + aDouble);
        blackhole("191000000", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19false", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("19-2000000", "" + aDouble);
        blackhole("19-820130816", "" + aDouble);
        blackhole("19null", "" + aDouble);
        blackhole("1925000000", "" + aDouble);
        blackhole("-41-96.0", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41\u045176", "" + aDouble);
        blackhole("-4192", "" + aDouble);
        blackhole("-4151", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41-54", "" + aDouble);
        blackhole("-41-87.0", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-4119", "" + aDouble);
        blackhole("-41-41", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41T", "" + aDouble);
        blackhole("-41-42.0", "" + aDouble);
        blackhole("-4125", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41-1410065408", "" + aDouble);
        blackhole("-418.0", "" + aDouble);
        blackhole("-4155.0", "" + aDouble);
        blackhole("-4197000000", "" + aDouble);
        blackhole("-41-9900", "" + aDouble);
        blackhole("-41935228928", "" + aDouble);
        blackhole("-41-8400", "" + aDouble);
        blackhole("-41C(82)", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41true", "" + aDouble);
        blackhole("-413900", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-4194000000", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41true", "" + aDouble);
        blackhole("-415500", "" + aDouble);
        blackhole("-41-2900", "" + aDouble);
        blackhole("-41-194313216", "" + aDouble);
        blackhole("-4112", "" + aDouble);
        blackhole("-41C(87)", "" + aDouble);
        blackhole("-4191", "" + aDouble);
        blackhole("-4121", "" + aDouble);
        blackhole("-4118", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41\u045180", "" + aDouble);
        blackhole("-41C", "" + aDouble);
        blackhole("-4175", "" + aDouble);
        blackhole("-41-43", "" + aDouble);
        blackhole("-4180", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41-52.0", "" + aDouble);
        blackhole("-4175000000", "" + aDouble);
        blackhole("-4144", "" + aDouble);
        blackhole("-41-1705032704", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-4183.0", "" + aDouble);
        blackhole("-41I", "" + aDouble);
        blackhole("-4194.0", "" + aDouble);
        blackhole("-4112.0", "" + aDouble);
        blackhole("-41-99.0", "" + aDouble);
        blackhole("-4117.0", "" + aDouble);
        blackhole("-41-84.0", "" + aDouble);
        blackhole("-4158000000", "" + aDouble);
        blackhole("-41-55000000", "" + aDouble);
        blackhole("-411460392448", "" + aDouble);
        blackhole("-41C(70)", "" + aDouble);
        blackhole("-41\u04511", "" + aDouble);
        blackhole("-418000", "" + aDouble);
        blackhole("-4118", "" + aDouble);
        blackhole("-41-1000000", "" + aDouble);
        blackhole("-411000000", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41false", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-41-2000000", "" + aDouble);
        blackhole("-41-820130816", "" + aDouble);
        blackhole("-41null", "" + aDouble);
        blackhole("-4125000000", "" + aDouble);
        blackhole("null-96.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045176", "" + aDouble);
        blackhole("null92", "" + aDouble);
        blackhole("null51", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-54", "" + aDouble);
        blackhole("null-87.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null19", "" + aDouble);
        blackhole("null-41", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullT", "" + aDouble);
        blackhole("null-42.0", "" + aDouble);
        blackhole("null25", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-1410065408", "" + aDouble);
        blackhole("null8.0", "" + aDouble);
        blackhole("null55.0", "" + aDouble);
        blackhole("null97000000", "" + aDouble);
        blackhole("null-9900", "" + aDouble);
        blackhole("null935228928", "" + aDouble);
        blackhole("null-8400", "" + aDouble);
        blackhole("nullC(82)", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null3900", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null94000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null5500", "" + aDouble);
        blackhole("null-2900", "" + aDouble);
        blackhole("null-194313216", "" + aDouble);
        blackhole("null12", "" + aDouble);
        blackhole("nullC(87)", "" + aDouble);
        blackhole("null91", "" + aDouble);
        blackhole("null21", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045180", "" + aDouble);
        blackhole("nullC", "" + aDouble);
        blackhole("null75", "" + aDouble);
        blackhole("null-43", "" + aDouble);
        blackhole("null80", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-52.0", "" + aDouble);
        blackhole("null75000000", "" + aDouble);
        blackhole("null44", "" + aDouble);
        blackhole("null-1705032704", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null83.0", "" + aDouble);
        blackhole("nullI", "" + aDouble);
        blackhole("null94.0", "" + aDouble);
        blackhole("null12.0", "" + aDouble);
        blackhole("null-99.0", "" + aDouble);
        blackhole("null17.0", "" + aDouble);
        blackhole("null-84.0", "" + aDouble);
        blackhole("null58000000", "" + aDouble);
        blackhole("null-55000000", "" + aDouble);
        blackhole("null1460392448", "" + aDouble);
        blackhole("nullC(70)", "" + aDouble);
        blackhole("null\u04511", "" + aDouble);
        blackhole("null8000", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("null-1000000", "" + aDouble);
        blackhole("null1000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullfalse", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-2000000", "" + aDouble);
        blackhole("null-820130816", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null25000000", "" + aDouble);
        blackhole("T-96.0", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T\u045176", "" + aDouble);
        blackhole("T92", "" + aDouble);
        blackhole("T51", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T-54", "" + aDouble);
        blackhole("T-87.0", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T19", "" + aDouble);
        blackhole("T-41", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("TT", "" + aDouble);
        blackhole("T-42.0", "" + aDouble);
        blackhole("T25", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T-1410065408", "" + aDouble);
        blackhole("T8.0", "" + aDouble);
        blackhole("T55.0", "" + aDouble);
        blackhole("T97000000", "" + aDouble);
        blackhole("T-9900", "" + aDouble);
        blackhole("T935228928", "" + aDouble);
        blackhole("T-8400", "" + aDouble);
        blackhole("TC(82)", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("Ttrue", "" + aDouble);
        blackhole("T3900", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T94000000", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("Ttrue", "" + aDouble);
        blackhole("T5500", "" + aDouble);
        blackhole("T-2900", "" + aDouble);
        blackhole("T-194313216", "" + aDouble);
        blackhole("T12", "" + aDouble);
        blackhole("TC(87)", "" + aDouble);
        blackhole("T91", "" + aDouble);
        blackhole("T21", "" + aDouble);
        blackhole("T18", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T\u045180", "" + aDouble);
        blackhole("TC", "" + aDouble);
        blackhole("T75", "" + aDouble);
        blackhole("T-43", "" + aDouble);
        blackhole("T80", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T-52.0", "" + aDouble);
        blackhole("T75000000", "" + aDouble);
        blackhole("T44", "" + aDouble);
        blackhole("T-1705032704", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T83.0", "" + aDouble);
        blackhole("TI", "" + aDouble);
        blackhole("T94.0", "" + aDouble);
        blackhole("T12.0", "" + aDouble);
        blackhole("T-99.0", "" + aDouble);
        blackhole("T17.0", "" + aDouble);
        blackhole("T-84.0", "" + aDouble);
        blackhole("T58000000", "" + aDouble);
        blackhole("T-55000000", "" + aDouble);
        blackhole("T1460392448", "" + aDouble);
        blackhole("TC(70)", "" + aDouble);
        blackhole("T\u04511", "" + aDouble);
        blackhole("T8000", "" + aDouble);
        blackhole("T18", "" + aDouble);
        blackhole("T-1000000", "" + aDouble);
        blackhole("T1000000", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("Tfalse", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T-2000000", "" + aDouble);
        blackhole("T-820130816", "" + aDouble);
        blackhole("Tnull", "" + aDouble);
        blackhole("T25000000", "" + aDouble);
        blackhole("-42.0-96.0", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0\u045176", "" + aDouble);
        blackhole("-42.092", "" + aDouble);
        blackhole("-42.051", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0-54", "" + aDouble);
        blackhole("-42.0-87.0", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.019", "" + aDouble);
        blackhole("-42.0-41", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0T", "" + aDouble);
        blackhole("-42.0-42.0", "" + aDouble);
        blackhole("-42.025", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0-1410065408", "" + aDouble);
        blackhole("-42.08.0", "" + aDouble);
        blackhole("-42.055.0", "" + aDouble);
        blackhole("-42.097000000", "" + aDouble);
        blackhole("-42.0-9900", "" + aDouble);
        blackhole("-42.0935228928", "" + aDouble);
        blackhole("-42.0-8400", "" + aDouble);
        blackhole("-42.0C(82)", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0true", "" + aDouble);
        blackhole("-42.03900", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.094000000", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0true", "" + aDouble);
        blackhole("-42.05500", "" + aDouble);
        blackhole("-42.0-2900", "" + aDouble);
        blackhole("-42.0-194313216", "" + aDouble);
        blackhole("-42.012", "" + aDouble);
        blackhole("-42.0C(87)", "" + aDouble);
        blackhole("-42.091", "" + aDouble);
        blackhole("-42.021", "" + aDouble);
        blackhole("-42.018", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0\u045180", "" + aDouble);
        blackhole("-42.0C", "" + aDouble);
        blackhole("-42.075", "" + aDouble);
        blackhole("-42.0-43", "" + aDouble);
        blackhole("-42.080", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0-52.0", "" + aDouble);
        blackhole("-42.075000000", "" + aDouble);
        blackhole("-42.044", "" + aDouble);
        blackhole("-42.0-1705032704", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.083.0", "" + aDouble);
        blackhole("-42.0I", "" + aDouble);
        blackhole("-42.094.0", "" + aDouble);
        blackhole("-42.012.0", "" + aDouble);
        blackhole("-42.0-99.0", "" + aDouble);
        blackhole("-42.017.0", "" + aDouble);
        blackhole("-42.0-84.0", "" + aDouble);
        blackhole("-42.058000000", "" + aDouble);
        blackhole("-42.0-55000000", "" + aDouble);
        blackhole("-42.01460392448", "" + aDouble);
        blackhole("-42.0C(70)", "" + aDouble);
        blackhole("-42.0\u04511", "" + aDouble);
        blackhole("-42.08000", "" + aDouble);
        blackhole("-42.018", "" + aDouble);
        blackhole("-42.0-1000000", "" + aDouble);
        blackhole("-42.01000000", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0false", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.0-2000000", "" + aDouble);
        blackhole("-42.0-820130816", "" + aDouble);
        blackhole("-42.0null", "" + aDouble);
        blackhole("-42.025000000", "" + aDouble);
        blackhole("25-96.0", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25\u045176", "" + aDouble);
        blackhole("2592", "" + aDouble);
        blackhole("2551", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25-54", "" + aDouble);
        blackhole("25-87.0", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("2519", "" + aDouble);
        blackhole("25-41", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25T", "" + aDouble);
        blackhole("25-42.0", "" + aDouble);
        blackhole("2525", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25-1410065408", "" + aDouble);
        blackhole("258.0", "" + aDouble);
        blackhole("2555.0", "" + aDouble);
        blackhole("2597000000", "" + aDouble);
        blackhole("25-9900", "" + aDouble);
        blackhole("25935228928", "" + aDouble);
        blackhole("25-8400", "" + aDouble);
        blackhole("25C(82)", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25true", "" + aDouble);
        blackhole("253900", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("2594000000", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25true", "" + aDouble);
        blackhole("255500", "" + aDouble);
        blackhole("25-2900", "" + aDouble);
        blackhole("25-194313216", "" + aDouble);
        blackhole("2512", "" + aDouble);
        blackhole("25C(87)", "" + aDouble);
        blackhole("2591", "" + aDouble);
        blackhole("2521", "" + aDouble);
        blackhole("2518", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25\u045180", "" + aDouble);
        blackhole("25C", "" + aDouble);
        blackhole("2575", "" + aDouble);
        blackhole("25-43", "" + aDouble);
        blackhole("2580", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25-52.0", "" + aDouble);
        blackhole("2575000000", "" + aDouble);
        blackhole("2544", "" + aDouble);
        blackhole("25-1705032704", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("2583.0", "" + aDouble);
        blackhole("25I", "" + aDouble);
        blackhole("2594.0", "" + aDouble);
        blackhole("2512.0", "" + aDouble);
        blackhole("25-99.0", "" + aDouble);
        blackhole("2517.0", "" + aDouble);
        blackhole("25-84.0", "" + aDouble);
        blackhole("2558000000", "" + aDouble);
        blackhole("25-55000000", "" + aDouble);
        blackhole("251460392448", "" + aDouble);
        blackhole("25C(70)", "" + aDouble);
        blackhole("25\u04511", "" + aDouble);
        blackhole("258000", "" + aDouble);
        blackhole("2518", "" + aDouble);
        blackhole("25-1000000", "" + aDouble);
        blackhole("251000000", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25false", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("25-2000000", "" + aDouble);
        blackhole("25-820130816", "" + aDouble);
        blackhole("25null", "" + aDouble);
        blackhole("2525000000", "" + aDouble);
        blackhole("null-96.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045176", "" + aDouble);
        blackhole("null92", "" + aDouble);
        blackhole("null51", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-54", "" + aDouble);
        blackhole("null-87.0", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null19", "" + aDouble);
        blackhole("null-41", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullT", "" + aDouble);
        blackhole("null-42.0", "" + aDouble);
        blackhole("null25", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-1410065408", "" + aDouble);
        blackhole("null8.0", "" + aDouble);
        blackhole("null55.0", "" + aDouble);
        blackhole("null97000000", "" + aDouble);
        blackhole("null-9900", "" + aDouble);
        blackhole("null935228928", "" + aDouble);
        blackhole("null-8400", "" + aDouble);
        blackhole("nullC(82)", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null3900", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null94000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nulltrue", "" + aDouble);
        blackhole("null5500", "" + aDouble);
        blackhole("null-2900", "" + aDouble);
        blackhole("null-194313216", "" + aDouble);
        blackhole("null12", "" + aDouble);
        blackhole("nullC(87)", "" + aDouble);
        blackhole("null91", "" + aDouble);
        blackhole("null21", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null\u045180", "" + aDouble);
        blackhole("nullC", "" + aDouble);
        blackhole("null75", "" + aDouble);
        blackhole("null-43", "" + aDouble);
        blackhole("null80", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-52.0", "" + aDouble);
        blackhole("null75000000", "" + aDouble);
        blackhole("null44", "" + aDouble);
        blackhole("null-1705032704", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null83.0", "" + aDouble);
        blackhole("nullI", "" + aDouble);
        blackhole("null94.0", "" + aDouble);
        blackhole("null12.0", "" + aDouble);
        blackhole("null-99.0", "" + aDouble);
        blackhole("null17.0", "" + aDouble);
        blackhole("null-84.0", "" + aDouble);
        blackhole("null58000000", "" + aDouble);
        blackhole("null-55000000", "" + aDouble);
        blackhole("null1460392448", "" + aDouble);
        blackhole("nullC(70)", "" + aDouble);
        blackhole("null\u04511", "" + aDouble);
        blackhole("null8000", "" + aDouble);
        blackhole("null18", "" + aDouble);
        blackhole("null-1000000", "" + aDouble);
        blackhole("null1000000", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("nullfalse", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null-2000000", "" + aDouble);
        blackhole("null-820130816", "" + aDouble);
        blackhole("nullnull", "" + aDouble);
        blackhole("null25000000", "" + aDouble);
        blackhole("-1410065408-96.0", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408\u045176", "" + aDouble);
        blackhole("-141006540892", "" + aDouble);
        blackhole("-141006540851", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408-54", "" + aDouble);
        blackhole("-1410065408-87.0", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-141006540819", "" + aDouble);
        blackhole("-1410065408-41", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408T", "" + aDouble);
        blackhole("-1410065408-42.0", "" + aDouble);
        blackhole("-141006540825", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408-1410065408", "" + aDouble);
        blackhole("-14100654088.0", "" + aDouble);
        blackhole("-141006540855.0", "" + aDouble);
        blackhole("-141006540897000000", "" + aDouble);
        blackhole("-1410065408-9900", "" + aDouble);
        blackhole("-1410065408935228928", "" + aDouble);
        blackhole("-1410065408-8400", "" + aDouble);
        blackhole("-1410065408C(82)", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408true", "" + aDouble);
        blackhole("-14100654083900", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-141006540894000000", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408true", "" + aDouble);
        blackhole("-14100654085500", "" + aDouble);
        blackhole("-1410065408-2900", "" + aDouble);
        blackhole("-1410065408-194313216", "" + aDouble);
        blackhole("-141006540812", "" + aDouble);
        blackhole("-1410065408C(87)", "" + aDouble);
        blackhole("-141006540891", "" + aDouble);
        blackhole("-141006540821", "" + aDouble);
        blackhole("-141006540818", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408\u045180", "" + aDouble);
        blackhole("-1410065408C", "" + aDouble);
        blackhole("-141006540875", "" + aDouble);
        blackhole("-1410065408-43", "" + aDouble);
        blackhole("-141006540880", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408-52.0", "" + aDouble);
        blackhole("-141006540875000000", "" + aDouble);
        blackhole("-141006540844", "" + aDouble);
        blackhole("-1410065408-1705032704", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-141006540883.0", "" + aDouble);
        blackhole("-1410065408I", "" + aDouble);
        blackhole("-141006540894.0", "" + aDouble);
        blackhole("-141006540812.0", "" + aDouble);
        blackhole("-1410065408-99.0", "" + aDouble);
        blackhole("-141006540817.0", "" + aDouble);
        blackhole("-1410065408-84.0", "" + aDouble);
        blackhole("-141006540858000000", "" + aDouble);
        blackhole("-1410065408-55000000", "" + aDouble);
        blackhole("-14100654081460392448", "" + aDouble);
        blackhole("-1410065408C(70)", "" + aDouble);
        blackhole("-1410065408\u04511", "" + aDouble);
        blackhole("-14100654088000", "" + aDouble);
        blackhole("-141006540818", "" + aDouble);
        blackhole("-1410065408-1000000", "" + aDouble);
        blackhole("-14100654081000000", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408false", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-1410065408-2000000", "" + aDouble);
        blackhole("-1410065408-820130816", "" + aDouble);
        blackhole("-1410065408null", "" + aDouble);
        blackhole("-141006540825000000", "" + aDouble);
        blackhole("8.0-96.0", "" + aDouble);
        blackhole("8.0null", "" + aDouble);
        blackhole("8.0\u045176", "" + aDouble);
        blackhole("8.092", "" + aDouble);
        blackhole("8.051", "" + aDouble);
        blackhole("8.0null", "" + aDouble);
        blackhole("8.0-54", "" + aDouble);
        blackhole("8.0-87.0", "" + aDouble);
        blackhole("8.0null", "" + aDouble);
        blackhole("8.019", "" + aDouble);
        blackhole("8.0-41", "" + aDouble);
        blackhole("8.0null", "" + aDouble);
        blackhole("8.0T", "" + aDouble);
        blackhole("8.0-42.0", "" + aDouble);
        blackhole("8.025", "" + aDouble);
        blackhole("C(87)-96.0", "" + aDouble);
    }

    @Test
    public void testManyLoops() {
        parseEager("manyLoops", AllowAssumptions.NO);
    }

    public int manyLoops() {
        Random rng = new Random();
        int count = 0;
        // @formatter:off
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        while (rng.nextBoolean()) { ++count; }
        /* 129th */ while (rng.nextBoolean()) { ++count; }
        /* 130th */ while (rng.nextBoolean()) { ++count; }
        // @formatter:on
        return count;
    }
}
