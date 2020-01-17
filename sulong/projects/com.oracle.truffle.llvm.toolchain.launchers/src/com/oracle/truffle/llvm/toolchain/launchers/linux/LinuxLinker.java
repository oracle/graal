/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.toolchain.launchers.linux;

import com.oracle.truffle.llvm.toolchain.launchers.common.ClangLike;
import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LinuxLinker extends Driver {

    public static final String LLD = "ld.lld";

    private LinuxLinker() {
        super(LLD);
    }

    public static List<String> getLinkerFlags() {
        return Arrays.asList("--mllvm=-lto-embed-bitcode", "--lto-O0");
    }

    public static void link(String[] args) {
        new LinuxLinker().doLink(args);
    }

    private void doLink(String[] args) {
        List<String> sulongArgs = new ArrayList<>();
        sulongArgs.add(exe);
        sulongArgs.add("-L" + getSulongHome().resolve(ClangLike.NATIVE_PLATFORM).resolve("lib"));
        sulongArgs.addAll(LinuxLinker.getLinkerFlags());
        List<String> userArgs = Arrays.asList(args);
        boolean verbose = userArgs.contains("-v");
        runDriver(sulongArgs, userArgs, verbose, false, false);
    }

}
