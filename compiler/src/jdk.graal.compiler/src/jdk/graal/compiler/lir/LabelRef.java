/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.StandardOp.BranchOp;
import jdk.graal.compiler.lir.StandardOp.JumpOp;

/**
 * LIR instructions such as {@link JumpOp} and {@link BranchOp} need to reference their target
 * {@link BasicBlock}. However, direct references are not possible since the control flow graph (and
 * therefore successors lists) can be changed by optimizations - and fixing the instructions is
 * error prone. Therefore, we represent an edge to block B from block A via the tuple {@code (A,
 * successor-index-of-B)}. That is, indirectly by storing the index into the successor list of A.
 * Note therefore that the successor list cannot be re-ordered.
 */
public final class LabelRef {

    private final LIR lir;
    private final BasicBlock<?> block;
    private final int suxIndex;

    /**
     * Returns a new reference to a successor of the given block.
     *
     * @param block The base block that contains the successor list.
     * @param suxIndex The index of the successor.
     * @return The newly created label reference.
     */
    public static LabelRef forSuccessor(final LIR lir, final BasicBlock<?> block, final int suxIndex) {
        return new LabelRef(lir, block, suxIndex);
    }

    /**
     * Returns a new reference to a successor of the given block.
     *
     * @param block The base block that contains the successor list.
     * @param suxIndex The index of the successor.
     */
    private LabelRef(final LIR lir, final BasicBlock<?> block, final int suxIndex) {
        this.lir = lir;
        this.block = block;
        this.suxIndex = suxIndex;
    }

    public BasicBlock<?> getSourceBlock() {
        return block;
    }

    public BasicBlock<?> getTargetBlock() {
        return block.getSuccessorAt(suxIndex);
    }

    public Label label() {
        return ((StandardOp.LabelOp) lir.getLIRforBlock(getTargetBlock()).get(0)).getLabel();
    }

    @Override
    public String toString() {
        return getSourceBlock() + " -> " + (suxIndex < block.getSuccessorCount() ? getTargetBlock() : "?");
    }
}
