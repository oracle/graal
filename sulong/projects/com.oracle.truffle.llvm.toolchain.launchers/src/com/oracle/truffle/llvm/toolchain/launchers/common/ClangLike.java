/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.toolchain.launchers.common;

import java.util.List;

public class ClangLike extends ClangLikeBase {

    public static void runClangXX(String[] args) {
        new ClangLike(args, true, OS.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClang(String[] args) {
        new ClangLike(args, false, OS.getCurrent(), NATIVE_PLATFORM).run();
    }

    protected ClangLike(String[] args, boolean cxx, OS os, String platform) {
        super(args, cxx, os, platform);
    }

    @Override
    protected void getCompilerArgs(List<String> sulongArgs) {
        sulongArgs.add("-I" + getSulongHome().resolve(platform).resolve("include"));
        sulongArgs.add("-I" + getSulongHome().resolve("include"));
        // Add libc++ unconditionally as C++ might be compiled via clang [GR-23036]
        sulongArgs.add("-stdlib=libc++");
        // Suppress warning because of libc++
        sulongArgs.add("-Wno-unused-command-line-argument");
        super.getCompilerArgs(sulongArgs);
    }

    @Override
    protected void getLinkerArgs(List<String> sulongArgs) {
        sulongArgs.add("-L" + getSulongHome().resolve(platform).resolve("lib"));
        super.getLinkerArgs(sulongArgs);
    }
}
