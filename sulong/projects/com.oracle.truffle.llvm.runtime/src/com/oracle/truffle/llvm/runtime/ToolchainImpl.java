/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.llvm.api.Toolchain;

public final class ToolchainImpl implements Toolchain {

    private final ToolchainConfig toolchainConfig;
    private final LLVMLanguage language;

    public ToolchainImpl(ToolchainConfig toolchainConfig, LLVMLanguage language) {
        this.toolchainConfig = toolchainConfig;
        this.language = language;
    }

    @Override
    public TruffleFile getToolPath(String tool) {
        if (toolchainConfig == null) {
            return null;
        }
        TruffleLanguage.Env env = language.getContextReference().get().getEnv();
        String toolchainRoot = toolchainConfig.getToolchainRootOverride();
        TruffleFile root = toolchainRoot != null //
                        ? env.getInternalTruffleFile(toolchainRoot)
                        : env.getInternalTruffleFile(language.getLLVMLanguageHome()).resolve(toolchainConfig.getToolchainSubdir());
        switch (tool) {
            case "CC":
                return root.resolve("bin").resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-clang");
            case "CXX":
                return root.resolve("bin").resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-clang++");
            default:
                return null;
        }
    }

    @Override
    public String getIdentifier() {
        if (toolchainConfig == null) {
            return null;
        }
        return toolchainConfig.getToolchainSubdir();
    }
}
