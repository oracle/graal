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
package com.oracle.truffle.llvm.toolchain.launchers.darwin;

import com.oracle.truffle.llvm.toolchain.launchers.common.ClangLike;
import com.oracle.truffle.llvm.toolchain.launchers.common.Driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class DarwinLinker extends Driver {

    public static final String LD = "/usr/bin/ld";

    private DarwinLinker() {
        super(LD, false);
    }

    public static void link(String[] args) {
        new DarwinLinker().doLink(args);
    }

    private void doLink(String[] args) {
        List<String> sulongArgs = new ArrayList<>();
        sulongArgs.add(exe);
        sulongArgs.add("-L" + getSulongHome().resolve(ClangLike.NATIVE_PLATFORM).resolve("lib"));
        sulongArgs.add("-lto_library");
        sulongArgs.add(getLLVMBinDir().resolve("..").resolve("lib").resolve("libLTO.dylib").toString());
        List<String> userArgs = Arrays.asList(args);
        boolean verbose = userArgs.contains("-v");
        runDriver(sulongArgs, userArgs, verbose, false, false);
    }

    @Override
    public void runDriver(List<String> sulongArgs, List<String> userArgs, boolean verb, boolean hlp, boolean earlyexit) {
        runDriverWithSaveTemps(this, sulongArgs, userArgs, verb, hlp, earlyexit, "", true, userArgs.indexOf("-o"));
    }

    static void runDriverWithSaveTemps(Driver driver, List<String> sulongArgs, List<String> userArgs, boolean verb, boolean hlp, boolean earlyexit, String linkerOptionPrefix, boolean needLinkerFlags,
                    int outputFlagPos) {
        Path tempDir = null;
        int returnCode;
        try {
            if (needLinkerFlags && !earlyexit) {
                try {
                    tempDir = Files.createTempDirectory("graalvm-clang-wrapper");
                    String newOutput = tempDir.resolve("temp.out").toString();
                    List<String> newUserArgs = newUserArgs(userArgs, newOutput, linkerOptionPrefix, outputFlagPos);
                    driver.runDriverReturn(sulongArgs, newUserArgs, verb, hlp, earlyexit);
                    String bcFile = newOutput + ".lto.bc";
                    if (Files.exists(Paths.get(bcFile))) {
                        sulongArgs.add(linkerOptionPrefix + "-sectcreate");
                        sulongArgs.add(linkerOptionPrefix + "__LLVM");
                        sulongArgs.add(linkerOptionPrefix + "__bundle");
                        sulongArgs.add(linkerOptionPrefix + bcFile);
                    }
                } catch (Exception e) {
                    // something went wrong -- let the normal driver run fail
                    if (verb) {
                        System.err.println("Running clang with `-save-temps` failed: " + e);
                    }
                }
            }
            returnCode = driver.runDriverReturn(sulongArgs, userArgs, verb, hlp, earlyexit);
        } catch (IOException e) {
            returnCode = 1;
        } catch (Exception e) {
            System.err.println("Exception: " + e);
            returnCode = 1;
        } finally {
            if (tempDir != null) {
                try {
                    Files.walk(tempDir).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                } catch (IOException e) {
                    if (verb) {
                        System.err.println("Deleting the temporary directory (" + tempDir + ") failed: " + e);
                    }
                }
            }
        }
        // Do not call System.exit from withing the try block, otherwise finally is not executed
        System.exit(returnCode);
    }

    private static List<String> newUserArgs(List<String> userArgs, String newOutput, String linkerOptionPrefix, int outputFlagPos) {
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
        newUserArgs.add(linkerOptionPrefix + "-save-temps");
        return newUserArgs;
    }
}
