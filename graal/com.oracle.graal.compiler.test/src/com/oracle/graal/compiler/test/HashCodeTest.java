/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.graal.compiler.phases.HighTier;
import com.oracle.graal.compiler.phases.MidTier;
import com.oracle.graal.nodes.InvokeNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.OptimisticOptimizations;
import com.oracle.graal.phases.tiers.MidTierContext;

public class HashCodeTest extends GraalCompilerTest {

    static class OverrideHashCode {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    public static final Object NonOverridingConstant = new Object();
    public static final Object OverridingConstant = new OverrideHashCode();

    public static final int hashCodeSnippet01(Object o) {
        return o.hashCode();
    }

    public static final int systemIdentityHashCodeSnippet01(Object o) {
        return System.identityHashCode(o);
    }

    public static final int hashCodeFoldSnippet01() {
        return NonOverridingConstant.hashCode();
    }

    public static final int identityHashCodeFoldSnippet01() {
        return System.identityHashCode(NonOverridingConstant);
    }

    public static final int hashCodeNoFoldOverridingSnippet01(Object o) {
        return o.hashCode();
    }

    public static final int identityHashCodeFoldOverridingSnippet01() {
        return System.identityHashCode(OverridingConstant);
    }

    @Test
    public void test01() {
        test("hashCodeSnippet01", new Object());
    }

    @Test
    public void test02() {
        test("systemIdentityHashCodeSnippet01", new Object());
    }

    @Test
    public void test03() {
        StructuredGraph g = buildGraphAfterMidTier("hashCodeFoldSnippet01");
        Assert.assertEquals(0, g.getNodes().filter(InvokeNode.class).count());
    }

    @Test
    public void test04() {
        StructuredGraph g = buildGraphAfterMidTier("identityHashCodeFoldSnippet01");
        Assert.assertEquals(0, g.getNodes().filter(InvokeNode.class).count());
    }

    @Test
    public void test05() {
        StructuredGraph g = buildGraphAfterMidTier("hashCodeNoFoldOverridingSnippet01");
        Assert.assertEquals(1, g.getNodes().filter(InvokeNode.class).count());
    }

    @Test
    public void test06() {
        StructuredGraph g = buildGraphAfterMidTier("identityHashCodeFoldOverridingSnippet01");
        Assert.assertEquals(0, g.getNodes().filter(InvokeNode.class).count());
    }

    @SuppressWarnings("try")
    private StructuredGraph buildGraphAfterMidTier(String name) {
        StructuredGraph g = parseForCompile(getResolvedJavaMethod(name));
        new HighTier().apply(g, getDefaultHighTierContext());
        new MidTier().apply(g, new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, g.getProfilingInfo()));
        return g;
    }

}
