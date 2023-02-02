/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ToolchainImpl implements Toolchain {

    private static final String[] WINDOWS_EXECUTABLE_EXTENSIONS = {".exe", ".cmd"};
    private final ToolchainConfig toolchainConfig;
    private final LLVMLanguage language;

    public ToolchainImpl(ToolchainConfig toolchainConfig, LLVMLanguage language) {
        this.toolchainConfig = toolchainConfig;
        this.language = language;
    }

    @Override
    public TruffleFile getToolPath(String tool) {
        TruffleFile res = getToolPathImpl(tool);
        if (LLVMInfo.SYSNAME.toLowerCase().contains("windows")) {
            /*
             * On Windows the tools have either a .exe or a .cmd suffix. ATM there is no other way
             * than to see if the file exists.
             */
            TruffleFile parent = res.getParent();
            String name = res.getName();
            for (String ext : WINDOWS_EXECUTABLE_EXTENSIONS) {
                TruffleFile fileWithExt = parent.resolve(name + ext);
                try {
                    if (fileWithExt.exists()) {
                        return fileWithExt;
                    }
                } catch (Throwable e) {
                    // ignore if call to exists() fails
                }
            }
        }
        return res;
    }

    /**
     * Please keep this list in sync with Toolchain.java (method documentation) and mx_sulong.py's
     * ToolchainConfig::_tool_map.
     */
    private TruffleFile getToolPathImpl(String tool) {

        if (toolchainConfig == null) {
            return null;
        }

        final TruffleFile binPrefix = getWrappersRoot().resolve("bin");
        switch (tool) {
            case "PATH":
                return binPrefix;
            case "CC":
                return binPrefix.resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-clang");
            case "CL":
                if (!toolchainConfig.enableCL()) {
                    return null;
                }
                return binPrefix.resolve("graalvm-" + toolchainConfig.getToolchainSubdir() + "-clang-cl");
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

    @Override
    public List<TruffleFile> getPaths(String pathName) {
        if (toolchainConfig == null) {
            return null;
        }

        switch (pathName) {
            case "PATH":
                return Collections.unmodifiableList(Arrays.asList(getWrappersRoot().resolve("bin")));
            case "LD_LIBRARY_PATH":
                return Collections.unmodifiableList(Arrays.asList(getSysroot().resolve("lib")));
            default:
                return null;
        }
    }

    /**
     * Returns the directory where the toolchain wrappers live.
     *
     * Usually this is the same as {@link #getSysroot}, but for the bootstrap toolchain the wrappers
     * are in a different location.
     */
    private TruffleFile getWrappersRoot() {
        TruffleLanguage.Env env = LLVMLanguage.getContext().getEnv();
        String toolchainRoot = toolchainConfig.getToolchainRootOverride();
        return toolchainRoot != null //
                        ? env.getInternalTruffleFile(toolchainRoot)
                        : getSysroot();
    }

    /**
     * Returns the "sysroot" of the toolchain. That is the directory where the toolchain specific
     * header files an libraries are located.
     */
    private TruffleFile getSysroot() {
        TruffleLanguage.Env env = LLVMLanguage.getContext().getEnv();
        return env.getInternalTruffleFile(language.getLLVMLanguageHome()).resolve(toolchainConfig.getToolchainSubdir());
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
