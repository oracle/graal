/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.debug.HistogramInstructionTracer;
import com.oracle.truffle.api.bytecode.debug.PrintInstructionTracer;
import com.oracle.truffle.api.frame.Frame;

/**
 * A low-overhead callback interface to observe instruction execution in Bytecode DSL interpreters.
 * A tracer receives a notification immediately before the interpreter executes the instruction at a
 * given bytecode index. The callback runs on the language thread that executes the bytecode, on the
 * hot path, and may be invoked at very high frequency.
 * <p>
 * Tracers for a {@link BytecodeRootNodes} instance may be attached via the
 * {@link BytecodeRootNodes#addInstructionTracer(InstructionTracer)} method. Attaching tracers to
 * all root node instances of a {@link BytecodeDescriptor} is possible using
 * {@link BytecodeDescriptor#addInstructionTracer(TruffleLanguage, InstructionTracer)}. Note that
 * attaching a tracer requires all affected root nodes to be invalidated and is therefore a very
 * expensive operation. It also potentially requires all root nodes to be re-parsed. As a trade-off,
 * not using an instruction tracer is basically free, except for reserving instructions in the
 * bytecode interpreter, similar to how the {@link Instrumentation @Instrumentation} annotation is
 * implemented.
 * <p>
 * Typical uses include printing a trace, maintaining per-opcode counters, or driving custom
 * debugging or testing utilities. For convenience, the
 * {@code com.oracle.truffle.api.bytecode.debug} package provides reference implementations such as
 * a {@link PrintInstructionTracer line printer} and a {@link HistogramInstructionTracer histogram
 * tracer}.
 * <p>
 * <strong>Performance considerations.</strong>
 * <ul>
 * <li>Instruction tracers are subject to partial evaluation, with the tracer reference being a
 * partial-evaluation constant, allowing the tracer to be optimized.
 * <li>Prefer {@link InstructionAccess#getTracedOperationCode(BytecodeNode, int)} for fast-path
 * logic. In generated interpreters this typically avoids allocating an {@link Instruction} object.
 * <li>{@link InstructionAccess#getTracedInstruction(BytecodeNode, int)} materializes an
 * {@link Instruction} object and is therefore more expensive. Use it only when metadata is actually
 * needed, for example to print the instruction name or retrieve source sections.
 * </ul>
 *
 * @see BytecodeRootNodes#addInstructionTracer(InstructionTracer)
 * @see BytecodeDescriptor#addInstructionTracer(TruffleLanguage, InstructionTracer)
 * @since 25.1
 */
public interface InstructionTracer {

    /**
     * Called immediately before executing the instruction at {@code bytecodeIndex} of the given
     * {@code bytecode} node.
     *
     * @param access accessor to query information about the current instruction, valid only during
     *            this call
     * @param bytecode the {@link BytecodeNode} currently being interpreted
     * @param bytecodeIndex the bytecode index (BCI) of the instruction to be executed
     * @param frame the current frame for the root being interpreted
     * @since 25.1
     */
    void onInstructionEnter(InstructionAccess access, BytecodeNode bytecode, int bytecodeIndex, Frame frame);

    /**
     * Returns the {@link BytecodeDescriptor} that this tracer is exclusive for, or
     * <code>null</code> if this instruction tracer may be used with any bytecode descriptor.
     *
     * @since 25.1
     */
    default BytecodeDescriptor<?, ?, ?> getExclusiveBytecodeDescriptor() {
        return null;
    }

    /**
     * Accessor for properties of the instruction about to execute.
     * <p>
     * Instances of this class are provided by the generated interpreter and are scoped to a single
     * {@link InstructionTracer#onInstructionEnter} invocation. They must not be stored, shared
     * across threads, or used after the callback returns.
     *
     * @since 25.1
     */
    abstract class InstructionAccess {

        /**
         * Internal constructor for generated code.
         *
         * @since 25.1
         */
        protected InstructionAccess(Object token) {
            BytecodeRootNodes.checkToken(token);
        }

        /**
         * Materializes the {@link Instruction} object for the instruction at the given bytecode
         * index.
         *
         * @param bytecode the {@link BytecodeNode} currently being interpreted
         * @param bytecodeIndex the bytecode index (BCI) of the instruction
         * @return the materialized {@link Instruction} representing the instruction at {@code BCI}
         * @since 25.1
         */
        public abstract Instruction getTracedInstruction(BytecodeNode bytecode, int bytecodeIndex);

        /**
         * Returns the operation code for the instruction at the given bytecode index, without
         * allocating an {@link Instruction} object.
         * <p>
         * The returned value corresponds to {@link Instruction#getOperationCode()} for the same
         * instruction.
         *
         * @param bytecode the {@link BytecodeNode} currently being interpreted
         * @param bytecodeIndex the bytecode index (BCI) of the instruction
         * @return the integer operation code of the instruction at {@code BCI}
         * @see Instruction#getOperationCode()
         * @since 25.1
         */
        public abstract int getTracedOperationCode(BytecodeNode bytecode, int bytecodeIndex);

    }

}
