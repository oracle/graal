/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.tests.interop.nfi;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.tests.CommonTestUtils;
import com.oracle.truffle.llvm.tests.interop.InteropTestBase;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.nfi.api.SignatureLibrary;

public class NFIAPITest {

    @ClassRule public static CommonTestUtils.RunWithTestEngineConfigRule runWithPolyglot = new CommonTestUtils.RunWithTestEngineConfigRule(InteropTestBase::updateContextBuilder);

    private static final Path TEST_DIR = Paths.get(TestOptions.getTestDistribution("SULONG_EMBEDDED_TEST_SUITES"), "nfi");

    public static Object sulongObject;

    private static InteropLibrary INTEROP = InteropLibrary.getUncached();
    private static SignatureLibrary SIGNATURES = SignatureLibrary.getUncached();

    @BeforeClass
    public static void initialize() {
        TestOptions.assumeBundledLLVM();
        sulongObject = loadLibrary("basicTest.c.dir");
    }

    private static Object loadLibrary(String lib) {
        File file = new File(TEST_DIR.toFile(), lib + "/" + InteropTestBase.getTestLibraryName(runWithPolyglot.getPolyglotContext()));
        String loadLib = "with llvm load '" + file.getAbsolutePath() + "'";
        Source source = Source.newBuilder("nfi", loadLib, "loadLibrary").internal(true).build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parseInternal(source);
        return target.call();
    }

    protected abstract static class TestRootNode extends RootNode {

        protected TestRootNode() {
            super(null);
        }

        @TruffleBoundary
        protected static void assertEquals(Object expected, Object actual) {
            Assert.assertEquals(expected, actual);
        }

        @Override
        public final Object execute(VirtualFrame frame) {
            try {
                return executeTest(frame);
            } catch (InteropException e) {
                throw CompilerDirectives.shouldNotReachHere(e);
            }
        }

        public abstract Object executeTest(VirtualFrame frame) throws InteropException;
    }

    protected static class SendExecuteNode extends TestRootNode {

        private final Object receiver;

        @Child private InteropLibrary interop;

        protected SendExecuteNode(Object library, String symbol, String signature) {
            this(lookupAndBind(library, symbol, signature));
        }

        protected SendExecuteNode(Object receiver) {
            this.receiver = receiver;
            this.interop = InteropLibrary.getFactory().create(receiver);
        }

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return interop.execute(receiver, frame.getArguments());
        }
    }

    protected static Object lookupAndBind(Object lib, String name, String signature) {
        try {
            Source sigSource = Source.newBuilder("nfi", String.format("with llvm %s", signature), "signature").build();
            Object parsedSig = runWithPolyglot.getTruffleTestEnv().parseInternal(sigSource).call();

            Object function = INTEROP.readMember(lib, name);
            return SIGNATURES.bind(parsedSig, function);
        } catch (UnsupportedMessageException | UnknownIdentifierException ex) {
            throw CompilerDirectives.shouldNotReachHere(ex);
        }
    }
}
