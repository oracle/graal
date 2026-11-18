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
package jdk.graal.compiler.replacements.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.meta.ResolvedJavaField;

/** Checks folding before template specialization as well as reuse of completed templates. */
public class SnippetFoldingPolicyTest extends SnippetsTest {
    static final class Data {
        final int value;

        Data(int value) {
            this.value = value;
        }
    }

    static final Data DATA = new Data(37);

    static class TestSnippets implements Snippets {
        @Snippet
        public static int boundField(@ConstantParameter Data data) {
            return data.value;
        }

        @Snippet
        public static int staticField() {
            return DATA.value;
        }
    }

    private static final class Templates extends SnippetTemplate.AbstractTemplates {
        private final SnippetTemplate.SnippetInfo boundField;
        private final SnippetTemplate.SnippetInfo staticField;

        Templates(OptionValues options, Providers providers) {
            super(options, providers);
            boundField = snippet(providers, TestSnippets.class, "boundField");
            staticField = snippet(providers, TestSnippets.class, "staticField");
        }

        SnippetTemplate read(Providers providers, StructuredGraph graph, boolean bound) {
            var args = new SnippetTemplate.Arguments(bound ? boundField : staticField, GuardsStage.FIXED_DEOPTS, false, LoweringTool.StandardLoweringStage.HIGH_TIER);
            if (bound) {
                args.add("data", DATA);
            }
            return template(providers, graph.start(), args);
        }
    }

    @Test
    public void constantBoundArgumentsRespectFoldingPolicy() {
        checkFolding(true, false);
        checkFolding(true, true);
    }

    @Test
    public void preparationTimeConstantsRespectFoldingPolicy() {
        checkFolding(false, false);
        checkFolding(false, true);
    }

    private void checkFolding(boolean bound, boolean useCache) {
        Providers ordinary = installer.getProviders();
        if (!bound) {
            // Warm the source-graph cache under the ordinary policy before selecting no folding.
            var prepared = installer.getSnippet(getResolvedJavaMethod(TestSnippets.class, "staticField"), null, null, null, false, null, getInitialOptions());
            Assert.assertTrue(prepared.getNodes(ReturnNode.TYPE).first().result().isConstant());
        }
        ConstantFieldProvider fields = new ConstantFieldProvider() {
            @Override
            public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
                if (testField(field)) {
                    return null;
                }
                return ordinary.getConstantFieldProvider().readConstantField(field, tool);
            }

            @Override
            public boolean isTrustedFinal(CanonicalizerTool tool, ResolvedJavaField field) {
                return !testField(field) && ordinary.getConstantFieldProvider().isTrustedFinal(tool, field);
            }

            private boolean testField(ResolvedJavaField field) {
                return field.getDeclaringClass().toJavaName().startsWith(SnippetFoldingPolicyTest.class.getName());
            }
        };
        Providers restricted = createInstaller(ordinary.copyWith(fields)).getProviders();
        Assert.assertNotSame(ordinary.getReplacements().getTemplatesCache(), restricted.getReplacements().getTemplatesCache());
        for (boolean restrictedFirst : new boolean[]{false, true}) {
            ordinary.getReplacements().getTemplatesCache().clear();
            restricted.getReplacements().getTemplatesCache().clear();
            Templates templates = new Templates(getInitialOptions().derive(SnippetTemplate.Options.UseSnippetTemplateCache, useCache), ordinary);
            StructuredGraph graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
            Providers first = restrictedFirst ? restricted : ordinary;
            Providers second = restrictedFirst ? ordinary : restricted;
            SnippetTemplate firstTemplate = templates.read(first, graph, bound);
            SnippetTemplate secondTemplate = templates.read(second, graph, bound);
            Assert.assertNotSame(firstTemplate, secondTemplate);
            assertConstant(firstTemplate, !restrictedFirst);
            assertConstant(secondTemplate, restrictedFirst);
            SnippetTemplate firstAgain = templates.read(new Providers(first), graph, bound);
            SnippetTemplate secondAgain = templates.read(new Providers(second), graph, bound);
            assertConstant(firstAgain, !restrictedFirst);
            assertConstant(secondAgain, restrictedFirst);
            Assert.assertEquals(useCache, firstTemplate == firstAgain);
            Assert.assertEquals(useCache, secondTemplate == secondAgain);
        }
    }

    private static void assertConstant(SnippetTemplate template, boolean expected) {
        StructuredGraph graph = template.getSnippet();
        Assert.assertEquals(1, graph.getNodes(ReturnNode.TYPE).count());
        var result = graph.getNodes(ReturnNode.TYPE).first().result();
        Assert.assertEquals(expected, result.isConstant());
        if (expected) {
            Assert.assertEquals(37, result.asJavaConstant().asInt());
        }
    }
}
