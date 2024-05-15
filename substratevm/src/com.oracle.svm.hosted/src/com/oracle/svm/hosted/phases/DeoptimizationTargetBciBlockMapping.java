/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;

import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.bytecode.BytecodeStream;
import org.graalvm.compiler.core.common.PermanentBailoutException;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.java.BciBlockMapping;
import org.graalvm.compiler.options.OptionValues;

import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.code.FrameInfoEncoder;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * To guarantee DeoptEntryNodes and DeoptProxyNodes are inserted at the correct positions, the bci
 * block mapping creation must be augmented to identify these insertion points.
 *
 * Deoptimizations can occur at three different places:
 * <ol>
 * <li>At the start of any bci (!duringCall && !rethrowException).</li>
 * <li>During a call (duringCall && !rethrowException).</li>
 * <li>Exceptional flow out of a bci (!duringCall && rethrowException)</li>
 * </ol>
 *
 * For case 1, a {@link DeoptEntryNode} must be inserted before this bci and also an exception flow
 * control must be modeled.
 *
 * For case 2, a {@link DeoptProxyAnchorNode} must be inserted in both the normal and exception
 * successor to guarantee the callsite cannot be optimized around. However, if a
 * {@link DeoptEntryNode} within the given path immediately follows the call, then it is not
 * necessary to insert a {@link DeoptProxyAnchorNode}, as the optimization is already prevented.
 *
 * For case 3, a {@link DeoptEntryNode} must be inserted within the exceptional control path before
 * the logic determining which handler should be entered.
 *
 * Since deoptimizations can throw exceptions, at each "normal" (i.e., case 1) DeoptEntryNode
 * insertion point we also must model its exceptional control flow path. It is not possible for a
 * DeoptEntryNode for case 3 to throw an exception, as it is in a transient state which does
 * correspond to a position within the Java code.
 */
final class DeoptimizationTargetBciBlockMapping extends BciBlockMapping {

    /**
     * Keep track of blocks inserted for DeoptEntryPoints so that characteristics of these blocks
     * can be validated later within {@link #verify()}.
     */
    private final Set<DeoptEntryInsertionPoint> insertedBlocks;

    private DeoptimizationTargetBciBlockMapping(Bytecode code, DebugContext debug) {
        super(code, debug);
        VMError.guarantee(MultiMethod.isDeoptTarget(code.getMethod()), "Deoptimization Target expected.");
        insertedBlocks = new HashSet<>();
    }

    /**
     * Marks places within the graph where either a DeoptEntryNode or DeoptProxyAnchorNode needs to
     * be inserted.
     */
    interface DeoptEntryInsertionPoint {

        /*
         * The bci of the invoke this insertion point is serving as a proxy anchor for, or
         * BytecodeFrame.UNKNOWN_BCI if it is not "proxifying" an invoke.
         */
        int proxifiedInvokeBci();

        boolean isExceptionDispatch();

        /*
         * The bci for the stateAfter value for the node to be inserted at this block. Note that for
         * DeoptProxyAnchorNodes this value may not be the same as the deoptEntryBci.
         */
        int frameStateBci();

        BciBlock asBlock();

        /**
         * Whether this insertion point is protecting an invoke.
         */
        default boolean proxifysInvoke() {
            assert proxifiedInvokeBci() >= 0 || proxifiedInvokeBci() == BytecodeFrame.UNKNOWN_BCI;
            return proxifiedInvokeBci() != BytecodeFrame.UNKNOWN_BCI;
        }

        /**
         * Whether this is a proxy ({@link DeoptProxyAnchorNode}) or a full {@link DeoptEntryNode}.
         */
        boolean isProxy();
    }

    /**
     * Represents a DeoptEntryInsertionPoint whose successor is a BciBlock.
     */
    static final class DeoptBciBlock extends BciBlock implements DeoptEntryInsertionPoint {
        public final int deoptEntryBci;

        public int proxifiedInvokeBci;

        private DeoptBciBlock(int startBci, int deoptEntryBci, int proxifiedInvokeBci) {
            super(startBci, startBci);
            this.deoptEntryBci = deoptEntryBci;
            this.proxifiedInvokeBci = proxifiedInvokeBci;
        }

        static DeoptBciBlock createDeoptEntry(int bci) {
            assert bci >= 0;
            return new DeoptBciBlock(bci, bci, BytecodeFrame.UNKNOWN_BCI);
        }

        static DeoptBciBlock createDeoptProxy(int successorBci, int proxifiedInvokeBci) {
            assert proxifiedInvokeBci >= 0;
            return new DeoptBciBlock(successorBci, BytecodeFrame.UNKNOWN_BCI, proxifiedInvokeBci);
        }

        @Override
        public void setEndBci(int bci) {
            throw GraalError.unimplementedOverride(); // ExcludeFromJacocoGeneratedReport
        }

        @Override
        public boolean isInstructionBlock() {
            return false;
        }

        @Override
        public int proxifiedInvokeBci() {
            return proxifiedInvokeBci;
        }

        public void setProxifiedInvokeBci(int bci) {
            assert proxifiedInvokeBci == BytecodeFrame.UNKNOWN_BCI;
            this.proxifiedInvokeBci = bci;
        }

        @Override
        public boolean isProxy() {
            assert deoptEntryBci >= 0 || deoptEntryBci == BytecodeFrame.UNKNOWN_BCI;
            return deoptEntryBci == BytecodeFrame.UNKNOWN_BCI;
        }

        @Override
        public int frameStateBci() {
            return getStartBci();
        }

        @Override
        public boolean isExceptionDispatch() {
            return false;
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

        final boolean isDeoptEntry;
        final boolean isInvokeProxy;

        DeoptExceptionDispatchBlock(int bci, boolean isDeoptEntry, boolean isInvokeProxy) {
            super(bci);
            this.isDeoptEntry = isDeoptEntry;
            this.isInvokeProxy = isInvokeProxy;
        }

        DeoptExceptionDispatchBlock(ExceptionDispatchBlock dispatch, int bci, boolean isDeoptEntry, boolean isInvokeProxy) {
            super(dispatch.handler, bci);
            this.isDeoptEntry = isDeoptEntry;
            this.isInvokeProxy = isInvokeProxy;
        }

        /*
         * For DeoptExceptionDispatchBlocks the deoptEntryBci, invokeBci, and frameStateBci are the
         * same (when present).
         */

        @Override
        public int proxifiedInvokeBci() {
            return isInvokeProxy ? deoptBci : BytecodeFrame.UNKNOWN_BCI;
        }

        @Override
        public boolean isProxy() {
            return !isDeoptEntry;
        }

        @Override
        public int frameStateBci() {
            return deoptBci;
        }

        @Override
        public boolean isExceptionDispatch() {
            return true;
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
        BciBlockMapping map = new DeoptimizationTargetBciBlockMapping(code, debug);
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
    private boolean isDeoptEntry(int bci, boolean duringCall, boolean rethrowException) {
        ResolvedJavaMethod method = code.getMethod();
        return SubstrateCompilationDirectives.singleton().isDeoptEntry((MultiMethod) method, bci, duringCall, rethrowException);
    }

    /**
     * Checking whether this bci corresponds to a deopt entry point.
     */
    private boolean isRegisteredDeoptEntry(int bci, boolean duringCall, boolean rethrowException) {
        return SubstrateCompilationDirectives.singleton().isRegisteredDeoptEntry((MultiMethod) code.getMethod(), bci, duringCall, rethrowException);
    }

    /* A new block must be created for all places where a DeoptEntryNode will be inserted. */
    @Override
    protected boolean isStartOfNewBlock(BciBlock current, int bci) {
        /*
         * Checking whether a DeoptEntryPoint will be created for this spot.
         */
        if (isDeoptEntry(bci, false, false)) {
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

        if (isDeoptEntry(invokeBci, true, false)) {
            /*
             * Because this invoke has an implicit deopt entry, it is necessary to insert proxies
             * afterwards.
             */
            if (!(sux instanceof DeoptBciBlock)) {
                /*
                 * A DeoptProxy must be inserted to guarantee values will be guarded.
                 */
                DeoptBciBlock proxyBlock = DeoptBciBlock.createDeoptProxy(sux.getStartBci(), invokeBci);
                recordInsertedBlock(proxyBlock);
                getInstructionBlock(invokeBci).addSuccessor(proxyBlock);
                proxyBlock.addSuccessor(sux);
                return;
            } else {
                /*
                 * If a DeoptEntryPoint is next, then no additional action needs to be taken, as it
                 * inserts the needed proxy inputs. We mark this DeoptEntryPoint as also guarding an
                 * invoke in case we can remove it later.
                 */
                ((DeoptBciBlock) sux).setProxifiedInvokeBci(invokeBci);
            }

        }
        super.addInvokeNormalSuccessor(invokeBci, sux);
    }

    /**
     * If passed DeoptBciBlock is covered by an exception handler, then the proper
     * ExceptionDispatchBlock is added as a successor to the block.
     */
    private void addExceptionHandlerEdge(DeoptBciBlock block) {
        /* Checking whether this deopt is covered by an exception handler. */
        ExceptionDispatchBlock deoptExceptionHandler = handleExceptions(block.getStartBci(), false, false);
        if (deoptExceptionHandler != null) {
            block.addSuccessor(deoptExceptionHandler);
        }
    }

    /**
     * Like makeBlock, creates a new block start at the provided bci. This is needed by
     * makeExceptionEntries because from there one can't call makeBlock because it could lead to an
     * endless recursion.
     */
    @Override
    protected BciBlock startNewBlock(int bci) {
        BciBlock currentBlock = blockMap[bci];
        if (currentBlock != null) {
            /* Already is a block start - nothing to do. */
            assert currentBlock.getStartBci() == bci;
            return currentBlock;
        }
        BciBlock newBlock = new BciBlock(bci);
        blocksNotYetAssignedId++;
        if (isDeoptEntry(bci, false, false)) {
            DeoptBciBlock deoptEntry = DeoptBciBlock.createDeoptEntry(bci);
            recordInsertedBlock(deoptEntry);
            deoptEntry.addSuccessor(newBlock);
            newBlock = deoptEntry;
        }
        blockMap[bci] = newBlock;
        return newBlock;
    }

    /**
     * Because exception entries may also be DeoptEntryInsertionPoints and have an exception edge to
     * another exception entry (or itself), two passes are necessary: first to create all exception
     * entries and DeoptBciBlocks, and second to create the ExceptionDispatchBlocks for the
     * DeoptBciBlocks.
     */
    @Override
    protected Set<BciBlock> makeExceptionEntries(boolean splitRanges) {
        /* First creating BciBlocks, and, if necessary, DeoptBciBlocks for all exception entries. */
        Set<BciBlock> requestedBlockStarts = super.makeExceptionEntries(splitRanges);

        /* Adding exception edges for all added DeoptBciBlocks covered by exception handlers. */
        for (BciBlock block : requestedBlockStarts) {
            if (block instanceof DeoptBciBlock) {
                addExceptionHandlerEdge((DeoptBciBlock) block);
            }
        }

        return requestedBlockStarts;
    }

    /**
     * Adding new {@link DeoptBciBlock} for a bci which needs an explicit DeoptEntryNode.
     */
    @Override
    protected BciBlock processNewBciBlock(int bci, BciBlock newBlock) {
        if (isDeoptEntry(bci, false, false)) {
            DeoptBciBlock deoptBciBlock = DeoptBciBlock.createDeoptEntry(bci);
            recordInsertedBlock(deoptBciBlock);
            deoptBciBlock.addSuccessor(newBlock);

            addExceptionHandlerEdge(deoptBciBlock);

            // overwriting with new DeoptEntryBlock
            blockMap[bci] = deoptBciBlock;
            return deoptBciBlock;
        }
        return newBlock;
    }

    /**
     * A deopt entry node must be inserted after an exception object is created if either:
     * <ul>
     * <li>This point is an explicit deopt entry.</li>
     * <li>This point follows an invoke with an implicit deopt entry. In this situation only a deopt
     * proxy node is needed.</li>
     * </ul>
     */
    @Override
    protected ExceptionDispatchBlock processNewExceptionDispatchBlock(int bci, boolean isInvoke, ExceptionDispatchBlock handler) {
        boolean isDeoptEntry = isDeoptEntry(bci, false, true);
        boolean isInvokeProxy = isInvoke && isDeoptEntry(bci, true, false);
        if (isDeoptEntry || isInvokeProxy) {
            DeoptExceptionDispatchBlock block;
            if (handler == null) {
                /*
                 * This means that this exception will be dispatched to the unwind block. In this
                 * case it is still necessary to create a deopt entry node.
                 */
                block = new DeoptExceptionDispatchBlock(bci, isDeoptEntry, isInvokeProxy);
            } else {
                block = new DeoptExceptionDispatchBlock(handler, bci, isDeoptEntry, isInvokeProxy);
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
     * <li>Correspond to a recorded deoptimization entrypoint.</li>
     * <li>DeoptProxies must protected an invoke.</li>
     * <li>Have only one non-proxy DeoptEntryInsertionPoint for each encoded BCI.</li>
     * <li>Have at most 2 successors.</li>
     * <li>The successors should not be DeoptEntryInsertionPoints.</li>
     * <li>Not be duplicated.</li>
     * </ul>
     */
    @Override
    protected boolean verify() {
        Set<Long> coveredEncodedBcis = new HashSet<>();
        for (DeoptEntryInsertionPoint deopt : insertedBlocks) {
            BciBlock block = deopt.asBlock();
            int bci = deopt.frameStateBci();
            boolean isExceptionDispatch = deopt.isExceptionDispatch();
            boolean isProxy = deopt.isProxy();
            boolean proxifysInvoke = deopt.proxifysInvoke();
            boolean coversInvoke = false;
            boolean coversDeoptEntry = false;

            if (deopt.proxifysInvoke()) {
                int proxifiedInvokeBci = deopt.proxifiedInvokeBci();
                assert proxifiedInvokeBci >= 0;
                coversInvoke = isRegisteredDeoptEntry(proxifiedInvokeBci, true, false);
            }
            if (!isProxy) {
                coversDeoptEntry = isRegisteredDeoptEntry(bci, false, isExceptionDispatch);
            }

            if (block.getId() < 0) {
                /*
                 * When using -H:+DeoptimizeAll DeoptInsertionPoints can be within unreachable
                 * paths.
                 */
                assert !coversInvoke && !coversDeoptEntry;

                /* Other information about this block is irrelevant as it is unreachable. */
                continue;
            }

            if (proxifysInvoke) {
                assert coversInvoke;
            }

            if (isProxy) {
                assert proxifysInvoke;
            } else {
                assert coversDeoptEntry;
                assert coveredEncodedBcis.add(FrameInfoEncoder.encodeBci(bci, false, isExceptionDispatch)) : "Deoptimization entry points must be unique.";
            }

            assert block.getSuccessorCount() <= 2 : "DeoptEntryInsertionPoint must have at most 2 successors";
            for (BciBlock sux : block.getSuccessors()) {
                assert !(sux instanceof DeoptEntryInsertionPoint) : "Successor of DeoptEntryInsertionPoint should not be a DeoptEntryInsertionPoint.";
            }
            assert !block.isDuplicate() : "DeoptEntryInsertionPoint must be unique";
        }
        return super.verify();
    }
}
