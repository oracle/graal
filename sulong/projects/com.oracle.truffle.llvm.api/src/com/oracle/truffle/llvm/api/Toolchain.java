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

package com.oracle.truffle.llvm.api;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;

import java.util.List;

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
     * Known tools are:
     * <dl>
     * <dt><code>CC</code></dt>
     * <dd>A C compiler with a <code>clang</code>-like command line interface.</dd>
     * <dt><code>CXX</code></dt>
     * <dd>A C++ compiler with a <code>clang++</code>-like command line interface.</dd>
     * <dt><code>LD</code></dt>
     * <dd>A linker that can deal with object files and bitcode files.</dd>
     * <dt><code>AR</code></dt>
     * <dd>Archiver</dd>
     * <dt><code>NM</code></dt>
     * <dd>Symbol table lister</dd>
     * <dt><code>OBJCOPY</code></dt>
     * <dd>Object copying and editing tool</dd>
     * <dt><code>OBJDUMP</code></dt>
     * <dd>Object file dumper</dd>
     * <dt><code>RANLIB</code></dt>
     * <dd>Archive index generator</dd>
     * <dt><code>READELF</code></dt>
     * <dd>GNU-style object reader</dd>
     * <dt><code>READOBJ</code></dt>
     * <dd>Object reader</dd>
     * <dt><code>STRIP</code></dt>
     * <dd>Object stripping tool</dd>
     * </dl>
     *
     * Note that not all toolchains support all tools.
     */
    TruffleFile getToolPath(String tool);

    /**
     * Returns a list of directories for a given path name. Every implementation is free to choose
     * its own set of supported path names. If a path name is not supported or not known,
     * {@code null} must be returned. Note that the directories returned by this method do not need
     * to exist.
     * <p>
     * Known path names are:
     * <dl>
     * <dt><code>PATH</code></dt>
     * <dd>Directories where the toolchain executables are located.</dd>
     * <dt><code>LD_LIBRARY_PATH</code></dt>
     * <dd>(Additional) directories to look up shared libraries.</dd>
     * </dl>
     */
    List<TruffleFile> getPaths(String pathName);

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
        TruffleFile ld = toolchain.getToolPath("LD");

        String[] args = {"make", "CC=" + cc, "CXX=" + cxx, "LD=" + ld, "OUTPUT_DIR=" + id};
        Process p = env.newProcessBuilder(args).start();
        p.waitFor();
        // END: toolchain-example
        return p.exitValue();
    }
}
