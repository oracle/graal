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

package com.oracle.truffle.llvm.api;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;

/**
 * A toolchain provides access to a set of tools which are used to translate an input file into a
 * source format that can be executed. Its purpose is to support build systems that are executed by
 * users, for example to compile and install additional libraries.
 *
 * <h4>Example:</h4> {@codesnippet toolchain-example}
 */
public interface Toolchain {

    /**
     * Gets the path to the executable for a given tool. Every implementation is free to choose its
     * own set of supported tools. The command line interface of the executable is specific to the
     * tool. If a tool is not supported or not known, {@code null} must be returned.
     *
     * <dl>
     * <dt><code>CC</code></dt>
     * <dd>A C compiler with a <code>clang</code>-like command line interface.</dd>
     * <dt><code>CXX</code></dt>
     * <dd>A C++ compiler with a <code>clang++</code>-like command line interface.</dd>
     * </dl>
     *
     * Note that not all toolchains support all tools.
     */
    TruffleFile getToolPath(String tool);

    /**
     * Returns an identifier for the toolchain. It can be used to distinguish results produced by
     * different toolchains. Since the identifier may be used as a path suffix to place results in
     * distinct locations, it should not contain special characters like slashes {@code /} or
     * {@code \}.
     */
    String getIdentifier();
}

abstract class ToolchainExampleSnippet {

    private ToolchainExampleSnippet() {
    }

    /**
     * Example for using the {@link Toolchain} to run {@code make} via a {@link ProcessBuilder}.
     */
    int runMake(TruffleLanguage.Env env) throws Exception {
        // BEGIN: toolchain-example
        LanguageInfo llvmInfo = env.getInternalLanguages().get("llvm");
        Toolchain toolchain = env.lookup(llvmInfo, Toolchain.class);
        String id = toolchain.getIdentifier();
        TruffleFile cc = toolchain.getToolPath("CC");
        TruffleFile cxx = toolchain.getToolPath("CXX");

        String[] args = {"make", "CC=" + cc, "CXX=" + cxx, "OUTPUT_DIR=" + id};
        Process p = env.newProcessBuilder(args).start();
        p.waitFor();
        // END: toolchain-example
        return p.exitValue();
    }
}
