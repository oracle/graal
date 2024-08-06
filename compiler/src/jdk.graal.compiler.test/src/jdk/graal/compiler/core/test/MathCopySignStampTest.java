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
package jdk.graal.compiler.core.test;

import jdk.graal.compiler.core.common.type.FloatStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class MathCopySignStampTest extends GraalCompilerTest {

    private Stamp[] stampsToBind;

    @Override
    protected StructuredGraph parse(StructuredGraph.Builder builder, PhaseSuite<HighTierContext> graphBuilderSuite) {
        StructuredGraph graph = super.parse(builder, graphBuilderSuite);
        if (stampsToBind != null) {
            for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                param.setStamp(stampsToBind[param.index()]);
            }
        }
        return graph;
    }

    private static final float[] floatValues = {
                    0.0f,
                    -0.0f,
                    1.0f,
                    -1.0f,
                    123.4f,
                    -56.7f,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NaN,
    };

    public static float floatCopySign(float magnitude, float sign) {
        return Math.copySign(magnitude, sign);
    }

    @Test
    public void testFloatCopySign() throws InvalidInstalledCodeException {
        for (float f1 : floatValues) {
            FloatStamp s1 = new FloatStamp(32, f1, f1, !Float.isNaN(f1));
            for (float f2 : floatValues) {
                FloatStamp s2 = new FloatStamp(32, f2, f2, !Float.isNaN(f2));
                for (float f3 : floatValues) {
                    FloatStamp s3 = new FloatStamp(32, f3, f3, !Float.isNaN(f3));
                    for (float f4 : floatValues) {
                        FloatStamp s4 = new FloatStamp(32, f4, f4, !Float.isNaN(f4));
                        stampsToBind = new Stamp[]{s1.meet(s2), s3.meet(s4)};
                        InstalledCode code = getCode(getResolvedJavaMethod("floatCopySign"), null, true);
                        Assert.assertEquals(floatCopySign(f1, f3), (float) code.executeVarargs(f1, f3), 0);
                        Assert.assertEquals(floatCopySign(f2, f3), (float) code.executeVarargs(f2, f3), 0);
                        Assert.assertEquals(floatCopySign(f1, f4), (float) code.executeVarargs(f1, f4), 0);
                        Assert.assertEquals(floatCopySign(f2, f4), (float) code.executeVarargs(f2, f4), 0);
                        stampsToBind = null;
                    }
                }
            }
        }
    }

    public static int badCopyStampGR43958(short magnitude) {
        /*
         * Previously the stamp of the copySign would be incorrect.
         */
        return (short) Math.copySign(magnitude, -942.5804f);
    }

    @Test
    public void testGR43958() throws InvalidInstalledCodeException {
        short[] shortValues = {42, -42, 95};
        for (short magnitude : shortValues) {
            InstalledCode code = getCode(getResolvedJavaMethod("badCopyStampGR43958"), null, true);
            Assert.assertEquals(badCopyStampGR43958(magnitude), (int) code.executeVarargs(magnitude), 0);
        }
    }

    private static final double[] doubleValues = {
                    0.0d,
                    -0.0d,
                    1.0d,
                    -1.0d,
                    123.4d,
                    -56.7d,
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    Double.NaN,
    };

    public static double doubleCopySign(double magnitude, double sign) {
        return Math.copySign(magnitude, sign);
    }

    @Test
    public void testDoubleCopySign() throws InvalidInstalledCodeException {
        for (double d1 : doubleValues) {
            FloatStamp s1 = new FloatStamp(64, d1, d1, !Double.isNaN(d1));
            for (double d2 : doubleValues) {
                FloatStamp s2 = new FloatStamp(64, d2, d2, !Double.isNaN(d2));
                for (double d3 : doubleValues) {
                    FloatStamp s3 = new FloatStamp(64, d3, d3, !Double.isNaN(d3));
                    for (double d4 : doubleValues) {
                        FloatStamp s4 = new FloatStamp(64, d4, d4, !Double.isNaN(d4));
                        stampsToBind = new Stamp[]{s1.meet(s2), s3.meet(s4)};
                        InstalledCode code = getCode(getResolvedJavaMethod("doubleCopySign"), null, true);
                        Assert.assertEquals(doubleCopySign(d1, d3), (double) code.executeVarargs(d1, d3), 0);
                        Assert.assertEquals(doubleCopySign(d2, d3), (double) code.executeVarargs(d2, d3), 0);
                        Assert.assertEquals(doubleCopySign(d1, d4), (double) code.executeVarargs(d1, d4), 0);
                        Assert.assertEquals(doubleCopySign(d2, d4), (double) code.executeVarargs(d2, d4), 0);
                        stampsToBind = null;
                    }
                }
            }
        }
    }
}
