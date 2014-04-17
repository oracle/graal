/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.jtt.lang;

import java.util.*;
import java.util.function.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.options.OptionValue.OverrideScope;
import com.oracle.graal.phases.*;

public class LambdaEagerTest extends GraalCompilerTest {

    private static final EnumSet<DeoptimizationReason> UNRESOLVED_UNREACHED = EnumSet.of(DeoptimizationReason.Unresolved, DeoptimizationReason.UnreachedCode);

    private static int doBinary(IntBinaryOperator op, int x, int y) {
        return op.applyAsInt(x, y);
    }

    private static int add(int x, int y) {
        return x + y;
    }

    public static int nonCapturing(int x, int y) {
        return doBinary((a, b) -> a + b, x, y);
    }

    public static int nonCapturing2(int x, int y) {
        return doBinary(LambdaEagerTest::add, x, y);
    }

    public static int capturing(int x, int y, int z) {
        return doBinary((a, b) -> a + b - z, x, y);
    }

    @Test
    public void testEagerResolveNonCapturing01() {
        Result expected = new Result(3, null);
        testAgainstExpected(getMethod("nonCapturing"), expected, UNRESOLVED_UNREACHED, 1, 2);
    }

    @Test
    public void testEagerResolveNonCapturing02() {
        Result expected = new Result(3, null);
        testAgainstExpected(getMethod("nonCapturing2"), expected, UNRESOLVED_UNREACHED, 1, 2);
    }

    @Test
    public void testEagerResolveCapturing() {
        Result expected = new Result(0, null);
        testAgainstExpected(getMethod("capturing"), expected, UNRESOLVED_UNREACHED, 1, 2, 3);
    }

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph, boolean forceCompile) {
        try (OverrideScope scope = OptionValue.override(GraalOptions.InlineEverything, true)) {
            return super.getCode(method, graph, forceCompile);
        }
    }
}