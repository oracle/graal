/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.options.OptionValues;
import org.junit.Test;

import jdk.vm.ci.code.CodeUtil;

public class IntegerDivRemCanonicalizationTest extends GraalCompilerTest {

    public static int redundantRemNode(int a, int b) {
        int r = (a - a % b) / b;
        return r;
    }

    @Test
    public void testRedundantRemNode() {
        StructuredGraph graph = parseForCompile(getResolvedJavaMethod("redundantRemNode"));
        createCanonicalizerPhase().apply(graph, getProviders());
        // We expect the remainder to be canonicalized away.
        assertTrue(graph.getNodes().filter(SignedRemNode.class).count() == 0);
    }

    static Stamp foldStamp(Stamp stamp1, Stamp stamp2) {
        if (stamp1.isEmpty()) {
            return stamp1;
        }
        if (stamp2.isEmpty()) {
            return stamp2;
        }
        IntegerStamp a = (IntegerStamp) stamp1;
        IntegerStamp b = (IntegerStamp) stamp2;
        assert a.getBits() == b.getBits();
        if (a.lowerBound() == a.upperBound() && b.lowerBound() == b.upperBound() && b.lowerBound() != 0) {
            long value = CodeUtil.convert(a.lowerBound() / b.lowerBound(), a.getBits(), false);
            return IntegerStamp.create(a.getBits(), value, value);
        } else if (b.isStrictlyPositive()) {
            long newLowerBound = a.lowerBound() < 0 ? a.lowerBound() / b.lowerBound() : a.lowerBound() / b.upperBound();
            long newUpperBound = a.upperBound() < 0 ? a.upperBound() / b.upperBound() : a.upperBound() / b.lowerBound();
            return IntegerStamp.create(a.getBits(), newLowerBound, newUpperBound);
        } else {
            return a.unrestricted();
        }
    }

    @Test
    public void testStamp() {
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.StressExplicitExceptionCode, true);
        test(opt, "foldStamp", IntegerStamp.create(32, Integer.MIN_VALUE, Integer.MAX_VALUE), IntegerStamp.create(32, 0, 0));
    }

}
