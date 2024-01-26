/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;

public class InstanceOfCanonicalizationTest extends GraalCompilerTest {

    @SuppressWarnings("unused")
    public static boolean checkCastIncompatibleTypes(Object arr) {
        // Cast first to a byte array, then to a boolean array. This only succeeds if arr is null.
        byte[] barr = (byte[]) arr;
        boolean[] bbarr = (boolean[]) (Object) barr;
        return true;
    }

    @SuppressWarnings("cast")
    public static int unsatisfiableInstanceOf(byte[] barr) {
        // Plain instanceof does not allow null, so this will never succeed.
        if ((Object) barr instanceof boolean[]) {
            return -1;
        }
        return 1;
    }

    @Test
    public void testCheckCastIncompatibleTypes() {
        StructuredGraph g = parseEager("checkCastIncompatibleTypes", AllowAssumptions.NO, getInitialOptions());
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());

        // The second check-cast against boolean[] should canonicalize to a null check
        Assert.assertEquals(1, g.getNodes().filter(InstanceOfNode.class).count());
        Assert.assertEquals(1, g.getNodes().filter(IsNullNode.class).count());

        testAgainstExpected(g.method(), new Result(checkCastIncompatibleTypes(null), null), null, new Object[]{null});
        testAgainstExpected(g.method(), new Result(null, new ClassCastException()), null, new Object[]{new byte[10]});
        testAgainstExpected(g.method(), new Result(null, new ClassCastException()), null, new Object[]{new boolean[10]});
    }

    @Test
    public void testUnsatisfiableInstanceOf() {
        StructuredGraph g = parseEager("unsatisfiableInstanceOf", AllowAssumptions.NO, getInitialOptions());
        createCanonicalizerPhase().apply(g, getDefaultHighTierContext());

        // Tested condition can never be true, so it should canonicalize to a constant.
        Assert.assertEquals(0, g.getNodes().filter(InstanceOfNode.class).count());

        testAgainstExpected(g.method(), new Result(unsatisfiableInstanceOf(null), null), null, new Object[]{null});
        testAgainstExpected(g.method(), new Result(unsatisfiableInstanceOf(new byte[10]), null), null, new Object[]{new byte[10]});
    }
}
