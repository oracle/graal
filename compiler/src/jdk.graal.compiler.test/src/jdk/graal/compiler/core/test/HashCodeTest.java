/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.nodes.InvokeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.tiers.MidTierContext;
import jdk.graal.compiler.phases.tiers.Suites;

import org.junit.Assert;
import org.junit.Test;

public class HashCodeTest extends GraalCompilerTest {

    static class OverrideHashCode {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    static final class DontOverrideHashCode {
    }

    public static final Object NonOverridingConstant = new Object();
    public static final Object OverridingConstant = new OverrideHashCode();

    private static void initialize(Class<?> c) {
        try {
            Class.forName(c.getName(), true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new AssertionError(e);
        }
    }

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

    public static final int identityHashCodeFoldOverridingSnippet01() {
        return System.identityHashCode(OverridingConstant);
    }

    public static final int dontOverrideHashCodeFinalClass(DontOverrideHashCode o) {
        return o.hashCode();
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
        StructuredGraph g = buildGraphAfterMidTier("identityHashCodeFoldOverridingSnippet01");
        Assert.assertEquals(0, g.getNodes().filter(InvokeNode.class).count());
    }

    @Test
    public void test06() {
        initialize(DontOverrideHashCode.class);
        StructuredGraph g = buildGraphAfterMidTier("dontOverrideHashCodeFinalClass");
        Assert.assertEquals(0, g.getNodes().filter(InvokeNode.class).count());
    }

    private StructuredGraph buildGraphAfterMidTier(String name) {
        StructuredGraph g = parseForCompile(getResolvedJavaMethod(name));
        OptionValues options = getInitialOptions();
        Suites suites = createSuites(options);
        suites.getHighTier().apply(g, getDefaultHighTierContext());
        suites.getMidTier().apply(g, new MidTierContext(getProviders(), getTargetProvider(), OptimisticOptimizations.ALL, g.getProfilingInfo()));
        return g;
    }

}
