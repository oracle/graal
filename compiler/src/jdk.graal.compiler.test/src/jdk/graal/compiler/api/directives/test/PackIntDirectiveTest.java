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
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.PackIntNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;

public class PackIntDirectiveTest extends GraalCompilerTest {
    public static int add(int a, int b) {
        long packed = GraalDirectives.packInt(a + b);
        return GraalDirectives.unpackInt(packed);
    }

    public static int store(long[] array, int a, int b) {
        array[0] = GraalDirectives.packInt(a + b);
        return GraalDirectives.unpackInt(array[0]);
    }

    @Test
    public void testOverflowAndNegativeValues() {
        for (int a : new int[]{0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (int b : new int[]{0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                test("add", a, b);
                test("store", new long[1], a, b);
            }
        }
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        Assert.assertEquals(1, graph.getNodes().filter(PackIntNode.class).count());
        Assert.assertTrue(graph.getNodes().filter(SignExtendNode.class).isEmpty());
    }
}
