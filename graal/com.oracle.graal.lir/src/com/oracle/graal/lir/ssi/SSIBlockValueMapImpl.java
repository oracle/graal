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
package com.oracle.graal.lir.ssi;

import static com.oracle.graal.lir.LIRValueUtil.*;
import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.util.*;

public final class SSIBlockValueMapImpl implements BlockValueMap {

    private static final class BlockData {

        /** Mapping from value to index into {@link #incoming}. */
        private final ValueMap<Value, Integer> valueIndexMap;
        private final ArrayList<Value> incoming;
        private final ArrayList<Value> outgoing;

        private BlockData(int initialVariableCapacity, int initialStackSlotCapacity) {
            valueIndexMap = new VariableVirtualStackValueMap<>(initialVariableCapacity, initialStackSlotCapacity);
            incoming = new ArrayList<>();
            outgoing = new ArrayList<>();
        }

        public Integer getIndex(Value operand) {
            return valueIndexMap.get(operand);
        }

        public Value getIncoming(int index) {
            return incoming.get(index);
        }

        public void setIncoming(int index, Value value) {
            incoming.set(index, value);
        }

        public int addIncoming(Value operand) {
            assert isVariable(operand) || isVirtualStackSlot(operand) : "Not a variable or vstack: " + operand;
            int index = incoming.size();
            incoming.add(Value.ILLEGAL);
            valueIndexMap.put(operand, index);
            return index;
        }

        public boolean contains(Value operand) {
            return getIndex(operand) != null;
        }

        public int addOutgoing(Value operand) {
            int index = outgoing.size();
            outgoing.add(operand);
            return index;
        }

    }

    /** Mapping from value to definition block. */
    private final ValueMap<Value, AbstractBlockBase<?>> valueToDefBlock;
    private final BlockMap<BlockData> blockData;
    private final int initialVariableCapacity;
    private final int initialStackSlotCapacity;

    public SSIBlockValueMapImpl(AbstractControlFlowGraph<?> cfg, int initialVariableCapacity, int initialStackSlotCapacity) {
        this.initialVariableCapacity = initialVariableCapacity;
        this.initialStackSlotCapacity = initialStackSlotCapacity;
        valueToDefBlock = new VariableVirtualStackValueMap<>(initialVariableCapacity, initialStackSlotCapacity);
        blockData = new BlockMap<>(cfg);
    }

    @Override
    public void accessOperand(Value operand, AbstractBlockBase<?> block) {
        assert block != null : "block is null";
        if (operand instanceof CompositeValue) {
            ((CompositeValue) operand).forEachComponent(null, OperandMode.USE, (op, value, mode, flag) -> {
                accessOperand(value, block);
                return value;
            });
        } else if (processValue(operand)) {
            AbstractBlockBase<?> defBlock = getDefinitionBlock(operand);
            assert defBlock != null : "Value is not defined: " + operand;
            assert AbstractControlFlowGraph.dominates(defBlock, block) : "Block " + defBlock + " does not dominate " + block;
            accessRecursive(operand, defBlock, block);
        }
    }

    @Override
    public void defineOperand(Value operand, AbstractBlockBase<?> block) {
        assert block != null : "block is null";
        if (processValue(operand)) {
            AbstractBlockBase<?> defBlock = getDefinitionBlock(operand);
            if (defBlock == null) {
                setDefinitionBlock(operand, block);
            } else {
                /*
                 * Redefinition: this can happen for nodes that do not produce a new value but proxy
                 * another one (PiNode, reinterpret).
                 */
                assert AbstractControlFlowGraph.dominates(defBlock, block) : String.format("Definition of %s in %s does not dominate the redefinition %s", operand, defBlock, block);
            }
        }
    }

    private static boolean processValue(Value operand) {
        return isVariable(operand) || isVirtualStackSlot(operand);
    }

    // setter getter for internal data structures

    private AbstractBlockBase<?> getDefinitionBlock(Value operand) {
        return valueToDefBlock.get(operand);
    }

    private void setDefinitionBlock(Value operand, AbstractBlockBase<?> block) {
        valueToDefBlock.put(operand, block);
    }

    private BlockData getOrInit(AbstractBlockBase<?> block) {
        BlockData data = blockData.get(block);
        if (data == null) {
            data = new BlockData(initialVariableCapacity, initialStackSlotCapacity);
            blockData.put(block, data);
        }
        return data;
    }

    // implementation

    private void accessRecursive(Value operand, AbstractBlockBase<?> defBlock, AbstractBlockBase<?> block) {
        Deque<AbstractBlockBase<?>> worklist = new ArrayDeque<>();
        worklist.add(block);

        while (!worklist.isEmpty()) {
            accessRecursive(operand, defBlock, worklist.pollLast(), worklist);
        }
    }

    private void accessRecursive(Value operand, AbstractBlockBase<?> defBlock, AbstractBlockBase<?> block, Deque<AbstractBlockBase<?>> worklist) {
        try (Indent indent = Debug.logAndIndent("get operand %s in block %s", operand, block)) {
            if (block.equals(defBlock)) {
                Debug.log("found definition!");
                return;
            }
            BlockData data = getOrInit(block);
            Integer index = data.getIndex(operand);
            if (index != null) {
                // value is live at block begin but might not be initialized
                Value in = data.getIncoming(index);
                if (Value.ILLEGAL.equals(in)) {
                    data.setIncoming(index, operand);
                    Debug.log("uninitialized incoming value -> initialize!");
                } else {
                    Debug.log("incoming value already initialized!");
                }
                return;
            }

            // the value is not yet live a current block
            int idx = addLiveValueToBlock(operand, block);
            data.setIncoming(idx, operand);

            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                worklist.addLast(pred);
            }
        }
    }

    private int addLiveValueToBlock(Value operand, AbstractBlockBase<?> block) {
        try (Indent indent = Debug.logAndIndent("add incoming value!")) {
            int index = -1;
            for (AbstractBlockBase<?> pred : block.getPredecessors()) {
                try (Indent indent1 = Debug.logAndIndent("Add outgoing operand to %s", pred)) {
                    BlockData predData = getOrInit(pred);
                    int predIndex = predData.addOutgoing(operand);

                    if (index == -1) {
                        index = predIndex;
                    } else {
                        assert predIndex == index;
                    }

                    for (AbstractBlockBase<?> succ : pred.getSuccessors()) {
                        Debug.log("Add incoming operand to %s", succ);
                        BlockData succData = getOrInit(succ);
                        if (!succData.contains(operand)) {
                            int succIndex = succData.addIncoming(operand);
                            assert succIndex == predIndex;
                        }
                    }
                }
            }
            Debug.log("new index: %d", index);
            return index;
        }
    }

    // finish

    public void finish(LIRGeneratorTool gen) {
        Debug.dump(gen.getResult().getLIR(), "Before SSI operands");
        AbstractControlFlowGraph<?> cfg = gen.getResult().getLIR().getControlFlowGraph();
        for (AbstractBlockBase<?> block : cfg.getBlocks()) {
            // set label
            BlockData data = blockData.get(block);
            if (data != null) {
                if (data.incoming != null && data.incoming.size() > 0) {
                    LabelOp label = getLabel(gen, block);
                    label.addIncomingValues(data.incoming.toArray(new Value[data.incoming.size()]));
                }
                // set block end
                if (data.outgoing != null && data.outgoing.size() > 0) {
                    BlockEndOp blockEndOp = getBlockEnd(gen, block);
                    blockEndOp.addOutgoingValues(data.outgoing.toArray(new Value[data.outgoing.size()]));
                }
            }
        }
    }

    private static List<LIRInstruction> getLIRforBlock(LIRGeneratorTool gen, AbstractBlockBase<?> block) {
        return gen.getResult().getLIR().getLIRforBlock(block);
    }

    private static LabelOp getLabel(LIRGeneratorTool gen, AbstractBlockBase<?> block) {
        return (LabelOp) getLIRforBlock(gen, block).get(0);
    }

    private static BlockEndOp getBlockEnd(LIRGeneratorTool gen, AbstractBlockBase<?> block) {
        List<LIRInstruction> instructions = getLIRforBlock(gen, block);
        return (BlockEndOp) instructions.get(instructions.size() - 1);
    }
}
