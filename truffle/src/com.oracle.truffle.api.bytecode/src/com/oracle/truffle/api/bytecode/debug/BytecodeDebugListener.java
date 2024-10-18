/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode.debug;

import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instruction;

/**
 * Base interface for a bytecode root node to get additional debug event that are normally not
 * available. Useful for testing and debugging.
 * <p>
 * Warning: Do not deploy with implementing this listener in production. It causes severe
 * performance degradation.
 * <p>
 * The debug listener can also be explicitly disabled by setting
 * {@link GenerateBytecode#enableBytecodeDebugListener()} to <code>false</code> .
 *
 * @since 24.2
 */
@SuppressWarnings("unused")
public interface BytecodeDebugListener {

    /**
     * Invoked before an instruction is executed. This has a very significant performance cost. Only
     * override this method temporarily for debugging. This method may be called on partial
     * evaluated code paths.
     *
     * @since 24.2
     */
    default void beforeRootExecute(Instruction enterInstruction) {
    }

    /**
     * Invoked before an instruction is executed. This has a very significant performance cost. Only
     * override this method temporarily for debugging. This method may be called on partial
     * evaluated code paths.
     *
     * @since 24.2
     */
    default void afterRootExecute(Instruction leaveInstruction, Object returnValue, Throwable t) {
    }

    /**
     * Invoked before an instruction is executed. This has a very significant performance cost. Only
     * override this method temporarily for debugging. This method may be called on partial
     * evaluated code paths.
     *
     * @since 24.2
     */
    default void beforeInstructionExecute(Instruction instruction) {
    }

    /**
     * Invoked after an instruction was executed. This has a very significant performance cost. Only
     * override this method temporarily for debugging. This method may be called on partial
     * evaluated code paths.
     *
     * @since 24.2
     */
    default void afterInstructionExecute(Instruction instruction, Throwable exception) {
    }

    /**
     * Invoked when an operation or instrumentation specializes itself.
     *
     * @since 24.2
     */
    default void onSpecialize(Instruction instruction, String specialization) {
    }

    /**
     * Invoked when a bytecode node performs an on-stack transition. On stack transitions may happen
     * if additional bytecode or tag instrumentations are applied during execution while the current
     * method is on-stack.
     *
     * @since 24.2
     */
    default void onBytecodeStackTransition(Instruction source, Instruction target) {
    }

    /**
     * Invoked when an instruction is invalidated. Instructions are invalidated to make bytecode
     * nodes leave the current bytecode loop and update its own bytecodes.
     *
     * @since 24.2
     */
    default void onInvalidateInstruction(Instruction before, Instruction after) {
    }

    /**
     * Invoked when an instruction was quickened.
     *
     * @since 24.2
     */
    default void onQuicken(Instruction before, Instruction after) {
    }

    /**
     * Invoked when an operand was quickened due to boxing elimination.
     *
     * @since 24.2
     */
    default void onQuickenOperand(Instruction baseInstruction, int operandIndex, Instruction operandBefore, Instruction operandAfter) {
    }

}
