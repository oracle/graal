/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.api.TruffleLanguage.Env;

/**
 * Context extensions encapsulate optional functionality that has a state and which therefore needs
 * to live on the context-level.
 */
public interface ContextExtension {

    /**
     * This function will be called exactly once per context, at context initialization time.
     */
    default void initialize(@SuppressWarnings("unused") LLVMContext context) {
    }

    /**
     * Key to uniquely identify a {@link ContextExtension}. Can be retrieved from the language using
     * {@link LLVMLanguage#lookupContextExtension}, and is safe to be cached in the AST.
     */
    abstract class Key<C extends ContextExtension> {

        // don't allow subclassing outside of this package
        Key() {
        }

        /**
         * Get a context extension from a context. Safe to be used on the fast-path.
         */
        public abstract C get(LLVMContext ctx);
    }

    abstract class Registry {

        public abstract <C extends ContextExtension> Key<C> register(Class<C> type, Factory<C> factory);
    }

    @FunctionalInterface
    interface Factory<C extends ContextExtension> {

        C create(Env env);
    }
}
