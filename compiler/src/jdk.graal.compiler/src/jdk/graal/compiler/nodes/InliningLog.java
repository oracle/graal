/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.MetaUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This class contains all inlining decisions performed on a graph during the compilation.
 *
 * Each inlining decision consists of:
 *
 * <ul>
 * <li>a value indicating whether the decision was positive or negative</li>
 * <li>the call target method</li>
 * <li>the reason for the inlining decision</li>
 * <li>the name of the phase in which the inlining decision took place</li>
 * <li>the inlining log of the inlined graph, or {@code null} if the decision was negative</li>
 * </ul>
 *
 * A phase that does inlining should use the instance of this class contained in the
 * {@link StructuredGraph} by calling {@link #addDecision} whenever it decides to inline a method.
 * If there are invokes in the graph at the end of the respective phase, then that phase must call
 * {@link #addDecision} to log negative decisions.
 */
public class InliningLog {
    private static final String TREE_NODE = "\u251c\u2500\u2500";
    private static final String LAST_TREE_NODE = "\u2514\u2500\u2500";

    public static final class Decision {
        private final boolean positive;
        private final String reason;
        private final String phase;
        private final ResolvedJavaMethod target;

        private Decision(boolean positive, String reason, String phase, ResolvedJavaMethod target) {
            this.positive = positive;
            this.reason = reason;
            this.phase = phase;
            this.target = target;
        }

        public boolean isPositive() {
            return positive;
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return String.format("<%s> %s: %s, %s", phase, target != null ? target.format("%H.%n(%p)") : "", positive ? "yes" : "no",
                            reason);
        }
    }

    /**
     * A call-tree node with inlining decisions. The root callsite represents the root compiled
     * method. Non-root nodes represent invokes in inlined methods' bodies.
     */
    public static final class Callsite {
        /**
         * A special bci for the root method's callsite.
         */
        private static final int ROOT_CALLSITE_BCI = -1;

        /**
         * The list of inlining decisions made about this callsite.
         */
        private final List<Decision> decisions;

        /**
         * The list of callsites in the inlined body of the target method.
         */
        private final List<Callsite> children;

        /**
         * The callsite whose inlined body contains this callsite. The value is {@code null} for the
         * root callsite.
         */
        private Callsite parent;

        /**
         * The invoke associated with the callsite. The value is {@code null} for the root callsite.
         *
         * For non-root nodes, {@link Invokable#getTargetMethod()} is used to obtain the
         * {@link #target target method}. However, the target method might change in the lifetime of
         * the node. Thus, the target method must be (1) initialized at the time the callsite is
         * created and (2) updated at the time {@link #addDecision a decision is made}.
         *
         * The invoke is also lost (the value is {@code null}) when it is removed and
         * {@link #copyTree copied}.
         */
        private Invokable invoke;

        /**
         * The target method of the callsite. This field should reflect the correct target method at
         * the end of a compilation.
         */
        private ResolvedJavaMethod target;

        /**
         * The bci of the invoke. The value is {@link #ROOT_CALLSITE_BCI} for the root method's
         * callsite. For other callsites, we remember their {@link Invokable#bci() invoke's bci}
         * because the invoke might be lost when it is removed.
         *
         * @see #copyTree(Callsite, Callsite, UnmodifiableEconomicMap, EconomicMap)
         */
        private final int bci;

        /**
         * {@code true} if the call was known to be indirect at the time of the last inlining
         * decision (or at the time the call-tree node was created if there was no inlining
         * decision).
         */
        private boolean indirect;

        /**
         * The original callsite holding the invoke from which this invoke was originally duplicated
         * or {@code null}.
         *
         * If this field is set, the optimization log interprets it as the true parent node
         * overriding the {@link #parent} field. This allows us to build a slightly different tree
         * in the optimization log while preserving the behavior of {@link #positionString()} and
         * {@link #formatAsTree}.
         *
         * It must hold that the original callsite (the value of this field) precedes this node in
         * the preorder traversal of the call tree. This property simplifies the construction of the
         * modified tree in the optimization log.
         */
        private final Callsite originalCallsite;

        /**
         * The last non-null type profile of the call target or {@code null}. The value is updated
         * on each inlining decision.
         */
        private JavaTypeProfile targetTypeProfile;

        Callsite(Callsite parent, Callsite originalCallsite, Invokable invoke, ResolvedJavaMethod target, int bci, boolean indirect, JavaTypeProfile targetTypeProfile) {
            this.parent = parent;
            this.bci = bci;
            this.indirect = indirect;
            this.decisions = new ArrayList<>();
            this.children = new ArrayList<>();
            this.invoke = invoke;
            this.target = target;
            this.originalCallsite = originalCallsite;
            this.targetTypeProfile = targetTypeProfile;
            if (parent != null) {
                parent.children.add(this);
            }
        }

        /**
         * Adds an inlining decision, updates the target method, the type profile, and the indirect
         * field.
         *
         * @param decision the decision to be added
         */
        private void addDecision(Decision decision) {
            decisions.add(decision);
            target = decision.target;
            indirect = !decision.positive && invokeIsIndirect(invoke);
            JavaTypeProfile newTypeProfile = targetTypeProfile(invoke);
            if (newTypeProfile != null) {
                targetTypeProfile = newTypeProfile;
            }
        }

        /**
         * Returns {@code true} if the invokable is an {@link Invoke} with an indirect call target.
         *
         * @param invokable an invokable
         * @return {@code true} if the invokable is an indirect invoke
         */
        private static boolean invokeIsIndirect(Invokable invokable) {
            if (!(invokable instanceof Invoke)) {
                return false;
            }
            CallTargetNode callTargetNode = ((Invoke) invokable).callTarget();
            if (callTargetNode == null) {
                return false;
            }
            return callTargetNode.invokeKind.isIndirect();
        }

        /**
         * Returns the type profile associated with the call target of the provided invoke or
         * {@code null}.
         *
         * @param invokable an invokable
         * @return the type profile of the call target or {@code null}
         */
        private static JavaTypeProfile targetTypeProfile(Invokable invokable) {
            if (!(invokable instanceof Invoke)) {
                return null;
            }
            CallTargetNode callTarget = ((Invoke) invokable).callTarget();
            if (!(callTarget instanceof MethodCallTargetNode)) {
                return null;
            }
            return ((MethodCallTargetNode) callTarget).getTypeProfile();
        }

        /**
         * Creates and adds a child call-tree node (callsite) to this node.
         *
         * @param childInvoke the invoke which represents the child callsite to be added
         * @param childOriginalCallsite the original callsite from which the child invoke was
         *            duplicated (if any)
         * @return the created callsite for the child
         */
        private Callsite addChild(Invokable childInvoke, Callsite childOriginalCallsite) {
            return new Callsite(this, childOriginalCallsite, childInvoke, childInvoke.getTargetMethod(), childInvoke.bci(), invokeIsIndirect(childInvoke), targetTypeProfile(childInvoke));
        }

        public String positionString() {
            if (parent == null) {
                if (target != null) {
                    return "compilation of " + target.format("%H.%n(%p)");
                } else if (invoke != null && invoke.getTargetMethod() != null) {
                    return "compilation of " + invoke.getTargetMethod().getName() + "(bci: " + getBci() + ")";
                } else {
                    return "unknown method (bci: " + getBci() + ")";
                }
            }
            String position;
            if (parent.target != null) {
                position = MetaUtil.appendLocation(new StringBuilder(100), parent.target, getBci()).toString();
            } else if (invoke != null && invoke.getTargetMethod() != null) {
                position = invoke.getTargetMethod().getName() + "(bci: " + getBci() + ")";
            } else {
                position = "unknown method (bci: " + getBci() + ")";
            }
            return "at " + position;
        }

        /**
         * Gets the list of inlining decisions made about this callsite.
         */
        public List<Decision> getDecisions() {
            return decisions;
        }

        /**
         * Gets the list of callsites in the inlined body of the target method.
         */
        public List<Callsite> getChildren() {
            return children;
        }

        /**
         * Gets the callsite whose inlined body contains this callsite. Returns {@code null} for the
         * root callsite.
         *
         * @return the parent callsite
         */
        public Callsite getParent() {
            return parent;
        }

        /**
         * Gets the invoke associated with the callsite. Returns {@code null} for the root callsite.
         * Might return {@code null} if the invoke was removed.
         *
         * @return the invoke associated with the callsite
         */
        public Invokable getInvoke() {
            return invoke;
        }

        /**
         * Updates the invoke associated with the callsite.
         *
         * @param newInvoke the new invoke
         */
        public void setInvoke(Invokable newInvoke) {
            invoke = newInvoke;
        }

        /**
         * Gets the target method of the callsite. The target is correct at the end of the
         * compilation.
         *
         * @return the target method of the callsite
         */
        public ResolvedJavaMethod getTarget() {
            return target;
        }

        /**
         * Gets the parent callsite, which may be overridden by {@link #originalCallsite} if it set.
         *
         * The optimization log interprets the call-tree node returned by this method as the parent
         * of this node. This allows it to build a slightly different call-tree while preserving the
         * behavior of {@link #positionString()} and {@link #formatAsTree}.
         *
         * @return the parent callsite (overridable by {@link #originalCallsite})
         */
        public Callsite getOverriddenParent() {
            return originalCallsite == null ? parent : originalCallsite;
        }

        /**
         * Gets the original callsite, which overrides the parent callsite when it is set.
         *
         * @see #getOverriddenParent()
         * @return the original callsite
         */
        public Callsite getOriginalCallsite() {
            return originalCallsite;
        }

        /**
         * Gets the bci of the invoke. Returns {@link #ROOT_CALLSITE_BCI} for the root callsite.
         *
         * @return the bci of the invoke
         */
        public int getBci() {
            return bci;
        }

        /**
         * Returns {@code true} if the call was known to be indirect at the time of the last
         * inlining decision (or at the time the call-tree node was created if there was no inlining
         * decision).
         *
         * @return {@code true} if the call was known to be indirect
         */
        public boolean isIndirect() {
            return indirect;
        }

        /**
         * Returns the last non-null type profile of the call target or {@code null}.
         */
        public JavaTypeProfile getTargetTypeProfile() {
            return targetTypeProfile;
        }

        /**
         * Returns {@code true} if the callsite is inlined.
         */
        public boolean isInlined() {
            return CollectionsUtil.anyMatch(decisions, InliningLog.Decision::isPositive);
        }
    }

    private Callsite root;

    private final EconomicMap<Invokable, Callsite> leaves;

    public InliningLog(ResolvedJavaMethod rootMethod) {
        this.root = new Callsite(null, null, null, rootMethod, Callsite.ROOT_CALLSITE_BCI, false, null);
        this.leaves = EconomicMap.create();
    }

    /**
     * Add an inlining decision for the specified invoke.
     *
     * An inlining decision can be either positive or negative. A positive inlining decision must be
     * logged after replacing an {@link Invoke} with a graph. In this case, the node replacement map
     * and the {@link InliningLog} of the inlined graph must be provided.
     */
    void addDecision(Invokable invoke, boolean positive, String phase, EconomicMap<Node, Node> replacements, InliningLog calleeLog, ResolvedJavaMethod inlineeMethod, String reason, Object... args) {
        assert leaves.containsKey(invoke) : invoke;
        assert !positive || Objects.isNull(replacements) == Objects.isNull(calleeLog) : Assertions.errorMessage(positive, replacements, calleeLog);
        Callsite callsite = leaves.get(invoke);
        callsite.addDecision(new Decision(positive, String.format(reason, args), phase, inlineeMethod));
        if (positive) {
            leaves.removeKey(invoke);
            if (calleeLog == null) {
                return;
            }
            EconomicMap<Callsite, Callsite> mapping = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            for (Callsite calleeChild : calleeLog.root.children) {
                copyTree(callsite, calleeChild, replacements, mapping);
            }
            MapCursor<Invokable, Callsite> entries = calleeLog.leaves.getEntries();
            while (entries.advance()) {
                FixedNode invokeFromCallee = entries.getKey().asFixedNodeOrNull();
                Callsite callsiteFromCallee = entries.getValue();
                if (invokeFromCallee == null || invokeFromCallee.isDeleted()) {
                    // Some invoke nodes could have been removed by optimizations.
                    continue;
                }
                Invokable inlinedInvokeFromCallee = (Invokable) replacements.get(invokeFromCallee);
                Callsite descendant = mapping.get(callsiteFromCallee);
                leaves.put(inlinedInvokeFromCallee, descendant);
            }
        }
    }

    /**
     * Appends the inlining decision tree from {@code replacementLog} to this log.
     *
     * This is called for example when a node in a graph is replaced with a snippet.
     *
     * @param replacementLog if non-null, its subtrees are appended below the root of this log.
     * @see InliningLog#addDecision
     */
    public void addLog(UnmodifiableEconomicMap<Node, Node> replacements, InliningLog replacementLog) {
        if (replacementLog != null) {
            EconomicMap<Callsite, Callsite> mapping = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
            for (Callsite calleeChild : replacementLog.root.children) {
                copyTree(root, calleeChild, replacements, mapping);
            }
            MapCursor<Invokable, Callsite> entries = replacementLog.leaves.getEntries();
            while (entries.advance()) {
                FixedNode replacementInvoke = entries.getKey().asFixedNodeOrNull();
                Callsite replacementCallsite = entries.getValue();
                if (replacementInvoke == null || replacementInvoke.isDeleted()) {
                    // Some invoke nodes could have been removed by optimizations.
                    continue;
                }
                Invokable invoke = (Invokable) replacements.get(replacementInvoke);
                Callsite callsite = mapping.get(replacementCallsite);
                leaves.put(invoke, callsite);
            }
        }
    }

    /**
     * Completely replace this log's contents with a copy of {@code replacementLog}'s contents.
     *
     * The precondition is that this inlining log is completely empty. This is usually called as
     * part of graph copying.
     *
     * @see InliningLog#addDecision
     */
    public void replaceLog(UnmodifiableEconomicMap<Node, Node> replacements, InliningLog replacementLog) {
        assert root.decisions.isEmpty();
        assert root.children.isEmpty();
        assert leaves.isEmpty();
        EconomicMap<Callsite, Callsite> mapping = EconomicMap.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        root = copyTree(null, replacementLog.root, replacements, mapping);
        MapCursor<Invokable, Callsite> replacementEntries = replacementLog.leaves.getEntries();
        while (replacementEntries.advance()) {
            FixedNode replacementInvoke = replacementEntries.getKey().asFixedNodeOrNull();
            Callsite replacementSite = replacementEntries.getValue();
            if (replacementInvoke != null && replacementInvoke.isAlive()) {
                Invokable invoke = (Invokable) replacements.get(replacementInvoke);
                Callsite site = mapping.get(replacementSite);
                leaves.put(invoke, site);
            }
        }
    }

    /**
     * Recursively copies a call tree and adds it to this call tree.
     *
     * @param parent the call-tree node which will hold the copy ({@code null} if the copy replaces
     *            the root)
     * @param replacementSite the root of the call tree to be copied
     * @param replacements the mapping from original graph nodes to replaced nodes
     * @param mapping the mapping from original call-tree nodes to copies
     * @return the root of the copied subtree
     */
    private static Callsite copyTree(Callsite parent, Callsite replacementSite, UnmodifiableEconomicMap<Node, Node> replacements, EconomicMap<Callsite, Callsite> mapping) {
        Invokable invoke = null;
        if (replacementSite.invoke != null) {
            FixedNode replacementSiteInvoke = replacementSite.invoke.asFixedNodeOrNull();
            if (replacementSiteInvoke != null && replacementSiteInvoke.isAlive()) {
                invoke = (Invokable) replacements.get(replacementSiteInvoke);
            }
        }
        Callsite originalCallsite = replacementSite.originalCallsite == null ? null : mapping.get(replacementSite.originalCallsite);
        Callsite site = new Callsite(parent, originalCallsite, invoke, replacementSite.target, replacementSite.bci, replacementSite.indirect, replacementSite.targetTypeProfile);
        site.decisions.addAll(replacementSite.decisions);
        mapping.put(replacementSite, site);
        for (Callsite replacementChild : replacementSite.children) {
            copyTree(site, replacementChild, replacements, mapping);
        }
        return site;
    }

    public void checkInvariants(StructuredGraph graph) {
        if (!Assertions.assertionsEnabled()) {
            return;
        }
        for (Invoke invoke : graph.getInvokes()) {
            assert leaves.containsKey(invoke) : "Invoke " + invoke + " not contained in the leaves.";
        }
        checkParentPointers();
    }

    private void checkParentPointers() {
        assert root.parent == null : "Non-null parent of root: " + root.parent;
        if (Assertions.assertionsEnabled()) {
            checkParentPointers(root);
        }
    }

    private static void checkParentPointers(Callsite site) {
        for (Callsite child : site.children) {
            assert site == child.parent : "Callsite " + site + " with child " + child + " has an invalid parent pointer " + child.parent;
            checkParentPointers(child);
        }
    }

    private final UpdateScope noUpdates = new UpdateScope((oldNode, newNode) -> {
    });

    private UpdateScope currentUpdateScope = null;

    /**
     * Used to designate scopes in which {@link Invokable} registration or cloning should be handled
     * differently.
     */
    public final class UpdateScope implements AutoCloseable {
        private final BiConsumer<Invokable, Invokable> updater;

        private UpdateScope(BiConsumer<Invokable, Invokable> updater) {
            this.updater = updater;
        }

        public void activate() {
            if (currentUpdateScope != null) {
                throw GraalError.shouldNotReachHere("InliningLog updating already set."); // ExcludeFromJacocoGeneratedReport
            }
            currentUpdateScope = this;
        }

        @Override
        public void close() {
            assert currentUpdateScope != null;
            currentUpdateScope = null;
        }

        public BiConsumer<Invokable, Invokable> getUpdater() {
            return updater;
        }
    }

    public BiConsumer<Invokable, Invokable> getUpdateScope() {
        if (currentUpdateScope == null) {
            return null;
        }
        return currentUpdateScope.getUpdater();
    }

    /**
     * Creates and sets a new update scope for the log.
     *
     * The specified {@code updater} is invoked when an {@link Invokable} node is registered or
     * cloned. If the node is newly registered, then the first argument to the {@code updater} is
     * {@code null}. If the node is cloned, then the first argument is the node it was cloned from.
     *
     * @param updater an operation taking a null (or the original node), and the registered (or
     *            cloned) {@link Invokable}
     * @return a bound {@link UpdateScope} object, or a {@code null} if tracing is disabled
     */
    public UpdateScope openUpdateScope(BiConsumer<Invokable, Invokable> updater) {
        UpdateScope scope = new UpdateScope(updater);
        scope.activate();
        return scope;
    }

    /**
     * Creates a new update scope that does not update {@code log}.
     *
     * This update scope will not add a newly created {@code Invokable} to the log, nor will it
     * amend its position if it was cloned. Instead, users need to update the inlining log with the
     * new {@code Invokable} on their own.
     *
     * @see #openUpdateScope
     */
    public static UpdateScope openDefaultUpdateScope(InliningLog log) {
        if (log == null) {
            return null;
        }
        log.noUpdates.activate();
        return log.noUpdates;
    }

    /**
     * Opens a new update scope that registers callsites for duplicated invokes and sets the
     * {@link Callsite#originalCallsite} of the duplicated callsite to the original callsite (the
     * callsite of the invoke from which it is duplicated).
     *
     * @return a bound {@link UpdateScope} or {@code null} if the log is disabled
     */
    public static UpdateScope openUpdateScopeTrackingOriginalCallsites(InliningLog inliningLog) {
        if (inliningLog == null) {
            return null;
        }
        return inliningLog.openUpdateScope((originalInvoke, newInvoke) -> {
            if (originalInvoke != null) {
                assert !inliningLog.containsLeafCallsite(newInvoke);
                inliningLog.trackDuplicatedCallsite(originalInvoke, newInvoke, inliningLog.leaves.get(originalInvoke));
            }
        });
    }

    /**
     * Opens a new update scope tracking the replacement of an invoke. Exactly one invoke must be
     * registered when this scope is active, which becomes the replacement invoke. The method
     * associates the {@link Callsite} of the replaced invoke with the replacement invoke,
     * preserving all inlining decisions. It is a responsibility of the caller to delete the
     * replaced invoke.
     *
     * If the log is in a {@link #openDefaultUpdateScope default} update scope (i.e., updates to
     * invokes are handled by the user), this call has no effect.
     *
     * @param inliningLog the inlining log or {@code null} if it disabled
     * @param replacedInvoke the invoke that is getting replaced
     *
     * @return a bound {@link UpdateScope} or {@code null} if the log is disabled
     */
    public static UpdateScope openUpdateScopeTrackingReplacement(InliningLog inliningLog, Invokable replacedInvoke) {
        if (inliningLog == null || inliningLog.currentUpdateScope == inliningLog.noUpdates) {
            return null;
        }
        return inliningLog.openUpdateScope((nullInvoke, replacementInvoke) -> {
            assert nullInvoke == null;
            assert !inliningLog.leaves.containsKey(replacementInvoke);
            Callsite callsite = inliningLog.leaves.get(replacedInvoke);
            callsite.invoke = replacementInvoke;
            inliningLog.leaves.removeKey(replacedInvoke);
            inliningLog.leaves.put(replacementInvoke, callsite);
        });
    }

    private RootScope currentRootScope = null;

    /**
     * Used to change the current effective root of the method being compiled.
     *
     * This root scope is used in situations in which a phase does its own ad-hoc inlining, in which
     * it replaces an Invoke with other nodes, some of which may be other Invokes. The prime example
     * for this is the bytecode parser, which does not create separate graphs with their own
     * inlining logs when inlining an Invoke, but instead continues recursively parsing the graph
     * corresponding to the Invoke.
     *
     * Root scopes can be nested.
     *
     * @see #openRootScope
     */
    public final class RootScope implements AutoCloseable {
        private final RootScope parent;
        private final Callsite replacementRoot;

        public RootScope(RootScope parent, Callsite replacementRoot) {
            this.parent = parent;
            this.replacementRoot = replacementRoot;
        }

        void activate() {
            currentRootScope = this;
        }

        public Invokable getInvoke() {
            return replacementRoot.invoke;
        }

        @Override
        public void close() {
            assert currentRootScope != null;
            unregisterLeafCallsite(replacementRoot.invoke);
            currentRootScope = parent;
        }
    }

    public static final class PlaceholderInvokable implements Invokable {
        private final int bci;
        private final ResolvedJavaMethod callerMethod;
        private final ResolvedJavaMethod method;

        public PlaceholderInvokable(ResolvedJavaMethod callerMethod, ResolvedJavaMethod method, int bci) {
            this.callerMethod = callerMethod;
            this.method = method;
            this.bci = bci;
        }

        @Override
        public ResolvedJavaMethod getTargetMethod() {
            return method;
        }

        @Override
        public int bci() {
            return bci;
        }

        @Override
        public void setBci(int bci) {
            GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public FixedNode asFixedNodeOrNull() {
            return null;
        }

        @Override
        public ResolvedJavaMethod getContextMethod() {
            return callerMethod;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(bci) ^ callerMethod.hashCode() ^ method.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof PlaceholderInvokable) {
                final PlaceholderInvokable that = (PlaceholderInvokable) obj;
                return that.bci == bci && that.method.equals(method) && that.callerMethod.equals(callerMethod);
            }
            return false;
        }

        @Override
        public String toString() {
            return String.format("Invokable(caller: %s, bci: %d, method: %s)", callerMethod.format("%H.%n"), bci, method.format("%H.%n"));
        }
    }

    public RootScope openRootScope(ResolvedJavaMethod callerMethod, ResolvedJavaMethod target, int bci) {
        return openRootScope(new PlaceholderInvokable(callerMethod, target, bci));
    }

    public RootScope openRootScope(Invokable invoke) {
        if (!leaves.containsKey(invoke)) {
            // Create the invoke if it was not added to the graph yet.
            trackNewCallsite(invoke);
        }
        RootScope scope = new RootScope(currentRootScope, leaves.get(invoke));
        scope.activate();
        return scope;
    }

    public boolean containsLeafCallsite(Invokable invokable) {
        return leaves.containsKey(invokable);
    }

    /**
     * Removes a callsite from the set of leaf callsites. The callsite is not removed from the call
     * tree. The callsite can be {@link #registerLeafCallsite re-registered} later.
     *
     * @param invokable the invoke representing the callsite
     * @return the unregistered callsite
     */
    public Callsite unregisterLeafCallsite(Invokable invokable) {
        return leaves.removeKey(invokable);
    }

    /**
     * Registers a callsite as a leaf callsite. This method must be called during graph compression,
     * or other node-id changes.
     *
     * @param invokable the invoke representing the callsite
     * @param callsite the callsite to be registered
     */
    public void registerLeafCallsite(Invokable invokable, Callsite callsite) {
        leaves.put(invokable, callsite);
    }

    /**
     * Removes a leaf callsite from the call tree. This implies {@link #unregisterLeafCallsite
     * unregistering} it from the set of leaves.
     *
     * @param invokable the invoke representing the callsite
     */
    public void removeLeafCallsite(Invokable invokable) {
        Callsite callsite = unregisterLeafCallsite(invokable);
        assert callsite != null : "it must be a leaf callsite";
        assert callsite.parent != null : "a leaf callsite must have a parent";
        callsite.parent.children.remove(callsite);
    }

    public void trackNewCallsite(Invokable invoke) {
        assert !leaves.containsKey(invoke);
        Callsite currentRoot = findCurrentRoot();
        Callsite callsite = currentRoot.addChild(invoke, null);
        leaves.put(invoke, callsite);
    }

    private Callsite findCurrentRoot() {
        return currentRootScope != null ? currentRootScope.replacementRoot : root;
    }

    public void trackDuplicatedCallsite(Invokable sibling, Invokable newInvoke, Callsite childOriginalCallsite) {
        Callsite siblingCallsite = leaves.get(sibling);
        Callsite parentCallsite = siblingCallsite.parent;
        Callsite callsite = parentCallsite.addChild(newInvoke, childOriginalCallsite);
        callsite.decisions.addAll(siblingCallsite.decisions);
        leaves.put(newInvoke, callsite);
    }

    /**
     * Formats the inlining log as a hierarchical tree.
     *
     * @param nullIfEmpty specifies whether null should be returned if there are no inlining
     *            decisions
     * @return the tree representation of the inlining log
     */
    public String formatAsTree(boolean nullIfEmpty) {
        assert root.decisions.isEmpty();
        assert !root.children.isEmpty() || leaves.isEmpty();
        if (nullIfEmpty && root.children.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(512);
        formatAsTree(root, "", builder);
        return builder.toString();
    }

    private static void formatAsTree(Callsite site, String indent, StringBuilder builder) {
        String position = site.positionString();
        builder.append(indent).append(position).append(": ");
        if (site.decisions.isEmpty()) {
            if (site.parent != null) {
                builder.append("(no decisions made about ").append(site.target != null ? site.target.format("%H.%n(%p)") : "callee").append(")");
            }
            builder.append(System.lineSeparator());
        } else if (site.decisions.size() == 1) {
            builder.append(site.decisions.get(0).toString());
            builder.append(System.lineSeparator());
        } else {
            builder.append(System.lineSeparator());
            for (Decision decision : site.decisions) {
                String node = (decision == site.decisions.get(site.decisions.size() - 1)) ? LAST_TREE_NODE : TREE_NODE;
                builder.append(indent + "   " + node).append(decision.toString());
                builder.append(System.lineSeparator());
            }
        }
        for (Callsite child : site.children) {
            formatAsTree(child, indent + "  ", builder);
        }
    }

    /**
     * Gets the callsite representing the root method.
     */
    public Callsite getRootCallsite() {
        return root;
    }

    /**
     * Sets the call tree to the provided call tree.
     *
     * @param root the root of the new call tree
     */
    public void setRootCallsite(Callsite root) {
        this.root = root;
    }

    /**
     * Adds a positive inlining decision and transfers the inlining log of the callee to this log.
     * The inlining log of the callee must be associated with the same graph. The inlining log of
     * the callee is cleared and should not be used anymore. The target of the callsite is updated
     * using the provided call target.
     *
     * @param invoke the inlined invoke
     * @param callTargetNode the call target of the invoke
     * @param calleeLog the inlining log of the callee
     * @param phase the phase which performed the decision
     * @param reason the reason for the inlining decision
     */
    public void inlineByTransfer(Invokable invoke, CallTargetNode callTargetNode, InliningLog calleeLog, String phase, String reason) {
        Callsite caller = leaves.get(invoke);
        caller.addDecision(new Decision(true, reason, phase, invoke.getTargetMethod()));
        caller.target = callTargetNode.targetMethod;
        caller.indirect = callTargetNode.invokeKind.isIndirect();
        for (Callsite child : calleeLog.getRootCallsite().children) {
            caller.children.add(child);
            child.parent = caller;
        }
        leaves.removeKey(invoke);
        leaves.putAll(calleeLog.leaves);
        calleeLog.root.children.clear();
        calleeLog.leaves.clear();
        checkParentPointers();
    }
}
