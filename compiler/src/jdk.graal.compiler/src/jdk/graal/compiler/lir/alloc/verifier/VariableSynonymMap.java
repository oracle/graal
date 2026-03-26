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
import jdk.graal.compiler.util.EconomicHashMap;

import java.util.List;
import java.util.Map;

/**
 * Union-find data structure for synonyms between variables created by virtual moves.
 */
public class VariableSynonymMap implements ConflictResolver {
    Map<RAVariable, RAVariable> parent = new EconomicHashMap<>();
    Map<RAVariable, Integer> rank = new EconomicHashMap<>();

    protected void addSynonym(RAVariable source, RAVariable target) {
        union(source, target);
    }

    protected RAVariable find(RAVariable x) {
        parent.putIfAbsent(x, x);
        if (!parent.get(x).equals(x)) {
            parent.put(x, find(parent.get(x)));
        }
        return parent.get(x);
    }

    protected void union(RAVariable a, RAVariable b) {
        RAVariable rootA = find(a);
        RAVariable rootB = find(b);

        if (rootA.equals(rootB)) {
            return;
        }

        int rankA = rank.getOrDefault(rootA, 0);
        int rankB = rank.getOrDefault(rootB, 0);

        if (rankA < rankB) {
            parent.put(rootA, rootB);
        } else if (rankA > rankB) {
            parent.put(rootB, rootA);
        } else {
            parent.put(rootB, rootA);
            rank.put(rootA, rankA + 1);
        }
    }

    protected boolean isSynonymOf(RAVariable source, RAVariable target) {
        return find(source).equals(find(target));
    }

    @Override
    public void prepare(LIR lir, BlockMap<List<RAVInstruction.Base>> blockInstructions) {
        for (var blockId : lir.getBlocks()) {
            var block = lir.getBlockById(blockId);
            var instructions = blockInstructions.get(block);

            for (var instruction : instructions) {
                this.prepareFromInstr(instruction, block);
            }
        }
    }

    public void prepareFromInstr(RAVInstruction.Base instruction, BasicBlock<?> block) {
        if (instruction instanceof RAVInstruction.ValueMove move) {
            if (!move.variableOrConstant.isVariable() || !move.getLocation().isVariable()) {
                return;
            }

            this.addSynonym(move.variableOrConstant.asVariable(), move.getLocation().asVariable());
        }
    }

    @Override
    public ValueAllocationState resolveValueState(RAVariable target, ValueAllocationState valueState, RAValue location) {
        var stateValue = valueState.getRAValue();
        if (!stateValue.isVariable()) {
            return null;
        }

        if (!isSynonymOf(target, stateValue.asVariable())) {
            return null;
        }

        return new ValueAllocationState(target, null, null);
    }

    @Override
    public ValueAllocationState resolveConflictedState(RAVariable target, ConflictedAllocationState conflictedState, RAValue location) {
        var confStates = conflictedState.getConflictedStates();
        for (var valueState : confStates) {
            var stateValue = valueState.getRAValue();
            if (!stateValue.isVariable()) {
                return null;
            }

            if (!isSynonymOf(target, stateValue.asVariable())) {
                return null;
            }

        }
        return new ValueAllocationState(target, null, null);
    }
}
