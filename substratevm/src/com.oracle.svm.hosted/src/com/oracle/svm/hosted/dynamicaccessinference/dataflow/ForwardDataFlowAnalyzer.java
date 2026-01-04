/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.dynamicaccessinference.dataflow;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.graalvm.collections.Pair;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeStream;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BciBlockMapping.BciBlock;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Abstract bytecode forward data-flow analyzer. Abstract program states, represented by {@code S},
 * are propagated through the control-flow graph of a method until a fixed point is reached, i.e.,
 * there are no more modifications of the abstract program states.
 * <p>
 * This class can be used to implement simple data-flow algorithms, such as finding reaching
 * definitions, or more complex algorithms where {@code S} is an abstract representation of the
 * program's execution frames.
 *
 * @param <S> The type of the abstract program state to be propagated during the data-flow analysis.
 *            {@code S} should override {@link Object#equals(Object)} in order to allow a fixed
 *            point to be reached by the analyzer.
 */
public abstract class ForwardDataFlowAnalyzer<S> {

    /**
     * Executes the data-flow analysis on {@link BciBlockMapping}. It is assumed that the received
     * bytecode is valid and verified by a bytecode verifier.
     *
     * @param controlFlowGraph The control-flow graph of the method to analyze.
     * @return A mapping from bytecode instruction BCI to the inferred abstract state of the program
     *         before the execution of corresponding instruction.
     * @throws DataFlowAnalysisException Can be thrown to signal unrecoverable exceptions in the
     *             analysis.
     */
    public Map<Integer, S> analyze(BciBlockMapping controlFlowGraph) {
        ExceptionHandler[] exceptionHandlers = controlFlowGraph.code.getExceptionHandlers();

        Map<Integer, S> states = new HashMap<>();
        LinkedHashSet<BciBlock> workList = new LinkedHashSet<>();

        /* Create an initial (usually "empty") state at BCI 0. */
        states.put(controlFlowGraph.getStartBlock().getStartBci(), createInitialState(controlFlowGraph.code.getMethod()));
        workList.add(controlFlowGraph.getStartBlock());

        while (!workList.isEmpty()) {
            BciBlock currentBlock = workList.removeFirst();

            Pair<Integer, S> outStateAndEndBCI = processBlock(currentBlock, controlFlowGraph.code, states);
            /*
             * We don't have to process the basic block's successors if we reach a fixed point
             * within that block (i.e., there are no changes in the abstract state at some point
             * during the processing of the basic block).
             */
            if (outStateAndEndBCI == null) {
                continue;
            }

            S outState = outStateAndEndBCI.getRight();
            int blockEndBCI = outStateAndEndBCI.getLeft();

            /* Go through all non-exception handler successors. */
            for (BciBlock successor : currentBlock.getSuccessors()) {
                if (!successor.isInstructionBlock()) {
                    continue;
                }

                S outStateCopy = copyState(outState);
                mergeIntoSuccessorBlock(outStateCopy, successor, states, workList);
            }

            BitSet handlers = exceptionHandlers.length > 0
                            ? controlFlowGraph.getBciExceptionHandlerIDs(blockEndBCI)
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

                S handlerState = createExceptionState(outState, exceptionTypes);
                mergeIntoSuccessorBlock(handlerState, handlerBlock, states, workList);
            }
        }

        return states;
    }

    /**
     * Wrapper for {@link #analyze(BciBlockMapping)} which creates a control-flow graph based on
     * {@link Bytecode}.
     */
    public Map<Integer, S> analyze(Bytecode bytecode) {
        OptionValues emptyOptions = new OptionValues(null, OptionValues.newOptionMap());
        DebugContext disabledDebugContext = DebugContext.disabled(emptyOptions);
        BciBlockMapping controlFlowGraph = BciBlockMapping.create(new BytecodeStream(bytecode.getCode()), bytecode, emptyOptions, disabledDebugContext, false);
        return analyze(controlFlowGraph);
    }

    private Pair<Integer, S> processBlock(BciBlock block, Bytecode code, Map<Integer, S> states) {
        BytecodeStream stream = new BytecodeStream(code.getCode());
        stream.setBCI(block.getStartBci());

        S outState = processInstruction(states.get(stream.currentBCI()), stream, code);
        while (stream.nextBCI() <= block.getEndBci()) {
            S successorState = states.get(stream.nextBCI());
            if (outState.equals(successorState)) {
                /*
                 * If a fixed point is reached within a basic block, further instructions of that
                 * block do not have to be processed. This early exit is signaled by returning null.
                 */
                return null;
            } else {
                states.put(stream.nextBCI(), outState);
            }
            stream.next();
            outState = processInstruction(states.get(stream.currentBCI()), stream, code);
        }

        /*
         * There seems to sometimes be a mismatch between the actual end BCI of a BciBlock and the
         * BCI obtained from BciBlock::getEndBci. Because of that, we return the BCI of the last
         * processed instruction in the block together with the state.
         */
        return Pair.create(stream.currentBCI(), outState);
    }

    private void mergeIntoSuccessorBlock(S state, BciBlock successorBlock, Map<Integer, S> states, Set<BciBlock> workList) {
        int successorStartBCI = successorBlock.getStartBci();
        S successorState = states.get(successorStartBCI);
        if (successorState == null) {
            /* First time we enter this basic block. */
            states.put(successorStartBCI, state);
            workList.add(successorBlock);
        } else {
            S mergedState = mergeStates(successorState, state);
            if (!mergedState.equals(successorState)) {
                states.put(successorStartBCI, mergedState);
                workList.add(successorBlock);
            }
        }
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
     * Merge two states from divergent control-flow paths. The operation should be:
     * <ul>
     * <li>Idempotent: {@code mergeStates(s, s) = s};</li>
     * <li>Commutative: {@code mergeStates(x, y) = mergeStates(y, x)};</li>
     * <li>Associative:
     * {@code mergeStates(mergeStates(x, y), z) = mergeStates(x, mergeStates(y, z))}.</li>
     * </ul>
     *
     * @return The merged state of the left and right input states.
     */
    protected abstract S mergeStates(S left, S right);

    /**
     * The data-flow transfer function. The function should be monotonic, i.e., it should enable the
     * fixed point to be reached with respect to the implementation {@link S#equals(Object)} and
     * {@link #mergeStates(Object, Object)}.
     *
     * @param inState The abstract program state right before the execution of the instruction.
     * @param stream A bytecode stream of which the position is set to the instruction currently
     *            being processed. The stream should not be modified from this method.
     * @param code The bytecode of the method currently being analyzed.
     * @return The abstract program state after the instruction being processed.
     */
    protected abstract S processInstruction(S inState, BytecodeStream stream, Bytecode code);
}
