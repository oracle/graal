/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetSubstitutionInvocationPlugin;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.TestSnippets;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Tests snippet counters with encoded snippets on jargraal.
 */
public class SnippetCountersTest extends GraalCompilerTest {
    public static void testedMethod() {
        substitutedInvoke();
    }

    public static void substitutedInvoke() {
    }

    private TestSnippets.CounterTestSnippets.TestSnippetCounters counters;

    @Test
    public void test() {
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) getReplacements();
        try (DebugCloseable _ = replacements.suppressEncodedSnippets()) {
            OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.SnippetCounters, true);
            replacements.registerSnippetTemplateCache(new TestSnippets.CounterTestSnippets.Templates(options, getProviders()));
            replacements.encode(options);
            counters = new TestSnippets.CounterTestSnippets.TestSnippetCounters(SnippetCounter.Group::new);
            executeActual(getResolvedJavaMethod("testedMethod"), null);
            Assert.assertEquals(1L, counters.increments.value());
            Assert.assertEquals(2L, counters.doubleIncrements.value());
        }
    }

    @Override
    protected GraphBuilderConfiguration.Plugins getDefaultGraphBuilderPlugins() {
        GraphBuilderConfiguration.Plugins p = super.getDefaultGraphBuilderPlugins();
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(p.getInvocationPlugins(), SnippetCountersTest.class);
        r.register(new SnippetSubstitutionInvocationPlugin<>(TestSnippets.CounterTestSnippets.Templates.class, "substitutedInvoke") {
            @Override
            public SnippetTemplate.SnippetInfo getSnippet(TestSnippets.CounterTestSnippets.Templates templates) {
                return templates.increase;
            }

            @Override
            protected Object[] getConstantArguments(ResolvedJavaMethod targetMethod) {
                return new Object[]{counters};
            }
        });
        return p;
    }
}
