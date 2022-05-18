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
package com.oracle.truffle.llvm.tests.swift;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.oracle.truffle.llvm.tests.CommonTestUtils;
import com.oracle.truffle.llvm.tests.interop.InteropTestBase;
import com.oracle.truffle.llvm.tests.options.TestOptions;

@RunWith(CommonTestUtils.ExcludingTruffleRunner.class)
public class SwiftMethodsTest extends InteropTestBase {

    private static Value testLibrary;

    private static Value objectCreator;
    private static Value parent;
    private static Value child;

    private static final String OUT_FILE_NAME = "swiftMethodsTest";

    @BeforeClass
    public static void loadTestBitcode() {
        final Path basePath = Paths.get(TestOptions.getTestDistribution("SULONG_EMBEDDED_TEST_SUITES"), "swift");
        File file = Paths.get(basePath.toString(), "swiftMethodsTest.swift" + CommonTestUtils.TEST_DIR_EXT, OUT_FILE_NAME).toFile();
        org.graalvm.polyglot.Source source;
        try {
            source = org.graalvm.polyglot.Source.newBuilder("llvm", file).build();
        } catch (IOException ex) {
            throw new AssertionError(ex);
        }
        testLibrary = runWithPolyglot.getPolyglotContext().eval(source);

        objectCreator = testLibrary.getMember("ObjectCreator");
        parent = objectCreator.invokeMember("createParent");
        child = objectCreator.invokeMember("createChild");
    }

    @Test
    public void testMethodsWithoutArguments() {
        Assert.assertEquals(14, parent.invokeMember("get14").asInt());
        Assert.assertEquals(0, Double.compare(3.5, parent.invokeMember("get3P5").asDouble()));
    }

    @Test
    public void testMethodsWithArgument() {
        Assert.assertEquals(36, parent.invokeMember("square", 6).asInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWrongNumberOfArguments() {
        parent.invokeMember("square");
    }

    @Test
    public void testDynamicBinding() {
        Assert.assertEquals(214, child.invokeMember("get14").asInt());
        Assert.assertEquals(0, Double.compare(3.5, child.invokeMember("get3P5").asDouble()));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testNonExistingMethod() {
        parent.invokeMember("methodWhichDoesNotExist");
    }

}
