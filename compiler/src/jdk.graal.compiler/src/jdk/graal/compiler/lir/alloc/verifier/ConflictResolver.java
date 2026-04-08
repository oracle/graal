/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.lir.LIR;

import java.util.List;

/**
 * Resolve {@link ConflictedAllocationState} occurrences based on internal set of rules.
 *
 * <p>
 * In-case comparison of {@link ValueAllocationState} fails, it also might get resolved by this.
 * </p>
 */
public interface ConflictResolver {
    /**
     * {@link ConflictResolver} can prepare its own internal state here so it can later resolve
     * conflicts.
     *
     * @param lir LIR
     * @param blockInstructions IR of the Verifier
     */
    void prepare(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions);

    /**
     * This is run during the checking/verification stage, before this any
     * conflict resolution is needed.
     *
     * @param instruction RAV instruction that we build conflict resolver information from
     * @param block Block where this instruction is in
     */
    void prepareFromInstr(RAVInstruction.Base instruction, BasicBlock<?> block);

    /**
     * Resolve an issue stemming from {@link ValueAllocationState} not having the correct value in
     * verification phase.
     *
     * @param target Variable we are looking to resolve to
     * @param valueState Current ValueAllocationState instance
     * @param location Location where the valueState is stored
     * @return {@link ValueAllocationState} instance if conflict is resolved, otherwise null.
     */
    ValueAllocationState resolveValueState(RAVariable target, ValueAllocationState valueState, RAValue location);

    /**
     * Resolve a {@link ConflictedAllocationState} to {@link ValueAllocationState} based on the
     * target variable.
     *
     * @param target Variable we are looking to resolve to
     * @param conflictedState Set of conflicted states
     * @param location Location where the valueState is stored
     * @return {@link ValueAllocationState} instance if conflict is resolved, otherwise null.
     */
    ValueAllocationState resolveConflictedState(RAVariable target, ConflictedAllocationState conflictedState, RAValue location);
}
