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

package jdk.graal.compiler.phases.common.priorityinline.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.bytecode.Bytecodes;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugDumpScope;
import jdk.graal.compiler.debug.TTY.Filter;
import jdk.graal.compiler.duplication.phases.MethodDuplicationPhase;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.inlining.InliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.DefaultInliningProvider;
import jdk.graal.compiler.phases.common.priorityinline.AbstractPriorityInliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase;
import jdk.graal.compiler.phases.common.priorityinline.nodes.GenericNode;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.JavaTypeProfile.ProfiledType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.TriState;

public class PriorityInliningDefaultTest extends PriorityInliningTest {

    private RuntimeException runtimeException = new RuntimeException("something went wrong");

    @Test
    public void testPriorityInliningIsSelectedByDefault() {
        PhaseSuite<HighTierContext> highTier = createSuites(getInitialOptions()).getHighTier();
        Assert.assertNotNull(highTier.findPhase(PriorityInliningPhase.class));
        Assert.assertNull(highTier.findPhase(InliningPhase.class));
    }

    @Test
    public void testTraditionalInliningCanBeSelected() {
        OptionValues options = new OptionValues(getInitialOptions(), PriorityInliningPhase.Options.UsePriorityInlining, false);
        PhaseSuite<HighTierContext> highTier = createSuites(options).getHighTier();
        Assert.assertNull(highTier.findPhase(PriorityInliningPhase.class));
        Assert.assertNotNull(highTier.findPhase(InliningPhase.class));
    }

    @Test
    public void testMethodDuplicationCanBeSelected() {
        Assert.assertNull(createSuites(getInitialOptions()).getHighTier().findPhase(MethodDuplicationPhase.class));
        OptionValues options = new OptionValues(getInitialOptions(), MethodDuplicationPhase.Options.OptMethodDuplication, true);
        Assert.assertNotNull(createSuites(options).getHighTier().findPhase(MethodDuplicationPhase.class));
    }

    @Test
    public void testSimpleInlining() {
        int[] src = new int[]{7};
        int[] dest = new int[1];
        test("copyArray", src, dest);
        assertInlined(getGraph("copyArray", getInitialOptions()));
    }

    public int[] copyArray(int[] src, int[] dest) {
        int i = 0;
        check(src, dest);
        while (i < src.length) {
            copyElement(src, dest, i);
            i++;
        }
        return dest;
    }

    private void check(int[] src, int[] dest) {
        if (src == null || dest == null) {
            throw runtimeException;
        }
        if (src.length != dest.length) {
            throw runtimeException;
        }
    }

    private void copyElement(int[] src, int[] dest, int idx) {
        if (idx < 0 || idx > src.length) {
            throw runtimeException;
        }
        dest[idx] = src[idx];
    }

    @Test
    public void testInliningWithGeneric() {
        int[] src = new int[]{1, 2, 3};
        int[] dest = new int[3];
        test("copyOrThrow", src, dest);
        CallTree callTree = getCallTreeAfterInlining("copyOrThrow");
        Assert.assertEquals(1, callTree.root().children().size());
        Assert.assertTrue(callTree.root().children().get(0) instanceof GenericNode);
        Assert.assertEquals("Throwable.fillInStackTrace", callTree.root().children().get(0).invoke().callTarget().targetName());
    }

    public int[] copyOrThrow(int[] src, int[] dest) {
        if (src == dest && src.length == 0 && dest.length == 0) {
            throw new RuntimeException("cannot copy");
        }
        return copyArray(src, dest);
    }

    @Test
    public void testForceInliningIgnoresRootGraphSizeLimit() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        AbstractPriorityInliningPhase.Options.PriorityForceInline, "forceInlineTarget,nestedForceInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("forceInlineLargeRootSnippet", options);
        ResolvedJavaMethod forceInlineTarget = getResolvedJavaMethod("forceInlineTarget");
        ResolvedJavaMethod nestedForceInlineTarget = getResolvedJavaMethod("nestedForceInlineTarget");
        ResolvedJavaMethod nonForceInlineTarget = getResolvedJavaMethod("nonForceInlineTarget");
        boolean foundNonForceInlineTarget = false;
        for (Invoke invoke : graph.getInvokes()) {
            Assert.assertNotEquals("forced target should be inlined even when the root graph is larger than the inlining limit", forceInlineTarget, invoke.getTargetMethod());
            Assert.assertNotEquals("nested forced target should be inlined after the final force-inlining pass exposes it", nestedForceInlineTarget, invoke.getTargetMethod());
            foundNonForceInlineTarget |= nonForceInlineTarget.equals(invoke.getTargetMethod());
        }
        Assert.assertTrue("non-forced target should not be inlined while continuing only to satisfy force inlining", foundNonForceInlineTarget);
    }

    public int forceInlineLargeRootSnippet(int value) {
        return forceInlineTarget(value) + nonForceInlineTarget(value) + value;
    }

    private static int forceInlineTarget(int value) {
        return nestedForceInlineTarget(value) + 1;
    }

    private static int nestedForceInlineTarget(int value) {
        return value + 3;
    }

    private static int nonForceInlineTarget(int value) {
        return value + 2;
    }

    @Test
    public void testRecursiveForceInliningTerminates() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        AbstractPriorityInliningPhase.Options.PriorityForceInline, "recursiveForceInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("recursiveForceInlineSnippet", options);
        ResolvedJavaMethod recursiveForceInlineTarget = getResolvedJavaMethod("recursiveForceInlineTarget");
        boolean foundRecursiveForceInlineTarget = false;
        for (Invoke invoke : graph.getInvokes()) {
            foundRecursiveForceInlineTarget |= recursiveForceInlineTarget.equals(invoke.getTargetMethod());
        }
        Assert.assertTrue("recursive forced target should remain after the recursive expansion cutoff is reached", foundRecursiveForceInlineTarget);
    }

    public int recursiveForceInlineSnippet(int value) {
        return recursiveForceInlineTarget(value);
    }

    private static int recursiveForceInlineTarget(int value) {
        if (value <= 0) {
            return 0;
        }
        return value + recursiveForceInlineTarget(value - 1);
    }

    @Test
    public void testPriorityNeverInliningTracingUsesNotUsedForInliningCause() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.TraceInlining, true,
                        AbstractPriorityInliningPhase.Options.PriorityNeverInline, "priorityNeverInlineTarget",
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph;
        try (Filter _ = new Filter()) {
            graph = getGraph("priorityNeverInlineRoot", options);
        }
        ResolvedJavaMethod priorityNeverInlineTarget = getResolvedJavaMethod("priorityNeverInlineTarget");
        Assert.assertEquals("priority never-inline target should remain as an invoke", 1, countInvokesTo(graph, priorityNeverInlineTarget));
        String inliningTree = graph.getInliningLog().formatAsTree(false);
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("priorityNeverInlineTarget"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("inlining this method is not allowed."));
    }

    public int priorityNeverInlineRoot(int value) {
        return priorityNeverInlineTarget(value) + value;
    }

    private static int priorityNeverInlineTarget(int value) {
        return value + 19;
    }

    @Test
    public void testDirectedInliningIgnoresRootGraphSizeLimit() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline,
                        "directedInlineLargeRootSnippet->directedInlineCaller->directedInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInlineLargeRootSnippet", options);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        ResolvedJavaMethod directedInlineRemainder = getResolvedJavaMethod("directedInlineRemainder");
        Assert.assertEquals("directed caller should be inlined to expose the requested edge", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("directed target should be inlined even when the root graph is larger than the inlining limit", 0, countInvokesTo(graph, directedInlineTarget));
        Assert.assertEquals("non-directed callee should not be inlined while continuing only to satisfy directed inlining", 1, countInvokesTo(graph, directedInlineRemainder));
    }

    @Test
    public void testDirectedInliningForcesFullChain() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline,
                        "directedInlineLargeRootSnippet->directedInlineCaller->directedInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInlineLargeRootSnippet", options);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        Assert.assertEquals("full-chain rule should force the root-to-inlined-caller edge", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("full-chain rule should force the terminal edge", 0, countInvokesTo(graph, directedInlineTarget));
    }

    @Test
    public void testDirectedInliningRequiresMatchingBci() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline,
                        "directedInlineLargeRootSnippet->directedInlineCaller@-123456->directedInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInlineLargeRootSnippet", options);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        Assert.assertEquals("directed root callee should still be inlined", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("directed target should remain when the rule bci does not match", 1, countInvokesTo(graph, directedInlineTarget));
    }

    @Test
    public void testDirectedInliningMatchesRootBciForNestedCallsite() {
        int rootInvokeBci = invokeBci(getResolvedJavaMethod("directedInlineTwiceRoot"), 0);
        int targetInvokeBci = invokeBci(getResolvedJavaMethod("directedInlineCaller"), 0);
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline,
                        "directedInlineTwiceRoot->directedInlineCaller,directedInlineTwiceRoot@" + rootInvokeBci +
                                        "->directedInlineCaller@" + targetInvokeBci + "->directedInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInlineTwiceRoot", options);
        ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
        ResolvedJavaMethod directedInlineTarget = getResolvedJavaMethod("directedInlineTarget");
        ResolvedJavaMethod directedInlineRemainder = getResolvedJavaMethod("directedInlineRemainder");
        Assert.assertEquals("root-to-caller rule should inline both caller invokes", 0, countInvokesTo(graph, directedInlineCaller));
        Assert.assertEquals("nested rule with root bci should inline only the target under the matching caller instance", 1, countInvokesTo(graph, directedInlineTarget));
        Assert.assertEquals("non-directed callees from both caller instances should remain", 2, countInvokesTo(graph, directedInlineRemainder));
    }

    @Test
    public void testDirectedInliningAcceptsMethodFilterSignatures() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline,
                        "directedInlineSignatureRoot(int;int)->directedInlineSignatureCaller(int;int)," +
                                        "directedInlineSignatureRoot(int;int)->directedInlineSignatureCaller(int;int)->directedInlineSignatureTarget(int;int)",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInlineSignatureRoot", options);
        ResolvedJavaMethod directedInlineSignatureCaller = getResolvedJavaMethod("directedInlineSignatureCaller");
        ResolvedJavaMethod directedInlineSignatureTarget = getResolvedJavaMethod("directedInlineSignatureTarget");
        ResolvedJavaMethod directedInlineSignatureRemainder = getResolvedJavaMethod("directedInlineSignatureRemainder");
        Assert.assertEquals("signature rule should parse and inline the directed caller", 0, countInvokesTo(graph, directedInlineSignatureCaller));
        Assert.assertEquals("signature rule should parse and inline the directed target", 0, countInvokesTo(graph, directedInlineSignatureTarget));
        Assert.assertEquals("non-directed signature callee should remain", 1, countInvokesTo(graph, directedInlineSignatureRemainder));
    }

    public int directedInlineLargeRootSnippet(int value) {
        return directedInlineCaller(value) + value;
    }

    public int directedInlineTwiceRoot(int value) {
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

    public int directedInlineSignatureRoot(int left, int right) {
        return directedInlineSignatureCaller(left, right) + left;
    }

    private static int directedInlineSignatureCaller(int left, int right) {
        return directedInlineSignatureTarget(left, right) + directedInlineSignatureRemainder(left, right);
    }

    private static int directedInlineSignatureTarget(int left, int right) {
        return left + right + 3;
    }

    private static int directedInlineSignatureRemainder(int left, int right) {
        return left - right + 5;
    }

    @Test
    public void testDirectedInliningProfileBackedInterfaceRootIgnoresRootGraphSizeLimit() {
        DirectedInterface receiver = new DirectedInterfaceLeaf();
        for (int i = 0; i < 10000; i++) {
            directedInterfaceRoot(receiver, i);
        }
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline, "directedInterfaceRoot->DirectedInterface.directedInterfaceCaller",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInterfaceRoot", options);
        ResolvedJavaMethod directedInterfaceCaller = getResolvedJavaMethod(DirectedInterface.class, "directedInterfaceCaller");
        Assert.assertEquals("profile-backed directed root callee should be inlined even when the root graph is larger than the inlining limit", 0,
                        countInvokesTo(graph, directedInterfaceCaller));
    }

    @Test
    public void testDirectedInliningMatchesVirtualIntermediateCaller() {
        DirectedInterface receiver = new DirectedInterfaceLeaf();
        for (int i = 0; i < 10000; i++) {
            directedInterfaceRoot(receiver, i);
        }
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline,
                        "directedInterfaceRoot->DirectedInterface{DirectedInterfaceLeaf}.directedInterfaceCaller->directedInterfaceTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedInterfaceRoot", options);
        Assert.assertEquals("virtual intermediate component should match the declared target while exploring the concrete callee graph", 0,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedInterface.class, "directedInterfaceCaller")));
        Assert.assertEquals("full-chain rule should force the target inside the selected concrete implementation", 0,
                        countInvokesTo(graph, getResolvedJavaMethod("directedInterfaceTarget")));
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

    @Test
    public void testDirectedInliningForcesMatchingPolymorphicCallee() {
        new DirectedPolymorphicA().directedPolymorphicTarget();
        new DirectedPolymorphicB().directedPolymorphicTarget();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(getResolvedJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(getResolvedJavaType(DirectedPolymorphicB.class), 0.5D)};
        String directedTarget = "directedPolymorphicRoot->DirectedPolymorphicBase{DirectedPolymorphicA}.directedPolymorphicTarget";
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline, directedTarget,
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraphWithTypeProfile("directedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("directed inline should force only the matching receiver type and leave the other receiver type on the fallback invoke",
                        1,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget")));
    }

    @Test
    public void testDirectedInliningUsesDeclaredPolymorphicCallee() {
        new DirectedPolymorphicA().directedPolymorphicTarget();
        new DirectedPolymorphicB().directedPolymorphicTarget();
        ProfiledType[] injectedProfile = {
                        new ProfiledType(getResolvedJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(getResolvedJavaType(DirectedPolymorphicB.class), 0.5D)};
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline, "directedPolymorphicRoot->DirectedPolymorphicBase.directedPolymorphicTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
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
        ProfiledType[] injectedProfile = {
                        new ProfiledType(getResolvedJavaType(DirectedPolymorphicA.class), 0.5D),
                        new ProfiledType(getResolvedJavaType(DirectedPolymorphicB.class), 0.5D)};
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline, "directedPolymorphicRoot->DirectedPolymorphicA.directedPolymorphicTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraphWithTypeProfile("directedPolymorphicRoot", options,
                        getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget"),
                        new JavaTypeProfile(TriState.FALSE, 0.0D, injectedProfile));
        Assert.assertEquals("concrete callee rule should not select from a virtual callsite declared as the base method", 1,
                        countInvokesTo(graph, getResolvedJavaMethod(DirectedPolymorphicBase.class, "directedPolymorphicTarget")));
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

    @Test
    public void testDirectedInliningRespectsRootInvokeAllowList() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedInline, "rootInvokeAllowedSnippet->disallowedDirectedRootTarget",
                        BytecodeParserOptions.InlineDuringParsing, false);
        DebugContext debug = getDebugContext(options, null, null);
        try (DebugContext.Scope _ = debug.scope("PriorityInliningAllowListTest", new DebugDumpScope("rootInvokeAllowedSnippet", true))) {
            ResolvedJavaMethod rootMethod = getResolvedJavaMethod("rootInvokeAllowedSnippet");
            ResolvedJavaMethod allowListedTarget = getResolvedJavaMethod("allowListedRootTarget");
            StructuredGraph graph = parseEager(rootMethod, StructuredGraph.AllowAssumptions.YES, debug);
            try (DebugContext.Scope _ = debug.scope("PriorityInlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                createCanonicalizerPhase().apply(graph, context);
                new PriorityInliningPhase(createCanonicalizerPhase(), graph.getOptions(), new DefaultInliningProvider() {
                    @Override
                    public List<Invoke> rootInvokeAllowed(StructuredGraph rootGraph) {
                        ArrayList<Invoke> allowed = new ArrayList<>();
                        for (Invoke invoke : rootGraph.getInvokes()) {
                            if (allowListedTarget.equals(invoke.getTargetMethod())) {
                                allowed.add(invoke);
                            }
                        }
                        return allowed;
                    }
                }).apply(graph, context);
            }

            ResolvedJavaMethod disallowedTarget = getResolvedJavaMethod("disallowedDirectedRootTarget");
            Assert.assertEquals("directed root invoke excluded by rootInvokeAllowed should remain as an invoke", 1, countInvokesTo(graph, disallowedTarget));
        } catch (Throwable e) {
            throw debug.handle(e);
        }
    }

    public int rootInvokeAllowedSnippet(int value) {
        return allowListedRootTarget(value) + disallowedDirectedRootTarget(value);
    }

    private static int allowListedRootTarget(int value) {
        return value + 37;
    }

    private static int disallowedDirectedRootTarget(int value) {
        return value + 41;
    }

    @Test
    public void testDirectedNeverInliningPreservesMatchingTarget() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        AbstractPriorityInliningPhase.Options.PriorityForceInline, "directedNeverInlineCaller,directedNeverInlineTarget",
                        InliningPhase.Options.DirectedDontInline, "directedNeverInlineRoot->directedNeverInlineCaller->directedNeverInlineTarget",
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedNeverInlineRoot", options);
        ResolvedJavaMethod directedNeverInlineCaller = getResolvedJavaMethod("directedNeverInlineCaller");
        ResolvedJavaMethod directedNeverInlineTarget = getResolvedJavaMethod("directedNeverInlineTarget");
        Assert.assertEquals("directed dont-inline caller should still be inlined to expose the requested edge", 0, countInvokesTo(graph, directedNeverInlineCaller));
        Assert.assertEquals("directed dont-inline target should remain as an invoke", 1, countInvokesTo(graph, directedNeverInlineTarget));
    }

    @Test
    public void testDirectedInliningRulesFile() throws IOException {
        Path rulesFile = Files.createTempFile("directed-inlining-rules", ".txt");
        try {
            Files.writeString(rulesFile, """
                            # mixed directed inlining commands
                            inline,directedInlineLargeRootSnippet->directedInlineCaller
                            dontinline,directedNeverInlineRoot->directedNeverInlineCaller->directedNeverInlineTarget
                            """);

            OptionValues inlineOptions = new OptionValues(getInitialOptions(),
                            InliningPhase.Options.DirectedInliningRulesFile, rulesFile.toString(),
                            PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                            BytecodeParserOptions.InlineDuringParsing, false);
            StructuredGraph inlineGraph = getGraph("directedInlineLargeRootSnippet", inlineOptions);
            ResolvedJavaMethod directedInlineCaller = getResolvedJavaMethod("directedInlineCaller");
            Assert.assertEquals("inline command from file should force the matching invoke", 0, countInvokesTo(inlineGraph, directedInlineCaller));

            OptionValues neverInlineOptions = new OptionValues(getInitialOptions(),
                            AbstractPriorityInliningPhase.Options.PriorityForceInline, "directedNeverInlineCaller,directedNeverInlineTarget",
                            InliningPhase.Options.DirectedInliningRulesFile, rulesFile.toString(),
                            BytecodeParserOptions.InlineDuringParsing, false);
            StructuredGraph neverInlineGraph = getGraph("directedNeverInlineRoot", neverInlineOptions);
            ResolvedJavaMethod directedNeverInlineTarget = getResolvedJavaMethod("directedNeverInlineTarget");
            Assert.assertEquals("dontinline command from file should keep the matching target as an invoke", 1, countInvokesTo(neverInlineGraph, directedNeverInlineTarget));
        } finally {
            Files.deleteIfExists(rulesFile);
        }
    }

    @Test
    public void testDirectedNeverInliningDoesNotForceInlinedCaller() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        InliningPhase.Options.DirectedDontInline, "directedNeverInlineRoot->directedNeverInlineCaller->directedNeverInlineTarget",
                        PriorityInliningPhase.Options.InlinedCompilerNodeLimit, 1,
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedNeverInlineRoot", options);
        ResolvedJavaMethod directedNeverInlineCaller = getResolvedJavaMethod("directedNeverInlineCaller");
        Assert.assertEquals("nested dont-inline rule should not force the root-to-inlined-caller edge", 1, countInvokesTo(graph, directedNeverInlineCaller));
    }

    @Test
    public void testDirectedNeverInliningRequiresMatchingBci() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        AbstractPriorityInliningPhase.Options.PriorityForceInline, "directedNeverInlineCaller",
                        InliningPhase.Options.DirectedDontInline, "directedNeverInlineRoot->directedNeverInlineCaller@-123456->directedNeverInlineTarget",
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph = getGraph("directedNeverInlineRoot", options);
        ResolvedJavaMethod directedNeverInlineCaller = getResolvedJavaMethod("directedNeverInlineCaller");
        ResolvedJavaMethod directedNeverInlineTarget = getResolvedJavaMethod("directedNeverInlineTarget");
        Assert.assertEquals("directed dont-inline caller should still be inlined", 0, countInvokesTo(graph, directedNeverInlineCaller));
        Assert.assertEquals("directed dont-inline target should be inlined when the rule bci does not match", 0, countInvokesTo(graph, directedNeverInlineTarget));
    }

    @Test
    public void testDirectedNeverInliningTracing() {
        OptionValues options = new OptionValues(getInitialOptions(),
                        GraalOptions.TraceInlining, true,
                        AbstractPriorityInliningPhase.Options.PriorityForceInline, "directedNeverInlineCaller",
                        InliningPhase.Options.DirectedDontInline, "directedNeverInlineRoot->directedNeverInlineCaller->directedNeverInlineTarget",
                        BytecodeParserOptions.InlineDuringParsing, false);
        StructuredGraph graph;
        try (Filter _ = new Filter()) {
            graph = getGraph("directedNeverInlineRoot", options);
        }
        String inliningTree = graph.getInliningLog().formatAsTree(false);
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("directedNeverInlineTarget"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("the callsite is excluded by a directed dont-inline rule."));
    }

    public int directedNeverInlineRoot(int value) {
        return directedNeverInlineCaller(value) + value;
    }

    private static int directedNeverInlineCaller(int value) {
        return directedNeverInlineTarget(value) + directedNeverInlineRemainder(value);
    }

    private static int directedNeverInlineTarget(int value) {
        return value + 7;
    }

    private static int directedNeverInlineRemainder(int value) {
        return value + 11;
    }

    private StructuredGraph getGraphWithTypeProfile(final String snippet, OptionValues options,
                    ResolvedJavaMethod profiledTargetMethod, JavaTypeProfile profile) {
        DebugContext debug = getDebugContext(options, null, null);
        try (DebugContext.Scope _ = debug.scope("PriorityInliningTest", new DebugDumpScope(snippet, true))) {
            ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
            StructuredGraph graph = parseForCompile(method, options);
            injectTypeProfile(graph, profiledTargetMethod, profile);
            try (DebugContext.Scope _ = debug.scope("PriorityInlining", graph)) {
                PhaseSuite<HighTierContext> graphBuilderSuite = getDefaultGraphBuilderSuite();
                HighTierContext context = new HighTierContext(getProviders(), graphBuilderSuite, OptimisticOptimizations.ALL);
                createCanonicalizerPhase().apply(graph, context);
                new PriorityInliningPhase(createCanonicalizerPhase(), graph.getOptions()).apply(graph, context);
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

    @Test
    public void testTracing() {
        OptionValues options = new OptionValues(getInitialOptions(), GraalOptions.TraceInlining, true);
        StructuredGraph graph;
        try (Filter _ = new Filter()) {
            graph = getGraph("traceInliningTest", options);
        }
        String inliningTree = graph.getInliningLog().formatAsTree(false);
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("PriorityInliningDefaultTest.traceInliningTest"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("<PriorityInliningPhase>"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("yes, worth inlining according to the cost-benefit analysis."));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("PriorityInliningDefaultTest$Dog.shout"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("java.lang.Error.<init>"));
        Assert.assertTrue("Got: " + inliningTree, inliningTree.contains("no, inlining this method is not allowed."));
    }

    abstract class Animal {
        public abstract String shout(String word);
    }

    class Dog extends Animal {
        @Override
        public String shout(String word) {
            return bark(word);
        }

        private String bark(String word) {
            if (word.length() > 3) {
                throw new RuntimeException("Confused.");
            } else if (word.length() == 0) {
                throw new Error("Asleep.");
            } else {
                return word;
            }
        }

        // javac in JDK21 will only set this$0 in the constructor if there is a usage.
        // Use this dummy method to preserve the original inlining behavior.
        public void dummy() {
            PriorityInliningDefaultTest.this.dummy();
        }
    }

    public void traceInliningTest(String word) {
        Animal animal = new Dog();
        animal.shout(word);
    }

    @Override
    protected Suites createSuites(OptionValues options) {
        Suites suites = super.createSuites(options);
        return suites;
    }

    private void dummy() {
    }

}
