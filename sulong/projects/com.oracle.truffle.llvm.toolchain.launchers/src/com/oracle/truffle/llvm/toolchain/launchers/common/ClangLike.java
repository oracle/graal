/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates.
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
        new ClangLike(args, ClangLikeBase.Tool.ClangXX, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClang(String[] args) {
        new ClangLike(args, ClangLikeBase.Tool.Clang, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClangCL(String[] args) {
        new ClangLike(args, ClangLikeBase.Tool.ClangCL, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runFlang(String[] args) {
        new ClangLike(args, ClangLikeBase.Tool.Flang, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    protected ClangLike(String[] args, ClangLikeBase.Tool tool, OS os, Arch arch, String platform) {
        super(args, tool, os, arch, platform);
    }

    @Override
    protected void getCompilerArgs(List<String> sulongArgs) {
        sulongArgs.add("-I" + getSulongHome().resolve(platform).resolve("include"));
        sulongArgs.add("-I" + getSulongHome().resolve("include"));
        // Add libc++ unconditionally as C++ might be compiled via clang [GR-23036]
        sulongArgs.add("-stdlib=libc++");
        // Suppress warning because of libc++
        if (tool != Tool.Flang) {
            sulongArgs.add("-Wno-unused-command-line-argument");
        }
        if (tool != Tool.ClangCL) {
            // clang-cl does not support any of these when using CMakeTestCCompiler.cmake
            // set CMAKE_C_FLAGS or CMAKE_CXX_FLAGS instead
            super.getCompilerArgs(sulongArgs);
        }
    }

    @Override
    protected void getLinkerArgs(List<String> sulongArgs) {
        sulongArgs.add("-L" + getSulongHome().resolve(platform).resolve("lib"));
        if (os == OS.DARWIN && tool == Tool.ClangXX) {
            // for some reason `-lc++` does not pull in libc++abi, force it instead
            sulongArgs.add("-lc++abi");
        }
        super.getLinkerArgs(sulongArgs);
    }
}
