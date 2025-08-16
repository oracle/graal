/*
 * Copyright (c) 2011, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.test.Graal;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.phases.HighTier;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.TestSnippets;
import jdk.graal.compiler.replacements.TestSnippets.TransplantTestSnippets;
import jdk.graal.compiler.replacements.nodes.LateLoweredNode;
import jdk.graal.compiler.runtime.RuntimeProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class TransplantLowLevelGraphTest extends GraalCompilerTest {
    private static DebugCloseable snippetScope;

    private static TestSnippets.TransplantTestSnippets.Templates transplantTestSnippets;

    @BeforeClass
    public static void setup() {
        Providers providers = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders();
        OptionValues options = getInitialOptions();
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) providers.getReplacements();
        snippetScope = replacements.suppressEncodedSnippets();
        transplantTestSnippets = new TestSnippets.TransplantTestSnippets.Templates(options, providers);
        replacements.encode(options);
    }

    @AfterClass
    public static void teardown() {
        snippetScope.close();
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins p = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(p.getInvocationPlugins(), TransplantTestSnippets.class);

        // register producer
        r.register(new InvocationPlugin("producer") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                JavaKind returnKind = JavaKind.Int;
                Stamp returnStamp = StampFactory.forKind(returnKind);
                ValueNode[] arguments = new ValueNode[0];
                LateLoweredNode lateMacroInvoke = new LateLoweredNode(b.bci(), targetMethod, returnStamp, arguments, null);
                lateMacroInvoke.setTemplateProducer(new Supplier<SnippetTemplate>() {
                    @Override
                    public SnippetTemplate get() {
                        Arguments args = new Arguments(transplantTestSnippets.producer, GuardsStage.AFTER_FSA, true, LoweringTool.StandardLoweringStage.LOW_TIER);
                        // no args
                        SnippetTemplate template = transplantTestSnippets.template(getProviders(), lateMacroInvoke, args);
                        return template;
                    }
                });
                b.addPush(returnKind, lateMacroInvoke);
                b.setStateAfter(lateMacroInvoke);
                return true;
            }
        });

        // register producerWithArgs
        r.register(new InvocationPlugin("producerWithArgs", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg0, ValueNode arg1) {
                JavaKind returnKind = JavaKind.Int;
                Stamp returnStamp = StampFactory.forKind(returnKind);
                ValueNode[] arguments = new ValueNode[]{arg0, arg1};
                LateLoweredNode lateMacroInvoke = new LateLoweredNode(b.bci(), targetMethod, returnStamp, arguments, null);
                lateMacroInvoke.setTemplateProducer(new Supplier<SnippetTemplate>() {
                    @Override
                    public SnippetTemplate get() {
                        Arguments args = new Arguments(transplantTestSnippets.producerWithArgs, GuardsStage.AFTER_FSA, true, LoweringTool.StandardLoweringStage.LOW_TIER);
                        args.add("a", arg0);
                        args.add("b", arg1);
                        SnippetTemplate template = transplantTestSnippets.template(getProviders(), lateMacroInvoke, args);
                        return template;
                    }
                });
                b.addPush(returnKind, lateMacroInvoke);
                b.setStateAfter(lateMacroInvoke);
                return true;
            }
        });

        // register producerWithDeopt
        r.register(new InvocationPlugin("producerWithDeopt", int.class, int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg0, ValueNode arg1) {
                JavaKind returnKind = JavaKind.Int;
                Stamp returnStamp = StampFactory.forKind(returnKind);
                ValueNode[] arguments = new ValueNode[]{arg0, arg1};
                LateLoweredNode lateMacroInvoke = new LateLoweredNode(b.bci(), targetMethod, returnStamp, arguments, null);
                lateMacroInvoke.setTemplateProducer(new Supplier<SnippetTemplate>() {
                    @Override
                    public SnippetTemplate get() {
                        Arguments args = new Arguments(transplantTestSnippets.producerWithDeopt, GuardsStage.AFTER_FSA, true, LoweringTool.StandardLoweringStage.LOW_TIER);
                        args.add("a", arg0);
                        args.add("b", arg1);
                        SnippetTemplate template = transplantTestSnippets.template(getProviders(), lateMacroInvoke, args);
                        return template;
                    }
                });
                b.addPush(returnKind, lateMacroInvoke);
                b.setStateAfter(lateMacroInvoke);
                return true;
            }
        });

        return p;
    }

    public OptionValues getOptionsWithoutInlining() {
        OptionValues opt = new OptionValues(getInitialOptions(), HighTier.Options.Inline, false);
        return opt;
    }

    public int snippet01(int a) {
        int t = TransplantTestSnippets.producer();
        if (a == 12) {
            GraalDirectives.sideEffect();
            return TransplantTestSnippets.producer();
        } else {
            return t + TransplantTestSnippets.producer();
        }
    }

    @Test
    public void test01() {
        test(getOptionsWithoutInlining(), "snippet01", 100);
    }

    public int snippet02(int a, int b) {
        int t = TransplantTestSnippets.producerWithArgs(a, b);
        for (int i = 0; i < a; i++) {
            if (a == 12) {
                GraalDirectives.sideEffect();
                t = t * 2 + TransplantTestSnippets.producerWithArgs(a, b);
            } else {
                t = t + TransplantTestSnippets.producerWithArgs(a, b);
            }
        }
        return t;
    }

    @Test
    public void test02() {
        test(getOptionsWithoutInlining(), "snippet02", 100, 200);
    }

    public static int snippet03(int a, int b) {
        int t = TransplantTestSnippets.producerWithDeopt(a, b);
        I = t;
        return t;
    }

    static Integer I;

    @Test
    public void test03() throws InvalidInstalledCodeException {
        InstalledCode ic = getCode(getResolvedJavaMethod("snippet03"), getOptionsWithoutInlining());

        ic.executeVarargs(98, 100);
        assert ic.isValid();

        ic.executeVarargs(98, 100);
        assert ic.isValid();

        ic.executeVarargs(98, 100);
        assert ic.isValid();

        ic.executeVarargs(99, 100);
        assert !ic.isValid();
    }

    public int snippet04(int a) {
        if (a == 12) {
            TransplantTestSnippets.producerWithArgs(a, a);
        } else {
            GraalDirectives.sideEffect(1);
        }
        GraalDirectives.sideEffect(2);
        GraalDirectives.controlFlowAnchor();
        return 0;
    }

    @Test
    public void test04() {
        test(getOptionsWithoutInlining(), "snippet04", 100);
    }
}
