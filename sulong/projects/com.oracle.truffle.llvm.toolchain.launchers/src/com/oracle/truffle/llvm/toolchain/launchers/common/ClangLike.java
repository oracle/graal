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
package com.oracle.truffle.llvm.toolchain.launchers.common;

import com.oracle.truffle.llvm.toolchain.launchers.darwin.DarwinLinker;
import com.oracle.truffle.llvm.toolchain.launchers.linux.LinuxLinker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ClangLike extends Driver {

    public static final String NATIVE_PLATFORM = "native";
    /**
     * Detects whether we are attempting a linker invocation. If not we can omit flags which would
     * cause warnings with clang. This should be conservative and return {@code true} if in doubt.
     */
    protected final boolean needLinkerFlags;
    protected final boolean needCompilerFlags;
    protected final boolean verbose;
    protected final boolean help;
    protected final boolean cxx;
    protected final boolean earlyExit;
    protected final OS os;
    protected final String[] args;
    protected final String platform;

    protected ClangLike(String[] args, boolean cxx, OS os, String platform) {
        super(cxx ? "clang++" : "clang");
        this.cxx = cxx;
        this.os = os;
        this.platform = platform;
        boolean mayHaveInputFiles = false;
        boolean mayBeLinkerInvocation = true;
        boolean isVerbose = false;
        boolean showHelp = false;
        boolean shouldExitEarly = false;
        boolean keepArgs = true;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (!mayHaveInputFiles && !arg.startsWith("-")) {
                // argument starts with "-" -> assume that it is an input file
                mayHaveInputFiles = true;
            }
            if (stageSelectionFlag(arg)) {
                mayBeLinkerInvocation = false;
                continue;
            }
            switch (arg) {
                case "-v":
                    isVerbose = true;
                    break;
                case "--help":
                    showHelp = true;
                    break;
                case "-fembed-bitcode":
                    System.err.println("Using `-fembed-bitcode` is not supported.");
                    System.err.println("Try calling the the wrapped tool directly: " + exe);
                    System.exit(1);
                    break;
                case "--graalvm-help":
                    showHelp = true;
                    shouldExitEarly = true;
                    break;
                case "--graalvm-print-cmd":
                    isVerbose = true;
                    shouldExitEarly = true;
                    break;
                case "-Wl,--gc-sections":
                case "-Wa,--noexecstack":
                    keepArgs = false;
                    args[i] = null;
                    break;
            }
        }
        this.args = keepArgs ? args : Arrays.stream(args).filter(Objects::nonNull).toArray(String[]::new);
        this.needLinkerFlags = mayBeLinkerInvocation & mayHaveInputFiles;
        this.needCompilerFlags = mayHaveInputFiles;
        this.verbose = isVerbose;
        this.help = showHelp;
        this.earlyExit = shouldExitEarly;
    }

    private static boolean stageSelectionFlag(String s) {
        switch (s) {
            case "-E": // preprocessor
            case "-fsyntax-only": // parser and type checker
            case "-S": // assembly generator
            case "-c": // object file generator
                return true;
        }
        return false;
    }

    public static void runClangXX(String[] args) {
        new ClangLike(args, true, OS.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClang(String[] args) {
        new ClangLike(args, false, OS.getCurrent(), NATIVE_PLATFORM).run();
    }

    protected final void run() {
        List<String> sulongArgs = getArgs();
        runDriver(sulongArgs, Arrays.asList(args), verbose, help, earlyExit);
    }

    protected List<String> getArgs() {
        List<String> sulongArgs = new ArrayList<>();
        sulongArgs.add(exe);

        // compiler flags
        if (needCompilerFlags) {
            sulongArgs.addAll(Arrays.asList("-flto=full", "-g", "-O1", "-I" + getSulongHome().resolve("include")));
            // c++ specific flags
            if (cxx) {
                sulongArgs.add("-stdlib=libc++");
            }
        }
        // linker flags
        if (needLinkerFlags) {
            sulongArgs.add("-L" + getSulongHome().resolve(platform).resolve("lib"));
            if (os == OS.LINUX) {
                sulongArgs.addAll(Arrays.asList("-fuse-ld=lld", "-Wl," + String.join(",", LinuxLinker.getLinkerFlags())));
            } else if (os == OS.DARWIN) {
                sulongArgs.add("-fembed-bitcode");
                sulongArgs.add("-Wl," + String.join(",", DarwinLinker.getLinkerFlags(this)));
            }
        }
        return sulongArgs;
    }

}
