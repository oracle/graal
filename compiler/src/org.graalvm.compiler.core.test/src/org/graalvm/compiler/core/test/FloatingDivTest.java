/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.calc.FloatingIntegerDivNode;
import org.graalvm.compiler.nodes.calc.FloatingIntegerRemNode;
import org.graalvm.compiler.nodes.calc.SignedDivNode;
import org.graalvm.compiler.nodes.calc.SignedRemNode;
import org.graalvm.compiler.options.OptionValues;
import org.junit.Assert;
import org.junit.Test;

public class FloatingDivTest extends GraalCompilerTest {

    public static int snippet(int x) {
        int result = 0;
        for (int n = 0; n < x; n++) {
            if (n % 5 == 0 && n % 3 == 0) {
                result += 1;
            } else if (n % 5 == 0) {
                result += 2;
            } else if (n % 3 == 0) {
                result += 3;
            } else {
                result += 4;
            }
        }
        return result;
    }

    private boolean noFixedDivLeft = true;
    private boolean noFloatingDivLeft = true;

    private void check(boolean c) {
        noFixedDivLeft = c;
        noFloatingDivLeft = c;
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        if (noFixedDivLeft) {
            Assert.assertEquals(0, graph.getNodes().filter(SignedDivNode.class).count());
            Assert.assertEquals(0, graph.getNodes().filter(SignedRemNode.class).count());
        }
        super.checkHighTierGraph(graph);
    }

    @Override
    protected void checkLowTierGraph(StructuredGraph graph) {
        if (noFloatingDivLeft) {
            Assert.assertEquals(0, graph.getNodes().filter(FloatingIntegerDivNode.class).count());
            Assert.assertEquals(0, graph.getNodes().filter(FloatingIntegerRemNode.class).count());
        }
        super.checkLowTierGraph(graph);
    }

    @Test
    public void test01() {
        test("snippet", 100);
    }

    public static int snippet2(int x, int y, int z) {
        int result = 0;
        for (int n = 0; n < x; n++) {
            if (n % y == 0 && n % z == 0) {
                result += 1;
            } else if (n % y == 0) {
                result += 2;
            } else if (n % z == 0) {
                result += 3;
            } else {
                result += 4;
            }
        }
        return result;
    }

    @Test
    public void test02() {
        check(false);
        test("snippet2", 100, 5, 3);
        check(true);
    }

    public static int snippet3(int a) {
        return a / -1;
    }

    @Test
    public void test03() {
        check(false);
        test("snippet3", 10);
        test("snippet3", Integer.MIN_VALUE);
        check(true);
    }

    public static int snippet4(int a, @SuppressWarnings("unused") int b) {
        int i = 0;
        for (; i < a; i++) {
            GraalDirectives.sideEffect();
        }
        int res1 = i / 100000;
        GraalDirectives.sideEffect();
        return a / (res1 + 1);
    }

    @Test
    public void test04() {
        check(false);
        OptionValues opt = new OptionValues(getInitialOptions(), GraalOptions.LoopPeeling, false);
        test(opt, "snippet4", 10, 3);
        check(true);
    }
}
