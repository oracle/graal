/*
 * Copyright (c) 2012, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test.inlining;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.Objects;
import java.util.regex.Pattern;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.Bytecodes;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.nodes.FullInfopointNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.StructuredGraph.Builder;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.TypeSwitchNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.DirectedInliningRules;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.inlining.policy.GreedyInliningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

public class InliningTest extends GraalCompilerTest {

    @Test
    public void testInvokeStaticInlining() {
        assertInlined(getGraph("invokeStaticSnippet", false));
        assertInlined(getGraph("invokeStaticOnInstanceSnippet", false));
    }

    @SuppressWarnings("all")
    public static Boolean invokeStaticSnippet(boolean value) {
        return Boolean.valueOf(value);
    }

    @SuppressWarnings({"all", "static"})
    public static Boolean invokeStaticOnInstanceSnippet(Boolean obj, boolean value) {
        return obj.valueOf(value);
    }

    @Test
    public void testStaticBindableInlining() {
        assertInlined(getGraph("invokeConstructorSnippet", false));
        assertInlined(getGraph("invokeFinalMethodSnippet", false));
        assertInlined(getGraph("invokeMethodOnFinalClassSnippet", false));
        assertInlined(getGraph("invokeMethodOnStaticFinalFieldSnippet", false));
    }

    @Ignore("would need read elimination/EA before inlining")
    @Test
    public void testDependentStaticBindableInlining() {
        assertInlined(getGraph("invokeMethodOnFinalFieldSnippet", false));
        assertInlined(getGraph("invokeMethodOnFieldSnippet", false));
    }

    @Test
    public void testStaticBindableInliningIP() {
        assertManyMethodInfopoints(assertInlined(getGraph("invokeConstructorSnippet", true)));
        assertManyMethodInfopoints(assertInlined(getGraph("invokeFinalMethodSnippet", true)));
        assertManyMethodInfopoints(assertInlined(getGraph("invokeMethodOnFinalClassSnippet", true)));
        assertManyMethodInfopoints(assertInlined(getGraph("invokeMethodOnStaticFinalFieldSnippet", true)));
    }

    @Ignore("would need read elimination/EA before inlining")
    @Test
    public void testDependentStaticBindableInliningIP() {
        assertManyMethodInfopoints(assertInlined(getGraph("invokeMethodOnFinalFieldSnippet", true)));
        assertManyMethodInfopoints(assertInlined(getGraph("invokeMethodOnFieldSnippet", true)));
    }

    @SuppressWarnings("all")
    public static Object invokeConstructorSnippet(int value) {
        return new SuperClass(value);
    }

    @SuppressWarnings("all")
    public static int invokeFinalMethodSnippet(SuperClass superClass, SubClassA subClassA, FinalSubClass finalSubClass) {
        return superClass.publicFinalMethod() + subClassA.publicFinalMethod() + finalSubClass.publicFinalMethod() + superClass.protectedFinalMethod() + subClassA.protectedFinalMethod() +
                        finalSubClass.protectedFinalMethod();
    }

    @SuppressWarnings("all")
    public static int invokeMethodOnFinalClassSnippet(FinalSubClass finalSubClass) {
        return finalSubClass.publicFinalMethod() + finalSubClass.publicNotOverriddenMethod() + finalSubClass.publicOverriddenMethod() + finalSubClass.protectedFinalMethod() +
                        finalSubClass.protectedNotOverriddenMethod() + finalSubClass.protectedOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeMethodOnStaticFinalFieldSnippet() {
        return StaticFinalFields.NumberStaticFinalField.intValue() + StaticFinalFields.SuperClassStaticFinalField.publicOverriddenMethod() +
                        StaticFinalFields.FinalSubClassStaticFinalField.publicOverriddenMethod() + StaticFinalFields.SingleImplementorStaticFinalField.publicOverriddenMethod() +
                        StaticFinalFields.MultipleImplementorsStaticFinalField.publicOverriddenMethod() + StaticFinalFields.SubClassAStaticFinalField.publicOverriddenMethod() +
                        StaticFinalFields.SubClassBStaticFinalField.publicOverriddenMethod() + StaticFinalFields.SubClassCStaticFinalField.publicOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeMethodOnFinalFieldSnippet() {
        FinalFields fields = new FinalFields();
        return fields.numberFinalField.intValue() + fields.superClassFinalField.publicOverriddenMethod() + fields.finalSubClassFinalField.publicOverriddenMethod() +
                        fields.singleImplementorFinalField.publicOverriddenMethod() + fields.multipleImplementorsFinalField.publicOverriddenMethod() +
                        fields.subClassAFinalField.publicOverriddenMethod() + fields.subClassBFinalField.publicOverriddenMethod() + fields.subClassCFinalField.publicOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeMethodOnFieldSnippet() {
        Fields fields = new Fields();
        return fields.numberField.intValue() + fields.superClassField.publicOverriddenMethod() + fields.finalSubClassField.publicOverriddenMethod() +
                        fields.singleImplementorField.publicOverriddenMethod() + fields.multipleImplementorsField.publicOverriddenMethod() + fields.subClassAField.publicOverriddenMethod() +
                        fields.subClassBField.publicOverriddenMethod() + fields.subClassCField.publicOverriddenMethod();
    }

    public interface Attributes {

        int getLength();
    }

    public class NullAttributes implements Attributes {

        @Override
        public int getLength() {
            return 0;
        }

    }

    public class TenAttributes implements Attributes {

        @Override
        public int getLength() {
            return 10;
        }

    }

    public int getAttributesLength(Attributes a) {
        return a.getLength();
    }

    @Test
    public void testGuardedInline() {
        NullAttributes nullAttributes = new NullAttributes();
        for (int i = 0; i < 10000; i++) {
            getAttributesLength(nullAttributes);
        }
        getAttributesLength(new TenAttributes());

        test("getAttributesLength", nullAttributes);
        test("getAttributesLength", (Object) null);
    }

    @Test
    public void testClassHierarchyAnalysis() {
        assertInlined(getGraph("invokeLeafClassMethodSnippet", false));
        assertInlined(getGraph("invokeConcreteMethodSnippet", false));
        assertInlined(getGraph("invokeSingleImplementorInterfaceSnippet", false));

        assertNotInlined(getGraph("invokeOverriddenPublicMethodSnippet", false));
        assertNotInlined(getGraph("invokeOverriddenProtectedMethodSnippet", false));
        assertNotInlined(getGraph("invokeOverriddenInterfaceMethodSnippet", false));
    }

    @Test
    public void testClassHierarchyAnalysisIP() {
        assertManyMethodInfopoints(assertInlined(getGraph("invokeLeafClassMethodSnippet", true)));
        assertManyMethodInfopoints(assertInlined(getGraph("invokeConcreteMethodSnippet", true)));
        assertManyMethodInfopoints(assertInlined(getGraph("invokeSingleImplementorInterfaceSnippet", true)));

        assertFewMethodInfopoints(assertNotInlined(getGraph("invokeOverriddenPublicMethodSnippet", true)));
        assertFewMethodInfopoints(assertNotInlined(getGraph("invokeOverriddenProtectedMethodSnippet", true)));
        assertFewMethodInfopoints(assertNotInlined(getGraph("invokeOverriddenInterfaceMethodSnippet", true)));
    }

    public static void traceInliningTest() {
        callTrivial();
    }

    @Test
    public void testForceInlineIgnoresMethodInlineBailoutLimit() {
        ResolvedJavaMethod checkIndex = getResolvedJavaMethod(Objects.class, "checkIndex", int.class, int.class);
        Assert.assertTrue(checkIndex.shouldBeInlined());
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.MethodInlineBailoutLimit, 5);
        StructuredGraph graph = getGraph("forceInlineBailoutSnippet", options, false, checkIndex, 10);
        Assert.assertEquals("Force-inlined Objects.checkIndex invokes should be inlined despite the bailout limit.", 0, countInvokesTo(graph, checkIndex));
    }

    @SuppressWarnings("all")
    public static int forceInlineBailoutSnippet(int value) {
        int index = value & 7;
        return Objects.checkIndex(index, 10) +
                        Objects.checkIndex(index + 1, 10) +
                        Objects.checkIndex(index + 2, 10) +
                        Objects.checkIndex(index + 3, 10) +
                        Objects.checkIndex(index + 4, 10) +
                        Objects.checkIndex(index + 5, 10) +
                        Objects.checkIndex(index + 6, 10) +
                        Objects.checkIndex(index + 7, 10) +
                        Objects.checkIndex(index + 8, 16) +
                        Objects.checkIndex(index + 9, 16);
    }

    @Test
    public void testUnsetDirectedInliningOptionsDoNotBypassNormalInliningPolicy() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.MethodInlineBailoutLimit, 2,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("without directed inlining options, the normal inlining policy should still own this invoke", 1,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
    }

    @Test
    public void testManualInliningPhaseWithoutOptionsDoesNotParseGraphDirectedRules() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        ResolvedJavaMethod method = getResolvedJavaMethod("directedInlineRoot");
        StructuredGraph graph = parseForCompile(method, options);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(),
                        OptimisticOptimizations.ALL);
        createCanonicalizerPhase().apply(graph, context);
        new InliningPhase(new GreedyInliningPolicy(null), createCanonicalizerPhase()).apply(graph, context);
        createCanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        Assert.assertEquals("without constructor options, the manual inlining phase should not parse directed rules from the graph", 1,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
    }

    @Test
    public void testDirectedInliningIgnoresMethodInlineBailoutLimit() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller->directedInlineTarget",
                        InliningPhase.Options.MethodInlineBailoutLimit, 2,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        ResolvedJavaMethod directedInlineRemainder = getResolvedJavaMethod("directedInlineRemainder");
        Assert.assertEquals("directed caller should be revisited after the exploration bailout", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("directed target should be inlined after the exploration bailout", 0, countInvokesTo(graph, directedInlineTarget));
        Assert.assertEquals("non-directed callee should remain while satisfying directed inlining after bailout", 1, countInvokesTo(graph, directedInlineRemainder));
    }

    @Test
    public void testDirectedInliningIgnoresSizeLimits() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller->directedInlineTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        ResolvedJavaMethod directedInlineRemainder = getResolvedJavaMethod("directedInlineRemainder");
        Assert.assertEquals("directed caller should be inlined to expose the requested edge", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("directed target should be inlined despite inlining size limits", 0, countInvokesTo(graph, directedInlineTarget));
        Assert.assertEquals("non-directed callee should remain while continuing only to satisfy directed inlining", 1, countInvokesTo(graph, directedInlineRemainder));
    }

    @Test
    public void testDirectedInliningDoesNotExploreUnrelatedInvokesAfterSizeLimit() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller->directedInlineTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.TrivialInliningSize, 1000);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        ResolvedJavaMethod directedInlineRemainder = getResolvedJavaMethod("directedInlineRemainder");
        Assert.assertEquals("directed caller should be inlined despite the graph size limit", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("directed target should be inlined despite the graph size limit", 0, countInvokesTo(graph, directedInlineTarget));
        Assert.assertEquals("unrelated trivial callee should not be explored just because the caller has a directed invoke", 1, countInvokesTo(graph, directedInlineRemainder));
    }

    @Test
    public void testDirectedInliningForcesFullChain() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller->directedInlineTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("full-chain rule should force the root-to-inlined-caller edge", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
        Assert.assertEquals("full-chain rule should force the terminal edge", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineTarget")));
    }

    @Test
    public void testDirectedInliningSupportsRootToCalleeRule() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("direct root-to-callee rule should inline the root invoke", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
        Assert.assertEquals("callee of the directed root invoke should not be forced by the two-part rule", 1,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineTarget")));
    }

    @Test
    public void testDirectedInliningSupportsRootInvokeBci() {
        int rootInvokeBci = invokeBci(getResolvedJavaMethod("directedInlineRoot"), 0);
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot@" + rootInvokeBci + "->directedInlineCaller",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("directed root invoke should match the root bci", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
    }

    @Test
    public void testDirectedInliningMatchesRootBciForNestedCallsite() {
        int rootInvokeBci = invokeBci(getResolvedJavaMethod("directedInlineTwiceRoot"), 0);
        int targetInvokeBci = invokeBci(getResolvedJavaMethod("directedInlineCaller"), 0);
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline,
                        "directedInlineTwiceRoot->directedInlineCaller,directedInlineTwiceRoot@" + rootInvokeBci +
                                        "->directedInlineCaller@" + targetInvokeBci + "->directedInlineTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineTwiceRoot", options, false);
        Assert.assertEquals("root-to-caller rule should inline both caller invokes", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
        Assert.assertEquals("nested rule with root bci should inline only the target under the matching caller instance", 1,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineTarget")));
        Assert.assertEquals("non-directed callees from both caller instances should remain", 2,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineRemainder")));
    }

    @Test
    public void testDirectedInliningRespectsExplicitRootInvokeAllowList() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        ResolvedJavaMethod method = getResolvedJavaMethod("directedInlineRoot");
        StructuredGraph graph = parseForCompile(method, options);
        HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(),
                        OptimisticOptimizations.ALL);
        createCanonicalizerPhase().apply(graph, context);
        new InliningPhase(new GreedyInliningPolicy(null), createCanonicalizerPhase(), new LinkedList<>(), options).apply(
                        graph, context);
        createCanonicalizerPhase().apply(graph, context);
        new DeadCodeEliminationPhase().apply(graph);
        Assert.assertEquals("directed root invoke should not bypass an explicit empty root invoke allow-list", 1,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
    }

    @Test
    public void testDirectedInliningUsesCommaSeparatedRules() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline,
                        "noSuchRoot->directedInlineCaller,directedInlineRoot->directedInlineCaller",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("directed root callee should be inlined when a later comma-separated rule matches", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
    }

    @Test
    public void testDirectedInliningRulesFile() throws IOException {
        Path rulesFile = Files.createTempFile("directed-inlining-rules", ".txt");
        try {
            Files.writeString(rulesFile, """
                            # mixed directed inlining commands
                            inline,noSuchRoot->directedInlineCaller
                            inline directedInlineRoot->directedInlineCaller
                            dontinline,directedNeverInlineRoot->directedNeverInlineCaller->directedNeverInlineTarget
                            """);

            OptionValues inlineOptions = new OptionValues(getInitialOptions(),
                            BytecodeParserOptions.InlineDuringParsing, false,
                            InliningPhase.Options.DirectedInliningRulesFile, rulesFile.toString(),
                            GraalOptions.MaximumDesiredSize, 1,
                            GraalOptions.MaximumInliningSize, 0,
                            GraalOptions.TrivialInliningSize, 0);
            StructuredGraph inlineGraph = getGraph("directedInlineRoot", inlineOptions, false);
            Assert.assertEquals("inline command from file should force the matching invoke", 0,
                            countInvokesTo(inlineGraph, getResolvedJavaMethod("directedInlineCaller")));

            OptionValues neverInlineOptions = new OptionValues(getInitialOptions(),
                            BytecodeParserOptions.InlineDuringParsing, false,
                            GraalOptions.TraceInlining, true,
                            InliningPhase.Options.DirectedInliningRulesFile, rulesFile.toString());
            StructuredGraph neverInlineGraph;
            try (TTY.Filter _ = new TTY.Filter()) {
                neverInlineGraph = getGraph("directedNeverInlineRoot", neverInlineOptions, false);
            }
            Assert.assertEquals("dontinline command should keep the matching target as an invoke", 1,
                            countInvokesTo(neverInlineGraph, getResolvedJavaMethod("directedNeverInlineTarget")));
        } finally {
            Files.deleteIfExists(rulesFile);
        }
    }

    @Test
    public void testDirectedInliningRulesFileReportsLineNumber() throws IOException {
        Path rulesFile = Files.createTempFile("directed-inlining-rules", ".txt");
        try {
            Files.writeString(rulesFile, """
                            # comment
                            badcommand A.a->B.b
                            """);
            IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
                            () -> DirectedInliningRules.parse(null, null, rulesFile.toString()));
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains(rulesFile + ":2"));
            Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("inline <rule>"));
        } finally {
            Files.deleteIfExists(rulesFile);
        }
    }

    @Test
    public void testDirectedInliningRejectsMethodFilterSyntax() {
        IllegalArgumentException exception = Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.*->C.c"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("single positive MethodFilter pattern"));

        exception = Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("~A.a->C.c"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("single positive MethodFilter pattern"));

        exception = Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.a->B.b|A.a->C.c"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("use ',' to separate rules"));

        Assert.assertNotNull(parseDirectedInlineRule("A.a@1->B.b@2->C.c"));
        Assert.assertNotNull(parseDirectedInlineRule("A.a@1->B.b@2->C.c@3->D.d"));
        Assert.assertNotNull(parseDirectedInlineRule("A.a@1->InterfaceB{B_1}.b@2->C.c"));
        Assert.assertNotNull(parseDirectedInlineRule("A.a(int[])->InterfaceB{B_1}.b(Object[])->C.c(int[])"));

        exception = Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.a@1->InterfaceB{B_1,B_2}.b"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("exactly one receiver type"));

        exception = Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.a@1->InterfaceB{B_1,B_2}.b@2->C.c"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("exactly one receiver type"));

        exception = Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.a->C.c@1"));
        Assert.assertTrue(exception.getMessage(), exception.getMessage().contains("single positive MethodFilter pattern"));

        Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.a->InterfaceB{}.b"));
        Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A.a->InterfaceB{B_*}.b"));
        Assert.assertThrows(IllegalArgumentException.class,
                        () -> parseDirectedInlineRule("A{B_1}.a->C.c"));
    }

    private static DirectedInliningRules parseDirectedInlineRule(String rule) {
        return DirectedInliningRules.parse(rule, null, null).inlineRules();
    }

    @Test
    public void testDirectedInliningRequiresMatchingBci() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInlineRoot->directedInlineCaller@-123456->directedInlineTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("directed root callee should still be inlined", 0, countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
        Assert.assertEquals("directed target should remain when the rule bci does not match", 1, countInvokesTo(graph, getResolvedJavaMethod("directedInlineTarget")));
    }

    @Test
    public void testDirectedInliningMatchesInvokeBciWithoutSourcePositions() {
        int targetInvokeBci = invokeBci(getResolvedJavaMethod("directedInlineCaller"), 0);
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline,
                        "directedInlineRoot->directedInlineCaller@" + targetInvokeBci + "->directedInlineTarget",
                        GraalOptions.TrackNodeSourcePosition, false,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("directed caller should be inlined to expose the requested edge", 0, countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
        Assert.assertEquals("directed target should match the invoke bci even without source positions", 0, countInvokesTo(graph, getResolvedJavaMethod("directedInlineTarget")));
        Assert.assertEquals("non-directed callee should remain", 1, countInvokesTo(graph, getResolvedJavaMethod("directedInlineRemainder")));
    }

    @Test
    public void testDirectedInliningMatchesParseTimeInlinedCallerWithoutSourcePositions() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, true,
                        InliningPhase.Options.DirectedInline, "parseTimeDirectedInlineRoot->parseTimeDirectedInlineCaller->parseTimeDirectedInlineTarget",
                        GraalOptions.TrackNodeSourcePosition, false,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("parseTimeDirectedInlineRoot", options, false);
        ResolvedJavaMethod parseTimeDirectedInlineCaller = getResolvedJavaMethod("parseTimeDirectedInlineCaller");
        ResolvedJavaMethod parseTimeDirectedInlineTarget = getResolvedJavaMethod("parseTimeDirectedInlineTarget");
        ResolvedJavaMethod parseTimeDirectedInlineRemainder = getResolvedJavaMethod("parseTimeDirectedInlineRemainder");
        Assert.assertEquals("parse-time caller should already be inlined", 0, countInvokesTo(graph, parseTimeDirectedInlineCaller));
        Assert.assertEquals("directed target should match the parse-time caller through frame states", 0, countInvokesTo(graph, parseTimeDirectedInlineTarget));
        Assert.assertEquals("non-directed parse-time callee should remain", 1, countInvokesTo(graph, parseTimeDirectedInlineRemainder));
    }

    public static int directedInlineRoot(int value) {
        return directedInlineCaller(value) + value;
    }

    public static int directedInlineTwiceRoot(int value) {
        return directedInlineCaller(value) + directedInlineCaller(value + 1);
    }

    private static int directedInlineCaller(int value) {
        return directedInlineTarget(value) + directedInlineRemainder(value);
    }

    private static int directedInlineTarget(int value) {
        return value + 3;
    }

    private static int directedInlineRemainder(int value) {
        return value + 5;
    }

    public static int parseTimeDirectedInlineRoot(int value) {
        return parseTimeDirectedInlineCaller(value) + value;
    }

    @BytecodeParserForceInline
    private static int parseTimeDirectedInlineCaller(int value) {
        return parseTimeDirectedInlineTarget(value) + parseTimeDirectedInlineRemainder(value);
    }

    private static int parseTimeDirectedInlineTarget(int value) {
        return value + 13;
    }

    private static int parseTimeDirectedInlineRemainder(int value) {
        return value + 17;
    }

    @Test
    public void testDirectedInliningBailoutRecoveryUsesAssumptionBackedRootCallee() {
        new DirectedAssumptionLeaf().directedAssumptionCaller(0);
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedAssumptionRoot->DirectedAssumptionBase.directedAssumptionCaller",
                        InliningPhase.Options.MethodInlineBailoutLimit, 2,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedAssumptionRoot", options, false);
        Assert.assertEquals("assumption-backed directed root callee should be revisited after the exploration bailout", 0,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedAssumptionBase.class, "directedAssumptionCaller")));
    }

    public static int directedAssumptionRoot(DirectedAssumptionBase receiver, int value) {
        return receiver.directedAssumptionCaller(value) + value;
    }

    abstract static class DirectedAssumptionBase {
        abstract int directedAssumptionCaller(int value);
    }

    static final class DirectedAssumptionLeaf extends DirectedAssumptionBase {
        @Override
        int directedAssumptionCaller(int value) {
            return directedAssumptionTarget(value) + directedAssumptionRemainder(value);
        }
    }

    private static int directedAssumptionTarget(int value) {
        return value + 19;
    }

    private static int directedAssumptionRemainder(int value) {
        return value + 23;
    }

    @Test
    public void testDirectedInliningBailoutRecoveryUsesProfileBackedInterfaceRootCallee() {
        DirectedInterface receiver = new DirectedInterfaceLeaf();
        for (int i = 0; i < 10000; i++) {
            directedInterfaceRoot(receiver, i);
        }
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedInterfaceRoot->DirectedInterface.directedInterfaceCaller",
                        InliningPhase.Options.MethodInlineBailoutLimit, 2,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInterfaceRoot", options, false);
        Assert.assertEquals("profile-backed interface root callee should be revisited after the exploration bailout", 0,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedInterface.class, "directedInterfaceCaller")));
    }

    @Test
    public void testDirectedInliningMatchesVirtualIntermediateCaller() {
        DirectedInterface receiver = new DirectedInterfaceLeaf();
        for (int i = 0; i < 10000; i++) {
            directedInterfaceRoot(receiver, i);
        }
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline,
                        "directedInterfaceRoot->DirectedInterface{DirectedInterfaceLeaf}.directedInterfaceCaller->directedInterfaceTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInterfaceRoot", options, false);
        Assert.assertEquals("virtual intermediate component should match the declared target while exploring the concrete callee graph", 0,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedInterface.class, "directedInterfaceCaller")));
        Assert.assertEquals("full-chain rule should force the target inside the selected concrete implementation", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInterfaceTarget")));
    }

    @Test
    public void testDirectedInliningRequiresReceiverTypeForVirtualIntermediateCaller() {
        ResolvedJavaMethod root = getResolvedJavaMethod("directedInterfaceRoot");
        ResolvedJavaMethod declaredCaller = getResolvedJavaMethod(DirectedInterface.class, "directedInterfaceCaller");
        ResolvedJavaMethod concreteCaller = getResolvedJavaMethod(DirectedInterfaceLeaf.class, "directedInterfaceCaller");
        ResolvedJavaType receiverType = getMetaAccess().lookupJavaType(DirectedInterfaceLeaf.class);
        ResolvedJavaMethod target = getResolvedJavaMethod("directedInterfaceTarget");
        DirectedInliningRules.Callsite[] rootCallsite = {new DirectedInliningRules.Callsite(root, DirectedInliningRules.ANY_BCI)};
        DirectedInliningRules.Callsite[] concreteCallerCallsite = {
                        new DirectedInliningRules.Callsite(root, DirectedInliningRules.ANY_BCI),
                        new DirectedInliningRules.Callsite(declaredCaller, receiverType, concreteCaller, DirectedInliningRules.ANY_BCI)};

        DirectedInliningRules unqualifiedRule = parseDirectedInlineRule("directedInterfaceRoot->DirectedInterface.directedInterfaceCaller->directedInterfaceTarget");
        Assert.assertFalse("unqualified virtual intermediate component should not force a concrete receiver path",
                        unqualifiedRule.matchesOrPrefix(rootCallsite, concreteCaller, receiverType, declaredCaller));
        Assert.assertFalse("unqualified virtual intermediate component should not match the terminal edge inside a concrete implementation",
                        unqualifiedRule.matches(concreteCallerCallsite, target, null, target));

        DirectedInliningRules qualifiedRule = parseDirectedInlineRule("directedInterfaceRoot->DirectedInterface{DirectedInterfaceLeaf}.directedInterfaceCaller->directedInterfaceTarget");
        Assert.assertTrue("receiver-qualified virtual intermediate component should force the selected receiver path",
                        qualifiedRule.matchesOrPrefix(rootCallsite, concreteCaller, receiverType, declaredCaller));
        Assert.assertTrue("receiver-qualified virtual intermediate component should match the terminal edge inside the selected concrete implementation",
                        qualifiedRule.matches(concreteCallerCallsite, target, null, target));
    }

    @Test
    public void testDirectedInliningForcesMatchingPolymorphicCallee() {
        new DirectedPolymorphicA().directedPolymorphicTarget();
        new DirectedPolymorphicB().directedPolymorphicTarget();
        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicB.class), 0.5D)};
        String directedTarget = "directedPolymorphicRoot->DirectedPolymorphicBase{DirectedPolymorphicA}.directedPolymorphicTarget";
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, directedTarget,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraphWithTypeProfile("directedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("directed inline should force only the matching receiver type and leave the other receiver type on the fallback invoke", 1,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget")));
        Assert.assertEquals("partial polymorphic directed inline should keep the receiver dispatch", 1, graph.getNodes().filter(TypeSwitchNode.class).count());
    }

    @Test
    public void testDirectedInliningUsesDeclaredPolymorphicCallee() {
        new DirectedPolymorphicA().directedPolymorphicTarget();
        new DirectedPolymorphicB().directedPolymorphicTarget();
        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicB.class), 0.5D)};
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedPolymorphicRoot->DirectedPolymorphicBase.directedPolymorphicTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraphWithTypeProfile("directedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("declared polymorphic directed inline should force all concrete targets", 0,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget")));
    }

    @Test
    public void testDirectedInliningConcreteCalleeDoesNotMatchDeclaredPolymorphicCallsite() {
        new DirectedPolymorphicA().directedPolymorphicTarget();
        new DirectedPolymorphicB().directedPolymorphicTarget();
        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicB.class), 0.5D)};
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, "directedPolymorphicRoot->DirectedPolymorphicA.directedPolymorphicTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraphWithTypeProfile("directedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("concrete callee rule should not select from a virtual callsite declared as the base method", 1,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget")));
        Assert.assertEquals("no polymorphic target should be forced by a concrete callee rule", 0, graph.getNodes().filter(TypeSwitchNode.class).count());
    }

    @Test
    public void testDirectedInliningFiltersReceiverTypeWhenConcreteMethodIsShared() {
        new DirectedInheritedPolymorphicA().directedInheritedPolymorphicTarget();
        new DirectedInheritedPolymorphicB().directedInheritedPolymorphicTarget();
        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(DirectedInheritedPolymorphicA.class), 0.5D),
                        new ProfiledType(metaAccess.lookupJavaType(DirectedInheritedPolymorphicB.class), 0.5D)};
        String directedTarget = "directedInheritedPolymorphicRoot->" +
                        "DirectedInheritedPolymorphicBase{DirectedInheritedPolymorphicA}.directedInheritedPolymorphicTarget";
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedInline, directedTarget,
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraphWithTypeProfile("directedInheritedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedInheritedPolymorphicBase.class, "directedInheritedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("directed inline should filter by receiver type even when both receiver types share the same concrete method",
                        1,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedInheritedPolymorphicBase.class, "directedInheritedPolymorphicTarget")));
    }

    @Test
    public void testDirectedNeverInliningFiltersMatchingPolymorphicCallee() {
        new DirectedPolymorphicA().directedPolymorphicTarget();
        new DirectedPolymorphicB().directedPolymorphicTarget();
        MetaAccessProvider metaAccess = getMetaAccess();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(metaAccess.lookupJavaType(DirectedPolymorphicB.class), 0.5D)};
        String directedTarget = "directedPolymorphicRoot->DirectedPolymorphicBase{DirectedPolymorphicA}.directedPolymorphicTarget";
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedDontInline, directedTarget);
        StructuredGraph graph = getGraphWithTypeProfile("directedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("directed dont-inline should leave the matching concrete target on the fallback invoke while other targets remain eligible", 1,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget")));
        Assert.assertEquals("partial polymorphic directed dont-inline should keep the receiver dispatch", 1, graph.getNodes().filter(TypeSwitchNode.class).count());
    }

    public static int directedInterfaceRoot(DirectedInterface receiver, int value) {
        return receiver.directedInterfaceCaller(value) + value;
    }

    interface DirectedInterface {
        int directedInterfaceCaller(int value);
    }

    static final class DirectedInterfaceLeaf implements DirectedInterface {
        @Override
        public int directedInterfaceCaller(int value) {
            return directedInterfaceTarget(value) + directedInterfaceRemainder(value);
        }
    }

    private static int directedInterfaceTarget(int value) {
        return value + 29;
    }

    private static int directedInterfaceRemainder(int value) {
        return value + 31;
    }

    public static int directedPolymorphicRoot(DirectedPolymorphicBase receiver) {
        return receiver.directedPolymorphicTarget();
    }

    abstract static class DirectedPolymorphicBase {
        abstract int directedPolymorphicTarget();
    }

    static final class DirectedPolymorphicA extends DirectedPolymorphicBase {
        @Override
        int directedPolymorphicTarget() {
            return 37;
        }
    }

    static final class DirectedPolymorphicB extends DirectedPolymorphicBase {
        @Override
        int directedPolymorphicTarget() {
            return 41;
        }
    }

    public static int directedInheritedPolymorphicRoot(DirectedInheritedPolymorphicBase receiver) {
        return receiver.directedInheritedPolymorphicTarget();
    }

    abstract static class DirectedInheritedPolymorphicBase {
        int directedInheritedPolymorphicTarget() {
            return 43;
        }
    }

    static final class DirectedInheritedPolymorphicA extends DirectedInheritedPolymorphicBase {
    }

    static final class DirectedInheritedPolymorphicB extends DirectedInheritedPolymorphicBase {
    }

    @Test
    public void testDirectedNeverInliningPreservesMatchingTarget() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        GraalOptions.TraceInlining, true,
                        InliningPhase.Options.DirectedDontInline, "directedNeverInlineRoot->directedNeverInlineCaller->directedNeverInlineTarget");
        StructuredGraph graph;
        try (TTY.Filter _ = new TTY.Filter()) {
            graph = getGraph("directedNeverInlineRoot", options, false);
        }
        Assert.assertEquals("directed dont-inline caller should still be inlined to expose the requested edge", 0, countInvokesTo(graph, getResolvedJavaMethod("directedNeverInlineCaller")));
        Assert.assertEquals("directed dont-inline target should remain as an invoke", 1, countInvokesTo(graph, getResolvedJavaMethod("directedNeverInlineTarget")));
        String inliningTree = graph.getInliningLog().formatAsTree(false);
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("directedNeverInlineTarget"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("directed dont-inline directive matched"));
    }

    @Test
    public void testDirectedNeverInliningDoesNotForceInlinedCaller() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        BytecodeParserOptions.InlineDuringParsing, false,
                        InliningPhase.Options.DirectedDontInline, "directedInlineRoot->directedInlineCaller->directedInlineTarget",
                        GraalOptions.MaximumDesiredSize, 1,
                        GraalOptions.MaximumInliningSize, 0,
                        GraalOptions.TrivialInliningSize, 0);
        StructuredGraph graph = getGraph("directedInlineRoot", options, false);
        Assert.assertEquals("nested dont-inline rule should not force the root-to-inlined-caller edge", 1,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInlineCaller")));
    }

    public static int directedNeverInlineRoot(int value) {
        return directedNeverInlineCaller(value) + value;
    }

    @BytecodeParserForceInline
    private static int directedNeverInlineCaller(int value) {
        return directedNeverInlineTarget(value) + directedNeverInlineRemainder(value);
    }

    private static int directedNeverInlineTarget(int value) {
        return value + 7;
    }

    private static int directedNeverInlineRemainder(int value) {
        return value + 11;
    }

    private static void callTrivial() {
        callNonTrivial();
    }

    private static double callNonTrivial() {
        double x = 0.0;
        for (int i = 0; i < 10; i++) {
            x += i * 1.21;
        }
        return x;
    }

    @Test
    public void testTracing() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.TraceInlining, true);
        StructuredGraph graph;
        try (TTY.Filter _ = new TTY.Filter()) {
            graph = getGraph("traceInliningTest", options, false);
        }
        String inliningTree = graph.getInliningLog().formatAsTree(false);
        String expectedRegex = "compilation of jdk.graal.compiler.core.test.inlining.InliningTest.traceInliningTest.*: \\R" +
                        "  at .*jdk.graal.compiler.core.test.inlining.InliningTest.traceInliningTest.*: <GraphBuilderPhase> jdk.graal.compiler.core.test.inlining.InliningTest.callTrivial.*: yes, inline method\\R" +
                        "    at .*jdk.graal.compiler.core.test.inlining.InliningTest.callTrivial.*: .*\\R" +
                        "       .*<GraphBuilderPhase> jdk.graal.compiler.core.test.inlining.InliningTest.callNonTrivial.*: .*(.*\\R)*" +
                        "       .*<InliningPhase> jdk.graal.compiler.core.test.inlining.InliningTest.callNonTrivial.*: .*(.*\\R)*";
        Pattern expectedPattern = Pattern.compile(expectedRegex, Pattern.MULTILINE);
        Assert.assertTrue("Got: " + inliningTree, expectedPattern.matcher(inliningTree).matches());
    }

    @SuppressWarnings("all")
    public static int invokeLeafClassMethodSnippet(SubClassA subClassA) {
        return subClassA.publicFinalMethod() + subClassA.publicNotOverriddenMethod() + subClassA.publicOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeConcreteMethodSnippet(SuperClass superClass) {
        return superClass.publicNotOverriddenMethod() + superClass.protectedNotOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeSingleImplementorInterfaceSnippet(SingleImplementorInterface testInterface) {
        return testInterface.publicNotOverriddenMethod() + testInterface.publicOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeConcreteInterfaceMethodSnippet(MultipleImplementorsInterface testInterface) {
        return testInterface.publicNotOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeOverriddenInterfaceMethodSnippet(MultipleImplementorsInterface testInterface) {
        return testInterface.publicOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeOverriddenPublicMethodSnippet(SuperClass superClass) {
        return superClass.publicOverriddenMethod();
    }

    @SuppressWarnings("all")
    public static int invokeOverriddenProtectedMethodSnippet(SuperClass superClass) {
        return superClass.protectedOverriddenMethod();
    }

    private StructuredGraph getGraph(final String snippet, final boolean eagerInfopointMode) {
        return getGraph(snippet, null, eagerInfopointMode);
    }

    private StructuredGraph getGraph(final String snippet, OptionValues options, final boolean eagerInfopointMode) {
        return getGraph(snippet, options, eagerInfopointMode, null, 0);
    }

    private StructuredGraph getGraph(final String snippet, OptionValues options, final boolean eagerInfopointMode, ResolvedJavaMethod expectedInvokeTarget, int expectedInvokesBeforeInlining) {
        DebugContext debug = options == null ? getDebugContext() : getDebugContext(options, null, null);
        try (DebugContext.Scope _ = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            Builder builder = builder(method, AllowAssumptions.YES, debug);
            StructuredGraph graph = eagerInfopointMode ? parse(builder, getDebugGraphBuilderSuite()) : parse(builder, getEagerGraphBuilderSuite());
            try (DebugContext.Scope _ = debug.scope("Inlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = eagerInfopointMode
                                ? getCustomGraphBuilderSuite(GraphBuilderConfiguration.getDefault(getDefaultGraphBuilderPlugins()).withFullInfopoints(true))
                                : getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                if (expectedInvokeTarget != null) {
                    Assert.assertEquals("Original invokes of method " + expectedInvokeTarget, expectedInvokesBeforeInlining, countInvokesTo(graph, expectedInvokeTarget));
                }
                createInliningPhase().apply(graph, context);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                new DeadCodeEliminationPhase().apply(graph);
                return graph;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private StructuredGraph getGraphWithTypeProfile(final String snippet, OptionValues options,
                    ResolvedJavaMethod profiledTargetMethod, JavaTypeProfile profile) {
        DebugContext debug = options == null ? getDebugContext() : getDebugContext(options, null, null);
        try (DebugContext.Scope _ = debug.scope("InliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            StructuredGraph graph = parseForCompile(method, options);
            injectTypeProfile(graph, profiledTargetMethod, profile);
            try (DebugContext.Scope _ = debug.scope("Inlining", graph)) {
                HighTierContext context = new HighTierContext(getProviders(), getDefaultGraphBuilderSuite(),
                                OptimisticOptimizations.ALL);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                createInliningPhase().apply(graph, context);
                debug.dump(DebugContext.BASIC_LEVEL, graph, "Graph");
                createCanonicalizerPhase().apply(graph, context);
                new DeadCodeEliminationPhase().apply(graph);
                return graph;
            }
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    private static void injectTypeProfile(StructuredGraph graph, ResolvedJavaMethod targetMethod, JavaTypeProfile profile) {
        for (MethodCallTargetNode callTargetNode : graph.getNodes(MethodCallTargetNode.TYPE)) {
            if (targetMethod.equals(callTargetNode.targetMethod())) {
                callTargetNode.setJavaTypeProfile(profile);
            }
        }
    }

    private static int countInvokesTo(StructuredGraph graph, ResolvedJavaMethod target) {
        int count = 0;
        for (Invoke invoke : graph.getInvokes()) {
            if (target.equals(invoke.getTargetMethod())) {
                count++;
            }
        }
        return count;
    }

    private static int invokeBci(ResolvedJavaMethod method, int invokeIndex) {
        BytecodeStream stream = new BytecodeStream(method.getCode());
        int invokeCount = 0;
        while (stream.currentBC() != Bytecodes.END) {
            if (Bytecodes.isInvoke(stream.currentBC()) && invokeCount++ == invokeIndex) {
                return stream.currentBCI();
            }
            stream.next();
        }
        Assert.fail("Could not find invoke " + invokeIndex + " in " + method);
        return -1;
    }

    private static StructuredGraph assertInlined(StructuredGraph graph) {
        return assertNotInGraph(graph, Invoke.class);
    }

    private static StructuredGraph assertNotInlined(StructuredGraph graph) {
        return assertInGraph(graph, Invoke.class);
    }

    private static int[] countMethodInfopoints(StructuredGraph graph) {
        int start = 0;
        int end = 0;
        for (FullInfopointNode ipn : graph.getNodes().filter(FullInfopointNode.class)) {
            if (ipn.getReason() == InfopointReason.METHOD_START) {
                ++start;
            } else if (ipn.getReason() == InfopointReason.METHOD_END) {
                ++end;
            }
        }
        return new int[]{start, end};
    }

    private static StructuredGraph assertManyMethodInfopoints(StructuredGraph graph) {
        int[] counts = countMethodInfopoints(graph);
        if (counts[0] <= 1 || counts[1] <= 1) {
            fail(String.format("Graph contains too few required method boundary infopoints: %d starts, %d ends.", counts[0], counts[1]));
        }
        return graph;
    }

    private static StructuredGraph assertFewMethodInfopoints(StructuredGraph graph) {
        int[] counts = countMethodInfopoints(graph);
        if (counts[0] > 1 || counts[1] > 1) {
            fail(String.format("Graph contains too many method boundary infopoints: %d starts, %d ends.", counts[0], counts[1]));
        }
        return graph;
    }

    // some interfaces and classes for testing
    private interface MultipleImplementorsInterface {

        int publicNotOverriddenMethod();

        int publicOverriddenMethod();
    }

    private interface SingleImplementorInterface {

        int publicNotOverriddenMethod();

        int publicOverriddenMethod();
    }

    private static class SuperClass implements MultipleImplementorsInterface {

        protected int value;

        SuperClass(int value) {
            this.value = value;
        }

        @Override
        public int publicNotOverriddenMethod() {
            return value;
        }

        @Override
        public int publicOverriddenMethod() {
            return value;
        }

        protected int protectedNotOverriddenMethod() {
            return value;
        }

        protected int protectedOverriddenMethod() {
            return value;
        }

        public final int publicFinalMethod() {
            return value + 255;
        }

        protected final int protectedFinalMethod() {
            return value + 255;
        }
    }

    private static class SubClassA extends SuperClass implements SingleImplementorInterface {

        SubClassA(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 2;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 2;
        }
    }

    private static class SubClassB extends SuperClass {

        SubClassB(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 3;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 3;
        }
    }

    private static class SubClassC extends SuperClass {

        SubClassC(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 4;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 4;
        }
    }

    private static final class FinalSubClass extends SuperClass {

        FinalSubClass(int value) {
            super(value);
        }

        @Override
        public int publicOverriddenMethod() {
            return value + 5;
        }

        @Override
        protected int protectedOverriddenMethod() {
            return value * 5;
        }
    }

    private static final class StaticFinalFields {

        private static final Number NumberStaticFinalField = Integer.valueOf(1);
        private static final SuperClass SuperClassStaticFinalField = new SubClassA(2);
        private static final FinalSubClass FinalSubClassStaticFinalField = new FinalSubClass(3);
        private static final SingleImplementorInterface SingleImplementorStaticFinalField = new SubClassA(4);
        private static final MultipleImplementorsInterface MultipleImplementorsStaticFinalField = new SubClassC(5);
        private static final SubClassA SubClassAStaticFinalField = new SubClassA(6);
        private static final SubClassB SubClassBStaticFinalField = new SubClassB(7);
        private static final SubClassC SubClassCStaticFinalField = new SubClassC(8);
    }

    private static final class FinalFields {

        private final Number numberFinalField = Integer.valueOf(1);
        private final SuperClass superClassFinalField = new SubClassA(2);
        private final FinalSubClass finalSubClassFinalField = new FinalSubClass(3);
        private final SingleImplementorInterface singleImplementorFinalField = new SubClassA(4);
        private final MultipleImplementorsInterface multipleImplementorsFinalField = new SubClassC(5);
        private final SubClassA subClassAFinalField = new SubClassA(6);
        private final SubClassB subClassBFinalField = new SubClassB(7);
        private final SubClassC subClassCFinalField = new SubClassC(8);
    }

    private static final class Fields {

        private Number numberField = Integer.valueOf(1);
        private SuperClass superClassField = new SubClassA(2);
        private FinalSubClass finalSubClassField = new FinalSubClass(3);
        private SingleImplementorInterface singleImplementorField = new SubClassA(4);
        private MultipleImplementorsInterface multipleImplementorsField = new SubClassC(5);
        private SubClassA subClassAField = new SubClassA(6);
        private SubClassB subClassBField = new SubClassB(7);
        private SubClassC subClassCField = new SubClassC(8);
    }
}
