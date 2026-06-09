/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.inlining;

import java.util.LinkedList;

import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.common.AbstractInliningPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.inlining.policy.InliningPolicy;
import jdk.graal.compiler.phases.common.inlining.walker.InliningData;
import jdk.graal.compiler.phases.common.inlining.walker.InliningData.DevirtualizationInfo;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class InliningPhase extends AbstractInliningPhase {

    public static class Options {

        private static final String DIRECTED_INLINING_RULE_HELP = """
                        Rules use forms such as A.a[@<bci>]->C.c,
                        A.a[@<bci>]->InterfaceB{B_1}.b,
                        A.a[@<bci>]->InterfaceB{B_1}.b[@<bci>]->C.c, and
                        A.a[@<bci>]->B.b[@<bci>]->C.c.
                        Root and inlined-caller components are single positive MethodFilter patterns.
                        Non-root target components are single positive MethodFilter patterns, optionally with a receiver-type filter
                        after the declared holder to select one receiver type from a virtual/interface target.
                        A receiver-type filter must select exactly one receiver type; use separate rules for separate receiver paths.
                        In a chain, each bci identifies the selected invoke inside the method immediately to its left.
                        Directed inline forces each prefix edge in the chain; directed dont-inline matches only the terminal edge.
                        Separate multiple rules with ','.""";

        @Option(help = "Unconditionally inline intrinsics", type = OptionType.Debug)//
        public static final OptionKey<Boolean> AlwaysInlineIntrinsics = new OptionKey<>(false);

        /**
         * This is a defensive measure against known pathologies of the inliner where the breadth of
         * the inlining call tree exploration can be wide enough to prevent inlining from completing
         * in reasonable time.
         */
        @Option(help = "Per-compilation method inlining exploration limit before giving up (use 0 to disable)", type = OptionType.Debug)//
        public static final OptionKey<Integer> MethodInlineBailoutLimit = new OptionKey<>(1000);

        @Option(help = "Unconditionally inline directed call sites.\n\n" + DIRECTED_INLINING_RULE_HELP, type = OptionType.Debug) public static final OptionKey<String> DirectedInline = new OptionKey<>(
                        null);

        @Option(help = "Do not inline directed call sites.\n\n" + DIRECTED_INLINING_RULE_HELP, type = OptionType.Debug) public static final OptionKey<String> DirectedDontInline = new OptionKey<>(
                        null);

        @Option(help = "File containing directed inlining commands. Each non-empty, non-comment line uses " +
                        "'inline <rule>' or 'dontinline <rule>'; a comma may be used instead of whitespace after the command. " +
                        "Command-line DirectedInline and DirectedDontInline rules are unioned with file rules.\n\n" +
                        DIRECTED_INLINING_RULE_HELP, type = OptionType.Debug) public static final OptionKey<String> DirectedInliningRulesFile = new OptionKey<>(null);
    }

    private final InliningPolicy inliningPolicy;
    private final CanonicalizerPhase canonicalizer;
    private final LinkedList<Invoke> rootInvokes;
    private final DirectedInliningRules.RuleSet directedRules;

    private final int maxMethodPerInlining = Integer.MAX_VALUE;

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer) {
        this(policy, canonicalizer, null, null);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer, OptionValues options) {
        this(policy, canonicalizer, null, options);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer, LinkedList<Invoke> rootInvokes) {
        this(policy, canonicalizer, rootInvokes, null);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer, LinkedList<Invoke> rootInvokes, OptionValues options) {
        this.inliningPolicy = policy;
        this.canonicalizer = canonicalizer;
        this.rootInvokes = rootInvokes;
        this.directedRules = options == null ? DirectedInliningRules.RuleSet.EMPTY : parseDirectedRules(options);
    }

    public CanonicalizerPhase getCanonicalizer() {
        return canonicalizer;
    }

    @Override
    public float codeSizeIncrease() {
        return 10_000f;
    }

    @Override
    protected boolean isForceInlinedTarget(ResolvedJavaMethod targetMethod, Invoke invoke) {
        if (!isAllowedRootInvoke(invoke)) {
            return false;
        }
        DirectedInliningRules.RuleSet activeDirectedRules = this.directedRules;
        if (activeDirectedRules.hasRules()) {
            if (activeDirectedRules.matchesDontInline(invoke, targetMethod)) {
                return false;
            }
            if (activeDirectedRules.matchesInlineOrPrefix(invoke, targetMethod, null)) {
                return true;
            }
        }
        if (!super.isForceInlinedTarget(targetMethod, invoke)) {
            return false;
        }
        return true;
    }

    private boolean isAllowedRootInvoke(Invoke invoke) {
        /*
         * Restricting the inliner to an explicit subset of root invokes only excludes the
         * non-selected root invokes from this pass. Descendants under the allowed roots are still
         * explored normally and must continue to satisfy force-inline verification.
         */
        return rootInvokes == null || !isRootGraphInvoke(invoke) || rootInvokes.contains(invoke);
    }

    /**
     *
     * This method sets in motion the inlining machinery.
     *
     * @see InliningData
     * @see InliningData#moveForward()
     *
     */
    @Override
    protected void runInlining(final StructuredGraph graph, final HighTierContext context) {
        OptionValues options = graph.getOptions();
        DirectedInliningRules.RuleSet activeDirectedRules = this.directedRules;
        if (!activeDirectedRules.hasRules()) {
            boolean completed = runInliningToCompletion(graph, context, rootInvokes, null, null,
                            Options.MethodInlineBailoutLimit.getValue(options));
            if (!completed) {
                /* We still want to inline force-inlined invokes even if the limit was reached. */
                LinkedList<Invoke> forceInlinedInvokes = collectForceInlinedInvokes(graph, context);
                if (!forceInlinedInvokes.isEmpty()) {
                    runInliningToCompletion(graph, context, forceInlinedInvokes, null, null, 0);
                }
            }
            return;
        }
        DirectedInliningRules directedInliningRules = activeDirectedRules.inlineRules();
        DirectedInliningRules directedDontInliningRules = activeDirectedRules.dontInlineRules();
        runInliningToCompletion(graph, context, rootInvokes, directedInliningRules, directedDontInliningRules,
                        Options.MethodInlineBailoutLimit.getValue(options));
        /*
         * We still want to inline force-inlined invokes and directed-inline roots even if the
         * exploration limit was reached or normal exploration stopped before visiting them.
         */
        LinkedList<Invoke> requiredInvokes = collectRequiredRootInvokes(graph, context, activeDirectedRules);
        if (!requiredInvokes.isEmpty()) {
            runInliningToCompletion(graph, context, requiredInvokes, directedInliningRules, directedDontInliningRules, 0);
        }
    }

    private static DirectedInliningRules.RuleSet parseDirectedRules(OptionValues options) {
        String inlineRules = Options.DirectedInline.getValue(options);
        String dontInlineRules = Options.DirectedDontInline.getValue(options);
        String commandFile = Options.DirectedInliningRulesFile.getValue(options);
        if (isEmpty(inlineRules) && isEmpty(dontInlineRules) && isEmpty(commandFile)) {
            return DirectedInliningRules.RuleSet.EMPTY;
        }
        return DirectedInliningRules.parse(inlineRules, dontInlineRules, commandFile);
    }

    private static boolean isEmpty(String optionValue) {
        return optionValue == null || optionValue.trim().isEmpty();
    }

    /**
     * Runs inlining until all candidate graphs are processed or until the exploration bailout limit
     * is reached. A limit of 0 disables this bailout.
     *
     * @param invokes optional subset of root-graph invokes to process; {@code null} means all
     *            invokes
     * @param limit maximum number of inlining exploration steps before bailing out
     * @return {@code true} if inlining completed normally, {@code false} if the bailout limit was
     *         reached
     */
    private boolean runInliningToCompletion(final StructuredGraph graph, final HighTierContext context, LinkedList<Invoke> invokes,
                    DirectedInliningRules directedInliningRules, DirectedInliningRules directedDontInliningRules, int limit) {
        final InliningData data = new InliningData(graph, context, maxMethodPerInlining, canonicalizer, inliningPolicy, invokes, directedInliningRules, directedDontInliningRules);

        int count = 0;
        assert data.repOK();
        while (data.hasUnprocessedGraphs()) {
            boolean wasInlined = data.moveForward();
            assert data.repOK();
            count++;
            if (!wasInlined) {
                if (limit > 0 && count == limit) {
                    // Limit the amount of exploration which is done
                    return false;
                }
            }
        }

        assert data.inliningDepth() == 0 : data.inliningDepth() + " " + count + " " + limit;
        assert data.graphCount() == 0 : data.graphCount() + " " + count + " " + limit;
        return true;
    }

    private LinkedList<Invoke> collectForceInlinedInvokes(StructuredGraph graph, HighTierContext context) {
        LinkedList<Invoke> forceInlinedInvokes = new LinkedList<>();
        for (Invoke invoke : graph.getInvokes()) {
            if (mustBeInlined(invoke, context)) {
                forceInlinedInvokes.add(invoke);
            }
        }
        return forceInlinedInvokes;
    }

    /**
     * Collects the root invokes used to seed the unlimited recovery pass after the limited pass gives
     * up. Restricting recovery to these invokes lets directed-inline and forced-inline targets remain
     * reachable without reopening unrelated root invokes that were already stopped by the limit.
     */
    private LinkedList<Invoke> collectRequiredRootInvokes(StructuredGraph graph, HighTierContext context, DirectedInliningRules.RuleSet requiredDirectedRules) {
        LinkedList<Invoke> requiredInvokes = new LinkedList<>();
        for (Invoke invoke : graph.getInvokes()) {
            if (isAllowedRootInvoke(invoke) &&
                            (mustBeInlined(invoke, context) || matchesRootInvokeForDirectedInline(requiredDirectedRules, invoke))) {
                requiredInvokes.add(invoke);
            }
        }
        return requiredInvokes;
    }

    /**
     * Returns {@code true} when this root-graph invoke directly matches a directed inline rule or is
     * the first required prefix of a longer directed inline chain and should be revisited by the
     * unlimited recovery pass after
     * {@link Options#MethodInlineBailoutLimit} was reached.
     */
    private static boolean matchesRootInvokeForDirectedInline(DirectedInliningRules.RuleSet directedRules, Invoke invoke) {
        if (!isRootGraphInvoke(invoke)) {
            return false;
        }
        DevirtualizationInfo targetInfo = InliningData.resolveDirectOrDevirtualizedTargetInfo(invoke, true);
        return (targetInfo != null && directedRules.matchesInlineOrPrefix(invoke, targetInfo.targetMethod(), targetInfo.dispatchedType())) ||
                        matchesProfiledTargetForDirectedInline(directedRules, invoke);
    }

    /**
     * Checks profile-backed receiver types for direct abstract or interface root invokes that cannot
     * be resolved to a single target before the recovery pass starts.
     */
    private static boolean matchesProfiledTargetForDirectedInline(DirectedInliningRules.RuleSet directedRules, Invoke invoke) {
        if (!(invoke.callTarget() instanceof MethodCallTargetNode callTarget) || !callTarget.invokeKind().isIndirect()) {
            return false;
        }
        ResolvedJavaMethod targetMethod = callTarget.targetMethod();
        JavaTypeProfile typeProfile = callTarget.getTypeProfile();
        if (targetMethod == null || typeProfile == null || typeProfile.getTypes() == null) {
            return false;
        }
        if (directedRules.matchesInlineOrPrefix(invoke, targetMethod, null)) {
            return true;
        }
        ResolvedJavaType contextType = invoke.getContextType();
        for (JavaTypeProfile.ProfiledType profiledType : typeProfile.getTypes()) {
            ResolvedJavaMethod concrete = profiledType.getType().resolveConcreteMethod(targetMethod, contextType);
            if (directedRules.matchesInlineOrPrefix(invoke, concrete, profiledType.getType())) {
                return true;
            }
        }
        return false;
    }

}
