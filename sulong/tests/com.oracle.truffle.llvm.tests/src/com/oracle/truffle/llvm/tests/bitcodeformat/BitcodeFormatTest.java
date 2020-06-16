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
package com.oracle.truffle.llvm.tests.bitcodeformat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.oracle.truffle.llvm.tests.Platform;
import com.oracle.truffle.llvm.tests.pipe.CaptureNativeOutput;
import com.oracle.truffle.llvm.tests.pipe.CaptureOutput;
import com.oracle.truffle.llvm.tests.util.ProcessUtil;
import org.graalvm.polyglot.Context;

import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runners.Parameterized;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class BitcodeFormatTest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule();

    private static final Path testBase = Paths.get(TestOptions.TEST_SUITE_PATH, "bitcodeformat");

    protected Map<String, String> getContextOptions() {
        return Collections.emptyMap();
    }

    protected Function<Context.Builder, CaptureOutput> getCaptureOutput() {
        return c -> new CaptureNativeOutput();
    }

    private void runCandidate(Path candidateBinary) throws IOException {
        assertTrue("File " + candidateBinary.toAbsolutePath().toFile() + " does not exist.",
                        candidateBinary.toAbsolutePath().toFile().exists());

        ProcessUtil.ProcessResult result;
        result = ProcessUtil.executeSulongTestMain(candidateBinary.toAbsolutePath().toFile(), new String[]{},
                        getContextOptions(), getCaptureOutput());

        int sulongRet = result.getReturnValue();
        assertEquals(0, sulongRet);
        assertEquals("Hello, World!\n", result.getStdOutput());
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() throws IOException {
        Set<String> blacklist = getBlacklist();
        Collection<Object[]> testlist = Files.list(testBase).map(f -> new Object[]{f.getFileName()}).collect(Collectors.toList());
        testlist.removeIf(t -> blacklist.contains(t[0].toString()));
        return testlist;
    }

    protected static Set<String> getBlacklist() {
        Set<String> blacklist = new HashSet<>();

        if (Platform.isAArch64()) {
            blacklist.addAll(Arrays.asList(
                            "hello-linux-link-fembed-bitcode", "hello-linux-link-fembed-bitcode.so"));
        }

        return blacklist;
    }

    @Parameter(0) public Path value;

    @Before
    public void checkOS() {
        Assume.assumeTrue("Linux only test", !Platform.isDarwin() || !value.toString().contains("linux-link"));
        Assume.assumeTrue("Darwin only test", Platform.isDarwin() || !value.toString().contains("darwin-link"));
    }

    @Test
    public void checkNumbers() throws IOException {
        runCandidate(testBase.resolve(value));
    }

}
