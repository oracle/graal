/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.oracle.truffle.llvm.toolchain.launchers.darwin.DarwinLinker;
import com.oracle.truffle.llvm.toolchain.launchers.linux.LinuxLinker;

public abstract class ClangLikeBase extends Driver {

    public static final String NATIVE_PLATFORM = "native";
    public static final String XCRUN = "/usr/bin/xcrun";
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
    protected final int outputFlagPos;
    protected final boolean nostdincxx;

    protected ClangLikeBase(String[] args, boolean cxx, OS os, String platform) {
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
        boolean noStdIncxx = false;
        int outputFlagIdx = -1;
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
                case "-o":
                    outputFlagIdx = i;
                    break;
                case "-v":
                    isVerbose = true;
                    break;
                case "--help":
                    showHelp = true;
                    break;
                case "-fembed-bitcode":
                    String s = "-fembed-bitcode";
                    unsupportedFlagExit(s);
                    break;
                case "--graalvm-help":
                    showHelp = true;
                    shouldExitEarly = true;
                    break;
                case "--graalvm-print-cmd":
                    isVerbose = true;
                    shouldExitEarly = true;
                    break;
                case "-nostdinc++":
                    noStdIncxx = true;
                    break;
                case "-Wl,--gc-sections":
                case "-Wa,--noexecstack":
                    keepArgs = false;
                    args[i] = null;
                    break;
            }
            if (arg.startsWith("-fuse-ld=")) {
                unsupportedFlagExit("-fuse-ld");
            }
        }
        this.args = keepArgs ? args : Arrays.stream(args).filter(Objects::nonNull).toArray(String[]::new);
        this.needLinkerFlags = mayBeLinkerInvocation & mayHaveInputFiles;
        this.needCompilerFlags = mayHaveInputFiles;
        this.verbose = isVerbose;
        this.help = showHelp;
        this.earlyExit = shouldExitEarly;
        this.outputFlagPos = outputFlagIdx;
        this.nostdincxx = noStdIncxx;
    }

    private void unsupportedFlagExit(String flag) {
        System.err.println("Using `" + flag + "` is not supported.");
        System.err.println("Try calling the the wrapped tool directly: " + exe);
        System.exit(1);
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

    protected final void run() {
        List<String> sulongArgs = getArgs();
        runDriver(sulongArgs, Arrays.asList(args), verbose, help, earlyExit);
    }

    protected List<String> getArgs() {
        List<String> sulongArgs = new ArrayList<>();
        if (os == OS.DARWIN && Files.isExecutable(Paths.get(XCRUN)) && Files.isExecutable(Paths.get(exe))) {
            sulongArgs.add(XCRUN);
        }
        sulongArgs.add(exe);

        // compiler flags
        if (needCompilerFlags) {
            getCompilerArgs(sulongArgs);
        }
        // linker flags
        if (needLinkerFlags) {
            getLinkerArgs(sulongArgs);
        }
        return sulongArgs;
    }

    protected void getCompilerArgs(List<String> sulongArgs) {
        sulongArgs.addAll(Arrays.asList("-flto=full", "-g", "-O1"));
    }

    protected void getLinkerArgs(List<String> sulongArgs) {
        if (os == OS.LINUX) {
            sulongArgs.addAll(Arrays.asList("-fuse-ld=" + getLLVMExecutable(LinuxLinker.LLD), "-Wl," + String.join(",", LinuxLinker.getLinkerFlags())));
        } else if (os == OS.DARWIN) {
            sulongArgs.add("-fuse-ld=" + DarwinLinker.LD);
        }
    }

}
