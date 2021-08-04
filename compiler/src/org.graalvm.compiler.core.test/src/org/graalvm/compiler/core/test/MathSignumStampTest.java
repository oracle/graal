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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.core.common.type.FloatStamp;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.nodes.ParameterNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.phases.PhaseSuite;
import org.graalvm.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class MathSignumStampTest extends GraalCompilerTest {

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

    public static float floatSignum(float f) {
        return Math.signum(f);
    }

    private static final float[] floatValues = {
                    0.0f,
                    -0.0f,
                    123.4f,
                    -56.7f,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NaN,
    };

    @Test
    public void testFloatSignum() throws InvalidInstalledCodeException {
        for (float f1 : floatValues) {
            FloatStamp s1 = new FloatStamp(32, f1, f1, !Float.isNaN(f1));
            for (float f2 : floatValues) {
                FloatStamp s2 = new FloatStamp(32, f2, f2, !Float.isNaN(f2));
                stampsToBind = new Stamp[]{s1.meet(s2)};
                InstalledCode code = getCode(getResolvedJavaMethod("floatSignum"), null, true);
                Assert.assertEquals(floatSignum(f1), (float) code.executeVarargs(f1), 0);
                Assert.assertEquals(floatSignum(f2), (float) code.executeVarargs(f2), 0);
                stampsToBind = null;
            }
        }
    }

    public static double doubleSignum(double d) {
        return Math.signum(d);
    }

    private static final double[] doubleValues = {
                    0.0d,
                    -0.0d,
                    123.4d,
                    -56.7d,
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    Double.NaN,
    };

    @Test
    public void testDoubleSignum() throws InvalidInstalledCodeException {
        for (double d1 : doubleValues) {
            FloatStamp s1 = new FloatStamp(64, d1, d1, !Double.isNaN(d1));
            for (double d2 : doubleValues) {
                FloatStamp s2 = new FloatStamp(64, d2, d2, !Double.isNaN(d2));
                stampsToBind = new Stamp[]{s1.meet(s2)};
                InstalledCode code = getCode(getResolvedJavaMethod("doubleSignum"), null, true);
                Assert.assertEquals(doubleSignum(d1), (double) code.executeVarargs(d1), 0);
                Assert.assertEquals(doubleSignum(d2), (double) code.executeVarargs(d2), 0);
                stampsToBind = null;
            }
        }
    }
}
