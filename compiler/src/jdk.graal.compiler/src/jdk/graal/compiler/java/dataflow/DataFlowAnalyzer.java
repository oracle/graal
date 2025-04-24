/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.java.dataflow;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BciBlockMapping.BciBlock;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import org.graalvm.collections.Pair;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small bytecode forward data flow analyzer.
 *
 * @param <S> The abstract program state type to be propagated during the data flow analysis.
 */
public abstract class DataFlowAnalyzer<S> {

    /**
     * Executes the data flow analysis on method bytecode.
     *
     * @param bytecode The bytecode to analyze.
     * @return A mapping from bytecode instruction BCI to the inferred abstract state of the program before the execution of that instruction.
     */
    public Map<Integer, S> analyze(Bytecode bytecode) throws DataFlowAnalysisException {
        BciBlockMapping controlFlowGraph = BciBlockMapping.create(new BytecodeStream(bytecode.getCode()), bytecode, null, DebugContext.disabled(null), false);

        ExceptionHandler[] exceptionHandlers = controlFlowGraph.code.getExceptionHandlers();

        Map<Integer, S> states = new HashMap<>();
        Set<BciBlock> workList = new HashSet<>();

        states.put(controlFlowGraph.getStartBlock().getStartBci(), createInitialState(bytecode.getMethod()));
        workList.add(controlFlowGraph.getStartBlock());

        while (!workList.isEmpty()) {
            BciBlock currentBlock = workList.iterator().next();
            workList.remove(currentBlock);

            Pair<Integer, S> outState = processBlock(currentBlock, controlFlowGraph.code, states);
            /*
             * We don't have to process the basic block's successors if we reach
             * a fixed point within that block.
             */
            if (outState == null) {
                continue;
            }

            /* Go through all non-exception handler successors. */
            for (BciBlock successor : currentBlock.getSuccessors()) {
                if (!successor.isInstructionBlock()) {
                    continue;
                }

                S successorState = states.get(successor.getStartBci());
                if (successorState == null) {
                    states.put(successor.getStartBci(), copyState(outState.getRight()));
                    workList.add(successor);
                } else {
                    S mergedState = mergeStates(successorState, outState.getRight());
                    if (!mergedState.equals(successorState)) {
                        states.put(successor.getStartBci(), mergedState);
                        workList.add(successor);
                    }
                }
            }

            BitSet handlers = exceptionHandlers.length > 0
                    ? controlFlowGraph.getBciExceptionHandlerIDs(outState.getLeft())
                    : new BitSet();

            /* Go through all the exception handler successors. */
            for (int i = handlers.nextSetBit(0); i >= 0;) {
                BciBlock handlerBlock = controlFlowGraph.getHandlerBlock(i);

                /* Gather all the exception types caught by this handler. */
                List<JavaType> exceptionTypes = new ArrayList<>();
                while (i >= 0 && controlFlowGraph.getHandlerBlock(i) == handlerBlock) {
                    exceptionTypes.add(exceptionHandlers[i].getCatchType());
                    i = handlers.nextSetBit(i + 1);
                }
                S handlerState = createExceptionState(outState.getRight(), exceptionTypes);

                S successorState = states.get(handlerBlock.getStartBci());
                if (successorState == null) {
                    states.put(handlerBlock.getStartBci(), handlerState);
                    workList.add(handlerBlock);
                } else {
                    S mergedState = mergeStates(successorState, handlerState);
                    if (!mergedState.equals(successorState)) {
                        states.put(handlerBlock.getStartBci(), mergedState);
                        workList.add(handlerBlock);
                    }
                }
            }
        }

        return states;
    }

    private Pair<Integer, S> processBlock(BciBlock block, Bytecode code, Map<Integer, S> states) throws DataFlowAnalysisException {
        BytecodeStream stream = new BytecodeStream(code.getCode());
        stream.setBCI(block.getStartBci());

        S outState = processInstruction(states.get(stream.currentBCI()), stream, code);
        while (stream.nextBCI() <= block.getEndBci()) {
            S successorState = states.get(stream.nextBCI());
            if (!outState.equals(successorState)) {
                states.put(stream.nextBCI(), outState);
            } else {
                return null;
            }
            stream.next();
            outState = processInstruction(states.get(stream.currentBCI()), stream, code);
        }

        /*
         * There seems to sometimes be a mismatch between the actual end BCI of a BciBlock
         * and the BCI obtained from BciBlock::getEndBci. Because of that, we return the BCI
         * of the last processed instruction in the block together with the state.
         */
        return Pair.create(stream.currentBCI(), outState);
    }

    /**
     * Create the initial state for the analysis.
     *
     * @param method The JVMCI method being analyzed.
     * @return The abstract program state at the entry point of the method.
     */
    protected abstract S createInitialState(ResolvedJavaMethod method);

    /**
     * Create the state at the entry of an exception handler.
     *
     * @param inState The abstract program state before the entry into the exception handler.
     * @param exceptionTypes The exception types of the handler which is being entered.
     * @return The abstract program state at the entry point of the exception handler.
     */
    protected abstract S createExceptionState(S inState, List<JavaType> exceptionTypes);

    /**
     * Create a deep copy of a state.
     *
     * @param state State to be copied.
     * @return Deep copy of the input state.
     */
    protected abstract S copyState(S state);

    /**
     * Merge two states from divergent control flow paths.
     *
     * @return The merged state of the left and right input states.
     */
    protected abstract S mergeStates(S left, S right) throws DataFlowAnalysisException;

    /**
     * The data flow transfer function.
     *
     * @param inState The input state.
     * @param stream A bytecode stream of which the position is set to the instruction currently being processed. The stream should not be modified from this method.
     * @param code The bytecode of the method currently being analyzed.
     * @return The abstract program state after the instruction being processed.
     */
    protected abstract S processInstruction(S inState, BytecodeStream stream, Bytecode code) throws DataFlowAnalysisException;
}
