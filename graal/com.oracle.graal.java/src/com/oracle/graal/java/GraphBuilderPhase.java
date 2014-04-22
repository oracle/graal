/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.graal.compiler.common.GraalOptions.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Node.ValueNumberable;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.java.BciBlockMapping.LocalLiveness;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
public class GraphBuilderPhase extends BasePhase<HighTierContext> {

    private final GraphBuilderConfiguration graphBuilderConfig;

    public GraphBuilderPhase(GraphBuilderConfiguration graphBuilderConfig) {
        this.graphBuilderConfig = graphBuilderConfig;
    }

    @Override
    protected void run(StructuredGraph graph, HighTierContext context) {
        new Instance(context.getMetaAccess(), graphBuilderConfig, context.getOptimisticOptimizations()).run(graph);
    }

    public static class Instance extends Phase {

        private LineNumberTable lnt;
        private int previousLineNumber;
        private int currentLineNumber;

        protected StructuredGraph currentGraph;

        private final MetaAccessProvider metaAccess;

        private BytecodeParser parser;

        private ValueNode methodSynchronizedObject;
        private ExceptionDispatchBlock unwindBlock;

        private FixedWithNextNode lastInstr;                 // the last instruction added

        private final GraphBuilderConfiguration graphBuilderConfig;
        private final OptimisticOptimizations optimisticOpts;

        /**
         * Node that marks the begin of block during bytecode parsing. When a block is identified
         * the first time as a jump target, the placeholder is created and used as the successor for
         * the jump. When the block is seen the second time, a {@link MergeNode} is created to
         * correctly merge the now two different predecessor states.
         */
        protected static class BlockPlaceholderNode extends FixedWithNextNode {

            /*
             * Cannot be explicitly declared as a Node type since it is not an input; would cause
             * the !NODE_CLASS.isAssignableFrom(type) guarantee in
             * NodeClass.FieldScanner.scanField() to fail.
             */
            private final Object nextPlaceholder;

            public BlockPlaceholderNode(BytecodeParser builder) {
                super(StampFactory.forVoid());
                nextPlaceholder = builder.placeholders;
                builder.placeholders = this;
            }

            BlockPlaceholderNode nextPlaceholder() {
                return (BlockPlaceholderNode) nextPlaceholder;
            }
        }

        /**
         * Gets the current frame state being processed by this builder.
         */
        protected HIRFrameStateBuilder getCurrentFrameState() {
            return parser.getFrameState();
        }

        /**
         * Gets the graph being processed by this builder.
         */
        protected StructuredGraph getGraph() {
            return currentGraph;
        }

        public Instance(MetaAccessProvider metaAccess, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts) {
            this.graphBuilderConfig = graphBuilderConfig;
            this.optimisticOpts = optimisticOpts;
            this.metaAccess = metaAccess;
            assert metaAccess != null;
        }

        @Override
        protected void run(StructuredGraph graph) {
            ResolvedJavaMethod method = graph.method();
            if (graphBuilderConfig.eagerInfopointMode()) {
                lnt = method.getLineNumberTable();
                previousLineNumber = -1;
            }
            int entryBCI = graph.getEntryBCI();
            ProfilingInfo profilingInfo = method.getProfilingInfo();
            assert method.getCode() != null : "method must contain bytecodes: " + method;
            unwindBlock = null;
            methodSynchronizedObject = null;
            this.currentGraph = graph;
            HIRFrameStateBuilder frameState = new HIRFrameStateBuilder(method, graph, graphBuilderConfig.eagerResolving());
            this.parser = new BytecodeParser(metaAccess, method, graphBuilderConfig, optimisticOpts, frameState, new BytecodeStream(method.getCode()), profilingInfo, method.getConstantPool(),
                            entryBCI);
            TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
            try {
                parser.build();
            } finally {
                filter.remove();
            }
            parser = null;
        }

        @Override
        protected String getDetailedName() {
            return getName() + " " + MetaUtil.format("%H.%n(%p):%r", parser.getMethod());
        }

        public static class ExceptionInfo {

            public final FixedWithNextNode exceptionEdge;
            public final ValueNode exception;

            public ExceptionInfo(FixedWithNextNode exceptionEdge, ValueNode exception) {
                this.exceptionEdge = exceptionEdge;
                this.exception = exception;
            }
        }

        private static class Target {

            FixedNode fixed;
            HIRFrameStateBuilder state;

            public Target(FixedNode fixed, HIRFrameStateBuilder state) {
                this.fixed = fixed;
                this.state = state;
            }
        }

        class BytecodeParser extends AbstractBytecodeParser<ValueNode, HIRFrameStateBuilder> {

            private BciBlock[] loopHeaders;
            private LocalLiveness liveness;
            /**
             * Head of placeholder list.
             */
            public BlockPlaceholderNode placeholders;

            public BytecodeParser(MetaAccessProvider metaAccess, ResolvedJavaMethod method, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                            HIRFrameStateBuilder frameState, BytecodeStream stream, ProfilingInfo profilingInfo, ConstantPool constantPool, int entryBCI) {
                super(metaAccess, method, graphBuilderConfig, optimisticOpts, frameState, stream, profilingInfo, constantPool, entryBCI);
            }

            @Override
            protected void build() {
                if (PrintProfilingInformation.getValue()) {
                    TTY.println("Profiling info for " + MetaUtil.format("%H.%n(%p)", method));
                    TTY.println(MetaUtil.indent(MetaUtil.profileToString(profilingInfo, method, CodeUtil.NEW_LINE), "  "));
                }

                try (Indent indent = Debug.logAndIndent("build graph for %s", method)) {

                    // compute the block map, setup exception handlers and get the entrypoint(s)
                    BciBlockMapping blockMap = BciBlockMapping.create(method);
                    loopHeaders = blockMap.loopHeaders;
                    liveness = blockMap.liveness;

                    lastInstr = currentGraph.start();
                    if (isSynchronized(method.getModifiers())) {
                        // add a monitor enter to the start block
                        currentGraph.start().setStateAfter(frameState.create(FrameState.BEFORE_BCI));
                        methodSynchronizedObject = synchronizedObject(frameState, method);
                        lastInstr = genMonitorEnter(methodSynchronizedObject);
                    }
                    frameState.clearNonLiveLocals(blockMap.startBlock, liveness, true);
                    ((StateSplit) lastInstr).setStateAfter(frameState.create(0));
                    finishPrepare(lastInstr);

                    if (graphBuilderConfig.eagerInfopointMode()) {
                        InfopointNode ipn = currentGraph.add(new InfopointNode(InfopointReason.METHOD_START, frameState.create(0)));
                        lastInstr.setNext(ipn);
                        lastInstr = ipn;
                    }

                    currentBlock = blockMap.startBlock;
                    blockMap.startBlock.entryState = frameState;
                    if (blockMap.startBlock.isLoopHeader) {
                        /*
                         * TODO(lstadler,gduboscq) createTarget might not be safe at this position,
                         * since it expects currentBlock, etc. to be set up correctly. A better
                         * solution to this problem of start blocks that are loop headers would be
                         * to create a dummy block in BciBlockMapping.
                         */
                        appendGoto(createTarget(blockMap.startBlock, frameState));
                    } else {
                        blockMap.startBlock.firstInstruction = lastInstr;
                    }

                    for (BciBlock block : blockMap.blocks) {
                        processBlock(block);
                    }
                    processBlock(unwindBlock);

                    Debug.dump(currentGraph, "After bytecode parsing");

                    connectLoopEndToBegin();

                    // remove Placeholders
                    for (BlockPlaceholderNode n = placeholders; n != null; n = n.nextPlaceholder()) {
                        if (!n.isDeleted()) {
                            currentGraph.removeFixed(n);
                        }
                    }
                    placeholders = null;

                    // remove dead FrameStates
                    for (Node n : currentGraph.getNodes(FrameState.class)) {
                        if (n.usages().isEmpty() && n.predecessor() == null) {
                            n.safeDelete();
                        }
                    }
                }
            }

            /**
             * A hook for derived classes to modify the graph start instruction or append new
             * instructions to it.
             *
             * @param startInstr The start instruction of the graph.
             */
            protected void finishPrepare(FixedWithNextNode startInstr) {
            }

            private BciBlock unwindBlock() {
                if (unwindBlock == null) {
                    unwindBlock = new ExceptionDispatchBlock();
                    unwindBlock.startBci = -1;
                    unwindBlock.endBci = -1;
                    unwindBlock.deoptBci = FrameState.UNWIND_BCI;
                    unwindBlock.setId(Integer.MAX_VALUE);
                }
                return unwindBlock;
            }

            /**
             * @param type the unresolved type of the constant
             */
            @Override
            protected void handleUnresolvedLoadConstant(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                frameState.push(Kind.Object, appendConstant(Constant.NULL_OBJECT));
            }

            /**
             * @param type the unresolved type of the type check
             * @param object the object value whose type is being checked against {@code type}
             */
            @Override
            protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
                assert !graphBuilderConfig.eagerResolving();
                append(new FixedGuardNode(currentGraph.unique(new IsNullNode(object)), Unresolved, InvalidateRecompile));
                frameState.apush(appendConstant(Constant.NULL_OBJECT));
            }

            /**
             * @param type the unresolved type of the type check
             * @param object the object value whose type is being checked against {@code type}
             */
            @Override
            protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
                assert !graphBuilderConfig.eagerResolving();
                BlockPlaceholderNode successor = currentGraph.add(new BlockPlaceholderNode(this));
                DeoptimizeNode deopt = currentGraph.add(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                append(new IfNode(currentGraph.unique(new IsNullNode(object)), successor, deopt, 1));
                lastInstr = successor;
                frameState.ipush(appendConstant(Constant.INT_0));
            }

            /**
             * @param type the type being instantiated
             */
            @Override
            protected void handleUnresolvedNewInstance(JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                frameState.apush(appendConstant(Constant.NULL_OBJECT));
            }

            /**
             * @param type the type of the array being instantiated
             * @param length the length of the array
             */
            @Override
            protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                frameState.apush(appendConstant(Constant.NULL_OBJECT));
            }

            /**
             * @param type the type being instantiated
             * @param dims the dimensions for the multi-array
             */
            @Override
            protected void handleUnresolvedNewMultiArray(JavaType type, List<ValueNode> dims) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                frameState.apush(appendConstant(Constant.NULL_OBJECT));
            }

            /**
             * @param field the unresolved field
             * @param receiver the object containing the field or {@code null} if {@code field} is
             *            static
             */
            @Override
            protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
                assert !graphBuilderConfig.eagerResolving();
                Kind kind = field.getKind();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                frameState.push(kind.getStackKind(), appendConstant(Constant.defaultForKind(kind)));
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
             * @param representation
             * @param type
             */
            @Override
            protected void handleUnresolvedExceptionType(Representation representation, JavaType type) {
                assert !graphBuilderConfig.eagerResolving();
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
            }

            protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
                assert !graphBuilderConfig.eagerResolving();
                boolean withReceiver = invokeKind != InvokeKind.Static;
                append(new DeoptimizeNode(InvalidateRecompile, Unresolved));
                frameState.popArguments(javaMethod.getSignature().getParameterSlots(withReceiver), javaMethod.getSignature().getParameterCount(withReceiver));
                Kind kind = javaMethod.getSignature().getReturnKind();
                if (kind != Kind.Void) {
                    frameState.push(kind.getStackKind(), appendConstant(Constant.defaultForKind(kind)));
                }
            }

            private DispatchBeginNode handleException(ValueNode exceptionObject, int bci) {
                assert bci == FrameState.BEFORE_BCI || bci == bci() : "invalid bci";
                Debug.log("Creating exception dispatch edges at %d, exception object=%s, exception seen=%s", bci, exceptionObject, profilingInfo.getExceptionSeen(bci));

                BciBlock dispatchBlock = currentBlock.exceptionDispatchBlock();
                /*
                 * The exception dispatch block is always for the last bytecode of a block, so if we
                 * are not at the endBci yet, there is no exception handler for this bci and we can
                 * unwind immediately.
                 */
                if (bci != currentBlock.endBci || dispatchBlock == null) {
                    dispatchBlock = unwindBlock();
                }

                HIRFrameStateBuilder dispatchState = frameState.copy();
                dispatchState.clearStack();

                DispatchBeginNode dispatchBegin;
                if (exceptionObject == null) {
                    dispatchBegin = currentGraph.add(new ExceptionObjectNode(metaAccess));
                    dispatchState.apush(dispatchBegin);
                    dispatchState.setRethrowException(true);
                    dispatchBegin.setStateAfter(dispatchState.create(bci));
                } else {
                    dispatchBegin = currentGraph.add(new DispatchBeginNode());
                    dispatchBegin.setStateAfter(dispatchState.create(bci));
                    dispatchState.apush(exceptionObject);
                    dispatchState.setRethrowException(true);
                }
                FixedNode target = createTarget(dispatchBlock, dispatchState);
                finishInstruction(dispatchBegin, dispatchState).setNext(target);
                return dispatchBegin;
            }

            @Override
            protected ValueNode genLoadIndexed(ValueNode array, ValueNode index, Kind kind) {
                return new LoadIndexedNode(array, index, kind);
            }

            @Override
            protected ValueNode genStoreIndexed(ValueNode array, ValueNode index, Kind kind, ValueNode value) {
                return new StoreIndexedNode(array, index, kind, value);
            }

            @Override
            protected ValueNode genIntegerAdd(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerAddNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genIntegerSub(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerSubNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genIntegerMul(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerMulNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genFloatAdd(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new FloatAddNode(StampFactory.forKind(kind), x, y, isStrictFP);
            }

            @Override
            protected ValueNode genFloatSub(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new FloatSubNode(StampFactory.forKind(kind), x, y, isStrictFP);
            }

            @Override
            protected ValueNode genFloatMul(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new FloatMulNode(StampFactory.forKind(kind), x, y, isStrictFP);
            }

            @Override
            protected ValueNode genFloatDiv(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new FloatDivNode(StampFactory.forKind(kind), x, y, isStrictFP);
            }

            @Override
            protected ValueNode genFloatRem(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP) {
                return new FloatRemNode(StampFactory.forKind(kind), x, y, isStrictFP);
            }

            @Override
            protected ValueNode genIntegerDiv(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerDivNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genIntegerRem(Kind kind, ValueNode x, ValueNode y) {
                return new IntegerRemNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genNegateOp(ValueNode x) {
                return (new NegateNode(x));
            }

            @Override
            protected ValueNode genLeftShift(Kind kind, ValueNode x, ValueNode y) {
                return new LeftShiftNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genRightShift(Kind kind, ValueNode x, ValueNode y) {
                return new RightShiftNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genUnsignedRightShift(Kind kind, ValueNode x, ValueNode y) {
                return new UnsignedRightShiftNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genAnd(Kind kind, ValueNode x, ValueNode y) {
                return new AndNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genOr(Kind kind, ValueNode x, ValueNode y) {
                return new OrNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genXor(Kind kind, ValueNode x, ValueNode y) {
                return new XorNode(StampFactory.forKind(kind), x, y);
            }

            @Override
            protected ValueNode genNormalizeCompare(ValueNode x, ValueNode y, boolean isUnorderedLess) {
                return new NormalizeCompareNode(x, y, isUnorderedLess);
            }

            @Override
            protected ValueNode genFloatConvert(FloatConvert op, ValueNode input) {
                return new FloatConvertNode(op, input);
            }

            @Override
            protected ValueNode genNarrow(ValueNode input, int bitCount) {
                return new NarrowNode(input, bitCount);
            }

            @Override
            protected ValueNode genSignExtend(ValueNode input, int bitCount) {
                return new SignExtendNode(input, bitCount);
            }

            @Override
            protected ValueNode genZeroExtend(ValueNode input, int bitCount) {
                return new ZeroExtendNode(input, bitCount);
            }

            @Override
            protected void genGoto() {
                appendGoto(createTarget(currentBlock.getSuccessors().get(0), frameState));
                assert currentBlock.numNormalSuccessors() == 1;
            }

            @Override
            protected ValueNode genObjectEquals(ValueNode x, ValueNode y) {
                return new ObjectEqualsNode(x, y);
            }

            @Override
            protected ValueNode genIntegerEquals(ValueNode x, ValueNode y) {
                return new IntegerEqualsNode(x, y);
            }

            @Override
            protected ValueNode genIntegerLessThan(ValueNode x, ValueNode y) {
                return new IntegerLessThanNode(x, y);
            }

            @Override
            protected ValueNode genUnique(ValueNode x) {
                return (ValueNode) currentGraph.unique((Node & ValueNumberable) x);
            }

            protected ValueNode genIfNode(ValueNode condition, ValueNode falseSuccessor, ValueNode trueSuccessor, double d) {
                return new IfNode((LogicNode) condition, (FixedNode) falseSuccessor, (FixedNode) trueSuccessor, d);
            }

            @Override
            protected void genThrow() {
                ValueNode exception = frameState.apop();
                append(new FixedGuardNode(currentGraph.unique(new IsNullNode(exception)), NullCheckException, InvalidateReprofile, true));
                lastInstr.setNext(handleException(exception, bci()));
            }

            @Override
            protected ValueNode genCheckCast(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck, boolean b) {
                return new CheckCastNode(type, object, profileForTypeCheck, b);
            }

            @Override
            protected ValueNode genInstanceOf(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck) {
                return new InstanceOfNode(type, object, profileForTypeCheck);
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
            protected void emitNullCheck(ValueNode receiver) {
                if (ObjectStamp.isObjectNonNull(receiver.stamp())) {
                    return;
                }
                BlockPlaceholderNode trueSucc = currentGraph.add(new BlockPlaceholderNode(this));
                BlockPlaceholderNode falseSucc = currentGraph.add(new BlockPlaceholderNode(this));
                append(new IfNode(currentGraph.unique(new IsNullNode(receiver)), trueSucc, falseSucc, 0.01));
                lastInstr = falseSucc;

                BytecodeExceptionNode exception = currentGraph.add(new BytecodeExceptionNode(metaAccess, NullPointerException.class));
                exception.setStateAfter(frameState.create(bci()));
                trueSucc.setNext(exception);
                exception.setNext(handleException(exception, bci()));
            }

            @Override
            protected void emitBoundsCheck(ValueNode index, ValueNode length) {
                BlockPlaceholderNode trueSucc = currentGraph.add(new BlockPlaceholderNode(this));
                BlockPlaceholderNode falseSucc = currentGraph.add(new BlockPlaceholderNode(this));
                append(new IfNode(currentGraph.unique(new IntegerBelowThanNode(index, length)), trueSucc, falseSucc, 0.99));
                lastInstr = trueSucc;

                BytecodeExceptionNode exception = currentGraph.add(new BytecodeExceptionNode(metaAccess, ArrayIndexOutOfBoundsException.class, index));
                exception.setStateAfter(frameState.create(bci()));
                falseSucc.setNext(exception);
                exception.setNext(handleException(exception, bci()));
            }

            @Override
            protected ValueNode genArrayLength(ValueNode x) {
                return new ArrayLengthNode(x);
            }

            @Override
            protected ValueNode genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value) {
                return new StoreFieldNode(receiver, field, value);
            }

            @Override
            protected void genInvokeStatic(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    ResolvedJavaMethod resolvedTarget = (ResolvedJavaMethod) target;
                    ResolvedJavaType holder = resolvedTarget.getDeclaringClass();
                    if (!holder.isInitialized() && ResolveClassBeforeStaticInvoke.getValue()) {
                        handleUnresolvedInvoke(target, InvokeKind.Static);
                    } else {
                        ValueNode[] args = frameState.popArguments(resolvedTarget.getSignature().getParameterSlots(false), resolvedTarget.getSignature().getParameterCount(false));
                        appendInvoke(InvokeKind.Static, resolvedTarget, args);
                    }
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Static);
                }
            }

            @Override
            protected void genInvokeInterface(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterSlots(true), target.getSignature().getParameterCount(true));
                    appendInvoke(InvokeKind.Interface, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Interface);
                }
            }

            @Override
            protected void genInvokeDynamic(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    Constant appendix = constantPool.lookupAppendix(stream.readCPI4(), Bytecodes.INVOKEDYNAMIC);
                    if (appendix != null) {
                        frameState.apush(ConstantNode.forConstant(appendix, metaAccess, currentGraph));
                    }
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterSlots(false), target.getSignature().getParameterCount(false));
                    appendInvoke(InvokeKind.Static, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Static);
                }
            }

            @Override
            protected void genInvokeVirtual(JavaMethod target) {
                if (target instanceof ResolvedJavaMethod) {
                    /*
                     * Special handling for runtimes that rewrite an invocation of
                     * MethodHandle.invoke(...) or MethodHandle.invokeExact(...) to a static
                     * adapter. HotSpot does this - see
                     * https://wikis.oracle.com/display/HotSpotInternals/Method+handles
                     * +and+invokedynamic
                     */
                    boolean hasReceiver = !isStatic(((ResolvedJavaMethod) target).getModifiers());
                    Constant appendix = constantPool.lookupAppendix(stream.readCPI(), Bytecodes.INVOKEVIRTUAL);
                    if (appendix != null) {
                        frameState.apush(ConstantNode.forConstant(appendix, metaAccess, currentGraph));
                    }
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterSlots(hasReceiver), target.getSignature().getParameterCount(hasReceiver));
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
                if (target instanceof ResolvedJavaMethod) {
                    assert target != null;
                    assert target.getSignature() != null;
                    ValueNode[] args = frameState.popArguments(target.getSignature().getParameterSlots(true), target.getSignature().getParameterCount(true));
                    appendInvoke(InvokeKind.Special, (ResolvedJavaMethod) target, args);
                } else {
                    handleUnresolvedInvoke(target, InvokeKind.Special);
                }
            }

            private void appendInvoke(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args) {
                Kind resultType = targetMethod.getSignature().getReturnKind();
                if (DeoptALot.getValue()) {
                    append(new DeoptimizeNode(DeoptimizationAction.None, RuntimeConstraint));
                    frameState.pushReturn(resultType, ConstantNode.defaultForKind(resultType, currentGraph));
                    return;
                }

                JavaType returnType = targetMethod.getSignature().getReturnType(method.getDeclaringClass());
                if (graphBuilderConfig.eagerResolving()) {
                    returnType = returnType.resolve(targetMethod.getDeclaringClass());
                }
                if (invokeKind != InvokeKind.Static) {
                    emitExplicitExceptions(args[0], null);
                    if (invokeKind != InvokeKind.Special && this.optimisticOpts.useTypeCheckHints()) {
                        JavaTypeProfile profile = profilingInfo.getTypeProfile(bci());
                        args[0] = TypeProfileProxyNode.create(args[0], profile);
                    }
                }
                MethodCallTargetNode callTarget = currentGraph.add(createMethodCallTarget(invokeKind, targetMethod, args, returnType));

                // be conservative if information was not recorded (could result in endless
                // recompiles
                // otherwise)
                if (graphBuilderConfig.omitAllExceptionEdges() || (optimisticOpts.useExceptionProbability() && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE)) {
                    createInvoke(callTarget, resultType);
                } else {
                    assert bci() == currentBlock.endBci;
                    frameState.clearNonLiveLocals(currentBlock, liveness, false);

                    InvokeWithExceptionNode invoke = createInvokeWithException(callTarget, resultType);

                    BciBlock nextBlock = currentBlock.getSuccessor(0);
                    invoke.setNext(createTarget(nextBlock, frameState));
                }
            }

            protected MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, JavaType returnType) {
                return new MethodCallTargetNode(invokeKind, targetMethod, args, returnType);
            }

            protected InvokeNode createInvoke(CallTargetNode callTarget, Kind resultType) {
                InvokeNode invoke = append(new InvokeNode(callTarget, bci()));
                frameState.pushReturn(resultType, invoke);
                return invoke;
            }

            protected InvokeWithExceptionNode createInvokeWithException(CallTargetNode callTarget, Kind resultType) {
                DispatchBeginNode exceptionEdge = handleException(null, bci());
                InvokeWithExceptionNode invoke = append(new InvokeWithExceptionNode(callTarget, exceptionEdge, bci()));
                frameState.pushReturn(resultType, invoke);
                BciBlock nextBlock = currentBlock.getSuccessor(0);
                invoke.setStateAfter(frameState.create(nextBlock.startBci));
                return invoke;
            }

            @Override
            protected void genReturn(ValueNode x) {
                frameState.setRethrowException(false);
                frameState.clearStack();
                if (graphBuilderConfig.eagerInfopointMode()) {
                    append(new InfopointNode(InfopointReason.METHOD_END, frameState.create(bci())));
                }

                synchronizedEpilogue(FrameState.AFTER_BCI, x);
                if (frameState.lockDepth() != 0) {
                    throw new BailoutException("unbalanced monitors");
                }

                append(new ReturnNode(x));
            }

            @Override
            protected MonitorEnterNode genMonitorEnter(ValueNode x) {
                MonitorIdNode monitorId = currentGraph.add(new MonitorIdNode(frameState.lockDepth()));
                MonitorEnterNode monitorEnter = append(new MonitorEnterNode(x, monitorId));
                frameState.pushLock(x, monitorId);
                return monitorEnter;
            }

            @Override
            protected MonitorExitNode genMonitorExit(ValueNode x, ValueNode returnValue) {
                MonitorIdNode monitorId = frameState.peekMonitorId();
                ValueNode lockedObject = frameState.popLock();
                if (GraphUtil.originalValue(lockedObject) != GraphUtil.originalValue(x)) {
                    throw new BailoutException("unbalanced monitors: mismatch at monitorexit, %s != %s", GraphUtil.originalValue(x), GraphUtil.originalValue(lockedObject));
                }
                MonitorExitNode monitorExit = append(new MonitorExitNode(x, monitorId, returnValue));
                return monitorExit;
            }

            @Override
            protected void genJsr(int dest) {
                BciBlock successor = currentBlock.jsrSuccessor;
                assert successor.startBci == dest : successor.startBci + " != " + dest + " @" + bci();
                JsrScope scope = currentBlock.jsrScope;
                if (!successor.jsrScope.pop().equals(scope)) {
                    throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
                }
                if (successor.jsrScope.nextReturnAddress() != getStream().nextBCI()) {
                    throw new JsrNotSupportedBailout("unstructured control flow (internal limitation)");
                }
                frameState.push(Kind.Int, ConstantNode.forInt(getStream().nextBCI(), currentGraph));
                appendGoto(createTarget(successor, frameState));
            }

            @Override
            protected void genRet(int localIndex) {
                BciBlock successor = currentBlock.retSuccessor;
                ValueNode local = frameState.loadLocal(localIndex);
                JsrScope scope = currentBlock.jsrScope;
                int retAddress = scope.nextReturnAddress();
                append(new FixedGuardNode(currentGraph.unique(new IntegerEqualsNode(local, ConstantNode.forInt(retAddress, currentGraph))), JavaSubroutineMismatch, InvalidateReprofile));
                if (!successor.jsrScope.equals(scope.pop())) {
                    throw new JsrNotSupportedBailout("unstructured control flow (ret leaves more than one scope)");
                }
                appendGoto(createTarget(successor, frameState));
            }

            @Override
            protected void genIntegerSwitch(ValueNode value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors) {
                double[] successorProbabilities = successorProbabilites(actualSuccessors.size(), keySuccessors, keyProbabilities);
                IntegerSwitchNode switchNode = append(new IntegerSwitchNode(value, actualSuccessors.size(), keys, keyProbabilities, keySuccessors));
                for (int i = 0; i < actualSuccessors.size(); i++) {
                    switchNode.setBlockSuccessor(i, createBlockTarget(successorProbabilities[i], actualSuccessors.get(i), frameState));
                }
            }

            @Override
            protected ConstantNode appendConstant(Constant constant) {
                assert constant != null;
                return ConstantNode.forConstant(constant, metaAccess, currentGraph);
            }

            @Override
            protected ValueNode append(ValueNode v) {
                if (v instanceof ControlSinkNode) {
                    return append((ControlSinkNode) v);
                }
                if (v instanceof ControlSplitNode) {
                    return append((ControlSplitNode) v);
                }
                if (v instanceof FixedWithNextNode) {
                    return append((FixedWithNextNode) v);
                }
                if (v instanceof FloatingNode) {
                    return append((FloatingNode) v);
                }
                throw GraalInternalError.shouldNotReachHere("Can not append Node of type: " + v.getClass().getName());
            }

            private <T extends ControlSinkNode> T append(T fixed) {
                assert !fixed.isAlive() && !fixed.isDeleted() : "instruction should not have been appended yet";
                assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
                T added = currentGraph.add(fixed);
                lastInstr.setNext(added);
                lastInstr = null;
                return added;
            }

            private <T extends ControlSplitNode> T append(T fixed) {
                assert !fixed.isAlive() && !fixed.isDeleted() : "instruction should not have been appended yet";
                assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
                T added = currentGraph.add(fixed);
                lastInstr.setNext(added);
                lastInstr = null;
                return added;
            }

            protected <T extends FixedWithNextNode> T append(T fixed) {
                assert !fixed.isAlive() && !fixed.isDeleted() : "instruction should not have been appended yet";
                assert lastInstr.next() == null : "cannot append instruction to instruction which isn't end (" + lastInstr + "->" + lastInstr.next() + ")";
                T added = currentGraph.add(fixed);
                lastInstr.setNext(added);
                lastInstr = added;
                return added;
            }

            private <T extends FloatingNode> T append(T v) {
                assert !(v instanceof ConstantNode);
                T added = currentGraph.unique(v);
                return added;
            }

            private Target checkLoopExit(FixedNode target, BciBlock targetBlock, HIRFrameStateBuilder state) {
                if (currentBlock != null) {
                    long exits = currentBlock.loops & ~targetBlock.loops;
                    if (exits != 0) {
                        LoopExitNode firstLoopExit = null;
                        LoopExitNode lastLoopExit = null;

                        int pos = 0;
                        ArrayList<BciBlock> exitLoops = new ArrayList<>(Long.bitCount(exits));
                        do {
                            long lMask = 1L << pos;
                            if ((exits & lMask) != 0) {
                                exitLoops.add(loopHeaders[pos]);
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
                            LoopBeginNode loopBegin = (LoopBeginNode) loop.firstInstruction;
                            LoopExitNode loopExit = currentGraph.add(new LoopExitNode(loopBegin));
                            if (lastLoopExit != null) {
                                lastLoopExit.setNext(loopExit);
                            }
                            if (firstLoopExit == null) {
                                firstLoopExit = loopExit;
                            }
                            lastLoopExit = loopExit;
                            Debug.log("Target %s (%s) Exits %s, scanning framestates...", targetBlock, target, loop);
                            newState.insertLoopProxies(loopExit, (HIRFrameStateBuilder) loop.entryState);
                            loopExit.setStateAfter(newState.create(bci));
                        }

                        lastLoopExit.setNext(target);
                        return new Target(firstLoopExit, newState);
                    }
                }
                return new Target(target, state);
            }

            private FixedNode createTarget(double probability, BciBlock block, HIRFrameStateBuilder stateAfter) {
                assert probability >= 0 && probability <= 1.01 : probability;
                if (isNeverExecutedCode(probability)) {
                    return currentGraph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                } else {
                    assert block != null;
                    return createTarget(block, stateAfter);
                }
            }

            private FixedNode createTarget(BciBlock block, HIRFrameStateBuilder state) {
                assert block != null && state != null;
                assert !block.isExceptionEntry || state.stackSize() == 1;

                if (block.firstInstruction == null) {
                    /*
                     * This is the first time we see this block as a branch target. Create and
                     * return a placeholder that later can be replaced with a MergeNode when we see
                     * this block again.
                     */
                    block.firstInstruction = currentGraph.add(new BlockPlaceholderNode(this));
                    Target target = checkLoopExit(block.firstInstruction, block, state);
                    FixedNode result = target.fixed;
                    block.entryState = target.state == state ? state.copy() : target.state;
                    block.entryState.clearNonLiveLocals(block, liveness, true);

                    Debug.log("createTarget %s: first visit, result: %s", block, block.firstInstruction);
                    return result;
                }

                // We already saw this block before, so we have to merge states.
                if (!((HIRFrameStateBuilder) block.entryState).isCompatibleWith(state)) {
                    throw new BailoutException("stacks do not match; bytecodes would not verify");
                }

                if (block.firstInstruction instanceof LoopBeginNode) {
                    assert block.isLoopHeader && currentBlock.getId() >= block.getId() : "must be backward branch";
                    /*
                     * Backward loop edge. We need to create a special LoopEndNode and merge with
                     * the loop begin node created before.
                     */
                    LoopBeginNode loopBegin = (LoopBeginNode) block.firstInstruction;
                    Target target = checkLoopExit(currentGraph.add(new LoopEndNode(loopBegin)), block, state);
                    FixedNode result = target.fixed;
                    ((HIRFrameStateBuilder) block.entryState).merge(loopBegin, target.state);

                    Debug.log("createTarget %s: merging backward branch to loop header %s, result: %s", block, loopBegin, result);
                    return result;
                }
                assert currentBlock == null || currentBlock.getId() < block.getId() : "must not be backward branch";
                assert block.firstInstruction.next() == null : "bytecodes already parsed for block";

                if (block.firstInstruction instanceof BlockPlaceholderNode) {
                    /*
                     * This is the second time we see this block. Create the actual MergeNode and
                     * the End Node for the already existing edge. For simplicity, we leave the
                     * placeholder in the graph and just append the new nodes after the placeholder.
                     */
                    BlockPlaceholderNode placeholder = (BlockPlaceholderNode) block.firstInstruction;

                    // The EndNode for the already existing edge.
                    AbstractEndNode end = currentGraph.add(new EndNode());
                    // The MergeNode that replaces the placeholder.
                    MergeNode mergeNode = currentGraph.add(new MergeNode());
                    FixedNode next = placeholder.next();

                    placeholder.setNext(end);
                    mergeNode.addForwardEnd(end);
                    mergeNode.setNext(next);

                    block.firstInstruction = mergeNode;
                }

                MergeNode mergeNode = (MergeNode) block.firstInstruction;

                // The EndNode for the newly merged edge.
                AbstractEndNode newEnd = currentGraph.add(new EndNode());
                Target target = checkLoopExit(newEnd, block, state);
                FixedNode result = target.fixed;
                ((HIRFrameStateBuilder) block.entryState).merge(mergeNode, target.state);
                mergeNode.addForwardEnd(newEnd);

                Debug.log("createTarget %s: merging state, result: %s", block, result);
                return result;
            }

            /**
             * Returns a block begin node with the specified state. If the specified probability is
             * 0, the block deoptimizes immediately.
             */
            private BeginNode createBlockTarget(double probability, BciBlock block, HIRFrameStateBuilder stateAfter) {
                FixedNode target = createTarget(probability, block, stateAfter);
                BeginNode begin = BeginNode.begin(target);

                assert !(target instanceof DeoptimizeNode && begin instanceof BeginStateSplitNode && ((BeginStateSplitNode) begin).stateAfter() != null) : "We are not allowed to set the stateAfter of the begin node, because we have to deoptimize "
                                + "to a bci _before_ the actual if, so that the interpreter can update the profiling information.";
                return begin;
            }

            private ValueNode synchronizedObject(HIRFrameStateBuilder state, ResolvedJavaMethod target) {
                if (isStatic(target.getModifiers())) {
                    return appendConstant(target.getDeclaringClass().getEncoding(Representation.JavaClass));
                } else {
                    return state.loadLocal(0);
                }
            }

            @Override
            protected void processBlock(BciBlock block) {
                // Ignore blocks that have no predecessors by the time their bytecodes are parsed
                if (block == null || block.firstInstruction == null) {
                    Debug.log("Ignoring block %s", block);
                    return;
                }
                try (Indent indent = Debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, block.firstInstruction, block.isLoopHeader)) {

                    lastInstr = block.firstInstruction;
                    frameState = (HIRFrameStateBuilder) block.entryState;
                    parser.setCurrentFrameState(frameState);
                    currentBlock = block;

                    frameState.cleanupDeletedPhis();
                    if (lastInstr instanceof MergeNode) {
                        int bci = block.startBci;
                        if (block instanceof ExceptionDispatchBlock) {
                            bci = ((ExceptionDispatchBlock) block).deoptBci;
                        }
                        ((MergeNode) lastInstr).setStateAfter(frameState.create(bci));
                    }

                    if (block == unwindBlock) {
                        frameState.setRethrowException(false);
                        createUnwind();
                    } else if (block instanceof ExceptionDispatchBlock) {
                        createExceptionDispatch((ExceptionDispatchBlock) block);
                    } else {
                        frameState.setRethrowException(false);
                        iterateBytecodesForBlock(block);
                    }
                }
            }

            private void connectLoopEndToBegin() {
                for (LoopBeginNode begin : currentGraph.getNodes(LoopBeginNode.class)) {
                    if (begin.loopEnds().isEmpty()) {
                        // @formatter:off
                // Remove loop header without loop ends.
                // This can happen with degenerated loops like this one:
                // for (;;) {
                //     try {
                //         break;
                //     } catch (UnresolvedException iioe) {
                //     }
                // }
                // @formatter:on
                        assert begin.forwardEndCount() == 1;
                        currentGraph.reduceDegenerateLoopBegin(begin);
                    } else {
                        GraphUtil.normalizeLoopBegin(begin);
                    }
                }
            }

            private void createUnwind() {
                assert frameState.stackSize() == 1 : frameState;
                ValueNode exception = frameState.apop();
                append(new FixedGuardNode(currentGraph.unique(new IsNullNode(exception)), NullCheckException, InvalidateReprofile, true));
                synchronizedEpilogue(FrameState.AFTER_EXCEPTION_BCI, null);
                append(new UnwindNode(exception));
            }

            private void synchronizedEpilogue(int bci, ValueNode returnValue) {
                if (Modifier.isSynchronized(method.getModifiers())) {
                    MonitorExitNode monitorExit = genMonitorExit(methodSynchronizedObject, returnValue);
                    if (returnValue != null) {
                        frameState.push(returnValue.getKind(), returnValue);
                    }
                    monitorExit.setStateAfter(frameState.create(bci));
                    assert !frameState.rethrowException();
                }
            }

            private void createExceptionDispatch(ExceptionDispatchBlock block) {
                assert frameState.stackSize() == 1 : frameState;
                if (block.handler.isCatchAll()) {
                    assert block.getSuccessorCount() == 1;
                    appendGoto(createTarget(block.getSuccessor(0), frameState));
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
                            BciBlock nextBlock = block.getSuccessorCount() == 1 ? unwindBlock() : block.getSuccessor(1);
                            ValueNode exception = frameState.stackAt(0);
                            FixedNode trueSuccessor = currentGraph.add(new DeoptimizeNode(InvalidateReprofile, UnreachedCode));
                            FixedNode nextDispatch = createTarget(nextBlock, frameState);
                            append(new IfNode(currentGraph.unique(new InstanceOfNode((ResolvedJavaType) catchType, exception, null)), trueSuccessor, nextDispatch, 0));
                            return;
                        }
                    }
                }

                if (initialized) {
                    BciBlock nextBlock = block.getSuccessorCount() == 1 ? unwindBlock() : block.getSuccessor(1);
                    ValueNode exception = frameState.stackAt(0);
                    CheckCastNode checkCast = currentGraph.add(new CheckCastNode((ResolvedJavaType) catchType, exception, null, false));
                    frameState.apop();
                    frameState.push(Kind.Object, checkCast);
                    FixedNode catchSuccessor = createTarget(block.getSuccessor(0), frameState);
                    frameState.apop();
                    frameState.push(Kind.Object, exception);
                    FixedNode nextDispatch = createTarget(nextBlock, frameState);
                    checkCast.setNext(catchSuccessor);
                    append(new IfNode(currentGraph.unique(new InstanceOfNode((ResolvedJavaType) catchType, exception, null)), checkCast, nextDispatch, 0.5));
                } else {
                    handleUnresolvedExceptionType(Representation.ObjectHub, catchType);
                }
            }

            private void appendGoto(FixedNode target) {
                if (lastInstr != null) {
                    lastInstr.setNext(target);
                }
            }

            private boolean isBlockEnd(Node n) {
                return n instanceof ControlSplitNode || n instanceof ControlSinkNode;
            }

            @Override
            protected void iterateBytecodesForBlock(BciBlock block) {
                if (block.isLoopHeader) {
                    // Create the loop header block, which later will merge the backward branches of
                    // the
                    // loop.
                    AbstractEndNode preLoopEnd = currentGraph.add(new EndNode());
                    LoopBeginNode loopBegin = currentGraph.add(new LoopBeginNode());
                    lastInstr.setNext(preLoopEnd);
                    // Add the single non-loop predecessor of the loop header.
                    loopBegin.addForwardEnd(preLoopEnd);
                    lastInstr = loopBegin;

                    // Create phi functions for all local variables and operand stack slots.
                    frameState.insertLoopPhis(loopBegin);
                    loopBegin.setStateAfter(frameState.create(block.startBci));

                    /*
                     * We have seen all forward branches. All subsequent backward branches will
                     * merge to the loop header. This ensures that the loop header has exactly one
                     * non-loop predecessor.
                     */
                    block.firstInstruction = loopBegin;
                    /*
                     * We need to preserve the frame state builder of the loop header so that we can
                     * merge values for phi functions, so make a copy of it.
                     */
                    block.entryState = frameState.copy();

                    Debug.log("  created loop header %s", loopBegin);
                }
                assert lastInstr.next() == null : "instructions already appended at block " + block;
                Debug.log("  frameState: %s", frameState);

                lastInstr = finishInstruction(lastInstr, frameState);

                int endBCI = stream.endBCI();

                stream.setBCI(block.startBci);
                int bci = block.startBci;
                BytecodesParsed.add(block.endBci - bci);

                while (bci < endBCI) {
                    if (graphBuilderConfig.eagerInfopointMode() && lnt != null) {
                        currentLineNumber = lnt.getLineNumber(bci);
                        if (currentLineNumber != previousLineNumber) {
                            append(new InfopointNode(InfopointReason.LINE_NUMBER, frameState.create(bci)));
                            previousLineNumber = currentLineNumber;
                        }
                    }

                    // read the opcode
                    int opcode = stream.currentBC();
                    traceState();
                    traceInstruction(bci, opcode, bci == block.startBci);
                    if (bci == entryBCI) {
                        if (block.jsrScope != JsrScope.EMPTY_SCOPE) {
                            throw new BailoutException("OSR into a JSR scope is not supported");
                        }
                        EntryMarkerNode x = append(new EntryMarkerNode());
                        frameState.insertProxies(x);
                        x.setStateAfter(frameState.create(bci));
                    }
                    processBytecode(bci, opcode);

                    if (lastInstr == null || isBlockEnd(lastInstr) || lastInstr.next() != null) {
                        break;
                    }

                    stream.next();
                    bci = stream.currentBCI();

                    if (bci > block.endBci) {
                        frameState.clearNonLiveLocals(currentBlock, liveness, false);
                    }
                    if (lastInstr instanceof StateSplit) {
                        if (lastInstr.getClass() == BeginNode.class) {
                            // BeginNodes do not need a frame state
                        } else {
                            StateSplit stateSplit = (StateSplit) lastInstr;
                            if (stateSplit.stateAfter() == null) {
                                stateSplit.setStateAfter(frameState.create(bci));
                            }
                        }
                    }
                    lastInstr = finishInstruction(lastInstr, frameState);
                    if (bci < endBCI) {
                        if (bci > block.endBci) {
                            assert !block.getSuccessor(0).isExceptionEntry;
                            assert block.numNormalSuccessors() == 1;
                            // we fell through to the next block, add a goto and break
                            appendGoto(createTarget(block.getSuccessor(0), frameState));
                            break;
                        }
                    }
                }
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

            private void traceState() {
                if (traceLevel >= TRACELEVEL_STATE && Debug.isLogEnabled()) {
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
            }

            @Override
            protected void genIf(ValueNode x, Condition cond, ValueNode y) {
                // assert !x.isDeleted() && !y.isDeleted();
                // assert currentBlock.numNormalSuccessors() == 2;
                assert currentBlock.getSuccessors().size() == 2;
                BciBlock trueBlock = currentBlock.getSuccessors().get(0);
                BciBlock falseBlock = currentBlock.getSuccessors().get(1);
                if (trueBlock == falseBlock) {
                    appendGoto(createTarget(trueBlock, frameState));
                    return;
                }

                double probability = profilingInfo.getBranchTakenProbability(bci());
                if (probability < 0) {
                    assert probability == -1 : "invalid probability";
                    Debug.log("missing probability in %s at bci %d", method, bci());
                    probability = 0.5;
                }

                if (!optimisticOpts.removeNeverExecutedCode()) {
                    if (probability == 0) {
                        probability = 0.0000001;
                    } else if (probability == 1) {
                        probability = 0.999999;
                    }
                }

                // the mirroring and negation operations get the condition into canonical form
                boolean mirror = cond.canonicalMirror();
                boolean negate = cond.canonicalNegate();

                ValueNode a = mirror ? y : x;
                ValueNode b = mirror ? x : y;

                ValueNode condition;
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
                condition = genUnique(condition);

                ValueNode trueSuccessor = createBlockTarget(probability, trueBlock, frameState);
                ValueNode falseSuccessor = createBlockTarget(1 - probability, falseBlock, frameState);

                ValueNode ifNode = negate ? genIfNode(condition, falseSuccessor, trueSuccessor, 1 - probability) : genIfNode(condition, trueSuccessor, falseSuccessor, probability);
                append(ifNode);
            }

        }
    }
}
