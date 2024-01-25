/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements;

import static java.util.FormattableFlags.ALTERNATE;
import static jdk.graal.compiler.debug.DebugContext.applyFormattingFlagsAndWidth;
import static jdk.graal.compiler.debug.DebugOptions.DumpOnError;
import static jdk.graal.compiler.graph.iterators.NodePredicates.isNotA;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;
import static jdk.graal.compiler.phases.common.DeadCodeEliminationPhase.Optionality.Required;
import static jdk.vm.ci.services.Services.IS_IN_NATIVE_IMAGE;
import static org.graalvm.word.LocationIdentity.any;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Formattable;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.WordBase;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet.VarargsParameter;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.api.replacements.SnippetTemplateCache;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.RetryableBailoutException;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugOptions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeMap;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.loop.phases.LoopTransformations;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeCycles;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodeinfo.NodeSize;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.DeoptBciSupplier;
import jdk.graal.compiler.nodes.DeoptimizingNode;
import jdk.graal.compiler.nodes.DeoptimizingNode.DeoptBefore;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GraphState.GuardsStage;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InliningLog;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicConstantNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MemoryMapControlSinkNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode.Placeholder;
import jdk.graal.compiler.nodes.PiNode.PlaceholderStamp;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.ProfileData.ProfileSource;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValueNodeInterface;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.VirtualState.NodePositionClosure;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.graal.compiler.nodes.extended.CaptureStateBeginNode;
import jdk.graal.compiler.nodes.extended.GuardedNode;
import jdk.graal.compiler.nodes.java.AbstractNewObjectNode;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.StoreIndexedNode;
import jdk.graal.compiler.nodes.loop.LoopEx;
import jdk.graal.compiler.nodes.memory.MemoryAccess;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.MemoryKill;
import jdk.graal.compiler.nodes.memory.MemoryMap;
import jdk.graal.compiler.nodes.memory.MemoryMapNode;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.memory.MultiMemoryKill;
import jdk.graal.compiler.nodes.memory.SingleMemoryKill;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.MemoryEdgeProxy;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.SnippetParameterInfo;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.PhaseSuite;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.common.DeadCodeEliminationPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase;
import jdk.graal.compiler.phases.common.FloatingReadPhase.MemoryMapImpl;
import jdk.graal.compiler.phases.common.GuardLoweringPhase;
import jdk.graal.compiler.phases.common.HighTierLoweringPhase;
import jdk.graal.compiler.phases.common.IterativeConditionalEliminationPhase;
import jdk.graal.compiler.phases.common.LowTierLoweringPhase;
import jdk.graal.compiler.phases.common.LoweringPhase;
import jdk.graal.compiler.phases.common.MidTierLoweringPhase;
import jdk.graal.compiler.phases.common.RemoveValueProxyPhase;
import jdk.graal.compiler.phases.common.SnippetFrameStateAssignment;
import jdk.graal.compiler.phases.common.SnippetFrameStateAssignment.NodeStateAssignment;
import jdk.graal.compiler.phases.common.SnippetFrameStateAssignment.SnippetFrameStateAssignmentClosure;
import jdk.graal.compiler.phases.common.WriteBarrierAdditionPhase;
import jdk.graal.compiler.phases.common.inlining.InliningUtil;
import jdk.graal.compiler.phases.graph.ReentrantNodeIterator;
import jdk.graal.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.nodes.CStringConstant;
import jdk.graal.compiler.replacements.nodes.ExplodeLoopNode;
import jdk.graal.compiler.replacements.nodes.FallbackInvokeWithExceptionNode;
import jdk.graal.compiler.replacements.nodes.LoadSnippetVarargParameterNode;
import jdk.graal.compiler.replacements.nodes.MacroWithExceptionNode;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.virtual.phases.ea.PartialEscapePhase;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * A snippet template is a graph created by parsing a snippet method and then specialized by binding
 * constants to the snippet's {@link ConstantParameter} parameters.
 *
 * Snippet templates can be managed in a cache maintained by {@link AbstractTemplates}.
 *
 * <h1>Snippet Lowering of {@link WithExceptionNode}</h1>
 *
 * Lowering of {@link WithExceptionNode} is more complicated than for normal (fixed) nodes, because
 * of the {@linkplain WithExceptionNode#exceptionEdge() exception edge}.
 *
 * <h2>Replacing a WithExceptionNode with a Snippet with an {@link UnwindNode}</h2>
 *
 * <h3>Replacee Graph</h3>
 *
 * The <em>replacee graph</em> is the graph that contains the node to be lowered and where the
 * <em>snippet graph</em> will be inlined into. In the example below, the {@code WithException} node
 * will be lowered via a snippet.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *           WithException  <--- // to be lowered
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin       ExceptionObject
 *         |              |
 *     nextSucc      exceptionSucc
 *         |              |
 *        ...            ...
 * </pre>
 *
 * <h3>Snippet Graph</h3>
 *
 * A <em>snippet graph</em> is a complete graph starting with a {@link StartNode} and ending with a
 * {@link ReturnNode} and or an {@link UnwindNode}. For simplicity, multiple {@linkplain ReturnNode
 * ReturnNodes} are {@linkplain InliningUtil#mergeReturns merged} and replaced by a single return
 * node.
 *
 * The following is the simplest possible example for a snippet with an {@link UnwindNode}. The
 * snippet consists of an @code InvokeWithException'}.
 *
 * <pre>
 *              Start'
 *                |
 *         InvokeWithException'
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin'      ExceptionObject'
 *         |              |
 *      Return'        Unwind'
 * </pre>
 *
 * <h3>Replacee Graph after inlining</h3>
 *
 * After inlining the snippet, the {@code WithException} node in the replacee graph is replaced by
 * the snippet graph. The {@code Start'} is connected to the predecessor, the {@code Return'} to the
 * {@linkplain WithExceptionNode#next() next} edge, and the {@code Unwind'} to the
 * {@linkplain WithExceptionNode#exceptionEdge() exception edge}.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *         InvokeWithException'
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin'      ExceptionObject'
 *         |              |
 *       Begin       ExceptionObject
 *         |              |
 *     nextSucc      exceptionSucc
 *         |              |
 *        ...            ...
 * </pre>
 *
 * It is important to note that we do not yet remove the original {@code Begin} and
 * {@code WithException} node, although they are no longer needed. Doing so would require support
 * from the {@link LoweringPhase}, which might still hold references to these nodes for subsequent
 * lowering rounds. {@linkplain BeginNode#simplify Simplifications} and
 * {@linkplain ExceptionObjectNode#lower lowering} will take care of removing these nodes.
 *
 * <h3>Replacee Graph after lowering</h3>
 *
 * After the whole replacee graph has been lowered, the superfluous nodes are gone.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *         InvokeWithException'
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin'      ExceptionObject'
 *         |              |
 *     nextSucc      exceptionSucc
 *         |              |
 *        ...            ...
 * </pre>
 *
 * <h2>Replacing a non-throwing WithExceptionNode with a Snippet with an {@link UnwindNode}</h2>
 *
 * <h3>Replacee Graph</h3>
 *
 * {@linkplain WithExceptionNode#replaceWithNonThrowing() Non-throwing} {@link WithExceptionNode}
 * are nodes where the {@linkplain WithExceptionNode#exceptionEdge() exception edge} is an
 * {@link UnreachableBeginNode} followed by an {@link UnreachableControlSinkNode}. This pattern is
 * used to encode that we have proven that the {@link WithExceptionNode} will never throw an
 * exception.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *           WithException  <--- // to be lowered
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin       UnreachableBegin
 *         |              |
 *     nextSucc      UnreachableControlSink
 *         |
 *        ...
 * </pre>
 *
 * <h3>Snippet Graph</h3>
 *
 * The <em>snippet graph</em> is the same as in the example above.
 *
 * <pre>
 *              Start'
 *                |
 *         InvokeWithException'
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin'      ExceptionObject'
 *         |              |
 *      Return'        Unwind'
 * </pre>
 *
 * <h3>Replacee Graph after inlining</h3>
 *
 * In this case, we remove the {@code ExceptionObject'} from the snippet graph, and attach the
 * original {@code UnreachableBegin} directly to the exception producing instruction from the
 * snippet (the {@code InvokeWithException'} node in this example).
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *         InvokeWithException'
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin'      UnreachableBegin
 *         |              |
 *       Begin       UnreachableControlSink
 *         |
 *     nextSucc
 *         |
 *        ...
 * </pre>
 *
 * <h2>Replacing a WithExceptionNode with a Snippet without an {@link UnwindNode}</h2>
 *
 * There are cases where snippets are known not to throw an exception, although the replacee is a
 * {@link WithExceptionNode}.
 *
 * <h3>Replacee Graph</h3>
 *
 * Let us reuse the example from the first case.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *           WithException  <--- // to be lowered
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin       ExceptionObject
 *         |              |
 *     nextSucc      exceptionSucc
 *         |              |
 *        ...            ...
 * </pre>
 *
 * <h3>Snippet Graph</h3>
 *
 * In this example, the snippet graph consists of a single {@code NonThrowingWork'} node.
 *
 * <pre>
 *              Start'
 *                |
 *          NonThrowingWork'
 *                |
 *             Return'
 * </pre>
 *
 * <h3>Replacee Graph after inlining</h3>
 *
 * As outlined above, we cannot simply delete the nodes connected to the replacee because it would
 * interfere with the {@link LoweringPhase}. Thus, we introduce an artificial
 * {@code PlaceholderWithException'} that connects to the
 * {@linkplain WithExceptionNode#exceptionEdge() exception edge}. This placeholder node removes the
 * exception edge and itself in a subsequent {@linkplain PlaceholderWithExceptionNode#simplify
 * canonicalization}.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
  *                |
 *           NonThrowingWork'
 *                 |
 *      PlaceholderWithException'
 *            /         \
 *    [next] /           \ [exceptionEdge]
 *          /             \
 *       Begin'      ExceptionObject'
 *         |              |
 *       Begin       ExceptionObject
 *         |              |
 *     nextSucc      exceptionSucc
 *         |              |
 *        ...            ...
 * </pre>
 *
 * <h3>Replacee Graph after lowering</h3>
 *
 * After the whole replacee graph has been lowered, the graph looks like the following.
 *
 * <pre>
 *                ...
 *                 |
 *                pred
 *                 |
 *           NonThrowingWork'
 *                 |
 *             nextSucc
 *                 |
 *                ...
 * </pre>
 *
 */
public class SnippetTemplate {

    /**
     * Times instantiations of all templates derived from this snippet.
     */
    private static final TimerKey totalInstantiationTimer = DebugContext.timer("SnippetInstantiationTime");

    /**
     * Counts instantiations of all templates derived from this snippet.
     */
    private static final CounterKey totalInstantiationCounter = DebugContext.counter("SnippetInstantiationCount");

    private boolean mayRemoveLocation = false;

    /**
     * Holds the {@link ResolvedJavaMethod} of the snippet, together with some information about the
     * method that needs to be computed only once. The {@link SnippetInfo} should be created once
     * per snippet and then cached.
     */
    public abstract static class SnippetInfo {

        protected final ResolvedJavaMethod method;
        protected final ResolvedJavaMethod original;
        protected final LocationIdentity[] privateLocations;
        protected final Object receiver;

        public Object getReceiver() {
            return receiver;
        }

        boolean hasReceiver() {
            assert hasReceiver(method) == (receiver != null) : "Snippet with the receiver must have it set as constant. Snippet: " + this;
            return hasReceiver(method);
        }

        static boolean hasReceiver(ResolvedJavaMethod method) {
            return method.hasReceiver();
        }

        /**
         * Times instantiations of all templates derived from this snippet.
         */
        private final TimerKey instantiationTimer;

        /**
         * Counts instantiations of all templates derived from this snippet.
         */
        private final CounterKey instantiationCounter;

        private final CounterKey creationCounter;

        private final TimerKey creationTimer;

        protected abstract SnippetParameterInfo info();

        protected SnippetInfo(ResolvedJavaMethod method, ResolvedJavaMethod original, LocationIdentity[] privateLocations, Object receiver) {
            this.method = method;
            this.original = original;
            this.privateLocations = privateLocations;
            instantiationCounter = DebugContext.counter("SnippetInstantiationCount[%s]", method.getName());
            instantiationTimer = DebugContext.timer("SnippetInstantiationTime[%s]", method.getName());
            creationCounter = DebugContext.counter("SnippetCreationCount[%s]", method.getName());
            creationTimer = DebugContext.timer("SnippetCreationTime[%s]", method.getName());
            this.receiver = receiver;
        }

        public ResolvedJavaMethod getMethod() {
            return method;
        }

        public int getParameterCount() {
            return info().getParameterCount();
        }

        public boolean isConstantParameter(int paramIdx) {
            return info().isConstantParameter(paramIdx);
        }

        public boolean isVarargsParameter(int paramIdx) {
            return info().isVarargsParameter(paramIdx);
        }

        public boolean isNonNullParameter(int paramIdx) {
            return info().isNonNullParameter(paramIdx);
        }

        public String getParameterName(int paramIdx) {
            return info().getParameterName(paramIdx);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ":" + method.format("%h.%n");
        }
    }

    protected static class LazySnippetInfo extends SnippetInfo {
        protected final AtomicReference<SnippetParameterInfo> lazy = new AtomicReference<>(null);

        protected LazySnippetInfo(ResolvedJavaMethod method, ResolvedJavaMethod original, LocationIdentity[] privateLocations, Object receiver) {
            super(method, original, privateLocations, receiver);
        }

        @Override
        protected SnippetParameterInfo info() {
            if (lazy.get() == null) {
                lazy.compareAndSet(null, new SnippetParameterInfo(method));
            }
            return lazy.get();
        }
    }

    public static class EagerSnippetInfo extends SnippetInfo {
        protected final SnippetParameterInfo snippetParameterInfo;

        protected EagerSnippetInfo(ResolvedJavaMethod method, ResolvedJavaMethod original, LocationIdentity[] privateLocations, Object receiver, SnippetParameterInfo snippetParameterInfo) {
            super(method, original, privateLocations, receiver);
            this.snippetParameterInfo = snippetParameterInfo;
        }

        @Override
        protected SnippetParameterInfo info() {
            return snippetParameterInfo;
        }

        public EagerSnippetInfo copyWith(ResolvedJavaMethod newMethod) {
            return new EagerSnippetInfo(newMethod, original, privateLocations, receiver, snippetParameterInfo);
        }
    }

    /**
     * Values that are bound to the snippet method parameters. The methods {@link #add},
     * {@link #addConst}, and {@link #addVarargs} must be called in the same order as in the
     * signature of the snippet method. The parameter name is passed to the add methods for
     * assertion checking, i.e., to enforce that the order matches. Which method needs to be called
     * depends on the annotation of the snippet method parameter:
     * <ul>
     * <li>Use {@link #add} for a parameter without an annotation. The value is bound when the
     * {@link SnippetTemplate} is {@link SnippetTemplate#instantiate instantiated}.
     * <li>Use {@link #addConst} for a parameter annotated with {@link ConstantParameter}. The value
     * is bound when the {@link SnippetTemplate} is {@link SnippetTemplate#SnippetTemplate created}.
     * <li>Use {@link #addVarargs} for an array parameter annotated with {@link VarargsParameter}. A
     * separate {@link SnippetTemplate} is {@link SnippetTemplate#SnippetTemplate created} for every
     * distinct array length. The actual values are bound when the {@link SnippetTemplate} is
     * {@link SnippetTemplate#instantiate instantiated}
     * </ul>
     */
    public static final class Arguments implements Formattable {

        protected final SnippetInfo info;
        protected final CacheKey cacheKey;
        protected final Object[] values;
        protected final Stamp[] constStamps;
        protected boolean cacheable;

        protected int nextParamIdx;

        public Arguments(SnippetInfo info, GuardsStage guardsStage, LoweringTool.LoweringStage loweringStage) {
            this.info = info;
            this.cacheKey = new CacheKey(info, guardsStage, loweringStage);
            this.values = new Object[info.getParameterCount()];
            this.constStamps = new Stamp[info.getParameterCount()];
            this.cacheable = true;
            if (info.hasReceiver()) {
                addConst("this", info.getReceiver());
            }
        }

        public Arguments add(String name, Object value) {
            assert check(name, false, false);
            values[nextParamIdx] = value;
            nextParamIdx++;
            return this;
        }

        public Arguments addConst(String name, Object value) {
            assert value != null;
            if (value instanceof CStringConstant) {
                return addConst(name, value, StampFactory.pointer());
            }
            return addConst(name, value, null);
        }

        public Arguments addConst(String name, Object value, Stamp stamp) {
            assert check(name, true, false);
            values[nextParamIdx] = value;
            constStamps[nextParamIdx] = stamp;
            cacheKey.setParam(nextParamIdx, value);
            nextParamIdx++;
            return this;
        }

        public Arguments addVarargs(String name, Class<?> componentType, Stamp argStamp, Object value) {
            assert check(name, false, true);
            Varargs varargs = new Varargs(componentType, argStamp, value);
            values[nextParamIdx] = varargs;
            // A separate template is necessary for every distinct array length
            cacheKey.setParam(nextParamIdx, varargs.length);
            nextParamIdx++;
            return this;
        }

        public void setCacheable(boolean cacheable) {
            this.cacheable = cacheable;
        }

        private boolean check(String name, boolean constParam, boolean varargsParam) {
            assert nextParamIdx < info.getParameterCount() : "too many parameters: " + name + "  " + this;
            assert info.getParameterName(nextParamIdx) == null || info.getParameterName(nextParamIdx).equals(name) : "wrong parameter name at " + nextParamIdx + " : " + name + "  " + this;
            assert constParam == info.isConstantParameter(nextParamIdx) : "Parameter " + (constParam ? "not " : "") + "annotated with @" + ConstantParameter.class.getSimpleName() + ": " + name +
                            "  " + this;
            assert varargsParam == info.isVarargsParameter(nextParamIdx) : "Parameter " + (varargsParam ? "not " : "") + "annotated with @" + VarargsParameter.class.getSimpleName() + ": " + name +
                            "  " + this;
            return true;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("Parameters<").append(info.method.format("%h.%n")).append(" [");
            String sep = "";
            for (int i = 0; i < info.getParameterCount(); i++) {
                result.append(sep);
                if (info.isConstantParameter(i)) {
                    result.append("const ");
                } else if (info.isVarargsParameter(i)) {
                    result.append("varargs ");
                }
                result.append(info.getParameterName(i)).append(" = ").append(values[i]);
                sep = ", ";
            }
            result.append(">");
            return result.toString();
        }

        @Override
        public void formatTo(Formatter formatter, int flags, int width, int precision) {
            if ((flags & ALTERNATE) == 0) {
                formatter.format(applyFormattingFlagsAndWidth(toString(), flags, width));
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(info.method.getName()).append('(');
                String sep = "";
                for (int i = 0; i < info.getParameterCount(); i++) {
                    if (info.isConstantParameter(i)) {
                        sb.append(sep);
                        if (info.getParameterName(i) != null) {
                            sb.append(info.getParameterName(i));
                        } else {
                            sb.append(i);
                        }
                        sb.append('=').append(values[i]);
                        sep = ", ";
                    }
                }
                sb.append(")");
                String string = sb.toString();
                if (string.indexOf('%') != -1) {
                    // Quote any % signs
                    string = string.replace("%", "%%");
                }
                formatter.format(applyFormattingFlagsAndWidth(string, flags & ~ALTERNATE, width));
            }
        }
    }

    /**
     * Wrapper for the prototype value of a {@linkplain VarargsParameter varargs} parameter.
     */
    static class Varargs {

        protected final Class<?> componentType;
        protected final Stamp stamp;
        protected final Object value;
        protected final int length;

        protected Varargs(Class<?> componentType, Stamp stamp, Object value) {
            this.componentType = componentType;
            this.stamp = stamp;
            this.value = value;
            if (value instanceof List) {
                this.length = ((List<?>) value).size();
            } else {
                this.length = Array.getLength(value);
            }
        }

        @Override
        public String toString() {
            if (value instanceof boolean[]) {
                return Arrays.toString((boolean[]) value);
            }
            if (value instanceof byte[]) {
                return Arrays.toString((byte[]) value);
            }
            if (value instanceof char[]) {
                return Arrays.toString((char[]) value);
            }
            if (value instanceof short[]) {
                return Arrays.toString((short[]) value);
            }
            if (value instanceof int[]) {
                return Arrays.toString((int[]) value);
            }
            if (value instanceof long[]) {
                return Arrays.toString((long[]) value);
            }
            if (value instanceof float[]) {
                return Arrays.toString((float[]) value);
            }
            if (value instanceof double[]) {
                return Arrays.toString((double[]) value);
            }
            if (value instanceof Object[]) {
                return Arrays.toString((Object[]) value);
            }
            return String.valueOf(value);
        }
    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED)
    static final class VarargsPlaceholderNode extends FloatingNode implements ArrayLengthProvider {

        public static final NodeClass<VarargsPlaceholderNode> TYPE = NodeClass.create(VarargsPlaceholderNode.class);
        protected final Varargs varargs;

        protected VarargsPlaceholderNode(Varargs varargs, MetaAccessProvider metaAccess) {
            super(TYPE, StampFactory.objectNonNull(TypeReference.createExactTrusted(metaAccess.lookupJavaType(varargs.componentType).getArrayClass())));
            this.varargs = varargs;
        }

        @Override
        public ValueNode findLength(FindLengthMode mode, ConstantReflectionProvider constantReflection) {
            return ConstantNode.forInt(varargs.length);
        }
    }

    static class CacheKey {

        private final ResolvedJavaMethod method;
        private final Object[] values;
        private final GuardsStage guardsStage;
        private final LoweringTool.LoweringStage loweringStage;
        private int hash;

        protected CacheKey(SnippetInfo info, GuardsStage guardsStage, LoweringTool.LoweringStage loweringStage) {
            this.method = info.method;
            this.guardsStage = guardsStage;
            this.loweringStage = loweringStage;
            this.values = new Object[info.getParameterCount()];
            this.hash = info.method.hashCode() + 31 * guardsStage.ordinal();
        }

        protected void setParam(int paramIdx, Object value) {
            values[paramIdx] = value;
            hash = (hash * 31) ^ (value == null ? 0 : value.hashCode());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof CacheKey)) {
                return false;
            }
            CacheKey other = (CacheKey) obj;
            if (!method.equals(other.method)) {
                return false;
            }
            if (guardsStage != other.guardsStage || loweringStage != other.loweringStage) {
                return false;
            }
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null && !values[i].equals(other.values[i])) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    private static final TimerKey SnippetTemplateCreationTime = DebugContext.timer("SnippetTemplateCreationTime");
    private static final CounterKey SnippetTemplates = DebugContext.counter("SnippetTemplateCount");

    static class Options {
        @Option(help = "Use a LRU cache for snippet templates.")//
        public static final OptionKey<Boolean> UseSnippetTemplateCache = new OptionKey<>(true);

        @Option(help = "")//
        static final OptionKey<Integer> MaxTemplatesPerSnippet = new OptionKey<>(50);
    }

    /**
     * Base class for snippet classes. It provides a cache for {@link SnippetTemplate}s.
     */
    public abstract static class AbstractTemplates implements SnippetTemplateCache {

        protected final OptionValues options;
        protected final SnippetReflectionProvider snippetReflection;
        private final Map<CacheKey, SnippetTemplate> templates;

        private final boolean shouldTrackNodeSourcePosition;

        protected AbstractTemplates(OptionValues options, Providers providers) {
            this.options = options;
            this.snippetReflection = providers.getSnippetReflection();
            this.shouldTrackNodeSourcePosition = providers.getCodeCache() != null && providers.getCodeCache().shouldDebugNonSafepoints();
            if (Options.UseSnippetTemplateCache.getValue(options)) {
                int size = Options.MaxTemplatesPerSnippet.getValue(options);
                this.templates = Collections.synchronizedMap(new LRUCache<>(size, size));
            } else {
                this.templates = null;
            }
        }

        public static ResolvedJavaMethod findMethod(MetaAccessProvider metaAccess, Class<?> declaringClass, String methodName) {
            ResolvedJavaType type = metaAccess.lookupJavaType(declaringClass);
            type.link();
            ResolvedJavaMethod result = null;
            for (ResolvedJavaMethod m : type.getDeclaredMethods(false)) {
                if (m.getName().equals(methodName)) {
                    if (!Assertions.assertionsEnabled()) {
                        return m;
                    } else {
                        assert result == null : "multiple definitions found";
                        result = m;
                    }
                }
            }
            if (result == null) {
                throw new GraalError("Could not find method in " + declaringClass + " named " + methodName);
            }
            return result;
        }

        /**
         * Finds the unique method in {@code declaringClass} named {@code methodName} annotated by
         * {@link Snippet} and returns a {@link SnippetInfo} value describing it. There must be
         * exactly one snippet method in {@code declaringClass} with a given name.
         *
         * The snippet found must have {@link ProfileSource#isTrusted(ProfileSource)} known profiles
         * for all {@link IfNode} in the {@link StructuredGraph}.
         */
        protected SnippetInfo snippet(Providers providers,
                        Class<? extends Snippets> declaringClass,
                        String methodName,
                        LocationIdentity... initialPrivateLocations) {
            return snippet(providers,
                            declaringClass,
                            methodName,
                            null,
                            null,
                            initialPrivateLocations);
        }

        /**
         * See {@link #snippet(Providers, Class, String, LocationIdentity...)} for details.
         */
        protected SnippetInfo snippet(Providers providers,
                        Class<? extends Snippets> declaringClass,
                        String methodName,
                        ResolvedJavaMethod original,
                        Object receiver,
                        LocationIdentity... initialPrivateLocations) {
            assert methodName != null;
            ResolvedJavaMethod javaMethod = findMethod(providers.getMetaAccess(), declaringClass, methodName);
            assert javaMethod != null : "did not find @" + Snippet.class.getSimpleName() + " method in " + declaringClass + " named " + methodName;
            providers.getReplacements().registerSnippet(javaMethod, original, receiver, GraalOptions.TrackNodeSourcePosition.getValue(options), options);
            LocationIdentity[] privateLocations = GraalOptions.SnippetCounters.getValue(options) ? SnippetCounterNode.addSnippetCounters(initialPrivateLocations) : initialPrivateLocations;
            if (IS_IN_NATIVE_IMAGE || GraalOptions.EagerSnippets.getValue(options)) {
                SnippetParameterInfo snippetParameterInfo = providers.getReplacements().getSnippetParameterInfo(javaMethod);
                return new EagerSnippetInfo(javaMethod, original, privateLocations, receiver, snippetParameterInfo);
            } else {
                return new LazySnippetInfo(javaMethod, original, privateLocations, receiver);
            }
        }

        /**
         * Gets a template for a given key, creating it first if necessary.
         */
        @SuppressWarnings("try")
        public SnippetTemplate template(CoreProviders context, ValueNode replacee, final Arguments args) {
            StructuredGraph graph = replacee.graph();
            DebugContext outer = graph.getDebug();
            SnippetTemplate template = Options.UseSnippetTemplateCache.getValue(options) && args.cacheable ? templates.get(args.cacheKey) : null;
            if (template == null || (graph.trackNodeSourcePosition() && !template.snippet.trackNodeSourcePosition())) {
                try (DebugContext debug = context.getReplacements().openSnippetDebugContext("SnippetTemplate_", args.cacheKey.method, outer, options)) {
                    try (DebugCloseable a = SnippetTemplateCreationTime.start(outer);
                                    DebugCloseable a2 = args.info.creationTimer.start(outer);
                                    DebugContext.Scope s = debug.scope("SnippetSpecialization", args.info.method)) {
                        SnippetTemplates.increment(outer);
                        args.info.creationCounter.increment(outer);
                        OptionValues snippetOptions = new OptionValues(options, GraalOptions.TraceInlining, GraalOptions.TraceInliningForStubsAndSnippets.getValue(options),
                                        DebugOptions.OptimizationLog, null);
                        template = new SnippetTemplate(snippetOptions,
                                        debug,
                                        context,
                                        snippetReflection,
                                        args,
                                        graph.trackNodeSourcePosition() || shouldTrackNodeSourcePosition,
                                        replacee,
                                        createMidTierPreLoweringPhases(),
                                        createMidTierPostLoweringPhases());
                        if (Options.UseSnippetTemplateCache.getValue(snippetOptions) && args.cacheable) {
                            templates.put(args.cacheKey, template);
                        }
                        if (outer.areMetricsEnabled()) {
                            DebugContext.counter("SnippetTemplateNodeCount[%#s]", args).add(outer, template.nodes.size());
                        }
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }
                }
            }
            assert checkTemplate(context.getMetaAccess(), args, template.snippet.method());
            return template;
        }

        /**
         * Additional mid-tier optimization phases to run on the snippet graph during
         * {@link #template} creation. These phases are run before mid-tier lowering, only for
         * snippets lowered in the mid-tier or low-tier lowering.
         */
        protected PhaseSuite<CoreProviders> createMidTierPreLoweringPhases() {
            return null;
        }

        /**
         * Additional mid-tier optimization phases to run on the snippet graph during
         * {@link #template} creation. These phases are run after mid-tier lowering, only for
         * snippets lowered in the low-tier lowering.
         */
        protected PhaseSuite<CoreProviders> createMidTierPostLoweringPhases() {
            return null;
        }
    }

    private static final class LRUCache<K, V> extends LinkedHashMap<K, V> {
        private static final long serialVersionUID = 1L;
        private final int maxCacheSize;

        LRUCache(int initialCapacity, int maxCacheSize) {
            super(initialCapacity, 0.75F, true);
            this.maxCacheSize = maxCacheSize;
        }

        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<K, V> eldest) {
            return size() > maxCacheSize;
        }
    }

    // These values must be compared with equals() not '==' to support replay compilation.
    private static final Object UNUSED_PARAMETER = "UNUSED_PARAMETER";
    private static final Object CONSTANT_PARAMETER = "CONSTANT_PARAMETER";

    private final SnippetReflectionProvider snippetReflection;

    /**
     * Creates a snippet template.
     */
    @SuppressWarnings("try")
    protected SnippetTemplate(OptionValues options,
                    DebugContext debug,
                    CoreProviders providers,
                    SnippetReflectionProvider snippetReflection,
                    Arguments args,
                    boolean trackNodeSourcePosition,
                    Node replacee,
                    PhaseSuite<CoreProviders> midTierPreLoweringPhases,
                    PhaseSuite<CoreProviders> midTierPostLoweringPhases) {
        this.snippetReflection = snippetReflection;
        this.info = args.info;

        Object[] constantArgs = getConstantArgs(args);
        BitSet nonNullParameters = getNonNullParameters(args);
        StructuredGraph snippetGraph = providers.getReplacements().getSnippet(args.info.method,
                        args.info.original,
                        constantArgs,
                        nonNullParameters,
                        trackNodeSourcePosition,
                        replacee.getNodeSourcePosition(),
                        options);
        assert snippetGraph.getAssumptions() == null : snippetGraph;

        ResolvedJavaMethod method = snippetGraph.method();
        Signature signature = method.getSignature();

        // Copy snippet graph, replacing constant parameters with given arguments
        final StructuredGraph snippetCopy = new StructuredGraph.Builder(options, debug).name(snippetGraph.name).method(snippetGraph.method()).trackNodeSourcePosition(
                        snippetGraph.trackNodeSourcePosition()).setIsSubstitution(true).build();
        snippetCopy.getGraphState().setGuardsStage(snippetGraph.getGuardsStage());
        snippetCopy.getGraphState().getStageFlags().addAll(snippetGraph.getGraphState().getStageFlags());
        assert !GraalOptions.TrackNodeSourcePosition.getValue(options) || snippetCopy.trackNodeSourcePosition();
        try (DebugContext.Scope scope = debug.scope("SpecializeSnippet", snippetCopy)) {
            if (!snippetGraph.isUnsafeAccessTrackingEnabled()) {
                snippetCopy.disableUnsafeAccessTracking();
            }
            assert snippetCopy.isSubstitution();
            assert !DumpOnError.getValue(options) || debug.contextLookupTopdown(StructuredGraph.class) == snippetCopy : "DumpOnError should cause the snippet graph to be available for dumping";

            EconomicMap<Node, Node> nodeReplacements = EconomicMap.create(Equivalence.IDENTITY);
            nodeReplacements.put(snippetGraph.start(), snippetCopy.start());

            MetaAccessProvider metaAccess = providers.getMetaAccess();
            assert checkTemplate(metaAccess, args, method);

            int parameterCount = args.info.getParameterCount();
            VarargsPlaceholderNode[] placeholders = new VarargsPlaceholderNode[parameterCount];

            for (int i = 0; i < parameterCount; i++) {
                ParameterNode parameter = snippetGraph.getParameter(i);
                if (parameter != null) {
                    if (args.info.isConstantParameter(i)) {
                        Object arg = args.values[i];
                        JavaKind kind = signature.getParameterKind(i);
                        ConstantNode constantNode;
                        if (arg instanceof Constant) {
                            Stamp stamp = args.constStamps[i];
                            if (stamp == null) {
                                assert arg instanceof JavaConstant : "could not determine type of constant " + arg;
                                constantNode = ConstantNode.forConstant((JavaConstant) arg, metaAccess, snippetCopy);
                            } else {
                                constantNode = ConstantNode.forConstant(stamp, (Constant) arg, metaAccess, snippetCopy);
                            }
                        } else {
                            constantNode = ConstantNode.forConstant(this.snippetReflection.forBoxed(kind, arg), metaAccess, snippetCopy);
                        }
                        nodeReplacements.put(parameter, constantNode);
                    } else if (args.info.isVarargsParameter(i)) {
                        Varargs varargs = (Varargs) args.values[i];
                        VarargsPlaceholderNode placeholder = snippetCopy.unique(new VarargsPlaceholderNode(varargs, providers.getMetaAccess()));
                        nodeReplacements.put(parameter, placeholder);
                        placeholders[i] = placeholder;
                    } else if (args.info.isNonNullParameter(i)) {
                        GraalError.guarantee(StampTool.isPointerNonNull(parameter), "Expected %s to have a non-null stamp, but was %s", parameter, parameter.stamp(NodeView.DEFAULT));
                    }
                }
            }
            try (InliningLog.UpdateScope updateScope = InliningLog.openDefaultUpdateScope(snippetCopy.getInliningLog())) {
                UnmodifiableEconomicMap<Node, Node> duplicates = snippetCopy.addDuplicates(snippetGraph.getNodes(), snippetGraph, snippetGraph.getNodeCount(), nodeReplacements);
                if (updateScope != null) {
                    snippetCopy.getInliningLog().replaceLog(duplicates, snippetGraph.getInliningLog());
                }
            }

            debug.dump(DebugContext.INFO_LEVEL, snippetCopy, "Before specialization");

            // Gather the template parameters
            parameters = new Object[parameterCount];
            for (int i = 0; i < parameterCount; i++) {
                if (args.info.isConstantParameter(i)) {
                    parameters[i] = CONSTANT_PARAMETER;
                } else if (args.info.isVarargsParameter(i)) {
                    assert snippetCopy.getParameter(i) == null;
                    Varargs varargs = (Varargs) args.values[i];
                    int length = varargs.length;
                    ParameterNode[] params = new ParameterNode[length];
                    Stamp stamp = varargs.stamp;
                    for (int j = 0; j < length; j++) {
                        // Use a decimal friendly numbering make it more obvious how values map
                        assert parameterCount < 10000 : Assertions.errorMessage(parameterCount, params);
                        int idx = (i + 1) * 10000 + j;
                        assert idx >= parameterCount : "collision in parameter numbering";
                        ParameterNode local = snippetCopy.addOrUnique(new ParameterNode(idx, StampPair.createSingle(stamp)));
                        params[j] = local;
                    }
                    parameters[i] = params;

                    VarargsPlaceholderNode placeholder = placeholders[i];
                    if (placeholder != null) {
                        for (Node usage : placeholder.usages().snapshot()) {
                            if (usage instanceof LoadIndexedNode) {
                                LoadIndexedNode loadIndexed = (LoadIndexedNode) usage;
                                debug.dump(DebugContext.INFO_LEVEL, snippetCopy, "Before replacing %s", loadIndexed);
                                LoadSnippetVarargParameterNode loadSnippetParameter = snippetCopy.add(
                                                new LoadSnippetVarargParameterNode(params, loadIndexed.index(), loadIndexed.stamp(NodeView.DEFAULT)));
                                snippetCopy.replaceFixedWithFixed(loadIndexed, loadSnippetParameter);
                                debug.dump(DebugContext.INFO_LEVEL, snippetCopy, "After replacing %s", loadIndexed);
                            } else if (usage instanceof StoreIndexedNode) {
                                /*
                                 * The template lowering doesn't really treat this as an array so
                                 * you can't store back into the varargs. Allocate your own array if
                                 * you really need this and EA should eliminate it.
                                 */
                                throw new GraalError("Can't store into VarargsParameter array");
                            }
                        }
                    }
                } else {
                    ParameterNode local = snippetCopy.getParameter(i);
                    if (local == null) {
                        // Parameter value was eliminated
                        parameters[i] = UNUSED_PARAMETER;
                    } else {
                        parameters[i] = local;
                    }
                }
            }

            explodeLoops(snippetCopy, providers);

            List<UnwindNode> unwindNodes = snippetCopy.getNodes(UnwindNode.TYPE).snapshot();
            if (unwindNodes.size() == 0) {
                unwindPath = null;
            } else if (unwindNodes.size() > 1) {
                throw GraalError.shouldNotReachHere("Graph has more than one UnwindNode"); // ExcludeFromJacocoGeneratedReport
            } else {
                unwindPath = unwindNodes.get(0);
            }

            List<FallbackInvokeWithExceptionNode> fallbackInvokes = snippetCopy.getNodes().filter(FallbackInvokeWithExceptionNode.class).snapshot();
            if (fallbackInvokes.size() == 0) {
                fallbackInvoke = null;
            } else if (fallbackInvokes.size() > 1) {
                throw GraalError.shouldNotReachHere("Graph has more than one " + FallbackInvokeWithExceptionNode.class.getSimpleName()); // ExcludeFromJacocoGeneratedReport
            } else {
                fallbackInvoke = fallbackInvokes.get(0);
            }

            CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();

            /*-
             * Mirror the behavior of normal compilations here (without aggressive optimizations
             * but with the most important phases)
             *
             * (1) Run some important high-tier optimizations
             * (2) Perform high-tier lowering
             * (3) If lowering stage is != high tier --> Run important mid tier phases --> guard lowering
             * (4) perform mid-tier lowering
             * (5) If lowering stage is != mid tier --> Run important mid tier phases after lowering --> write barrier addition
             * (6) perform low-tier lowering
             * (7) Run final phases --> dead code elimination
             *
             */
            // (1)
            GuardsStage guardsStage = args.cacheKey.guardsStage;
            boolean needsPEA = false;
            boolean needsCE = false;
            LoweringTool.LoweringStage loweringStage = args.cacheKey.loweringStage;
            for (Node n : snippetCopy.getNodes()) {
                if (n instanceof AbstractNewObjectNode || n instanceof AbstractBoxingNode) {
                    needsPEA = true;
                    break;
                } else if (n instanceof LogicNode) {
                    needsCE = true;
                }
            }
            if (needsPEA) {
                /*
                 * Certain snippets do not have real side-effects if they do allocations, i.e., they
                 * only access the init_location, therefore we might require a late schedule to get
                 * that. However, snippets are small so the compile time cost for this can be
                 * ignored.
                 *
                 * An example of a snippet doing allocation are the boxing snippets since they only
                 * write to newly allocated memory which is not yet visible to the interpreter until
                 * the entire allocation escapes. See LocationIdentity#INIT_LOCATION and
                 * WriteNode#hasSideEffect for details.
                 */
                new PartialEscapePhase(true, true, canonicalizer, null, options, SchedulingStrategy.LATEST).apply(snippetCopy, providers);
            }
            if (needsCE) {
                new IterativeConditionalEliminationPhase(canonicalizer, false).apply(snippetCopy, providers);
            }
            // (2)
            try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate_HIGH_TIER", snippetCopy)) {
                new HighTierLoweringPhase(canonicalizer).apply(snippetCopy, providers);
            } catch (Throwable e) {
                throw debug.handle(e);
            }
            if (loweringStage != LoweringTool.StandardLoweringStage.HIGH_TIER) {
                // (3)
                assert !guardsStage.allowsFloatingGuards() : guardsStage;
                // only create memory map nodes if we need the memory graph
                new FloatingReadPhase(true, canonicalizer).apply(snippetCopy, providers);
                if (!snippetCopy.getGraphState().isExplicitExceptionsNoDeopt()) {
                    new GuardLoweringPhase().apply(snippetCopy, providers);
                }
                assert snippetCopy.getGraphState().isAfterStage(StageFlag.GUARD_LOWERING);
                new RemoveValueProxyPhase(canonicalizer).apply(snippetCopy, providers);
                // (4)
                if (midTierPreLoweringPhases != null) {
                    midTierPreLoweringPhases.apply(snippetCopy, providers);
                }
                try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate_MID_TIER", snippetCopy)) {
                    new MidTierLoweringPhase(canonicalizer).apply(snippetCopy, providers);
                    snippetCopy.getGraphState().setAfterFSA();
                    snippetCopy.getGraphState().forceDisableFrameStateVerification();
                } catch (Throwable e) {
                    throw debug.handle(e);
                }
                if (loweringStage != LoweringTool.StandardLoweringStage.MID_TIER) {
                    // (5)
                    if (midTierPostLoweringPhases != null) {
                        midTierPostLoweringPhases.apply(snippetCopy, providers);
                    }
                    new WriteBarrierAdditionPhase().apply(snippetCopy, providers);
                    try (DebugContext.Scope s = debug.scope("LoweringSnippetTemplate_LOW_TIER", snippetCopy)) {
                        // (6)
                        new LowTierLoweringPhase(canonicalizer).apply(snippetCopy, providers);
                    } catch (Throwable e) {
                        throw debug.handle(e);
                    }
                    // (7)
                    new DeadCodeEliminationPhase(Required).apply(snippetCopy);
                }
            }
            assert checkAllVarargPlaceholdersAreDeleted(parameterCount, placeholders);

            ArrayList<StateSplit> curSideEffectNodes = new ArrayList<>();
            ArrayList<DeoptimizingNode> curDeoptNodes = new ArrayList<>();
            ArrayList<ValueNode> curPlaceholderStampedNodes = new ArrayList<>();

            for (Node node : snippetCopy.getNodes()) {
                if (node instanceof ValueNode) {
                    ValueNode valueNode = (ValueNode) node;
                    if (valueNode.stamp(NodeView.DEFAULT) == PlaceholderStamp.singleton()) {
                        curPlaceholderStampedNodes.add(valueNode);
                    }
                }

                if (node instanceof StateSplit) {
                    StateSplit stateSplit = (StateSplit) node;
                    FrameState frameState = stateSplit.stateAfter();
                    if (stateSplit.hasSideEffect()) {
                        curSideEffectNodes.add((StateSplit) node);
                    }
                    if (frameState != null) {
                        stateSplit.setStateAfter(null);
                    }
                }
                if (node instanceof DeoptimizingNode) {
                    DeoptimizingNode deoptNode = (DeoptimizingNode) node;
                    if (deoptNode.canDeoptimize()) {
                        curDeoptNodes.add(deoptNode);
                    }
                }
            }
            this.snippet = snippetCopy;
            StartNode entryPointNode = snippet.start();
            MemoryAnchorNode anchor = snippetCopy.add(new MemoryAnchorNode(info.privateLocations));
            snippetCopy.start().replaceAtUsages(anchor, InputType.Memory);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, snippetCopy, "After adding memory anchor %s", anchor);
            if (anchor.hasNoUsages()) {
                anchor.safeDelete();
                this.memoryAnchor = null;
            } else {
                /*
                 * Find out if all the return and unwind memory maps point to the anchor (i.e.,
                 * there's no kill anywhere)
                 */
                boolean needsMemoryMaps = false;
                for (MemoryMapControlSinkNode sinkNode : snippet.getNodes(MemoryMapControlSinkNode.TYPE)) {
                    MemoryMapNode memoryMap = sinkNode.getMemoryMap();
                    if (memoryMap.getLocations().size() > 1 || memoryMap.getLastLocationAccess(LocationIdentity.any()) != anchor) {
                        needsMemoryMaps = true;
                        break;
                    }
                }
                boolean needsAnchor;
                if (needsMemoryMaps) {
                    needsAnchor = true;
                } else {
                    // Check that all those memory maps where the only usages of the anchor
                    needsAnchor = anchor.usages().filter(isNotA(MemoryMapNode.class)).isNotEmpty();
                    // Remove the useless memory map on return nodes
                    MemoryMapNode memoryMap = null;
                    for (ReturnNode retNode : snippet.getNodes(ReturnNode.TYPE)) {
                        if (memoryMap == null) {
                            memoryMap = retNode.getMemoryMap();
                        } else {
                            assert memoryMap == retNode.getMemoryMap() : Assertions.errorMessage(memoryMap, retNode);
                        }
                        retNode.setMemoryMap(null);
                    }
                    if (memoryMap != null) {
                        memoryMap.safeDelete();
                    }
                    // Remove the useless memory map on unwind node
                    MemoryMapNode unwindMemoryMap = null;
                    for (UnwindNode unwindNode : snippet.getNodes(UnwindNode.TYPE)) {
                        if (unwindMemoryMap == null) {
                            unwindMemoryMap = unwindNode.getMemoryMap();
                        } else {
                            assert unwindMemoryMap == unwindNode.getMemoryMap() : Assertions.errorMessage(unwindMemoryMap, unwindNode, unwindNode.getMemoryMap());
                        }
                        unwindNode.setMemoryMap(null);
                    }
                    if (unwindMemoryMap != null) {
                        unwindMemoryMap.safeDelete();
                    }
                }
                if (needsAnchor) {
                    snippetCopy.addAfterFixed(snippetCopy.start(), anchor);
                    this.memoryAnchor = anchor;
                } else {
                    anchor.safeDelete();
                    this.memoryAnchor = null;
                }
            }

            debug.dump(DebugContext.INFO_LEVEL, snippet, "SnippetTemplate after fixing memory anchoring");
            List<ReturnNode> returnNodes = snippet.getNodes(ReturnNode.TYPE).snapshot();
            if (returnNodes.isEmpty()) {
                /*
                 * The snippet does not have a return node. That can cause issues for subsequent
                 * lowerings if the replacee gets killed, because killCFG might kill a MergeNode
                 * that is still referenced by the LoweringTool. To solve this, we create an
                 * artificial return node and insert it into a temporary branch right after the
                 * start node. That way, the next node of the replacee will be attached to the
                 * artificial branch and killing the replacee will not affect its successor. The
                 * branch will fold away after snippet instantiation during canonicalization,
                 * together with the original successor.
                 */
                this.returnNode = snippet.add(new ReturnNode(getDefaultReturnValue(snippet, replacee)));
                // insert empty memory map
                MemoryMapImpl mmap = new MemoryMapImpl();
                MemoryMapNode memoryMap = snippet.unique(new MemoryMapNode(mmap.getMap()));
                returnNode.setMemoryMap(memoryMap);
                // this is the condition that controls the lifetime of the branch
                this.artificialReturnCondition = snippet.unique(new PlaceholderLogicNode());
                // insert the temporary branch
                FixedWithNextNode insertAfter = snippet.start();
                FixedNode next = insertAfter.next();
                insertAfter.setNext(null);
                IfNode branch = snippet.add(new IfNode(artificialReturnCondition, next, this.returnNode, ProfileData.BranchProbabilityData.unknown()));
                insertAfter.setNext(branch);
            } else if (returnNodes.size() == 1) {
                this.artificialReturnCondition = null;
                this.returnNode = returnNodes.get(0);
            } else {
                this.artificialReturnCondition = null;
                AbstractMergeNode merge = snippet.add(new MergeNode());
                List<MemoryMapNode> memMaps = new ArrayList<>();
                for (ReturnNode retNode : returnNodes) {
                    MemoryMapNode memoryMapNode = retNode.getMemoryMap();
                    if (memoryMapNode != null) {
                        memMaps.add(memoryMapNode);
                    }
                }
                ValueNode returnValue = InliningUtil.mergeReturns(merge, returnNodes);
                this.returnNode = snippet.add(new ReturnNode(returnValue));
                if (!memMaps.isEmpty()) {
                    MemoryMapImpl mmap = FloatingReadPhase.mergeMemoryMaps(merge, memMaps);
                    MemoryMapNode memoryMap = snippet.unique(new MemoryMapNode(mmap.getMap()));
                    this.returnNode.setMemoryMap(memoryMap);
                    for (MemoryMapNode mm : memMaps) {
                        if (mm != memoryMap && mm.isAlive()) {
                            assert mm.hasNoUsages();
                            GraphUtil.killWithUnusedFloatingInputs(mm);
                        }
                    }
                }
                merge.setNext(this.returnNode);
            }
            debug.dump(DebugContext.INFO_LEVEL, snippet, "After fixing returns");
            canonicalizer.apply(snippet, providers);

            boolean needsMergeStateMap = !guardsStage.areFrameStatesAtDeopts();

            if (needsMergeStateMap) {
                frameStateAssignment = new SnippetFrameStateAssignmentClosure(snippetCopy);
                ReentrantNodeIterator.apply(frameStateAssignment, snippetCopy.start(), SnippetFrameStateAssignment.NodeStateAssignment.BEFORE_BCI);
                GraalError.guarantee(frameStateAssignment.verify(), "snippet frame state verification failed: %s", info);
            } else {
                frameStateAssignment = null;
            }

            assert verifyIntrinsicsProcessed(snippetCopy);

            curDeoptNodes.removeIf(x -> x.asNode().isDeleted());
            curSideEffectNodes.removeIf(x -> x.asNode().isDeleted());
            // ExceptionObjectNodes are handled explicitly
            curSideEffectNodes.removeIf(ExceptionObjectNode.class::isInstance);
            this.sideEffectNodes = curSideEffectNodes;
            this.deoptNodes = curDeoptNodes;
            this.placeholderStampedNodes = curPlaceholderStampedNodes;

            nodes = new ArrayList<>(snippet.getNodeCount());
            for (Node node : snippet.getNodes()) {
                if (node != entryPointNode && node != entryPointNode.stateAfter()) {
                    nodes.add(node);
                }
            }

            debug.dump(DebugContext.INFO_LEVEL, snippet, "SnippetTemplate final state");
            assert snippet.verify();
            this.snippet.freeze();

        } catch (Throwable ex) {
            throw debug.handle(ex);
        }
    }

    /**
     * Gets a default return value that is compatible with {@code replacee}.
     */
    private static ValueNode getDefaultReturnValue(StructuredGraph snippet, Node replacee) {
        if (replacee instanceof ValueNode) {
            JavaKind javaKind = ((ValueNode) replacee).stamp(NodeView.DEFAULT).getStackKind();
            if (javaKind != JavaKind.Void) {
                return ConstantNode.defaultForKind(javaKind, snippet);
            }
        }
        return null;
    }

    private static boolean verifyIntrinsicsProcessed(StructuredGraph snippetCopy) {
        if (IS_IN_NATIVE_IMAGE) {
            return true;
        }
        for (MethodCallTargetNode target : snippetCopy.getNodes(MethodCallTargetNode.TYPE)) {
            ResolvedJavaMethod targetMethod = target.targetMethod();
            if (targetMethod != null) {
                assert targetMethod.getAnnotation(Fold.class) == null && targetMethod.getAnnotation(NodeIntrinsic.class) == null : "plugin should have been processed";
            }
        }
        return true;
    }

    public static void explodeLoops(final StructuredGraph snippetCopy, CoreProviders providers) {
        // Do any required loop explosion
        boolean exploded = false;
        CanonicalizerPhase canonicalizer = CanonicalizerPhase.create();
        do {
            exploded = false;
            ExplodeLoopNode explodeLoop = snippetCopy.getNodes().filter(ExplodeLoopNode.class).first();
            if (explodeLoop != null) { // Earlier canonicalization may have removed the loop
                // altogether
                LoopBeginNode loopBegin = explodeLoop.findLoopBegin();
                if (loopBegin != null) {
                    LoopEx loop = providers.getLoopsDataProvider().getLoopsData(snippetCopy).loop(loopBegin);
                    Mark mark = snippetCopy.getMark();
                    try {
                        LoopTransformations.fullUnroll(loop, providers, canonicalizer);
                    } catch (RetryableBailoutException e) {
                        // This is a hard error in this context
                        throw new GraalError(e, snippetCopy.toString());
                    }
                    canonicalizer.applyIncremental(snippetCopy, providers, mark);
                    loop.deleteUnusedNodes();
                }
                GraphUtil.removeFixedWithUnusedInputs(explodeLoop);
                exploded = true;
            }
        } while (exploded);
    }

    protected static Object[] getConstantArgs(Arguments args) {
        Object[] constantArgs = args.values.clone();
        for (int i = 0; i < args.info.getParameterCount(); i++) {
            if (!args.info.isConstantParameter(i)) {
                constantArgs[i] = null;
            } else {
                assert constantArgs[i] != null : "Can't pass raw null through as argument";
            }
        }
        return constantArgs;
    }

    private static BitSet getNonNullParameters(Arguments args) {
        BitSet nonNullParameters = new BitSet(args.info.getParameterCount());
        for (int i = 0; i < args.info.getParameterCount(); i++) {
            if (args.info.isNonNullParameter(i)) {
                nonNullParameters.set(i);
            }
        }
        return nonNullParameters;
    }

    private static boolean checkAllVarargPlaceholdersAreDeleted(int parameterCount, VarargsPlaceholderNode[] placeholders) {
        for (int i = 0; i < parameterCount; i++) {
            if (placeholders[i] != null) {
                assert placeholders[i].isDeleted() : placeholders[i];
            }
        }
        return true;
    }

    private static boolean checkConstantArgument(MetaAccessProvider metaAccess, final ResolvedJavaMethod method, Signature signature, int paramIndex, String name, Object arg, JavaKind kind) {
        ResolvedJavaType type = signature.getParameterType(paramIndex, method.getDeclaringClass()).resolve(method.getDeclaringClass());
        if (metaAccess.lookupJavaType(WordBase.class).isAssignableFrom(type)) {
            assert arg instanceof Constant || arg instanceof ConstantNode : method + ": word constant parameters must be passed boxed in a Constant value: " + arg;
            return true;
        }
        if (kind != JavaKind.Object) {
            assert arg != null && kind.toBoxedJavaClass() == arg.getClass() : method + ": wrong value kind for " + name + ": expected " + kind + ", got " +
                            (arg == null ? "null" : arg.getClass().getSimpleName());
        }
        return true;
    }

    private static boolean checkVarargs(MetaAccessProvider metaAccess, final ResolvedJavaMethod method, Signature signature, int i, String name, Varargs varargs) {
        ResolvedJavaType type = (ResolvedJavaType) signature.getParameterType(i, method.getDeclaringClass());
        assert type.isArray() : "varargs parameter must be an array type";
        assert type.getComponentType().isAssignableFrom(metaAccess.lookupJavaType(varargs.componentType)) : "componentType for " + name + " not matching " + type.toJavaName() + " instance: " +
                        varargs.componentType;
        return true;
    }

    private static boolean checkNonNull(ResolvedJavaMethod method, String parameterName, Object arg) {
        if (arg instanceof ValueNode) {
            assert StampTool.isPointerNonNull((ValueNode) arg) : method + ": non-null Node for argument " + parameterName + " must have non-null stamp: " + arg;
        } else if (arg instanceof Constant) {
            assert JavaConstant.isNull((Constant) arg) : method + ": non-null Constant for argument " + parameterName + " must not represent null";
        } else {
            assert arg != null : method + ": non-null object for argument " + parameterName + " must not be null";
        }
        return true;
    }

    /**
     * The graph built from the snippet method.
     */
    private final StructuredGraph snippet;

    private final SnippetInfo info;

    /**
     * The named parameters of this template that must be bound to values during instantiation. For
     * a parameter that is still live after specialization, the value in this map is either a
     * {@link ParameterNode} instance or a {@link ParameterNode} array. For an eliminated parameter,
     * the value is identical to the key.
     */
    private final Object[] parameters;

    /**
     * The return node (if any) of the snippet.
     */
    private final ReturnNode returnNode;

    /**
     * The condition that keeps an artificial return node alive or {@code null} if no such return
     * node has been added. During {@link SnippetTemplate#instantiate},
     * {@link PlaceholderLogicNode#markForDeletion()} will be called which cause the branch with the
     * artificial return to fold away.
     */
    private final PlaceholderLogicNode artificialReturnCondition;

    /**
     * The node that will be replaced with the exception handler of the replacee node, or null if
     * the snippet does not have an exception handler path.
     */
    private final UnwindNode unwindPath;

    /**
     * The fallback invoke (if any) of the snippet.
     */
    private final FallbackInvokeWithExceptionNode fallbackInvoke;

    /**
     * The memory anchor (if any) of the snippet.
     */
    private final MemoryAnchorNode memoryAnchor;

    /**
     * Nodes that inherit the {@link StateSplit#stateAfter()} from the replacee during
     * instantiation.
     */
    private final ArrayList<StateSplit> sideEffectNodes;

    /**
     * Nodes that inherit a deoptimization {@link FrameState} from the replacee during
     * instantiation.
     */
    private final ArrayList<DeoptimizingNode> deoptNodes;

    /**
     * Mapping of merge and loop exit nodes to frame state info determining if a merge/loop exit
     * node is required to have a framestate after the lowering, and if so which state
     * (before,after).
     */
    private SnippetFrameStateAssignment.SnippetFrameStateAssignmentClosure frameStateAssignment;

    /**
     * Nodes that have a stamp originating from a {@link Placeholder}.
     */
    private final ArrayList<ValueNode> placeholderStampedNodes;

    /**
     * The nodes to be inlined when this specialization is instantiated.
     */
    private final ArrayList<Node> nodes;

    /**
     * Gets the instantiation-time bindings to this template's parameters.
     *
     * @return the map that will be used to bind arguments to parameters when inlining this template
     */
    private EconomicMap<Node, Node> bind(StructuredGraph replaceeGraph, MetaAccessProvider metaAccess, Arguments args) {
        EconomicMap<Node, Node> replacements = EconomicMap.create(Equivalence.IDENTITY);
        assert args.info.getParameterCount() == parameters.length : "number of args (" + args.info.getParameterCount() + ") != number of parameters (" + parameters.length + ")";
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            assert parameter != null : this + " has no parameter named " + args.info.getParameterName(i);
            Object argument = args.values[i];
            if (parameter instanceof ParameterNode) {
                if (argument instanceof ValueNode) {
                    replacements.put((ParameterNode) parameter, (ValueNode) argument);
                } else {
                    JavaKind kind = ((ParameterNode) parameter).getStackKind();
                    assert argument != null || kind == JavaKind.Object : this + " cannot accept null for non-object parameter named " + args.info.getParameterName(i);
                    JavaConstant constant = forBoxed(argument, kind);
                    replacements.put((ParameterNode) parameter, ConstantNode.forConstant(constant, metaAccess, replaceeGraph));
                }
            } else if (parameter instanceof ParameterNode[]) {
                ParameterNode[] params = (ParameterNode[]) parameter;
                Varargs varargs = (Varargs) argument;
                int length = params.length;
                List<?> list = null;
                Object array = null;
                if (varargs.value instanceof List) {
                    list = (List<?>) varargs.value;
                    assert list.size() == length : length + " != " + list.size();
                } else {
                    array = varargs.value;
                    assert array != null;
                    assert array.getClass().isArray();
                    assert Array.getLength(array) == length : length + " != " + Array.getLength(array);
                }

                for (int j = 0; j < length; j++) {
                    ParameterNode param = params[j];
                    assert param != null;
                    Object value = list != null ? list.get(j) : Array.get(array, j);
                    if (value instanceof ValueNode) {
                        replacements.put(param, (ValueNode) value);
                    } else {
                        JavaConstant constant = forBoxed(value, param.getStackKind());
                        ConstantNode element = ConstantNode.forConstant(constant, metaAccess, replaceeGraph);
                        replacements.put(param, element);
                    }
                }
            } else {
                assert parameter.equals(CONSTANT_PARAMETER) || parameter.equals(UNUSED_PARAMETER) : "unexpected entry for parameter: " + args.info.getParameterName(i) + " -> " + parameter;
            }
        }
        return replacements;
    }

    /**
     * Converts a Java boxed value to a {@link JavaConstant} of the right kind. This adjusts for the
     * limitation that a {@link Local}'s kind is a {@linkplain JavaKind#getStackKind() stack kind}
     * and so cannot be used for re-boxing primitives smaller than an int.
     *
     * @param argument a Java boxed value
     * @param localKind the kind of the {@link Local} to which {@code argument} will be bound
     */
    protected JavaConstant forBoxed(Object argument, JavaKind localKind) {
        assert localKind == localKind.getStackKind() : Assertions.errorMessage(argument, localKind);
        if (localKind == JavaKind.Int) {
            return JavaConstant.forBoxedPrimitive(argument);
        }
        return snippetReflection.forBoxed(localKind, argument);
    }

    /**
     * Logic for replacing a snippet-lowered node at its usages with the return value of the
     * snippet. An alternative to the {@linkplain SnippetTemplate#DEFAULT_REPLACER default}
     * replacement logic can be used to handle mismatches between the stamp of the node being
     * lowered and the stamp of the snippet's return value.
     */
    public interface UsageReplacer {
        /**
         * Replaces all usages of {@code oldNode} with direct or indirect usages of {@code newNode}.
         */
        void replace(ValueNode oldNode, ValueNode newNode);
    }

    /**
     * Represents the default {@link UsageReplacer usage replacer} logic which simply delegates to
     * {@link Node#replaceAtUsages(Node)}.
     */
    public static final UsageReplacer DEFAULT_REPLACER = new UsageReplacer() {

        @Override
        public void replace(ValueNode oldNode, ValueNode newNode) {
            if (newNode == null) {
                assert oldNode.hasNoUsages();
            } else {
                oldNode.replaceAtUsages(newNode);
            }
        }
    };

    private boolean assertSnippetKills(ValueNode replacee) {
        if (replacee.graph().isBeforeStage(StageFlag.FLOATING_READS)) {
            // no floating reads yet, ignore locations created while lowering
            return true;
        }
        if (returnNode == null) {
            // The snippet terminates control flow
            return true;
        }
        MemoryMapNode memoryMap = returnNode.getMemoryMap();
        if (memoryMap == null || memoryMap.isEmpty()) {
            // there are no kills in the snippet graph
            return true;
        }

        EconomicSet<LocationIdentity> kills = EconomicSet.create(Equivalence.DEFAULT);
        kills.addAll(memoryMap.getLocations());

        if (MemoryKill.isSingleMemoryKill(replacee)) {
            // check if some node in snippet graph also kills the same location
            LocationIdentity locationIdentity = ((SingleMemoryKill) replacee).getKilledLocationIdentity();
            if (locationIdentity.isAny()) {
                // if the replacee kills ANY_LOCATION, the snippet can kill arbitrary locations
                return true;
            }
            assert kills.contains(locationIdentity) : replacee + " kills " + locationIdentity + ", but snippet doesn't contain a kill to this location";
            kills.remove(locationIdentity);
        }
        assert !(MemoryKill.isMultiMemoryKill(replacee)) : replacee + " multi not supported (yet)";

        // remove ANY_LOCATION if it's just a kill by the start node
        if (memoryMap.getLastLocationAccess(any()) instanceof MemoryAnchorNode) {
            kills.remove(any());
        }

        // node can only lower to a ANY_LOCATION kill if the replacee also kills ANY_LOCATION
        assert !kills.contains(any()) : "snippet graph contains a kill to ANY_LOCATION, but replacee (" + replacee + ") doesn't kill ANY_LOCATION.  kills: " + kills;

        /*
         * Kills to private locations are safe, since there can be no floating read to these
         * locations except reads that are introduced by the snippet itself or related snippets in
         * the same lowering round. These reads are anchored to a MemoryAnchor at the beginning of
         * their snippet, so they can not float above a kill in another instance of the same
         * snippet.
         */
        for (LocationIdentity p : this.info.privateLocations) {
            kills.remove(p);
        }

        assert kills.isEmpty() : "snippet graph kills non-private locations " + kills + " that replacee (" + replacee + ") doesn't kill";
        return true;
    }

    private static class MemoryInputMap implements MemoryMap {

        private final LocationIdentity locationIdentity;
        private final MemoryKill lastLocationAccess;

        MemoryInputMap(ValueNode replacee) {
            if (replacee instanceof MemoryAccess) {
                MemoryAccess access = (MemoryAccess) replacee;
                locationIdentity = access.getLocationIdentity();
                lastLocationAccess = access.getLastLocationAccess();
            } else {
                locationIdentity = null;
                lastLocationAccess = null;
            }
        }

        @Override
        public MemoryKill getLastLocationAccess(LocationIdentity location) {
            if (locationIdentity != null && locationIdentity.equals(location)) {
                return lastLocationAccess;
            } else {
                return null;
            }
        }

        @Override
        public Collection<LocationIdentity> getLocations() {
            if (locationIdentity == null) {
                return Collections.emptySet();
            } else {
                return Collections.singleton(locationIdentity);
            }
        }
    }

    private class MemoryOutputMap extends MemoryInputMap {

        private final UnmodifiableEconomicMap<Node, Node> duplicates;
        private MemoryMapNode memoryMap;

        MemoryOutputMap(ValueNode replacee, MemoryMapNode memoryMap, UnmodifiableEconomicMap<Node, Node> duplicates) {
            super(replacee);
            this.duplicates = duplicates;
            this.memoryMap = memoryMap;
        }

        @Override
        public MemoryKill getLastLocationAccess(LocationIdentity locationIdentity) {
            MemoryKill lastLocationAccess = memoryMap.getLastLocationAccess(locationIdentity);
            assert lastLocationAccess != null : locationIdentity;
            if (lastLocationAccess == memoryAnchor) {
                return super.getLastLocationAccess(locationIdentity);
            } else {
                return (MemoryKill) duplicates.get(ValueNodeInterface.asNode(lastLocationAccess));
            }
        }

        @Override
        public Collection<LocationIdentity> getLocations() {
            return memoryMap.getLocations();
        }
    }

    private void rewireMemoryGraph(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        verifyWithExceptionNode(replacee);
        if (replacee.graph().isAfterStage(StageFlag.FLOATING_READS)) {
            // rewire outgoing memory edges
            if (returnNode != null) {
                // outgoing memory edges are always attached to the replacee
                replaceMemoryUsages(replacee, new MemoryOutputMap(replacee, returnNode.getMemoryMap(), duplicates));
                ReturnNode ret = (ReturnNode) duplicates.get(returnNode);
                if (ret != null) {
                    MemoryMapNode memoryMap = ret.getMemoryMap();
                    if (memoryMap != null) {
                        ret.setMemoryMap(null);
                        memoryMap.safeDelete();
                    }
                }
            }
            // rewire exceptional memory edges
            if (unwindPath != null) {
                // exceptional memory edges are attached to the exception edge
                replaceMemoryUsages(((WithExceptionNode) replacee).exceptionEdge(), new MemoryOutputMap(replacee, unwindPath.getMemoryMap(), duplicates));
                UnwindNode unwind = (UnwindNode) duplicates.get(unwindPath);
                if (unwind != null) {
                    MemoryMapNode memoryMap = unwind.getMemoryMap();
                    if (memoryMap != null) {
                        unwind.setMemoryMap(null);
                        memoryMap.safeDelete();
                    }
                }
            }
            if (memoryAnchor != null) {
                // rewire incoming memory edges
                MemoryAnchorNode memoryDuplicate = (MemoryAnchorNode) duplicates.get(memoryAnchor);
                replaceMemoryUsages(memoryDuplicate, new MemoryInputMap(replacee));

                if (memoryDuplicate.hasNoUsages()) {
                    if (memoryDuplicate.next() != null) {
                        memoryDuplicate.graph().removeFixed(memoryDuplicate);
                    } else {
                        // this was a dummy memory node used when instantiating pure data-flow
                        // snippets: it was not attached to the control flow.
                        memoryDuplicate.safeDelete();
                    }
                }
            }
        }
    }

    /**
     * Verifies that a {@link WithExceptionNode} has only memory usages via the
     * {@link WithExceptionNode#next()} edge. On the {@link WithExceptionNode#exceptionEdge()} there
     * must be a {@link MemoryKill} (or an {@link UnreachableBeginNode}), otherwise we would not
     * know from which edge a memory usage is coming from.
     */
    private static void verifyWithExceptionNode(ValueNode node) {
        if (node instanceof WithExceptionNode && MemoryKill.isMemoryKill(node)) {
            WithExceptionNode withExceptionNode = (WithExceptionNode) node;
            AbstractBeginNode exceptionEdge = withExceptionNode.exceptionEdge();
            if (exceptionEdge instanceof UnreachableBeginNode) {
                // exception edge is unreachable - we are good
                return;
            }
            GraalError.guarantee(MemoryKill.isMemoryKill(exceptionEdge), "The exception edge of %s is not a memory kill %s", node, exceptionEdge);
            if (MemoryKill.isSingleMemoryKill(exceptionEdge)) {
                SingleMemoryKill exceptionEdgeKill = (SingleMemoryKill) exceptionEdge;
                if (exceptionEdgeKill.getKilledLocationIdentity().isAny()) {
                    // exception edge kills any - we are good
                    return;
                }
                // if the exception edge does not kill any, it must kill the same location
                GraalError.guarantee(MemoryKill.isSingleMemoryKill(withExceptionNode), "Not a single memory kill: %s", withExceptionNode);
                SingleMemoryKill withExceptionKill = (SingleMemoryKill) withExceptionNode;
                GraalError.guarantee(withExceptionKill.getKilledLocationIdentity().equals(exceptionEdgeKill.getKilledLocationIdentity()),
                                "Kill locations do not match: %s (%s) vs %s (%s)", withExceptionKill, withExceptionKill.getKilledLocationIdentity(), exceptionEdgeKill,
                                exceptionEdgeKill.getKilledLocationIdentity());
            } else if (MemoryKill.isMultiMemoryKill(exceptionEdge)) {
                // for multi memory kills the locations must match
                MultiMemoryKill exceptionEdgeKill = (MultiMemoryKill) exceptionEdge;
                GraalError.guarantee(MemoryKill.isMultiMemoryKill(exceptionEdge), "Not a single memory kill: %s", withExceptionNode);
                MultiMemoryKill withExceptionKill = (MultiMemoryKill) withExceptionNode;
                GraalError.guarantee(Arrays.equals(withExceptionKill.getKilledLocationIdentities(), exceptionEdgeKill.getKilledLocationIdentities()),
                                "Kill locations do not match: %s (%s) vs %s (%s)", withExceptionKill, withExceptionKill.getKilledLocationIdentities(), exceptionEdgeKill,
                                exceptionEdgeKill.getKilledLocationIdentities());
            } else {
                GraalError.shouldNotReachHere("Unexpected exception edge: " + exceptionEdge); // ExcludeFromJacocoGeneratedReport
            }
        }
    }

    private static LocationIdentity getLocationIdentity(Node node) {
        if (node instanceof MemoryAccess) {
            return ((MemoryAccess) node).getLocationIdentity();
        } else if (node instanceof MemoryEdgeProxy) {
            return ((MemoryEdgeProxy) node).getLocationIdentity();
        } else if (node instanceof MemoryPhiNode) {
            return ((MemoryPhiNode) node).getLocationIdentity();
        } else {
            return null;
        }
    }

    private void replaceMemoryUsages(ValueNode node, MemoryMap map) {
        for (Node usage : node.usages().snapshot()) {
            if (usage instanceof MemoryMapNode) {
                continue;
            }

            LocationIdentity location = getLocationIdentity(usage);
            if (location != null) {
                for (Position pos : usage.inputPositions()) {
                    if (pos.getInputType() == InputType.Memory && pos.get(usage) == node) {
                        MemoryKill replacement = map.getLastLocationAccess(location);
                        if (replacement == null) {
                            assert mayRemoveLocation || LocationIdentity.any().equals(location) ||
                                            CollectionsUtil.anyMatch(info.privateLocations, Predicate.isEqual(location)) : "Snippet " + info.method.format("%h.%n") +
                                                            " contains access to the non-private location " +
                                                            location + ", but replacee doesn't access this location." + map.getLocations();
                        } else {
                            pos.set(usage, replacement.asNode());
                        }
                    }
                }
            }
        }
    }

    public Node getReturnValue(UnmodifiableEconomicMap<Node, Node> duplicates) {
        if (returnNode.result() != null) {
            return duplicates.get(returnNode.result());
        }
        return null;
    }

    /**
     * Replaces a given fixed node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @return the map of duplicated nodes (original -&gt; duplicate)
     */
    @SuppressWarnings("try")
    public UnmodifiableEconomicMap<Node, Node> instantiate(MetaAccessProvider metaAccess, FixedNode replacee, UsageReplacer replacer, Arguments args) {
        return instantiate(metaAccess, replacee, replacer, args, true);
    }

    /**
     * Replaces a given fixed node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @param killReplacee is true, the replacee node is deleted
     * @return the map of duplicated nodes (original -&gt; duplicate)
     */
    @SuppressWarnings("try")
    public UnmodifiableEconomicMap<Node, Node> instantiate(MetaAccessProvider metaAccess, FixedNode replacee, UsageReplacer replacer, Arguments args, boolean killReplacee) {
        if (!(replacee instanceof ControlSinkNode)) {
            /*
             * For all use cases of this, the replacee is killed sooner ({@code killReplacee ==
             * true}) or later (by the caller of this method). However, we cannot do that if the
             * snippet does not have a return node we because that means we kill the {@code
             * replacee.next()} which might be connected to a merge whose next node has not yet been
             * lowered [GR-33909].
             */
            GraalError.guarantee(this.returnNode != null, "Cannot kill %s because snippet %s does not have a return node", replacee, this);
        }

        DebugContext debug = replacee.getDebug();
        assert assertSnippetKills(replacee);
        try (DebugCloseable a = args.info.instantiationTimer.start(debug);
                        DebugCloseable b = totalInstantiationTimer.start(debug)) {
            args.info.instantiationCounter.increment(debug);
            totalInstantiationCounter.increment(debug);
            // Inline the snippet nodes, replacing parameters with the given args in the process
            final FixedNode replaceeGraphPredecessor = (FixedNode) replacee.predecessor();
            StartNode entryPointNode = snippet.start();
            FixedNode firstCFGNode = entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            EconomicMap<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            replacements.put(entryPointNode, AbstractBeginNode.prevBegin(replacee));
            EconomicMap<Node, Node> duplicates = inlineSnippet(replacee, debug, replaceeGraph, replacements);

            // Re-wire the control flow graph around the replacee
            FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
            replacee.replaceAtPredecessor(firstCFGNodeDuplicate);

            if (replacee.graph().getGuardsStage().areFrameStatesAtSideEffects()) {
                boolean replaceeHasSideEffect = replacee instanceof StateSplit && ((StateSplit) replacee).hasSideEffect();
                boolean replacementHasSideEffect = !sideEffectNodes.isEmpty();

                /*
                 * Following cases are allowed: Either the replacee and replacement don't have
                 * side-effects or the replacee has and the replacement hasn't (lowered to something
                 * without a side-effect which is fine regarding correctness) or both have
                 * side-effects, under which conditions also merges should have states.
                 */

                if (replacementHasSideEffect) {
                    GraalError.guarantee(replaceeHasSideEffect, "Lowering node %s without side-effect to snippet %s with sideeffects=%s", replacee, info, this.sideEffectNodes);
                }
            }

            updateStamps(replacee, duplicates);

            rewireMemoryGraph(replacee, duplicates);

            rewireFrameStates(replacee, duplicates, replaceeGraphPredecessor);

            captureLoopExitStates(replacee, duplicates);

            // Replace all usages of the replacee with the value returned by the snippet
            ValueNode returnValue = null;
            AbstractBeginNode originalWithExceptionNextNode = null;
            if (returnNode != null && !(replacee instanceof ControlSinkNode)) {
                ReturnNode returnDuplicate = (ReturnNode) duplicates.get(returnNode);
                returnValue = returnDuplicate.result();
                if (returnValue == null && replacee.usages().isNotEmpty() && MemoryKill.isMemoryKill(replacee)) {
                    replacer.replace(replacee, null);
                } else {
                    assert returnValue != null || replacee.hasNoUsages();
                    replacer.replace(replacee, returnValue);
                }
                if (returnDuplicate.isAlive()) {
                    FixedNode next = null;
                    if (replacee instanceof FixedWithNextNode) {
                        FixedWithNextNode fwn = (FixedWithNextNode) replacee;
                        next = fwn.next();
                        fwn.setNext(null);
                    } else if (replacee instanceof WithExceptionNode) {
                        WithExceptionNode withExceptionNode = (WithExceptionNode) replacee;
                        next = originalWithExceptionNextNode = withExceptionNode.next();
                        withExceptionNode.setNext(null);
                    }
                    returnDuplicate.replaceAndDelete(next);
                }
            }
            if (unwindPath != null && unwindPath.isAlive()) {
                GraalError.guarantee(replacee.graph().isBeforeStage(StageFlag.FLOATING_READS) || replacee instanceof WithExceptionNode,
                                "Using a snippet with an UnwindNode after floating reads would require support for the memory graph (unless the replacee has an exception edge)");
                GraalError.guarantee(replacee instanceof WithExceptionNode, "Snippet has an UnwindNode, but replacee is not a node with an exception handler");

                // snippet exception handler
                UnwindNode snippetUnwindDuplicate = (UnwindNode) duplicates.get(unwindPath);
                ValueNode snippetExceptionValue = snippetUnwindDuplicate.exception();
                FixedWithNextNode snippetUnwindPath = (FixedWithNextNode) snippetUnwindDuplicate.predecessor();
                GraalError.guarantee(!(snippetExceptionValue instanceof ExceptionObjectNode) || snippetUnwindPath == snippetExceptionValue,
                                "Snippet unwind predecessor must be the exception object %s: %s", snippetUnwindPath, snippetExceptionValue);
                GraalError.guarantee(!(snippetUnwindPath instanceof MergeNode) || snippetExceptionValue instanceof PhiNode,
                                "If the snippet unwind predecessor is a merge node, the exception object must be a phi %s: %s", snippetUnwindPath, snippetExceptionValue);
                // replacee exception handler
                WithExceptionNode replaceeWithExceptionNode = (WithExceptionNode) replacee;
                AbstractBeginNode exceptionEdge = replaceeWithExceptionNode.exceptionEdge();
                if (exceptionEdge instanceof ExceptionObjectNode) {
                    /*
                     * The exception object node is a begin node, i.e., it can be used as an anchor
                     * for other nodes, thus we need to re-route them to a valid anchor, i.e. the
                     * begin node of the unwind block.
                     */
                    GraalError.guarantee(exceptionEdge.usages().filter(x -> x instanceof GuardedNode && ((GuardedNode) x).getGuard() == exceptionEdge).count() == 0,
                                    "Must not have guards attached to exception object node %s", exceptionEdge);
                    replaceeWithExceptionNode.setExceptionEdge(null);
                    // replace the old exception object with the one
                    exceptionEdge.replaceAtUsages(snippetExceptionValue);
                    GraalError.guarantee(originalWithExceptionNextNode != null, "Need to have next node to link placeholder to: %s", replacee);
                    // replace exceptionEdge with snippetUnwindPath
                    replaceExceptionObjectNode(exceptionEdge, snippetUnwindPath);
                    GraphUtil.killCFG(snippetUnwindDuplicate);
                } else {
                    GraalError.guarantee(exceptionEdge instanceof UnreachableBeginNode, "Unexpected exception edge: %s", exceptionEdge);
                    markExceptionsUnreachable(unwindPath.exception(), duplicates);
                }
            } else {
                /*
                 * Since the snippet unwindPath is null or has been deleted, a placeholder
                 * WithExceptionNode needs to be added for any WithExceptionNode replacee. This
                 * placeholder WithExceptionNode temporarily preserves the replacee's original
                 * exception edge and is needed because lowering should not remove edges from the
                 * original CFG.
                 */
                if (replacee instanceof WithExceptionNode) {
                    GraalError.guarantee(originalWithExceptionNextNode != null, "Need to have next node to link placeholder to: %s", replacee);

                    LocationIdentity loc = null;
                    if (MemoryKill.isSingleMemoryKill(replacee)) {
                        loc = ((SingleMemoryKill) replacee).getKilledLocationIdentity();
                    } else if (MemoryKill.isMultiMemoryKill(replacee)) {
                        GraalError.unimplemented("Cannot use placeholder with exception with a multi memory node " + replacee); // ExcludeFromJacocoGeneratedReport
                    }

                    WithExceptionNode newExceptionNode = replacee.graph().add(new PlaceholderWithExceptionNode(loc));

                    /*
                     * First attaching placeholder as predecessor of original WithExceptionNode next
                     * edge.
                     */
                    ((FixedWithNextNode) originalWithExceptionNextNode.predecessor()).setNext(newExceptionNode);
                    newExceptionNode.setNext(originalWithExceptionNextNode);

                    /* Now connecting exception edge. */
                    WithExceptionNode oldExceptionNode = (WithExceptionNode) replacee;
                    AbstractBeginNode exceptionEdge = oldExceptionNode.exceptionEdge();
                    oldExceptionNode.setExceptionEdge(null);
                    newExceptionNode.setExceptionEdge(exceptionEdge);
                }
            }

            if (artificialReturnCondition != null) {
                ((PlaceholderLogicNode) duplicates.get(artificialReturnCondition)).markForDeletion();
            }

            if (fallbackInvoke != null) {
                GraalError.guarantee(replacee instanceof MacroWithExceptionNode, "%s can only be used in snippets replacing %s", FallbackInvokeWithExceptionNode.class.getSimpleName(),
                                MacroWithExceptionNode.class.getSimpleName());
                WithExceptionNode fallbackInvokeNode = (WithExceptionNode) duplicates.get(fallbackInvoke);
                MacroWithExceptionNode macroNode = (MacroWithExceptionNode) replacee;
                // create fallback invoke
                InvokeWithExceptionNode invoke = macroNode.createInvoke(returnValue);
                // replace placeholder
                replaceeGraph.replaceWithExceptionSplit(fallbackInvokeNode, invoke);
                // register the invoke as the replacement for the fallback invoke
                duplicates.put(fallbackInvoke, invoke);
            }

            if (killReplacee) {
                // Remove the replacee from its graph
                GraphUtil.killCFG(replacee);
            }

            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After lowering %s with %s", replacee, this);
            return duplicates;
        }
    }

    /**
     * Searches for {@link WithExceptionNode} reachable from the {@link UnwindNode} and marks them
     * as {@linkplain WithExceptionNode#replaceWithNonThrowing() non-throwing}.
     */
    private static void markExceptionsUnreachable(ValueNode snippetExceptionValue, EconomicMap<Node, Node> duplicates) {
        assert snippetExceptionValue.graph().isSubstitution() : "search should be done in the snippet graph";
        if (snippetExceptionValue instanceof ValuePhiNode) {
            for (ValueNode phiInput : ((PhiNode) snippetExceptionValue).values().snapshot()) {
                markExceptionsUnreachable(phiInput, duplicates);
            }
        } else {
            // snippetExceptionValue is a (lowered) ExceptionObjectNode
            Node snippetUnwindPred = snippetExceptionValue.predecessor();
            // the exception node might have been lowered to a memory anchor and a begin node
            while (snippetUnwindPred instanceof AbstractBeginNode || snippetUnwindPred instanceof MemoryAnchorNode) {
                snippetUnwindPred = snippetUnwindPred.predecessor();
            }
            GraalError.guarantee(snippetUnwindPred instanceof WithExceptionNode, "Unexpected exception producer: %s", snippetUnwindPred);
            WithExceptionNode snippetWithException = (WithExceptionNode) duplicates.get(snippetUnwindPred);
            snippetWithException.replaceWithNonThrowing();
        }
    }

    /**
     * Replaces the original {@link ExceptionObjectNode} with the exception handling path from the
     * snippet. Ideally, this should simply be the following:
     *
     * <pre>
     * graph.replaceFixedWithFixed(originalExceptionEdge, replacementUnwindPath);
     * </pre>
     *
     * Unfortunately, removing control flow paths during lowering might confuse the lowering phase.
     * So until this is fixed (GR-34538), instead of deleting the original
     * {@link ExceptionObjectNode}, we keep it and insert the new exception handling path just
     * before it. {@link ExceptionObjectNode#lower} will notice this situation later and delete the
     * node.
     *
     * @param originalExceptionEdge the {@link ExceptionObjectNode} that should be deleted
     * @param replacementUnwindPath the replacement for the original {@link ExceptionObjectNode}
     *
     * @see ExceptionObjectNode#lower
     */
    public static void replaceExceptionObjectNode(AbstractBeginNode originalExceptionEdge, FixedWithNextNode replacementUnwindPath) {
        replacementUnwindPath.setNext(originalExceptionEdge);
    }

    private EconomicMap<Node, Node> inlineSnippet(Node replacee, DebugContext debug, StructuredGraph replaceeGraph, EconomicMap<Node, Node> replacements) {
        Mark mark = replaceeGraph.getMark();
        InliningLog log = replaceeGraph.getInliningLog();
        try (InliningLog.UpdateScope scope = log == null ? null : log.openUpdateScope((oldNode, newNode) -> {
            if (oldNode == null) {
                log.trackNewCallsite(newNode);
            }
        })) {
            EconomicMap<Node, Node> duplicates = replaceeGraph.addDuplicates(nodes, snippet, snippet.getNodeCount(), replacements);
            if (scope != null) {
                log.addLog(duplicates, snippet.getInliningLog());
            }
            if (replaceeGraph.trackNodeSourcePosition()) {
                NodeSourcePosition position = replacee.getNodeSourcePosition();
                InliningUtil.updateSourcePosition(replaceeGraph, duplicates, mark, position, true);
            }
            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After inlining snippet %s", snippet.method());
            return duplicates;
        }
    }

    private void propagateStamp(Node node) {
        if (node instanceof PhiNode) {
            PhiNode phi = (PhiNode) node;
            if (phi.inferStamp()) {
                for (Node usage : node.usages()) {
                    propagateStamp(usage);
                }
            }
        }
    }

    private void updateStamps(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        for (ValueNode node : placeholderStampedNodes) {
            ValueNode dup = (ValueNode) duplicates.get(node);
            Stamp replaceeStamp = replacee.stamp(NodeView.DEFAULT);
            if (node instanceof Placeholder) {
                Placeholder placeholderDup = (Placeholder) dup;
                placeholderDup.makeReplacement(replaceeStamp);
            } else {
                dup.setStamp(replaceeStamp);
            }
        }
        for (ParameterNode paramNode : snippet.getNodes(ParameterNode.TYPE)) {
            for (Node usage : paramNode.usages()) {
                Node usageDup = duplicates.get(usage);
                propagateStamp(usageDup);
            }
        }
    }

    /**
     * Gets a copy of the specialized graph.
     */
    public StructuredGraph copySpecializedGraph(DebugContext debugForCopy) {
        return (StructuredGraph) snippet.copy(debugForCopy);
    }

    /**
     * Replaces a given floating node with this specialized snippet.
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param tool lowering tool used to insert the snippet into the control-flow
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     * @return the return node of the inlined snippet
     */
    @SuppressWarnings("try")
    public ValueNode instantiate(MetaAccessProvider metaAccess, FloatingNode replacee, UsageReplacer replacer, LoweringTool tool, Arguments args) {
        DebugContext debug = replacee.getDebug();
        assert assertSnippetKills(replacee);
        try (DebugCloseable a = args.info.instantiationTimer.start(debug);
                        DebugCloseable b = totalInstantiationTimer.start(debug)) {
            args.info.instantiationCounter.increment(debug);
            totalInstantiationCounter.increment(debug);

            // Inline the snippet nodes, replacing parameters with the given args in the process
            StartNode entryPointNode = snippet.start();
            FixedNode firstCFGNode = entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            EconomicMap<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            replacements.put(entryPointNode, tool.getCurrentGuardAnchor().asNode());
            UnmodifiableEconomicMap<Node, Node> duplicates = inlineSnippet(replacee, debug, replaceeGraph, replacements);

            FixedWithNextNode lastFixedNode = tool.lastFixedNode();
            assert lastFixedNode != null && lastFixedNode.isAlive() : replaceeGraph + " lastFixed=" + lastFixedNode;
            FixedNode next = lastFixedNode.next();
            lastFixedNode.setNext(null);
            FixedNode firstCFGNodeDuplicate = (FixedNode) duplicates.get(firstCFGNode);
            replaceeGraph.addAfterFixed(lastFixedNode, firstCFGNodeDuplicate);

            /*
             * floating nodes that are not state-splits do not need to re-wire frame states, however
             * snippets might contain merges for which we want proper frame states
             */
            assert !(replacee instanceof StateSplit) : Assertions.errorMessageContext("replacee", replacee);
            updateStamps(replacee, duplicates);

            rewireMemoryGraph(replacee, duplicates);

            rewireFrameStates(replacee, duplicates, lastFixedNode);

            // Replace all usages of the replacee with the value returned by the snippet
            ReturnNode returnDuplicate = (ReturnNode) duplicates.get(returnNode);
            ValueNode returnValue = returnDuplicate.result();
            assert returnValue != null || replacee.hasNoUsages();
            replacer.replace(replacee, returnValue);

            if (returnDuplicate.isAlive()) {
                returnDuplicate.replaceAndDelete(next);
            }

            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After lowering %s with %s", replacee, this);

            return returnValue;
        }
    }

    /**
     * Replaces a given floating node with this specialized snippet.
     *
     * This snippet must be pure data-flow
     *
     * @param metaAccess
     * @param replacee the node that will be replaced
     * @param replacer object that replaces the usages of {@code replacee}
     * @param args the arguments to be bound to the flattened positional parameters of the snippet
     */
    @SuppressWarnings("try")
    public void instantiate(MetaAccessProvider metaAccess, FloatingNode replacee, UsageReplacer replacer, Arguments args) {
        DebugContext debug = replacee.getDebug();
        assert assertSnippetKills(replacee);
        try (DebugCloseable a = args.info.instantiationTimer.start(debug);
                        DebugCloseable b = totalInstantiationTimer.start(debug)) {
            args.info.instantiationCounter.increment(debug);
            totalInstantiationCounter.increment(debug);

            // Inline the snippet nodes, replacing parameters with the given args in the process
            StartNode entryPointNode = snippet.start();
            assert entryPointNode.next() == (memoryAnchor == null ? returnNode : memoryAnchor) : entryPointNode.next();
            StructuredGraph replaceeGraph = replacee.graph();
            EconomicMap<Node, Node> replacements = bind(replaceeGraph, metaAccess, args);
            MemoryAnchorNode anchorDuplicate = null;
            if (memoryAnchor != null) {
                anchorDuplicate = replaceeGraph.add(new MemoryAnchorNode(info.privateLocations));
                replacements.put(memoryAnchor, anchorDuplicate);
            }
            List<Node> floatingNodes = new ArrayList<>(nodes.size() - 2);
            for (Node n : nodes) {
                if (n != entryPointNode && n != returnNode) {
                    floatingNodes.add(n);
                }
            }
            UnmodifiableEconomicMap<Node, Node> duplicates = inlineSnippet(replacee, debug, replaceeGraph, replacements);

            // floating nodes are not state-splits not need to re-wire frame states
            assert !(replacee instanceof StateSplit) : Assertions.errorMessageContext("replacee", replacee);
            updateStamps(replacee, duplicates);

            rewireMemoryGraph(replacee, duplicates);
            assert anchorDuplicate == null || anchorDuplicate.isDeleted();

            // Replace all usages of the replacee with the value returned by the snippet
            ValueNode returnValue = (ValueNode) duplicates.get(returnNode.result());
            replacer.replace(replacee, returnValue);

            debug.dump(DebugContext.DETAILED_LEVEL, replaceeGraph, "After lowering %s with %s", replacee, this);
        }
    }

    protected void rewireFrameStates(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FixedNode replaceeGraphCFGPredecessor) {
        if (replacee.graph().getGuardsStage().areFrameStatesAtSideEffects() && requiresFrameStateProcessingBeforeFSA(replacee)) {
            rewireFrameStatesBeforeFSA(replacee, duplicates, replaceeGraphCFGPredecessor);
        } else if (replacee.graph().getGuardsStage().areFrameStatesAtDeopts()) {
            if (replacee instanceof DeoptimizingNode && ((DeoptimizingNode) replacee).canDeoptimize()) {
                rewireFrameStatesAfterFSA(replacee, duplicates);
            } else {
                /*
                 * Guarantee no frame states need to be attached to DeoptimizingNodes, since it is
                 * not possible to assign them a valid state.
                 */
                for (DeoptimizingNode deoptNode : deoptNodes) {
                    DeoptimizingNode deoptDup = (DeoptimizingNode) duplicates.get(deoptNode.asNode());
                    GraalError.guarantee(!deoptDup.canDeoptimize(), "No FrameState is being transferred to DeoptimizingNode.");
                }
            }
        }
    }

    private boolean requiresFrameStateProcessingBeforeFSA(ValueNode replacee) {
        return replacee instanceof StateSplit || frameStateAssignment != null;
    }

    private void rewireFrameStatesBeforeFSA(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FixedNode replaceeGraphCFGPredecessor) {
        if (frameStateAssignment != null) {
            assignNecessaryFrameStates(replacee, duplicates, replaceeGraphCFGPredecessor);
        }
    }

    private void assignNecessaryFrameStates(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FixedNode replaceeGraphCFGPredecessor) {
        FrameState stateAfter = null;
        if (replacee instanceof StateSplit && ((StateSplit) replacee).hasSideEffect()) {
            stateAfter = ((StateSplit) replacee).stateAfter();
            GraalError.guarantee(stateAfter != null, "Statesplit with side-effect %s needs a framestate", replacee);
        } else {
            /*
             * We don't have a state split as a replacee, thus we take the prev state as the state
             * after for the node in the snippet.
             */
            stateAfter = GraphUtil.findLastFrameState(replaceeGraphCFGPredecessor);
        }
        final ExceptionObjectNode exceptionObject;
        if (replacee instanceof WithExceptionNode) {
            WithExceptionNode withExceptionNode = (WithExceptionNode) replacee;
            if (withExceptionNode.exceptionEdge() instanceof ExceptionObjectNode) {
                exceptionObject = (ExceptionObjectNode) withExceptionNode.exceptionEdge();
            } else {
                GraalError.guarantee(withExceptionNode.exceptionEdge() instanceof UnreachableBeginNode, "Unexpected exception edge %s", withExceptionNode.exceptionEdge());
                exceptionObject = null;
            }
        } else {
            exceptionObject = null;
        }
        NodeMap<NodeStateAssignment> assignedStateMappings = frameStateAssignment.getStateMapping();
        FrameState stateAfterInvalidForDeoptimization = stateAfter.isValidForDeoptimization() ? null : stateAfter;
        MapCursor<Node, NodeStateAssignment> stateAssignments = assignedStateMappings.getEntries();
        while (stateAssignments.advance()) {
            Node nodeRequiringState = stateAssignments.getKey();

            if (nodeRequiringState instanceof DeoptBciSupplier) {
                if (replacee instanceof DeoptBciSupplier) {
                    ((DeoptBciSupplier) duplicates.get(nodeRequiringState)).setBci(((DeoptBciSupplier) replacee).bci());
                }
            }

            NodeStateAssignment assignment = stateAssignments.getValue();
            switch (assignment) {
                case AFTER_BCI:
                    setReplaceeGraphStateAfter(nodeRequiringState, replacee, duplicates, stateAfter);
                    break;
                case AFTER_BCI_INVALID_FOR_DEOPTIMIZATION:
                    if (stateAfterInvalidForDeoptimization == null) {
                        stateAfterInvalidForDeoptimization = stateAfter.duplicate();
                        stateAfterInvalidForDeoptimization.invalidateForDeoptimization();
                    }
                    setReplaceeGraphStateAfter(nodeRequiringState, replacee, duplicates, stateAfterInvalidForDeoptimization);
                    break;
                case AFTER_EXCEPTION_BCI:
                    if (nodeRequiringState instanceof ExceptionObjectNode) {
                        ExceptionObjectNode newExceptionObject = (ExceptionObjectNode) duplicates.get(nodeRequiringState);
                        rewireExceptionFrameState(exceptionObject, newExceptionObject, newExceptionObject);
                    } else if (nodeRequiringState instanceof MergeNode) {
                        MergeNode mergeNode = (MergeNode) duplicates.get(nodeRequiringState);
                        rewireExceptionFrameState(exceptionObject, getExceptionValueFromMerge(mergeNode), mergeNode);
                    } else {
                        GraalError.shouldNotReachHere("Unexpected exception state node: " + nodeRequiringState); // ExcludeFromJacocoGeneratedReport
                    }
                    break;
                case BEFORE_BCI:
                    FrameState stateBeforeSnippet = GraphUtil.findLastFrameState(replaceeGraphCFGPredecessor);
                    ((StateSplit) duplicates.get(nodeRequiringState)).setStateAfter(stateBeforeSnippet.duplicate());
                    break;
                case INVALID:
                    /*
                     * We cannot assign a proper frame state for this snippet's node since there are
                     * effects which cannot be represented by a single state at the node
                     */
                    throw GraalError.shouldNotReachHere("Invalid snippet replacing a node before frame state assignment with node " + nodeRequiringState + " for replacee " + replacee); // ExcludeFromJacocoGeneratedReport
                default:
                    throw GraalError.shouldNotReachHere("Unknown StateAssigment:" + assignment); // ExcludeFromJacocoGeneratedReport
            }
            replacee.graph().getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, replacee.graph(), "After duplicating after state for node %s in snippet", duplicates.get(nodeRequiringState));
        }
    }

    /**
     * Before frame state assignment, if the inlined snippet contains a loop with side effects,
     * place a {@link CaptureStateBeginNode} after every loop exit. This ensures that the correct
     * loop exit states will be available for later frame state assignment.
     * <p/>
     *
     * In general, a loop with a side effect inlined from a snippet will look like this, after
     * inserting this node:
     *
     * <pre>
     *                                  State(AFTER_BCI)
     *                                  /
     *  +--------------------> LoopBegin
     *  |                         |
     *  |                         If
     *  |                        /  \
     *  |  State(INVALID)       /    \      State(AFTER_BCI)
     *  |                 \    /      \      / |
     *  |             SideEffect     LoopExit  |
     *  |                   |          |       |
     *  +--------------- LoopEnd   CaptureStateBegin
     *                                 |
     *                                ...
     * </pre>
     *
     * Even if the loop exit disappears (for example, through full unrolling), the
     * {@code CaptureStateBegin} will have a valid frame state that can be propagated to its
     * successors. Without the captured state, a fully unrolled version of the loop would only
     * contain the side effect's invalid frame state, and frame state assignment would propagate
     * this.
     * <p/>
     *
     * If the code following the {@code CaptureStateBegin} contains an {@link AbstractBeginNode}
     * with floating guards hanging off it:
     *
     * <pre>
     *     LoopExit
     *        |
     *   CaptureStateBegin
     *        |
     *       ...
     *   AbstractBegin
     *        |     \
     *       ...    Guard
     * </pre>
     *
     * and that {@code AbstractBegin} is optimized out, its
     * {@link AbstractBeginNode#prevBegin(FixedNode)} will be the {@code CaptureStateBegin}, so the
     * guard will be floated there. The guard will not float upwards to the loop exit, where again
     * it would eventually get an invalid frame state if the loop is fully unrolled.
     */
    private static void captureLoopExitStates(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        StructuredGraph replaceeGraph = replacee.graph();
        if (replaceeGraph.getGuardsStage().areFrameStatesAtDeopts() && !replaceeGraph.getGuardsStage().allowsFloatingGuards()) {
            return;
        }
        for (Node duplicate : duplicates.getValues()) {
            if (!(duplicate instanceof LoopExitNode)) {
                continue;
            }
            LoopExitNode loopExit = (LoopExitNode) duplicate;
            if (loopExit.stateAfter() != null && !(loopExit.next() instanceof StateSplit && ((StateSplit) loopExit.next()).stateAfter() != null)) {
                CaptureStateBeginNode captureState = replaceeGraph.add(new CaptureStateBeginNode());
                captureState.setStateAfter(loopExit.stateAfter());
                replaceeGraph.addAfterFixed(loopExit, captureState);
                replaceeGraph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, replaceeGraph, "After capturing state %s at %s after %s", loopExit.stateAfter(), captureState, loopExit);
            }
        }
    }

    private static ValuePhiNode getExceptionValueFromMerge(MergeNode mergeNode) {
        Iterator<ValuePhiNode> phis = mergeNode.valuePhis().iterator();
        GraalError.guarantee(phis.hasNext(), "No phi at merge %s", mergeNode);
        ValuePhiNode phi = phis.next();
        GraalError.guarantee(!phis.hasNext(), "More than one phi at merge %s", mergeNode);
        return phi;
    }

    private void setReplaceeGraphStateAfter(Node nodeRequiringState, Node replacee, UnmodifiableEconomicMap<Node, Node> duplicates, FrameState stateAfter) {
        FrameState newState = stateAfter.duplicate();
        if (stateAfter.values().contains(replacee)) {
            ValueNode valueInReplacement = (ValueNode) duplicates.get(returnNode.result());
            if (!(nodeRequiringState instanceof AbstractMergeNode || nodeRequiringState instanceof LoopExitNode)) {
                // merges and loop exit cannot have "this node" on stack
                if (valueInReplacement instanceof ValuePhiNode) {
                    ValuePhiNode valuePhi = (ValuePhiNode) valueInReplacement;
                    FixedNode next = (FixedNode) nodeRequiringState;
                    while (next instanceof FixedWithNextNode) {
                        next = ((FixedWithNextNode) next).next();
                    }
                    if (next instanceof EndNode) {
                        EndNode duplicateEnd = (EndNode) duplicates.get(next);
                        int endIndex = valuePhi.merge().forwardEndIndex(duplicateEnd);
                        if (endIndex != -1) {
                            valueInReplacement = valuePhi.valueAt(endIndex);
                        }
                    }
                }
            }
            propagateValInState(newState, replacee, valueInReplacement);
        }
        ((StateSplit) duplicates.get(nodeRequiringState)).setStateAfter(newState);
    }

    private static void propagateValInState(FrameState newState, Node replacee, Node replacement) {
        newState.applyToNonVirtual(new NodePositionClosure<>() {
            @Override
            public void apply(Node from, Position p) {
                if (p.get(from) == replacee) {
                    p.set(from, replacement);
                }
            }
        });
    }

    private static void rewireExceptionFrameState(ExceptionObjectNode exceptionObject, ValueNode newExceptionObject, StateSplit newStateSplit) {
        if (exceptionObject == null) {
            /*
             * The exception edge is dead in the replacee graph. Thus, we will not use the exception
             * path from the snippet graph and therefore no need for a frame state.
             */
            return;
        }
        FrameState exceptionState = exceptionObject.stateAfter();
        assert exceptionState.values().contains(exceptionObject);
        assert exceptionState.rethrowException();
        assert exceptionState.stackSize() == 1 : Assertions.errorMessage(exceptionObject, newExceptionObject, newStateSplit, exceptionState);
        FrameState newExceptionState = exceptionState.duplicate();
        newExceptionState.applyToNonVirtual(new NodePositionClosure<>() {
            @Override
            public void apply(Node from, Position p) {
                if (p.get(from) == exceptionObject) {
                    p.set(from, newExceptionObject);
                }
            }
        });
        newStateSplit.setStateAfter(newExceptionState);
    }

    private void rewireFrameStatesAfterFSA(ValueNode replacee, UnmodifiableEconomicMap<Node, Node> duplicates) {
        DeoptimizingNode replaceeDeopt = (DeoptimizingNode) replacee;
        GraalError.guarantee(replaceeDeopt.canDeoptimize(), "Method expects the replacee to have deopt state");
        FrameState stateBefore = null;
        FrameState stateDuring = null;
        FrameState stateAfter = null;
        if (replaceeDeopt instanceof DeoptimizingNode.DeoptBefore) {
            stateBefore = ((DeoptimizingNode.DeoptBefore) replaceeDeopt).stateBefore();
        }
        if (replaceeDeopt instanceof DeoptimizingNode.DeoptDuring) {
            stateDuring = ((DeoptimizingNode.DeoptDuring) replaceeDeopt).stateDuring();
        }
        if (replaceeDeopt instanceof DeoptimizingNode.DeoptAfter) {
            stateAfter = ((DeoptimizingNode.DeoptAfter) replaceeDeopt).stateAfter();
        }

        if (stateAfter == null && stateDuring == null && stateBefore == null) {
            /*
             * There should only be no state available to transfer during testing or if the
             * replacee's graph itself is a substitution.
             */
            StructuredGraph graph = replacee.graph();
            boolean condition = graph.getGraphState().isFrameStateVerificationDisabled() || graph.isSubstitution();
            GraalError.guarantee(condition, "No state available to transfer");
            return;
        }
        final ExceptionObjectNode exceptionObject;
        if (replacee instanceof WithExceptionNode) {
            WithExceptionNode withExceptionNode = (WithExceptionNode) replacee;
            if (withExceptionNode.exceptionEdge() instanceof ExceptionObjectNode) {
                exceptionObject = (ExceptionObjectNode) withExceptionNode.exceptionEdge();
            } else {
                GraalError.guarantee(withExceptionNode.exceptionEdge() instanceof UnreachableBeginNode, "Unexpected exception edge %s", withExceptionNode.exceptionEdge());
                exceptionObject = null;
            }
        } else {
            exceptionObject = null;
        }

        for (DeoptimizingNode deoptNode : deoptNodes) {
            DeoptimizingNode deoptDup = (DeoptimizingNode) duplicates.get(deoptNode.asNode());
            if (deoptDup.canDeoptimize()) {
                if (deoptDup instanceof ExceptionObjectNode) {
                    ExceptionObjectNode newExceptionObject = (ExceptionObjectNode) deoptDup;
                    rewireExceptionFrameState(exceptionObject, newExceptionObject, newExceptionObject);
                    continue;
                }
                if (deoptDup instanceof DeoptimizingNode.DeoptBefore) {
                    GraalError.guarantee(stateBefore != null, "Invalid stateBefore being transferred.");
                    ((DeoptimizingNode.DeoptBefore) deoptDup).setStateBefore(stateBefore);
                }
                if (deoptDup instanceof DeoptimizingNode.DeoptDuring) {
                    // compute a state "during" for a DeoptDuring inside the snippet depending
                    // on what kind of states we had on the node we are replacing.
                    // If the original node had a state "during" already, we just use that,
                    // otherwise we need to find a strategy to compute a state during based on
                    // some other state (before or after).
                    DeoptimizingNode.DeoptDuring deoptDupDuring = (DeoptimizingNode.DeoptDuring) deoptDup;
                    if (stateDuring != null) {
                        deoptDupDuring.setStateDuring(stateDuring);
                    } else if (stateAfter != null) {
                        deoptDupDuring.computeStateDuring(stateAfter);
                    } else if (stateBefore != null) {
                        boolean guarantee = ((DeoptBefore) replaceeDeopt).canUseAsStateDuring() || !deoptDupDuring.hasSideEffect();
                        GraalError.guarantee(guarantee, "Can't use stateBefore as stateDuring for state split %s",
                                        deoptDupDuring);
                        deoptDupDuring.setStateDuring(stateBefore);
                    } else {
                        throw GraalError.shouldNotReachHere("No stateDuring assigned."); // ExcludeFromJacocoGeneratedReport
                    }
                }
                if (deoptDup instanceof DeoptimizingNode.DeoptAfter) {
                    DeoptimizingNode.DeoptAfter deoptDupAfter = (DeoptimizingNode.DeoptAfter) deoptDup;
                    if (stateAfter != null) {
                        deoptDupAfter.setStateAfter(stateAfter);
                    } else {
                        boolean guarantee = stateBefore != null && !deoptDupAfter.hasSideEffect();
                        GraalError.guarantee(guarantee, "Can't use stateBefore as stateAfter for state split %s", deoptDupAfter);
                        deoptDupAfter.setStateAfter(stateBefore);
                    }

                }
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder(snippet.toString()).append('(');
        String sep = "";
        for (int i = 0; i < parameters.length; i++) {
            String name = "[" + i + "]";
            Object value = parameters[i];
            buf.append(sep);
            sep = ", ";
            if (value == null) {
                buf.append("<null> ").append(name);
            } else if (value.equals(UNUSED_PARAMETER)) {
                buf.append("<unused> ").append(name);
            } else if (value.equals(CONSTANT_PARAMETER)) {
                buf.append("<constant> ").append(name);
            } else if (value instanceof ParameterNode) {
                ParameterNode param = (ParameterNode) value;
                buf.append(param.getStackKind().getJavaName()).append(' ').append(name);
            } else {
                ParameterNode[] params = (ParameterNode[]) value;
                String kind = params.length == 0 ? "?" : params[0].getStackKind().getJavaName();
                buf.append(kind).append('[').append(params.length).append("] ").append(name);
            }
        }
        return buf.append(')').toString();
    }

    private static boolean checkTemplate(MetaAccessProvider metaAccess, Arguments args, ResolvedJavaMethod method) {
        Signature signature = method.getSignature();
        int offset = args.info.hasReceiver() ? 1 : 0;
        for (int i = offset; i < args.info.getParameterCount(); i++) {
            if (args.info.isConstantParameter(i)) {
                JavaKind kind = signature.getParameterKind(i - offset);
                assert IS_IN_NATIVE_IMAGE || checkConstantArgument(metaAccess, method, signature, i - offset, args.info.getParameterName(i), args.values[i], kind);

            } else if (args.info.isVarargsParameter(i)) {
                assert args.values[i] instanceof Varargs : Assertions.errorMessage(args.values[i], args, method);
                Varargs varargs = (Varargs) args.values[i];
                assert IS_IN_NATIVE_IMAGE || checkVarargs(metaAccess, method, signature, i - offset, args.info.getParameterName(i), varargs);

            } else if (args.info.isNonNullParameter(i)) {
                assert checkNonNull(method, args.info.getParameterName(i), args.values[i]);
            }
        }
        return true;
    }

    public void setMayRemoveLocation(boolean mayRemoveLocation) {
        this.mayRemoveLocation = mayRemoveLocation;
    }
}

/**
 * This class represent a temporary WithExceptionNode which will be removed during the following
 * simplification phase. This class is needed during lowering to temporarily preserve the original
 * CFG edges for select snippet lowerings.
 */
@NodeInfo(size = NodeSize.SIZE_0, cycles = NodeCycles.CYCLES_0, cyclesRationale = "This node is immediately removed on next simplification pass")
final class PlaceholderWithExceptionNode extends WithExceptionNode implements Simplifiable, MultiMemoryKill {
    static final NodeClass<PlaceholderWithExceptionNode> TYPE = NodeClass.create(PlaceholderWithExceptionNode.class);

    private final LocationIdentity killedLocation;

    protected PlaceholderWithExceptionNode(LocationIdentity killedLocation) {
        super(TYPE, StampFactory.forVoid());
        this.killedLocation = killedLocation;
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (exceptionEdge != null) {
            killExceptionEdge();
        }
        graph().removeSplit(this, next());
    }

    @Override
    public LocationIdentity[] getKilledLocationIdentities() {
        if (killedLocation != null) {
            return new LocationIdentity[]{killedLocation};
        } else {
            return MemoryKill.MULTI_KILL_NO_LOCATION;
        }
    }
}

@NodeInfo(size = NodeSize.SIZE_0, cycles = NodeCycles.CYCLES_0, cyclesRationale = "This node is immediately removed on next simplification pass")
final class PlaceholderLogicNode extends LogicNode implements Canonicalizable {
    static final NodeClass<PlaceholderLogicNode> TYPE = NodeClass.create(PlaceholderLogicNode.class);
    private boolean delete;

    protected PlaceholderLogicNode() {
        super(TYPE);
        delete = false;
    }

    public void markForDeletion() {
        delete = true;
    }

    @Override
    public Node canonical(CanonicalizerTool tool) {
        if (delete) {
            return LogicConstantNode.tautology();
        }
        return this;
    }
}
