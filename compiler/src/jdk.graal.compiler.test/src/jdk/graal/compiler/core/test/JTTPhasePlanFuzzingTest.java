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
package jdk.graal.compiler.core.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.jtt.JTTTest;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class JTTPhasePlanFuzzingTest extends JTTTest {

    private String previousFuzzing;
    private int parsedGraphs;
    private int compilations;

    @Before
    public void enableFuzzing() {
        previousFuzzing = System.setProperty(COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY, "true");
        resetCache();
    }

    @After
    public void restoreFuzzing() {
        if (previousFuzzing == null) {
            System.clearProperty(COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY);
        } else {
            System.setProperty(COMPILATION_PLAN_FUZZING_SYSTEM_PROPERTY, previousFuzzing);
        }
    }

    public static int snippet(int value) {
        return value + 1;
    }

    @Override
    protected StructuredGraph parseForCompile(ResolvedJavaMethod method, CompilationIdentifier compilationId, OptionValues options) {
        parsedGraphs++;
        return super.parseForCompile(method, compilationId, options);
    }

    @Override
    protected CompilationResult compile(ResolvedJavaMethod method, StructuredGraph graph, CompilationResult result, CompilationIdentifier compilationId, OptionValues options) {
        compilations++;
        return super.compile(method, graph, result, compilationId, options);
    }

    @Test
    public void testRuntimeArgumentsReuse() {
        OptionValues options = getInitialOptions();
        ResolvedJavaMethod method = getResolvedJavaMethod("snippet");
        testAgainstExpected(options, method, executeExpected(method, null, 1), CollectionsUtil.setOf(), null, 1);
        Assert.assertEquals(1, compilations);
        int initialParses = parsedGraphs;
        InstalledCode code = cache.get().get(method).getRight();

        for (int value = 2; value < 10; value++) {
            testAgainstExpected(options, method, executeExpected(method, null, value), CollectionsUtil.setOf(), null, value);
        }
        Assert.assertSame(code, cache.get().get(method).getRight());
        Assert.assertEquals(1, compilations);
        Assert.assertEquals("Cached executions must not parse graphs to rebuild the phase plan", initialParses, parsedGraphs);

        code.invalidate();
        testAgainstExpected(options, method, executeExpected(method, null, 10), CollectionsUtil.setOf(), null, 10);
        Assert.assertEquals(2, compilations);
        Assert.assertTrue(parsedGraphs > initialParses);

        OptionValues differentOptions = new OptionValues(options, HighTier.Options.Inline, !HighTier.Options.Inline.getValue(options));
        testAgainstExpected(differentOptions, method, executeExpected(method, null, 11), CollectionsUtil.setOf(), null, 11);
        Assert.assertEquals(3, compilations);
    }

    @Test
    public void testBoundArgumentsRecompile() {
        runTest("snippet", 1);
        Assert.assertEquals(2, compilations);
        runTest("snippet", 2);
        Assert.assertEquals(3, compilations);
    }
}
