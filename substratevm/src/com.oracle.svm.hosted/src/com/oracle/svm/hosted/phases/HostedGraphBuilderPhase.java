/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.FrameStateBuilder;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.GuardsStage;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.results.StaticAnalysisResults;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.hosted.phases.SubstrateGraphBuilderPhase.SubstrateBytecodeParser;

import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class HostedGraphBuilderPhase extends SubstrateGraphBuilderPhase {

    public HostedGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext,
                    WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext, wordTypes);
    }

    @Override
    protected BytecodeParser createBytecodeParser(StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        return new HostedBytecodeParser(this, graph, parent, method, entryBCI, intrinsicContext);
    }
}

class HostedBytecodeParser extends SubstrateBytecodeParser {

    private int currentDeoptIndex;

    HostedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI, IntrinsicContext intrinsicContext) {
        super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, true);
    }

    @Override
    public HostedMethod getMethod() {
        return (HostedMethod) super.getMethod();
    }

    @Override
    protected boolean forceLoopPhis() {
        return getMethod().compilationInfo.isDeoptTarget() || super.forceLoopPhis();
    }

    @Override
    protected boolean stampFromValueForForcedPhis() {
        return true;
    }

    @Override
    protected BciBlockMapping generateBlockMap() {
        if (isDeoptimizationEnabled() && isMethodDeoptTarget()) {
            /*
             * Need to add blocks representing where deoptimization entrypoint nodes will be
             * inserted.
             */
            return HostedBciBlockMapping.create(stream, code, options, graph.getDebug(), false);
        } else {
            return BciBlockMapping.create(stream, code, options, graph.getDebug(), asyncExceptionLiveness());
        }
    }

    @Override
    protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
        super.build(startInstruction, startFrameState);

        /* We never have floating guards in AOT compiled code. */
        getGraph().setGuardsStage(GuardsStage.FIXED_DEOPTS);

        assert !getMethod().isEntryPoint() : "Cannot directly use as entry point, create a call stub ";

        if (getMethod().compilationInfo.isDeoptTarget()) {
            /*
             * All DeoptProxyNodes should be valid.
             */
            for (DeoptProxyNode deoptProxy : graph.getNodes(DeoptProxyNode.TYPE)) {
                assert deoptProxy.hasProxyPoint();
            }
        }
    }

    @Override
    public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
        StaticAnalysisResults staticAnalysisResults = getMethod().getProfilingInfo();
        return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp, staticAnalysisResults.getTypeProfile(bci()), staticAnalysisResults.getMethodProfile(bci()));
    }

    @Override
    protected void createExceptionDispatch(BciBlockMapping.ExceptionDispatchBlock block) {
        if (block instanceof HostedBciBlockMapping.DeoptEntryInsertionPoint) {
            /*
             * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
             * Afterwards, this block should jump to either the original ExceptionDispatchBlock or
             * the UnwindBlock if there is no handler.
             */
            assert block instanceof HostedBciBlockMapping.DeoptExceptionDispatchBlock;
            insertDeoptNode((HostedBciBlockMapping.DeoptEntryInsertionPoint) block);
            List<BciBlockMapping.BciBlock> successors = block.getSuccessors();
            assert successors.size() <= 1;
            BciBlockMapping.BciBlock successor = successors.isEmpty() ? blockMap.getUnwindBlock() : successors.get(0);
            appendGoto(successor);
        } else {
            super.createExceptionDispatch(block);
        }
    }

    @Override
    protected void iterateBytecodesForBlock(BciBlockMapping.BciBlock block) {
        if (block instanceof HostedBciBlockMapping.DeoptEntryInsertionPoint) {
            /*
             * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
             * Afterwards, this block should jump to the original BciBlock.
             */
            assert block instanceof HostedBciBlockMapping.DeoptBciBlock;
            insertDeoptNode((HostedBciBlockMapping.DeoptEntryInsertionPoint) block);
            assert block.getSuccessors().size() == 1;
            appendGoto(block.getSuccessor(0));
        } else {
            super.iterateBytecodesForBlock(block);
        }
    }

    /**
     * Inserts either a DeoptEntryNode or DeoptProxyAnchorNode into the graph.
     */
    private void insertDeoptNode(HostedBciBlockMapping.DeoptEntryInsertionPoint deopt) {
        /* Ensuring current frameState matches the expectations of the DeoptEntryInsertionPoint. */
        if (deopt instanceof HostedBciBlockMapping.DeoptBciBlock) {
            assert !frameState.rethrowException();
        } else {
            assert deopt instanceof HostedBciBlockMapping.DeoptExceptionDispatchBlock;
            assert frameState.rethrowException();
        }

        DeoptProxyAnchorNode deoptNode = graph.add(deopt.isProxy() ? new DeoptProxyAnchorNode() : new DeoptEntryNode());
        if (lastInstr != null) {
            lastInstr.setNext(deoptNode);
        }
        lastInstr = deoptNode;
        FrameState stateAfter = frameState.create(deopt.frameStateBci(), deoptNode);
        deoptNode.setStateAfter(stateAfter);
        insertProxies(deoptNode, frameState);
    }

    private void insertProxies(FixedNode deoptTarget, FrameStateBuilder state) {

        /*
         * At a deoptimization point we wrap non-constant locals (and java stack elements) with
         * proxy nodes. This is to avoid global value numbering on locals (or derived expressions).
         * The effect is that when a local is accessed after a deoptimization point it is really
         * loaded from its location. This is similar to what happens in the GraphBuilderPhase if
         * entryBCI is set for OSR.
         */
        state.insertProxies(value -> createProxyNode(value, deoptTarget));
        currentDeoptIndex++;
    }

    private ValueNode createProxyNode(ValueNode value, FixedNode deoptTarget) {
        ValueNode v = DeoptProxyNode.create(value, deoptTarget, currentDeoptIndex);
        if (v.graph() != null) {
            return v;
        }
        return graph.addOrUniqueWithInputs(v);
    }

}

/**
 * To guarantee DeoptEntryNodes and DeoptProxyNodes are inserted at the correct positions, the bci
 * block mapping creation must be augmented to identify these insertion points.
 */
final class HostedBciBlockMapping extends BciBlockMapping {

    /**
     * Keep track of blocks inserted for DeoptEntryPoints so that characteristics of these blocks
     * can be validated later within {@link #verify()}.
     */
    private final Set<DeoptEntryInsertionPoint> insertedBlocks;

    private HostedBciBlockMapping(Bytecode code, DebugContext debug) {
        super(code, debug);
        insertedBlocks = new HashSet<>();
    }

    /**
     * Marks places within the graph where either a DeoptEntryNode or DeoptProxyAnchorNode needs to
     * be inserted.
     */
    interface DeoptEntryInsertionPoint {

        /* The deopt entry position this block represents. */
        int deoptEntryBci();

        boolean duringCall();

        boolean rethrowException();

        /*
         * The bci for the stateAfter value for the node to be inserted at this block. Note that for
         * DeoptProxyAnchorNodes this value may not be the same as the deoptEntryBci.
         */
        int frameStateBci();

        /* Whether this block represents a DeoptEntryNode or DeoptProxyAnchorNode. */
        boolean isProxy();

        BciBlock asBlock();
    }

    /**
     * Represents a DeoptEntryInsertionPoint whose successor is a BciBlock.
     */
    static final class DeoptBciBlock extends BciBlock implements DeoptEntryInsertionPoint {
        public final int deoptEntryBci;
        public final boolean isProxy;

        private DeoptBciBlock(int startBci, int deoptEntryBci, boolean isProxy) {
            super(startBci, startBci);
            this.deoptEntryBci = deoptEntryBci;
            this.isProxy = isProxy;
        }

        static DeoptBciBlock createDeoptEntry(int bci) {
            return new DeoptBciBlock(bci, bci, false);
        }

        static DeoptBciBlock createDeoptProxy(int successorBci, int deoptBci) {
            return new DeoptBciBlock(successorBci, deoptBci, true);
        }

        @Override
        public void setEndBci(int bci) {
            throw GraalError.shouldNotReachHere();
        }

        @Override
        public boolean isInstructionBlock() {
            return false;
        }

        @Override
        public int deoptEntryBci() {
            return deoptEntryBci;
        }

        @Override
        public int frameStateBci() {
            return getStartBci();
        }

        /*
         * Proxies correspond to the frameState (duringCall && !rethrowException)
         */
        @Override
        public boolean duringCall() {
            return isProxy;
        }

        @Override
        public boolean rethrowException() {
            return false;
        }

        @Override
        public boolean isProxy() {
            return isProxy;
        }

        @Override
        public BciBlock asBlock() {
            return this;
        }

        @Override
        public String toString() {
            return super.toString() + " (DeoptBciBlock)";
        }
    }

    /**
     * Represents a DeoptEntryInsertionPoint whose successor is an ExceptionDispatchBlock.
     */
    static class DeoptExceptionDispatchBlock extends ExceptionDispatchBlock implements DeoptEntryInsertionPoint {
        public final boolean isProxy;

        DeoptExceptionDispatchBlock(int bci, boolean isProxy) {
            super(bci);
            this.isProxy = isProxy;
        }

        DeoptExceptionDispatchBlock(ExceptionDispatchBlock dispatch, int bci, boolean isProxy) {
            super(dispatch.handler, bci);
            this.isProxy = isProxy;
        }

        /*
         * For DeoptExceptionDispatchBlocks the deoptEntryBci and frameStateBci are the same.
         */
        @Override
        public int deoptEntryBci() {
            return deoptBci;
        }

        @Override
        public int frameStateBci() {
            return deoptBci;
        }

        /*
         * Proxies correspond to the frameState (duringCall && !rethrowException)
         */
        @Override
        public boolean duringCall() {
            return isProxy;
        }

        /*
         * If not a proxy, then this block should correspond to the point after an exception object
         * is added, but before it is dispatched.
         */
        @Override
        public boolean rethrowException() {
            return !isProxy;
        }

        @Override
        public boolean isProxy() {
            return isProxy;
        }

        @Override
        public BciBlock asBlock() {
            return this;
        }

        @Override
        public String toString() {
            return super.toString() + " (DeoptExceptionDispatchBlock)";
        }
    }

    /**
     * Creates a BciBlockMapping with blocks explicitly representing where DeoptEntryNodes and
     * DeoptProxyAnchorNodes are to be inserted.
     */
    public static BciBlockMapping create(BytecodeStream stream, Bytecode code, OptionValues options, DebugContext debug, boolean hasAsyncExceptions) {
        BciBlockMapping map = new HostedBciBlockMapping(code, debug);
        buildMap(stream, code, options, debug, map, hasAsyncExceptions);
        return map;
    }

    @Override
    protected BciBlock getInstructionBlock(int bci) {
        /*
         * DeoptBciBlocks are not instruction blocks; they only represent places where
         * DeoptEntryNodes and DeoptProxyAnchorNodes are to be inserted. For a given bci, if a
         * DeoptBciBlock is found within the blockMap, then the bci's instruction block will be the
         * block's successor.
         */
        BciBlock current = blockMap[bci];
        assert !(current instanceof ExceptionDispatchBlock);
        if (current instanceof DeoptBciBlock) {
            assert !(current.getSuccessor(0) instanceof DeoptBciBlock);
            return current.getSuccessor(0);
        }
        return current;
    }

    /**
     * Checking whether this bci corresponds to a deopt entry point.
     */
    private boolean needsDeoptEntryBlock(int bci, boolean duringCall, boolean rethrowException) {
        return ((HostedMethod) code.getMethod()).compilationInfo.isDeoptEntry(bci, duringCall, rethrowException);
    }

    /* A new block must be created for all places where a DeoptEntryNode will be inserted. */
    @Override
    protected boolean isStartOfNewBlock(BciBlock current, int bci) {
        /*
         * Checking whether a DeoptEntryPoint will be created for this spot.
         */
        if (needsDeoptEntryBlock(bci, false, false)) {
            return true;
        }

        return super.isStartOfNewBlock(current, bci);
    }

    private void recordInsertedBlock(DeoptEntryInsertionPoint block) {
        blocksNotYetAssignedId++;
        assert insertedBlocks.add(block);
    }

    /**
     * Checks whether a DeoptProxyAnchorNode must be inserted for an invoke with an implicit
     * deoptimization entrypoint.
     */
    @Override
    protected void addInvokeNormalSuccessor(int invokeBci, BciBlock sux) {
        if (sux.isExceptionEntry()) {
            throw new PermanentBailoutException("Exception handler can be reached by both normal and exceptional control flow");
        }

        if (needsDeoptEntryBlock(invokeBci, true, false)) {
            /*
             * Because this invoke has an implicit deopt entry, it is necessary to insert proxies
             * afterwards.
             *
             * If a DeoptEntryPoint is next, then no additional action needs to be taken, as it
             * inserts the needed proxy inputs. Otherwise, a DeoptProxy must be inserted.
             */
            if (!(sux instanceof DeoptBciBlock)) {
                DeoptBciBlock proxyBlock = DeoptBciBlock.createDeoptProxy(sux.getStartBci(), invokeBci);
                recordInsertedBlock(proxyBlock);
                getInstructionBlock(invokeBci).addSuccessor(proxyBlock);
                proxyBlock.addSuccessor(sux);
                return;
            }

        }
        super.addInvokeNormalSuccessor(invokeBci, sux);
    }

    /**
     * Adding new {@link DeoptBciBlock} for a bci which needs an explicit DeoptEntryNode.
     */
    @Override
    protected BciBlock processNewBciBlock(int bci, BciBlock newBlock) {
        if (needsDeoptEntryBlock(bci, false, false)) {
            DeoptBciBlock deoptBciBlock = DeoptBciBlock.createDeoptEntry(bci);
            recordInsertedBlock(deoptBciBlock);
            deoptBciBlock.addSuccessor(newBlock);

            // overwriting with new DeoptEntryBlock
            blockMap[bci] = deoptBciBlock;
            return deoptBciBlock;
        }
        return newBlock;
    }

    /**
     * A deopt entry node is must be inserted after an exception object is created if either:
     * <ul>
     * <li>This point is an explicit deopt entry.</li>
     * <li>This point follows an invoke with an implicit deopt entry. In this situation only a deopt
     * proxy node is needed.</li>
     * </ul>
     */
    @Override
    protected ExceptionDispatchBlock processNewExceptionDispatchBlock(int bci, boolean isInvoke, ExceptionDispatchBlock handler) {
        boolean isExplictDeoptEntry = needsDeoptEntryBlock(bci, false, true);
        if (isExplictDeoptEntry || (isInvoke && needsDeoptEntryBlock(bci, true, false))) {
            boolean isProxy = !isExplictDeoptEntry;
            DeoptExceptionDispatchBlock block;
            if (handler == null) {
                /*
                 * This means that this exception will be dispatched to the unwind block. In this
                 * case it is still necessary to create deopt entry node.
                 */
                block = new DeoptExceptionDispatchBlock(bci, isProxy);
            } else {
                block = new DeoptExceptionDispatchBlock(handler, bci, isProxy);
                block.addSuccessor(handler);
            }
            recordInsertedBlock(block);
            return block;
        }
        return handler;
    }

    /**
     * DeoptEntryInsertionPoint blocks should have the following characteristics:
     *
     * <ul>
     * <li>Be on a reachable path (i.e. have a block id >= 0).</li>
     * <li>Correspond to a deoptimization entrypoint recorded within method.compilationInfo.</li>
     * <li>DeoptProxies can only represent implicit deoptimizations.</li>
     * <li>Have only one non-proxy DeoptEntryInsertionPoint for each encoded BCI.</li>
     * <li>Have at most 1 successor.</li>
     * <li>Not be duplicated.</li>
     * </ul>
     */
    @Override
    protected boolean verify() {
        Set<Long> coveredEncodedBcis = new HashSet<>();
        for (DeoptEntryInsertionPoint deopt : insertedBlocks) {
            BciBlock block = deopt.asBlock();
            int bci = deopt.deoptEntryBci();
            boolean duringCall = deopt.duringCall();
            boolean rethrowException = deopt.rethrowException();
            if (block.getId() < 0) {
                /*
                 * When using -H:+DeoptimizeAll DeoptInsertionPoints can be within unreachable
                 * paths.
                 */
                assert !((HostedMethod) code.getMethod()).compilationInfo.isRegisteredDeoptEntry(bci, duringCall, rethrowException);

                /* Other information about this block is irrelevant as it is unreachable. */
                continue;
            } else {
                assert needsDeoptEntryBlock(bci, duringCall, rethrowException);
            }
            assert deopt.isProxy() == duringCall : "deopt proxy nodes always represent implicit deopt entries from invokes.";
            if (!deopt.isProxy()) {
                assert coveredEncodedBcis.add(FrameInfoEncoder.encodeBci(bci, duringCall, rethrowException)) : "Deoptimization entry points must be unique.";
            }
            assert block.getSuccessorCount() <= 1 : "DeoptEntryInsertionPoint must have at most 1 successor";
            assert !block.isDuplicate() : "DeoptEntryInsertionPoint must be unique";
        }
        return super.verify();
    }
}
