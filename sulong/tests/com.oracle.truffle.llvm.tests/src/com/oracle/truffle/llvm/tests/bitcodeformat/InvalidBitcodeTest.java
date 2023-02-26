/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.llvm.tests.CommonTestUtils;
import com.oracle.truffle.llvm.tests.TestCaseCollector;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(CommonTestUtils.ExcludingParametersFactory.class)
public class InvalidBitcodeTest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule();

    private static final Path testBase = Paths.get(TestOptions.getTestDistribution("SULONG_EMBEDDED_TEST_SUITES"), "invalid");

    private static void runCandidate(Path candidateBinary) throws IOException {
        assertTrue("File " + candidateBinary.toAbsolutePath().toFile() + " does not exist.",
                        candidateBinary.toAbsolutePath().toFile().exists());

        Source source = Source.newBuilder("llvm", candidateBinary.toFile()).build();
        Builder builder = Context.newBuilder();
        try (Context context = builder.allowAllAccess(true).build()) {
            context.eval(source);
        }
    }

    @Parameters(name = "{1}")
    public static Collection<Object[]> data() throws IOException {
        TestCaseCollector.ExcludeMap excluded = TestCaseCollector.getExcludedTests(InvalidBitcodeTest.class);
        return Files.list(testBase).map(f -> new Object[]{f, f.getFileName().toString(), excluded.get(f.getFileName().toString())}).collect(
                        Collectors.toList());
    }

    @Parameter(value = 0) public Path path;
    @Parameter(value = 1) public String testName;
    @Parameter(value = 2) public String exclusionReason;

    @Test(expected = PolyglotException.class)
    public void test() throws IOException {
        runCandidate(path);
    }
}
