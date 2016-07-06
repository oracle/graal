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

import java.util.List;

import com.oracle.graal.compiler.common.alloc.RegisterAllocationConfig;
import com.oracle.graal.compiler.common.alloc.Trace;
import com.oracle.graal.compiler.common.alloc.TraceBuilderResult;
import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.debug.DebugCloseable;
import com.oracle.graal.debug.DebugMemUseTracker;
import com.oracle.graal.debug.DebugTimer;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.gen.LIRGeneratorTool.MoveFactory;
import com.oracle.graal.lir.phases.LIRPhase;
import com.oracle.graal.lir.phases.LIRPhase.LIRPhaseStatistics;

import jdk.vm.ci.code.TargetDescription;

public abstract class TraceAllocationPhase<C extends TraceAllocationPhase.TraceAllocationContext> {

    public static class TraceAllocationContext {
        public final MoveFactory spillMoveFactory;
        public final RegisterAllocationConfig registerAllocationConfig;
        public final TraceBuilderResult<?> resultTraces;

        public TraceAllocationContext(MoveFactory spillMoveFactory, RegisterAllocationConfig registerAllocationConfig, TraceBuilderResult<?> resultTraces) {
            this.spillMoveFactory = spillMoveFactory;
            this.registerAllocationConfig = registerAllocationConfig;
            this.resultTraces = resultTraces;
        }
    }

    private CharSequence name;

    /**
     * Records time spent within {@link #apply}.
     */
    private final DebugTimer timer;

    /**
     * Records memory usage within {@link #apply}.
     */
    private final DebugMemUseTracker memUseTracker;

    public TraceAllocationPhase() {
        LIRPhaseStatistics statistics = LIRPhase.statisticsClassValue.get(getClass());
        timer = statistics.timer;
        memUseTracker = statistics.memUseTracker;
    }

    public final CharSequence getName() {
        if (name == null) {
            name = LIRPhase.createName(getClass());
        }
        return name;
    }

    public final <B extends AbstractBlockBase<B>> void apply(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace<B> trace, C context) {
        apply(target, lirGenRes, codeEmittingOrder, trace, context, true);
    }

    @SuppressWarnings("try")
    public final <B extends AbstractBlockBase<B>> void apply(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace<B> trace, C context,
                    boolean dumpLIR) {
        try (Scope s = Debug.scope(getName(), this)) {
            try (DebugCloseable a = timer.start(); DebugCloseable c = memUseTracker.start()) {
                run(target, lirGenRes, codeEmittingOrder, trace, context);
                if (dumpLIR && Debug.isDumpEnabled(Debug.BASIC_LOG_LEVEL)) {
                    Debug.dump(Debug.BASIC_LOG_LEVEL, lirGenRes.getLIR(), "%s", getName());
                }
            }
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected abstract <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, Trace<B> trace, C context);
}
