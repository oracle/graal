/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace;

import static com.oracle.graal.lir.LIRValueUtil.isStackSlotValue;
import static com.oracle.graal.lir.alloc.trace.TraceUtil.asShadowedRegisterValue;
import static com.oracle.graal.lir.alloc.trace.TraceUtil.isShadowedRegisterValue;
import static jdk.vm.ci.code.ValueUtil.isIllegal;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.List;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.TraceBuilder.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.ssa.SSAUtil.PhiValueVisitor;
import com.oracle.graal.lir.ssi.SSIUtil;

public final class TraceGlobalMoveResolutionPhase extends TraceAllocationPhase {

    /**
     * Abstract move resolver interface for testing.
     */
    public abstract static class MoveResolver {
        public abstract void addMapping(Value src, AllocatableValue dst);
    }

    private final TraceBuilderResult<?> resultTraces;

    public TraceGlobalMoveResolutionPhase(TraceBuilderResult<?> resultTraces) {
        this.resultTraces = resultTraces;
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder, MoveFactory spillMoveFactory,
                    RegisterAllocationConfig registerAllocationConfig) {
        resolveGlobalDataFlow(resultTraces, lirGenRes, spillMoveFactory, target.arch);
    }

    @SuppressWarnings("try")
    private static <B extends AbstractBlockBase<B>> void resolveGlobalDataFlow(TraceBuilderResult<B> resultTraces, LIRGenerationResult lirGenRes, MoveFactory spillMoveFactory, Architecture arch) {
        LIR lir = lirGenRes.getLIR();
        /* Resolve trace global data-flow mismatch. */
        TraceGlobalMoveResolver moveResolver = new TraceGlobalMoveResolver(lirGenRes, spillMoveFactory, arch);
        PhiValueVisitor visitor = (Value phiIn, Value phiOut) -> {
            if (!isIllegal(phiIn) && !TraceGlobalMoveResolver.isMoveToSelf(phiOut, phiIn)) {
                addMapping(moveResolver, phiOut, phiIn);
            }
        };

        try (Indent indent = Debug.logAndIndent("Trace global move resolution")) {
            for (List<B> trace : resultTraces.getTraces()) {
                for (AbstractBlockBase<?> fromBlock : trace) {
                    for (AbstractBlockBase<?> toBlock : fromBlock.getSuccessors()) {
                        if (resultTraces.getTraceForBlock(fromBlock) != resultTraces.getTraceForBlock(toBlock)) {
                            try (Indent indent0 = Debug.logAndIndent("Handle trace edge from %s (Trace%d) to %s (Trace%d)", fromBlock, resultTraces.getTraceForBlock(fromBlock), toBlock,
                                            resultTraces.getTraceForBlock(toBlock))) {

                                final List<LIRInstruction> instructions;
                                final int insertIdx;
                                if (fromBlock.getSuccessorCount() == 1) {
                                    instructions = lir.getLIRforBlock(fromBlock);
                                    insertIdx = instructions.size() - 1;
                                } else {
                                    assert toBlock.getPredecessorCount() == 1;
                                    instructions = lir.getLIRforBlock(toBlock);
                                    insertIdx = 1;
                                }

                                moveResolver.setInsertPosition(instructions, insertIdx);
                                SSIUtil.forEachValuePair(lir, toBlock, fromBlock, visitor);
                                moveResolver.resolveAndAppendMoves();
                            }
                        }
                    }
                }
            }
        }
    }

    public static void addMapping(MoveResolver moveResolver, Value from, Value to) {
        assert !isIllegal(to);
        // prepare input/output values.
        final Value src;
        final Value srcShadow;
        if (isShadowedRegisterValue(from)) {
            ShadowedRegisterValue phiOutSh = asShadowedRegisterValue(from);
            src = phiOutSh.getRegister();
            srcShadow = phiOutSh.getStackSlot();
        } else {
            src = from;
            srcShadow = null;
        }
        assert src != null;
        assert srcShadow == null || isRegister(src) && isStackSlotValue(srcShadow) : "Unexpected shadowed value: " + from;

        final Value dst;
        final Value dstShadow;
        if (isShadowedRegisterValue(to)) {
            ShadowedRegisterValue phiInSh = asShadowedRegisterValue(to);
            dst = phiInSh.getRegister();
            dstShadow = phiInSh.getStackSlot();
        } else {
            dst = to;
            dstShadow = null;
        }
        assert dst != null;
        assert dstShadow == null || isRegister(dst) && isStackSlotValue(dstShadow) : "Unexpected shadowed value: " + to;

        // set dst
        if (!dst.equals(src)) {
            moveResolver.addMapping(src, (AllocatableValue) dst);
        }
        // set dst_shadow
        if (dstShadow != null && !dstShadow.equals(src)) {
            moveResolver.addMapping(src, (AllocatableValue) dstShadow);
        }
    }
}
