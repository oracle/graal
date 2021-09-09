/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode;

import com.oracle.truffle.llvm.DefaultLoader;
import com.oracle.truffle.llvm.nativemode.NativeConfigurationFactory.Key;
import com.oracle.truffle.llvm.nativemode.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.nativemode.runtime.memory.LLVMNativeMemory;
import com.oracle.truffle.llvm.parser.factories.BasicIntrinsicsProvider;
import com.oracle.truffle.llvm.parser.factories.BasicNodeFactory;
import com.oracle.truffle.llvm.parser.factories.BasicPlatformCapability;
import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMIntrinsicProvider;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.LLVMLanguage.Loader;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.NodeFactory;
import com.oracle.truffle.llvm.runtime.PlatformCapability;
import com.oracle.truffle.llvm.runtime.ToolchainConfig;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.LLVMCapability;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

public class NativeConfiguration implements Configuration {

    private final Loader loader;
    private final LLVMIntrinsicProvider intrinsicProvider;
    private final PlatformCapability<?> platformCapability;

    protected NativeConfiguration(LLVMLanguage language, ContextExtension.Registry ctxExtRegistry, Key key) {
        loader = new DefaultLoader();
        intrinsicProvider = new BasicIntrinsicsProvider(language);
        platformCapability = BasicPlatformCapability.create(key.loadCxxLibraries);
        if (key.enableNFI) {
            ctxExtRegistry.register(NativeContextExtension.class, new NFIContextExtension.Factory());
        }
    }

    @Override
    public NodeFactory createNodeFactory(LLVMLanguage language, DataLayout dataLayout) {
        return new BasicNodeFactory(language, dataLayout);
    }

    @Override
    @SuppressWarnings("deprecation")
    public <C extends LLVMCapability> C getCapability(Class<C> type) {
        if (type == LLVMMemory.class) {
            return type.cast(LLVMNativeMemory.getInstance());
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
