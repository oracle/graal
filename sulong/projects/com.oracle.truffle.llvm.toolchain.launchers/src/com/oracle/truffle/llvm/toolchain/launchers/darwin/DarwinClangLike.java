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
package com.oracle.truffle.llvm.toolchain.launchers.darwin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.oracle.truffle.llvm.toolchain.launchers.common.ClangLike;

public class DarwinClangLike extends ClangLike {

    protected DarwinClangLike(String[] args, boolean cxx, OS os, String platform) {
        super(args, cxx, os, platform);
    }

    public static void runClangXX(String[] args) {
        new DarwinClangLike(args, true, OS.getCurrent(), NATIVE_PLATFORM).run();
    }

    public static void runClang(String[] args) {
        new DarwinClangLike(args, false, OS.getCurrent(), NATIVE_PLATFORM).run();
    }

    @Override
    public void runDriver(List<String> sulongArgs, List<String> userArgs, boolean verb, boolean hlp, boolean earlyexit) {
        Path tempDirWithPrefix = null;
        try {
            if (needLinkerFlags && !earlyexit) {
                try {
                    tempDirWithPrefix = Files.createTempDirectory("graalvm-clang-wrapper");
                    String newOutput = tempDirWithPrefix.resolve("temp.out").toString();
                    List<String> newUserArgs = newUserArgs(userArgs, newOutput);
                    super.runDriverReturn(sulongArgs, newUserArgs, verb, hlp, earlyexit);
                    String bcFile = newOutput + ".lto.opt.bc";
                    sulongArgs.add("-Wl,-sectcreate,__LLVM,__bundle," + bcFile);
                } catch (Exception e) {
                    // something went wrong -- let the normal driver run fail
                    if (verb) {
                        System.err.println("Running clang with `-save-temps` failed: " + e);
                    }
                }
            }
            super.runDriver(sulongArgs, userArgs, verb, hlp, earlyexit);
        } finally {
            if (tempDirWithPrefix != null) {
                try {
                    Files.walk(tempDirWithPrefix).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    if (verb) {
                        System.err.println("Deleting the temporary directory (" + tempDirWithPrefix + ") failed: " + e);
                    }
                }
            }
        }
    }

    private List<String> newUserArgs(List<String> userArgs, String newOutput) {
        List<String> newUserArgs = new ArrayList<>(userArgs.size() + 2);
        if (outputFlagPos == -1) {
            newUserArgs.addAll(userArgs);
        } else {
            for (int i = 0; i < outputFlagPos; i++) {
                newUserArgs.add(userArgs.get(i));
            }
            for (int i = outputFlagPos + 2; i < userArgs.size(); i++) {
                newUserArgs.add(userArgs.get(i));
            }
        }
        newUserArgs.add("-o");
        newUserArgs.add(newOutput);
        newUserArgs.add("-Wl,-save-temps");
        return newUserArgs;
    }
}
