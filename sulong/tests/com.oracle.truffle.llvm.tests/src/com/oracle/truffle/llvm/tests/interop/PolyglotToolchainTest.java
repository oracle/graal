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
package com.oracle.truffle.llvm.tests.interop;

import java.io.IOException;
import java.util.stream.Collectors;

import com.oracle.truffle.api.TruffleOptions;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.llvm.api.Toolchain;
import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;

public class PolyglotToolchainTest extends InteropTestBase {

    private static Toolchain getToolchain() {
        TruffleLanguage.Env env = runWithPolyglot.getTruffleTestEnv();
        LanguageInfo llvmInfo = env.getInternalLanguages().get("llvm");
        return env.lookup(llvmInfo, Toolchain.class);
    }

    private static Value testLibrary;

    @BeforeClass
    public static void checkAOT() {
        Assume.assumeFalse("skipping host interop test in native mode", TruffleOptions.AOT);
    }

    @BeforeClass
    public static void loadTestBitcode() {
        testLibrary = InteropTestBase.loadTestBitcodeValue("polyglotToolchain.c");
    }

    @Test
    public void testCC() throws IOException {
        try (CaptureOutput out = new CaptureNativeOutput()) {
            testLibrary.getMember("print_cc").execute();
            Assert.assertEquals(String.format("CC=%s\n", getToolchain().getToolPath("CC").toString()), out.getStdOut());
        }
    }

    @Test
    public void testLDLibraryPath() throws IOException {
        try (CaptureOutput out = new CaptureNativeOutput()) {
            testLibrary.getMember("print_ld_library_path").execute();
            Assert.assertEquals(String.format("LD_LIBRARY_PATH=%s\n",
                            getToolchain().getPaths("LD_LIBRARY_PATH").stream().map(TruffleFile::toString).collect(Collectors.joining(":"))),
                            out.getStdOut());
        }
    }

    @Test
    public void testIdentifier() throws IOException {
        try (CaptureOutput out = new CaptureNativeOutput()) {
            testLibrary.getMember("print_id").execute();
            Assert.assertEquals(String.format("ID=%s\n", getToolchain().getIdentifier().toString()), out.getStdOut());
        }
    }
}
