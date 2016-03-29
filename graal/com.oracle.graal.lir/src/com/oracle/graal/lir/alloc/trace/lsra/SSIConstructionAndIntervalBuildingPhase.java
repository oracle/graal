/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.trace.lsra;

import java.util.List;

import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.AllocationPhase;
import com.oracle.graal.lir.ssa.SSAUtil;
import com.oracle.graal.lir.ssi.SSIUtil;

import jdk.vm.ci.code.TargetDescription;

/**
 * Constructs {@linkplain SSIUtil SSI LIR} and builds {@link TraceInterval intervals}.
 *
 * @see SSIUtil
 */
public final class SSIConstructionAndIntervalBuildingPhase extends AllocationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    AllocationContext context) {
        assert SSAUtil.verifySSAForm(lirGenRes.getLIR());
        TraceBuilderResult<?> traceBuilderResult = context.contextLookup(TraceBuilderResult.class);
        assert traceBuilderResult != null : "no trace builder result";
        boolean neverSpillConstants = false;
        TraceIntervalMap intervalMap = new SSIAndIntervalBuilder(lirGenRes.getLIR(), traceBuilderResult, target, lirGenRes, context.registerAllocationConfig, neverSpillConstants,
                        context.spillMoveFactory).buildSSIAndIntervals();
        context.contextAdd(intervalMap);
    }
}
