/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.instrumentation.StandardTags;
import java.io.PrintStream;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.global.LLVMGlobalVariableAccess;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public abstract class LLVMNode extends Node {
    public static final int DOUBLE_SIZE_IN_BYTES = 8;
    public static final int FLOAT_SIZE_IN_BYTES = 4;

    public static final int I16_SIZE_IN_BYTES = 2;
    public static final int I16_SIZE_IN_BITS = 16;
    public static final int I16_MASK = 0xffff;

    public static final int I32_SIZE_IN_BYTES = 4;
    public static final int I32_SIZE_IN_BITS = 32;
    public static final long I32_MASK = 0xffffffffL;

    public static final int I64_SIZE_IN_BYTES = 8;
    public static final int I64_SIZE_IN_BITS = 64;

    public static final int I8_SIZE_IN_BITS = 8;
    public static final int I8_MASK = 0xff;

    public static final int ADDRESS_SIZE_IN_BYTES = 8;

    public final ContextReference<LLVMContext> getContextReference() {
        return getRootNode().getLanguage(LLVMLanguage.class).getContextReference();
    }

    public final LLVMLanguage getLLVMLanguage() {
        return getRootNode().getLanguage(LLVMLanguage.class);
    }

    protected static final LLVMGlobalVariableAccess createGlobalAccess() {
        return new LLVMGlobalVariableAccess();
    }

    protected static PrintStream debugStream(ContextReference<LLVMContext> context) {
        return SulongEngineOption.getStream(context.get().getEnv().getOptions().get(SulongEngineOption.DEBUG));
    }

    protected static boolean debugEnabled(ContextReference<LLVMContext> context) {
        return SulongEngineOption.isTrue(context.get().getEnv().getOptions().get(SulongEngineOption.DEBUG));
    }

    protected static PrintStream nativeCallStatisticsStream(ContextReference<LLVMContext> context) {
        return SulongEngineOption.getStream(context.get().getEnv().getOptions().get(SulongEngineOption.NATIVE_CALL_STATS));
    }

    protected static boolean nativeCallStatisticsEnabled(ContextReference<LLVMContext> context) {
        return SulongEngineOption.isTrue(context.get().getEnv().getOptions().get(SulongEngineOption.NATIVE_CALL_STATS));
    }

    @Override
    protected boolean isTaggedWith(Class<?> tag) {
        // only nodes that have a SourceSection attached are considered to be tagged by any
        // anything, for sulong only those nodes that actually represent source language statements
        // should have one
        return tag == StandardTags.StatementTag.class || super.isTaggedWith(tag);
    }
}
