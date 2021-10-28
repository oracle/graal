/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.toolchain.launchers.windows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.toolchain.launchers.common.ClangLike;
import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

public final class WindowsLinker extends Driver {

    public static final String LLD_LINK = "lld-link.exe";
    public static final String LLD_LINK_NO_EXE = "lld-link";

    private WindowsLinker() {
        super(LLD_LINK);
    }

    public static List<String> getLinkerFlags() {
        return Arrays.asList("-mllvm:-lto-embed-bitcode=optimized", "-opt:lldlto=0", "-debug:dwarf");
    }

    public static void link(String[] args) {
        new WindowsLinker().doLink(args);
    }

    private void doLink(String[] args) {
        List<String> sulongArgs = new ArrayList<>();
        sulongArgs.add(exe);
        sulongArgs.add("-libpath:" + getSulongHome().resolve(ClangLike.NATIVE_PLATFORM).resolve("lib"));
        sulongArgs.addAll(WindowsLinker.getLinkerFlags());
        List<String> userArgs = Arrays.asList(args);
        boolean verbose = userArgs.contains("-verbose");
        runDriver(sulongArgs, userArgs, verbose, false, false);
    }

}
