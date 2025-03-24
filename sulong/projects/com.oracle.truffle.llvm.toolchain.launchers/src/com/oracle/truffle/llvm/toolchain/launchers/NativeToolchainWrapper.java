/*
 * Copyright (c) 2025, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.toolchain.launchers;

import java.util.List;
import java.util.Map;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context.Builder;

public final class NativeToolchainWrapper extends AbstractLanguageLauncher {

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        /*
         * We do everything here and not in launch() to avoid making more of
         * AbstractLanguageLauncher/Truffle reachable, such as
         * com.oracle.truffle.polyglot.PolyglotLanguageDispatch.
         */

        String[] args = arguments.toArray(new String[0]);

        String toolName = AbstractBinUtil.getProcessName();
        if (toolName == null) {
            System.err.println("Error: Could not figure out process name");
            System.exit(1);
        }

        if (toolName.startsWith("graalvm-native-")) {
            toolName = toolName.substring("graalvm-native-".length());
        } else if (toolName.startsWith("graalvm-")) {
            toolName = toolName.substring("graalvm-".length());
        }

        switch (toolName) {
            case "clang", "cc", "gcc" -> Clang.main(args);
            case "clang++", "c++", "g++" -> ClangXX.main(args);
            case "clang-cl", "cl" -> ClangCL.main(args);
            case "ld", "ld.lld", "lld", "lld-link", "ld64" -> Linker.main(args);
            case "flang-new", "flang" -> Flang.main(args);
            default -> BinUtil.main(args);
        }

        System.exit(0);
        throw new Error("unreachable");
    }

    @Override
    protected void launch(Builder contextBuilder) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected String getLanguageId() {
        return "";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        throw new UnsupportedOperationException();
    }

}
