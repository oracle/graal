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
package jdk.graal.compiler.phases.common.priorityinline;

import static jdk.graal.compiler.bytecode.Bytecodes.INSTANCEOF;
import static jdk.graal.compiler.phases.common.priorityinline.AbstractPriorityInliningPhase.Options.PriorityForceInline;
import static jdk.graal.compiler.phases.common.priorityinline.AbstractPriorityInliningPhase.Options.PriorityNeverInline;
import static jdk.graal.compiler.phases.common.priorityinline.PriorityInliningPhase.Options.UseGraphCache;

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.MethodFilter;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.BytecodeParserOptions;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.Replacements;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.BoxNodeIdentityPhase;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase;
import jdk.graal.compiler.phases.common.inlining.DirectedInliningRules;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.common.inlining.walker.InliningData;
import jdk.graal.compiler.phases.common.inlining.walker.InliningData.DevirtualizationInfo;
import jdk.graal.compiler.phases.common.priorityinline.data.BenefitKind;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CallTreeNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.CutoffNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.DeletedNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.DontInlineCause;
import jdk.graal.compiler.phases.common.priorityinline.nodes.GenericNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.IndirectNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.InlineCacheNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.ParentNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;
import jdk.graal.compiler.phases.common.priorityinline.tuning.TuningPolicy;
import jdk.graal.compiler.phases.tiers.HighTierContext;
import jdk.graal.compiler.replacements.ConstantBindingParameterPlugin;
import jdk.graal.compiler.replacements.ReplacementsImpl;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.AbstractJavaProfile;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaMethodProfile;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class CallTree extends Graph {
    private final CallTreeState state;
    private final Expander.Policy policy;
    private final TuningPolicy tuningPolicy;
    private final CanonicalizerPhase canonicalizer;
    private final HighTierContext context;
    private final InliningProvider inliningProvider;
    private final GraphCache<ResolvedJavaMethod, StructuredGraph> graphCache;
    /**
     * Separate cache for graphs parsed or decoded with explicit OOME allocation edges. The same
     * method can be materialized in both normal and OOME-protected inline contexts; sharing a cache
     * would either drop required OOME edges or reuse exception edges where the caller cannot catch
     * them.
     */
    private final GraphCache<ResolvedJavaMethod, StructuredGraph> oomeGraphCache;
    private final EconomicMap<ResolvedJavaMethod, Integer> methodHistogram;
    private final SubgraphNode root;
    private final int initialRootInvokes;
    private long currentSpending;
    private double totalBenefit;
    private int expansionsLeft;
    private int totalNodesParsed;
    protected int totalNodes;

    /**
     * Matched methods should always be inlined by the priority inliner. No methods match the filter
     * if the field is {@code null}.
     */
    private final MethodFilter forceInlineFilter;
    private final DirectedInliningRules directedInliningRules;
    private final DirectedInliningRules directedDontInliningRules;

    /**
     * Matched methods must never be inlined by the priority inliner. No methods match the filter if
     * the field is {@code null}.
     */
    private final MethodFilter neverInlineFilter;

    @SuppressWarnings("this-escape")
    public CallTree(CanonicalizerPhase canonicalizer, Expander.Policy expansionPolicy, TuningPolicy tuningPolicy,
                    HighTierContext context, InliningProvider inliningProvider, GraphCache<ResolvedJavaMethod, StructuredGraph> graphCache,
                    SubgraphNode root, OptionValues options, DirectedInliningRules.RuleSet directedRules) {
        super(null, root.getReadonlySubgraph().getOptions(), root.getReadonlySubgraph().getDebug(), false);
        this.state = expansionPolicy.createCallTreeState();
        this.policy = expansionPolicy;
        this.tuningPolicy = tuningPolicy;
        this.canonicalizer = canonicalizer;
        this.context = context;
        this.inliningProvider = inliningProvider;
        this.graphCache = graphCache;
        this.oomeGraphCache = new GraphCache<>();
        this.methodHistogram = EconomicMap.create();
        this.root = add(root);
        this.initialRootInvokes = root.getReadonlySubgraph().getNodes(MethodCallTargetNode.TYPE).count();
        this.currentSpending = 0;
        this.expansionsLeft = 0;
        this.totalNodesParsed = 0;
        this.totalNodes = 0;
        if (PriorityForceInline.getValue(options) == null) {
            this.forceInlineFilter = null;
        } else {
            this.forceInlineFilter = MethodFilter.parse(PriorityForceInline.getValue(options));
        }
        this.directedInliningRules = directedRules.inlineRules();
        this.directedDontInliningRules = directedRules.dontInlineRules();
        if (PriorityNeverInline.getValue(options) == null) {
            this.neverInlineFilter = null;
        } else {
            this.neverInlineFilter = MethodFilter.parse(PriorityNeverInline.getValue(options));
        }
    }

    public CallTreeState state() {
        return state;
    }

    public SubgraphNode root() {
        return root;
    }

    public void filteredPostOrder(Predicate<CallTreeNode> p, Consumer<CallTreeNode> f) {
        if (p.test(root)) {
            traverseFilteredPostOrder(root, p, f);
        }
    }

    private void traverseFilteredPostOrder(CallTreeNode node, Predicate<CallTreeNode> p, Consumer<CallTreeNode> f) {
        for (CallTreeNode child : node.children()) {
            if (p.test(child)) {
                traverseFilteredPostOrder(child, p, f);
            }
        }
        f.accept(node);
    }

    public void priorityOrder(Comparator<CallTreeNode> comparator, Function<CallTreeNode, Collection<CallTreeNode>> f) {
        PriorityQueue<CallTreeNode> queue = new PriorityQueue<>(comparator);
        queue.add(root);
        while (!queue.isEmpty()) {
            queue.addAll(f.apply(queue.poll()));
        }
    }

    public void initialize() {
        // Parse graph and expand call graph.
        List<Invoke> rootInvokeAllowed = inliningProvider.rootInvokeAllowed(root.getReadonlySubgraph());
        root.createImmediateChildren(null);
        if (rootInvokeAllowed != null) {
            EconomicSet<Invoke> allowed = EconomicSet.create();
            allowed.addAll(rootInvokeAllowed);
            for (CallTreeNode child : root.children()) {
                if (!allowed.contains(child.invoke())) {
                    child.replaceAtPredecessor(add(new GenericNode(child.invoke().asNode().getNodeSourcePosition(), child.invoke(), child.getFrequency(), DontInlineCause.NotUsedForInlining)));
                    child.safeRecursiveDelete();
                }
            }
        }
        restoreSubtreeInvariants(root, true);
    }

    public CallTreeNode createChild(CallTreeNode caller, Invoke invoke, double frequency) {
        return createChild(caller, invoke, null, null, null, frequency);
    }

    public CallTreeNode createChild(CallTreeNode caller, Invoke invoke, ResolvedJavaMethod directTarget, ResolvedJavaType dispatchedType, ResolvedJavaType originalDispatchedType, double frequency) {
        propagateOOMEContext(caller, invoke);
        if (directTarget == null) {
            CallTargetNode callTarget = invoke.callTarget();
            if (callTarget instanceof MethodCallTargetNode methodCallTarget) {
                if (methodCallTarget.invokeKind().isDirect()) {
                    return createDirectChild(caller, invoke, methodCallTarget.targetMethod(), dispatchedType, originalDispatchedType, true, frequency, false);
                }
                DevirtualizationInfo resolvedCallTarget = InliningData.resolveDirectOrDevirtualizedTargetInfo(invoke);
                if (resolvedCallTarget != null) {
                    ResolvedJavaType resolvedDispatchedType = resolvedCallTarget.dispatchedType();
                    return createDirectChild(caller, invoke, resolvedCallTarget.targetMethod(), resolvedDispatchedType, originalDispatchedType, true, frequency, true);
                } else {
                    return createInlineCacheOrIndirectChild(caller, invoke, methodCallTarget, frequency, null);
                }
            } else {
                return createGenericChild(caller, invoke, frequency, DontInlineCause.Indirect);
            }
        } else {
            return createDirectChild(caller, invoke, directTarget, dispatchedType, originalDispatchedType, false, frequency, false);
        }
    }

    private CallTreeNode createDirectChild(CallTreeNode caller, Invoke invoke, ResolvedJavaMethod targetMethod, ResolvedJavaType dispatchedType, ResolvedJavaType originalDispatchedType,
                    boolean monomorphic, double frequency, boolean devirtualization) {
        EnumSet<BenefitKind> benefits = estimateBenefits(invoke);
        if (devirtualization) {
            benefits.add(BenefitKind.Devirtualization);
        }
        assert !invoke.callTarget().arguments().contains(null) : "Null argument: " + invoke + ", " + invoke.callTarget().arguments();
        return add(createCutoffNode(caller, invoke, targetMethod, dispatchedType, originalDispatchedType, monomorphic, frequency, benefits));
    }

    protected CutoffNode createCutoffNode(CallTreeNode caller, Invoke invoke, ResolvedJavaMethod targetMethod, ResolvedJavaType dispatchedType, ResolvedJavaType originalDispatchedType,
                    boolean monomorphic, double frequency, EnumSet<BenefitKind> benefits) {
        return new CutoffNode(concatPositions(invoke, caller), invoke, frequency, targetMethod, dispatchedType, originalDispatchedType, monomorphic, benefits);
    }

    protected EnumSet<BenefitKind> estimateBenefits(Invoke invoke) {
        return BenefitKind.estimateBenefit(invoke);
    }

    public CallTreeNode createInlineCacheOrIndirectChild(CallTreeNode caller, Invoke invoke, MethodCallTargetNode target, double frequency, AbstractJavaProfile<?, ?> alternativeProfile) {
        final OptionValues options = invoke.asNode().getOptions();
        final int inlineCacheDispatchLimit = inliningProvider.getMaxPolymorphicDispatches(options);
        final AbstractJavaProfile<?, ?> inlineCacheProfile = inlineCacheProfile(caller, target, alternativeProfile);
        if (inlineCacheProfile != null && profileLength(inlineCacheProfile) > 0 && inlineCacheDispatchLimit > 0) {
            InlineCacheNode inlineCacheNode = add(new InlineCacheNode(concatPositions(invoke, caller), target.invoke(), target.invoke().getTargetMethod(), frequency, inlineCacheProfile,
                            inlineCacheDispatchLimit));
            inlineCacheNode.createImmediateChildren(caller);
            return inlineCacheNode;
        }
        // There is no type profile, so convert to a generic call.
        return createIndirectChild(caller, target.invoke(), frequency);
    }

    private AbstractJavaProfile<?, ?> inlineCacheProfile(CallTreeNode caller, MethodCallTargetNode target, AbstractJavaProfile<?, ?> alternativeProfile) {
        if (alternativeProfile != null) {
            return alternativeProfile;
        }

        return getPreferredProfile(caller, target);
    }

    protected static NodeSourcePosition concatPositions(Invoke invoke, CallTreeNode caller) {
        NodeSourcePosition invokePosition = invoke.asNode().getNodeSourcePosition();
        if (invokePosition == null) {
            return null;
        }
        // TODO BS GR-42092 This should never happen, this just means that the caller is incorrect
        // somewhere up the call chain.
        if (caller == null) {
            return null;
        }
        return invokePosition.addCaller(caller.compilationRootPosition());
    }

    /**
     * Select a profile based on implementation dependent priorities.
     */
    @SuppressWarnings("unused")
    protected AbstractJavaProfile<?, ?> getPreferredProfile(CallTreeNode caller, MethodCallTargetNode callTarget) {
        return callTarget.getTypeProfile();
    }

    public static int profileLength(AbstractJavaProfile<?, ?> profile) {
        if (profile instanceof JavaTypeProfile) {
            return ((JavaTypeProfile) profile).getTypes().length;
        } else if (profile instanceof JavaMethodProfile) {
            return ((JavaMethodProfile) profile).getMethods().length;
        } else {
            throw GraalError.shouldNotReachHere("Unexpected profile type."); // ExcludeFromJacocoGeneratedReport
        }
    }

    public GenericNode createGenericChild(CallTreeNode caller, Invoke invoke, double frequency, DontInlineCause dontInlineCause) {
        propagateOOMEContext(caller, invoke);
        return add(new GenericNode(concatPositions(invoke, caller), invoke, frequency, dontInlineCause));
    }

    public IndirectNode createIndirectChild(CallTreeNode caller, Invoke invoke, double frequency) {
        propagateOOMEContext(caller, invoke);
        return add(new IndirectNode(concatPositions(invoke, caller), invoke, frequency));
    }

    private void propagateOOMEContext(CallTreeNode caller, Invoke invoke) {
        if (inOOMEProtectedInlineContext(caller, invoke)) {
            invoke.setInOOMETry(true);
        }
    }

    public boolean isInOOMEProtectedInlineContext(CallTreeNode node) {
        return node.invoke() != null && node.invoke().isInOOMETry() && supportsOOMEExceptionEdges(node.targetMethod());
    }

    protected boolean supportsOOMEExceptionEdges(@SuppressWarnings("unused") ResolvedJavaMethod method) {
        return true;
    }

    protected boolean inOOMEProtectedInlineContext(CallTreeNode caller, Invoke invoke) {
        if (!BytecodeParserOptions.DoNotMoveAllocationsWithOOMEHandlers.getValue(invoke.asNode().getOptions())) {
            return false;
        }
        return (caller != null && caller.isInOOMEProtectedInlineContext()) || invoke.isInOOMETry() || invokeBciCatchesOOME(invoke);
    }

    /**
     * Recovers the OOME protected-region marker from bytecode metadata. The marker itself is not
     * reliable for every graph used by priority inlining. After earlier inlining, the invoke BCI
     * can belong to an inner source method while the active handler is in an outer source frame, so
     * walk the source-position chain before falling back to the graph method.
     */
    private boolean invokeBciCatchesOOME(Invoke invoke) {
        for (NodeSourcePosition position = invoke.asNode().getNodeSourcePosition(); position != null; position = position.getCaller()) {
            if (methodCatchesOOME(position.getMethod(), position.getBCI())) {
                return true;
            }
        }
        return methodCatchesOOME(invoke.asNode().graph().method(), invoke.bci());
    }

    private boolean methodCatchesOOME(ResolvedJavaMethod callerMethod, int bci) {
        if (bci < 0) {
            return false;
        }
        if (callerMethod == null) {
            return false;
        }
        if (!inliningProvider.supportsExceptionHandlerMetadata(callerMethod)) {
            return false;
        }
        for (ExceptionHandler handler : callerMethod.getExceptionHandlers()) {
            if (handler.getStartBCI() <= bci && bci < handler.getEndBCI() && catchTypeIncludesOOME(callerMethod, handler)) {
                return true;
            }
        }
        return false;
    }

    private static boolean catchTypeIncludesOOME(ResolvedJavaMethod callerMethod, ExceptionHandler handler) {
        JavaType catchType = resolveCatchType(callerMethod, handler);
        return BytecodeParser.isDirectOutOfMemoryErrorCatch(catchType);
    }

    /**
     * Returns the catch type for a source-level catch clause when JVMCI exposes one. A null catch
     * type with CPI 0 denotes a bytecode-level catch-all entry, which is not treated as an
     * OOME-compatible catch clause. Some JVMCI metadata providers can also report a null catch type
     * even though the exception-table entry still has a non-zero catch type CPI; in that case
     * resolve the type from the constant pool.
     */
    private static JavaType resolveCatchType(ResolvedJavaMethod callerMethod, ExceptionHandler handler) {
        JavaType catchType = handler.getCatchType();
        if (catchType == null && handler.catchTypeCPI() != 0) {
            catchType = callerMethod.getConstantPool().lookupType(handler.catchTypeCPI(), INSTANCEOF);
        }
        return catchType;
    }

    /**
     * Performs expansion regardless of expander policy which might, for example, dictate that an
     * indirect child should be created instead due to {@code node} having insufficient frequency.
     * It is still possible that the cutoff is not fully expanded due to non-policy related
     * constraints.
     */
    public final CallTreeNode expandCutoffNodeBypassPolicies(CutoffNode node) {
        ResolvedJavaMethod targetMethod = node.targetMethod();
        Invoke invoke = node.invoke();
        boolean inOOMEProtectedInlineContext = node.isInOOMEProtectedInlineContext();
        state().setHasExpandedSinceLastRound(true);

        // Check if the target method can be inlined without violating other constraints.
        DontInlineCause dontInlineCause = null;
        if (matchDirectedDontInline(node) != null) {
            dontInlineCause = DontInlineCause.DirectedDontInline;
        } else if (matchesNeverInlineFilter(targetMethod)) {
            dontInlineCause = DontInlineCause.NotUsedForInlining;
        } else if (!((targetMethod.getDeclaringClass().isInitialized() || inliningProvider.canInlineUninitialized()) && targetMethod.canBeInlined() && invoke.useForInlining())) {
            dontInlineCause = DontInlineCause.NotUsedForInlining;
        }
        if (dontInlineCause != null) {
            InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to generic!", node);
            return createGenericChild(node.parent(), invoke, node.getFrequency(), dontInlineCause);
        }

        // Try to resolve the intrinsified version of the specified method.
        GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> intrinsicGraph = createIntrinsicGraph(targetMethod, invoke.bci(), inOOMEProtectedInlineContext, invoke.getInlineControl(),
                        invoke.asNode().graph().trackNodeSourcePosition(), node.getCallerPosition());
        GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> subGraph = intrinsicGraph;

        if (subGraph == null) {
            if (targetMethod.isNative()) {
                // Cannot inline a native method without an intrinsic graph.
                InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to generic (native!)", node);
                return createGenericChild(node.parent(), invoke, node.getFrequency(), DontInlineCause.NotUsedForInlining);
            }
            subGraph = createGraph(targetMethod, invoke, root().getReadonlySubgraph().trackNodeSourcePosition(), node.getCallerPosition(), inOOMEProtectedInlineContext);
            if (subGraph == null) {
                // Target method does not have any associated bytecode, or cannot be inlined.
                InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to generic (null graph!)", node);
                return createGenericChild(node.parent(), invoke, node.getFrequency(), DontInlineCause.NotUsedForInlining);
            }
        }

        if (node.getRecursionDepth() > 0 && policy.isExpandedOften(node)) {
            InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to generic (expanded often)!", node);
            return createGenericChild(node.parent(), invoke, node.getFrequency(), DontInlineCause.NotUsedForInlining);
        }

        // Create subgraph node.
        assert subGraph != null;
        SubgraphNode subgraphNode = add(new SubgraphNode(node.compilationRootPosition(), invoke, node.getFrequency(), subGraph, node.isMonomorphic(), targetMethod, node.getDispatchedType(),
                        node.getOriginalDispatchedType(), node.getBenefits(), intrinsicGraph != null));
        subgraphNode.createImmediateChildren(node.parent());

        // Update expansion counts.
        for (CallTreeNode child : subgraphNode.children()) {
            incrementExpansionCount(child);
        }

        InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to subgraph!", node);
        return subgraphNode;
    }

    public CallTreeNode expandCutoffNode(CutoffNode node) {
        Invoke invoke = node.invoke();

        if (matchDirectedDontInline(node) != null) {
            InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to generic (directed dont-inline)!", node);
            return createGenericChild(node.parent(), invoke, node.getFrequency(), DontInlineCause.DirectedDontInline);
        }

        if (matchesNeverInlineFilter(node.targetMethod())) {
            InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to generic (priority never-inline)!", node);
            return createGenericChild(node.parent(), invoke, node.getFrequency(), DontInlineCause.NotUsedForInlining);
        }

        if (!node.isForceInlined() && policy.shouldBeIndirect(node)) {
            InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s to indirect!", node);
            return createIndirectChild(node.parent(), invoke, node.getFrequency());
        }

        // If this is a call to a method that the policy treats differently,
        // it must be expanded into a graph using custom rules.
        // One such example are Truffle's OptimizedCallTarget calls,
        // for which the policy invokes the partial evaluator to construct the graph.
        if (policy.isSpecialCallTarget(invoke)) {
            InliningUtil.logInliningDecision(node.getDebug(), "inlining expanded %s  to special!", node);
            return policy.expandSpecialTarget(this, node);
        }

        return expandCutoffNodeBypassPolicies(node);
    }

    public CallTreeNode replaceWithDirectIfApplicable(CallTreeNode node) {
        Invoke invoke = node.invoke();
        if (invoke != null && invoke.asNode().isAlive()) {
            ResolvedJavaMethod targetMethod = node.targetMethod();
            if (policy.isExpandedOften(node) && !(targetMethod != null && (targetMethod.shouldBeInlined() || matchesForceInlineFilter(targetMethod) || matchDirectedInline(node) != null))) {
                return node;
            }
            DevirtualizationInfo directTarget = InliningData.resolveDirectOrDevirtualizedTargetInfo(invoke);
            if (directTarget != null) {
                ResolvedJavaType resolvedDispatchedType = directTarget.dispatchedType();
                CallTreeNode direct = createDirectChild(node.parent(), invoke, directTarget.targetMethod(), resolvedDispatchedType, null, true, node.getFrequency(), true);
                copyDirectedInliningCallsites(node, direct);
                incrementExpansionCount(node);
                node.replaceAtPredecessor(direct);
                node.safeRecursiveDelete();
                return direct;
            }
        }

        return node;
    }

    public GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> createIntrinsicGraph(ResolvedJavaMethod targetMethod, int invokeBci, boolean inOOMETry, Invoke.InlineControl inlineControl,
                    boolean withNodeSourcePosition, NodeSourcePosition invokePosition) {
        StructuredGraph.AllowAssumptions allowAssumptions = StructuredGraph.AllowAssumptions.ifNonNull(root.getReadonlySubgraph().getAssumptions());
        StructuredGraph intrinsicGraph = context.getReplacements().getInlineSubstitution(targetMethod, invokeBci, inOOMETry, inlineControl, withNodeSourcePosition, invokePosition, allowAssumptions,
                        getOptions());
        if (intrinsicGraph != null) {
            // Note: intrinsic graphs are not cached, but we create a Ref for consistency.
            StructuredGraph intrinsicGraphCopy = (StructuredGraph) intrinsicGraph.copy(getDebug());
            mergeUnwinds(intrinsicGraphCopy);
            totalNodesParsed += intrinsicGraphCopy.getNodeCount();
            totalNodes += intrinsicGraphCopy.getNodeCount();
            if (UseGraphCache.getValue(getOptions())) {
                return graphCache.createRef(null, intrinsicGraphCopy);
            } else {
                return graphCache.createNonCounted(intrinsicGraphCopy);
            }
        }
        return null;
    }

    @SuppressWarnings("try")
    public GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> createGraph(ResolvedJavaMethod targetMethod, Invoke invoke, boolean withNodeSourcePosition, NodeSourcePosition callerContext) {
        return createGraph(targetMethod, invoke, withNodeSourcePosition, callerContext, inOOMEProtectedInlineContext(null, invoke));
    }

    @SuppressWarnings("try")
    public GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> createGraph(ResolvedJavaMethod targetMethod, Invoke invoke, boolean withNodeSourcePosition, NodeSourcePosition callerContext,
                    boolean inOOMEProtectedInlineContext) {
        assert createIntrinsicGraph(targetMethod, invoke.bci(), inOOMEProtectedInlineContext, invoke.getInlineControl(), false,
                        null) == null : "unexpected non-null intrinsic graph for targetMethod %s, invoke %s, callerContext %s".formatted(targetMethod, invoke, callerContext);
        if (targetMethod.getCode() != null && targetMethod.canBeInlined() && !matchesNeverInlineFilter(targetMethod)) {
            GraphCache<ResolvedJavaMethod, StructuredGraph> selectedGraphCache = getGraphCache(inOOMEProtectedInlineContext);
            GraphCache.Ref<ResolvedJavaMethod, StructuredGraph> ref = UseGraphCache.getValue(getOptions()) ? selectedGraphCache.getRef(targetMethod) : null;
            if (ref == null) {
                // The method has bytecodes => parse them.
                StructuredGraph.AllowAssumptions allowAssumptions = StructuredGraph.AllowAssumptions.ifNonNull(root.getReadonlySubgraph().getAssumptions());
                DebugContext debug = getDebug();
                GraphState readonlySubgraphState = root.getReadonlySubgraph().getGraphState();
                StructuredGraph newGraph = new StructuredGraph.Builder(getOptions(), debug, allowAssumptions).method(targetMethod).profileProvider(
                                root.getReadonlySubgraph().getProfileProvider()).compilationId(root.getReadonlySubgraph().compilationId()).trackNodeSourcePosition(
                                                withNodeSourcePosition).callerContext(callerContext).speculationLog(readonlySubgraphState.getSpeculationLog()).build();
                if (root.getReadonlySubgraph().isUnsafeAccessTrackingEnabled()) {
                    newGraph.disableUnsafeAccessTracking();
                }
                if (root.getReadonlySubgraph().getGraphState().isExplicitExceptionsNoDeopt()) {
                    newGraph.getGraphState().configureExplicitExceptionsNoDeopt();
                }
                boolean isCacheable;
                try (DebugContext.Scope s = debug.scope("InlineGraph", newGraph)) {
                    isCacheable = parseBytecodes(newGraph, invoke, inOOMEProtectedInlineContext);
                    totalNodesParsed += newGraph.getNodeCount();
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
                if (isCacheable && UseGraphCache.getValue(getOptions())) {
                    ref = selectedGraphCache.createRef(targetMethod, newGraph);
                } else {
                    ref = selectedGraphCache.createNonCounted(newGraph);
                }
            }
            totalNodes += ref.readonly().getNodeCount();
            return ref;
        } else {
            // There is no graph for this method.
            return null;
        }
    }

    @SuppressWarnings("try")
    public boolean canonicalizeUsages(ValueNode node) {
        StructuredGraph compilerGraph = node.graph();
        int nodeCount = compilerGraph.getNodeCount();
        Mark mark = compilerGraph.getMark();

        DebugContext debug = getDebug();
        try (DebugContext.Scope s = debug.scope("NewStampAndCanonicalize", compilerGraph)) {
            canonicalizer.applyIncremental(compilerGraph, context, node.usages());
        } catch (Throwable e) {
            throw debug.handle(e);
        }

        return compilerGraph.getNodeCount() < nodeCount || !compilerGraph.getNewNodes(mark).isEmpty();
    }

    /**
     * Parse the bytecodes of the specified graph. The parameters are replaced with the
     * corresponding callsite constants, when possible. If some parameter is replaced with a
     * constant, then the graph must not be cached.
     *
     * @return returns true if the graph is allowed to be cached, and false otherwise
     */
    private boolean parseBytecodes(StructuredGraph newGraph, Invoke invoke, boolean inOOMEProtectedInlineContext) {
        // If none of the parameters are constants, reuse the default suite.
        Object[] constants = new Object[invoke.callTarget().arguments().size()];
        boolean constantsSeen = false;
        int index = 0;
        for (ValueNode node : invoke.callTarget().arguments()) {
            if (node.isConstant()) {
                constantsSeen = true;
                constants[index] = node;
            }
            index++;
        }

        // Parse the graph.
        getGraphBuilderSuite(constantsSeen, constants, inOOMEProtectedInlineContext).apply(newGraph, context);
        assert newGraph.start().next() != null : "graph needs to be populated";
        // Ensure box nodes in the graph are processed before using the graph.
        new BoxNodeIdentityPhase().apply(newGraph, getContext());
        canonicalizer.apply(newGraph, context);
        if (GraalOptions.EarlyGVN.getValue(newGraph.getOptions())) {
            new DominatorBasedGlobalValueNumberingPhase(canonicalizer).apply(newGraph, context);
        }
        mergeUnwinds(newGraph);
        return !constantsSeen;
    }

    /**
     * Graph-copy inlining expects an inlinee graph to expose at most one {@link UnwindNode}. Fold
     * multiple throw exits before caching or inlining the graph.
     */
    public static void mergeUnwinds(StructuredGraph graph) {
        List<UnwindNode> unwinds = graph.getNodes(UnwindNode.TYPE).snapshot();
        if (unwinds.size() <= 1) {
            return;
        }

        MergeNode unwindMerge = graph.add(new MergeNode());
        FrameState stateSource = findMergedUnwindStateSource(unwinds);
        ValueNode exceptionValue = InliningUtil.mergeUnwindExceptions(unwindMerge, unwinds);
        FrameState stateAfter = createMergedUnwindStateAfter(graph, stateSource, exceptionValue);
        if (stateAfter != null) {
            unwindMerge.setStateAfter(stateAfter);
        }
        UnwindNode replacement = graph.add(new UnwindNode(exceptionValue));
        unwindMerge.setNext(replacement);
    }

    private static FrameState findMergedUnwindStateSource(List<UnwindNode> unwinds) {
        for (UnwindNode unwind : unwinds) {
            if (unwind.exception() instanceof StateSplit stateSplit && stateSplit.stateAfter() != null) {
                return stateSplit.stateAfter();
            }
        }
        return null;
    }

    private static FrameState createMergedUnwindStateAfter(StructuredGraph graph, FrameState stateSource, ValueNode exceptionValue) {
        if (stateSource != null) {
            return stateSource.duplicateModified(JavaKind.Object, JavaKind.Object, exceptionValue, null);
        }
        FrameState stateAfter = graph.add(new FrameState(null, null, BytecodeFrame.AFTER_EXCEPTION_BCI, ValueNode.EMPTY_ARRAY, new ValueNode[]{exceptionValue}, 1, null, null,
                        ValueNode.EMPTY_ARRAY, null, FrameState.StackState.Rethrow));
        stateAfter.invalidateForDeoptimization();
        return stateAfter;
    }

    private PhaseSuite<HighTierContext> getGraphBuilderSuite(boolean constantSeen, Object[] constants, boolean inOOMEProtectedInlineContext) {
        PhaseSuite<HighTierContext> suite = context.getGraphBuilderSuiteForCallee(inOOMEProtectedInlineContext);

        if (constantSeen) {
            Replacements replacements = context.getReplacements();
            if (replacements instanceof ReplacementsImpl) {
                PhaseSuite<HighTierContext> copied = suite.copy();
                GraphBuilderPhase originalBuilder = (GraphBuilderPhase) (copied.findPhase(GraphBuilderPhase.class).previous());
                GraphBuilderConfiguration newConfig = originalBuilder.getGraphBuilderConfig().copy();
                newConfig.getPlugins().appendParameterPlugin(new ConstantBindingParameterPlugin(constants, context.getMetaAccess(), context.getSnippetReflection()));
                copied.findPhase(GraphBuilderPhase.class).set(originalBuilder.copyWithConfig(newConfig));
                return copied;
            }
        }

        return suite;
    }

    public void restoreSubtreeInvariants(CallTreeNode node, boolean recursively) {
        node.markNeedsCostBenefitUpdate();
        node.initializeCounts();

        if (node instanceof ParentNode parentNode) {
            parentNode.clearExpansionQueue();

            for (CallTreeNode child : parentNode.children()) {
                if (recursively) {
                    restoreSubtreeInvariants(child, true);
                }
                parentNode.includeChildInCounts(child);
                if (child.hasActiveCutoffs()) {
                    parentNode.addToExpansionQueue(child);
                }
            }

            assert parentNode.hasActiveCutoffs() ^ parentNode.isExpansionQueueEmpty() : "Active cutoffs and expansion queue do not match: " + parentNode.activeCutoffCount() +
                            ", recursive: " + recursively;

            policy.updateParentNodeLocalBenefit(parentNode);
            if (parentNode.isExpansionQueueEmpty()) {
                parentNode.setLowestPriority();
            } else {
                policy.updateParentNodePriority(parentNode);
            }
        } else if (node instanceof CutoffNode cutoffNode) {
            policy.updateCutoffNodeLocalBenefit(cutoffNode);
            policy.updateCutoffNodePriority(cutoffNode);
        } else if (node instanceof GenericNode || node instanceof IndirectNode) {
            node.setLowestPriority();
            node.setLocalBenefit(0.0);
            node.setLowestPriority();
        } else if (node instanceof DeletedNode) {
            node.setLowestPriority();
        } else {
            GraalError.shouldNotReachHere("Unknown node type: " + node); // ExcludeFromJacocoGeneratedReport
        }

        node.updateSubtreeStatistics();
    }

    public CanonicalizerPhase getCanonicalizer() {
        return canonicalizer;
    }

    public int getTotalNodesParsed() {
        return totalNodesParsed;
    }

    public long getCurrentSpending() {
        return currentSpending;
    }

    public void addSpending(long extraSpending) {
        this.currentSpending += extraSpending;
    }

    public double getTotalBenefit() {
        return totalBenefit;
    }

    public void addBenefit(double extraBenefit) {
        this.totalBenefit += extraBenefit;
    }

    private void incrementExpansionCount(CallTreeNode node) {
        ResolvedJavaMethod method = node.invoke().getTargetMethod();
        methodHistogram.put(method, methodHistogram.get(method, 0) + 1);
    }

    /**
     * Returns the graph cache for context free graphs or graphs with explicit OOME exception edges,
     * depending on the value of {@code inOOMEProtectedInlineContext}.
     */
    public GraphCache<ResolvedJavaMethod, StructuredGraph> getGraphCache(boolean inOOMEProtectedInlineContext) {
        return inOOMEProtectedInlineContext ? oomeGraphCache : graphCache;
    }

    public Expander.Policy getPolicy() {
        return policy;
    }

    public boolean hasExpansionsLeft() {
        return expansionsLeft > 0;
    }

    public void resetExpansionsLeft() {
        expansionsLeft = root.activeCutoffCount() + 1;
    }

    public void decrementExpansionLeft() {
        expansionsLeft--;
    }

    public int getInitialRootInvokes() {
        return initialRootInvokes;
    }

    public boolean isCallGraphTooBig() {
        return policy.isCallGraphTooBig(this);
    }

    public HighTierContext getContext() {
        return context;
    }

    public EconomicMap<ResolvedJavaMethod, Integer> methodHistogram() {
        return methodHistogram;
    }

    public TuningPolicy tuningPolicy() {
        return tuningPolicy;
    }

    ResolvedJavaMethod method() {
        return root.getReadonlySubgraph().method();
    }

    public InliningProvider inliningProvider() {
        return inliningProvider;
    }

    @Override
    public String toString() {
        return root.getReadonlySubgraph().method().format("call-tree[%H.%n]");
    }

    @SuppressWarnings("unused")
    public void devirtualizeHotCallees(CoreProviders coreProviders) {
        // Hook for subclass
    }

    /**
     * Returns {@code true} if the target method should always be inlined by the priority inliner.
     *
     * Returns {@code false} if the target method is {@code null}.
     *
     * @param javaMethod the target method
     * @return {@code true} if the target method should always be inlined by the priority inliner
     */
    public boolean matchesForceInlineFilter(JavaMethod javaMethod) {
        return forceInlineFilter != null && javaMethod != null && forceInlineFilter.matches(javaMethod);
    }

    /**
     * Returns the matched directed-inline rule text for this node, or {@code null} when no rule
     * matches.
     */
    public String matchDirectedInline(CallTreeNode node) {
        if (directedInliningRules == null || node == null) {
            return null;
        }
        Invoke invoke = node.invoke();
        if (invoke == null) {
            return null;
        }
        return directedInliningRules.findMatchingRuleOrPrefix(callsites(node), node.targetMethod(), receiverType(node), invoke.getTargetMethod());
    }

    /**
     * Returns {@code true} if this invoke and candidate target method are selected by a directed
     * inline rule for {@code receiverType}.
     */
    public boolean matchesDirectedInline(Invoke invoke, ResolvedJavaMethod targetMethod,
                    ResolvedJavaType receiverType, CallTreeNode caller) {
        if (directedInliningRules == null || invoke == null || targetMethod == null) {
            return false;
        }
        return directedInliningRules.findMatchingRuleOrPrefix(callsites(invoke, caller), targetMethod, receiverType, invoke.getTargetMethod()) != null;
    }

    /**
     * Returns {@code true} if any directed rule selects specific receiver types from the declared
     * callee target.
     */
    public boolean hasDirectedReceiverTypeFilters() {
        return (directedInliningRules != null && directedInliningRules.hasReceiverTypeFilters()) ||
                        (directedDontInliningRules != null && directedDontInliningRules.hasReceiverTypeFilters());
    }

    void snapshotDirectedInliningCallsites(CallTreeNode node) {
        if (node.directedInliningCallsites() == null) {
            node.setDirectedInliningCallsites(callsites(node));
        }
    }

    void copyDirectedInliningCallsites(CallTreeNode source, CallTreeNode target) {
        DirectedInliningRules.Callsite[] sourceCallsites = source.directedInliningCallsites();
        target.setDirectedInliningCallsites(sourceCallsites == null ? callsites(source) : sourceCallsites);
    }

    /**
     * Returns the matched directed dont-inline rule text for this node, or {@code null} when no
     * rule matches.
     */
    public String matchDirectedDontInline(CallTreeNode node) {
        if (directedDontInliningRules == null || node == null) {
            return null;
        }
        Invoke invoke = node.invoke();
        if (invoke == null) {
            return null;
        }
        return directedDontInliningRules.findMatchingRule(callsites(node), node.targetMethod(), receiverType(node), invoke.getTargetMethod());
    }

    private static ResolvedJavaType receiverType(CallTreeNode node) {
        if (node instanceof CutoffNode cutoffNode) {
            return cutoffNode.getOriginalDispatchedType();
        } else if (node instanceof SubgraphNode subgraphNode) {
            return subgraphNode.getOriginalDispatchedType();
        }
        return null;
    }

    private static DirectedInliningRules.Callsite[] callsites(CallTreeNode node) {
        if (node.directedInliningCallsites() != null) {
            return node.directedInliningCallsites();
        }
        if (node.parent() instanceof InlineCacheNode inlineCacheNode) {
            return callsites(inlineCacheNode.invoke(), inlineCacheNode.parent());
        }
        if (node.parent() != null && node.parent().isRoot()) {
            return DirectedInliningRules.rootGraphCallsites(node.invoke(), callerMethod(node));
        }
        if (node.parent() != null && node.parent().directedInliningCallsites() != null) {
            DirectedInliningRules.Callsite[] parentCallsites = node.parent().directedInliningCallsites();
            return DirectedInliningRules.append(parentCallsites, callsite(node));
        }
        DirectedInliningRules.Callsite[] callsites = DirectedInliningRules.EMPTY_CALLSITES;
        for (CallTreeNode current = node; current != null && !current.isRoot(); current = current.parent()) {
            callsites = prepend(callsites, callsite(current));
        }
        return callsites;
    }

    private static DirectedInliningRules.Callsite[] callsites(Invoke invoke, CallTreeNode caller) {
        if (caller == null || caller.isRoot()) {
            return DirectedInliningRules.rootGraphCallsites(invoke, callerMethod(invoke, caller));
        }
        DirectedInliningRules.Callsite[] callsites = callsites(caller);
        DirectedInliningRules.Callsite callsite = new DirectedInliningRules.Callsite(componentMethod(caller), receiverType(caller), caller.targetMethod(), callerBci(invoke));
        return DirectedInliningRules.append(callsites, callsite);
    }

    private static DirectedInliningRules.Callsite callsite(CallTreeNode node) {
        CallTreeNode caller = node.parent();
        if (caller == null || caller.isRoot()) {
            return new DirectedInliningRules.Callsite(callerMethod(node), callerBci(node.invoke()));
        }
        return new DirectedInliningRules.Callsite(componentMethod(caller), receiverType(caller), caller.targetMethod(), callerBci(node.invoke()));
    }

    private static ResolvedJavaMethod componentMethod(CallTreeNode node) {
        Invoke invoke = node.invoke();
        ResolvedJavaMethod declaredMethod = invoke == null ? null : invoke.getTargetMethod();
        return declaredMethod == null ? node.targetMethod() : declaredMethod;
    }

    private static DirectedInliningRules.Callsite[] prepend(DirectedInliningRules.Callsite[] callsites, DirectedInliningRules.Callsite callsite) {
        DirectedInliningRules.Callsite[] result = new DirectedInliningRules.Callsite[callsites.length + 1];
        result[0] = callsite;
        System.arraycopy(callsites, 0, result, 1, callsites.length);
        return result;
    }

    private static ResolvedJavaMethod callerMethod(CallTreeNode node) {
        Invoke invoke = node.invoke();
        return invoke == null ? fallbackCallerMethod(null, node.parent()) : DirectedInliningRules.callerMethod(invoke, fallbackCallerMethod(invoke, node.parent()));
    }

    private static ResolvedJavaMethod callerMethod(Invoke invoke, CallTreeNode caller) {
        return DirectedInliningRules.callerMethod(invoke, fallbackCallerMethod(invoke, caller));
    }

    private static ResolvedJavaMethod fallbackCallerMethod(Invoke invoke, CallTreeNode caller) {
        for (CallTreeNode current = caller; current != null; current = current.parent()) {
            if (current instanceof SubgraphNode subgraphNode) {
                ResolvedJavaMethod method = subgraphNode.targetMethod();
                return method == null ? subgraphNode.getReadonlySubgraph().method() : method;
            }
        }
        return invoke == null ? null : invoke.asNode().graph().method();
    }

    private static int callerBci(Invoke invoke) {
        return invoke == null ? DirectedInliningRules.ANY_BCI : invoke.bci();
    }

    /**
     * Returns {@code true} if the target method should never be inlined by the priority inliner.
     *
     * Returns {@code false} if the target method is {@code null}.
     *
     * @param javaMethod the target method
     * @return {@code true} if the target method should never be inlined by the priority inliner
     */
    public boolean matchesNeverInlineFilter(JavaMethod javaMethod) {
        return neverInlineFilter != null && javaMethod != null && neverInlineFilter.matches(javaMethod);
    }
}
