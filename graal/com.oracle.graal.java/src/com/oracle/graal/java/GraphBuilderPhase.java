/*
 * Copyright (c) 2009, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.java;

import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.bytecode.Bytecodes.*;
import static com.oracle.graal.compiler.common.GraalInternalError.*;
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static com.oracle.graal.compiler.common.type.StampFactory.*;
import static com.oracle.graal.java.AbstractBytecodeParser.Options.*;
import static com.oracle.graal.nodes.StructuredGraph.*;
import static com.oracle.graal.nodes.type.StampTool.*;
import static java.lang.String.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.InlineInvokePlugin.InlineInfo;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.CallTargetNode.InvokeKind;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
public class GraphBuilderPhase extends BasePhase<HighTierContext> {

    private final GraphBuilderConfiguration graphBuilderConfig;

    public GraphBuilderPhase(GraphBuilderConfiguration config) {
        this.graphBuilderConfig = config;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        new Instance(context.getMetaAccess(), context.getStampProvider(), context.getConstantReflection(), graphBuilderConfig, context.getOptimisticOptimizations(), null).run(graph);
    }

    public GraphBuilderConfiguration getGraphBuilderConfig() {
        return graphBuilderConfig;
    }

    // Fully qualified name is a workaround for JDK-8056066
    public static class Instance extends com.oracle.graal.phases.Phase {

        protected StructuredGraph graph;

        private final MetaAccessProvider metaAccess;

        private final ReplacementContext initialReplacementContext;

        private final GraphBuilderConfiguration graphBuilderConfig;
        private final OptimisticOptimizations optimisticOpts;
        private final StampProvider stampProvider;
        private final ConstantReflectionProvider constantReflection;

        public Instance(MetaAccessProvider metaAccess, StampProvider stampProvider, ConstantReflectionProvider constantReflection, GraphBuilderConfiguration graphBuilderConfig,
                        OptimisticOptimizations optimisticOpts, ReplacementContext initialReplacementContext) {
            this.graphBuilderConfig = graphBuilderConfig;
            this.optimisticOpts = optimisticOpts;
            this.metaAccess = metaAccess;
            this.stampProvider = stampProvider;
            this.constantReflection = constantReflection;
            this.initialReplacementContext = initialReplacementContext;

            assert metaAccess != null;
        }

        @Override
        protected void run(@SuppressWarnings("hiding") StructuredGraph graph) {
            ResolvedJavaMethod method = graph.method();
            int entryBCI = graph.getEntryBCI();
            assert method.getCode() != null : "method must contain bytecodes: " + method;
            this.graph = graph;
            TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
            try {
                ReplacementContext replacementContext = initialReplacementContext;
                BytecodeParser parser = new BytecodeParser(null, metaAccess, method, graphBuilderConfig, optimisticOpts, entryBCI, replacementContext);
                HIRFrameStateBuilder frameState = new HIRFrameStateBuilder(parser, method, graph);
                frameState.initializeForMethodStart(graphBuilderConfig.eagerResolving() || replacementContext != null, graphBuilderConfig.getPlugins().getParameterPlugin());
                parser.build(graph.start(), frameState);

                connectLoopEndToBegin(graph);

                // Remove dead parameters.
                for (ParameterNode param : graph.getNodes(ParameterNode.TYPE)) {
                    if (param.hasNoUsages()) {
                        assert param.inputs().isEmpty();
                        param.safeDelete();
                    }
                }

                // Remove redundant begin nodes.
                Debug.dump(graph, "Before removing redundant begins");
                for (BeginNode beginNode : graph.getNodes(BeginNode.TYPE)) {
                    Node predecessor = beginNode.predecessor();
                    if (predecessor instanceof ControlSplitNode) {
                        // The begin node is necessary.
                    } else {
                        if (beginNode.hasUsages()) {
                            reanchorGuardedNodes(beginNode);
                        }
                        GraphUtil.unlinkFixedNode(beginNode);
                        beginNode.safeDelete();
                    }
                }
            } finally {
                filter.remove();
            }

            ComputeLoopFrequenciesClosure.compute(graph);
        }

        /**
         * Removes {@link GuardedNode}s from {@code beginNode}'s usages and re-attaches them to an
         * appropriate preceeding {@link GuardingNode}.
         */
        protected void reanchorGuardedNodes(BeginNode beginNode) {
            // Find the new guarding node
            GuardingNode guarding = null;
            Node pred = beginNode.predecessor();
            while (pred != null) {
                if (pred instanceof BeginNode) {
                    if (pred.predecessor() instanceof ControlSplitNode) {
                        guarding = (GuardingNode) pred;
                        break;
                    }
                } else if (pred.getNodeClass().getAllowedUsageTypes().contains(InputType.Guard)) {
                    guarding = (GuardingNode) pred;
                    break;
                }
                pred = pred.predecessor();
            }

            // Reset the guard for all of beginNode's usages
            for (Node usage : beginNode.usages().snapshot()) {
                GuardedNode guarded = (GuardedNode) usage;
                assert guarded.getGuard() == beginNode;
                guarded.setGuard(guarding);
            }
            assert beginNode.hasNoUsages() : beginNode;
        }

        @Override
        protected String getDetailedName() {
            return getName() + " " + graph.method().format("%H.%n(%p):%r");
        }

        private static class Target {

            FixedNode fixed;
            HIRFrameStateBuilder state;

            public Target(FixedNode fixed, HIRFrameStateBuilder state) {
                this.fixed = fixed;
                this.state = state;
            }
        }

        private static class ExplodedLoopContext {
            private BciBlock header;
            private int[] targetPeelIteration;
            private int peelIteration;
        }

        @SuppressWarnings("serial")
        public class BytecodeParserError extends GraalInternalError {

            public BytecodeParserError(Throwable cause) {
                super(cause);
            }

            public BytecodeParserError(String msg, Object... args) {
                super(msg, args);
            }
        }

        public class BytecodeParser extends AbstractBytecodeParser implements GraphBuilderContext {

            private BciBlockMapping blockMap;
            private LocalLiveness liveness;
            protected final int entryBCI;
            private final BytecodeParser parent;

            private LineNumberTable lnt;
            private int previousLineNumber;
            private int currentLineNumber;

            private ValueNode methodSynchronizedObject;

            private ValueNode returnValue;
            private FixedWithNextNode beforeReturnNode;
            private ValueNode unwindValue;
            private FixedWithNextNode beforeUnwindNode;

            private FixedWithNextNode lastInstr;                 // the last instruction added
            private final boolean explodeLoops;
            private final boolean mergeExplosions;
            private final Map<HIRFrameStateBuilder, Integer> mergeExplosionsMap;
            private Deque<ExplodedLoopContext> explodeLoopsContext;
            private int nextPeelIteration = 1;
            private boolean controlFlowSplit;
            private final InvocationPluginReceiver invocationPluginReceiver = new InvocationPluginReceiver(this);

            private FixedWithNextNode[] firstInstructionArray;
            private HIRFrameStateBuilder[] entryStateArray;
            private FixedWithNextNode[][] firstInstructionMatrix;
            private HIRFrameStateBuilder[][] entryStateMatrix;

            public BytecodeParser(BytecodeParser parent, MetaAccessProvider metaAccess, ResolvedJavaMethod method, GraphBuilderConfiguration graphBuilderConfig,
                            OptimisticOptimizations optimisticOpts, int entryBCI, ReplacementContext replacementContext) {
                super(metaAccess, method, graphBuilderConfig, optimisticOpts, replacementContext);
                this.entryBCI = entryBCI;
                this.parent = parent;

                if (graphBuilderConfig.insertNonSafepointDebugInfo()) {
                    lnt = method.getLineNumberTable();
                    previousLineNumber = -1;
                }

                LoopExplosionPlugin loopExplosionPlugin = graphBuilderConfig.getPlugins().getLoopExplosionPlugin();
                if (loopExplosionPlugin != null) {
                    explodeLoops = loopExplosionPlugin.shouldExplodeLoops(method);
                    if (explodeLoops) {
                        mergeExplosions = loopExplosionPlugin.shouldMergeExplosions(method);
                        mergeExplosionsMap = new HashMap<>();
                    } else {
                        mergeExplosions = false;
                        mergeExplosionsMap = null;
                    }
                } else {
                    explodeLoops = false;
                    mergeExplosions = false;
                    mergeExplosionsMap = null;
                }
            }

            public ValueNode getReturnValue() {
                return returnValue;
            }

            public FixedWithNextNode getBeforeReturnNode() {
                return this.beforeReturnNode;
            }

            public ValueNode getUnwindValue() {
                return unwindValue;
            }

            public FixedWithNextNode getBeforeUnwindNode() {
                return this.beforeUnwindNode;
            }

            protected void build(FixedWithNextNode startInstruction, HIRFrameStateBuilder startFrameState) {
                if (PrintProfilingInformation.getValue() && profilingInfo != null) {
                    TTY.println("Profiling info for " + method.format("%H.%n(%p)"));
                    TTY.println(MetaUtil.indent(profilingInfo.toString(method, CodeUtil.NEW_LINE), "  "));
                }

                try (Indent indent = Debug.logAndIndent("build graph for %s", method)) {

                    // compute the block map, setup exception handlers and get the entrypoint(s)
                    BciBlockMapping newMapping = BciBlockMapping.create(stream, method);
                    this.blockMap = newMapping;
                    this.firstInstructionArray = new FixedWithNextNode[blockMap.getBlockCount()];
                    this.entryStateArray = new HIRFrameStateBuilder[blockMap.getBlockCount()];

                    if (graphBuilderConfig.doLivenessAnalysis()) {
                        try (Scope s = Debug.scope("LivenessAnalysis")) {
                            int maxLocals = method.getMaxLocals();
                            liveness = LocalLiveness.compute(stream, blockMap.getBlocks(), maxLocals, blockMap.getLoopCount());
                        } catch (Throwable e) {
                            throw Debug.handle(e);
                        }
                    }

                    lastInstr = startInstruction;
                    this.setCurrentFrameState(startFrameState);
                    stream.setBCI(0);

                    BciBlock startBlock = blockMap.getStartBlock();
                    if (startInstruction == graph.start()) {
                        StartNode startNode = graph.start();
                        if (method.isSynchronized()) {
                            startNode.setStateAfter(createFrameState(BytecodeFrame.BEFORE_BCI));
                        } else {
                            frameState.clearNonLiveLocals(startBlock, liveness, true);
                            assert bci() == 0;
                            startNode.setStateAfter(createFrameState(bci()));
                        }
                    }

                    if (method.isSynchronized()) {
                        // add a monitor enter to the start block
                        methodSynchronizedObject = synchronizedObject(frameState, method);
                        frameState.clearNonLiveLocals(startBlock, liveness, true);
                        assert bci() == 0;
                        genMonitorEnter(methodSynchronizedObject, bci());
                    }

                    if (graphBuilderConfig.insertNonSafepointDebugInfo()) {
                        append(createInfoPointNode(InfopointReason.METHOD_START));
                    }

                    currentBlock = blockMap.getStartBlock();
                    setEntryState(startBlock, 0, frameState);
                    if (startBlock.isLoopHeader && !explodeLoops) {
                        appendGoto(startBlock);
                    } else {
                        setFirstInstruction(startBlock, 0, lastInstr);
                    }

                    int index = 0;
                    BciBlock[] blocks = blockMap.getBlocks();
                    while (index < blocks.length) {
                        BciBlock block = blocks[index];
                        index = iterateBlock(blocks, block);
                    }

                    if (this.mergeExplosions) {
                        Debug.dump(graph, "Before loop detection");
                        detectLoops(startInstruction);
                    }

                    if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue() && this.beforeReturnNode != startInstruction) {
                        Debug.dump(graph, "Bytecodes parsed: " + method.getDeclaringClass().getUnqualifiedName() + "." + method.getName());
                    }
                }
            }

            private void detectLoops(FixedNode startInstruction) {
                NodeBitMap visited = graph.createNodeBitMap();
                NodeBitMap active = graph.createNodeBitMap();
                Deque<Node> stack = new ArrayDeque<>();
                stack.add(startInstruction);
                visited.mark(startInstruction);
                while (!stack.isEmpty()) {
                    Node next = stack.peek();
                    assert next.isDeleted() || visited.isMarked(next);
                    if (next.isDeleted() || active.isMarked(next)) {
                        stack.pop();
                        if (!next.isDeleted()) {
                            active.clear(next);
                        }
                    } else {
                        active.mark(next);
                        for (Node n : next.cfgSuccessors()) {
                            if (active.contains(n)) {
                                // Detected cycle.
                                assert n instanceof MergeNode;
                                assert next instanceof EndNode;
                                MergeNode merge = (MergeNode) n;
                                EndNode endNode = (EndNode) next;
                                merge.removeEnd(endNode);
                                FixedNode afterMerge = merge.next();
                                if (!(afterMerge instanceof EndNode) || !(((EndNode) afterMerge).merge() instanceof LoopBeginNode)) {
                                    merge.setNext(null);
                                    LoopBeginNode newLoopBegin = this.appendLoopBegin(merge);
                                    newLoopBegin.setNext(afterMerge);
                                }
                                LoopBeginNode loopBegin = (LoopBeginNode) ((EndNode) merge.next()).merge();
                                LoopEndNode loopEnd = graph.add(new LoopEndNode(loopBegin));
                                if (parsingReplacement()) {
                                    loopEnd.disableSafepoint();
                                }
                                endNode.replaceAndDelete(loopEnd);
                            } else if (visited.contains(n)) {
                                // Normal merge into a branch we are already exploring.
                            } else {
                                visited.mark(n);
                                stack.push(n);
                            }
                        }
                    }
                }

                Debug.dump(graph, "After loops detected");
                insertLoopEnds(startInstruction);
            }

            private void insertLoopEnds(FixedNode startInstruction) {
                NodeBitMap visited = graph.createNodeBitMap();
                Deque<Node> stack = new ArrayDeque<>();
                stack.add(startInstruction);
                visited.mark(startInstruction);
                List<LoopBeginNode> loopBegins = new ArrayList<>();
                while (!stack.isEmpty()) {
                    Node next = stack.pop();
                    assert visited.isMarked(next);
                    if (next instanceof LoopBeginNode) {
                        loopBegins.add((LoopBeginNode) next);
                    }
                    for (Node n : next.cfgSuccessors()) {
                        if (visited.contains(n)) {
                            // Nothing to do.
                        } else {
                            visited.mark(n);
                            stack.push(n);
                        }
                    }
                }

                IdentityHashMap<LoopBeginNode, List<LoopBeginNode>> innerLoopsMap = new IdentityHashMap<>();
                for (int i = loopBegins.size() - 1; i >= 0; --i) {
                    LoopBeginNode loopBegin = loopBegins.get(i);
                    insertLoopExits(loopBegin, innerLoopsMap);
                    if (DumpDuringGraphBuilding.getValue()) {
                        Debug.dump(graph, "After building loop exits for %s.", loopBegin);
                    }
                }

                // Remove degenerated merges with only one predecessor.
                for (LoopBeginNode loopBegin : loopBegins) {
                    Node pred = loopBegin.forwardEnd().predecessor();
                    if (pred instanceof MergeNode) {
                        MergeNode.removeMergeIfDegenerated((MergeNode) pred);
                    }
                }
            }

            private void insertLoopExits(LoopBeginNode loopBegin, IdentityHashMap<LoopBeginNode, List<LoopBeginNode>> innerLoopsMap) {
                NodeBitMap visited = graph.createNodeBitMap();
                Deque<Node> stack = new ArrayDeque<>();
                for (LoopEndNode loopEnd : loopBegin.loopEnds()) {
                    stack.push(loopEnd);
                    visited.mark(loopEnd);
                }

                List<ControlSplitNode> controlSplits = new ArrayList<>();
                List<LoopBeginNode> innerLoopBegins = new ArrayList<>();

                while (!stack.isEmpty()) {
                    Node current = stack.pop();
                    if (current == loopBegin) {
                        continue;
                    }
                    for (Node pred : current.cfgPredecessors()) {
                        if (!visited.isMarked(pred)) {
                            visited.mark(pred);
                            if (pred instanceof LoopExitNode) {
                                // Inner loop
                                LoopExitNode loopExitNode = (LoopExitNode) pred;
                                LoopBeginNode innerLoopBegin = loopExitNode.loopBegin();
                                if (!visited.isMarked(innerLoopBegin)) {
                                    stack.push(innerLoopBegin);
                                    visited.mark(innerLoopBegin);
                                    innerLoopBegins.add(innerLoopBegin);
                                }
                            } else {
                                if (pred instanceof ControlSplitNode) {
                                    ControlSplitNode controlSplitNode = (ControlSplitNode) pred;
                                    controlSplits.add(controlSplitNode);
                                }
                                stack.push(pred);
                            }
                        }
                    }
                }

                for (ControlSplitNode controlSplit : controlSplits) {
                    for (Node succ : controlSplit.cfgSuccessors()) {
                        if (!visited.isMarked(succ)) {
                            LoopExitNode loopExit = graph.add(new LoopExitNode(loopBegin));
                            FixedNode next = ((FixedWithNextNode) succ).next();
                            next.replaceAtPredecessor(loopExit);
                            loopExit.setNext(next);
                        }
                    }
                }

                for (LoopBeginNode inner : innerLoopBegins) {
                    addLoopExits(loopBegin, inner, innerLoopsMap, visited);
                    if (DumpDuringGraphBuilding.getValue()) {
                        Debug.dump(graph, "After adding loop exits for %s.", inner);
                    }
                }

                innerLoopsMap.put(loopBegin, innerLoopBegins);
            }

            private void addLoopExits(LoopBeginNode loopBegin, LoopBeginNode inner, IdentityHashMap<LoopBeginNode, List<LoopBeginNode>> innerLoopsMap, NodeBitMap visited) {
                for (LoopExitNode exit : inner.loopExits()) {
                    if (!visited.isMarked(exit)) {
                        LoopExitNode newLoopExit = graph.add(new LoopExitNode(loopBegin));
                        FixedNode next = exit.next();
                        next.replaceAtPredecessor(newLoopExit);
                        newLoopExit.setNext(next);
                    }
                }

                for (LoopBeginNode innerInner : innerLoopsMap.get(inner)) {
                    addLoopExits(loopBegin, innerInner, innerLoopsMap, visited);
                }
            }

            private int iterateBlock(BciBlock[] blocks, BciBlock block) {
                if (block.isLoopHeader && this.explodeLoops) {
                    return iterateExplodedLoopHeader(blocks, block);
                } else {
                    processBlock(this, block);
                    return block.getId() + 1;
                }
            }

            private int iterateExplodedLoopHeader(BciBlock[] blocks, BciBlock header) {
                if (explodeLoopsContext == null) {
                    explodeLoopsContext = new ArrayDeque<>();
                }

                ExplodedLoopContext context = new ExplodedLoopContext();
                context.header = header;
                context.peelIteration = this.getCurrentDimension();
                if (this.mergeExplosions) {
                    this.addToMergeCache(getEntryState(context.header, context.peelIteration), context.peelIteration);
                }
                explodeLoopsContext.push(context);
                if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue()) {
                    Debug.dump(graph, "before loop explosion dimension " + context.peelIteration);
                }
                peelIteration(blocks, header, context);
                explodeLoopsContext.pop();
                return header.loopEnd + 1;
            }

            private void addToMergeCache(HIRFrameStateBuilder key, int dimension) {
                mergeExplosionsMap.put(key, dimension);
            }

            private void peelIteration(BciBlock[] blocks, BciBlock header, ExplodedLoopContext context) {
                while (true) {
                    if (TraceParserPlugins.getValue()) {
                        traceWithContext("exploding loop, iteration %d", context.peelIteration);
                    }
                    processBlock(this, header);
                    int j = header.getId() + 1;
                    while (j <= header.loopEnd) {
                        BciBlock block = blocks[j];
                        j = iterateBlock(blocks, block);
                    }

                    int[] targets = context.targetPeelIteration;
                    if (targets != null) {
                        // We were reaching the backedge during explosion. Explode further.
                        for (int i = 0; i < targets.length; ++i) {
                            context.peelIteration = targets[i];
                            context.targetPeelIteration = null;
                            if (Debug.isDumpEnabled() && DumpDuringGraphBuilding.getValue()) {
                                Debug.dump(graph, "next loop explosion iteration " + context.peelIteration);
                            }
                            if (i < targets.length - 1) {
                                peelIteration(blocks, header, context);
                            }
                        }
                    } else {
                        // We did not reach the backedge. Exit.
                        break;
                    }
                }
            }

            /**
             * @param type the unresolved type of the constant
             */
            @Override
            protected void handleUnresolvedLoadConstant(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type the unresolved type of the type check
             * @param object the object value whose type is being checked against {@code type}
             */
            @Override
            protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
                assert !graphBuilderConfig.eagerResolving();
                append(new FixedGuardNode(graph.unique(new IsNullNode(object)), Unresolved, InvalidateRecompile));
                frameState.apush(appendConstant(JavaConstant.NULL_POINTER));
            }

            /**
             * @param type the unresolved type of the type check
             * @param object the object value whose type is being checked against {@code type}
             */
            @Override
            protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
                assert !graphBuilderConfig.eagerResolving();
                AbstractBeginNode successor = graph.add(new BeginNode());
                DeoptimizeNode deopt = graph.add(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                append(new IfNode(graph.unique(new IsNullNode(object)), successor, deopt, 1));
                lastInstr = successor;
                frameState.ipush(appendConstant(JavaConstant.INT_0));
            }

            /**
             * @param type the type being instantiated
             */
            @Override
            protected void handleUnresolvedNewInstance(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type the type of the array being instantiated
             * @param length the length of the array
             */
            @Override
            protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type the type being instantiated
             * @param dims the dimensions for the multi-array
             */
            @Override
            protected void handleUnresolvedNewMultiArray(JavaType type, List<ValueNode> dims) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param field the unresolved field
             * @param receiver the object containing the field or {@code null} if {@code field} is
             *            static
             */
            @Override
            protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param field the unresolved field
             * @param value the value being stored to the field
             * @param receiver the object containing the field or {@code null} if {@code field} is
             *            static
             */
            @Override
            protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param type
             */
            @Override
            protected void handleUnresolvedExceptionType(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            /**
             * @param javaMethod
             * @param invokeKind
             */
            protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            private DispatchBeginNode handleException(ValueNode exceptionObject, int bci) {
                assert bci == BytecodeFrame.BEFORE_BCI || bci == bci() : "invalid bci";
                Debug.log("Creating exception dispatch edges at %d, exception object=%s, exception seen=%s", bci, exceptionObject, (profilingInfo == null ? "" : profilingInfo.getExceptionSeen(bci)));

                BciBlock dispatchBlock = currentBlock.exceptionDispatchBlock();
                /*
                 * The exception dispatch block is always for the last bytecode of a block, so if we
                 * are not at the endBci yet, there is no exception handler for this bci and we can
                 * unwind immediately.
                 */
                if (bci != currentBlock.endBci || dispatchBlock == null) {
                    dispatchBlock = blockMap.getUnwindBlock();
                }

                HIRFrameStateBuilder dispatchState = frameState.copy();
                dispatchState.clearStack();

                DispatchBeginNode dispatchBegin;
                if (exceptionObject == null) {
                    dispatchBegin = graph.add(new ExceptionObjectNode(metaAccess));
                    dispatchState.apush(dispatchBegin);
                    dispatchState.setRethrowException(true);
                    dispatchBegin.setStateAfter(dispatchState.create(bci));
                } else {
                    dispatchBegin = graph.add(new DispatchBeginNode());
                    dispatchState.apush(exceptionObject);
                    dispatchBegin.setStateAfter(dispatchState.create(bci));
                    dispatchState.setRethrowException(true);
                }
                this.controlFlowSplit = true;
                FixedNode target = createTarget(dispatchBlock, dispatchState);
                FixedWithNextNode finishedDispatch = finishInstruction(dispatchBegin, dispatchState);
                finishedDispatch.setNext(target);
                return dispatchBegin;
            }

            @Override
            protected ValueNode genLoadIndexed(ValueNode array, ValueNode index, Kind kind) {
                return LoadIndexedNode.create(array, index, kind, metaAccess, constantReflection);
            }

            @Override
            protected void genStoreIndexed(ValueNode array, ValueNode index, Kind kind, ValueNode value) {
                add(new StoreIndexedNode(array, index, kind, value));
            }

            @Override
            protected ValueNode genIntegerAdd(Kind kind, ValueNode x, ValueNode y) {
                return AddNode.create(x, y);
            }

            @Override
            protected ValueNode genIntegerSub(Kind kind, ValueNode x, ValueNode y) {
                return SubNode.create(x, y);
            }

            @Override
            protected ValueNode genIntegerMul(Kind kind, ValueNode x, ValueNode y) {
                return MulNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatAdd(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return AddNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatSub(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return SubNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatMul(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return MulNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatDiv(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return DivNode.create(x, y);
            }

            @Override
            protected ValueNode genFloatRem(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new RemNode(x, y);
            }

            @Override
            protected ValueNode genIntegerDiv(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerDivNode(x, y);
            }

            @Override
            protected ValueNode genIntegerRem(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerRemNode(x, y);
            }

            @Override
            protected ValueNode genNegateOp(ValueNode x) {
                return (new NegateNode(x));
            }

            @Override
            protected ValueNode genLeftShift(Kind kind, ValueNode x, ValueNode y) {
                return new LeftShiftNode(x, y);
            }

            @Override
            protected ValueNode genRightShift(Kind kind, ValueNode x, ValueNode y) {
                return new RightShiftNode(x, y);
            }

            @Override
            protected ValueNode genUnsignedRightShift(Kind kind, ValueNode x, ValueNode y) {
                return new UnsignedRightShiftNode(x, y);
            }

            @Override
            protected ValueNode genAnd(Kind kind, ValueNode x, ValueNode y) {
                return AndNode.create(x, y);
            }

            @Override
            protected ValueNode genOr(Kind kind, ValueNode x, ValueNode y) {
                return OrNode.create(x, y);
            }

            @Override
            protected ValueNode genXor(Kind kind, ValueNode x, ValueNode y) {
                return XorNode.create(x, y);
            }

            @Override
            protected ValueNode genNormalizeCompare(ValueNode x, ValueNode y, boolean isUnorderedLess) {
                return NormalizeCompareNode.create(x, y, isUnorderedLess, constantReflection);
            }

            @Override
            protected ValueNode genFloatConvert(FloatConvert op, ValueNode input) {
                return FloatConvertNode.create(op, input);
            }

            @Override
            protected ValueNode genNarrow(ValueNode input, int bitCount) {
                return NarrowNode.create(input, bitCount);
            }

            @Override
            protected ValueNode genSignExtend(ValueNode input, int bitCount) {
                return SignExtendNode.create(input, bitCount);
            }

            @Override
            protected ValueNode genZeroExtend(ValueNode input, int bitCount) {
                return ZeroExtendNode.create(input, bitCount);
            }

            @Override
            protected void genGoto() {
                appendGoto(currentBlock.getSuccessor(0));
                assert currentBlock.numNormalSuccessors() == 1;
            }

            @Override
            protected LogicNode genObjectEquals(ValueNode x, ValueNode y) {
                return ObjectEqualsNode.create(x, y, constantReflection);
            }

            @Override
            protected LogicNode genIntegerEquals(ValueNode x, ValueNode y) {
                return IntegerEqualsNode.create(x, y, constantReflection);
            }

            @Override
            protected LogicNode genIntegerLessThan(ValueNode x, ValueNode y) {
                return IntegerLessThanNode.create(x, y, constantReflection);
            }

            @Override
            protected ValueNode genUnique(ValueNode x) {
                return (ValueNode) graph.unique((Node & ValueNumberable) x);
            }

            protected ValueNode genIfNode(LogicNode condition, FixedNode falseSuccessor, FixedNode trueSuccessor, double d) {
                return new IfNode(condition, falseSuccessor, trueSuccessor, d);
            }

            @Override
            protected void genThrow() {
                ValueNode exception = frameState.apop();
                append(new FixedGuardNode(graph.unique(new IsNullNode(exception)), NullCheckException, InvalidateReprofile, true));
                lastInstr.setNext(handleException(exception, bci()));
            }

            @Override
            protected ValueNode createCheckCast(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck, boolean forStoreCheck) {
                return CheckCastNode.create(type, object, profileForTypeCheck, forStoreCheck, graph.getAssumptions());
            }

            @Override
            protected ValueNode createInstanceOf(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck) {
                return InstanceOfNode.create(type, object, profileForTypeCheck);
            }

            @Override
            protected ValueNode genConditional(ValueNode x) {
                return new ConditionalNode((LogicNode) x);
            }

            @Override
            protected NewInstanceNode createNewInstance(ResolvedJavaType type, boolean fillContents) {
                return new NewInstanceNode(type, fillContents);
            }

            @Override
            protected NewArrayNode createNewArray(ResolvedJavaType elementType, ValueNode length, boolean fillContents) {
                return new NewArrayNode(elementType, length, fillContents);
            }

            @Override
            protected NewMultiArrayNode createNewMultiArray(ResolvedJavaType type, List<ValueNode> dimensions) {
                return new NewMultiArrayNode(type, dimensions.toArray(new ValueNode[0]));
            }

            @Override
            protected ValueNode genLoadField(ValueNode receiver, ResolvedJavaField field) {
                return new LoadFieldNode(receiver, field);
            }

            @Override
            protected ValueNode emitExplicitNullCheck(ValueNode receiver) {
                if (StampTool.isPointerNonNull(receiver.stamp())) {
                    return receiver;
                }
                BytecodeExceptionNode exception = graph.add(new BytecodeExceptionNode(metaAccess, NullPointerException.class));
                AbstractBeginNode falseSucc = graph.add(new BeginNode());
                PiNode nonNullReceiver = graph.unique(new PiNode(receiver, receiver.stamp().join(objectNonNull())));
                nonNullReceiver.setGuard(falseSucc);
                append(new IfNode(graph.unique(new IsNullNode(receiver)), exception, falseSucc, 0.01));
                lastInstr = falseSucc;

                exception.setStateAfter(createFrameState(bci()));
                exception.setNext(handleException(exception, bci()));
                return nonNullReceiver;
            }

            @Override
            protected void emitExplicitBoundsCheck(ValueNode index, ValueNode length) {
                AbstractBeginNode trueSucc = graph.add(new BeginNode());
                BytecodeExceptionNode exception = graph.add(new BytecodeExceptionNode(metaAccess, ArrayIndexOutOfBoundsException.class, index));
                append(new IfNode(graph.unique(IntegerBelowNode.create(index, length, constantReflection)), trueSucc, exception, 0.99));
                lastInstr = trueSucc;

                exception.setStateAfter(createFrameState(bci()));
                exception.setNext(handleException(exception, bci()));
            }

            @Override
            protected ValueNode genArrayLength(ValueNode x) {
                return ArrayLengthNode.create(x, constantReflection);
            }

            @Override
            protected void genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
                StoreFieldNode storeFieldNode = new StoreFieldNode(receiver, field, value);
                append(storeFieldNode);
                storeFieldNode.setStateAfter(this.createFrameState(stream.nextBCI()));
            }

            /**
             * Ensure that concrete classes are at least linked before generating an invoke.
             * Interfaces may never be linked so simply return true for them.
             *
             * @param target
             * @return true if the declared holder is an interface or is linked
             */
            private boolean callTargetIsResolved(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
                    ResolvedJavaType resolvedType = resolvedTarget.getDeclaringClass();
                    return resolvedType.isInterface() || resolvedType.isLinked();
                }
                return false;
            }

            @Override
            protected void genInvokeStatic(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
                    ResolvedJavaType holder = resolvedTarget.getDeclaringClass();
                    if (!holder.isInitialized() && ResolveClassBeforeStaticInvoke.getValue()) {
                        handleUnresolvedInvoke(target, InvokeKind.Static);
                    } else {
                        ValueNode[] args = frameState.popArguments(resolvedTarget.getSignature().getParameterCount(false));
                        appendInvoke(InvokeKind.Static, resolvedTarget, args);
                    }
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Static);
                }
            }

            @Override
            protected void genInvokeInterface(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
                    appendInvoke(InvokeKind.Interface, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Interface);
                }
            }

            @Override
            protected void genInvokeDynamic(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    JavaConstant appendix = constantPool.lookupAppendix(stream.readCPI4(), Bytecodes.INVOKEDYNAMIC);
                    if (appendix != null) {
                        frameState.apush(ConstantNode.forConstant(appendix, metaAccess, graph));
                    }
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(false));
                    appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Static);
                }
            }

            @Override
            protected void genInvokeVirtual(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    /*
                     * Special handling for runtimes that rewrite an invocation of
                     * MethodHandle.invoke(...) or MethodHandle.invokeExact(...) to a static
                     * adapter. HotSpot does this - see
                     * https://wikis.oracle.com/display/HotSpotInternals/Method+handles
                     * +and+invokedynamic
                     */
                    boolean hasReceiver = !((ResolvedJavaMethod) target).isStatic();
                    JavaConstant appendix = constantPool.lookupAppendix(stream.readCPI(), Bytecodes.INVOKEVIRTUAL);
                    if (appendix != null) {
                        frameState.apush(ConstantNode.forConstant(appendix, metaAccess, graph));
                    }
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(hasReceiver));
                    if (hasReceiver) {
                        appendInvoke(InvokeKind.Virtual, (ResolvedJavaMethod) target, args);
                    } else {
                        appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
                    }
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Virtual);
                }

            }

            @Override
            protected void genInvokeSpecial(JavaMethod target) {
                if (callTargetIsResolved(target)) {
                    assert target != null;
                    assert target.getSignature() != null;
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterCount(true));
                    appendInvoke(InvokeKind.Special, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Special);
                }
            }

            private InvokeKind currentInvokeKind;
            private JavaType currentInvokeReturnType;

            public InvokeKind getInvokeKind() {
                return currentInvokeKind;
            }

            public JavaType getInvokeReturnType() {
                return currentInvokeReturnType;
            }

            public void handleReplacedInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args) {
                appendInvoke(invokeKind, targetMethod, args);
            }

            private void appendInvoke(InvokeKind initialInvokeKind, ResolvedJavaMethod initialTargetMethod, ValueNode[] args) {
                ResolvedJavaMethod targetMethod = initialTargetMethod;
                InvokeKind invokeKind = initialInvokeKind;
                if (initialInvokeKind.isIndirect()) {
                    ResolvedJavaType contextType = this.frameState.method.getDeclaringClass();
                    ResolvedJavaMethod specialCallTarget = MethodCallTargetNode.findSpecialCallTarget(initialInvokeKind, args[0], initialTargetMethod, contextType);
                    if (specialCallTarget != null) {
                        invokeKind = InvokeKind.Special;
                        targetMethod = specialCallTarget;
                    }
                }

                Kind resultType = targetMethod.getSignature().getReturnKind();
                if (DeoptALot.getValue()) {
                    append(new DeoptimizeNode(DeoptimizationAction.None, RuntimeConstraint));
                    frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, graph));
                    return;
                }

                JavaType returnType = targetMethod.getSignature().getReturnType(method.getDeclaringClass());
                if (graphBuilderConfig.eagerResolving() || parsingReplacement()) {
                    returnType = returnType.resolve(targetMethod.getDeclaringClass());
                }
                if (invokeKind.hasReceiver()) {
                    args[0] = emitExplicitExceptions(args[0], null);
                    if (invokeKind.isIndirect() && profilingInfo != null && this.optimisticOpts.useTypeCheckHints()) {
                        JavaTypeProfile profile = profilingInfo.getTypeProfile(bci());
                        args[0] = TypeProfileProxyNode.proxify(args[0], profile);
                    }

                    if (args[0].isNullConstant()) {
                        append(new DeoptimizeNode(InvalidateRecompile, NullCheckException));
                        return;
                    }
                }

                try {
                    currentInvokeReturnType = returnType;
                    currentInvokeKind = invokeKind;
                    if (tryGenericInvocationPlugin(args, targetMethod)) {
                        if (TraceParserPlugins.getValue()) {
                            traceWithContext("used generic invocation plugin for %s", targetMethod.format("%h.%n(%p)"));
                        }
                        return;
                    }

                    if (invokeKind.isDirect()) {
                        if (tryInvocationPlugin(args, targetMethod, resultType)) {
                            if (TraceParserPlugins.getValue()) {
                                traceWithContext("used invocation plugin for %s", targetMethod.format("%h.%n(%p)"));
                            }
                            return;
                        }

                        if (tryInline(args, targetMethod, returnType)) {
                            return;
                        }
                    }
                } finally {
                    currentInvokeReturnType = null;
                    currentInvokeKind = null;
                }

                MethodCallTargetNode callTarget = graph.add(createMethodCallTarget(invokeKind, targetMethod, args, returnType));

                // be conservative if information was not recorded (could result in endless
                // recompiles otherwise)
                Invoke invoke;
                if (!StressInvokeWithExceptionNode.getValue() &&
                                (graphBuilderConfig.omitAllExceptionEdges() || (optimisticOpts.useExceptionProbability() && profilingInfo != null && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE))) {
                    invoke = createInvoke(callTarget, resultType);
                } else {
                    invoke = createInvokeWithException(callTarget, resultType);
                    AbstractBeginNode beginNode = graph.add(new KillingBeginNode(LocationIdentity.any()));
                    invoke.setNext(beginNode);
                    lastInstr = beginNode;
                }

                InlineInvokePlugin plugin = graphBuilderConfig.getPlugins().getInlineInvokePlugin();
                if (plugin != null) {
                    if (TraceParserPlugins.getValue()) {
                        traceWithContext("did not inline %s", targetMethod.format("%h.%n(%p)"));
                    }
                    plugin.notifyOfNoninlinedInvoke(this, targetMethod, invoke);
                }
            }

            /**
             * Contains all the assertion checking logic around the application of an
             * {@link InvocationPlugin}. This class is only loaded when assertions are enabled.
             */
            class InvocationPluginAssertions {
                final InvocationPlugin plugin;
                final ValueNode[] args;
                final ResolvedJavaMethod targetMethod;
                final Kind resultType;
                final int beforeStackSize;
                final boolean needsNullCheck;
                final int nodeCount;
                final Mark mark;

                public InvocationPluginAssertions(InvocationPlugin plugin, ValueNode[] args, ResolvedJavaMethod targetMethod, Kind resultType) {
                    guarantee(assertionsEnabled(), "%s should only be loaded and instantiated if assertions are enabled", getClass().getSimpleName());
                    this.plugin = plugin;
                    this.targetMethod = targetMethod;
                    this.args = args;
                    this.resultType = resultType;
                    this.beforeStackSize = frameState.stackSize;
                    this.needsNullCheck = !targetMethod.isStatic() && args[0].getKind() == Kind.Object && !StampTool.isPointerNonNull(args[0].stamp());
                    this.nodeCount = graph.getNodeCount();
                    this.mark = graph.getMark();
                }

                String error(String format, Object... a) {
                    return String.format(format, a) + String.format("%n\tplugin at %s", plugin.getApplySourceLocation(metaAccess));
                }

                boolean check(boolean pluginResult) {
                    if (pluginResult == true) {
                        int expectedStackSize = beforeStackSize + resultType.getSlotCount();
                        assert expectedStackSize == frameState.stackSize : error("plugin manipulated the stack incorrectly: expected=%d, actual=%d", expectedStackSize, frameState.stackSize);
                        NodeIterable<Node> newNodes = graph.getNewNodes(mark);
                        assert !needsNullCheck || isPointerNonNull(args[0].stamp()) : error("plugin needs to null check the receiver of %s: receiver=%s", targetMethod.format("%H.%n(%p)"), args[0]);
                        for (Node n : newNodes) {
                            if (n instanceof StateSplit) {
                                StateSplit stateSplit = (StateSplit) n;
                                assert stateSplit.stateAfter() != null : error("%s node added by plugin for %s need to have a non-null frame state: %s", StateSplit.class.getSimpleName(),
                                                targetMethod.format("%H.%n(%p)"), stateSplit);
                            }
                        }
                        try {
                            graphBuilderConfig.getPlugins().getInvocationPlugins().checkNewNodes(BytecodeParser.this, plugin, newNodes);
                        } catch (Throwable t) {
                            throw new AssertionError(error("Error in plugin"), t);
                        }
                    } else {
                        assert nodeCount == graph.getNodeCount() : error("plugin that returns false must not create new nodes");
                        assert beforeStackSize == frameState.stackSize : error("plugin that returns false must modify the stack");
                    }
                    return true;
                }
            }

            private boolean tryInvocationPlugin(ValueNode[] args, ResolvedJavaMethod targetMethod, Kind resultType) {
                InvocationPlugin plugin = graphBuilderConfig.getPlugins().getInvocationPlugins().lookupInvocation(targetMethod);
                if (plugin != null) {

                    ReplacementContext context = this.replacementContext;
                    if (context != null && context.isCallToOriginal(targetMethod)) {
                        // Self recursive replacement means the original
                        // method should be called.
                        assert !targetMethod.hasBytecodes() : "TODO: when does this happen?";
                        return false;
                    }

                    InvocationPluginAssertions assertions = assertionsEnabled() ? new InvocationPluginAssertions(plugin, args, targetMethod, resultType) : null;
                    if (plugin.execute(this, targetMethod, invocationPluginReceiver.init(targetMethod, args), args)) {
                        assert assertions.check(true);
                        return true;
                    }
                    assert assertions.check(false);
                }
                return false;
            }

            private boolean tryGenericInvocationPlugin(ValueNode[] args, ResolvedJavaMethod targetMethod) {
                GenericInvocationPlugin plugin = graphBuilderConfig.getPlugins().getGenericInvocationPlugin();
                return plugin != null && plugin.apply(this, targetMethod, args);
            }

            private boolean tryInline(ValueNode[] args, ResolvedJavaMethod targetMethod, JavaType returnType) {
                InlineInvokePlugin plugin = graphBuilderConfig.getPlugins().getInlineInvokePlugin();
                boolean canBeInlined = parsingReplacement() || targetMethod.canBeInlined();
                if (plugin == null || !canBeInlined) {
                    return false;
                }
                InlineInfo inlineInfo = plugin.getInlineInfo(this, targetMethod, args, returnType);
                if (inlineInfo != null) {
                    return inline(plugin, targetMethod, inlineInfo.methodToInline, inlineInfo.isReplacement, inlineInfo.isIntrinsic, args);
                }
                return false;
            }

            public void intrinsify(ResolvedJavaMethod targetMethod, ResolvedJavaMethod substitute, ValueNode[] args) {
                boolean res = inline(null, targetMethod, substitute, true, true, args);
                assert res : "failed to inline " + substitute;
            }

            private boolean inline(InlineInvokePlugin plugin, ResolvedJavaMethod targetMethod, ResolvedJavaMethod inlinedMethod, boolean isReplacement, boolean isIntrinsic, ValueNode[] args) {
                int bci = bci();
                if (TraceInlineDuringParsing.getValue() || TraceParserPlugins.getValue()) {
                    if (targetMethod.equals(inlinedMethod)) {
                        traceWithContext("inlining call to %s", inlinedMethod.format("%h.%n(%p)"));
                    } else {
                        traceWithContext("inlining call to %s as replacement for %s", inlinedMethod.format("%h.%n(%p)"), targetMethod.format("%h.%n(%p)"));
                    }
                }
                ReplacementContext context = this.replacementContext;
                if (context != null && context.isCallToOriginal(targetMethod)) {
                    IntrinsicContext intrinsic = context.asIntrinsic();
                    if (intrinsic != null) {
                        if (intrinsic.isCompilationRoot()) {
                            // A root compiled intrinsic needs to deoptimize
                            // if the slow path is taken
                            DeoptimizeNode deopt = append(new DeoptimizeNode(InvalidateRecompile, RuntimeConstraint));
                            deopt.setStateBefore(intrinsic.getInvokeStateBefore(graph, null));
                            return true;
                        } else {
                            // Otherwise inline the original method. Any frame state created
                            // during the inlining will exclude frame(s) in the
                            // intrinsic method (see HIRFrameStateBuilder.create(int bci)).
                            parseAndInlineCallee(context.method, args, null);
                            return true;
                        }
                    } else {
                        // Self recursive replacement means the original
                        // method should be called.
                        if (context.method.hasBytecodes()) {
                            parseAndInlineCallee(context.method, args, null);
                            return true;
                        } else {
                            return false;
                        }
                    }
                } else {
                    if (context == null && isReplacement) {
                        assert !inlinedMethod.equals(targetMethod);
                        if (isIntrinsic) {
                            context = new IntrinsicContext(targetMethod, inlinedMethod, args, bci);
                        } else {
                            context = new ReplacementContext(targetMethod, inlinedMethod);
                        }
                    }
                    if (inlinedMethod.hasBytecodes()) {
                        parseAndInlineCallee(inlinedMethod, args, context);
                        if (plugin != null) {
                            plugin.postInline(inlinedMethod);
                        }
                    } else {
                        return false;
                    }
                }
                return true;
            }

            /**
             * Prints a line to {@link TTY} with a prefix indicating the current parse context. The
             * prefix is of the form:
             *
             * <pre>
             * {SPACE * n} {name of method being parsed} "(" {file name} ":" {line number} ")"
             * </pre>
             *
             * where {@code n} is the current inlining depth.
             *
             * @param format a format string
             * @param args arguments to the format string
             */
            @Override
            protected void traceWithContext(String format, Object... args) {
                StackTraceElement where = method.asStackTraceElement(bci());
                TTY.println(format("%s%s (%s:%d) %s", nSpaces(getDepth()), method.isConstructor() ? method.format("%h.%n") : method.getName(), where.getFileName(), where.getLineNumber(),
                                format(format, args)));
            }

            protected BytecodeParserError asParserError(Throwable e) {
                if (e instanceof BytecodeParserError) {
                    return (BytecodeParserError) e;
                }
                BytecodeParser bp = this;
                BytecodeParserError res = new BytecodeParserError(e);
                while (bp != null) {
                    res.addContext("parsing " + bp.method.asStackTraceElement(bp.bci()));
                    bp = bp.parent;
                }
                return res;
            }

            private void parseAndInlineCallee(ResolvedJavaMethod targetMethod, ValueNode[] args, ReplacementContext calleeReplacementContext) {
                BytecodeParser parser = new BytecodeParser(this, metaAccess, targetMethod, graphBuilderConfig, optimisticOpts, INVOCATION_ENTRY_BCI, calleeReplacementContext);
                HIRFrameStateBuilder startFrameState = new HIRFrameStateBuilder(parser, targetMethod, graph);
                if (!targetMethod.isStatic()) {
                    args[0] = nullCheckedValue(args[0]);
                }
                startFrameState.initializeFromArgumentsArray(args);
                parser.build(this.lastInstr, startFrameState);

                FixedWithNextNode calleeBeforeReturnNode = parser.getBeforeReturnNode();
                this.lastInstr = calleeBeforeReturnNode;
                if (calleeBeforeReturnNode != null) {
                    ValueNode calleeReturnValue = parser.getReturnValue();
                    if (calleeReturnValue != null) {
                        frameState.push(targetMethod.getSignature().getReturnKind().getStackKind(), calleeReturnValue);
                    }
                }

                FixedWithNextNode calleeBeforeUnwindNode = parser.getBeforeUnwindNode();
                if (calleeBeforeUnwindNode != null) {
                    ValueNode calleeUnwindValue = parser.getUnwindValue();
                    assert calleeUnwindValue != null;
                    calleeBeforeUnwindNode.setNext(handleException(calleeUnwindValue, bci()));
                }

                // Record inlined method dependency in the graph
                if (graph.isInlinedMethodRecordingEnabled()) {
                    graph.getInlinedMethods().add(targetMethod);
                }
            }

            protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, JavaType returnType) {
                return new MethodCallTargetNode(invokeKind, targetMethod, args, returnType);
            }

            protected InvokeNode createInvoke(CallTargetNode callTarget, Kind resultType) {
                InvokeNode invoke = append(new InvokeNode(callTarget, bci()));
                frameState.pushReturn(resultType, invoke);
                invoke.setStateAfter(createFrameState(stream.nextBCI()));
                return invoke;
            }

            protected InvokeWithExceptionNode createInvokeWithException(CallTargetNode callTarget, Kind resultType) {
                DispatchBeginNode exceptionEdge = handleException(null, bci());
                InvokeWithExceptionNode invoke = append(new InvokeWithExceptionNode(callTarget, exceptionEdge, bci()));
                frameState.pushReturn(resultType, invoke);
                invoke.setStateAfter(createFrameState(stream.nextBCI()));
                return invoke;
            }

            @Override
            protected void genReturn(ValueNode returnVal, Kind returnKind) {

                if (parent == null) {
                    frameState.setRethrowException(false);
                    frameState.clearStack();
                    beforeReturn(returnVal, returnKind);
                    append(new ReturnNode(returnVal));
                } else {
                    if (blockMap.getReturnCount() == 1 || !controlFlowSplit) {
                        // There is only a single return.
                        beforeReturn(returnVal, returnKind);
                        this.returnValue = returnVal;
                        this.beforeReturnNode = this.lastInstr;
                        this.lastInstr = null;
                    } else {
                        frameState.setRethrowException(false);
                        frameState.clearStack();
                        if (returnVal != null) {
                            frameState.push(returnKind, returnVal);
                        }
                        assert blockMap.getReturnCount() > 1;
                        appendGoto(blockMap.getReturnBlock());
                    }
                }
            }

            private void beforeReturn(ValueNode x, Kind kind) {
                if (graphBuilderConfig.insertNonSafepointDebugInfo()) {
                    append(createInfoPointNode(InfopointReason.METHOD_END));
                }

                synchronizedEpilogue(BytecodeFrame.AFTER_BCI, x, kind);
                if (frameState.lockDepth() != 0) {
                    throw bailout("unbalanced monitors");
                }
            }

            @Override
            protected void genMonitorEnter(ValueNode x, int bci) {
                MonitorIdNode monitorId = graph.add(new MonitorIdNode(frameState.lockDepth()));
                MonitorEnterNode monitorEnter = append(new MonitorEnterNode(x, monitorId));
                frameState.pushLock(x, monitorId);
                monitorEnter.setStateAfter(createFrameState(bci));
            }

            @Override
            protected void genMonitorExit(ValueNode x, ValueNode escapedReturnValue, int bci) {
                MonitorIdNode monitorId = frameState.peekMonitorId();
                ValueNode lockedObject = frameState.popLock();
                if (GraphUtil.originalValue(lockedObject) != GraphUtil.originalValue(x)) {
                    throw bailout(String.format("unbalanced monitors: mismatch at monitorexit, %s != %s", GraphUtil.originalValue(x), GraphUtil.originalValue(lockedObject)));
                }
                MonitorExitNode monitorExit = append(new MonitorExitNode(x, monitorId, escapedReturnValue));
                monitorExit.setStateAfter(createFrameState(bci));
            }

            @Override
            protected void genJsr(int dest) {
                BciBlock successor = currentBlock.getJsrSuccessor();
                assert successor.startBci == dest : successor.startBci + " != " + dest + " @" + bci();
                JsrScope scope = currentBlock.getJsrScope();
                int nextBci = getStream().nextBCI();
                if (!successor.getJsrScope().pop().equals(scope)) {
                    throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
                }
                if (successor.getJsrScope().nextReturnAddress() != nextBci) {
                    throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
                }
                ConstantNode nextBciNode = getJsrConstant(nextBci);
                frameState.push(Kind.Int, nextBciNode);
                appendGoto(successor);
            }

            @Override
            protected void genRet(int localIndex) {
                BciBlock successor = currentBlock.getRetSuccessor();
                ValueNode local = frameState.loadLocal(localIndex);
                JsrScope scope = currentBlock.getJsrScope();
                int retAddress = scope.nextReturnAddress();
                ConstantNode returnBciNode = getJsrConstant(retAddress);
                LogicNode guard = IntegerEqualsNode.create(local, returnBciNode, constantReflection);
                guard = graph.unique(guard);
                append(new FixedGuardNode(guard, JavaSubroutineMismatch, InvalidateReprofile));
                if (!successor.getJsrScope().equals(scope.pop())) {
                    throw new JsrNotSupportedBailout("unstructured control flow (ret leaves more than one scope)");
                }
                appendGoto(successor);
            }

            private ConstantNode getJsrConstant(long bci) {
                JavaConstant nextBciConstant = new RawConstant(bci);
                Stamp nextBciStamp = StampFactory.forConstant(nextBciConstant);
                ConstantNode nextBciNode = new ConstantNode(nextBciConstant, nextBciStamp);
                return graph.unique(nextBciNode);
            }

            @Override
            protected void genIntegerSwitch(ValueNode value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
                if (value.isConstant()) {
                    JavaConstant constant = (JavaConstant) value.asConstant();
                    int constantValue = constant.asInt();
                    for (int i = 0; i < keys.length; ++i) {
                        if (keys[i] == constantValue) {
                            appendGoto(actualSuccessors.get(keySuccessors[i]));
                            return;
                        }
                    }
                    appendGoto(actualSuccessors.get(keySuccessors[keys.length]));
                } else {
                    this.controlFlowSplit = true;
                    double[] successorProbabilities = successorProbabilites(actualSuccessors.size(), keySuccessors, keyProbabilities);
                    IntegerSwitchNode switchNode = append(new IntegerSwitchNode(value, actualSuccessors.size(), keys, keyProbabilities, keySuccessors));
                    for (int i = 0; i < actualSuccessors.size(); i++) {
                        switchNode.setBlockSuccessor(i, createBlockTarget(successorProbabilities[i], actualSuccessors.get(i), frameState));
                    }
                }
            }

            @Override
            protected ConstantNode appendConstant(JavaConstant constant) {
                assert constant != null;
                return ConstantNode.forConstant(constant, metaAccess, graph);
            }

            @Override
            public <T extends ValueNode> T append(T v) {
                if (v.graph() != null) {
                    return v;
                }
                T added = graph.addOrUnique(v);
                if (added == v) {
                    updateLastInstruction(v);
                }
                return added;
            }

            public <T extends ValueNode> T recursiveAppend(T v) {
                if (v.graph() != null) {
                    return v;
                }
                T added = graph.addOrUniqueWithInputs(v);
                if (added == v) {
                    updateLastInstruction(v);
                }
                return added;
            }

            private <T extends ValueNode> void updateLastInstruction(T v) {
                if (v instanceof FixedNode) {
                    FixedNode fixedNode = (FixedNode) v;
                    lastInstr.setNext(fixedNode);
                    if (fixedNode instanceof FixedWithNextNode) {
                        FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) fixedNode;
                        assert fixedWithNextNode.next() == null : "cannot append instruction to instruction which isn't end";
                        lastInstr = fixedWithNextNode;
                    } else {
                        lastInstr = null;
                    }
                }
            }

            private Target checkLoopExit(FixedNode target, BciBlock targetBlock, HIRFrameStateBuilder state) {
                if (currentBlock != null && !explodeLoops) {
                    long exits = currentBlock.loops & ~targetBlock.loops;
                    if (exits != 0) {
                        LoopExitNode firstLoopExit = null;
                        LoopExitNode lastLoopExit = null;

                        int pos = 0;
                        ArrayList<BciBlock> exitLoops = new ArrayList<>(Long.bitCount(exits));
                        do {
                            long lMask = 1L << pos;
                            if ((exits & lMask) != 0) {
                                exitLoops.add(blockMap.getLoopHeader(pos));
                                exits &= ~lMask;
                            }
                            pos++;
                        } while (exits != 0);

                        Collections.sort(exitLoops, new Comparator<BciBlock>() {

                            @Override
                            public int compare(BciBlock o1, BciBlock o2) {
                                return Long.bitCount(o2.loops) - Long.bitCount(o1.loops);
                            }
                        });

                        int bci = targetBlock.startBci;
                        if (targetBlock instanceof ExceptionDispatchBlock) {
                            bci = ((ExceptionDispatchBlock) targetBlock).deoptBci;
                        }
                        HIRFrameStateBuilder newState = state.copy();
                        for (BciBlock loop : exitLoops) {
                            LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(loop, this.getCurrentDimension());
                            LoopExitNode loopExit = graph.add(new LoopExitNode(loopBegin));
                            if (lastLoopExit != null) {
                                lastLoopExit.setNext(loopExit);
                            }
                            if (firstLoopExit == null) {
                                firstLoopExit = loopExit;
                            }
                            lastLoopExit = loopExit;
                            Debug.log("Target %s Exits %s, scanning framestates...", targetBlock, loop);
                            newState.insertLoopProxies(loopExit, getEntryState(loop, this.getCurrentDimension()));
                            loopExit.setStateAfter(newState.create(bci));
                        }

                        lastLoopExit.setNext(target);
                        return new Target(firstLoopExit, newState);
                    }
                }
                return new Target(target, state);
            }

            private HIRFrameStateBuilder getEntryState(BciBlock block, int dimension) {
                int id = block.id;
                if (dimension == 0) {
                    return entryStateArray[id];
                } else {
                    return getEntryStateMultiDimension(dimension, id);
                }
            }

            private HIRFrameStateBuilder getEntryStateMultiDimension(int dimension, int id) {
                if (entryStateMatrix != null && dimension - 1 < entryStateMatrix.length) {
                    HIRFrameStateBuilder[] entryStateArrayEntry = entryStateMatrix[dimension - 1];
                    if (entryStateArrayEntry == null) {
                        return null;
                    }
                    return entryStateArrayEntry[id];
                } else {
                    return null;
                }
            }

            private void setEntryState(BciBlock block, int dimension, HIRFrameStateBuilder entryState) {
                int id = block.id;
                if (dimension == 0) {
                    this.entryStateArray[id] = entryState;
                } else {
                    setEntryStateMultiDimension(dimension, entryState, id);
                }
            }

            private void setEntryStateMultiDimension(int dimension, HIRFrameStateBuilder entryState, int id) {
                if (entryStateMatrix == null) {
                    entryStateMatrix = new HIRFrameStateBuilder[4][];
                }
                if (dimension - 1 < entryStateMatrix.length) {
                    // We are within bounds.
                } else {
                    // We are out of bounds.
                    entryStateMatrix = Arrays.copyOf(entryStateMatrix, Math.max(entryStateMatrix.length * 2, dimension));
                }
                if (entryStateMatrix[dimension - 1] == null) {
                    entryStateMatrix[dimension - 1] = new HIRFrameStateBuilder[blockMap.getBlockCount()];
                }
                entryStateMatrix[dimension - 1][id] = entryState;
            }

            private void setFirstInstruction(BciBlock block, int dimension, FixedWithNextNode firstInstruction) {
                int id = block.id;
                if (dimension == 0) {
                    this.firstInstructionArray[id] = firstInstruction;
                } else {
                    setFirstInstructionMultiDimension(dimension, firstInstruction, id);
                }
            }

            private void setFirstInstructionMultiDimension(int dimension, FixedWithNextNode firstInstruction, int id) {
                if (firstInstructionMatrix == null) {
                    firstInstructionMatrix = new FixedWithNextNode[4][];
                }
                if (dimension - 1 < firstInstructionMatrix.length) {
                    // We are within bounds.
                } else {
                    // We are out of bounds.
                    firstInstructionMatrix = Arrays.copyOf(firstInstructionMatrix, Math.max(firstInstructionMatrix.length * 2, dimension));
                }
                if (firstInstructionMatrix[dimension - 1] == null) {
                    firstInstructionMatrix[dimension - 1] = new FixedWithNextNode[blockMap.getBlockCount()];
                }
                firstInstructionMatrix[dimension - 1][id] = firstInstruction;
            }

            private FixedWithNextNode getFirstInstruction(BciBlock block, int dimension) {
                int id = block.id;
                if (dimension == 0) {
                    return firstInstructionArray[id];
                } else {
                    return getFirstInstructionMultiDimension(dimension, id);
                }
            }

            private FixedWithNextNode getFirstInstructionMultiDimension(int dimension, int id) {
                if (firstInstructionMatrix != null && dimension - 1 < firstInstructionMatrix.length) {
                    FixedWithNextNode[] firstInstructionArrayEntry = firstInstructionMatrix[dimension - 1];
                    if (firstInstructionArrayEntry == null) {
                        return null;
                    }
                    return firstInstructionArrayEntry[id];
                } else {
                    return null;
                }
            }

            private FixedNode createTarget(double probability, BciBlock block, HIRFrameStateBuilder stateAfter) {
                assert probability >= 0 && probability <= 1.01 : probability;
                if (isNeverExecutedCode(probability)) {
                    return graph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                } else {
                    assert block != null;
                    return createTarget(block, stateAfter);
                }
            }

            private FixedNode createTarget(BciBlock block, HIRFrameStateBuilder state) {
                return createTarget(block, state, false, false);
            }

            private FixedNode createTarget(BciBlock block, HIRFrameStateBuilder state, boolean canReuseInstruction, boolean canReuseState) {
                assert block != null && state != null;
                assert !block.isExceptionEntry || state.stackSize() == 1;

                int operatingDimension = findOperatingDimension(block, state);

                if (getFirstInstruction(block, operatingDimension) == null) {
                    /*
                     * This is the first time we see this block as a branch target. Create and
                     * return a placeholder that later can be replaced with a MergeNode when we see
                     * this block again.
                     */
                    FixedNode targetNode;
                    if (canReuseInstruction && (block.getPredecessorCount() == 1 || !controlFlowSplit) && !block.isLoopHeader && (currentBlock.loops & ~block.loops) == 0) {
                        setFirstInstruction(block, operatingDimension, lastInstr);
                        lastInstr = null;
                    } else {
                        setFirstInstruction(block, operatingDimension, graph.add(new BeginNode()));
                    }
                    targetNode = getFirstInstruction(block, operatingDimension);
                    Target target = checkLoopExit(targetNode, block, state);
                    FixedNode result = target.fixed;
                    HIRFrameStateBuilder currentEntryState = target.state == state ? (canReuseState ? state : state.copy()) : target.state;
                    setEntryState(block, operatingDimension, currentEntryState);
                    currentEntryState.clearNonLiveLocals(block, liveness, true);

                    Debug.log("createTarget %s: first visit, result: %s", block, targetNode);
                    return result;
                }

                // We already saw this block before, so we have to merge states.
                if (!getEntryState(block, operatingDimension).isCompatibleWith(state)) {
                    throw bailout("stacks do not match; bytecodes would not verify");
                }

                if (getFirstInstruction(block, operatingDimension) instanceof LoopBeginNode) {
                    assert this.explodeLoops || (block.isLoopHeader && currentBlock.getId() >= block.getId()) : "must be backward branch";
                    /*
                     * Backward loop edge. We need to create a special LoopEndNode and merge with
                     * the loop begin node created before.
                     */
                    LoopBeginNode loopBegin = (LoopBeginNode) getFirstInstruction(block, operatingDimension);
                    LoopEndNode loopEnd = graph.add(new LoopEndNode(loopBegin));
                    if (parsingReplacement()) {
                        loopEnd.disableSafepoint();
                    }
                    Target target = checkLoopExit(loopEnd, block, state);
                    FixedNode result = target.fixed;
                    getEntryState(block, operatingDimension).merge(loopBegin, target.state);

                    Debug.log("createTarget %s: merging backward branch to loop header %s, result: %s", block, loopBegin, result);
                    return result;
                }
                assert currentBlock == null || currentBlock.getId() < block.getId() || this.mergeExplosions : "must not be backward branch";
                assert getFirstInstruction(block, operatingDimension).next() == null || this.mergeExplosions : "bytecodes already parsed for block";

                if (getFirstInstruction(block, operatingDimension) instanceof AbstractBeginNode && !(getFirstInstruction(block, operatingDimension) instanceof AbstractMergeNode)) {
                    /*
                     * This is the second time we see this block. Create the actual MergeNode and
                     * the End Node for the already existing edge.
                     */
                    AbstractBeginNode beginNode = (AbstractBeginNode) getFirstInstruction(block, operatingDimension);

                    // The EndNode for the already existing edge.
                    EndNode end = graph.add(new EndNode());
                    // The MergeNode that replaces the placeholder.
                    AbstractMergeNode mergeNode = graph.add(new MergeNode());
                    FixedNode next = beginNode.next();

                    if (beginNode.predecessor() instanceof ControlSplitNode) {
                        beginNode.setNext(end);
                    } else {
                        beginNode.replaceAtPredecessor(end);
                        beginNode.safeDelete();
                    }

                    mergeNode.addForwardEnd(end);
                    mergeNode.setNext(next);

                    setFirstInstruction(block, operatingDimension, mergeNode);
                }

                AbstractMergeNode mergeNode = (AbstractMergeNode) getFirstInstruction(block, operatingDimension);

                // The EndNode for the newly merged edge.
                EndNode newEnd = graph.add(new EndNode());
                Target target = checkLoopExit(newEnd, block, state);
                FixedNode result = target.fixed;
                getEntryState(block, operatingDimension).merge(mergeNode, target.state);
                mergeNode.addForwardEnd(newEnd);

                Debug.log("createTarget %s: merging state, result: %s", block, result);
                return result;
            }

            private int findOperatingDimension(BciBlock block, HIRFrameStateBuilder state) {
                if (this.explodeLoops && this.explodeLoopsContext != null && !this.explodeLoopsContext.isEmpty()) {
                    return findOperatingDimensionWithLoopExplosion(block, state);
                }
                return this.getCurrentDimension();
            }

            private int findOperatingDimensionWithLoopExplosion(BciBlock block, HIRFrameStateBuilder state) {
                for (ExplodedLoopContext context : explodeLoopsContext) {
                    if (context.header == block) {

                        if (this.mergeExplosions) {
                            state.clearNonLiveLocals(block, liveness, true);
                            Integer cachedDimension = mergeExplosionsMap.get(state);
                            if (cachedDimension != null) {
                                return cachedDimension;
                            }
                        }

                        // We have a hit on our current explosion context loop begin.
                        if (context.targetPeelIteration == null) {
                            context.targetPeelIteration = new int[1];
                        } else {
                            context.targetPeelIteration = Arrays.copyOf(context.targetPeelIteration, context.targetPeelIteration.length + 1);
                        }

                        // This is the first hit => allocate a new dimension and at the same
                        // time mark the context loop begin as hit during the current
                        // iteration.
                        if (this.mergeExplosions) {
                            this.addToMergeCache(state, nextPeelIteration);
                        }
                        context.targetPeelIteration[context.targetPeelIteration.length - 1] = nextPeelIteration++;
                        if (nextPeelIteration > MaximumLoopExplosionCount.getValue()) {
                            String message = "too many loop explosion iterations - does the explosion not terminate for method " + method + "?";
                            if (FailedLoopExplosionIsFatal.getValue()) {
                                throw new RuntimeException(message);
                            } else {
                                throw bailout(message);
                            }
                        }

                        // Operate on the target dimension.
                        return context.targetPeelIteration[context.targetPeelIteration.length - 1];
                    } else if (block.getId() > context.header.getId() && block.getId() <= context.header.loopEnd) {
                        // We hit the range of this context.
                        return context.peelIteration;
                    }
                }

                // No dimension found.
                return 0;
            }

            /**
             * Returns a block begin node with the specified state. If the specified probability is
             * 0, the block deoptimizes immediately.
             */
            private AbstractBeginNode createBlockTarget(double probability, BciBlock block, HIRFrameStateBuilder stateAfter) {
                FixedNode target = createTarget(probability, block, stateAfter);
                AbstractBeginNode begin = BeginNode.begin(target);

                assert !(target instanceof DeoptimizeNode && begin instanceof BeginStateSplitNode && ((BeginStateSplitNode) begin).stateAfter() != null) : "We are not allowed to set the stateAfter of the begin node, because we have to deoptimize "
                                + "to a bci _before_ the actual if, so that the interpreter can update the profiling information.";
                return begin;
            }

            private ValueNode synchronizedObject(HIRFrameStateBuilder state, ResolvedJavaMethod target) {
                if (target.isStatic()) {
                    return appendConstant(target.getDeclaringClass().getJavaClass());
                } else {
                    return state.loadLocal(0);
                }
            }

            protected void processBlock(BytecodeParser parser, BciBlock block) {
                // Ignore blocks that have no predecessors by the time their bytecodes are parsed
                int currentDimension = this.getCurrentDimension();
                FixedWithNextNode firstInstruction = getFirstInstruction(block, currentDimension);
                if (firstInstruction == null) {
                    Debug.log("Ignoring block %s", block);
                    return;
                }
                try (Indent indent = Debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, firstInstruction, block.isLoopHeader)) {

                    lastInstr = firstInstruction;
                    frameState = getEntryState(block, currentDimension);
                    parser.setCurrentFrameState(frameState);
                    currentBlock = block;

                    if (firstInstruction instanceof AbstractMergeNode) {
                        setMergeStateAfter(block, firstInstruction);
                    }

                    if (block == blockMap.getReturnBlock()) {
                        handleReturnBlock();
                    } else if (block == blockMap.getUnwindBlock()) {
                        handleUnwindBlock();
                    } else if (block instanceof ExceptionDispatchBlock) {
                        createExceptionDispatch((ExceptionDispatchBlock) block);
                    } else {
                        frameState.setRethrowException(false);
                        iterateBytecodesForBlock(block);
                    }
                }
            }

            private void handleUnwindBlock() {
                if (parent == null) {
                    frameState.setRethrowException(false);
                    createUnwind();
                } else {
                    ValueNode exception = frameState.apop();
                    this.unwindValue = exception;
                    this.beforeUnwindNode = this.lastInstr;
                }
            }

            private void handleReturnBlock() {
                Kind returnKind = method.getSignature().getReturnKind().getStackKind();
                ValueNode x = returnKind == Kind.Void ? null : frameState.pop(returnKind);
                assert frameState.stackSize() == 0;
                beforeReturn(x, returnKind);
                this.returnValue = x;
                this.beforeReturnNode = this.lastInstr;
            }

            private void setMergeStateAfter(BciBlock block, FixedWithNextNode firstInstruction) {
                AbstractMergeNode abstractMergeNode = (AbstractMergeNode) firstInstruction;
                if (abstractMergeNode.stateAfter() == null) {
                    int bci = block.startBci;
                    if (block instanceof ExceptionDispatchBlock) {
                        bci = ((ExceptionDispatchBlock) block).deoptBci;
                    }
                    abstractMergeNode.setStateAfter(createFrameState(bci));
                }
            }

            private void createUnwind() {
                assert frameState.stackSize() == 1 : frameState;
                ValueNode exception = frameState.apop();
                synchronizedEpilogue(BytecodeFrame.AFTER_EXCEPTION_BCI, null, null);
                append(new UnwindNode(exception));
            }

            private void synchronizedEpilogue(int bci, ValueNode currentReturnValue, Kind currentReturnValueKind) {
                if (method.isSynchronized()) {
                    if (currentReturnValue != null) {
                        frameState.push(currentReturnValueKind, currentReturnValue);
                    }
                    genMonitorExit(methodSynchronizedObject, currentReturnValue, bci);
                    assert !frameState.rethrowException();
                }
            }

            private void createExceptionDispatch(ExceptionDispatchBlock block) {
                assert frameState.stackSize() == 1 : frameState;
                if (block.handler.isCatchAll()) {
                    assert block.getSuccessorCount() == 1;
                    appendGoto(block.getSuccessor(0));
                    return;
                }

                JavaType catchType = block.handler.getCatchType();
                if (graphBuilderConfig.eagerResolving()) {
                    catchType = lookupType(block.handler.catchTypeCPI(), INSTANCEOF);
                }
                boolean initialized = (catchType instanceof ResolvedJavaType);
                if (initialized && graphBuilderConfig.getSkippedExceptionTypes() != null) {
                    ResolvedJavaType resolvedCatchType = (ResolvedJavaType) catchType;
                    for (ResolvedJavaType skippedType : graphBuilderConfig.getSkippedExceptionTypes()) {
                        if (skippedType.isAssignableFrom(resolvedCatchType)) {
                            BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                            ValueNode exception = frameState.stackAt(0);
                            FixedNode trueSuccessor = graph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                            FixedNode nextDispatch = createTarget(nextBlock, frameState);
                            append(new IfNode(graph.unique(new InstanceOfNode((ResolvedJavaType) catchType, exception, null)), trueSuccessor, nextDispatch, 0));
                            return;
                        }
                    }
                }

                if (initialized) {
                    BciBlock nextBlock = block.getSuccessorCount() == 1 ? blockMap.getUnwindBlock() : block.getSuccessor(1);
                    ValueNode exception = frameState.stackAt(0);
                    CheckCastNode checkCast = graph.add(new CheckCastNode((ResolvedJavaType) catchType, exception, null, false));
                    frameState.apop();
                    frameState.push(Kind.Object, checkCast);
                    FixedNode catchSuccessor = createTarget(block.getSuccessor(0), frameState);
                    frameState.apop();
                    frameState.push(Kind.Object, exception);
                    FixedNode nextDispatch = createTarget(nextBlock, frameState);
                    checkCast.setNext(catchSuccessor);
                    append(new IfNode(graph.unique(new InstanceOfNode((ResolvedJavaType) catchType, exception, null)), checkCast, nextDispatch, 0.5));
                } else {
                    handleUnresolvedExceptionType(catchType);
                }
            }

            private void appendGoto(BciBlock successor) {
                FixedNode targetInstr = createTarget(successor, frameState, true, true);
                if (lastInstr != null && lastInstr != targetInstr) {
                    lastInstr.setNext(targetInstr);
                }
            }

            @Override
            protected void iterateBytecodesForBlock(BciBlock block) {
                if (block.isLoopHeader && !explodeLoops) {
                    // Create the loop header block, which later will merge the backward branches of
                    // the loop.
                    controlFlowSplit = true;
                    LoopBeginNode loopBegin = appendLoopBegin(this.lastInstr);
                    lastInstr = loopBegin;

                    // Create phi functions for all local variables and operand stack slots.
                    frameState.insertLoopPhis(liveness, block.loopId, loopBegin);
                    loopBegin.setStateAfter(createFrameState(block.startBci));

                    /*
                     * We have seen all forward branches. All subsequent backward branches will
                     * merge to the loop header. This ensures that the loop header has exactly one
                     * non-loop predecessor.
                     */
                    setFirstInstruction(block, this.getCurrentDimension(), loopBegin);
                    /*
                     * We need to preserve the frame state builder of the loop header so that we can
                     * merge values for phi functions, so make a copy of it.
                     */
                    setEntryState(block, this.getCurrentDimension(), frameState.copy());

                    Debug.log("  created loop header %s", loopBegin);
                } else if (block.isLoopHeader && explodeLoops && this.mergeExplosions) {
                    frameState = frameState.copy();
                }
                assert lastInstr.next() == null : "instructions already appended at block " + block;
                Debug.log("  frameState: %s", frameState);

                lastInstr = finishInstruction(lastInstr, frameState);

                int endBCI = stream.endBCI();

                stream.setBCI(block.startBci);
                int bci = block.startBci;
                BytecodesParsed.add(block.endBci - bci);

                while (bci < endBCI) {
                    if (graphBuilderConfig.insertNonSafepointDebugInfo() && lnt != null) {
                        currentLineNumber = lnt.getLineNumber(bci);
                        if (currentLineNumber != previousLineNumber) {
                            append(createInfoPointNode(InfopointReason.LINE_NUMBER));
                            previousLineNumber = currentLineNumber;
                        }
                    }

                    // read the opcode
                    int opcode = stream.currentBC();
                    assert traceState();
                    assert traceInstruction(bci, opcode, bci == block.startBci);
                    if (parent == null && bci == entryBCI) {
                        if (block.getJsrScope() != JsrScope.EMPTY_SCOPE) {
                            throw new BailoutException("OSR into a JSR scope is not supported");
                        }
                        EntryMarkerNode x = append(new EntryMarkerNode());
                        frameState.insertProxies(x);
                        x.setStateAfter(createFrameState(bci));
                    }

                    try {
                        processBytecode(bci, opcode);
                    } catch (Throwable e) {
                        throw asParserError(e);
                    }

                    if (lastInstr == null || lastInstr.next() != null) {
                        break;
                    }

                    stream.next();
                    bci = stream.currentBCI();

                    assert block == currentBlock;
                    assert !(lastInstr instanceof StateSplit) || lastInstr instanceof BeginNode || ((StateSplit) lastInstr).stateAfter() != null : lastInstr;
                    lastInstr = finishInstruction(lastInstr, frameState);
                    if (bci < endBCI) {
                        if (bci > block.endBci) {
                            assert !block.getSuccessor(0).isExceptionEntry;
                            assert block.numNormalSuccessors() == 1;
                            // we fell through to the next block, add a goto and break
                            appendGoto(block.getSuccessor(0));
                            break;
                        }
                    }
                }
            }

            private LoopBeginNode appendLoopBegin(FixedWithNextNode fixedWithNext) {
                EndNode preLoopEnd = graph.add(new EndNode());
                LoopBeginNode loopBegin = graph.add(new LoopBeginNode());
                fixedWithNext.setNext(preLoopEnd);
                // Add the single non-loop predecessor of the loop header.
                loopBegin.addForwardEnd(preLoopEnd);
                return loopBegin;
            }

            /**
             * A hook for derived classes to modify the last instruction or add other instructions.
             *
             * @param instr The last instruction (= fixed node) which was added.
             * @param state The current frame state.
             * @return Returns the (new) last instruction.
             */
            protected FixedWithNextNode finishInstruction(FixedWithNextNode instr, HIRFrameStateBuilder state) {
                return instr;
            }

            private InfopointNode createInfoPointNode(InfopointReason reason) {
                if (graphBuilderConfig.insertFullDebugInfo()) {
                    return new FullInfopointNode(reason, createFrameState(bci()));
                } else {
                    return new SimpleInfopointNode(reason, new BytecodePosition(null, method, bci()));
                }
            }

            private boolean traceState() {
                if (Debug.isEnabled() && Options.TraceBytecodeParserLevel.getValue() >= TRACELEVEL_STATE && Debug.isLogEnabled()) {
                    traceStateHelper();
                }
                return true;
            }

            private void traceStateHelper() {
                Debug.log(String.format("|   state [nr locals = %d, stack depth = %d, method = %s]", frameState.localsSize(), frameState.stackSize(), method));
                for (int i = 0; i < frameState.localsSize(); ++i) {
                    ValueNode value = frameState.localAt(i);
                    Debug.log(String.format("|   local[%d] = %-8s : %s", i, value == null ? "bogus" : value.getKind().getJavaName(), value));
                }
                for (int i = 0; i < frameState.stackSize(); ++i) {
                    ValueNode value = frameState.stackAt(i);
                    Debug.log(String.format("|   stack[%d] = %-8s : %s", i, value == null ? "bogus" : value.getKind().getJavaName(), value));
                }
            }

            @Override
            protected void genIf(ValueNode x, Condition cond, ValueNode y) {
                assert currentBlock.getSuccessorCount() == 2;
                BciBlock trueBlock = currentBlock.getSuccessor(0);
                BciBlock falseBlock = currentBlock.getSuccessor(1);
                if (trueBlock == falseBlock) {
                    // The target block is the same independent of the condition.
                    appendGoto(trueBlock);
                    return;
                }

                ValueNode a = x;
                ValueNode b = y;

                // Check whether the condition needs to mirror the operands.
                if (cond.canonicalMirror()) {
                    a = y;
                    b = x;
                }

                // Create the logic node for the condition.
                LogicNode condition = createLogicNode(cond, a, b);

                // Check whether the condition needs to negate the result.
                boolean negate = cond.canonicalNegate();

                // Remove a logic negation node and fold it into the negate boolean.
                if (condition instanceof LogicNegationNode) {
                    LogicNegationNode logicNegationNode = (LogicNegationNode) condition;
                    negate = !negate;
                    condition = logicNegationNode.getValue();
                }

                if (condition instanceof LogicConstantNode) {
                    genConstantTargetIf(trueBlock, falseBlock, negate, condition);
                } else {
                    if (condition.graph() == null) {
                        condition = graph.unique(condition);
                    }

                    // Need to get probability based on current bci.
                    double probability = branchProbability();

                    if (negate) {
                        BciBlock tmpBlock = trueBlock;
                        trueBlock = falseBlock;
                        falseBlock = tmpBlock;
                        probability = 1 - probability;
                    }

                    if (isNeverExecutedCode(probability)) {
                        append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, true));
                        appendGoto(falseBlock);
                        return;
                    } else if (isNeverExecutedCode(1 - probability)) {
                        append(new FixedGuardNode(condition, UnreachedCode, InvalidateReprofile, false));
                        appendGoto(trueBlock);
                        return;
                    }

                    int oldBci = stream.currentBCI();
                    int trueBlockInt = checkPositiveIntConstantPushed(trueBlock);
                    if (trueBlockInt != -1) {
                        int falseBlockInt = checkPositiveIntConstantPushed(falseBlock);
                        if (falseBlockInt != -1) {
                            if (tryGenConditionalForIf(trueBlock, falseBlock, condition, oldBci, trueBlockInt, falseBlockInt)) {
                                return;
                            }
                        }
                    }

                    this.controlFlowSplit = true;
                    FixedNode trueSuccessor = createTarget(trueBlock, frameState, false, false);
                    FixedNode falseSuccessor = createTarget(falseBlock, frameState, false, true);
                    ValueNode ifNode = genIfNode(condition, trueSuccessor, falseSuccessor, probability);
                    append(ifNode);
                    if (parsingReplacement()) {
                        if (x instanceof BranchProbabilityNode) {
                            ((BranchProbabilityNode) x).simplify(null);
                        } else if (y instanceof BranchProbabilityNode) {
                            ((BranchProbabilityNode) y).simplify(null);
                        }
                    }
                }
            }

            private boolean tryGenConditionalForIf(BciBlock trueBlock, BciBlock falseBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt) {
                if (gotoOrFallThroughAfterConstant(trueBlock) && gotoOrFallThroughAfterConstant(falseBlock) && trueBlock.getSuccessor(0) == falseBlock.getSuccessor(0)) {
                    genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, false);
                    return true;
                } else if (this.parent != null && returnAfterConstant(trueBlock) && returnAfterConstant(falseBlock)) {
                    genConditionalForIf(trueBlock, condition, oldBci, trueBlockInt, falseBlockInt, true);
                    return true;
                }
                return false;
            }

            private void genConditionalForIf(BciBlock trueBlock, LogicNode condition, int oldBci, int trueBlockInt, int falseBlockInt, boolean genReturn) {
                ConstantNode trueValue = graph.unique(ConstantNode.forInt(trueBlockInt));
                ConstantNode falseValue = graph.unique(ConstantNode.forInt(falseBlockInt));
                ValueNode conditionalNode = ConditionalNode.create(condition, trueValue, falseValue);
                if (conditionalNode.graph() == null) {
                    conditionalNode = graph.addOrUnique(conditionalNode);
                }
                if (genReturn) {
                    Kind returnKind = method.getSignature().getReturnKind().getStackKind();
                    this.genReturn(conditionalNode, returnKind);
                } else {
                    frameState.push(Kind.Int, conditionalNode);
                    appendGoto(trueBlock.getSuccessor(0));
                    stream.setBCI(oldBci);
                }
            }

            private LogicNode createLogicNode(Condition cond, ValueNode a, ValueNode b) {
                LogicNode condition;
                assert !a.getKind().isNumericFloat();
                if (cond == Condition.EQ || cond == Condition.NE) {
                    if (a.getKind() == Kind.Object) {
                        condition = genObjectEquals(a, b);
                    } else {
                        condition = genIntegerEquals(a, b);
                    }
                } else {
                    assert a.getKind() != Kind.Object && !cond.isUnsigned();
                    condition = genIntegerLessThan(a, b);
                }
                return condition;
            }

            private void genConstantTargetIf(BciBlock trueBlock, BciBlock falseBlock, boolean negate, LogicNode condition) {
                LogicConstantNode constantLogicNode = (LogicConstantNode) condition;
                boolean value = constantLogicNode.getValue();
                if (negate) {
                    value = !value;
                }
                BciBlock nextBlock = falseBlock;
                if (value) {
                    nextBlock = trueBlock;
                }
                appendGoto(nextBlock);
            }

            private int checkPositiveIntConstantPushed(BciBlock block) {
                stream.setBCI(block.startBci);
                int currentBC = stream.currentBC();
                if (currentBC >= Bytecodes.ICONST_0 && currentBC <= Bytecodes.ICONST_5) {
                    int constValue = currentBC - Bytecodes.ICONST_0;
                    return constValue;
                }
                return -1;
            }

            private boolean gotoOrFallThroughAfterConstant(BciBlock block) {
                stream.setBCI(block.startBci);
                int currentBCI = stream.nextBCI();
                stream.setBCI(currentBCI);
                int currentBC = stream.currentBC();
                return stream.currentBCI() > block.endBci || currentBC == Bytecodes.GOTO || currentBC == Bytecodes.GOTO_W;
            }

            private boolean returnAfterConstant(BciBlock block) {
                stream.setBCI(block.startBci);
                int currentBCI = stream.nextBCI();
                stream.setBCI(currentBCI);
                int currentBC = stream.currentBC();
                return currentBC == Bytecodes.IRETURN;
            }

            public StampProvider getStampProvider() {
                return stampProvider;
            }

            public MetaAccessProvider getMetaAccess() {
                return metaAccess;
            }

            public void push(Kind kind, ValueNode value) {
                assert value.isAlive();
                assert kind == kind.getStackKind();
                frameState.push(kind, value);
            }

            private int getCurrentDimension() {
                if (this.explodeLoopsContext == null || this.explodeLoopsContext.isEmpty()) {
                    return 0;
                } else {
                    return this.explodeLoopsContext.peek().peelIteration;
                }
            }

            public ConstantReflectionProvider getConstantReflection() {
                return constantReflection;
            }

            /**
             * Gets the graph being processed by this builder.
             */
            public StructuredGraph getGraph() {
                return graph;
            }

            public BytecodeParser getParent() {
                return parent;
            }

            public Replacement getReplacement() {
                return replacementContext;
            }

            @Override
            public String toString() {
                Formatter fmt = new Formatter();
                BytecodeParser bp = this;
                String indent = "";
                while (bp != null) {
                    if (bp != this) {
                        fmt.format("%n%s", indent);
                    }
                    fmt.format("%s [bci: %d, replacement: %s]", bp.method.asStackTraceElement(bp.bci()), bp.bci(), bp.parsingReplacement());
                    fmt.format("%n%s", new BytecodeDisassembler().disassemble(bp.method, bp.bci(), bp.bci() + 10));
                    bp = bp.parent;
                    indent += " ";
                }
                return fmt.toString();
            }

            public BailoutException bailout(String string) {
                FrameState currentFrameState = createFrameState(bci());
                StackTraceElement[] elements = GraphUtil.approxSourceStackTraceElement(currentFrameState);
                BailoutException bailout = new BailoutException(string);
                throw GraphUtil.createBailoutException(string, bailout, elements);
            }

            private FrameState createFrameState(int bci) {
                if (currentBlock != null && bci > currentBlock.endBci) {
                    frameState.clearNonLiveLocals(currentBlock, liveness, false);
                }
                return frameState.create(bci);
            }

            public FrameState createStateAfter() {
                return createFrameState(stream.nextBCI());
            }
        }
    }

    static String nSpaces(int n) {
        return n == 0 ? "" : format("%" + n + "s", "");
    }

    @SuppressWarnings("all")
    private static boolean assertionsEnabled() {
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        return assertionsEnabled;
    }

    /**
     * Remove loop header without loop ends. This can happen with degenerated loops like this one:
     *
     * <pre>
     * for (;;) {
     *     try {
     *         break;
     *     } catch (UnresolvedException iioe) {
     *     }
     * }
     * </pre>
     */
    public static void connectLoopEndToBegin(StructuredGraph graph) {
        for (LoopBeginNode begin : graph.getNodes(LoopBeginNode.TYPE)) {
            if (begin.loopEnds().isEmpty()) {
                assert begin.forwardEndCount() == 1;
                graph.reduceDegenerateLoopBegin(begin);
            } else {
                GraphUtil.normalizeLoopBegin(begin);
            }
        }
    }
}
