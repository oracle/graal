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

    /**
     * Please keep this list in sync with Toolchain.java (method documentation) and mx_sulong.py's
     * ToolchainConfig::_tool_map.
     */
    @Override
    public TruffleFile getToolPath(String tool) {
        if (toolchainConfig == null) {
            return null;
        }

        final TruffleFile binPrefix = getRoot().resolve("bin");
        switch (tool) {
            case "PATH":
                return binPrefix;
            case "CC":
                return binPrefix.resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-clang");
            case "CXX":
                if (!toolchainConfig.enableCXX()) {
                    return null;
                }
                return binPrefix.resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-clang++");
            case "LD":
                return binPrefix.resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-ld");
            case "AR":
            case "NM":
            case "OBJCOPY":
            case "OBJDUMP":
            case "RANLIB":
            case "READELF":
            case "READOBJ":
            case "STRIP":
                return binPrefix.resolve(tool.toLowerCase());
            default:
                return null;
        }
    }

    protected TruffleFile getRoot() {
        TruffleLanguage.Env env = LLVMLanguage.getContext().getEnv();
        String toolchainRoot = toolchainConfig.getToolchainRootOverride();
        return toolchainRoot != null //
                        ? env.getInternalTruffleFile(toolchainRoot)
                        : env.getInternalTruffleFile(language.getLLVMLanguageHome()).resolve(toolchainConfig.getToolchainSubdir());
    }

    @Override
    public String getIdentifier() {
        if (toolchainConfig == null) {
            return null;
        }
        return toolchainConfig.getToolchainSubdir();
    }

    @Override
    public String toString() {
        return toolchainConfig.getClass().getSimpleName();
    }
}
