/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.scopes;

import java.util.Arrays;
import java.util.Comparator;

import jdk.graal.compiler.hightiercodegen.reconstruction.stackifier.CFStackifierSortPhase;
import org.graalvm.collections.EconomicSet;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.nodes.cfg.HIRBlock;

/**
 * This class represents a scope, i.e. a set of basic blocks that belong together in a block, e.g. a
 * loop block, then-block or else-block.
 *
 * Example:
 *
 * <pre>
 * if (condition) {
 *     A();
 *     if (condition2) {
 *         B();
 *     } else {
 *         C();
 *     }
 *     D();
 * }
 * while (condition3) {
 *     E();
 *     F();
 * }
 * </pre>
 *
 * This piece of code contains 4 scopes. The first one belongs to the first {@code If} and contains
 * A,B,C,D. The second two belong to the second {@code If} and contain B and C respectively. The
 * last scope belongs to the loop and contains E and F.
 *
 */
public class Scope {

    protected final EconomicSet<HIRBlock> blocks;
    /**
     * Block that is before the scope. This block contains either a LoopBeginNode, IfNode,
     * SwitchNode or an InvokeWithExceptionNode.
     */
    protected final HIRBlock startBlock;
    protected Scope parentScope;

    public Scope(EconomicSet<HIRBlock> blocks, HIRBlock startBlock) {
        this.blocks = blocks;
        this.startBlock = startBlock;
    }

    public EconomicSet<HIRBlock> getBlocks() {
        return blocks;
    }

    /**
     * Gives all blocks that are contained in this set sorted by their id. Care needs to be taken
     * when this function is called. E.g. calling this function before and after
     * {@link CFStackifierSortPhase} might give different results because the id of the
     * {@link HIRBlock} might be changed and therefore the order.
     *
     * @return blocks sorted by their id
     */
    public HIRBlock[] getSortedBlocks() {
        HIRBlock[] blockArray = blocks.toArray(new HIRBlock[blocks.size()]);
        Arrays.sort(blockArray, Comparator.comparingInt(BasicBlock<HIRBlock>::getId));
        return blockArray;
    }

    /**
     *
     * @return the {@link HIRBlock} with the highest id.
     */
    public HIRBlock getLastBlock() {
        HIRBlock last = blocks.iterator().next();
        for (HIRBlock b : blocks) {
            if (b.getId() > last.getId()) {
                last = b;
            }
        }
        return last;
    }

    public Scope getParentScope() {
        return parentScope;
    }

    public void setParentScope(Scope parentScope) {
        this.parentScope = parentScope;
    }

    @Override
    public String toString() {
        StringBuilder sB = new StringBuilder("[ ");
        blocks.forEach(b -> sB.append(b.toString()).append(" "));
        sB.append("]");
        return sB.toString();
    }

    public HIRBlock getStartBlock() {
        return startBlock;
    }
}
