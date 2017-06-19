/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.options;

import java.nio.file.Paths;
import java.util.ServiceLoader;

import com.oracle.truffle.llvm.option.OptionSummary;

public final class LLVMOptions {

    public static final SulongEngineOptionGen ENGINE = SulongEngineOptionGen.create();
    public static final SulongDebugOptionGen DEBUG = SulongDebugOptionGen.create();

    static {
        registerOptions();
        OptionSummary.checkForInvalidOptionNames();
    }

    private static void registerOptions() {
        ServiceLoader<LLVMOptionServiceProvider> loader = ServiceLoader.load(LLVMOptionServiceProvider.class);
        for (LLVMOptionServiceProvider definitions : loader) {
            definitions.getClass(); // ensure class is loaded.
        }
    }

    public static void main(String[] args) {
        OptionSummary.printOptions();
    }

    public static String[] getNativeLibraries() {
        String graalHome = System.getProperty("graalvm.home");
        String[] userLibraries = LLVMOptions.ENGINE.dynamicNativeLibraryPath();
        if (graalHome != null) {
            String defaultPath;
            if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                defaultPath = Paths.get(graalHome, "language", "llvm", "libsulong.so").toAbsolutePath().toString();
            } else {
                defaultPath = Paths.get(graalHome, "language", "llvm", "libsulong.dylib").toAbsolutePath().toString();
            }
            String[] prependedUserLibraries = new String[userLibraries.length + 1];
            System.arraycopy(userLibraries, 0, prependedUserLibraries, 1, userLibraries.length);
            prependedUserLibraries[0] = defaultPath;
            return prependedUserLibraries;
        } else {
            return userLibraries;
        }
    }

    public static String[] getBitcodeLibraries() {
        String graalHome = System.getProperty("graalvm.home");
        String[] userLibraries = LLVMOptions.ENGINE.dynamicBitcodeLibraries();
        if (graalHome != null) {
            String defaultPath = Paths.get(graalHome, "language", "llvm", "libsulong.bc").toAbsolutePath().toString();
            String[] prependedUserLibraries = new String[userLibraries.length + 1];
            System.arraycopy(userLibraries, 0, prependedUserLibraries, 1, userLibraries.length);
            prependedUserLibraries[0] = defaultPath;
            return prependedUserLibraries;
        } else {
            return userLibraries;
        }
    }

}
