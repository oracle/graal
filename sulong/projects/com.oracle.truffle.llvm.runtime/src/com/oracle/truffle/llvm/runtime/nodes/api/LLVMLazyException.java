/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.api;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.except.LLVMException;
import com.oracle.truffle.llvm.runtime.memory.LLVMAllocateNode;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLazyExceptionFactory.LazyExceptionExpressionNodeGen;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMLazyExceptionFactory.LazyExceptionStatementNodeGen;

/**
 * Factories for helper nodes that lazily throw exceptions at runtime. These nodes can be used by
 * the parser for recording parser errors that should only fail lazily when the code containing the
 * error is executed.
 */
public abstract class LLVMLazyException {

    @FunctionalInterface
    public interface LLVMExceptionFactory<E extends Throwable> {

        LLVMException create(Node node, E ex);
    }

    public static <E extends Throwable> LLVMAllocateNode createAllocateNode(LLVMExceptionFactory<E> factory, E cause) {
        return LazyExceptionExpressionNodeGen.create(new ExceptionThrower<>(factory, cause));
    }

    public static <E extends Throwable> LLVMExpressionNode createExpressionNode(LLVMExceptionFactory<E> factory, E cause) {
        return LazyExceptionExpressionNodeGen.create(new ExceptionThrower<>(factory, cause));
    }

    public static <E extends Throwable> LLVMStatementNode createStatementNode(LLVMExceptionFactory<E> factory, E cause) {
        return LazyExceptionStatementNodeGen.create(new ExceptionThrower<>(factory, cause));
    }

    static class ExceptionThrower<E extends Throwable> {

        private final LLVMExceptionFactory<E> factory;
        private final E cause;

        ExceptionThrower(LLVMExceptionFactory<E> factory, E cause) {
            this.factory = factory;
            this.cause = cause;
        }

        LLVMException doThrow(Node node) {
            CompilerDirectives.transferToInterpreter();
            throw factory.create(node, cause);
        }
    }

    abstract static class LazyExceptionExpressionNode extends LLVMExpressionNode implements LLVMAllocateNode {

        private final ExceptionThrower<?> thrower;

        LazyExceptionExpressionNode(ExceptionThrower<?> thrower) {
            this.thrower = thrower;
        }

        @Specialization
        Object doThrow() {
            throw thrower.doThrow(this);
        }
    }

    abstract static class LazyExceptionStatementNode extends LLVMStatementNode {

        private final ExceptionThrower<?> thrower;

        LazyExceptionStatementNode(ExceptionThrower<?> thrower) {
            this.thrower = thrower;
        }

        @Specialization
        void doThrow() {
            throw thrower.doThrow(this);
        }
    }
}
