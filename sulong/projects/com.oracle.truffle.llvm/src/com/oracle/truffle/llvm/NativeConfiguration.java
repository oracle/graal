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
package com.oracle.truffle.llvm;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.llvm.NativeConfigurationFactory.Key;
import com.oracle.truffle.llvm.parser.factories.BasicIntrinsicsProvider;
import com.oracle.truffle.llvm.parser.factories.BasicNodeFactory;
import com.oracle.truffle.llvm.parser.factories.BasicPlatformCapability;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.Loader;
import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.ToolchainConfig;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.runtime.memory.UnsafeArrayAccess;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;

public final class NativeConfiguration implements Configuration {

    private final Loader loader;
    private final LLVMIntrinsicProvider intrinsicProvider;
    private final PlatformCapability<?> platformCapability;

    NativeConfiguration(LLVMLanguage language, Key key) {
        loader = new DefaultLoader();
        intrinsicProvider = new BasicIntrinsicsProvider(language);
        platformCapability = BasicPlatformCapability.create(key.loadCxxLibraries);
    }

    @Override
    public NodeFactory createNodeFactory(LLVMContext context, DataLayout dataLayout) {
        return new BasicNodeFactory(context, dataLayout);
    }

    @Override
    public List<ContextExtension> createContextExtensions(TruffleLanguage.Env env) {
        List<ContextExtension> result = new ArrayList<>();
        if (env.getOptions().get(SulongEngineOption.ENABLE_NFI)) {
            result.add(new NFIContextExtension(env));
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public <C extends LLVMCapability> C getCapability(Class<C> type) {
        if (type == LLVMMemory.class) {
            return type.cast(LLVMNativeMemory.getInstance());
        } else if (type == UnsafeArrayAccess.class) {
            return type.cast(UnsafeArrayAccess.getInstance());
        } else if (type == ToolchainConfig.class) {
            return type.cast(NativeToolchainConfig.getInstance());
        } else if (type == Loader.class) {
            return type.cast(loader);
        } else if (type == LLVMIntrinsicProvider.class) {
            return type.cast(intrinsicProvider);
        } else if (type == PlatformCapability.class) {
            return type.cast(platformCapability);
        }
        return null;
    }
}
