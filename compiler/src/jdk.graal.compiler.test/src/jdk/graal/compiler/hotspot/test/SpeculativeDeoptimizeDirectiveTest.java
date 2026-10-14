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
package jdk.graal.compiler.hotspot.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SpeculativeDeoptimizeDirectiveTest extends HotSpotGraalCompilerTest {

    public static int twoSites(int value) {
        if (value == 0) {
            GraalDirectives.speculativeDeoptimizeAndInvalidate();
        }
        if (value == 1) {
            GraalDirectives.speculativeDeoptimizeAndInvalidate();
        }
        return value + 1;
    }

    @BytecodeParserForceInline
    public static int callee(int value) {
        if (value == 0) {
            GraalDirectives.speculativeDeoptimizeAndInvalidate();
        }
        return value + 1;
    }

    public static int caller(int value) {
        return callee(value);
    }

    public static int callerTwice(int value) {
        return callee(value) + callee(value - 1);
    }

    @Test
    public void testOnlyTakenSiteIsOmitted() throws Exception {
        ResolvedJavaMethod method = getResolvedJavaMethod("twoSites");
        InstalledCode initial = compileWithDeoptCount(method, 2);
        Assert.assertEquals(1, initial.executeVarargs(0));
        Assert.assertFalse(initial.isValid());

        InstalledCode second = compileWithDeoptCount(method, 1);
        Assert.assertEquals(1, second.executeVarargs(0));
        Assert.assertTrue(second.isValid());
        Assert.assertEquals(2, second.executeVarargs(1));
        Assert.assertFalse(second.isValid());

        InstalledCode third = compileWithDeoptCount(method, 0);
        Assert.assertEquals(2, third.executeVarargs(1));
        Assert.assertTrue(third.isValid());
    }

    @Test
    public void testInlinedSiteUsesCallerSpeculationLog() throws Exception {
        ResolvedJavaMethod method = getResolvedJavaMethod("caller");
        InstalledCode initial = compileWithDeoptCount(method, 1);
        Assert.assertEquals(1, initial.executeVarargs(0));
        Assert.assertFalse(initial.isValid());

        InstalledCode second = compileWithDeoptCount(method, 0);
        Assert.assertEquals(1, second.executeVarargs(0));
        Assert.assertTrue(second.isValid());
    }

    @Test
    public void testInlinedCopiesShareFailedSpeculation() throws Exception {
        ResolvedJavaMethod method = getResolvedJavaMethod("callerTwice");
        InstalledCode initial = compileWithDeoptCount(method, 2);
        Assert.assertEquals(1, initial.executeVarargs(0));
        Assert.assertFalse(initial.isValid());

        InstalledCode second = compileWithDeoptCount(method, 0);
        Assert.assertEquals(1, second.executeVarargs(0));
        Assert.assertTrue(second.isValid());
        Assert.assertEquals(3, second.executeVarargs(1));
        Assert.assertTrue(second.isValid());
    }

    private InstalledCode compileWithDeoptCount(ResolvedJavaMethod method, int expectedDeopts) {
        StructuredGraph graph = parseForCompile(method);
        Assert.assertEquals(expectedDeopts, graph.getNodes(DeoptimizeNode.TYPE).count());
        return getCode(method, graph, true);
    }
}
