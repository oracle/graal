/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.nodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.SourceVariable;
import com.oracle.truffle.llvm.parser.metadata.debuginfo.ValueFragment;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugObjectBuilder;
import com.oracle.truffle.llvm.runtime.debug.value.LLVMDebugValue;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.nodes.base.LLVMBasicBlockNode;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugAggregateObjectBuilder;
import com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.debug.LLVMDebugSimpleObjectBuilder;
import com.oracle.truffle.llvm.runtime.types.symbols.LocalVariableDebugInfo;

/**
 * This class contains the information needed to produce a debugging scope for local variables. It
 * encodes the llvm debug intrinsics and does propagation and abstract interpretation of those
 * intrinsics when debug information is requested.
 *
 * The code in this class does not need to be efficient, as it is only used when debugging
 * functions.
 */
public final class LLVMRuntimeDebugInformation implements LocalVariableDebugInfo {

    /**
     * One piece of debugging information: either a simple value, an initialization of an aggregate
     * value, or clearing/setting parts of an aggregate value.
     */
    abstract static class LocalVarDebugInfo {

        /**
         * This is an index into the block node's array of statements (not a bitcode index).
         */
        public final int instructionIndex;
        public final LLVMSourceSymbol variable;

        LocalVarDebugInfo(int instructionIndex, LLVMSourceSymbol variable) {
            this.instructionIndex = instructionIndex;
            this.variable = variable;
        }

        /**
         * Applies the debug info in this object on the object's state, taking information from the
         * frame as necessary.
         */
        public abstract LLVMDebugObjectBuilder process(LLVMDebugObjectBuilder previous, Frame frame);

        /**
         * Returns true if this entry in the debug info will completely reset the state of the local
         * variable (as opposed to only changing parts of it).
         */
        public abstract boolean isInitialize();
    }

    static class SimpleLocalVariable extends LocalVarDebugInfo {

        private final boolean mustDereference;
        private final Object value;
        private final int valueFrameIdentifier;

        SimpleLocalVariable(int instructionIndex, boolean mustDereference, Object value, int valueFrameIdentifier, LLVMSourceSymbol variable) {
            super(instructionIndex, variable);
            this.mustDereference = mustDereference;
            this.value = value;
            this.valueFrameIdentifier = valueFrameIdentifier;
        }

        protected Object getValue(Frame frame) {
            if (valueFrameIdentifier != -1) {
                FrameSlot slot = frame.getFrameDescriptor().findFrameSlot(valueFrameIdentifier);
                if (slot != null) {
                    return frame.getValue(slot);
                }
            } else if (value != null) {
                if (value instanceof LLVMExpressionNode) {
                    try {
                        return ((LLVMExpressionNode) value).executeGeneric((VirtualFrame) frame);
                    } catch (IllegalStateException e) {
                        // fallthrough
                    }
                } else {
                    return value;
                }
            }
            return null;
        }

        protected LLVMDebugValue.Builder createBuilder() {
            return mustDereference ? CommonNodeFactory.createDebugDeclarationBuilder() : CommonNodeFactory.createDebugValueBuilder();
        }

        @Override
        public LLVMDebugObjectBuilder process(LLVMDebugObjectBuilder previous, Frame frame) {
            return new LLVMDebugSimpleObjectBuilder(createBuilder(), getValue(frame));
        }

        @Override
        public boolean isInitialize() {
            return true;
        }
    }

    static final class InitAggreateLocalVariable extends LocalVarDebugInfo {

        private final int[] offsets;
        private final int[] lengths;

        InitAggreateLocalVariable(int instructionIndex, SourceVariable variable) {
            super(instructionIndex, variable.getSymbol());
            assert variable.hasFragments();
            List<ValueFragment> fragments = variable.getFragments();
            offsets = new int[fragments.size()];
            lengths = new int[fragments.size()];

            for (int i = 0; i < fragments.size(); i++) {
                ValueFragment fragment = fragments.get(i);
                offsets[i] = fragment.getOffset();
                lengths[i] = fragment.getLength();
            }
        }

        @Override
        public LLVMDebugObjectBuilder process(LLVMDebugObjectBuilder previous, Frame frame) {
            return new LLVMDebugAggregateObjectBuilder(offsets, lengths);
        }

        @Override
        public boolean isInitialize() {
            return true;
        }
    }

    static final class ClearLocalVariableParts extends LocalVarDebugInfo {
        private final int[] parts;

        ClearLocalVariableParts(int instructionIndex, LLVMSourceSymbol variable, int[] parts) {
            super(instructionIndex, variable);
            this.parts = parts;
        }

        @Override
        public LLVMDebugObjectBuilder process(LLVMDebugObjectBuilder previous, Frame frame) {
            ((LLVMDebugAggregateObjectBuilder) previous).clear(parts);
            return previous;
        }

        @Override
        public boolean isInitialize() {
            return false;
        }
    }

    static final class SetLocalVariablePart extends SimpleLocalVariable {

        private final int partIndex;

        SetLocalVariablePart(int instructionIndex, boolean mustDereference, Object value, int valueFrameIdentifier, LLVMSourceSymbol variable, int partIndex) {
            super(instructionIndex, mustDereference, value, valueFrameIdentifier, variable);
            this.partIndex = partIndex;
        }

        @Override
        public LLVMDebugObjectBuilder process(LLVMDebugObjectBuilder previous, Frame frame) {
            ((LLVMDebugAggregateObjectBuilder) previous).setPart(partIndex, createBuilder(), getValue(frame));
            return previous;
        }

        @Override
        public boolean isInitialize() {
            return false;
        }
    }

    /**
     * A list of all debug intrinsics describing local variables, as an array (sorted by statement
     * index) for each block.
     */
    private final LocalVarDebugInfo[][] infos;

    /**
     * The list of predecessors for each block (initialized lazily).
     */
    private ArrayList<Integer>[] predecessors;

    private LLVMBasicBlockNode[] blocks;

    /**
     * The debug information available at the entry of each block (initialized lazily). This is
     * generated by propagating the debug information using a fixed-point iteration.
     *
     * For every block, there is a map from {@link LLVMSourceSymbol} to a list of debug info
     * entries. The first one in the list always needs to have
     * {@link LocalVarDebugInfo#isInitialize()} == {@code true}, for all following list elements it
     * needs to be {@code false}. (i.e., the list only has multiple entries for local variables with
     * {@link SourceVariable#hasFragments() fragments}.
     */
    private ArrayList<HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>>> blockEntryDebugInfo;

    public LLVMRuntimeDebugInformation(int blockCount) {
        this.infos = new LocalVarDebugInfo[blockCount][];
    }

    public void setBlockDebugInfo(int blockIndex, LocalVarDebugInfo[] debugInfo) {
        assert infos[blockIndex] == null;
        infos[blockIndex] = debugInfo;
    }

    public void setBlocks(LLVMBasicBlockNode[] blocks) {
        this.blocks = blocks;
    }

    @Override
    public Map<LLVMSourceSymbol, Object> getLocalVariables(Frame frame, Node node) {
        Node current = node;
        while (current != null) {
            if (current.getParent() instanceof LLVMBasicBlockNode) {
                LLVMBasicBlockNode block = (LLVMBasicBlockNode) current.getParent();
                for (int i = 0; i < block.getStatements().length; i++) {
                    if (block.getStatements()[i] == current) {
                        return getLocalVariablesForIndex(frame, block.getBlockId(), i);
                    }
                }
                assert current == block.getTerminatingInstruction();
                return getLocalVariablesForIndex(frame, block.getBlockId(), Integer.MAX_VALUE);
            }
            current = current.getParent();
        }
        /*
         * If `node` is not a child of a basic block, we are stopped before entering the dispatch
         * loop. Treat this as if we're stopped at the first statement of the first block.
         */
        return getLocalVariablesForIndex(frame, 0, 0);
    }

    /**
     * Take the given entry state and apply all additional debug information in the block up until
     * {@code end} (which is an index into the statement node array). If all information in the
     * block should be applied, {@code end} can be {@link Integer#MAX_VALUE}.
     */
    private HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> applyBlockInfo(HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> blockEntryState, int blockId, int end) {
        HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> result = new HashMap<>();
        for (Map.Entry<LLVMSourceSymbol, List<LocalVarDebugInfo>> entry : blockEntryState.entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        for (LocalVarDebugInfo info : infos[blockId]) {
            if (info.instructionIndex > end) {
                break;
            }
            List<LocalVarDebugInfo> list;
            if (info.isInitialize()) {
                result.put(info.variable, list = new ArrayList<>());
            } else {
                list = result.get(info.variable);
                if (list == null) {
                    result.put(info.variable, list = new ArrayList<>());
                }
            }
            list.add(info);
        }
        return result;
    }

    private Map<LLVMSourceSymbol, Object> getLocalVariablesForIndex(Frame frame, int blockId, int index) {
        initializePredecessors();
        initializeDebugInfo();
        HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> info = applyBlockInfo(blockEntryDebugInfo.get(blockId), blockId, index);

        HashMap<LLVMSourceSymbol, Object> values = new HashMap<>();
        for (Map.Entry<LLVMSourceSymbol, List<LocalVarDebugInfo>> entry : info.entrySet()) {
            // process all debug info entries for this variable
            LLVMDebugObjectBuilder builder = null;
            for (LocalVarDebugInfo di : entry.getValue()) {
                builder = di.process(builder, frame);
            }
            values.put(entry.getKey(), builder.getValue(entry.getKey()));
        }
        return values;
    }

    private void initializePredecessors() {
        if (predecessors == null) {
            @SuppressWarnings("unchecked")
            ArrayList<Integer>[] result = new ArrayList[infos.length];
            for (int i = 0; i < infos.length; i++) {
                result[i] = new ArrayList<>(2);
            }
            for (LLVMBasicBlockNode b : blocks) {
                for (int successor : b.getTerminatingInstruction().getSuccessors()) {
                    if (successor >= 0) {
                        result[successor].add(b.getBlockId());
                    }
                }
            }
            predecessors = result;
        }
    }

    /**
     * Iteratively propagate the debug info: this consists of two fixed-point iterations, one that
     * optimistically propagates the debug info into loops, and a second one that conservatively
     * merges the state from the predecessors.
     */
    private void initializeDebugInfo() {
        if (blockEntryDebugInfo == null) {
            ArrayList<HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>>> result = new ArrayList<>();
            for (int i = 0; i < infos.length; i++) {
                result.add(new HashMap<>());
            }
            boolean changed;
            // step 1: optimistically propagate values from predecessors
            do {
                changed = false;
                for (int i = 0; i < infos.length; i++) {
                    changed = changed | merge(i, result, predecessors[i], true);
                }
            } while (changed);
            // step 2: only propagate common values from predecessors
            do {
                changed = false;
                for (int i = 0; i < infos.length; i++) {
                    changed = changed | merge(i, result, predecessors[i], false);
                }
            } while (changed);
            blockEntryDebugInfo = result;
        }
    }

    private boolean merge(int blockId, List<HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>>> entryDebugInfo, ArrayList<Integer> preds, boolean propagate) {
        if (preds.isEmpty()) {
            return false;
        }
        // get the first predecessor state
        HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> result = applyBlockInfo(entryDebugInfo.get(preds.get(0)), preds.get(0), Integer.MAX_VALUE);
        for (int i = 1; i < preds.size(); i++) {
            // merge with all other predecessor states
            HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> variables = applyBlockInfo(entryDebugInfo.get(preds.get(i)), preds.get(i), Integer.MAX_VALUE);
            Iterator<Entry<LLVMSourceSymbol, List<LocalVarDebugInfo>>> iterator = variables.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<LLVMSourceSymbol, List<LocalVarDebugInfo>> existing = iterator.next();
                if (propagate) {
                    // propagate optimistically
                    if (!result.containsKey(existing.getKey())) {
                        result.put(existing.getKey(), existing.getValue());
                    }
                } else {
                    // merge only matching states
                    if (!Objects.equals(variables.get(existing.getKey()), existing.getValue())) {
                        iterator.remove();
                    }
                }
            }
        }
        HashMap<LLVMSourceSymbol, List<LocalVarDebugInfo>> old = entryDebugInfo.get(blockId);
        entryDebugInfo.set(blockId, result);
        return !old.equals(result);
    }
}
