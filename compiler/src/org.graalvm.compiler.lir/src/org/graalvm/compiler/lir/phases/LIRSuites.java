/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.lir.phases;

import org.graalvm.compiler.lir.LIR;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.VirtualStackSlot;
import org.graalvm.compiler.lir.gen.LIRGenerationResult;
import org.graalvm.compiler.lir.gen.LIRGeneratorTool;
import org.graalvm.compiler.lir.phases.AllocationPhase.AllocationContext;
import org.graalvm.compiler.lir.phases.FinalCodeAnalysisPhase.FinalCodeAnalysisContext;
import org.graalvm.compiler.lir.phases.PostAllocationOptimizationPhase.PostAllocationOptimizationContext;
import org.graalvm.compiler.lir.phases.PreAllocationOptimizationPhase.PreAllocationOptimizationContext;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.StackSlot;

public class LIRSuites {

    private final LIRPhaseSuite<PreAllocationOptimizationContext> preAllocOptStage;
    private final LIRPhaseSuite<AllocationContext> allocStage;
    private final LIRPhaseSuite<PostAllocationOptimizationContext> postAllocStage;
    private final LIRPhaseSuite<FinalCodeAnalysisContext> finalCodeAnalysisStage;
    private boolean immutable;

    public LIRSuites(LIRPhaseSuite<PreAllocationOptimizationContext> preAllocOptStage, LIRPhaseSuite<AllocationContext> allocStage, LIRPhaseSuite<PostAllocationOptimizationContext> postAllocStage,
                    LIRPhaseSuite<FinalCodeAnalysisContext> finalCodeAnalysisStage) {
        this.preAllocOptStage = preAllocOptStage;
        this.allocStage = allocStage;
        this.postAllocStage = postAllocStage;
        this.finalCodeAnalysisStage = finalCodeAnalysisStage;
    }

    public LIRSuites(LIRSuites other) {
        this(other.getPreAllocationOptimizationStage().copy(), other.getAllocationStage().copy(), other.getPostAllocationOptimizationStage().copy(), other.getFinalCodeAnalysisStage().copy());
    }

    /**
     * {@link PreAllocationOptimizationPhase}s are executed between {@link LIR} generation and
     * register allocation.
     * <p>
     * {@link PreAllocationOptimizationPhase Implementers} can create new
     * {@link LIRGeneratorTool#newVariable variables}, {@link LIRGenerationResult#getFrameMap stack
     * slots} and {@link LIRGenerationResult#getFrameMapBuilder virtual stack slots}.
     */
    public LIRPhaseSuite<PreAllocationOptimizationContext> getPreAllocationOptimizationStage() {
        return preAllocOptStage;
    }

    /**
     * {@link AllocationPhase}s are responsible for register allocation and translating
     * {@link VirtualStackSlot}s into {@link StackSlot}s.
     * <p>
     * After the {@link AllocationStage} there should be no more {@link Variable}s and
     * {@link VirtualStackSlot}s.
     */
    public LIRPhaseSuite<AllocationContext> getAllocationStage() {
        return allocStage;
    }

    /**
     * {@link PostAllocationOptimizationPhase}s are executed after register allocation and before
     * the {@link #getFinalCodeAnalysisStage() final analysis stage} that precedes machine code
     * generation.
     * <p>
     * A {@link PostAllocationOptimizationPhase} must not introduce new {@link Variable}s,
     * {@link VirtualStackSlot}s or {@link StackSlot}s. Blocks might be removed from
     * {@link LIR#codeEmittingOrder()} by overwriting them with {@code null}.
     */
    public LIRPhaseSuite<PostAllocationOptimizationContext> getPostAllocationOptimizationStage() {
        return postAllocStage;
    }

    /**
     * {@link FinalCodeAnalysisPhase}s are executed after
     * {@linkplain #getPostAllocationOptimizationStage() post-allocation optimizations} and before
     * machine code generation.
     * <p>
     * A {@link FinalCodeAnalysisPhase} must not modify the code in any way but may verify
     * properties of the code or compute metadata like {@link ReferenceMap}s.
     */
    public LIRPhaseSuite<FinalCodeAnalysisContext> getFinalCodeAnalysisStage() {
        return finalCodeAnalysisStage;
    }

    public boolean isImmutable() {
        return immutable;
    }

    public synchronized void setImmutable() {
        if (!immutable) {
            preAllocOptStage.setImmutable();
            allocStage.setImmutable();
            postAllocStage.setImmutable();
            finalCodeAnalysisStage.setImmutable();
            immutable = true;
        }
    }

    public LIRSuites copy() {
        return new LIRSuites(preAllocOptStage.copy(), allocStage.copy(), postAllocStage.copy(), finalCodeAnalysisStage.copy());
    }
}
