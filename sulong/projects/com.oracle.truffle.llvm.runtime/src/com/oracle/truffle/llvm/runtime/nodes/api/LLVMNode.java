/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

import java.io.PrintStream;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLanguage.ContextReference;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMFunctionDescriptor;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

@TypeSystemReference(LLVMTypes.class)
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

    public static final int I8_SIZE_IN_BYTES = 1;
    public static final int I8_SIZE_IN_BITS = 8;
    public static final int I8_MASK = 0xff;

    public static final int I1_SIZE_IN_BYTES = 1;

    public static final int ADDRESS_SIZE_IN_BYTES = 8;

    public static NodeFactory getNodeFactory() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMLanguage.getLanguage().getNodeFactory();
    }

    public static LLVMMemory getLLVMMemory() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMLanguage.getLanguage().getCapability(LLVMMemory.class);
    }

    public static UnsafeArrayAccess getUnsafeArrayAccess() {
        CompilerAsserts.neverPartOfCompilation();
        return LLVMLanguage.getLanguage().getCapability(UnsafeArrayAccess.class);
    }

    protected static PrintStream nativeCallStatisticsStream(ContextReference<LLVMContext> context) {
        CompilerAsserts.neverPartOfCompilation();
        return SulongEngineOption.getStream(context.get().getEnv().getOptions().get(SulongEngineOption.NATIVE_CALL_STATS));
    }

    protected static boolean nativeCallStatisticsEnabled(ContextReference<LLVMContext> context) {
        CompilerAsserts.neverPartOfCompilation();
        return SulongEngineOption.isTrue(context.get().getEnv().getOptions().get(SulongEngineOption.NATIVE_CALL_STATS));
    }

    public boolean hasTag(Class<? extends Tag> tag) {
        // only nodes that have a SourceSection attached are considered to be tagged by any
        // anything, for sulong only those nodes that actually represent source language statements
        // should have one
        return tag == StandardTags.StatementTag.class;
    }

    public LLVMSourceLocation getSourceLocation() {
        return null;
    }

    @Override
    public SourceSection getSourceSection() {
        final LLVMSourceLocation location = getSourceLocation();
        if (location != null) {
            return location.getSourceSection();
        }

        return null;
    }

    protected static boolean isFunctionDescriptor(Object object) {
        return object instanceof LLVMFunctionDescriptor;
    }

    protected static LLVMFunctionDescriptor asFunctionDescriptor(Object object) {
        return object instanceof LLVMFunctionDescriptor ? (LLVMFunctionDescriptor) object : null;
    }

    protected static boolean isSameObject(Object a, Object b) {
        // used as a workaround for a DSL bug
        return a == b;
    }
}
