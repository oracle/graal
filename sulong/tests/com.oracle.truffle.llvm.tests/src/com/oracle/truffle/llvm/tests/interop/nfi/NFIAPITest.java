/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.runtime.NFIContextExtension;
import com.oracle.truffle.llvm.tests.interop.InteropTestBase;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.tests.options.TestOptions;
import com.oracle.truffle.tck.TruffleRunner;

public class NFIAPITest {

    @ClassRule public static TruffleRunner.RunWithPolyglotRule runWithPolyglot = new TruffleRunner.RunWithPolyglotRule(InteropTestBase.getContextBuilder());

    private static final Path TEST_DIR = Paths.get(TestOptions.TEST_SUITE_PATH, "nfi");
    private static final String SULONG_FILENAME = "O1." + NFIContextExtension.getNativeLibrarySuffix();

    public static TruffleObject sulongObject;
    public static CallTarget lookupAndBind;

    @BeforeClass
    public static void initialize() {
        sulongObject = loadLibrary("basicTest.c.dir", SULONG_FILENAME);
        lookupAndBind = lookupAndBind();
    }

    private static CallTarget lookupAndBind() {
        return Truffle.getRuntime().createCallTarget(new LookupAndBindNode());
    }

    private static TruffleObject loadLibrary(String lib, String filename) {
        File file = new File(TEST_DIR.toFile(), lib + "/" + filename);
        String loadLib = "with llvm load '" + file.getAbsolutePath() + "'";
        Source source = Source.newBuilder("nfi", loadLib, "loadLibrary").internal(true).build();
        CallTarget target = runWithPolyglot.getTruffleTestEnv().parseInternal(source);
        return (TruffleObject) target.call();
    }

    private static final class LookupAndBindNode extends RootNode {

        @Child private InteropLibrary lookupSymbol = InteropLibrary.getFactory().createDispatched(3);
        @Child private InteropLibrary bind = InteropLibrary.getFactory().createDispatched(3);

        private LookupAndBindNode() {
            super(null);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            Object library = frame.getArguments()[0];
            String symbolName = (String) frame.getArguments()[1];
            String signature = (String) frame.getArguments()[2];

            try {
                Object symbol = lookupSymbol.readMember(library, symbolName);
                return bind.invokeMember(symbol, "bind", signature);
            } catch (InteropException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }
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
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        }

        public abstract Object executeTest(VirtualFrame frame) throws InteropException;
    }

    protected static class SendExecuteNode extends TestRootNode {

        private final TruffleObject receiver;

        @Child private InteropLibrary interop;

        protected SendExecuteNode(TruffleObject library, String symbol, String signature) {
            this(lookupAndBind(library, symbol, signature));
        }

        protected SendExecuteNode(TruffleObject receiver) {
            this.receiver = receiver;
            this.interop = InteropLibrary.getFactory().create(receiver);
        }

        @Override
        public Object executeTest(VirtualFrame frame) throws InteropException {
            return interop.execute(receiver, frame.getArguments());
        }
    }

    protected static TruffleObject lookupAndBind(TruffleObject lib, String name, String signature) {
        return (TruffleObject) lookupAndBind.call(lib, name, signature);
    }
}
