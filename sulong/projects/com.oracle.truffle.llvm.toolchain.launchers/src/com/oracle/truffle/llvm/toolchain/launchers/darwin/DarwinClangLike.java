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
package com.oracle.truffle.llvm.toolchain.launchers.darwin;

import java.util.List;

import com.oracle.truffle.llvm.toolchain.launchers.common.ClangLike;
import com.oracle.truffle.llvm.toolchain.launchers.common.ClangLikeBase;

public class DarwinClangLike extends ClangLike {

    protected DarwinClangLike(String[] args, ClangLikeBase.Tool tool, OS os, Arch arch, String platform) {
        super(args, tool, os, arch, platform);
    }

    public static void runClangXX(String[] args) {
        new DarwinClangLike(args, ClangLikeBase.Tool.ClangXX, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClang(String[] args) {
        new DarwinClangLike(args, ClangLikeBase.Tool.Clang, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClangCL(String[] args) {
        new DarwinClangLike(args, ClangLikeBase.Tool.ClangCL, OS.getCurrent(), Arch.getCurrent(), NATIVE_PLATFORM).run();
    }

    @Override
    protected void getDebugCompilerArgs(List<String> sulongArgs) {
        // [GR-41537] Use -gdwarf-4 instead of -gdwarf-5, as the latter causes a problem in
        // dsymutil:
        // > https://github.com/llvm/llvm-project/commit/a17c90daf2e7c3b1817ec29ad6648ce89b927f9a
        // This workaround can be removed when LLVM is upgraded to >= 15
        sulongArgs.add("-gdwarf-4");
    }

    @Override
    public void runDriver(List<String> sulongArgs, List<String> userArgs, boolean verb, boolean hlp, boolean earlyexit) {
        DarwinLinker.runDriverWithSaveTemps(this, sulongArgs, userArgs, verb, hlp, earlyexit, "-Wl,", needLinkerFlags, outputFlagPos);
    }

}
